package com.llm.insight.dto;

import com.llm.insight.repository.entity.InsightAiModelConfig;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI 模型供应商响应体。{@link #token} 永远是 {@code "******"} 或 null。
 */
@Data
public class AiModelConfigResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String vendor;
    private String models;
    private String baseUrl;
    private Integer status;
    private String description;
    private BigDecimal temperature;
    /** 脱敏占位：已配置时为 "******"，未配置为 null */
    private String token;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public static AiModelConfigResponse from(InsightAiModelConfig e) {
        AiModelConfigResponse r = new AiModelConfigResponse();
        r.id = e.getId();
        r.vendor = e.getVendor();
        r.models = e.getModels();
        r.baseUrl = e.getBaseUrl();
        r.status = e.getStatus();
        r.description = e.getDescription();
        r.temperature = e.getTemperature();
        r.token = (e.getTokenEncrypted() != null && e.getTokenEncrypted().length > 0) ? "******" : null;
        r.createTime = e.getCreateTime();
        r.updateTime = e.getUpdateTime();
        return r;
    }
}