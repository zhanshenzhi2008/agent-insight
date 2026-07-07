package com.llm.insight.explorer.service;

import com.llm.insight.explorer.document.InsightColumnConfig;
import com.llm.insight.explorer.document.InsightDatasource;
import com.llm.insight.explorer.document.InsightQueryConfig;
import com.llm.insight.explorer.document.InsightTableConfig;
import com.llm.insight.explorer.engine.DynamicDatasourceManager;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ConfigService {

    private final MongoTemplate mongoTemplate;
    private final DynamicDatasourceManager dsManager;

    // ===== DataSource =====

    public InsightDatasource createDatasource(InsightDatasource ds) {
        ds.setCreatedAt(LocalDateTime.now());
        ds.setUpdatedAt(LocalDateTime.now());
        return mongoTemplate.save(ds);
    }

    public InsightDatasource updateDatasource(String id, InsightDatasource ds) {
        InsightDatasource existing = mongoTemplate.findById(id, InsightDatasource.class);
        if (existing == null) return null;
        ds.setId(id);
        ds.setCreatedAt(existing.getCreatedAt());
        ds.setCreatedBy(existing.getCreatedBy());
        ds.setUpdatedAt(LocalDateTime.now());
        InsightDatasource saved = mongoTemplate.save(ds);
        dsManager.evict(saved.getDatasourceKey());
        return saved;
    }

    public void deleteDatasource(String id) {
        InsightDatasource ds = mongoTemplate.findById(id, InsightDatasource.class);
        if (ds != null) {
            dsManager.evict(ds.getDatasourceKey());
            mongoTemplate.remove(ds);
            // 级联删除表和列配置
            mongoTemplate.remove(Query.query(
                    Criteria.where("datasourceKey").is(ds.getDatasourceKey())),
                    InsightTableConfig.class);
            mongoTemplate.remove(Query.query(
                    Criteria.where("datasourceKey").is(ds.getDatasourceKey())),
                    InsightColumnConfig.class);
        }
    }

    public List<InsightDatasource> listDatasources() {
        return mongoTemplate.findAll(InsightDatasource.class);
    }

    public InsightDatasource getDatasource(String datasourceKey) {
        return mongoTemplate.findOne(
                Query.query(Criteria.where("datasourceKey").is(datasourceKey)),
                InsightDatasource.class);
    }

    public InsightDatasource getDatasourceById(String id) {
        return mongoTemplate.findById(id, InsightDatasource.class);
    }

    // ===== Table Config =====

    public InsightTableConfig createTableConfig(InsightTableConfig config) {
        config.setCreatedAt(LocalDateTime.now());
        config.setUpdatedAt(LocalDateTime.now());
        return mongoTemplate.save(config);
    }

    public InsightTableConfig updateTableConfig(String id, InsightTableConfig config) {
        InsightTableConfig existing = mongoTemplate.findById(id, InsightTableConfig.class);
        if (existing == null) return null;
        config.setId(id);
        config.setCreatedAt(existing.getCreatedAt());
        config.setCreatedBy(existing.getCreatedBy());
        config.setUpdatedAt(LocalDateTime.now());
        return mongoTemplate.save(config);
    }

    public void deleteTableConfig(String id) {
        InsightTableConfig config = mongoTemplate.findById(id, InsightTableConfig.class);
        if (config != null) {
            mongoTemplate.remove(config);
            mongoTemplate.remove(Query.query(
                    Criteria.where("datasourceKey").is(config.getDatasourceKey())
                            .and("tableName").is(config.getTableName())),
                    InsightColumnConfig.class);
        }
    }

    public List<InsightTableConfig> listTableConfigs(String datasourceKey) {
        return mongoTemplate.find(
                Query.query(Criteria.where("datasourceKey").is(datasourceKey)),
                InsightTableConfig.class);
    }

    public InsightTableConfig getTableConfig(String datasourceKey, String tableName) {
        return mongoTemplate.findOne(
                Query.query(Criteria.where("datasourceKey").is(datasourceKey)
                        .and("tableName").is(tableName)),
                InsightTableConfig.class);
    }

    // ===== Column Config =====

    public InsightColumnConfig createColumnConfig(InsightColumnConfig config) {
        config.setCreatedAt(LocalDateTime.now());
        config.setUpdatedAt(LocalDateTime.now());
        return mongoTemplate.save(config);
    }

    public InsightColumnConfig updateColumnConfig(String id, InsightColumnConfig config) {
        InsightColumnConfig existing = mongoTemplate.findById(id, InsightColumnConfig.class);
        if (existing == null) return null;
        config.setId(id);
        config.setCreatedAt(existing.getCreatedAt());
        config.setCreatedBy(existing.getCreatedBy());
        config.setUpdatedAt(LocalDateTime.now());
        return mongoTemplate.save(config);
    }

    public void deleteColumnConfig(String id) {
        mongoTemplate.remove(mongoTemplate.findById(id, InsightColumnConfig.class));
    }

    public List<InsightColumnConfig> getColumnConfigs(String datasourceKey, String tableName) {
        return mongoTemplate.find(
                Query.query(Criteria.where("datasourceKey").is(datasourceKey)
                        .and("tableName").is(tableName)
                        .and("hidden").ne(true)),
                InsightColumnConfig.class);
    }

    public void saveColumnConfigs(String datasourceKey, String tableName,
                                  List<InsightColumnConfig> configs) {
        // 删除旧的，插入新的
        mongoTemplate.remove(Query.query(
                Criteria.where("datasourceKey").is(datasourceKey)
                        .and("tableName").is(tableName)),
                InsightColumnConfig.class);
        for (InsightColumnConfig c : configs) {
            c.setDatasourceKey(datasourceKey);
            c.setTableName(tableName);
            c.setCreatedAt(LocalDateTime.now());
            c.setUpdatedAt(LocalDateTime.now());
            mongoTemplate.save(c);
        }
    }

    // ===== Query Config =====

    public InsightQueryConfig createQueryConfig(InsightQueryConfig config) {
        config.setCreatedAt(LocalDateTime.now());
        config.setUpdatedAt(LocalDateTime.now());
        return mongoTemplate.save(config);
    }

    public InsightQueryConfig updateQueryConfig(String id, InsightQueryConfig config) {
        InsightQueryConfig existing = mongoTemplate.findById(id, InsightQueryConfig.class);
        if (existing == null) return null;
        config.setId(id);
        config.setCreatedAt(existing.getCreatedAt());
        config.setCreatedBy(existing.getCreatedBy());
        config.setUpdatedAt(LocalDateTime.now());
        return mongoTemplate.save(config);
    }

    public void deleteQueryConfig(String id) {
        mongoTemplate.remove(mongoTemplate.findById(id, InsightQueryConfig.class));
    }

    public List<InsightQueryConfig> listQueryConfigs(String datasourceKey) {
        return mongoTemplate.find(
                Query.query(Criteria.where("datasourceKey").is(datasourceKey)),
                InsightQueryConfig.class);
    }

    public InsightQueryConfig getQueryConfig(String id) {
        return mongoTemplate.findById(id, InsightQueryConfig.class);
    }

    // ===== Table Discovery =====

    /**
     * Discover tables (SQL) or collections (Mongo) on the given datasource.
     * Routes by datasource type:
     *   - MONGODB → MongoTemplate.getCollectionNames()
     *   - others  → JDBC DatabaseMetaData.getTables()
     */
    public List<java.util.Map<String, Object>> discoverTables(InsightDatasource ds) {
        dsManager.cacheDatasource(ds);
        String type = ds.getDatasourceType() == null ? "" : ds.getDatasourceType().toUpperCase();
        if ("MONGODB".equals(type)) {
            return discoverCollectionsViaMongo(ds);
        }
        return discoverTablesViaSqlMetadata(ds);
    }

    private List<java.util.Map<String, Object>> discoverTablesViaSqlMetadata(InsightDatasource ds) {
        javax.sql.DataSource dataSource = dsManager.getSqlDataSource(ds);
        java.util.List<java.util.Map<String, Object>> tables = new java.util.ArrayList<>();
        try (var conn = dataSource.getConnection();
             var rs = conn.getMetaData().getTables(null, null, "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                java.util.Map<String, Object> t = new java.util.LinkedHashMap<>();
                t.put("tableName", rs.getString("TABLE_NAME"));
                t.put("schema", rs.getString("TABLE_SCHEM"));
                t.put("type", rs.getString("TABLE_TYPE"));
                t.put("remark", rs.getString("REMARKS"));
                tables.add(t);
            }
        } catch (java.sql.SQLException e) {
            throw new RuntimeException("JDBC metadata 获取表列表失败: " + e.getMessage(), e);
        }
        return tables;
    }

    private List<java.util.Map<String, Object>> discoverCollectionsViaMongo(InsightDatasource ds) {
        MongoTemplate mongo = dsManager.getMongoTemplate(ds);
        java.util.List<java.util.Map<String, Object>> result = new java.util.ArrayList<>();
        for (String name : mongo.getCollectionNames()) {
            java.util.Map<String, Object> t = new java.util.LinkedHashMap<>();
            t.put("tableName", name);
            t.put("schema", ds.getConnectionConfig() != null ? ds.getConnectionConfig().getDatabase() : null);
            t.put("type", "COLLECTION");
            t.put("remark", null);
            result.add(t);
        }
        return result;
    }
}
