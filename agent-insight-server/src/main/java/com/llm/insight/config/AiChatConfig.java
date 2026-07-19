package com.llm.insight.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;

/**
 * Spring AI 2.0 多 Provider ChatClient 配置。
 *
 * 每个 Provider 有独立的 ChatModel 类，配置路径：
 * - OpenAI:   spring.ai.openai.*
 * - DeepSeek: spring.ai.deepseek.*
 * - Ollama:   spring.ai.ollama.*
 * - Anthropic: spring.ai.anthropic.*
 * - Google GenAI: spring.ai.google-genai.*
 *
 * AiChatService 根据 AI_PROVIDER 配置选择对应的 ChatClient。
 */
@Configuration
public class AiChatConfig {

    @Bean("deepseekChatClient")
    @ConditionalOnProperty(name = "spring.ai.deepseek.api-key", matchIfMissing = false)
    @Lazy
    public ChatClient deepseekChatClient(DeepSeekChatModel model) {
        return ChatClient.create(model);
    }

    @Bean("ollamaChatClient")
    @ConditionalOnProperty(name = "spring.ai.ollama.base-url", matchIfMissing = false)
    @Lazy
    public ChatClient ollamaChatClient(OllamaChatModel model) {
        return ChatClient.create(model);
    }

    @Bean("anthropicChatClient")
    @ConditionalOnProperty(name = "spring.ai.anthropic.api-key", matchIfMissing = false)
    @Lazy
    public ChatClient anthropicChatClient(AnthropicChatModel model) {
        return ChatClient.create(model);
    }

    @Bean("googleGenAiChatClient")
    @ConditionalOnProperty(name = "spring.ai.google.genai.api-key", matchIfMissing = false)
    @Lazy
    public ChatClient googleGenAiChatClient(GoogleGenAiChatModel model) {
        return ChatClient.create(model);
    }

    /** 默认 ChatClient（OpenAI） */
    @Primary
    @Bean("defaultChatClient")
    @Lazy
    public ChatClient defaultChatClient(OpenAiChatModel model) {
        return ChatClient.create(model);
    }

    @Bean("openAiChatClient")
    @Lazy
    public ChatClient openAiChatClient(OpenAiChatModel model) {
        return ChatClient.create(model);
    }
}
