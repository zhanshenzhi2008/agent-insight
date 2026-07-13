package com.llm.insight.common;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "分页响应包装")
public class PageResult<T> {

    @Schema(description = "数据列表")
    private java.util.List<T> content;

    @Schema(description = "总记录数")
    private long totalElements;

    @Schema(description = "总页数")
    private int totalPages;

    @Schema(description = "当前页（0-based）")
    private int page;

    @Schema(description = "每页大小")
    private int size;

    @Schema(description = "是否有下一页")
    private boolean hasNext;

    public static <T> PageResult<T> of(java.util.List<T> content, long total, int page, int size) {
        PageResult<T> result = new PageResult<>();
        result.content = content;
        result.totalElements = total;
        result.page = page;
        result.size = size;
        result.totalPages = size > 0 ? (int) Math.ceil((double) total / size) : 0;
        result.hasNext = page < result.totalPages - 1;
        return result;
    }
}
