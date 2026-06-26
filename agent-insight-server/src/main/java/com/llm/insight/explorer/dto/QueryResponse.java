package com.llm.insight.explorer.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "通用数据查询响应")
public class QueryResponse {

    @Schema(description = "状态：ok / error / timeout")
    private String status;

    @Schema(description = "数据列表（Map 无类型）")
    private List<Map<String, Object>> data;

    @Schema(description = "总记录数（分页用）")
    private Long total;

    @Schema(description = "当前页")
    private Integer page;

    @Schema(description = "每页大小")
    private Integer pageSize;

    @Schema(description = "总页数")
    private Integer totalPages;

    @Schema(description = "是否有下一页")
    private Boolean hasNext;

    @Schema(description = "列元数据（驱动前端动态渲染）")
    private List<ColumnMeta> columns;

    @Schema(description = "查询耗时 ms")
    private Long executionTimeMs;

    @Schema(description = "错误信息")
    private String error;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "列元数据")
    public static class ColumnMeta {
        /** 实际列名（数据库字段名） */
        private String key;
        /** 展示列名 */
        private String label;
        /** 数据类型：STRING / NUMBER / DATETIME / BOOLEAN / JSON / TEXT */
        private String dataType;
        /** 渲染类型：TEXT / LINK / TAG / MONEY / DATE / DATETIME / BOOLEAN / JSON / IMAGE / HTML */
        private String renderType;
        /** 列宽 */
        private Integer width;
        /** 是否可排序 */
        private Boolean sortable;
        /** 是否可筛选 */
        private Boolean filterable;
        /** 格式化字符串 */
        private String format;
        /** 枚举映射 */
        private java.util.Map<String, String> enumLabels;
        /** 值标签映射（如 1->成功 0->失败） */
        private java.util.Map<String, String> valueLabels;
        /** TAG 颜色映射 */
        private java.util.Map<String, String> tagColors;
        /** 点击跳转 URL 模式 */
        private String linkPattern;
        /** 精度 */
        private Integer precision;
        /** 最大显示长度 */
        private Integer maxDisplayLength;
    }
}
