package com.llm.insight.dto;

import com.llm.insight.repository.entity.InsightAiVendor;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * AI 模型供应商（凭证层）响应体。{@link #token} 永远是 {@code "******"} 或 null。
 */
@Data
public class AiVendorResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String vendor;
    private String displayName;
    private String baseUrl;
    private String apiVersion;
    private String proxyHost;
    private Integer proxyPort;
    private Integer timeoutSeconds;
    private Integer maxRetries;
    private String extraConfig;
    private Integer status;
    private String description;
    /** 脱敏占位：已配置时为 "******"，未配置为 null */
    private String token;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public static AiVendorResponse from(InsightAiVendor e) {
        AiVendorResponse r = new AiVendorResponse();
        r.id = e.getId();
        r.vendor = e.getVendor();
        r.displayName = e.getDisplayName();
        r.baseUrl = e.getBaseUrl();
        r.apiVersion = e.getApiVersion();
        r.proxyHost = e.getProxyHost();
        r.proxyPort = e.getProxyPort();
        r.timeoutSeconds = e.getTimeoutSeconds();
        r.maxRetries = e.getMaxRetries();
        r.extraConfig = e.getExtraConfig();
        r.status = e.getStatus();
        r.description = e.getDescription();
        r.token = (e.getTokenEncrypted() != null && e.getTokenEncrypted().length > 0) ? "******" : null;
        r.createTime = e.getCreateTime();
        r.updateTime = e.getUpdateTime();
        return r;
    }
}