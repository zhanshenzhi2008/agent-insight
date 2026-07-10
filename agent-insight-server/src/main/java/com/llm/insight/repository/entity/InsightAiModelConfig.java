package com.llm.insight.repository.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI 模型供应商配置表（agent-insight 自建业务表）。
 * <p>
 * 主键策略：雪花 ID（手工生成/外部传入），不依赖数据库自增，
 * 便于未来按 ID 做分布式路由或缓存键。
 * <p>
 * 敏感字段 {@link #tokenEncrypted} 由 {@link com.llm.insight.config.TokenEncryptor}
 * 加解密，**不要**直接读写明文。
 */
@Data
@Entity
@Table(name = "insight_ai_model")
public class InsightAiModelConfig implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "vendor", nullable = false, length = 64)
    private String vendor;

    @Column(name = "models", nullable = false, length = 512)
    private String models;

    @Column(name = "base_url", length = 512)
    private String baseUrl;

    @Column(name = "status", nullable = false)
    private Integer status;

    @Column(name = "description", length = 512)
    private String description;

    @Column(name = "temperature", precision = 3, scale = 2)
    private BigDecimal temperature;

    @Column(name = "token_encrypted")
    private byte[] tokenEncrypted;

    @Column(name = "create_time", nullable = false, updatable = false)
    private LocalDateTime createTime;

    @Column(name = "update_time", nullable = false)
    private LocalDateTime updateTime;
}