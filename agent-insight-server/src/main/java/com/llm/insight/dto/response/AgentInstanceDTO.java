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
@Schema(description = "Agent 实例信息")
public class AgentInstanceDTO {

    @Schema(description = "Agent 实例ID")
    private Long agentId;

    @Schema(description = "Agent 名称")
    private String agentName;

    @Schema(description = "是否为入口 Agent")
    private Boolean entrance;

    @Schema(description = "任务数量")
    private Integer taskCount;

    @Schema(description = "是否成功")
    private Boolean success;

    @Schema(description = "耗时(ms)")
    private Long duration;
}
