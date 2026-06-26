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

    /** ACTIVE / INACTIVE / ERROR */
    private String status;

    private ConnectionConfig connectionConfig;

    private String description;

    private java.util.List<String> tags;

    /** 允许查询的表名列表（空=全部允许） */
    private java.util.List<String> allowedTables;

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
