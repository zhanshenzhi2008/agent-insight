package com.llm.insight.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * AI 模型供应商（凭证层）CRUD 请求体。
 * <p>
 * 注意：{@code token} 字段永远是**明文**（API 层），由 Service 层加密后写入。
 */
@Data
public class AiVendorRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "vendor 不能为空")
    @Size(max = 64)
    private String vendor;

    @Size(max = 128)
    private String displayName;

    @Size(max = 512)
    private String baseUrl;

    @Size(max = 64)
    private String apiVersion;

    @Size(max = 128)
    private String proxyHost;

    private Integer proxyPort;

    private Integer timeoutSeconds;

    private Integer maxRetries;

    /** JSON 字符串（厂商特有扩展）。 */
    private String extraConfig;

    @NotNull(message = "status 不能为空")
    private Integer status;

    @Size(max = 512)
    private String description;

    /** 明文 token（API 层），可选；留空表示不更新。 */
    private String token;
}