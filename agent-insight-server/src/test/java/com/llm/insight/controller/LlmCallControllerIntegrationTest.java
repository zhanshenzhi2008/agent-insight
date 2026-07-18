package com.llm.insight.controller;

import com.llm.insight.dto.response.LlmCallDTO;
import com.llm.insight.dto.response.LlmCallDetailDTO;
import com.llm.insight.dto.response.TokenUsageDTO;
import com.llm.insight.service.LlmCallAnalysisService;
import com.llm.insight.support.BaseWebMvcTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@link LlmCallController}.
 *
 * <p>Mirrors {@code TraceControllerIntegrationTest}: uses
 * {@code MockMvcBuilders.standaloneSetup} via {@link BaseWebMvcTest} to exercise
 * the real Spring MVC serialization layer (Jackson, UTF-8, {@code @PathVariable}
 * type conversion, {@code @RequestParam} defaults, {@code @RestControllerAdvice}).
 * The pure Mockito {@code LlmCallControllerTest} misses all of these concerns.
 *
 * <p>Test IDs use {@code TC-F4-INT} prefix to distinguish from the unit-test
 * {@code TC-F4} series.
 */
@DisplayName("LlmCallController integration (MockMvc standaloneSetup)")
class LlmCallControllerIntegrationTest extends BaseWebMvcTest {

    private final LlmCallAnalysisService llmCallAnalysisService = mock(LlmCallAnalysisService.class);

    @Override
    protected Object[] controllerOrAdvice() {
        return new Object[]{new LlmCallController(llmCallAnalysisService)};
    }

    @Override
    protected Object[] getControllerAdvices() {
        return new Object[]{new GlobalExceptionHandler()};
    }

    // ─── TC-F4-INT-01: listLlmCalls ────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/requests/{requestId}/llm-calls")
    class ListLlmCalls {

