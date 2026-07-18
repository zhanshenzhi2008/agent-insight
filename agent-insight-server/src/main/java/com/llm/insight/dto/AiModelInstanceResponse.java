package com.llm.insight.dto;

import com.llm.insight.repository.entity.InsightModelInstance;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI 模型实例响应体。
 */
@Data
public class AiModelInstanceResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long vendorId;
    private String vendor;
    private String modelName;
    private String deploymentName;
    private String capability;
    private String tier;
    private Integer priority;
    private Integer maxTokens;
    private BigDecimal temperature;
    private BigDecimal topP;
    private Integer isActive;
    private Integer isCurrent;
    private String description;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public static AiModelInstanceResponse from(InsightModelInstance e) {
        return from(e, null);
    }

    public static AiModelInstanceResponse from(InsightModelInstance e, String vendorName) {
        AiModelInstanceResponse r = new AiModelInstanceResponse();
        r.id = e.getId();
        r.vendorId = e.getVendorId();
        r.vendor = vendorName;
        r.modelName = e.getModelName();
        r.deploymentName = e.getDeploymentName();
        r.capability = e.getCapability();
        r.tier = e.getTier();
        r.priority = e.getPriority();
        r.maxTokens = e.getMaxTokens();
        r.temperature = e.getTemperature();
        r.topP = e.getTopP();
        r.isActive = e.getIsActive();
        r.isCurrent = e.getIsCurrent();
        r.description = e.getDescription();
        r.createTime = e.getCreateTime();
        r.updateTime = e.getUpdateTime();
        return r;
    }
}