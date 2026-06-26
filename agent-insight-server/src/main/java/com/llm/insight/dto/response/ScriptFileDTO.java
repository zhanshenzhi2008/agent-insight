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
@Schema(description = "脚本文件信息")
public class ScriptFileDTO {

    @Schema(description = "文件名")
    private String fileName;

    @Schema(description = "完整路径")
    private String fullPath;

    @Schema(description = "文件类型（java/py/md）")
    private String extension;

    @Schema(description = "文件大小(字节)")
    private Long fileSize;

    @Schema(description = "最后修改时间")
    private Long lastModified;
}
