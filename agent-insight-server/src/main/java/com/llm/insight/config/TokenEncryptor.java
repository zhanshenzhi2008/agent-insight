package com.llm.insight.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM 对称加密器。
 *
 * <p>输出格式（base64 字符串）：{@code base64( IV(12) || CIPHERTEXT || TAG(16) )}。
 * <ul>
 *   <li>key：32 字节（256 bit），来源 {@link SecurityProperties#getEncryptionKey()}（base64 编码）</li>
 *   <li>IV：每次加密随机生成 12 字节（96 bit，GCM 推荐）</li>
 *   <li>TAG：GCM 认证标签 128 bit</li>
 * </ul>
 *
 * <p>NULL/空字符串视为明文无值，加解密两端保持一致行为——避免出现 "加密空字符串再解密" 的边缘 case。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TokenEncryptor {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final int KEY_LENGTH_BYTES = 32; // AES-256

    private final SecurityProperties securityProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    private SecretKeySpec keySpec;

    @PostConstruct
    public void init() {
        String base64 = securityProperties.getEncryptionKey();
        if (base64 == null || base64.isBlank()) {
            throw new IllegalStateException(
                    "agent-insight.security.encryption-key 未配置，无法启动加密器");
        }
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(base64.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "agent-insight.security.encryption-key 不是合法 base64 字符串", e);
        }
        if (keyBytes.length != KEY_LENGTH_BYTES) {
            throw new IllegalStateException(String.format(
                    "AES-256 要求 32 字节密钥，当前 %d 字节（请用 `openssl rand -base64 32` 重新生成）",
                    keyBytes.length));
        }
        this.keySpec = new SecretKeySpec(keyBytes, "AES");
        log.info("TokenEncryptor 初始化完成：算法 AES-256-GCM, key-length={} bytes", keyBytes.length);
    }

    /**
     * 加密明文 token。null 或空白返回 null。
     */
    public String encrypt(String plain) {
        if (plain == null || plain.isEmpty()) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));

            ByteBuffer buf = ByteBuffer.allocate(iv.length + ciphertext.length);
            buf.put(iv).put(ciphertext);
            return Base64.getEncoder().encodeToString(buf.array());
        } catch (Exception e) {
            throw new IllegalStateException("加密失败: " + e.getMessage(), e);
        }
    }

    /**
     * 解密 token（base64 输入：IV||CIPHERTEXT||TAG）。null 或空白返回 null。
     */
    public String decrypt(String encrypted) {
        if (encrypted == null || encrypted.isEmpty()) {
            return null;
        }
        try {
            byte[] all = Base64.getDecoder().decode(encrypted.trim());
            if (all.length < IV_LENGTH_BYTES + (TAG_LENGTH_BITS / 8)) {
                throw new IllegalStateException("密文长度不足，疑似损坏");
            }
            byte[] iv = new byte[IV_LENGTH_BYTES];
            byte[] ciphertext = new byte[all.length - IV_LENGTH_BYTES];
            System.arraycopy(all, 0, iv, 0, IV_LENGTH_BYTES);
            System.arraycopy(all, IV_LENGTH_BYTES, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("解密失败: " + e.getMessage(), e);
        }
    }

    /**
     * 判断字符串是否看起来像本加密器的输出（粗筛，base64 长度 > 28 字节）。
     * 用于 API 反序列化时区分 "明文 token" vs "已加密 base64"。
     */
    public boolean isEncrypted(String value) {
        if (value == null || value.length() < 28) {
            return false;
        }
        try {
            Base64.getDecoder().decode(value);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}