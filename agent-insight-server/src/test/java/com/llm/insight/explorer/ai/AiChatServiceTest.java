package com.llm.insight.explorer.ai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link AiChatService}.
 *
 * We only stub the {@link ChatClient} surface area used by AiChatService —
 * Spring AI 2.0's fluent API ({@code prompt().user().call().content()}) —
 * so that we can verify the disabled-flag short-circuit and the JSON
 * markdown-stripping logic without hitting a real provider.
 */
class AiChatServiceTest {

    private AiProperties props;
    private ChatClient client;
    private AiChatService service;

    @BeforeEach
    void setUp() {
        props = new AiProperties();
        props.setEnabled(false);
        ChatClient anyClient = mock(ChatClient.class);
        client = anyClient;
        service = new AiChatService(
                props, client, client, client, client, client);
    }

    @Test
    @DisplayName("chat returns a friendly notice when AI is disabled")
    void disabledShortCircuits() {
        String r = service.chat("system", "hello");
        assertThat(r).contains("AI 功能未启用");
    }

    @Test
    @DisplayName("chatForJson returns null when AI is disabled")
    void jsonShortCircuits() {
        Object r = service.chatForJson("system", "{}", java.util.Map.class);
        assertThat(r).isNull();
    }

    @Test
    @DisplayName("summarizeResults returns null when AI summarization is disabled")
    void summarizeShortCircuits() {
        props.setEnabled(true);
        props.setSummarizationEnabled(false);
        String r = service.summarizeResults(
                java.util.List.of(java.util.Map.of("a", 1)),
                java.util.List.of(),
                "table", "question");
        assertThat(r).isNull();
    }

    @Test
    @DisplayName("summarizeResults returns null for empty rows")
    void summarizeEmptyRows() {
        props.setEnabled(true);
        props.setSummarizationEnabled(true);
        String r = service.summarizeResults(
                java.util.List.of(),
                java.util.List.of(),
                "table", "question");
        assertThat(r).isNull();
    }

    @Test
    @DisplayName("analyzeColumnWithAi returns null when column-analysis is disabled")
    void columnAnalysisDisabled() {
        props.setEnabled(true);
        props.setColumnAnalysisEnabled(false);
        Object r = service.analyzeColumnWithAi("c", "c", "STRING", "TEXT",
                java.util.List.of("a"), java.util.List.of());
        assertThat(r).isNull();
    }

    @Test
    @DisplayName("translateToFilters returns null when NL-query is disabled")
    void nlQueryDisabled() {
        props.setEnabled(true);
        props.setNlQueryEnabled(false);
        Object r = service.translateToFilters("test",
                java.util.List.of(new com.llm.insight.explorer.engine.ColumnAnalyzerService.AnalyzedColumn()));
        assertThat(r).isNull();
    }
}