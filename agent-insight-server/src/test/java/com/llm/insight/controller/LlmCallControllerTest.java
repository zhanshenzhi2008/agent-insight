package com.llm.insight.controller;

import com.llm.insight.dto.response.LlmCallDTO;
import com.llm.insight.dto.response.LlmCallDetailDTO;
import com.llm.insight.dto.response.TokenUsageDTO;
import com.llm.insight.service.LlmCallAnalysisService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link LlmCallController}.
 * Covers TC-F4 series (TC-F4-01 ~ TC-F4-06).
 */
class LlmCallControllerTest {

    private LlmCallController controller(LlmCallAnalysisService svc) {
        return new LlmCallController(svc);
    }

    // ─── TC-F4-01: 获取 LLM 调用列表 ───────────────────────────────────────────

    @Nested
    class ListLlmCalls {

        @Test
        @DisplayName("GET /api/v1/requests/{id}/llm-calls returns call list")
        void testListLlmCalls() {
            LlmCallAnalysisService svc = mock(LlmCallAnalysisService.class);
            LlmCallDTO call = LlmCallDTO.builder()
                    .id(1L).requestId("req-001").agent("DataAgent")
                    .templateName("analyze_prompt").planUniqueName("plan_analyze")
                    .modelType("gpt-4o").spendTime(1500L)
                    .promptTokens(500).completionTokens(300).totalTokens(800)
                    .successExpression(true)
                    .requestBodyPreview("Analyze the data...")
                    .responseBodyPreview("Analysis complete.")
                    .createTime(LocalDateTime.of(2025, 1, 15, 10, 0))
                    .build();

            when(svc.listLlmCalls("req-001")).thenReturn(List.of(call));

            var resp = controller(svc).listLlmCalls("req-001");

            assertThat(resp.getCode()).isEqualTo(0);
            assertThat(resp.getData()).hasSize(1);
            assertThat(resp.getData().get(0).getId()).isEqualTo(1L);
            assertThat(resp.getData().get(0).getAgent()).isEqualTo("DataAgent");
            assertThat(resp.getData().get(0).getModelType()).isEqualTo("gpt-4o");
            assertThat(resp.getData().get(0).getTotalTokens()).isEqualTo(800);
            assertThat(resp.getData().get(0).getSuccessExpression()).isTrue();
        }

        @Test
        @DisplayName("GET /api/v1/requests/{id}/llm-calls returns empty list when no calls")
        void testListLlmCallsEmpty() {
            LlmCallAnalysisService svc = mock(LlmCallAnalysisService.class);
            when(svc.listLlmCalls("req-no-calls")).thenReturn(List.of());

            var resp = controller(svc).listLlmCalls("req-no-calls");

            assertThat(resp.getCode()).isEqualTo(0);
            assertThat(resp.getData()).isEmpty();
        }
    }

    // ─── TC-F4-02: 慢调用 TopN ─────────────────────────────────────────────────

    @Nested
    class GetSlowCalls {

        @Test
        @DisplayName("GET /api/v1/requests/{id}/llm-calls/slow?topN=5 returns slowest calls")
        void testGetSlowCalls() {
            LlmCallAnalysisService svc = mock(LlmCallAnalysisService.class);
            LlmCallDTO slowCall = LlmCallDTO.builder()
                    .id(5L).requestId("req-001").agent("DataAgent")
                    .spendTime(10000L).totalTokens(2000).successExpression(true).build();

            when(svc.getSlowCalls("req-001", 5)).thenReturn(List.of(slowCall));

            var resp = controller(svc).getSlowCalls("req-001", 5);

            assertThat(resp.getCode()).isEqualTo(0);
            assertThat(resp.getData().get(0).getSpendTime()).isEqualTo(10000L);
        }

        @Test
        @DisplayName("GET /api/v1/requests/{id}/llm-calls/slow uses default topN=10")
        void testGetSlowCallsDefaultTopN() {
            LlmCallAnalysisService svc = mock(LlmCallAnalysisService.class);
            when(svc.getSlowCalls("req-001", 10)).thenReturn(List.of());

            var resp = controller(svc).getSlowCalls("req-001", 10);

            assertThat(resp.getCode()).isEqualTo(0);
            assertThat(resp.getData()).isEmpty();
        }
    }

