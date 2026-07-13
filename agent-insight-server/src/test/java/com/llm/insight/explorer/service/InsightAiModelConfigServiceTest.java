package com.llm.insight.explorer.service;

import com.llm.insight.config.TokenEncryptor;
import com.llm.insight.dto.AiModelConfigRequest;
import com.llm.insight.dto.AiModelConfigResponse;
import com.llm.insight.repository.InsightAiModelConfigRepository;
import com.llm.insight.repository.entity.InsightAiModelConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InsightAiModelConfigServiceTest {

    private static final String KEY_B64 = Base64.getEncoder().encodeToString(new byte[32]);

    private InsightAiModelConfigRepository repo;
    private TokenEncryptor encryptor;
    private InsightAiModelConfigService service;

    @BeforeEach
    void setUp() {
        repo = mock(InsightAiModelConfigRepository.class);
        com.llm.insight.config.SecurityProperties props = new com.llm.insight.config.SecurityProperties();
        props.setEncryptionKey(KEY_B64);
        encryptor = new TokenEncryptor(props);
        encryptor.init();
        service = new InsightAiModelConfigService(repo, encryptor);
    }

    private InsightAiModelConfig sample(long id, String vendor, boolean hasToken) {
        InsightAiModelConfig e = new InsightAiModelConfig();
        e.setId(id);
        e.setVendor(vendor);
        e.setModels("gpt-4o");
        e.setStatus(1);
        e.setTemperature(new BigDecimal("0.30"));
        if (hasToken) {
            e.setTokenEncrypted(encryptor.encrypt("sk-real-key").getBytes(StandardCharsets.UTF_8));
        }
        return e;
    }

    @Test
    @DisplayName("list returns DTOs with masked token when token exists")
    void listMasksToken() {
        when(repo.findAllByOrderByIdAsc()).thenReturn(List.of(
                sample(1, "openai", true),
                sample(2, "ollama", false)));
        List<AiModelConfigResponse> out = service.list();
        assertThat(out).hasSize(2);
        assertThat(out.get(0).getToken()).isEqualTo("******");
        assertThat(out.get(1).getToken()).isNull();
        assertThat(out.get(0).getVendor()).isEqualTo("openai");
    }

    @Test
    @DisplayName("create encrypts the token, rejects duplicate vendor")
    void createEncryptsToken() {
        when(repo.findByVendor("openai")).thenReturn(Optional.empty());
        when(repo.save(any(InsightAiModelConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        AiModelConfigRequest req = new AiModelConfigRequest();
        req.setVendor("openai");
        req.setModels("gpt-4o");
        req.setStatus(1);
        req.setTemperature(new BigDecimal("0.5"));
        req.setToken("sk-plain-text");

        AiModelConfigResponse out = service.create(req);
        assertThat(out.getVendor()).isEqualTo("openai");
        // 响应中 token 字段应该是脱敏
        assertThat(out.getToken()).isEqualTo("******");
        assertThat(out.getId()).isNotNull();
    }

    @Test
    @DisplayName("create rejects duplicate vendor")
    void createRejectsDuplicate() {
        when(repo.findByVendor("openai")).thenReturn(Optional.of(sample(1, "openai", false)));
        AiModelConfigRequest req = new AiModelConfigRequest();
        req.setVendor("openai");
        req.setModels("gpt-4o");
        req.setStatus(1);

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("vendor 已存在");
    }

    @Test
    @DisplayName("update does not overwrite token when request token is blank/masked")
    void updatePreservesToken() {
        InsightAiModelConfig existing = sample(1, "openai", true);
        String originalEncrypted = new String(existing.getTokenEncrypted(), StandardCharsets.UTF_8);

        when(repo.findById(1L)).thenReturn(Optional.of(existing));
        when(repo.save(any(InsightAiModelConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        AiModelConfigRequest req = new AiModelConfigRequest();
        req.setVendor("openai");
        req.setModels("gpt-4o-mini");
        req.setStatus(1);
        req.setToken("");  // 空字符串视为不更新

        AiModelConfigResponse out = service.update(1L, req);
        // 密文应保持不变
        assertThat(new String(existing.getTokenEncrypted(), StandardCharsets.UTF_8))
                .isEqualTo(originalEncrypted);
        assertThat(out.getModels()).isEqualTo("gpt-4o-mini");
    }

    @Test
    @DisplayName("update with new token overwrites the encrypted blob")
    void updateOverwritesToken() {
        InsightAiModelConfig existing = sample(1, "openai", true);
        String originalEncrypted = new String(existing.getTokenEncrypted(), StandardCharsets.UTF_8);

        when(repo.findById(1L)).thenReturn(Optional.of(existing));
        when(repo.save(any(InsightAiModelConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        AiModelConfigRequest req = new AiModelConfigRequest();
        req.setVendor("openai");
        req.setModels("gpt-4o-mini");
        req.setStatus(1);
        req.setToken("sk-new-key");

        service.update(1L, req);
        assertThat(new String(existing.getTokenEncrypted(), StandardCharsets.UTF_8))
                .isNotEqualTo(originalEncrypted);
        // 解密出来确实是新 key
        assertThat(encryptor.decrypt(new String(existing.getTokenEncrypted(), StandardCharsets.UTF_8)))
                .isEqualTo("sk-new-key");
    }

    @Test
    @DisplayName("decryptToken returns plain text for configured entity")
    void decryptTokenWorks() {
        InsightAiModelConfig existing = sample(1, "openai", true);
        when(repo.findById(1L)).thenReturn(Optional.of(existing));
        assertThat(service.decryptToken(1L)).isEqualTo("sk-real-key");
    }

    @Test
    @DisplayName("decryptToken returns null when no token configured")
    void decryptTokenNullForUnset() {
        InsightAiModelConfig existing = sample(1, "openai", false);
        when(repo.findById(1L)).thenReturn(Optional.of(existing));
        assertThat(service.decryptToken(1L)).isNull();
    }

    @Test
    @DisplayName("getByVendor returns empty when not found")
    void getByVendorEmpty() {
        when(repo.findByVendor("nope")).thenReturn(Optional.empty());
        assertThat(service.getByVendor("nope")).isEmpty();
    }
}