package com.llm.insight.explorer.controller;

import com.llm.insight.common.ApiResponse;
import com.llm.insight.explorer.document.InsightDatasource;
import com.llm.insight.explorer.engine.DynamicDatasourceManager;
import com.llm.insight.explorer.engine.QueryRouter;
import com.llm.insight.explorer.service.ConfigService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DatasourceController}.
 * Covers TC-EX-01 ~ TC-EX-10 (Data Explorer: datasource management).
 */
class DatasourceControllerTest {

    private DatasourceController controller(ConfigService configService,
                                          DynamicDatasourceManager dsManager,
                                          QueryRouter queryRouter) {
        return new DatasourceController(configService, dsManager, queryRouter);
    }

    // ─── TC-EX-01/02/03: 创建数据源 ───────────────────────────────────────────

    @Nested
    class CreateDatasource {

        @Test
        @DisplayName("POST /api/v1/explorer/datasources creates MySQL datasource")
        void testCreateMySQLDatasource() {
            ConfigService configService = mock(ConfigService.class);
            InsightDatasource created = InsightDatasource.builder()
                    .id("ds-001").datasourceKey("test_mysql")
                    .datasourceName("Test MySQL").datasourceType("MYSQL")
                    .status("ACTIVE").createdBy("admin").createdAt(LocalDateTime.now())
                    .build();

            when(configService.createDatasource(any(InsightDatasource.class))).thenReturn(created);

            InsightDatasource input = InsightDatasource.builder()
                    .datasourceKey("test_mysql").datasourceName("Test MySQL")
                    .datasourceType("MYSQL")
                    .connectionConfig(InsightDatasource.ConnectionConfig.builder()
                            .host("localhost").port(3306).database("test_db")
                            .username("root").password("test123").build())
                    .build();

            ApiResponse<InsightDatasource> resp = controller(configService, null, null).create(input);

            assertThat(resp.getCode()).isEqualTo(0);
            assertThat(resp.getData().getDatasourceKey()).isEqualTo("test_mysql");
            assertThat(resp.getData().getDatasourceType()).isEqualTo("MYSQL");
            assertThat(resp.getData().getStatus()).isEqualTo("ACTIVE");
        }

        @Test
        @DisplayName("POST /api/v1/explorer/datasources creates PostgreSQL datasource")
        void testCreatePostgreSQLDatasource() {
            ConfigService configService = mock(ConfigService.class);
            InsightDatasource created = InsightDatasource.builder()
                    .id("ds-002").datasourceKey("test_pg")
                    .datasourceName("Test PostgreSQL").datasourceType("POSTGRESQL")
                    .status("ACTIVE").build();

            when(configService.createDatasource(any(InsightDatasource.class))).thenReturn(created);

            InsightDatasource input = InsightDatasource.builder()
                    .datasourceKey("test_pg").datasourceName("Test PostgreSQL")
                    .datasourceType("POSTGRESQL")
                    .connectionConfig(InsightDatasource.ConnectionConfig.builder()
                            .host("localhost").port(5432).database("test_db")
                            .username("postgres").password("test123").build())
                    .build();

            ApiResponse<InsightDatasource> resp = controller(configService, null, null).create(input);

            assertThat(resp.getCode()).isEqualTo(0);
            assertThat(resp.getData().getDatasourceType()).isEqualTo("POSTGRESQL");
        }

        @Test
        @DisplayName("POST /api/v1/explorer/datasources creates MongoDB datasource")
        void testCreateMongoDBDatasource() {
            ConfigService configService = mock(ConfigService.class);
            InsightDatasource created = InsightDatasource.builder()
                    .id("ds-003").datasourceKey("test_mongo")
                    .datasourceName("Test MongoDB").datasourceType("MONGODB")
                    .status("ACTIVE").build();

            when(configService.createDatasource(any(InsightDatasource.class))).thenReturn(created);

            InsightDatasource input = InsightDatasource.builder()
                    .datasourceKey("test_mongo").datasourceName("Test MongoDB")
                    .datasourceType("MONGODB")
                    .connectionConfig(InsightDatasource.ConnectionConfig.builder()
                            .host("localhost").port(27017).database("test_db").build())
                    .build();

            ApiResponse<InsightDatasource> resp = controller(configService, null, null).create(input);

            assertThat(resp.getCode()).isEqualTo(0);
            assertThat(resp.getData().getDatasourceType()).isEqualTo("MONGODB");
        }
    }

    // ─── TC-EX-10: 列出所有数据源 ─────────────────────────────────────────────

    @Nested
    class ListDatasources {

