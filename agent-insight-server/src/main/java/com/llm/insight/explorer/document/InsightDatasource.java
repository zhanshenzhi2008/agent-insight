package com.llm.insight.explorer.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "insight_datasource")
public class InsightDatasource {

    @Id
    private String id;

    /** 唯一标识，供 API 引用 */
    private String datasourceKey;

    private String datasourceName;

    /** MYSQL / POSTGRESQL / MONGODB */
    private String datasourceType;

    /**
     * 逻辑分组（v1.0 引入，2026-07-03 架构修订）
     * <p>同 logicalGroup 下可有多个物理实例（PRIMARY / REPLICA / SHARD-N）。
     * <p>命名规范：{@code {env}-{system}} 形式，kebab-case，如 {@code llm-agent}。
     * <p>MongoDB 模板包必填。
     */
    private String logicalGroup;

    /**
     * 物理角色（v1.0 引入）
     * <ul>
     *   <li>{@code PRIMARY}：主实例（读写）</li>
     *   <li>{@code REPLICA}：副本（只读）</li>
     *   <li>{@code SHARD-N}：分片 N</li>
     * </ul>
     * <p>默认 {@code PRIMARY}。
     */
    private String shardRole;

    /** ACTIVE / INACTIVE / ERROR */
    private String status;

    private ConnectionConfig connectionConfig;

    private String description;

    private java.util.List<String> tags;

    /** 允许查询的表名列表（空=全部允许） */
    private java.util.List<String> allowedTables;

    /**
     * 允许查询的 collection 名列表（v1.0 引入，Mongo 专用）
     * <p>与 {@link #allowedTables} 区别：本字段优先匹配 Mongo，缺省回退到 allowedTables。
     */
    private java.util.List<String> allowedCollections;

    /** 禁止查询的表名列表 */
    private java.util.List<String> deniedTables;

    private String createdBy;
    private LocalDateTime createdAt;
    private String updatedBy;
    private LocalDateTime updatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConnectionConfig {
        private String host;
        private Integer port;
        private String database;
        private String username;
        /** 存储时加密 */
        private String password;
        private Integer connectionPoolSize;
        private Integer connectionTimeout;
        private Integer socketTimeout;
        /** MYSQL: serverTimezone, useSSL | MongoDB: authSource */
        private Map<String, String> extraParams;
    }
}
