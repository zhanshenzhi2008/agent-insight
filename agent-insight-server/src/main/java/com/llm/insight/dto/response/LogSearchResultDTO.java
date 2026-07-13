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
@Schema(description = "日志搜索结果")
public class LogSearchResultDTO {

    @Schema(description = "行号")
    private Integer lineNumber;

    @Schema(description = "行内容")
    private String lineContent;
}
