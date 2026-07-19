package com.llm.insight.explorer.ai;

import com.llm.insight.explorer.engine.ColumnAnalyzerService.AnalyzedColumn;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the rewritten {@link AiChatService}.
 *
 * <p>The new constructor signature is:
 * <pre>
 *   AiChatService(AiProperties, AiModelRouteService, DynamicChatModelFactory, ChatClientCache)
 * </pre>
 *
 * <p>Database-driven: when {@code AI_ENABLED=true} and the route service
 * resolves a vendor, the factory must be invoked. When AI is disabled, no
 * upstream call happens.
 */
class AiChatServiceRefactorTest {

    private AiProperties props;
    private AiModelRouteService routeService;
    private DynamicChatModelFactory factory;
    private DynamicChatClientCache cache;

    @BeforeEach
    void setUp() {
        props = new AiProperties();
        props.setEnabled(false);
        props.setProvider("openai");
        props.setDefaultModel("gpt-4o");

        routeService = mock(AiModelRouteService.class);
        factory = mock(DynamicChatModelFactory.class);
        cache = new DynamicChatClientCache();
    }

    private AiChatService service() {
        return new AiChatService(props, routeService, factory, cache);
    }

    @Test
    @DisplayName("disabled → returns friendly notice; no factory call")
    void disabledShortCircuits() {
        props.setEnabled(false);
        String r = service().chat("system", "hello");
        assertThat(r).contains("AI 功能未启用");
        verify(factory, never()).create(any());
    }

    @Test
    @DisplayName("disabled → chatForJson returns null; no factory call")
    void jsonShortCircuits() {
        props.setEnabled(false);
        Object r = service().chatForJson("system", "{}", Map.class);
        assertThat(r).isNull();
        verify(factory, never()).create(any());
    }

    @Test
    @DisplayName("enabled but no route → returns error notice; no factory call")
    void enabledButNoRoute() {
        props.setEnabled(true);
        when(routeService.resolve("CHAT", "PRODUCTION")).thenReturn(java.util.Optional.empty());

        String r = service().chat("system", "hi");
        assertThat(r).contains("数据库中未配置可用 AI 模型");
        verify(factory, never()).create(any());
    }

    @Test
    @DisplayName("enabled + route found → factory.create is invoked")
    void enabledWithRoute() {
        props.setEnabled(true);

        var vendor = new com.llm.insight.repository.entity.InsightAiVendor();
        vendor.setId(1L);
        vendor.setVendor("openai");
        vendor.setTokenEncrypted("ignored".getBytes());
        var inst = new com.llm.insight.repository.entity.InsightModelInstance();
        inst.setId(10L);
        inst.setVendorId(1L);
        inst.setModelName("gpt-4o");
        inst.setCapability("CHAT");
        inst.setTier("PRODUCTION");
        when(routeService.resolve("CHAT", "PRODUCTION"))
                .thenReturn(java.util.Optional.of(new AiModelRouteService.RouteResult(vendor, inst)));

        var chatModel = mock(org.springframework.ai.chat.model.ChatModel.class);
        when(factory.create(any())).thenReturn(chatModel);
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class)))
                .thenReturn(mock(org.springframework.ai.chat.model.ChatResponse.class));

        // We don't care about the actual response text here — that needs real
        // Spring AI plumbing. We only verify that the routing + factory path
        // was actually attempted.
        try {
            service().chat("system", "hello");
        } catch (Exception ignored) {
            // Mockito stubbing of ChatResponse may not be perfect; we only assert
            // that the factory was hit before any NPE.
        }
        verify(factory).create(any());
    }

    @Test
    @DisplayName("analyzeColumnWithAi respects columnAnalysisEnabled flag")
    void columnAnalysisDisabled() {
        props.setEnabled(true);
        props.setColumnAnalysisEnabled(false);
        Object r = service().analyzeColumnWithAi(
                "c", "c", "STRING", "TEXT",
                List.of("a"), List.of());
        assertThat(r).isNull();
    }

    @Test
    @DisplayName("translateToFilters respects nlQueryEnabled flag")
    void nlQueryDisabled() {
        props.setEnabled(true);
        props.setNlQueryEnabled(false);
        Object r = service().translateToFilters(
                "test", List.of(new AnalyzedColumn()));
        assertThat(r).isNull();
    }

    @Test
    @DisplayName("summarizeResults returns null when summarization disabled")
    void summarizeDisabled() {
        props.setEnabled(true);
        props.setSummarizationEnabled(false);
        String r = service().summarizeResults(
                List.of(Map.of("a", 1)), List.of(), "table", "q");
        assertThat(r).isNull();
    }

    @Test
    @DisplayName("summarizeResults returns null for empty rows")
    void summarizeEmpty() {
        props.setEnabled(true);
        props.setSummarizationEnabled(true);
        String r = service().summarizeResults(List.of(), List.of(), "table", "q");
        assertThat(r).isNull();
    }
}
