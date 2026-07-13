package com.llm.insight.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "任务树结构")
public class TaskTreeDTO {

    @Schema(description = "请求ID")
    private String requestId;

    @Schema(description = "Agent 名称")
    private String agentName;

    @Schema(description = "根节点")
    private List<TaskTreeNodeDTO> roots;
}
