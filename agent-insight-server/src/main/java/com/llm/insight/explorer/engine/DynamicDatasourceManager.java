package com.llm.insight.explorer.engine;

import com.llm.insight.explorer.document.InsightDatasource;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 动态数据源连接池管理器。
 * 按 datasourceKey 缓存连接池，用到时懒加载，不用时不占资源。
 * <p>
 * 默认端口支持 env 覆盖，优先级：
 * 1. ConnectionConfig.port（非 null）
 * 2. 环境变量 POSTGRES_PORT / MONGODB_PORT（作为单独端口覆盖兜底）
 *    注：MYSQL 端口的环境变量兜底已在 #3.7 改造中废弃，原因是主链路走 MYSQL_URL，
 *    端口解析由 JDBC URL 自身承担；如确需单独覆盖 port，建在 Mongo InsightDatasource 中显式指定。
 * 3. 内置默认值
 */
@Slf4j
@Component
public class DynamicDatasourceManager {

    private static final int DEFAULT_MYSQL_PORT = 3306;
    private static final int DEFAULT_POSTGRES_PORT = 5433;
    private static final int DEFAULT_MONGODB_PORT = 27017;

    private final Map<String, DataSource> sqlDataSources = new ConcurrentHashMap<>();
    private final Map<String, MongoTemplate> mongoTemplates = new ConcurrentHashMap<>();
    private final Map<String, InsightDatasource> datasourceCache = new ConcurrentHashMap<>();

    public DataSource getSqlDataSource(InsightDatasource ds) {
        return sqlDataSources.computeIfAbsent(ds.getDatasourceKey(), key -> {
            log.info("创建 MySQL/PG 连接池: datasourceKey={}", key);
            HikariConfig config = buildHikariConfig(ds);
            return new HikariDataSource(config);
        });
    }

    public MongoTemplate getMongoTemplate(InsightDatasource ds) {
        return mongoTemplates.computeIfAbsent(ds.getDatasourceKey(), key -> {
            log.info("创建 MongoTemplate: datasourceKey={}", key);
            return createMongoTemplate(ds);
        });
    }

    public void evict(String datasourceKey) {
        DataSource ds = sqlDataSources.remove(datasourceKey);
        if (ds instanceof HikariDataSource hds) {
            hds.close();
            log.info("关闭 SQL 连接池: datasourceKey={}", datasourceKey);
        }
        MongoTemplate mt = mongoTemplates.remove(datasourceKey);
        if (mt != null) {
            log.info("关闭 MongoTemplate: datasourceKey={}", datasourceKey);
        }
        datasourceCache.remove(datasourceKey);
    }

    public void cacheDatasource(InsightDatasource ds) {
        datasourceCache.put(ds.getDatasourceKey(), ds);
    }

    public InsightDatasource getDatasource(String datasourceKey) {
        return datasourceCache.get(datasourceKey);
    }

    public Map<String, InsightDatasource> getDatasourceCache() {
        return datasourceCache;
    }

    private HikariConfig buildHikariConfig(InsightDatasource ds) {
        InsightDatasource.ConnectionConfig cc = ds.getConnectionConfig();

        HikariConfig config = new HikariConfig();
        config.setPoolName("insight-pool-" + ds.getDatasourceKey());
        config.setMaximumPoolSize(cc.getConnectionPoolSize() != null ? cc.getConnectionPoolSize() : 5);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(
                cc.getConnectionTimeout() != null ? (long) cc.getConnectionTimeout() : 30000);

        String url = buildJdbcUrl(ds.getDatasourceType(), cc);
        config.setJdbcUrl(url);
        config.setUsername(cc.getUsername());
        config.setPassword(cc.getPassword());
        config.setDriverClassName(getDriverClass(ds.getDatasourceType()));

        return config;
    }

    private String buildJdbcUrl(String type, InsightDatasource.ConnectionConfig cc) {
        String host = cc.getHost();
        int port = cc.getPort() != null ? cc.getPort() : getDefaultPort(type);
        String db = cc.getDatabase();
        StringBuilder sb = new StringBuilder();

        if ("MYSQL".equalsIgnoreCase(type)) {
            sb.append("jdbc:mysql://").append(host).append(":").append(port)
                    .append("/").append(db)
                    .append("?useSSL=false")
                    .append("&allowPublicKeyRetrieval=true")
                    .append("&serverTimezone=")
                    .append(cc.getExtraParams().getOrDefault("serverTimezone", "Asia/Shanghai"))
                    .append("&connectTimeout=").append(cc.getConnectionTimeout() != null ? cc.getConnectionTimeout() : 30000);
        } else if ("POSTGRESQL".equalsIgnoreCase(type)) {
            sb.append("jdbc:postgresql://").append(host).append(":").append(port)
                    .append("/").append(db)
                    .append("?loginTimeout=").append((cc.getConnectionTimeout() != null ? cc.getConnectionTimeout() : 30));
        }

        return sb.toString();
    }

    private String getDriverClass(String type) {
        return switch (type.toUpperCase()) {
            case "MYSQL" -> "com.mysql.cj.jdbc.Driver";
            case "POSTGRESQL" -> "org.postgresql.Driver";
            default -> throw new IllegalArgumentException("Unsupported SQL type: " + type);
        };
    }

    private int getDefaultPort(String type) {
        return switch (type.toUpperCase()) {
            // MYSQL 端口兜底：保留 DEFAULT_MYSQL_PORT 常量作为最后一道防线，
            // 实际部署中端口已由 ConnectionConfig.port 或 MYSQL_URL 决定，
            // 环境变量 MYSQL_PORT 兜底链路已废弃。
            case "MYSQL" -> DEFAULT_MYSQL_PORT;
            case "POSTGRESQL" -> getEnvInt("POSTGRES_PORT", DEFAULT_POSTGRES_PORT);
            default -> DEFAULT_MYSQL_PORT;
        };
    }

    private int getEnvInt(String key, int fallback) {
        String val = System.getenv(key);
        if (val != null && !val.isBlank()) {
            try {
                return Integer.parseInt(val);
            } catch (NumberFormatException ignored) {}
        }
        return fallback;
    }

    private MongoTemplate createMongoTemplate(InsightDatasource ds) {
        InsightDatasource.ConnectionConfig cc = ds.getConnectionConfig();
        StringBuilder uri = new StringBuilder("mongodb://");
        if (cc.getUsername() != null && !cc.getUsername().isEmpty()) {
            uri.append(cc.getUsername())
                    .append(":").append(cc.getPassword())
                    .append("@");
        }
        uri.append(cc.getHost()).append(":")
                .append(cc.getPort() != null ? cc.getPort() : getEnvInt("MONGODB_PORT", DEFAULT_MONGODB_PORT))
                .append("/").append(cc.getDatabase());
        String authSource = cc.getExtraParams() != null ? cc.getExtraParams().get("authSource") : null;
        if (authSource != null) uri.append("?authSource=").append(authSource);

        com.mongodb.client.MongoClient mongoClient =
                com.mongodb.client.MongoClients.create(uri.toString());
        return new MongoTemplate(mongoClient, cc.getDatabase());
    }
}
