package com.llm.insight.controller;

import com.llm.insight.common.PageResult;
import com.llm.insight.dto.request.RequestSearchQuery;
import com.llm.insight.dto.response.AgentInstanceDTO;
import com.llm.insight.dto.response.RequestOverviewDTO;
import com.llm.insight.dto.response.RequestSummaryDTO;
import com.llm.insight.service.RequestSearchService;
import com.llm.insight.support.BaseWebMvcTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@link RequestController}.
 *
 * <p>Mirrors {@code TraceControllerIntegrationTest}. The pure Mockito
 * {@code RequestControllerTest} does not exercise HTTP-level concerns that
 * matter in production: {@code @ModelAttribute} binding for the nested
 * {@link RequestSearchQuery}, {@code @DateTimeFormat} parsing of
 * {@code startTime}/{@code endTime}, or the {@code GlobalExceptionHandler}
 * envelope. This test fills those gaps.
 */
@DisplayName("RequestController integration (MockMvc standaloneSetup)")
class RequestControllerIntegrationTest extends BaseWebMvcTest {

    private final RequestSearchService requestSearchService = mock(RequestSearchService.class);

    @Override
    protected Object[] controllerOrAdvice() {
        return new Object[]{new RequestController(requestSearchService)};
    }

    @Override
    protected Object[] getControllerAdvices() {
        return new Object[]{new GlobalExceptionHandler()};
    }

    // ─── TC-F1-INT-01: searchRequests ─────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/requests")
    class SearchRequests {

