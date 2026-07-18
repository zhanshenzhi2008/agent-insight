package com.llm.insight.explorer.service;

import com.llm.insight.config.SecurityProperties;
import com.llm.insight.config.TokenEncryptor;
import com.llm.insight.dto.AiVendorRequest;
import com.llm.insight.dto.AiVendorResponse;
import com.llm.insight.repository.InsightAiVendorRepository;
import com.llm.insight.repository.entity.InsightAiVendor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InsightAiVendorServiceTest {

    private static final String KEY_B64 = Base64.getEncoder().encodeToString(new byte[32]);

    private InsightAiVendorRepository repo;
    private TokenEncryptor encryptor;
    private InsightAiVendorService service;

    @BeforeEach
    void setUp() {
        repo = mock(InsightAiVendorRepository.class);
        SecurityProperties props = new SecurityProperties();
        props.setEncryptionKey(KEY_B64);
        encryptor = new TokenEncryptor(props);
        encryptor.init();
        service = new InsightAiVendorService(repo, encryptor);
    }

    private InsightAiVendor sample(long id, String vendor, boolean hasToken) {
        InsightAiVendor e = new InsightAiVendor();
        e.setId(id);
        e.setVendor(vendor);
        e.setDisplayName(vendor);
        e.setBaseUrl("https://api." + vendor + ".com");
        e.setStatus(1);
        e.setTimeoutSeconds(30);
        e.setMaxRetries(3);
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
        List<AiVendorResponse> out = service.list();
        assertThat(out).hasSize(2);
        assertThat(out.get(0).getToken()).isEqualTo("******");
        assertThat(out.get(1).getToken()).isNull();
    }

    @Test
    @DisplayName("create encrypts the token, rejects duplicate vendor")
    void createEncryptsToken() {
        when(repo.findByVendor("openai")).thenReturn(Optional.empty());
        when(repo.save(any(InsightAiVendor.class))).thenAnswer(inv -> inv.getArgument(0));

        AiVendorRequest req = new AiVendorRequest();
        req.setVendor("openai");
        req.setDisplayName("OpenAI");
        req.setBaseUrl("https://api.openai.com");
        req.setStatus(1);
        req.setTimeoutSeconds(30);
        req.setMaxRetries(3);
        req.setToken("sk-plain-text");

        AiVendorResponse out = service.create(req);
        assertThat(out.getVendor()).isEqualTo("openai");
        assertThat(out.getToken()).isEqualTo("******");
        assertThat(out.getId()).isNotNull();
    }

    @Test
    @DisplayName("create rejects duplicate vendor")
    void createRejectsDuplicate() {
        when(repo.findByVendor("openai")).thenReturn(Optional.of(sample(1, "openai", false)));
        AiVendorRequest req = new AiVendorRequest();
        req.setVendor("openai");
        req.setStatus(1);

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("vendor 已存在");
    }

    @Test
    @DisplayName("update does not overwrite token when request token is blank/masked")
    void updatePreservesToken() {
        InsightAiVendor existing = sample(1, "openai", true);
        String originalEncrypted = new String(existing.getTokenEncrypted(), StandardCharsets.UTF_8);

        when(repo.findById(1L)).thenReturn(Optional.of(existing));
        when(repo.save(any(InsightAiVendor.class))).thenAnswer(inv -> inv.getArgument(0));

        AiVendorRequest req = new AiVendorRequest();
        req.setVendor("openai");
        req.setBaseUrl("https://api.openai.com/v2");
        req.setStatus(1);
        req.setToken("");

        AiVendorResponse out = service.update(1L, req);
        assertThat(new String(existing.getTokenEncrypted(), StandardCharsets.UTF_8))
                .isEqualTo(originalEncrypted);
        assertThat(out.getBaseUrl()).isEqualTo("https://api.openai.com/v2");
    }

    @Test
    @DisplayName("update with new token overwrites the encrypted blob")
    void updateOverwritesToken() {
        InsightAiVendor existing = sample(1, "openai", true);
        String originalEncrypted = new String(existing.getTokenEncrypted(), StandardCharsets.UTF_8);

        when(repo.findById(1L)).thenReturn(Optional.of(existing));
        when(repo.save(any(InsightAiVendor.class))).thenAnswer(inv -> inv.getArgument(0));

        AiVendorRequest req = new AiVendorRequest();
        req.setVendor("openai");
        req.setStatus(1);
        req.setToken("sk-new-key");

        service.update(1L, req);
        assertThat(new String(existing.getTokenEncrypted(), StandardCharsets.UTF_8))
                .isNotEqualTo(originalEncrypted);
        assertThat(encryptor.decrypt(new String(existing.getTokenEncrypted(), StandardCharsets.UTF_8)))
                .isEqualTo("sk-new-key");
    }

    @Test
    @DisplayName("decryptToken returns plain text for configured entity")
    void decryptTokenWorks() {
        InsightAiVendor existing = sample(1, "openai", true);
        when(repo.findById(1L)).thenReturn(Optional.of(existing));
        assertThat(service.decryptToken(1L)).isEqualTo("sk-real-key");
    }

    @Test
    @DisplayName("decryptToken returns null when no token configured")
    void decryptTokenNullForUnset() {
        InsightAiVendor existing = sample(1, "openai", false);
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