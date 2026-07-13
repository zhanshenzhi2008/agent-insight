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
@CompoundIndex(name = "table_key_idx", def = "{'tableKey': 1}", unique = true, sparse = true)
@CompoundIndex(name = "logical_group_idx", def = "{'logicalGroup': 1}")
public class InsightTableConfig {

    @Id
    private String id;

    /**
     * 业务唯一 key（v1.0 引入，2026-07-03 架构修订）
     * <p>命名规范：{@code {logicalGroup}.{tableName}}，如 {@code llm-agent.log_llm_agent_main}。
     * <p>与 {@link #datasourceKey} + {@link #tableName} 二元组的区别：
     * {@code tableKey} 在跨 logicalGroup 时也能唯一定位一张表。
     */
    private String tableKey;

    /** 逻辑分组（冗余存储，便于按组聚合查询） */
    private String logicalGroup;

    /** 关联的数据源 key */
    private String datasourceKey;

    /**
     * 外部 Mongo collection 名（v1.0 引入）
     * <p>SQL 数据源与 {@link #tableName} 相同；Mongo 数据源独立字段以表达 camelCase 字段命名。
     * <p>优先级：{@code collectionName} &gt; {@code tableName} &gt; 模板默认。
     */
    private String collectionName;

    /** 外部数据库中的实际表/集合名 */
    private String tableName;

    /**
     * 表类型（v1.0 引入）
     * <ul>
     *   <li>{@code TABLE}：SQL 表</li>
     *   <li>{@code VIEW}：SQL 视图</li>
     *   <li>{@code COLLECTION}：Mongo collection（默认）</li>
     * </ul>
     */
    private String tableType;

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

    /**
     * 模板版本（v1.0 引入）
     * <p>模板导入时由 importer 写入（如 {@code 1.0.0}），便于升级差异管理。
     */
    private String version;
}
