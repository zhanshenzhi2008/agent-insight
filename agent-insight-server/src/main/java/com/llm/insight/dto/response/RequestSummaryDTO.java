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
@Schema(description = "请求摘要")
public class RequestSummaryDTO {

    @Schema(description = "请求ID")
    private String requestId;

    @Schema(description = "顶层 Agent 名称")
    private String topAgentName;

    @Schema(description = "Agent 实例ID")
    private Long agentId;

    @Schema(description = "任务状态：1-运行中 2-成功 3-挂起 5-结束")
    private Integer taskStatus;

    @Schema(description = "是否成功")
    private Boolean success;

    @Schema(description = "总任务数")
    private Integer totalTaskCount;

    @Schema(description = "失败任务数")
    private Integer failedTaskCount;

    @Schema(description = "总耗时(ms)")
    private Long totalDuration;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "Agent 实例列表")
    private List<String> subAgentNames;
}
