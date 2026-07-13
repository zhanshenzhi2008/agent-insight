package com.llm.insight.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "LLM 调用记录")
public class LlmCallDTO {

    @Schema(description = "记录ID")
    private Long id;

    @Schema(description = "请求ID")
    private String requestId;

    @Schema(description = "Agent 名称")
    private String agent;

    @Schema(description = "模板名称")
    private String templateName;

    @Schema(description = "Plan 唯一标识")
    private String planUniqueName;

    @Schema(description = "模型类型")
    private String modelType;

    @Schema(description = "耗时(ms)")
    private Long spendTime;

    @Schema(description = "Prompt Tokens")
    private Integer promptTokens;

    @Schema(description = "Completion Tokens")
    private Integer completionTokens;

    @Schema(description = "总 Tokens")
    private Integer totalTokens;

    @Schema(description = "请求是否成功")
    private Boolean successExpression;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "请求体摘要（前200字符）")
    private String requestBodyPreview;

    @Schema(description = "响应体摘要（前200字符）")
    private String responseBodyPreview;
}
