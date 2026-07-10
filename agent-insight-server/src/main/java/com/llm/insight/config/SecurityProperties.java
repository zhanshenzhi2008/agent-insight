package com.llm.insight.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "agent-insight.security")
public class SecurityProperties {

    /**
     * AES-256-GCM 加密密钥（base64 编码，32 字节）。
     * 用于 insight_ai_model.token_encrypted 等敏感字段的加解密。
     */
    private String encryptionKey;
}