package com.llm.insight.explorer.controller;

import com.llm.insight.explorer.ai.AiChatService;
import com.llm.insight.explorer.ai.AiChatService.AiColumnAnalysis;
import com.llm.insight.explorer.ai.AiChatService.NlQueryResult;
import com.llm.insight.explorer.ai.AiProperties;
import com.llm.insight.explorer.engine.ColumnAnalyzerService.AnalyzedColumn;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AiAnalyzerController}.
 * Covers TC-AI series (TC-AI-01 ~ TC-AI-06).
 */
class AiControllerTest {

    private AiAnalyzerController controller(AiChatService aiChatService, AiProperties aiProps) {
        return new AiAnalyzerController(aiChatService, aiProps);
    }

    private static AiColumnAnalysis analysis(String columnName, String renderType) {
        AiColumnAnalysis a = new AiColumnAnalysis();
        a.setColumnName(columnName);
        a.setRecommendedRenderType(renderType);
        a.setReason("Test reason");
        a.setSuggestedDisplayName("Display " + columnName);
        return a;
    }

    private static NlQueryResult nlResult(String query, String column, String operator, String value) {
        NlQueryResult r = new NlQueryResult();
        r.setTranslatedText("Translated: " + query);
        NlQueryResult.FilterDto f = new NlQueryResult.FilterDto();
        f.setColumn(column);
        f.setOperator(operator);
        f.setValue(value);
        r.setFilters(List.of(f));
        return r;
    }

    // ─── TC-AI-01: AI 功能默认关闭 ───────────────────────────────────────────

    @Nested
    class AiDisabled {

        @Test
        @DisplayName("AI status endpoint returns current enabled state")
        void testAiDisabledWhenGloballyDisabled() {
            AiProperties aiProps = mock(AiProperties.class);
            when(aiProps.isEnabled()).thenReturn(false);

            var resp = controller(null, aiProps).status();

            assertThat(resp.getCode()).isEqualTo(0);
            assertThat(resp.getData().isEnabled()).isFalse();
        }

        @Test
        @DisplayName("POST /nl-query returns error when AI is disabled")
        void testNlQueryWhenDisabled() {
            AiChatService aiChatService = mock(AiChatService.class);
            AiProperties aiProps = mock(AiProperties.class);
            when(aiProps.isNlQueryEnabled()).thenReturn(false);

            var resp = controller(aiChatService, aiProps)
                    .nlQuery("show all orders", List.of());

            assertThat(resp.getCode()).isEqualTo(-1);
            assertThat(resp.getMessage()).isEqualTo("自然语言查询未启用");
        }

        @Test
        @DisplayName("POST /summarize returns error when summarization is disabled")
        void testSummarizeWhenDisabled() {
            AiChatService aiChatService = mock(AiChatService.class);
            AiProperties aiProps = mock(AiProperties.class);
            when(aiProps.isSummarizationEnabled()).thenReturn(false);

            var resp = controller(aiChatService, aiProps)
                    .summarize(java.util.Map.of("rows", List.of(), "columns", List.of()));

            assertThat(resp.getCode()).isEqualTo(-1);
            assertThat(resp.getMessage()).isEqualTo("结果摘要未启用");
        }

        @Test
        @DisplayName("POST /chat returns error when AI is globally disabled")
        void testChatWhenDisabled() {
            AiChatService aiChatService = mock(AiChatService.class);
            AiProperties aiProps = mock(AiProperties.class);
            when(aiProps.isEnabled()).thenReturn(false);

            var resp = controller(aiChatService, aiProps)
                    .chat(java.util.Map.of("message", "Hello"));

            assertThat(resp.getCode()).isEqualTo(-1);
            assertThat(resp.getMessage()).isEqualTo("AI 功能未启用");
        }
    }

    // ─── TC-AI-02: AI 列分析（启用）──────────────────────────────────────────

    @Nested
    class AnalyzeColumn {

        @Test
        @DisplayName("POST /api/v1/explorer/ai/analyze/column returns AI column analysis")
        void testAnalyzeColumn() {
            AiChatService aiChatService = mock(AiChatService.class);
            AiProperties aiProps = mock(AiProperties.class);

            AiColumnAnalysis analysis = analysis("user_name", "text");

            when(aiChatService.analyzeColumnWithAi(
                    eq("user_name"), eq("用户名"), eq("VARCHAR"),
                    eq("text"), anyList(), anyList()))
                    .thenReturn(analysis);

            var resp = controller(aiChatService, aiProps)
                    .analyzeColumn("user_name", "用户名", "VARCHAR", "text", null);

            assertThat(resp.getCode()).isEqualTo(0);
            assertThat(resp.getData().getColumnName()).isEqualTo("user_name");
            assertThat(resp.getData().getRecommendedRenderType()).isEqualTo("text");
        }

        @Test
        @DisplayName("POST /api/v1/explorer/ai/analyze/batch returns batch analysis results")
        void testBatchAnalyze() {
            AiChatService aiChatService = mock(AiChatService.class);
            AiProperties aiProps = mock(AiProperties.class);
            when(aiProps.isColumnAnalysisEnabled()).thenReturn(true);

            AiColumnAnalysis result1 = analysis("id", "text");
            AiColumnAnalysis result2 = analysis("email", "link");

            when(aiChatService.analyzeColumnWithAi(anyString(), anyString(), anyString(),
                    anyString(), anyList(), anyList()))
                    .thenReturn(result1)
                    .thenReturn(result2);

            List<AnalyzedColumn> columns = List.of(
                    AnalyzedColumn.builder().columnName("id").displayName("ID")
                            .dataType("BIGINT").renderType("text").build(),
                    AnalyzedColumn.builder().columnName("email").displayName("邮箱")
                            .dataType("VARCHAR").renderType("text").build()
            );

            var resp = controller(aiChatService, aiProps).batchAnalyze(columns);

            assertThat(resp.getCode()).isEqualTo(0);
            assertThat(resp.getData()).hasSize(2);
            assertThat(resp.getData().get(0).getColumnName()).isEqualTo("id");
            assertThat(resp.getData().get(1).getColumnName()).isEqualTo("email");
        }

