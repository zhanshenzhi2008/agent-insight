package com.llm.insight.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "请求概览")
public class RequestOverviewDTO {

    @Schema(description = "请求ID")
    private String requestId;

    @Schema(description = "顶层 Agent 名称")
    private String topAgentName;

    @Schema(description = "是否成功")
    private Boolean success;

    @Schema(description = "总任务数")
    private Integer totalTaskCount;

    @Schema(description = "失败任务数")
    private Integer failedTaskCount;

    @Schema(description = "总耗时(ms)")
    private Long totalDuration;

    @Schema(description = "LLM 调用次数")
    private Integer llmCallCount;

    @Schema(description = "总 Prompt Tokens")
    private Integer totalPromptTokens;

    @Schema(description = "总 Completion Tokens")
    private Integer totalCompletionTokens;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "Agent 实例列表")
    private List<AgentInstanceDTO> agentInstances;
}