    // ─── TC-F4-03: Token 消耗统计 ──────────────────────────────────────────────

    @Nested
    class GetTokenUsage {

        @Test
        @DisplayName("GET /api/v1/requests/{id}/llm-calls/usage returns token statistics")
        void testGetTokenUsage() {
            LlmCallAnalysisService svc = mock(LlmCallAnalysisService.class);
            TokenUsageDTO usage = TokenUsageDTO.builder()
                    .totalPromptTokens(5000)
                    .totalCompletionTokens(2500)
                    .totalTokens(7500)
                    .callCount(10)
                    .build();

            when(svc.getTokenUsage("req-001")).thenReturn(usage);

            var resp = controller(svc).getTokenUsage("req-001");

            assertThat(resp.getCode()).isEqualTo(0);
            assertThat(resp.getData().getTotalPromptTokens()).isEqualTo(5000);
            assertThat(resp.getData().getTotalCompletionTokens()).isEqualTo(2500);
            assertThat(resp.getData().getTotalTokens()).isEqualTo(7500);
            assertThat(resp.getData().getCallCount()).isEqualTo(10);
        }
    }

    // ─── TC-F4-04: LLM 调用失败列表 ────────────────────────────────────────────

    @Nested
    class GetFailedCalls {

        @Test
        @DisplayName("GET /api/v1/requests/{id}/llm-calls/failed returns only failed calls")
        void testGetFailedCalls() {
            LlmCallAnalysisService svc = mock(LlmCallAnalysisService.class);
            LlmCallDTO failedCall = LlmCallDTO.builder()
                    .id(3L).requestId("req-001").agent("DataAgent")
                    .successExpression(false).spendTime(5000L).build();

            when(svc.getFailedCalls("req-001")).thenReturn(List.of(failedCall));

            var resp = controller(svc).getFailedCalls("req-001");

            assertThat(resp.getCode()).isEqualTo(0);
            assertThat(resp.getData()).hasSize(1);
            assertThat(resp.getData().get(0).getSuccessExpression()).isFalse();
        }
    }

    // ─── TC-F4-05 & TC-F4-06: 获取调用详情 ────────────────────────────────────

    @Nested
    class GetCallDetail {

        @Test
        @DisplayName("GET /api/v1/llm-calls/{id}/detail returns full call detail")
        void testGetCallDetail() {
            LlmCallAnalysisService svc = mock(LlmCallAnalysisService.class);
            LlmCallDetailDTO detail = LlmCallDetailDTO.builder()
                    .id(1L).agent("DataAgent").templateName("analyze_prompt")
                    .modelType("gpt-4o").spendTime(1500L)
                    .promptTokens(500).completionTokens(300)
                    .successExpression(true)
                    .requestBody("{\"messages\":[{\"role\":\"user\",\"content\":\"Analyze...\"}]}")
                    .requestUrl("https://api.openai.com/v1/chat/completions")
                    .responseBody("{\"choices\":[{\"message\":{\"content\":\"Analysis complete.\"}}]}")
                    .createTime(LocalDateTime.of(2025, 1, 15, 10, 0))
                    .build();

            when(svc.getCallDetail(1L)).thenReturn(detail);

            var resp = controller(svc).getCallDetail(1L);

            assertThat(resp.getCode()).isEqualTo(0);
            assertThat(resp.getData().getId()).isEqualTo(1L);
            assertThat(resp.getData().getRequestBody()).isNotEmpty();
            assertThat(resp.getData().getResponseBody()).isNotEmpty();
        }

        @Test
        @DisplayName("GET /api/v1/llm-calls/999/detail returns 404 for non-existent call")
        void testCallNotFound() {
            LlmCallAnalysisService svc = mock(LlmCallAnalysisService.class);
            when(svc.getCallDetail(999L)).thenReturn(null);

            var resp = controller(svc).getCallDetail(999L);

            assertThat(resp.getCode()).isEqualTo(404);
            assertThat(resp.getMessage()).isEqualTo("LLM 调用记录不存在: 999");
        }
    }
}
