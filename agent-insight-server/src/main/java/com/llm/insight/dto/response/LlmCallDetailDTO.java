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
@Schema(description = "LLM 调用详情（含完整 requestBody / responseBody）")
public class LlmCallDetailDTO {

    @Schema(description = "记录ID")
    private Long id;

    @Schema(description = "Agent 名称")
    private String agent;

    @Schema(description = "模板名称")
    private String templateName;

    @Schema(description = "模型类型")
    private String modelType;

    @Schema(description = "耗时(ms)")
    private Long spendTime;

    @Schema(description = "Prompt Tokens")
    private Integer promptTokens;

    @Schema(description = "Completion Tokens")
    private Integer completionTokens;

    @Schema(description = "请求是否成功")
    private Boolean successExpression;

    @Schema(description = "请求体")
    private String requestBody;

    @Schema(description = "请求URL")
    private String requestUrl;

    @Schema(description = "响应体")
    private String responseBody;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
