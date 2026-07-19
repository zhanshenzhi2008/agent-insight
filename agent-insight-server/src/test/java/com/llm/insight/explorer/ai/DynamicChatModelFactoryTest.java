package com.llm.insight.explorer.ai;

import com.llm.insight.config.SecurityProperties;
import com.llm.insight.config.TokenEncryptor;
import com.llm.insight.repository.entity.InsightAiVendor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link DynamicChatModelFactory}.
 *
 * <p>Goal: verify that the factory builds a Spring AI {@link ChatModel} from an
 * {@link InsightAiVendor} row — i.e. the database row really drives the chat model
 * selection and not the {@code application.yml} {@code spring.ai.*} properties.
 *
 * <p>We assert only the public behaviour that matters:
 * <ul>
 *   <li>vendor=openai → OpenAiChatModel</li>
 *   <li>vendor=deepseek → DeepSeekChatModel (vendor key decrypted properly)</li>
 *   <li>vendor=ollama → OllamaChatModel (no token required)</li>
 *   <li>unknown vendor → IllegalArgumentException</li>
 *   <li>vendor with no token → IllegalArgumentException (key required for non-ollama)</li>
 * </ul>
 */
class DynamicChatModelFactoryTest {

    private static final String KEY_B64 = Base64.getEncoder().encodeToString(new byte[32]);

    private TokenEncryptor encryptor;
    private DynamicChatModelFactory factory;

    @BeforeEach
    void setUp() {
        SecurityProperties props = new SecurityProperties();
        props.setEncryptionKey(KEY_B64);
        encryptor = new TokenEncryptor(props);
        encryptor.init();
        factory = new DynamicChatModelFactory(encryptor);
    }

    private InsightAiVendor vendor(String name, String baseUrl, String plainToken) {
        InsightAiVendor v = new InsightAiVendor();
        v.setId(1L);
        v.setVendor(name);
        v.setBaseUrl(baseUrl);
        if (plainToken != null) {
            String encrypted = encryptor.encrypt(plainToken);
            v.setTokenEncrypted(encrypted.getBytes(StandardCharsets.UTF_8));
        }
        v.setStatus(1);
        v.setTimeoutSeconds(30);
        v.setMaxRetries(3);
        return v;
    }

    @Test
    @DisplayName("openai vendor → OpenAiChatModel")
    void openai() {
        ChatModel model = factory.create(vendor("openai", "https://api.openai.com", "sk-test-1234"));
        assertThat(model).isNotNull();
        assertThat(model.getClass().getSimpleName()).isEqualTo("OpenAiChatModel");
    }

    @Test
    @DisplayName("deepseek vendor → DeepSeekChatModel (token decrypted correctly)")
    void deepseek() {
        ChatModel model = factory.create(vendor("deepseek", "https://api.deepseek.com", "sk-ds-xyz"));
        assertThat(model).isNotNull();
        assertThat(model.getClass().getSimpleName()).isEqualTo("DeepSeekChatModel");
    }

    @Test
    @DisplayName("ollama vendor → OllamaChatModel (no token required)")
    void ollama() {
        ChatModel model = factory.create(vendor("ollama", "http://localhost:11434", null));
        assertThat(model).isNotNull();
        assertThat(model.getClass().getSimpleName()).isEqualTo("OllamaChatModel");
    }

    @Test
    @DisplayName("unknown vendor → IllegalArgumentException")
    void unknownVendor() {
        assertThatThrownBy(() -> factory.create(vendor("gpt5-from-future", null, "sk-x")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不支持的 vendor");
    }

    @Test
    @DisplayName("openai vendor with empty token → IllegalArgumentException")
    void openaiRequiresToken() {
        assertThatThrownBy(() -> factory.create(vendor("openai", null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("token");
    }

    @Test
    @DisplayName("null baseUrl → uses provider default (factory does not throw)")
    void nullBaseUrlOk() {
        ChatModel model = factory.create(vendor("openai", null, "sk-test"));
        assertThat(model).isNotNull();
    }
}
