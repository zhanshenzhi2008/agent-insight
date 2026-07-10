package com.llm.insight.explorer.controller;

import com.llm.insight.common.ApiResponse;
import com.llm.insight.explorer.service.ConfigService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link TableConfigController#discoverTables(String)}.
 *
 * Controller is now a thin pass-through — verifies the contract:
 *   - delegates to ConfigService.discoverTables(ds)
 *   - wraps exceptions in ApiResponse.error
 *   - unknown datasource returns error code
 *
 * Routing/Mongo/SQL details are covered in ConfigService unit tests.
 */
class TableConfigControllerDiscoverTest {

    private TableConfigController controller(ConfigService cs) {
        return new TableConfigController(cs);
    }

    @Test
    @DisplayName("returns ConfigService.discoverTables result as 200 data")
    void delegatesToService() {
        ConfigService cs = mock(ConfigService.class);
        var ds = com.llm.insight.explorer.document.InsightDatasource.builder()
                .datasourceKey("k").datasourceType("MONGODB").build();
        when(cs.getDatasource("k")).thenReturn(ds);
        when(cs.discoverTables(ds)).thenReturn(List.of(
                Map.of("tableName", "t1", "type", "COLLECTION", "schema", "s"),
                Map.of("tableName", "t2", "type", "COLLECTION", "schema", "s")));

        ApiResponse<List<Map<String, Object>>> resp = controller(cs).discoverTables("k");

        assertThat(resp.getCode()).isEqualTo(0);
        assertThat(resp.getData()).extracting(m -> m.get("tableName"))
                .containsExactly("t1", "t2");
    }

    @Test
    @DisplayName("unknown datasource key returns non-0 code with '不存在' message")
    void unknownDatasource() {
        ConfigService cs = mock(ConfigService.class);
        when(cs.getDatasource(any())).thenReturn(null);

        ApiResponse<List<Map<String, Object>>> resp = controller(cs).discoverTables("missing");

        assertThat(resp.getCode()).isNotEqualTo(0);
        assertThat(resp.getMessage()).contains("不存在");
    }

    @Test
    @DisplayName("service exception is caught and returned as error")
    void exceptionBecomesError() {
        ConfigService cs = mock(ConfigService.class);
        var ds = com.llm.insight.explorer.document.InsightDatasource.builder()
                .datasourceKey("k").datasourceType("MONGODB").build();
        when(cs.getDatasource("k")).thenReturn(ds);
        when(cs.discoverTables(ds)).thenThrow(new RuntimeException("boom"));

        ApiResponse<List<Map<String, Object>>> resp = controller(cs).discoverTables("k");

        assertThat(resp.getCode()).isNotEqualTo(0);
        assertThat(resp.getMessage()).contains("boom");
    }
}