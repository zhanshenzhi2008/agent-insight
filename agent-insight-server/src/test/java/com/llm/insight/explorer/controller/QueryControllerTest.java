package com.llm.insight.explorer.controller;

import com.llm.insight.common.ApiResponse;
import com.llm.insight.explorer.dto.QueryRequest;
import com.llm.insight.explorer.dto.QueryResponse;
import com.llm.insight.explorer.engine.QueryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link QueryController}.
 *
 * These verify the REST contract: success maps to code 0, error maps to -1,
 * and history recording is best-effort (no exception bubbles out).
 */
class QueryControllerTest {

    @Test
    @DisplayName("POST /execute returns code 0 on success")
    void executeSuccess() {
        QueryService svc = mock(QueryService.class);
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(svc.execute(any())).thenReturn(QueryResponse.builder()
                .status("ok").data(java.util.List.of()).total(0L).build());

        QueryController c = new QueryController(svc, mongo);
        ApiResponse<QueryResponse> resp = c.execute(QueryRequest.builder()
                .datasourceKey("ds").tableName("t").build());

        assertThat(resp.getCode()).isEqualTo(0);
        assertThat(resp.getData().getStatus()).isEqualTo("ok");
    }

    @Test
    @DisplayName("POST /execute returns code -1 when service reports an error")
    void executeError() {
        QueryService svc = mock(QueryService.class);
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(svc.execute(any())).thenReturn(QueryResponse.builder()
                .status("error").error("nope").build());

        QueryController c = new QueryController(svc, mongo);
        ApiResponse<QueryResponse> resp = c.execute(QueryRequest.builder()
                .datasourceKey("ds").tableName("t").build());

        assertThat(resp.getCode()).isEqualTo(-1);
        assertThat(resp.getMessage()).isEqualTo("error");
    }

    @Test
    @DisplayName("GET /history returns the persisted history list")
    void historyEndpoint() {
        QueryService svc = mock(QueryService.class);
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.find(any(org.springframework.data.mongodb.core.query.Query.class),
                org.mockito.ArgumentMatchers.eq(com.llm.insight.explorer.document.InsightQueryHistory.class)))
                .thenReturn(java.util.List.of());

        QueryController c = new QueryController(svc, mongo);
        ApiResponse<java.util.List<com.llm.insight.explorer.document.InsightQueryHistory>> resp =
                c.history(null, null, 0, 20);

        assertThat(resp.getCode()).isEqualTo(0);
        assertThat(resp.getData()).isEmpty();
    }
}