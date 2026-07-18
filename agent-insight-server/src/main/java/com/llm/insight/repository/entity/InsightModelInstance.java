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
 * AI 模型实例表（agent-insight 自建业务表）。
 * <p>
 * 一条记录 = 一个具体模型 + 能力维度（capability）+ 等级维度（tier）+ 调用参数。
 * <p>
 * 路由规则：{@link #isCurrent} 在 {@code (capability, tier)} 维度唯一。
 * 优先级：{@link #priority} 越小越优先（同 tier 多模型时使用）。
 *
 * <p>字段对照：
 * <ul>
 *   <li>{@link #vendorId}        → {@link InsightAiVendor#getId()}</li>
 *   <li>{@link #modelName}       → gpt-4o / claude-3.5-sonnet 等</li>
 *   <li>{@link #deploymentName}  → Azure 专用</li>
 *   <li>{@link #capability}      → CHAT / EMBEDDING / VISION / TTS / RERANKER</li>
 *   <li>{@link #tier}            → PRODUCTION / LIGHT / EXPERIMENTAL</li>
 *   <li>{@link #maxTokens}       → 单次响应最大 token 数</li>
 *   <li>{@link #temperature}     → 0.00 ~ 2.00</li>
 *   <li>{@link #topP}            → 0.00 ~ 1.00</li>
 * </ul>
 */
@Data
@Entity
@Table(name = "insight_model_instance")
public class InsightModelInstance implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "vendor_id", nullable = false)
    private Long vendorId;

    @Column(name = "model_name", nullable = false, length = 128)
    private String modelName;

    @Column(name = "deployment_name", length = 128)
    private String deploymentName;

    @Column(name = "capability", nullable = false, length = 32)
    private String capability;

    @Column(name = "tier", nullable = false, length = 32)
    private String tier;

    @Column(name = "priority", nullable = false)
    private Integer priority;

    @Column(name = "max_tokens")
    private Integer maxTokens;

    @Column(name = "temperature", precision = 3, scale = 2)
    private BigDecimal temperature;

    @Column(name = "top_p", precision = 3, scale = 2)
    private BigDecimal topP;

    @Column(name = "is_active", nullable = false)
    private Integer isActive;

    @Column(name = "is_current", nullable = false)
    private Integer isCurrent;

    @Column(name = "description", length = 512)
    private String description;

    @Column(name = "create_time", nullable = false, updatable = false)
    private LocalDateTime createTime;

    @Column(name = "update_time", nullable = false)
    private LocalDateTime updateTime;
}