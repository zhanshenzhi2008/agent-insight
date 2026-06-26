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

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "insight_table_config")
@CompoundIndex(name = "datasource_table_idx", def = "{'datasourceKey': 1, 'tableName': 1}", unique = true)
public class InsightTableConfig {

    @Id
    private String id;

    /** 关联的数据源 key */
    private String datasourceKey;

    /** 外部数据库中的实际表/集合名 */
    private String tableName;

    /** 中文/展示名 */
    private String displayName;

    private String description;

    /** 是否启用（未启用则不展示） */
    private Boolean enabled;

    /** 默认排序字段 */
    private String defaultSortField;

    /** 默认排序方向 ASC / DESC */
    private String defaultSortDirection;

    /** 分页大小 */
    private Integer pageSize;

    /** 是否允许自由 SQL 查询 */
    private Boolean allowFreeQuery;

    /** 允许的操作（QUERY / EXPORT / COUNT） */
    private List<String> allowedOperations;

    private String createdBy;
    private LocalDateTime createdAt;
    private String updatedBy;
    private LocalDateTime updatedAt;
}
