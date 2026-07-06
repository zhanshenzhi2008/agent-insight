package com.llm.insight.explorer.controller;

import com.llm.insight.common.ApiResponse;
import com.llm.insight.explorer.document.InsightColumnConfig;
import com.llm.insight.explorer.document.InsightDatasource;
import com.llm.insight.explorer.document.InsightTableConfig;
import com.llm.insight.explorer.engine.DynamicDatasourceManager;
import com.llm.insight.explorer.service.ConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import javax.sql.DataSource;

@RestController
@RequestMapping("/api/v1/explorer/tables")
@RequiredArgsConstructor
@Tag(name = "表配置", description = "数据表配置管理")
public class TableConfigController {

    private final ConfigService configService;
    private final DynamicDatasourceManager dsManager;

    @GetMapping
    @Operation(summary = "获取数据源下的所有表配置")
    public ApiResponse<List<InsightTableConfig>> list(
            @RequestParam String datasourceKey) {
        return ApiResponse.ok(configService.listTableConfigs(datasourceKey));
    }

    @PostMapping
    @Operation(summary = "创建表配置")
    public ApiResponse<InsightTableConfig> create(@RequestBody InsightTableConfig config) {
        return ApiResponse.ok(configService.createTableConfig(config));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新表配置")
    public ApiResponse<InsightTableConfig> update(@PathVariable String id,
                                                    @RequestBody InsightTableConfig config) {
        return ApiResponse.ok(configService.updateTableConfig(id, config));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除表配置")
    public ApiResponse<Void> delete(@PathVariable String id) {
        configService.deleteTableConfig(id);
        return ApiResponse.ok();
    }

    @GetMapping("/discover")
    @Operation(summary = "自动发现外部数据库的表/集合列表")
    public ApiResponse<List<Map<String, Object>>> discoverTables(
            @RequestParam String datasourceKey) {
        InsightDatasource ds = configService.getDatasource(datasourceKey);
        if (ds == null) return ApiResponse.error("数据源不存在");

        try {
            dsManager.cacheDatasource(ds);
            String type = ds.getDatasourceType() == null ? "" : ds.getDatasourceType().toUpperCase();
            if ("MONGODB".equals(type)) {
                return ApiResponse.ok(discoverCollectionsViaMongo(ds));
            }
            return ApiResponse.ok(discoverTablesViaSqlMetadata(ds));
        } catch (Exception e) {
            return ApiResponse.error("发现失败: " + e.getMessage());
        }
    }

    private List<Map<String, Object>> discoverTablesViaSqlMetadata(InsightDatasource ds) {
        javax.sql.DataSource dataSource = dsManager.getSqlDataSource(ds);
        java.util.List<Map<String, Object>> tables = new java.util.ArrayList<>();
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

    private List<Map<String, Object>> discoverCollectionsViaMongo(InsightDatasource ds) {
        org.springframework.data.mongodb.core.MongoTemplate mongo = dsManager.getMongoTemplate(ds);
        java.util.List<Map<String, Object>> result = new java.util.ArrayList<>();
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
