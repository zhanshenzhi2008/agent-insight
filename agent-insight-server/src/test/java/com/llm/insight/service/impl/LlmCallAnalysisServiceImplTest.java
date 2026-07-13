package com.llm.insight.service.impl;

import com.llm.insight.dto.response.LlmCallDTO;
import com.llm.insight.dto.response.LlmCallDetailDTO;
import com.llm.insight.dto.response.TokenUsageDTO;
import com.llm.insight.repository.LogLlmHttpRequestRepository;
import com.llm.insight.repository.entity.LogLlmHttpRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link LlmCallAnalysisServiceImpl}.
 *
 * Verifies:
 *  - listLlmCalls streams all requests for a requestId.
 *  - getSlowCalls returns top-N by spendTime desc.
 *  - getTokenUsage aggregates token counts correctly.
 *  - getFailedCalls returns only failed requests.
 *  - getCallDetail returns full DTO when found, null when not.
 *  - toDTO truncates long bodies at 200 chars.
 */
@ExtendWith(MockitoExtension.class)
class LlmCallAnalysisServiceImplTest {

    @Mock
    private LogLlmHttpRequestRepository httpRequestRepository;

    private LlmCallAnalysisServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new LlmCallAnalysisServiceImpl(httpRequestRepository);
    }

    // ─── listLlmCalls ─────────────────────────────────────────────────────────

    @Nested
    class ListLlmCalls {

        @Test
        @DisplayName("returns empty list when no calls found")
        void emptyList() {
            when(httpRequestRepository.findByRequestId("unknown")).thenReturn(List.of());

            List<LlmCallDTO> result = service.listLlmCalls("unknown");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("maps all entity fields to DTO")
        void mapsFieldsCorrectly() {
            LogLlmHttpRequest req = httpRequest(1L, "req-list", "AgentX", "template-1",
                    "gpt-4o", 1500L, 500, 200, true);

            when(httpRequestRepository.findByRequestId("req-list")).thenReturn(List.of(req));

            List<LlmCallDTO> result = service.listLlmCalls("req-list");

            assertThat(result).hasSize(1);
            LlmCallDTO dto = result.get(0);
            assertThat(dto.getId()).isEqualTo(1L);
            assertThat(dto.getAgent()).isEqualTo("AgentX");
            assertThat(dto.getTemplateName()).isEqualTo("template-1");
            assertThat(dto.getModelType()).isEqualTo("gpt-4o");
            assertThat(dto.getSpendTime()).isEqualTo(1500L);
            assertThat(dto.getPromptTokens()).isEqualTo(500);
            assertThat(dto.getCompletionTokens()).isEqualTo(200);
            assertThat(dto.getTotalTokens()).isEqualTo(700);
            assertThat(dto.getSuccessExpression()).isTrue();
            assertThat(dto.getRequestBodyPreview()).isEqualTo("short body");
            assertThat(dto.getResponseBodyPreview()).isEqualTo("short response");
        }

        @Test
        @DisplayName("truncates requestBody and responseBody at 200 characters")
        void truncatesLongBodies() {
            String longBody = "x".repeat(300);
            LogLlmHttpRequest req = httpRequest(2L, "req-trunc", "A", "t",
                    "m", 100L, 1, 1, true);
            req.setRequestBody(longBody);
            req.setResponseBody(longBody);

            when(httpRequestRepository.findByRequestId("req-trunc")).thenReturn(List.of(req));

            LlmCallDTO dto = service.listLlmCalls("req-trunc").get(0);

            assertThat(dto.getRequestBodyPreview()).hasSize(203); // 200 + "..."
            assertThat(dto.getRequestBodyPreview()).endsWith("...");
            assertThat(dto.getResponseBodyPreview()).hasSize(203);
        }

        @Test
        @DisplayName("treats null tokens as zero in total calculation")
        void nullTokensAsZero() {
            LogLlmHttpRequest req = httpRequest(3L, "req-null", "A", "t", "m", 100L, null, null, true);

            when(httpRequestRepository.findByRequestId("req-null")).thenReturn(List.of(req));

            LlmCallDTO dto = service.listLlmCalls("req-null").get(0);

            assertThat(dto.getTotalTokens()).isEqualTo(0);
        }
    }

    // ─── getSlowCalls ─────────────────────────────────────────────────────────

    @Nested
    class GetSlowCalls {

        @Test
        @DisplayName("delegates to repository with correct topN and PageRequest")
        void delegatesWithCorrectParams() {
            when(httpRequestRepository.findTopByRequestIdOrderBySpendTimeDesc(
                    eq("req-slow"), any(PageRequest.class)))
                    .thenReturn(List.of());

            service.getSlowCalls("req-slow", 5);

            verify(httpRequestRepository).findTopByRequestIdOrderBySpendTimeDesc(
                    eq("req-slow"),
                    eq(PageRequest.of(0, 5)));
        }

        @Test
        @DisplayName("maps result correctly")
        void mapsCorrectly() {
            LogLlmHttpRequest req = httpRequest(10L, "req-slow-2", "SlowAgent",
                    "slow-template", "claude-3", 30000L, 800, 400, false);

            when(httpRequestRepository.findTopByRequestIdOrderBySpendTimeDesc(
                    eq("req-slow-2"), any(PageRequest.class)))
                    .thenReturn(List.of(req));

            List<LlmCallDTO> result = service.getSlowCalls("req-slow-2", 3);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getSpendTime()).isEqualTo(30000L);
            assertThat(result.get(0).getTotalTokens()).isEqualTo(1200);
        }
    }

    // ─── getTokenUsage ────────────────────────────────────────────────────────

    @Nested
    class GetTokenUsage {

        @Test
        @DisplayName("aggregates prompt, completion tokens and call count")
        void aggregatesCorrectly() {
            when(httpRequestRepository.sumPromptTokensByRequestId("req-tokens"))
                    .thenReturn(5000);
            when(httpRequestRepository.sumCompletionTokensByRequestId("req-tokens"))
                    .thenReturn(2000);
            when(httpRequestRepository.countByRequestId("req-tokens"))
                    .thenReturn(10L);

            TokenUsageDTO result = service.getTokenUsage("req-tokens");

            assertThat(result.getTotalPromptTokens()).isEqualTo(5000);
            assertThat(result.getTotalCompletionTokens()).isEqualTo(2000);
            assertThat(result.getTotalTokens()).isEqualTo(7000);
            assertThat(result.getCallCount()).isEqualTo(10);
        }

        @Test
        @DisplayName("returns zero counts when repository returns null/zero")
        void zeroOnEmpty() {
            when(httpRequestRepository.sumPromptTokensByRequestId("req-empty")).thenReturn(0);
            when(httpRequestRepository.sumCompletionTokensByRequestId("req-empty")).thenReturn(0);
            when(httpRequestRepository.countByRequestId("req-empty")).thenReturn(0L);

            TokenUsageDTO result = service.getTokenUsage("req-empty");

            assertThat(result.getTotalTokens()).isZero();
            assertThat(result.getCallCount()).isZero();
        }
    }

    // ─── getFailedCalls ───────────────────────────────────────────────────────

    @Nested
    class GetFailedCalls {

        @Test
        @DisplayName("filters failed calls and maps to DTOs")
        void filtersFailed() {
            LogLlmHttpRequest failed = httpRequest(20L, "req-fail", "AgentY",
                    "t-fail", "gpt-4", 200L, 100, 50, false);

            when(httpRequestRepository.findByRequestIdAndSuccessExpressionFalse("req-fail"))
                    .thenReturn(List.of(failed));

            List<LlmCallDTO> result = service.getFailedCalls("req-fail");

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getSuccessExpression()).isFalse();
            assertThat(result.get(0).getAgent()).isEqualTo("AgentY");
        }
    }

    // ─── getCallDetail ───────────────────────────────────────────────────────

    @Nested
    class GetCallDetail {

        @Test
        @DisplayName("returns full detail DTO when record exists")
        void returnsDetailWhenFound() {
            LogLlmHttpRequest req = httpRequest(30L, "req-detail", "AgentZ",
                    "detail-template", "deepseek-chat", 500L, 300, 150, true);
            req.setRequestUrl("https://api.deepseek.com/v1/chat");
            req.setRequestBody("full request body here");
            req.setResponseBody("full response body here");

            when(httpRequestRepository.findById(30L)).thenReturn(Optional.of(req));

            LlmCallDetailDTO result = service.getCallDetail(30L);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(30L);
            assertThat(result.getAgent()).isEqualTo("AgentZ");
            assertThat(result.getModelType()).isEqualTo("deepseek-chat");
            assertThat(result.getRequestBody()).isEqualTo("full request body here");
            assertThat(result.getResponseBody()).isEqualTo("full response body here");
            assertThat(result.getRequestUrl()).isEqualTo("https://api.deepseek.com/v1/chat");
            assertThat(result.getPromptTokens()).isEqualTo(300);
            assertThat(result.getCompletionTokens()).isEqualTo(150);
        }

        @Test
        @DisplayName("returns null when record not found")
        void returnsNullWhenNotFound() {
            when(httpRequestRepository.findById(999L)).thenReturn(Optional.empty());

            LlmCallDetailDTO result = service.getCallDetail(999L);

            assertThat(result).isNull();
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private LogLlmHttpRequest httpRequest(Long id, String requestId, String agent,
                                          String template, String model, Long spendTime,
                                          Integer promptTokens, Integer completionTokens,
                                          Boolean success) {
        LogLlmHttpRequest r = new LogLlmHttpRequest();
        r.setId(id);
        r.setRequestId(requestId);
        r.setAgent(agent);
        r.setTemplateName(template);
        r.setPlanUniqueName("plan-001");
        r.setModelType(model);
        r.setSpendTime(spendTime);
        r.setPromptTokens(promptTokens);
        r.setCompletionTokens(completionTokens);
        r.setSuccessExpression(success);
        r.setRequestBody("short body");
        r.setResponseBody("short response");
        r.setCreateTime(LocalDateTime.of(2025, 1, 1, 0, 0));
        return r;
    }
}
