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
@Schema(description = "任务树节点")
public class TaskTreeNodeDTO {

    @Schema(description = "节点ID（taskDetailId）")
    private Long id;

    @Schema(description = "节点名称（taskUniqueName）")
    private String name;

    @Schema(description = "节点类型")
    private String type;

    @Schema(description = "是否成功")
    private Boolean success;

    @Schema(description = "耗时(ms)")
    private Long duration;

    @Schema(description = "子节点列表")
    private List<TaskTreeNodeDTO> children;
}
