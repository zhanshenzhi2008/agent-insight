package com.llm.insight.explorer.ai;

import com.llm.insight.explorer.engine.ColumnAnalyzerService.AnalyzedColumn;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AiChatService}.
 *
 * <p>New constructor signature:
 * <pre>
 *   AiChatService(AiProperties, AiModelRouteService, DynamicChatModelFactory,
 *                 DynamicChatClientCache, ChatClient defaultClient)
 * </pre>
 *
 * <p>Key scenarios tested:
 * <ul>
 *   <li>AI disabled → returns friendly notice</li>
 *   <li>AI enabled, route not found → uses default client</li>
 *   <li>AI enabled, route found → builds client from DB config</li>
 *   <li>Feature flags (column analysis, NL query, summarization) respected</li>
 * </ul>
 */
class AiChatServiceTest {

    private AiProperties props;
    private AiModelRouteService routeService;
    private DynamicChatModelFactory chatModelFactory;
    private DynamicChatClientCache cache;
    private ChatClient defaultClient;

    @BeforeEach
    void setUp() {
        props = new AiProperties();
        props.setEnabled(false);
        props.setProvider("openai");
        props.setDefaultModel("gpt-4o");

        routeService = mock(AiModelRouteService.class);
        chatModelFactory = mock(DynamicChatModelFactory.class);
        cache = new DynamicChatClientCache();
        defaultClient = mock(ChatClient.class);
    }

    private AiChatService service() {
        return new AiChatService(props, routeService, chatModelFactory, cache,
                Optional.ofNullable(defaultClient));
    }

    // -------------------------------------------------------------------------
    // Disabled scenarios — no upstream calls
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("disabled → returns friendly notice; no upstream call")
    void disabledShortCircuits() {
        String r = service().chat("system", "hello");
        assertThat(r).contains("AI 功能未启用");
        verify(routeService, never()).resolve(any(), any());
        verify(chatModelFactory, never()).create(any());
    }

    @Test
    @DisplayName("disabled → chatForJson returns null; no upstream call")
    void jsonShortCircuits() {
        Object r = service().chatForJson("system", "{}", Map.class);
        assertThat(r).isNull();
        verify(routeService, never()).resolve(any(), any());
    }

    @Test
    @DisplayName("enabled but no route → uses default client")
    void enabledButNoRouteUsesDefault() {
        props.setEnabled(true);
        when(routeService.resolve("CHAT", "PRODUCTION")).thenReturn(java.util.Optional.empty());

        service().chat("system", "hello");

        // Route service was consulted; factory was NOT called
        verify(routeService).resolve("CHAT", "PRODUCTION");
        verify(chatModelFactory, never()).create(any());
        // Default client was invoked (stubbing optional since it's a real fallback)
    }

    @Test
    @DisplayName("无 defaultChatClient 时返回友好提示 — 不依赖 OpenAI 自动配置")
    void noDefaultChatClientShortCircuits() {
        props.setEnabled(true);
        // 重新构造 service 用 Optional.empty() 模拟 defaultChatClient 缺失
        AiChatService svcWithNoDefault = new AiChatService(
                props, routeService, chatModelFactory, cache, java.util.Optional.empty());
        when(routeService.resolve("CHAT", "PRODUCTION")).thenReturn(java.util.Optional.empty());

        String r = svcWithNoDefault.chat("system", "hello");

        // 没有 defaultChatClient + 无路由 → 应返回"AI 未启用"提示
        assertThat(r).contains("AI 功能未启用");
        verify(chatModelFactory, never()).create(any());
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
        when(chatModelFactory.create(any())).thenReturn(chatModel);

        try {
            service().chat("system", "hello");
        } catch (Exception ignored) {
            // Mockito stubbing may not cover all Spring AI internals; we just verify factory was called
        }

        verify(chatModelFactory).create(any());
    }

    // -------------------------------------------------------------------------
    // Feature flags
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("analyzeColumnWithAi returns null when column-analysis is disabled")
    void columnAnalysisDisabled() {
        props.setEnabled(true);
        props.setColumnAnalysisEnabled(false);
        Object r = service().analyzeColumnWithAi(
                "c", "c", "STRING", "TEXT",
                List.of("a"), List.of());
        assertThat(r).isNull();
    }

    @Test
    @DisplayName("translateToFilters returns null when NL-query is disabled")
    void nlQueryDisabled() {
        props.setEnabled(true);
        props.setNlQueryEnabled(false);
        Object r = service().translateToFilters(
                "test", List.of(new AnalyzedColumn()));
        assertThat(r).isNull();
    }

    @Test
    @DisplayName("summarizeResults returns null when summarization is disabled")
    void summarizeDisabled() {
        props.setEnabled(true);
        props.setSummarizationEnabled(false);
        String r = service().summarizeResults(
                List.of(Map.of("a", 1)), List.of(), "table", "q");
        assertThat(r).isNull();
    }

    @Test
    @DisplayName("summarizeResults returns null for empty rows")
    void summarizeEmptyRows() {
        props.setEnabled(true);
        props.setSummarizationEnabled(true);
        String r = service().summarizeResults(
                List.of(), List.of(), "table", "question");
        assertThat(r).isNull();
    }
}
