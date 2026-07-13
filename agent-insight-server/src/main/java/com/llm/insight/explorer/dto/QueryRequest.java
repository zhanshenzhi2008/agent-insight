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
@Schema(description = "通用数据查询请求")
public class QueryRequest {

    @Schema(description = "数据源 key")
    private String datasourceKey;

    @Schema(description = "目标表名（与 savedQueryId 二选一）")
    private String tableName;

    @Schema(description = "已保存的查询 ID（与 tableName 二选一）")
    private String savedQueryId;

    @Schema(description = "WHERE 条件 [{column, operator, value}]")
    private List<FilterCondition> filters;

    @Schema(description = "排序 [{field, direction}]")
    private List<SortCondition> orders;

    @Schema(description = "分页：页码（0-based）")
    private Integer page = 0;

    @Schema(description = "分页：每页大小")
    private Integer pageSize = 20;

    @Schema(description = "是否只查总数")
    private Boolean countOnly = false;

    @Schema(description = "自由 SQL（仅限已配置 allowFreeQuery=true 的表）")
    private String freeSql;

    @Schema(description = "保存查询的变量值")
    private Map<String, Object> variables;

    @Schema(description = "返回字段白名单（空=全部字段）")
    private List<String> selectFields;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FilterCondition {
        private String column;
        /** EQ / NE / GT / GTE / LT / LTE / LIKE / IN / NOT_IN / IS_NULL / IS_NOT_NULL / BETWEEN */
        private String operator;
        private Object value;
        private Object value2; // BETWEEN 时用
        private String combine; // AND / OR
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SortCondition {
        private String field;
        private String direction; // ASC / DESC
    }
}
