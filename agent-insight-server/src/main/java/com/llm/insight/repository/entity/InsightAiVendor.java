package com.llm.insight.repository.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * AI 模型供应商凭证表（agent-insight 自建业务表）。
 * <p>
 * 一个 vendor 唯一一条记录，凭证（base_url / token / api_version / proxy / timeout）
 * 集中管理，与模型实例（{@link InsightModelInstance}）通过 {@code vendor_id} 关联。
 * <p>
 * 主键策略：雪花 ID（手工生成），便于分布式路由 / 缓存键。
 * <p>
 * 敏感字段 {@link #tokenEncrypted} 由 {@link com.llm.insight.config.TokenEncryptor}
 * 加解密，**不要**直接读写明文。
 */
@Data
@Entity
@Table(name = "insight_ai_vendor")
public class InsightAiVendor implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "vendor", nullable = false, length = 64)
    private String vendor;

    @Column(name = "display_name", length = 128)
    private String displayName;

    @Column(name = "base_url", length = 512)
    private String baseUrl;

    @Column(name = "token_encrypted")
    private byte[] tokenEncrypted;

    /** Azure 专用，例如 "2024-02-15"。 */
    @Column(name = "api_version", length = 64)
    private String apiVersion;

    @Column(name = "proxy_host", length = 128)
    private String proxyHost;

    @Column(name = "proxy_port")
    private Integer proxyPort;

    @Column(name = "timeout_seconds", nullable = false)
    private Integer timeoutSeconds;

    @Column(name = "max_retries", nullable = false)
    private Integer maxRetries;

    /** 厂商特有扩展，JSON 字符串。 */
    @Column(name = "extra_config", columnDefinition = "TEXT")
    private String extraConfig;

    @Column(name = "status", nullable = false)
    private Integer status;

    @Column(name = "description", length = 512)
    private String description;

    @Column(name = "create_time", nullable = false, updatable = false)
    private LocalDateTime createTime;

    @Column(name = "update_time", nullable = false)
    private LocalDateTime updateTime;
}