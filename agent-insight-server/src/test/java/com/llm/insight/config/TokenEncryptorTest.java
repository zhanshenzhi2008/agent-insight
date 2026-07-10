package com.llm.insight.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenEncryptorTest {

    private static final String KEY_B64 = Base64.getEncoder().encodeToString(
            new byte[32]); // 32 字节全 0，仅用于测试

    private SecurityProperties props;
    private TokenEncryptor encryptor;

    @BeforeEach
    void setUp() {
        props = new SecurityProperties();
        props.setEncryptionKey(KEY_B64);
        encryptor = new TokenEncryptor(props);
        encryptor.init();
    }

    @Test
    @DisplayName("round-trip: encrypt then decrypt returns the original plain text")
    void roundTrip() {
        String plain = "sk-1234567890abcdef-VERY-LONG-API-KEY-FOR-TESTING-中文字符";
        String encrypted = encryptor.encrypt(plain);
        assertThat(encrypted).isNotNull().isNotEqualTo(plain);
        assertThat(encryptor.decrypt(encrypted)).isEqualTo(plain);
    }

    @Test
    @DisplayName("encrypt produces different ciphertext each time (random IV)")
    void encryptIsNonDeterministic() {
        String plain = "sk-same-input";
        String e1 = encryptor.encrypt(plain);
        String e2 = encryptor.encrypt(plain);
        assertThat(e1).isNotEqualTo(e2);
        // 但都应能解回原文
        assertThat(encryptor.decrypt(e1)).isEqualTo(plain);
        assertThat(encryptor.decrypt(e2)).isEqualTo(plain);
    }

    @Test
    @DisplayName("encrypt returns null when input is null or empty")
    void nullAndEmptyPassthrough() {
        assertThat(encryptor.encrypt(null)).isNull();
        assertThat(encryptor.encrypt("")).isNull();
        assertThat(encryptor.decrypt(null)).isNull();
        assertThat(encryptor.decrypt("")).isNull();
    }

    @Test
    @DisplayName("decrypt rejects tampered ciphertext (GCM tag mismatch)")
    void tamperedCiphertextRejected() {
        String encrypted = encryptor.encrypt("sk-secret");
        // 翻转密文中间一个字符
        char[] chars = encrypted.toCharArray();
        int idx = encrypted.length() / 2;
        chars[idx] = chars[idx] == 'A' ? 'B' : 'A';
        String tampered = new String(chars);
        assertThatThrownBy(() -> encryptor.decrypt(tampered))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("init rejects key that is not 32 bytes")
    void wrongKeyLengthRejected() {
        SecurityProperties wrong = new SecurityProperties();
        wrong.setEncryptionKey(Base64.getEncoder().encodeToString(new byte[16])); // 16 字节
        TokenEncryptor bad = new TokenEncryptor(wrong);
        assertThatThrownBy(bad::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 字节");
    }

    @Test
    @DisplayName("init rejects key that is not valid base64")
    void invalidBase64Rejected() {
        SecurityProperties wrong = new SecurityProperties();
        wrong.setEncryptionKey("!!!not-base64!!!");
        TokenEncryptor bad = new TokenEncryptor(wrong);
        assertThatThrownBy(bad::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("base64");
    }

    @Test
    @DisplayName("init rejects empty key")
    void emptyKeyRejected() {
        SecurityProperties wrong = new SecurityProperties();
        wrong.setEncryptionKey("");
        TokenEncryptor bad = new TokenEncryptor(wrong);
        assertThatThrownBy(bad::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("未配置");
    }

    @Test
    @DisplayName("isEncrypted correctly distinguishes short/invalid/long base64")
    void isEncryptedHeuristic() {
        assertThat(encryptor.isEncrypted(null)).isFalse();
        assertThat(encryptor.isEncrypted("")).isFalse();
        assertThat(encryptor.isEncrypted("short")).isFalse();
        assertThat(encryptor.isEncrypted(encryptor.encrypt("hello"))).isTrue();
    }
}