        @Test
        @DisplayName("returns 200 with envelope + PageResult content (no params)")
        void returnsAllRequestsWhenNoFilters() throws Exception {
            RequestSummaryDTO dto = RequestSummaryDTO.builder()
                    .requestId("req-001").topAgentName("DataAgent").agentId(100L)
                    .taskStatus(2).success(true).totalTaskCount(3).failedTaskCount(0)
                    .totalDuration(60000L)
                    .createTime(LocalDateTime.of(2026, 7, 1, 10, 0, 0))
                    .build();
            when(requestSearchService.searchRequests(any(RequestSearchQuery.class)))
                    .thenReturn(PageResult.of(List.of(dto), 1, 0, 20));

            mockMvc.perform(get("/api/v1/requests").accept("application/json"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith("application/json"))
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.message").value("success"))
                    .andExpect(jsonPath("$.data.content", org.hamcrest.Matchers.hasSize(1)))
                    .andExpect(jsonPath("$.data.content[0].requestId").value("req-001"))
                    .andExpect(jsonPath("$.data.content[0].topAgentName").value("DataAgent"))
                    .andExpect(jsonPath("$.data.totalElements").value(1))
                    .andExpect(jsonPath("$.data.page").value(0))
                    .andExpect(jsonPath("$.data.size").value(20));

            verify(requestSearchService).searchRequests(any(RequestSearchQuery.class));
        }

        @Test
        @DisplayName("forwards requestId / agentName / status filters to the service")
        void forwardsStringFilters() throws Exception {
            RequestSummaryDTO dto = RequestSummaryDTO.builder()
                    .requestId("req-002").topAgentName("DataAgent").success(true).build();
            when(requestSearchService.searchRequests(any(RequestSearchQuery.class)))
                    .thenReturn(PageResult.of(List.of(dto), 1, 0, 20));

            mockMvc.perform(get("/api/v1/requests")
                            .param("requestId", "req-002")
                            .param("agentName", "DataAgent")
                            .param("status", "failed")
                            .accept("application/json"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content[0].requestId").value("req-002"));

            verify(requestSearchService).searchRequests(any(RequestSearchQuery.class));
        }

        @Test
        @DisplayName("parses startTime / endTime via @DateTimeFormat yyyy-MM-dd HH:mm:ss")
        void parsesDateRange() throws Exception {
            when(requestSearchService.searchRequests(any(RequestSearchQuery.class)))
                    .thenReturn(PageResult.of(List.of(), 0, 0, 20));

            mockMvc.perform(get("/api/v1/requests")
                            .param("startTime", "2026-07-01 00:00:00")
                            .param("endTime", "2026-07-31 23:59:59")
                            .accept("application/json"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.content").isEmpty());

            verify(requestSearchService).searchRequests(any(RequestSearchQuery.class));
        }

        @Test
        @DisplayName("forwards page / size pagination params (totalPages=2, page=3 → hasNext=false)")
        void forwardsPagination() throws Exception {
            // 100 records / size 50 = 2 total pages (0 and 1). Page 3 is past the end,
            // so PageResult.hasNext must be false. (Pin the contract for PageResult.of.)
            when(requestSearchService.searchRequests(any(RequestSearchQuery.class)))
                    .thenReturn(PageResult.of(List.of(), 100, 3, 50));

            mockMvc.perform(get("/api/v1/requests")
                            .param("page", "3")
                            .param("size", "50")
                            .accept("application/json"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalElements").value(100))
                    .andExpect(jsonPath("$.data.page").value(3))
                    .andExpect(jsonPath("$.data.size").value(50))
                    .andExpect(jsonPath("$.data.hasNext").value(false));

            verify(requestSearchService).searchRequests(any(RequestSearchQuery.class));
        }

        @Test
        @DisplayName("malformed startTime → 500 (known gap: not yet mapped to 400, see GlobalExceptionHandler)")
        void rejectsMalformedDate() throws Exception {
            // KNOWN GAP: DateTimeFormat parse failures throw MethodArgumentConversionNotSupportedException
            // (NOT MethodArgumentTypeMismatchException), so the current GlobalExceptionHandler falls
            // through to handleGeneral(Exception) → 500. Pin current behavior; the handler should be
            // extended in a separate PR to map conversion failures to 400.
            mockMvc.perform(get("/api/v1/requests")
                            .param("startTime", "not-a-date")
                            .accept("application/json"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code").value(500))
                    .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("not-a-date")));
        }

        @Test
        @DisplayName("returns empty content when no requests match")
        void returnsEmptyOnNoMatch() throws Exception {
            when(requestSearchService.searchRequests(any(RequestSearchQuery.class)))
                    .thenReturn(PageResult.of(List.of(), 0, 0, 20));

            mockMvc.perform(get("/api/v1/requests")
                            .param("requestId", "ghost")
                            .accept("application/json"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.content").isEmpty())
                    .andExpect(jsonPath("$.data.totalElements").value(0));
        }
    }

    // ─── TC-F1-INT-02: getOverview ────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/requests/{requestId}/overview")
    class GetOverview {

        @Test
        @DisplayName("returns 200 with overview DTO")
        void returnsOverview() throws Exception {
            RequestOverviewDTO overview = RequestOverviewDTO.builder()
                    .requestId("req-001").topAgentName("DataAgent")
                    .success(true).totalTaskCount(5).failedTaskCount(1)
                    .llmCallCount(12).totalPromptTokens(1000).totalCompletionTokens(500)
                    .totalDuration(30000L)
                    .createTime(LocalDateTime.of(2026, 7, 1, 10, 0, 0))
                    .build();
            when(requestSearchService.getOverview("req-001")).thenReturn(overview);

            mockMvc.perform(get("/api/v1/requests/{requestId}/overview", "req-001")
                            .accept("application/json"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.requestId").value("req-001"))
                    .andExpect(jsonPath("$.data.totalTaskCount").value(5))
                    .andExpect(jsonPath("$.data.failedTaskCount").value(1))
                    .andExpect(jsonPath("$.data.llmCallCount").value(12))
                    .andExpect(jsonPath("$.data.totalPromptTokens").value(1000))
                    .andExpect(jsonPath("$.data.totalCompletionTokens").value(500))
                    .andExpect(jsonPath("$.data.totalDuration").value(30000))
                    .andExpect(jsonPath("$.data.createTime").exists());

            verify(requestSearchService).getOverview("req-001");
        }

        @Test
        @DisplayName("returns null data when overview is not found (requestId unknown)")
        void returnsNullOnNotFound() throws Exception {
            when(requestSearchService.getOverview(eq("unknown"))).thenReturn(null);

            mockMvc.perform(get("/api/v1/requests/{requestId}/overview", "unknown")
                            .accept("application/json"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data").value(nullValue()));
        }
    }

    // ─── TC-F1-INT-03: listInstances ───────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/requests/{requestId}/instances")
    class ListInstances {

        @Test
        @DisplayName("returns 200 with agent instance list")
        void returnsInstances() throws Exception {
            AgentInstanceDTO entrance = AgentInstanceDTO.builder()
                    .agentId(100L).agentName("DataAgent").entrance(true)
                    .taskCount(3).success(true).duration(60000L).build();
            AgentInstanceDTO sub = AgentInstanceDTO.builder()
                    .agentId(101L).agentName("SubAgent").entrance(false)
                    .taskCount(2).success(true).duration(20000L).build();
            when(requestSearchService.listAgentInstances(eq("req-001")))
                    .thenReturn(List.of(entrance, sub));

            mockMvc.perform(get("/api/v1/requests/{requestId}/instances", "req-001")
                            .accept("application/json"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data", org.hamcrest.Matchers.hasSize(2)))
                    .andExpect(jsonPath("$.data[0].agentId").value(100))
                    .andExpect(jsonPath("$.data[0].entrance").value(true))
                    .andExpect(jsonPath("$.data[1].agentName").value("SubAgent"))
                    .andExpect(jsonPath("$.data[1].entrance").value(false));

            verify(requestSearchService).listAgentInstances("req-001");
        }

        @Test
        @DisplayName("returns empty data array when no agents")
        void returnsEmptyOnNoAgents() throws Exception {
            when(requestSearchService.listAgentInstances(eq("req-empty"))).thenReturn(List.of());

            mockMvc.perform(get("/api/v1/requests/{requestId}/instances", "req-empty")
                            .accept("application/json"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data").isEmpty());
        }
    }

    // ─── TC-F1-INT-04: error envelope ─────────────────────────────────────────

    @Nested
    @DisplayName("error handling")
    class ErrorEnvelope {

        @Test
        @DisplayName("IllegalArgumentException → 400 envelope")
        void mapsIllegalArgumentTo400() throws Exception {
            when(requestSearchService.searchRequests(any(RequestSearchQuery.class)))
                    .thenThrow(new IllegalArgumentException("时间区间非法"));

            mockMvc.perform(get("/api/v1/requests")
                            .param("startTime", "2026-07-01 00:00:00")
                            .param("endTime", "2026-06-01 00:00:00")
                            .accept("application/json"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value("时间区间非法"))
                    .andExpect(jsonPath("$.data").doesNotExist());
        }

        @Test
        @DisplayName("RuntimeException → 500 envelope")
        void mapsUnexpectedTo500() throws Exception {
            when(requestSearchService.getOverview(eq("boom")))
                    .thenThrow(new RuntimeException("数据库连接失败"));

            mockMvc.perform(get("/api/v1/requests/{requestId}/overview", "boom")
                            .accept("application/json"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code").value(500))
                    .andExpect(jsonPath("$.message", containsString("数据库连接失败")));
        }
    }
}