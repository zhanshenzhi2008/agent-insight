package com.llm.insight.explorer.ai;

import com.llm.insight.config.TokenEncryptor;
import com.llm.insight.repository.entity.InsightAiVendor;
import com.llm.insight.repository.entity.InsightModelInstance;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 根据 {@link InsightAiVendor} 数据库记录，动态创建 Spring AI {@link ChatModel}。
 *
 * <p>核心职责：从数据库里的 vendor 凭证（base_url / token）动态构建对应 Provider 的
 * ChatModel，替代原来 AiChatConfig 里预定义的那 5 个静态 bean。
 *
 * <p>路由调用链：
 * <pre>
 *   AiModelRouteService.resolve(capability, tier)
 *       → RouteResult(vendor, instance)
 *           → DynamicChatModelFactory.create(vendor)
 *               → ChatModel
 *                   → ChatClient.create(chatModel)
 * </pre>
 *
 * <p>当前支持的 Provider：openai / deepseek / ollama / anthropic
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DynamicChatModelFactory {

    private static final String DEFAULT_OPENAI_BASE_URL = "https://api.openai.com";
    private static final String DEFAULT_DEEPSEEK_BASE_URL = "https://api.deepseek.com";
    private static final String DEFAULT_OLLAMA_BASE_URL = "http://localhost:11434";
    private static final String DEFAULT_ANTHROPIC_BASE_URL = "https://api.anthropic.com";

    private final TokenEncryptor tokenEncryptor;

    /**
     * 从 vendor 记录创建 {@link ChatModel}。
     * 返回的 ChatModel 已被配置了 baseUrl / apiKey（从 vendor 凭证解密得到），
     * 不依赖 application.yml 的 spring.ai.* 配置。
     *
     * @param vendor 数据库里的 AI vendor 记录（包含 baseUrl / tokenEncrypted）
     * @return 对应 Provider 的 ChatModel 实现
     * @throws IllegalArgumentException 不支持的 vendor 或 token 为空
     */
    public ChatModel create(InsightAiVendor vendor) {
        if (vendor == null) {
            throw new IllegalArgumentException("vendor 不能为 null");
        }

        String decryptedToken = decryptToken(vendor);
        String vendorName = vendor.getVendor().toLowerCase();

        log.debug("创建 ChatModel: vendor={} baseUrl={} hasToken={}",
                vendorName, vendor.getBaseUrl(), decryptedToken != null);

        return switch (vendorName) {
            case "openai" -> createOpenAi(vendor, decryptedToken);
            case "deepseek" -> createDeepSeek(vendor, decryptedToken);
            case "ollama" -> createOllama(vendor);
            case "anthropic" -> createAnthropic(vendor, decryptedToken);
            default -> throw new IllegalArgumentException(
                    "不支持的 vendor: " + vendor.getVendor() + "，仅支持: openai / deepseek / ollama / anthropic");
        };
    }

    private ChatModel createOpenAi(InsightAiVendor vendor, String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("openai vendor 必须配置 token（API Key）");
        }
        String baseUrl = vendor.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = DEFAULT_OPENAI_BASE_URL;
        }

        OpenAiChatOptions.Builder optionsBuilder = OpenAiChatOptions.builder()
                .apiKey(token);

        if (vendor.getExtraConfig() != null && !vendor.getExtraConfig().isBlank()) {
            try {
                var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                var extra = mapper.readTree(vendor.getExtraConfig());
                if (extra.has("model")) {
                    optionsBuilder.model(extra.get("model").asText());
                }
            } catch (Exception e) {
                log.warn("解析 openai extraConfig 失败: {}", e.getMessage());
            }
        }

        return OpenAiChatModel.builder()
                .options(optionsBuilder.build())
                .build();
    }

    private ChatModel createDeepSeek(InsightAiVendor vendor, String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("deepseek vendor 必须配置 token（API Key）");
        }
        String baseUrl = vendor.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = DEFAULT_DEEPSEEK_BASE_URL;
        }

        DeepSeekApi api = DeepSeekApi.builder()
                .baseUrl(baseUrl)
                .apiKey(token)
                .build();

        DeepSeekChatModel.Builder modelBuilder = DeepSeekChatModel.builder()
                .deepSeekApi(api);

        if (vendor.getApiVersion() != null && !vendor.getApiVersion().isBlank()) {
            log.debug("deepseek apiVersion={} 被忽略（DeepSeekApi 不支持此字段）", vendor.getApiVersion());
        }

        return modelBuilder.build();
    }

    private ChatModel createOllama(InsightAiVendor vendor) {
        String baseUrl = vendor.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = DEFAULT_OLLAMA_BASE_URL;
        }

        OllamaApi api = OllamaApi.builder()
                .baseUrl(baseUrl)
                .build();

        return OllamaChatModel.builder()
                .ollamaApi(api)
                .build();
    }

    private ChatModel createAnthropic(InsightAiVendor vendor, String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("anthropic vendor 必须配置 token（API Key）");
        }
        String baseUrl = vendor.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = DEFAULT_ANTHROPIC_BASE_URL;
        }

        return AnthropicChatModel.builder()
                .options(AnthropicChatOptions.builder()
                        .apiKey(token)
                        .baseUrl(baseUrl)
                        .build())
                .build();
    }

    /**
     * 解密 vendor 的 token_encrypted 字段。
     * entity.getTokenEncrypted() 存储的是 AES-256-GCM 加密后的 base64 字符串（byte[]）。
     */
    private String decryptToken(InsightAiVendor vendor) {
        if (vendor.getTokenEncrypted() == null || vendor.getTokenEncrypted().length == 0) {
            return null;
        }
        try {
            String encoded = new String(vendor.getTokenEncrypted(), StandardCharsets.UTF_8);
            return tokenEncryptor.decrypt(encoded);
        } catch (Exception e) {
            log.error("解密 vendor {} token 失败: {}", vendor.getId(), e.getMessage());
            throw new IllegalStateException("token 解密失败: " + e.getMessage(), e);
        }
    }
}
