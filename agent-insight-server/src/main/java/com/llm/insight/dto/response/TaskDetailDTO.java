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
@Schema(description = "任务明细（执行单元）")
public class TaskDetailDTO {

    @Schema(description = "明细ID")
    private Long id;

    @Schema(description = "请求ID")
    private String requestId;

    @Schema(description = "Agent 名称")
    private String agentName;

    @Schema(description = "任务名称")
    private String taskName;

    @Schema(description = "任务唯一标识（对应源码中节点）")
    private String taskUniqueName;

    @Schema(description = "任务类型：expression/foreach/when")
    private String taskType;

    @Schema(description = "执行顺序")
    private Integer taskIndex;

    @Schema(description = "在任务树中的完整路径")
    private String fullPath;

    @Schema(description = "是否成功")
    private Boolean success;

    @Schema(description = "任务结果（截断显示）")
    private String result;

    @Schema(description = "结果类型：1-普通文本 2-Python文件 3-计划列表 4-CSV文件 5-引用文本")
    private Integer resultType;

    @Schema(description = "错误信息")
    private String errorMessage;

    @Schema(description = "耗时(ms)")
    private Long duration;

    @Schema(description = "Agent 重试次数")
    private Integer agentTryCount;

    @Schema(description = "任务重试次数")
    private Integer taskTryCount;

    @Schema(description = "是否最终结果")
    private Boolean finalResult;

    @Schema(description = "开始时间")
    private LocalDateTime createTime;

    @Schema(description = "结束时间")
    private LocalDateTime taskEndTime;

    @Schema(description = "步骤明细列表")
    private List<TaskStepDTO> steps;

    // 源码对照
    @Schema(description = "源码文件路径")
    private String sourceFile;

    @Schema(description = "源码起始行")
    private Integer sourceStartLine;

    @Schema(description = "源码结束行")
    private Integer sourceEndLine;
}
