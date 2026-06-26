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
@Schema(description = "任务步骤")
public class TaskStepDTO {

    @Schema(description = "步骤ID")
    private Long id;

    @Schema(description = "步骤类型编号：0=RAG,1=Template,2=Parser,3=Verifier,4=Action")
    private Integer step;

    @Schema(description = "步骤类型标签")
    private String stepLabel;

    @Schema(description = "模板名称")
    private String template;

    @Schema(description = "输入参数")
    private String input;

    @Schema(description = "输出结果")
    private String output;

    @Schema(description = "结果类型")
    private Integer resultType;

    @Schema(description = "是否成功")
    private Boolean success;

    @Schema(description = "耗时(ms)")
    private Long duration;

    @Schema(description = "结束时间")
    private LocalDateTime endTime;
}
