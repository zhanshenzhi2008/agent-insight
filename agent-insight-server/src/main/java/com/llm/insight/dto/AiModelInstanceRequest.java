package com.llm.insight.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * AI 模型实例 CRUD 请求体。
 * <p>
 * 创建/更新时 {@code vendorId} 不能为空（指明该实例挂在哪个 vendor 下）。
 */
@Data
public class AiModelInstanceRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull(message = "vendorId 不能为空")
    private Long vendorId;

    @NotBlank(message = "modelName 不能为空")
    @Size(max = 128)
    private String modelName;

    @Size(max = 128)
    private String deploymentName;

    @NotBlank(message = "capability 不能为空")
    @Size(max = 32)
    private String capability;

    @NotBlank(message = "tier 不能为空")
    @Size(max = 32)
    private String tier;

    private Integer priority;

    private Integer maxTokens;

    private BigDecimal temperature;

    private BigDecimal topP;

    private Integer isActive;

    private Integer isCurrent;

    @Size(max = 512)
    private String description;
}