        @Test
        @DisplayName("GET /api/v1/explorer/datasources returns all datasources (password masked)")
        void testListDatasources() {
            ConfigService configService = mock(ConfigService.class);
            InsightDatasource ds1 = InsightDatasource.builder()
                    .id("ds-001").datasourceKey("mysql_orders").datasourceName("MySQL 订单库")
                    .datasourceType("MYSQL").status("ACTIVE")
                    .connectionConfig(InsightDatasource.ConnectionConfig.builder()
                            .host("localhost").port(3306).database("orders")
                            .username("root").password("secret123").build())
                    .build();

            InsightDatasource ds2 = InsightDatasource.builder()
                    .id("ds-002").datasourceKey("pg_users").datasourceName("PostgreSQL 用户库")
                    .datasourceType("POSTGRESQL").status("ACTIVE")
                    .connectionConfig(InsightDatasource.ConnectionConfig.builder()
                            .host("localhost").port(5432).database("users")
                            .username("postgres").password("secret456").build())
                    .build();

            when(configService.listDatasources()).thenReturn(List.of(ds1, ds2));

            ApiResponse<List<InsightDatasource>> resp = controller(configService, null, null).list();

            assertThat(resp.getCode()).isEqualTo(0);
            assertThat(resp.getData()).hasSize(2);
            assertThat(resp.getData().get(0).getDatasourceKey()).isEqualTo("mysql_orders");
            assertThat(resp.getData().get(0).getConnectionConfig().getPassword()).isEqualTo("******");
            assertThat(resp.getData().get(1).getDatasourceKey()).isEqualTo("pg_users");
        }

        @Test
        @DisplayName("GET /api/v1/explorer/datasources returns empty list when no datasources")
        void testListDatasourcesEmpty() {
            ConfigService configService = mock(ConfigService.class);
            when(configService.listDatasources()).thenReturn(List.of());

            ApiResponse<List<InsightDatasource>> resp = controller(configService, null, null).list();

            assertThat(resp.getCode()).isEqualTo(0);
            assertThat(resp.getData()).isEmpty();
        }
    }

    // ─── TC-EX-04: 测试数据源连接 ─────────────────────────────────────────────

    @Nested
    class TestConnection {

        @Test
        @DisplayName("POST /api/v1/explorer/datasources/{id}/test returns connected=true on success")
        void testConnectionSuccess() throws Exception {
            ConfigService configService = mock(ConfigService.class);
            DynamicDatasourceManager dsManager = mock(DynamicDatasourceManager.class);
            DataSource mockDs = mock(DataSource.class);
            Connection mockConn = mock(Connection.class);
            Statement mockStmt = mock(Statement.class);

            InsightDatasource ds = InsightDatasource.builder()
                    .id("ds-001").datasourceKey("test_mysql").datasourceType("MYSQL").build();

            when(configService.getDatasourceById("ds-001")).thenReturn(ds);
            doNothing().when(dsManager).cacheDatasource(any());
            when(dsManager.getSqlDataSource(any())).thenReturn(mockDs);
            when(mockDs.getConnection()).thenReturn(mockConn);
            when(mockConn.createStatement()).thenReturn(mockStmt);
            when(mockStmt.executeQuery(anyString())).thenReturn(mock(java.sql.ResultSet.class,
                    withSettings().extraInterfaces(java.sql.ResultSetMetaData.class)));

            ApiResponse<Map<String, Object>> resp =
                    controller(configService, dsManager, null).testConnection("ds-001");

            assertThat(resp.getCode()).isEqualTo(0);
            assertThat(resp.getData().get("connected")).isEqualTo(true);
            assertThat(resp.getData()).containsKey("responseTimeMs");
        }

        @Test
        @DisplayName("POST /api/v1/explorer/datasources/{id}/test returns connected=false on failure")
        void testConnectionFailure() {
            ConfigService configService = mock(ConfigService.class);
            DynamicDatasourceManager dsManager = mock(DynamicDatasourceManager.class);

            InsightDatasource ds = InsightDatasource.builder()
                    .id("ds-bad").datasourceKey("bad_ds").datasourceType("MYSQL").build();

            when(configService.getDatasourceById("ds-bad")).thenReturn(ds);
            doThrow(new RuntimeException("Connection refused")).when(dsManager).cacheDatasource(any());

            ApiResponse<Map<String, Object>> resp =
                    controller(configService, dsManager, null).testConnection("ds-bad");

            assertThat(resp.getCode()).isEqualTo(0);
            assertThat(resp.getData().get("connected")).isEqualTo(false);
            assertThat(resp.getData()).containsKey("error");
        }

        @Test
        @DisplayName("POST /api/v1/explorer/datasources/{id}/test returns error when datasource not found")
        void testConnectionDatasourceNotFound() {
            ConfigService configService = mock(ConfigService.class);
            when(configService.getDatasourceById("unknown-id")).thenReturn(null);

            ApiResponse<Map<String, Object>> resp =
                    controller(configService, null, null).testConnection("unknown-id");

            assertThat(resp.getCode()).isEqualTo(-1);
            assertThat(resp.getMessage()).isEqualTo("数据源不存在");
        }
    }

