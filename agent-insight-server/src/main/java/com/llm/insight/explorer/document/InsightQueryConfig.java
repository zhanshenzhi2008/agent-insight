package com.llm.insight.explorer.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "insight_query_config")
@CompoundIndex(name = "name_datasource_idx", def = "{'datasourceKey': 1, 'queryName': 1}", unique = true)
public class InsightQueryConfig {

    @Id
    private String id;

    private String datasourceKey;

    private String queryName;
    private String displayName;

    /** SQL 查询语句（MySQL/PostgreSQL）或 MongoDB Aggregation JSON */
    private String queryTemplate;

    /** 查询类型：SQL / MONGODB_AGG */
    private String queryType;

    /** 变量定义 [{name: 'status', label: '状态', type: 'SELECT', options: [...]}] */
    private List<QueryVariable> variables;

    private String description;

    /** 关联的列配置 key */
    private String columnConfigRef;

    /** 标签 */
    private List<String> tags;

    /** 是否启用 */
    private Boolean enabled;

    private String createdBy;
    private LocalDateTime createdAt;
    private String updatedBy;
    private LocalDateTime updatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QueryVariable {
        private String name;
        private String label;
        /** INPUT / SELECT / MULTI_SELECT / DATE_RANGE / DATETIME_RANGE */
        private String type;
        private String defaultValue;
        /** SELECT/MULTI_SELECT 的选项列表 */
        private List<Option> options;
        private Boolean required;
        private String placeholder;

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Option {
            private String value;
            private String label;
        }
    }
}
