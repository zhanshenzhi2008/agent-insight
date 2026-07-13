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
@Schema(description = "源码行号映射")
public class SourceLineMappingDTO {

    @Schema(description = "Agent 名称")
    private String agentName;

    @Schema(description = "任务唯一标识")
    private String taskUniqueName;

    @Schema(description = "源码文件路径")
    private String filePath;

    @Schema(description = "起始行号")
    private Integer startLine;

    @Schema(description = "结束行号")
    private Integer endLine;
}
