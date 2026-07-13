package com.llm.insight.controller;

import com.llm.insight.common.ApiResponse;
import com.llm.insight.common.PageResult;
import com.llm.insight.dto.request.RequestSearchQuery;
import com.llm.insight.dto.response.AgentInstanceDTO;
import com.llm.insight.dto.response.RequestOverviewDTO;
import com.llm.insight.dto.response.RequestSummaryDTO;
import com.llm.insight.service.RequestSearchService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RequestController}.
 * Covers TC-F1 series (TC-F1-01 ~ TC-F1-08).
 */
class RequestControllerTest {

    private RequestController controller(RequestSearchService svc) {
        return new RequestController(svc);
    }

    // ─── TC-F1-01: 按 requestId 精确查询 ───────────────────────────────────────

    @Nested
    class SearchByRequestId {

        @Test
        @DisplayName("GET /api/v1/requests?requestId=xxx returns matching requests")
        void testSearchByRequestId() {
            RequestSearchService svc = mock(RequestSearchService.class);
            RequestSummaryDTO dto = RequestSummaryDTO.builder()
                    .requestId("req-001")
                    .topAgentName("DataAgent")
                    .agentId(100L)
                    .taskStatus(2)
                    .success(true)
                    .totalTaskCount(3)
                    .failedTaskCount(0)
                    .totalDuration(60000L)
                    .createTime(LocalDateTime.of(2025, 1, 15, 10, 0))
                    .build();

            when(svc.searchRequests(any(RequestSearchQuery.class)))
                    .thenReturn(PageResult.of(List.of(dto), 1, 0, 20));

            ApiResponse<PageResult<RequestSummaryDTO>> resp =
                    controller(svc).searchRequests(new RequestSearchQuery());

            assertThat(resp.getCode()).isEqualTo(0);
            assertThat(resp.getData().getContent()).hasSize(1);
            assertThat(resp.getData().getContent().get(0).getRequestId()).isEqualTo("req-001");
        }
    }

    // ─── TC-F1-02: 按时间范围查询 ─────────────────────────────────────────────

    @Nested
    class SearchByTimeRange {

        @Test
        @DisplayName("GET /api/v1/requests?startTime=...&endTime=... returns requests in range")
        void testSearchByTimeRange() {
            RequestSearchService svc = mock(RequestSearchService.class);
            when(svc.searchRequests(any(RequestSearchQuery.class)))
                    .thenReturn(PageResult.of(List.of(), 0, 0, 20));

            RequestSearchQuery query = new RequestSearchQuery();
            query.setStartTime(java.time.LocalDateTime.of(2025, 1, 1, 0, 0));
            query.setEndTime(java.time.LocalDateTime.of(2025, 1, 31, 23, 59, 59));

            ApiResponse<PageResult<RequestSummaryDTO>> resp = controller(svc).searchRequests(query);

            assertThat(resp.getCode()).isEqualTo(0);
            assertThat(resp.getData().getContent()).isEmpty();
        }
    }

    // ─── TC-F1-03: 按 Agent 名称过滤 ────────────────────────────────────────────

    @Nested
    class SearchByAgentName {

        @Test
        @DisplayName("GET /api/v1/requests?agentName=DataAgent returns only that agent")
        void testSearchByAgentName() {
            RequestSearchService svc = mock(RequestSearchService.class);
            RequestSummaryDTO dto = RequestSummaryDTO.builder()
                    .requestId("req-002")
                    .topAgentName("DataAgent")
                    .success(true)
                    .build();

            when(svc.searchRequests(any(RequestSearchQuery.class)))
                    .thenReturn(PageResult.of(List.of(dto), 1, 0, 20));

            RequestSearchQuery query = new RequestSearchQuery();
            query.setAgentName("DataAgent");

            ApiResponse<PageResult<RequestSummaryDTO>> resp = controller(svc).searchRequests(query);

            assertThat(resp.getCode()).isEqualTo(0);
            assertThat(resp.getData().getContent().get(0).getTopAgentName()).isEqualTo("DataAgent");
        }
    }

    // ─── TC-F1-04: 按状态过滤 ──────────────────────────────────────────────────

    @Nested
    class SearchByStatus {

        @Test
        @DisplayName("GET /api/v1/requests?status=failed returns only failed requests")
        void testSearchByStatus() {
            RequestSearchService svc = mock(RequestSearchService.class);
            RequestSummaryDTO dto = RequestSummaryDTO.builder()
                    .requestId("req-003")
                    .topAgentName("DataAgent")
                    .success(false)
                    .failedTaskCount(2)
                    .build();

            when(svc.searchRequests(any(RequestSearchQuery.class)))
                    .thenReturn(PageResult.of(List.of(dto), 1, 0, 20));

            RequestSearchQuery query = new RequestSearchQuery();
            query.setStatus("failed");

            ApiResponse<PageResult<RequestSummaryDTO>> resp = controller(svc).searchRequests(query);

            assertThat(resp.getCode()).isEqualTo(0);
            assertThat(resp.getData().getContent().get(0).getSuccess()).isFalse();
        }
    }