    // ─── TC-EX-07: 更新数据源配置 ──────────────────────────────────────────────

    @Nested
    class UpdateDatasource {

        @Test
        @DisplayName("PUT /api/v1/explorer/datasources/{id} updates datasource")
        void testUpdateDatasource() {
            ConfigService configService = mock(ConfigService.class);
            InsightDatasource updated = InsightDatasource.builder()
                    .id("ds-001").datasourceKey("test_mysql")
                    .datasourceName("Updated MySQL").datasourceType("MYSQL")
                    .status("ACTIVE").build();

            when(configService.getDatasourceById("ds-001")).thenReturn(null);
            when(configService.updateDatasource(eq("ds-001"), any(InsightDatasource.class)))
                    .thenReturn(updated);

            InsightDatasource input = InsightDatasource.builder()
                    .datasourceName("Updated MySQL").datasourceType("MYSQL").build();

            ApiResponse<InsightDatasource> resp =
                    controller(configService, null, null).update("ds-001", input);

            assertThat(resp.getCode()).isEqualTo(0);
            assertThat(resp.getData().getDatasourceName()).isEqualTo("Updated MySQL");
        }

        @Test
        @DisplayName("PUT /api/v1/explorer/datasources/{id} preserves password when masked")
        void testUpdateDatasourcePreservesPassword() {
            ConfigService configService = mock(ConfigService.class);
            InsightDatasource existing = InsightDatasource.builder()
                    .id("ds-001")
                    .connectionConfig(InsightDatasource.ConnectionConfig.builder()
                            .password("original_secret").build())
                    .build();

            InsightDatasource updated = InsightDatasource.builder()
                    .id("ds-001").datasourceName("Updated").datasourceType("MYSQL")
                    .connectionConfig(InsightDatasource.ConnectionConfig.builder()
                            .password("original_secret").build())
                    .build();

            when(configService.getDatasourceById("ds-001")).thenReturn(existing);
            when(configService.updateDatasource(eq("ds-001"), any())).thenReturn(updated);

            InsightDatasource input = InsightDatasource.builder()
                    .datasourceName("Updated").datasourceType("MYSQL")
                    .connectionConfig(InsightDatasource.ConnectionConfig.builder()
                            .password("******").build())
                    .build();

            controller(configService, null, null).update("ds-001", input);

            verify(configService).updateDatasource(eq("ds-001"), argThat(ds ->
                    "original_secret".equals(ds.getConnectionConfig().getPassword())));
        }
    }

    // ─── TC-EX-08: 删除数据源 ─────────────────────────────────────────────────

    @Nested
    class DeleteDatasource {

        @Test
        @DisplayName("DELETE /api/v1/explorer/datasources/{id} deletes datasource")
        void testDeleteDatasource() {
            ConfigService configService = mock(ConfigService.class);
            doNothing().when(configService).deleteDatasource("ds-001");

            ApiResponse<Void> resp = controller(configService, null, null).delete("ds-001");

            assertThat(resp.getCode()).isEqualTo(0);
            verify(configService).deleteDatasource("ds-001");
        }
    }

    // ─── TC-EX-06: 列出数据源中的表 ───────────────────────────────────────────

    @Nested
    class ListTables {

        @Test
        @DisplayName("GET /api/v1/explorer/datasources/{id}/tables returns table list for MySQL")
        void testListTablesMySQL() throws Exception {
            ConfigService configService = mock(ConfigService.class);
            DynamicDatasourceManager dsManager = mock(DynamicDatasourceManager.class);
            DataSource mockDs = mock(DataSource.class);
            Connection mockConn = mock(Connection.class);
            Statement mockStmt = mock(Statement.class);

            InsightDatasource ds = InsightDatasource.builder()
                    .id("ds-001").datasourceType("MYSQL").build();

            when(configService.getDatasourceById("ds-001")).thenReturn(ds);
            doNothing().when(dsManager).cacheDatasource(any());
            when(dsManager.getSqlDataSource(any())).thenReturn(mockDs);
            when(mockDs.getConnection()).thenReturn(mockConn);
            when(mockConn.createStatement()).thenReturn(mockStmt);
            when(mockStmt.executeQuery(anyString())).thenReturn(mock(java.sql.ResultSet.class,
                    withSettings().extraInterfaces(java.sql.ResultSetMetaData.class)));

            ApiResponse<List<Map<String, Object>>> resp =
                    controller(configService, dsManager, null).listTables("ds-001");

            assertThat(resp.getCode()).isEqualTo(0);
        }
    }
}
