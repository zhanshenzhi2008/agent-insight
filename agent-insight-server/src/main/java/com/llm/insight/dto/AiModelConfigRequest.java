package com.llm.insight.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * AI 模型供应商 CRUD 请求体。
 * <p>
 * 注意：{@code token} 字段永远是**明文**（API 层）。
 * - 入参：明文，由 Service 层加密后写入 token_encrypted 列。
 * - 出参：返回时统一脱敏为 {@code "******"}。
 */
@Data
public class AiModelConfigRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "vendor 不能为空")
    @Size(max = 64)
    private String vendor;

    @NotBlank(message = "models 不能为空")
    @Size(max = 512)
    private String models;

    @Size(max = 512)
    private String baseUrl;

    @NotNull(message = "status 不能为空")
    private Integer status;

    @Size(max = 512)
    private String description;

    private BigDecimal temperature;

    /** 明文 token（API 层），可选；留空表示不更新。 */
    private String token;
}