    // ─── TC-F1-05: 分页查询 ────────────────────────────────────────────────────

    @Nested
    class Pagination {

        @Test
        @DisplayName("GET /api/v1/requests?page=0&size=20 returns correct page metadata")
        void testPagination() {
            RequestSearchService svc = mock(RequestSearchService.class);
            when(svc.searchRequests(any(RequestSearchQuery.class)))
                    .thenReturn(PageResult.of(List.of(), 50, 0, 20));

            RequestSearchQuery query = new RequestSearchQuery();
            query.setPage(0);
            query.setSize(20);

            ApiResponse<PageResult<RequestSummaryDTO>> resp = controller(svc).searchRequests(query);

            assertThat(resp.getCode()).isEqualTo(0);
            assertThat(resp.getData().getTotalElements()).isEqualTo(50);
            assertThat(resp.getData().getPage()).isEqualTo(0);
            assertThat(resp.getData().getSize()).isEqualTo(20);
        }

        @Test
        @DisplayName("GET /api/v1/requests?page=2&size=20 returns second page")
        void testSecondPage() {
            RequestSearchService svc = mock(RequestSearchService.class);
            when(svc.searchRequests(any(RequestSearchQuery.class)))
                    .thenReturn(PageResult.of(List.of(), 50, 2, 20));

            RequestSearchQuery query = new RequestSearchQuery();
            query.setPage(2);
            query.setSize(20);

            ApiResponse<PageResult<RequestSummaryDTO>> resp = controller(svc).searchRequests(query);

            assertThat(resp.getCode()).isEqualTo(0);
            assertThat(resp.getData().getPage()).isEqualTo(2);
        }
    }

    // ─── TC-F1-07: 获取请求概览 ────────────────────────────────────────────────

    @Nested
    class GetOverview {

        @Test
        @DisplayName("GET /api/v1/requests/{requestId}/overview returns overview data")
        void testGetOverview() {
            RequestSearchService svc = mock(RequestSearchService.class);
            RequestOverviewDTO overview = RequestOverviewDTO.builder()
                    .requestId("req-001")
                    .topAgentName("DataAgent")
                    .success(true)
                    .totalTaskCount(5)
                    .failedTaskCount(1)
                    .llmCallCount(12)
                    .totalPromptTokens(1000)
                    .totalCompletionTokens(500)
                    .totalDuration(30000L)
                    .createTime(LocalDateTime.of(2025, 1, 15, 10, 0))
                    .build();

            when(svc.getOverview("req-001")).thenReturn(overview);

            ApiResponse<RequestOverviewDTO> resp = controller(svc).getOverview("req-001");

            assertThat(resp.getCode()).isEqualTo(0);
            assertThat(resp.getData().getRequestId()).isEqualTo("req-001");
            assertThat(resp.getData().getTotalTaskCount()).isEqualTo(5);
            assertThat(resp.getData().getFailedTaskCount()).isEqualTo(1);
            assertThat(resp.getData().getLlmCallCount()).isEqualTo(12);
        }

        @Test
        @DisplayName("GET /api/v1/requests/{requestId}/overview returns null when not found")
        void testGetOverviewNotFound() {
            RequestSearchService svc = mock(RequestSearchService.class);
            when(svc.getOverview("unknown")).thenReturn(null);

            ApiResponse<RequestOverviewDTO> resp = controller(svc).getOverview("unknown");

            assertThat(resp.getCode()).isEqualTo(0);
            assertThat(resp.getData()).isNull();
        }
    }

    // ─── TC-F1-08: 获取 Agent 实例列表 ─────────────────────────────────────────

    @Nested
    class ListInstances {

        @Test
        @DisplayName("GET /api/v1/requests/{requestId}/instances returns agent instance list")
        void testGetInstances() {
            RequestSearchService svc = mock(RequestSearchService.class);
            AgentInstanceDTO instance = AgentInstanceDTO.builder()
                    .agentId(100L)
                    .agentName("DataAgent")
                    .entrance(true)
                    .taskCount(3)
                    .success(true)
                    .duration(60000L)
                    .build();

            when(svc.listAgentInstances("req-001")).thenReturn(List.of(instance));

            ApiResponse<List<AgentInstanceDTO>> resp = controller(svc).listInstances("req-001");

            assertThat(resp.getCode()).isEqualTo(0);
            assertThat(resp.getData()).hasSize(1);
            assertThat(resp.getData().get(0).getAgentId()).isEqualTo(100L);
            assertThat(resp.getData().get(0).getAgentName()).isEqualTo("DataAgent");
            assertThat(resp.getData().get(0).getEntrance()).isTrue();
        }

        @Test
        @DisplayName("GET /api/v1/requests/{requestId}/instances returns empty list when no agents")
        void testGetInstancesEmpty() {
            RequestSearchService svc = mock(RequestSearchService.class);
            when(svc.listAgentInstances("req-empty")).thenReturn(List.of());

            ApiResponse<List<AgentInstanceDTO>> resp = controller(svc).listInstances("req-empty");

            assertThat(resp.getCode()).isEqualTo(0);
            assertThat(resp.getData()).isEmpty();
        }
    }
}