        @Test
        @DisplayName("POST /api/v1/explorer/ai/analyze/batch returns error when column analysis disabled")
        void testBatchAnalyzeWhenDisabled() {
            AiChatService aiChatService = mock(AiChatService.class);
            AiProperties aiProps = mock(AiProperties.class);
            when(aiProps.isColumnAnalysisEnabled()).thenReturn(false);

            var resp = controller(aiChatService, aiProps).batchAnalyze(List.of());

            assertThat(resp.getCode()).isEqualTo(-1);
            assertThat(resp.getMessage()).isEqualTo("AI 列分析未启用，请在配置中开启");
        }
    }

    // ─── TC-AI-03: 自然语言查询 ─────────────────────────────────────────────

    @Nested
    class NlQuery {

        @Test
        @DisplayName("POST /api/v1/explorer/ai/nl-query translates natural language to filters")
        void testNlQuery() {
            AiChatService aiChatService = mock(AiChatService.class);
            AiProperties aiProps = mock(AiProperties.class);
            when(aiProps.isNlQueryEnabled()).thenReturn(true);

            NlQueryResult result = nlResult("show all orders over 1000 yuan", "amount", "GT", "1000");

            when(aiChatService.translateToFilters(eq("show all orders over 1000 yuan"), anyList()))
                    .thenReturn(result);

            List<AnalyzedColumn> columns = List.of(
                    AnalyzedColumn.builder().columnName("amount").displayName("金额")
                            .dataType("DECIMAL").renderType("number").build()
            );

            var resp = controller(aiChatService, aiProps)
                    .nlQuery("show all orders over 1000 yuan", columns);

            assertThat(resp.getCode()).isEqualTo(0);
            assertThat(resp.getData().getTranslatedText()).contains("show all orders over 1000 yuan");
            assertThat(resp.getData().getFilters()).hasSize(1);
            assertThat(resp.getData().getFilters().get(0).getColumn()).isEqualTo("amount");
            assertThat(resp.getData().getFilters().get(0).getOperator()).isEqualTo("GT");
        }

        @Test
        @DisplayName("POST /api/v1/explorer/ai/nl-query returns error when disabled")
        void testNlQueryDisabled() {
            AiChatService aiChatService = mock(AiChatService.class);
            AiProperties aiProps = mock(AiProperties.class);
            when(aiProps.isNlQueryEnabled()).thenReturn(false);

            var resp = controller(aiChatService, aiProps).nlQuery("show orders", List.of());

            assertThat(resp.getCode()).isEqualTo(-1);
        }
    }

    // ─── TC-AI-04: AI Provider 切换 ────────────────────────────────────────

    @Nested
    class AiProviderSwitch {

        @Test
        @DisplayName("AI status endpoint returns current provider configuration")
        void testAiStatusWithProvider() {
            AiProperties aiProps = mock(AiProperties.class);
            when(aiProps.getProvider()).thenReturn("deepseek");
            when(aiProps.getDefaultModel()).thenReturn("deepseek-chat");
            when(aiProps.isEnabled()).thenReturn(true);
            when(aiProps.isColumnAnalysisEnabled()).thenReturn(true);
            when(aiProps.isNlQueryEnabled()).thenReturn(true);
            when(aiProps.isSummarizationEnabled()).thenReturn(true);

            var resp = controller(null, aiProps).status();

            assertThat(resp.getCode()).isEqualTo(0);
            assertThat(resp.getData().getProvider()).isEqualTo("deepseek");
            assertThat(resp.getData().getDefaultModel()).isEqualTo("deepseek-chat");
        }
    }

    // ─── TC-AI-05: AI 调用超时 ──────────────────────────────────────────────

    @Nested
    class AiTimeout {

        @Test
        @DisplayName("AI column analysis handles timeout gracefully")
        void testAnalyzeColumnTimeout() {
            AiChatService aiChatService = mock(AiChatService.class);
            AiProperties aiProps = mock(AiProperties.class);

            when(aiChatService.analyzeColumnWithAi(anyString(), anyString(), anyString(),
                    anyString(), anyList(), anyList()))
                    .thenThrow(new RuntimeException("AI 调用超时"));

            assertThatThrownBy(() -> controller(aiChatService, aiProps)
                    .analyzeColumn("test_col", "Test", "VARCHAR", "text", null))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("AI 调用超时");
        }
    }

    // ─── TC-AI-06: AI API Key 错误 ──────────────────────────────────────────

    @Nested
    class AiAuthError {

        @Test
        @DisplayName("AI chat returns error when API key is invalid")
        void testChatWithInvalidKey() {
            AiChatService aiChatService = mock(AiChatService.class);
            AiProperties aiProps = mock(AiProperties.class);
            when(aiProps.isEnabled()).thenReturn(true);

            when(aiChatService.chat(anyString(), eq("Hello")))
                    .thenThrow(new RuntimeException("Authentication error: Invalid API key"));

            assertThatThrownBy(() -> controller(aiChatService, aiProps)
                    .chat(java.util.Map.of("message", "Hello")))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Authentication error");
        }
    }
}