        @Test
        @DisplayName("returns 200 with envelope + LLM call list")
        void returnsCallList() throws Exception {
            LlmCallDTO call = LlmCallDTO.builder()
                    .id(1L).requestId("req-001").agent("DataAgent")
                    .templateName("analyze_prompt").planUniqueName("plan_analyze")
                    .modelType("gpt-4o").spendTime(1500L)
                    .promptTokens(500).completionTokens(300).totalTokens(800)
                    .successExpression(true)
                    .requestBodyPreview("Analyze the data...")
                    .responseBodyPreview("Analysis complete.")
                    .createTime(LocalDateTime.of(2026, 7, 1, 10, 0, 0))
                    .build();
            when(llmCallAnalysisService.listLlmCalls("req-001")).thenReturn(List.of(call));

            mockMvc.perform(get("/api/v1/requests/{requestId}/llm-calls", "req-001")
                            .accept("application/json"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith("application/json"))
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.message").value("success"))
                    .andExpect(jsonPath("$.data", org.hamcrest.Matchers.hasSize(1)))
                    .andExpect(jsonPath("$.data[0].id").value(1))
                    .andExpect(jsonPath("$.data[0].requestId").value("req-001"))
                    .andExpect(jsonPath("$.data[0].agent").value("DataAgent"))
                    .andExpect(jsonPath("$.data[0].modelType").value("gpt-4o"))
                    .andExpect(jsonPath("$.data[0].totalTokens").value(800))
                    .andExpect(jsonPath("$.data[0].successExpression").value(true))
                    .andExpect(jsonPath("$.data[0].createTime").exists());

            verify(llmCallAnalysisService).listLlmCalls("req-001");
        }

        @Test
        @DisplayName("returns empty data array when no calls")
        void returnsEmptyWhenNoCalls() throws Exception {
            when(llmCallAnalysisService.listLlmCalls("req-empty")).thenReturn(List.of());

            mockMvc.perform(get("/api/v1/requests/{requestId}/llm-calls", "req-empty")
                            .accept("application/json"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data").isEmpty());
        }
    }

    // ─── TC-F4-INT-02: getSlowCalls ────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/requests/{requestId}/llm-calls/slow")
    class GetSlowCalls {

        @Test
        @DisplayName("returns slow calls honoring topN=5")
        void returnsSlowCallsWithTopN() throws Exception {
            LlmCallDTO slow = LlmCallDTO.builder()
                    .id(5L).requestId("req-001").agent("DataAgent")
                    .spendTime(10000L).totalTokens(2000).successExpression(true).build();
            when(llmCallAnalysisService.getSlowCalls("req-001", 5)).thenReturn(List.of(slow));

            mockMvc.perform(get("/api/v1/requests/{requestId}/llm-calls/slow", "req-001")
                            .param("topN", "5")
                            .accept("application/json"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data[0].spendTime").value(10000))
                    .andExpect(jsonPath("$.data[0].totalTokens").value(2000));

            verify(llmCallAnalysisService).getSlowCalls("req-001", 5);
        }

        @Test
        @DisplayName("defaults topN=10 when query param is omitted")
        void defaultsTopNToTen() throws Exception {
            when(llmCallAnalysisService.getSlowCalls("req-001", 10)).thenReturn(List.of());

            mockMvc.perform(get("/api/v1/requests/{requestId}/llm-calls/slow", "req-001")
                            .accept("application/json"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data").isEmpty());

            verify(llmCallAnalysisService).getSlowCalls("req-001", 10);
        }

        @Test
        @DisplayName("non-numeric topN → 400 (type mismatch)")
        void rejectsNonNumericTopN() throws Exception {
            mockMvc.perform(get("/api/v1/requests/{requestId}/llm-calls/slow", "req-001")
                            .param("topN", "not-a-number")
                            .accept("application/json"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400));

            verify(llmCallAnalysisService, never()).getSlowCalls(any(String.class), anyInt());
        }
    }

    // ─── TC-F4-INT-03: getTokenUsage ───────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/requests/{requestId}/llm-calls/usage")
    class GetTokenUsage {

        @Test
        @DisplayName("returns token usage statistics")
        void returnsUsage() throws Exception {
            TokenUsageDTO usage = TokenUsageDTO.builder()
                    .totalPromptTokens(5000).totalCompletionTokens(2500).totalTokens(7500)
                    .callCount(10).build();
            when(llmCallAnalysisService.getTokenUsage("req-001")).thenReturn(usage);

            mockMvc.perform(get("/api/v1/requests/{requestId}/llm-calls/usage", "req-001")
                            .accept("application/json"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.totalPromptTokens").value(5000))
                    .andExpect(jsonPath("$.data.totalCompletionTokens").value(2500))
                    .andExpect(jsonPath("$.data.totalTokens").value(7500))
                    .andExpect(jsonPath("$.data.callCount").value(10));

            verify(llmCallAnalysisService).getTokenUsage("req-001");
        }

        @Test
        @DisplayName("returns envelope with null data when service returns null")
        void returnsNullWhenMissing() throws Exception {
            when(llmCallAnalysisService.getTokenUsage("req-empty")).thenReturn(null);

            mockMvc.perform(get("/api/v1/requests/{requestId}/llm-calls/usage", "req-empty")
                            .accept("application/json"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data").doesNotExist());
        }
    }

    // ─── TC-F4-INT-04: getFailedCalls ──────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/requests/{requestId}/llm-calls/failed")
    class GetFailedCalls {

        @Test
        @DisplayName("returns only failed calls (successExpression=false)")
        void returnsFailedCalls() throws Exception {
            LlmCallDTO failed = LlmCallDTO.builder()
                    .id(3L).requestId("req-001").agent("DataAgent")
                    .successExpression(false).spendTime(5000L).build();
            when(llmCallAnalysisService.getFailedCalls("req-001")).thenReturn(List.of(failed));

            mockMvc.perform(get("/api/v1/requests/{requestId}/llm-calls/failed", "req-001")
                            .accept("application/json"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data", org.hamcrest.Matchers.hasSize(1)))
                    .andExpect(jsonPath("$.data[0].successExpression").value(false))
                    .andExpect(jsonPath("$.data[0].spendTime").value(5000));

            verify(llmCallAnalysisService).getFailedCalls("req-001");
        }

        @Test
        @DisplayName("returns empty array when no failures")
        void returnsEmptyWhenNoFailures() throws Exception {
            when(llmCallAnalysisService.getFailedCalls("req-ok")).thenReturn(List.of());

            mockMvc.perform(get("/api/v1/requests/{requestId}/llm-calls/failed", "req-ok")
                            .accept("application/json"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data").isEmpty());
        }
    }

    // ─── TC-F4-INT-05: getCallDetail ───────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/llm-calls/{callId}/detail")
    class GetCallDetail {

        @Test
        @DisplayName("binds Long path variable and serializes full detail")
        void returnsDetailByLongId() throws Exception {
            LlmCallDetailDTO detail = LlmCallDetailDTO.builder()
                    .id(1L).agent("DataAgent").templateName("analyze_prompt")
                    .modelType("gpt-4o").spendTime(1500L)
                    .promptTokens(500).completionTokens(300)
                    .successExpression(true)
                    .requestBody("{\"messages\":[{\"role\":\"user\",\"content\":\"Analyze...\"}]}")
                    .requestUrl("https://api.openai.com/v1/chat/completions")
                    .responseBody("{\"choices\":[{\"message\":{\"content\":\"Analysis complete.\"}}]}")
                    .createTime(LocalDateTime.of(2026, 7, 1, 10, 0, 0))
                    .build();
            when(llmCallAnalysisService.getCallDetail(1L)).thenReturn(detail);

            mockMvc.perform(get("/api/v1/llm-calls/{callId}/detail", 1L)
                            .accept("application/json"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.id").value(1))
                    .andExpect(jsonPath("$.data.agent").value("DataAgent"))
                    .andExpect(jsonPath("$.data.modelType").value("gpt-4o"))
                    .andExpect(jsonPath("$.data.requestUrl").value("https://api.openai.com/v1/chat/completions"))
                    .andExpect(jsonPath("$.data.requestBody", containsString("messages")))
                    .andExpect(jsonPath("$.data.responseBody", containsString("choices")))
                    .andExpect(jsonPath("$.data.createTime").exists());

            verify(llmCallAnalysisService).getCallDetail(1L);
        }

        @Test
        @DisplayName("returns {code:404, message:…} envelope when detail not found")
        void returns404EnvelopeWhenMissing() throws Exception {
            when(llmCallAnalysisService.getCallDetail(999L)).thenReturn(null);

            mockMvc.perform(get("/api/v1/llm-calls/{callId}/detail", 999L)
                            .accept("application/json"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(404))
                    .andExpect(jsonPath("$.message").value("LLM 调用记录不存在: 999"))
                    .andExpect(jsonPath("$.data").doesNotExist());

            verify(llmCallAnalysisService).getCallDetail(999L);
        }

        @Test
        @DisplayName("non-numeric callId → 400 (type mismatch)")
        void rejectsNonNumericCallId() throws Exception {
            mockMvc.perform(get("/api/v1/llm-calls/{callId}/detail", "abc")
                            .accept("application/json"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400));

            verify(llmCallAnalysisService, never()).getCallDetail(anyLong());
        }
    }

    // ─── TC-F4-INT-06: error envelope ──────────────────────────────────────────

    @Nested
    @DisplayName("error handling")
    class ErrorEnvelope {

        @Test
        @DisplayName("IllegalArgumentException → 400 envelope")
        void mapsIllegalArgumentTo400() throws Exception {
            when(llmCallAnalysisService.listLlmCalls(eq("bad")))
                    .thenThrow(new IllegalArgumentException("requestId 不能为空"));

            mockMvc.perform(get("/api/v1/requests/{requestId}/llm-calls", "bad")
                            .accept("application/json"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value("requestId 不能为空"))
                    .andExpect(jsonPath("$.data").doesNotExist());
        }

        @Test
        @DisplayName("RuntimeException → 500 envelope")
        void mapsUnexpectedTo500() throws Exception {
            when(llmCallAnalysisService.getSlowCalls(eq("boom"), eq(10)))
                    .thenThrow(new RuntimeException("Mongo 连接超时"));

            mockMvc.perform(get("/api/v1/requests/{requestId}/llm-calls/slow", "boom")
                            .accept("application/json"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code").value(500))
                    .andExpect(jsonPath("$.message", containsString("Mongo 连接超时")));
        }
    }
}