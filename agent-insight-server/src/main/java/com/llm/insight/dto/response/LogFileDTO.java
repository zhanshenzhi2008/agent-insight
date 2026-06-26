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
@Schema(description = "日志文件内容")
public class LogFileDTO {

    @Schema(description = "文件名")
    private String fileName;

    @Schema(description = "文件大小(字节)")
    private Long fileSize;

    @Schema(description = "请求ID")
    private String requestId;

    @Schema(description = "日志行列表")
    private List<String> lines;

    @Schema(description = "起始行号")
    private Integer startLine;

    @Schema(description = "结束行号")
    private Integer endLine;

    @Schema(description = "是否有更多内容")
    private Boolean hasMore;

    @Schema(description = "状态：ok / not_found / too_large / error")
    private String status;

    @Schema(description = "状态消息")
    private String message;

    public static LogFileDTO notFound(String message) {
        LogFileDTO dto = new LogFileDTO();
        dto.setStatus("not_found");
        dto.setMessage(message);
        return dto;
    }

    public static LogFileDTO tooLarge(long fileSize) {
        LogFileDTO dto = new LogFileDTO();
        dto.setStatus("too_large");
        dto.setFileSize(fileSize);
        dto.setMessage("日志文件超过 100MB，请使用下载接口分段获取");
        return dto;
    }

    public static LogFileDTO error(String message) {
        LogFileDTO dto = new LogFileDTO();
        dto.setStatus("error");
        dto.setMessage(message);
        return dto;
    }
}
