package com.llm.insight.explorer.engine;

import com.llm.insight.explorer.document.InsightColumnConfig;
import com.llm.insight.explorer.document.InsightDatasource;
import com.llm.insight.explorer.document.InsightDatasource.ConnectionConfig;
import com.llm.insight.explorer.dto.QueryRequest;
import com.llm.insight.explorer.dto.QueryResponse;
import com.llm.insight.explorer.service.ConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link QueryService}.
 *
 * Verifies:
 *  - Datasource-not-found returns an error response.
 *  - Column metadata is resolved from ConfigService when available.
 *  - Hidden columns are excluded and ordering follows orderIndex.
 *  - Auto-inference kicks in when no column config exists but query returns rows.
 */
class QueryServiceTest {

    private DynamicDatasourceManager dsManager;
    private QueryRouter router;
    private ConfigService configService;
    private SqlQueryExecutor sqlExecutor;

    @BeforeEach
    void setUp() {
        dsManager = mock(DynamicDatasourceManager.class);
        configService = mock(ConfigService.class);
        sqlExecutor = mock(SqlQueryExecutor.class);
        router = mock(QueryRouter.class);
        when(router.route(any(InsightDatasource.class))).thenReturn(sqlExecutor);
    }

    @Test
    @DisplayName("returns error response when datasource is not configured")
    void missingDatasource() {
        when(configService.getDatasource("missing")).thenReturn(null);
        QueryService service = new QueryService(dsManager, router, configService);

        QueryResponse r = service.execute(
                QueryRequest.builder().datasourceKey("missing").tableName("t").build());

        assertThat(r.getStatus()).isEqualTo("error");
        assertThat(r.getError()).contains("数据源不存在");
    }

    @Test
    @DisplayName("uses preconfigured columns from ConfigService")
    void usesConfiguredColumns() {
        InsightDatasource ds = mysqlDatasource();
        when(configService.getDatasource(ds.getDatasourceKey())).thenReturn(ds);

        InsightColumnConfig a = column("id", "ID", 1, false);
        InsightColumnConfig b = column("name", "Name", 0, false);
        InsightColumnConfig hidden = column("secret", "Secret", 2, true);
        when(configService.getColumnConfigs(ds.getDatasourceKey(), "users"))
                .thenReturn(List.of(a, b, hidden));

        QueryResponse mocked = QueryResponse.builder()
                .status("ok")
                .data(List.of(Map.of("id", 1, "name", "alice")))
                .total(1L).page(0).pageSize(20).totalPages(1).hasNext(false)
                .build();
        when(sqlExecutor.execute(any(), any())).thenReturn(mocked);

        QueryService service = new QueryService(dsManager, router, configService);
        QueryResponse r = service.execute(QueryRequest.builder()
                .datasourceKey(ds.getDatasourceKey()).tableName("users").build());

        assertThat(r.getColumns()).hasSize(2);
        assertThat(r.getColumns().get(0).getKey()).isEqualTo("name"); // orderIndex 0
        assertThat(r.getColumns().get(1).getKey()).isEqualTo("id");
        assertThat(r.getColumns()).noneMatch(c -> "secret".equals(c.getKey()));
    }

    @Test
    @DisplayName("wraps executor exception into a friendly error response")
    void executorFailureHandled() {
        InsightDatasource ds = mysqlDatasource();
        when(configService.getDatasource(ds.getDatasourceKey())).thenReturn(ds);
        when(sqlExecutor.execute(any(), any()))
                .thenThrow(new RuntimeException("connection refused"));
        when(configService.getColumnConfigs(any(), any())).thenReturn(List.of());

        QueryService service = new QueryService(dsManager, router, configService);
        QueryResponse r = service.execute(QueryRequest.builder()
                .datasourceKey(ds.getDatasourceKey()).tableName("users").build());

        assertThat(r.getStatus()).isEqualTo("error");
        assertThat(r.getError()).contains("connection refused");
    }

    @Test
    @DisplayName("infers columns from query result when no column config is present")
    void infersColumnsFromData() {
        InsightDatasource ds = mysqlDatasource();
        when(configService.getDatasource(ds.getDatasourceKey())).thenReturn(ds);
        // ResponseHasData re-fetches the datasource from the manager cache; make sure it's there.
        when(dsManager.getDatasource(ds.getDatasourceKey())).thenReturn(ds);
        when(configService.getColumnConfigs(ds.getDatasourceKey(), "users")).thenReturn(List.of());

        org.mockito.Mockito.lenient().when(sqlExecutor.execute(any(), any()))
                .thenReturn(QueryResponse.builder()
                        .status("ok")
                        .data(List.of(Map.of("id", 1, "name", "alice")))
                        .total(1L).page(0).pageSize(20).totalPages(1).hasNext(false)
                        .build());

        QueryService service = new QueryService(dsManager, router, configService);
        QueryResponse r = service.execute(QueryRequest.builder()
                .datasourceKey(ds.getDatasourceKey()).tableName("users").build());

        // First column resolution: configs empty → fall to inference
        // Inference: responseHasData must be true and inferColumnsFromData returns columns from row keys
        assertThat(r.getColumns()).extracting("key").containsExactlyInAnyOrder("id", "name");
        assertThat(r.getColumns().get(0).getSortable()).isTrue();
    }

    private InsightDatasource mysqlDatasource() {
        ConnectionConfig cc = ConnectionConfig.builder()
                .host("localhost").port(3306).database("test")
                .username("u").password("p").build();
        return InsightDatasource.builder()
                .id("d1").datasourceKey("test_mysql").datasourceName("Test MySQL")
                .datasourceType("MYSQL").status("ACTIVE")
                .connectionConfig(cc).build();
    }

    private InsightColumnConfig column(String name, String display, int order, boolean hidden) {
        return InsightColumnConfig.builder()
                .datasourceKey("test_mysql").tableName("users")
                .columnName(name).displayName(display).dataType("STRING").renderType("TEXT")
                .orderIndex(order).hidden(hidden).sortable(true).filterable(true)
                .build();
    }
}