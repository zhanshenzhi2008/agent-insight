package com.llm.insight.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Token 消耗统计")
public class TokenUsageDTO {

    @Schema(description = "总 Prompt Tokens")
    private Integer totalPromptTokens;

    @Schema(description = "总 Completion Tokens")
    private Integer totalCompletionTokens;

    @Schema(description = "总 Tokens")
    private Integer totalTokens;

    @Schema(description = "LLM 调用次数")
    private Integer callCount;
}
