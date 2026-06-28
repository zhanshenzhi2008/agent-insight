package com.llm.insight.explorer.controller;

import com.llm.insight.common.ApiResponse;
import com.llm.insight.explorer.document.InsightDatasource;
import com.llm.insight.explorer.engine.DynamicDatasourceManager;
import com.llm.insight.explorer.engine.QueryRouter;
import com.llm.insight.explorer.service.ConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import javax.sql.DataSource;
import java.util.*;

@RestController
@RequestMapping("/api/v1/explorer/datasources")
@RequiredArgsConstructor
@Tag(name = "数据源配置", description = "外部数据源管理")
public class DatasourceController {

    private final ConfigService configService;
    private final DynamicDatasourceManager dsManager;
    private final QueryRouter queryRouter;

    @GetMapping
    @Operation(summary = "查询所有数据源")
    public ApiResponse<List<InsightDatasource>> list() {
        List<InsightDatasource> list = configService.listDatasources();
        // 脱敏：隐藏密码
        list.forEach(ds -> {
            if (ds.getConnectionConfig() != null) {
                ds.getConnectionConfig().setPassword("******");
            }
        });
        return ApiResponse.ok(list);
    }

    @GetMapping("/{id}")
    @Operation(summary = "根据 ID 获取数据源")
    public ApiResponse<InsightDatasource> getById(@PathVariable String id) {
        InsightDatasource ds = configService.getDatasourceById(id);
        if (ds != null && ds.getConnectionConfig() != null) {
            ds.getConnectionConfig().setPassword("******");
        }
        return ApiResponse.ok(ds);
    }

    @PostMapping
    @Operation(summary = "创建数据源")
    public ApiResponse<InsightDatasource> create(@RequestBody InsightDatasource ds) {
        return ApiResponse.ok(configService.createDatasource(ds));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新数据源")
    public ApiResponse<InsightDatasource> update(@PathVariable String id,
                                                  @RequestBody InsightDatasource ds) {
        // 密码为空字符串则不更新
        if (ds.getConnectionConfig() != null && "******".equals(ds.getConnectionConfig().getPassword())) {
            InsightDatasource existing = configService.getDatasourceById(id);
            if (existing != null) {
                ds.getConnectionConfig().setPassword(existing.getConnectionConfig().getPassword());
            }
        }
        return ApiResponse.ok(configService.updateDatasource(id, ds));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除数据源（级联删除表/列配置）")
    public ApiResponse<Void> delete(@PathVariable String id) {
        configService.deleteDatasource(id);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/test")
    @Operation(summary = "测试数据源连接")
    public ApiResponse<Map<String, Object>> testConnection(@PathVariable String id) {
        InsightDatasource ds = configService.getDatasourceById(id);
        if (ds == null) return ApiResponse.error("数据源不存在");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("datasourceKey", ds.getDatasourceKey());
        result.put("datasourceType", ds.getDatasourceType());

        long start = System.currentTimeMillis();
        try {
            dsManager.cacheDatasource(ds);
            if ("MONGODB".equalsIgnoreCase(ds.getDatasourceType())) {
                var mongo = dsManager.getMongoTemplate(ds);
                mongo.getDb().listCollectionNames().first();
            } else {
                DataSource dataSource = dsManager.getSqlDataSource(ds);
                dataSource.getConnection().createStatement().execute(
                        "MYSQL".equalsIgnoreCase(ds.getDatasourceType())
                                ? "SELECT 1" : "SELECT 1");
            }
            result.put("connected", true);
            result.put("responseTimeMs", System.currentTimeMillis() - start);
            return ApiResponse.ok(result);
        } catch (Exception e) {
            result.put("connected", false);
            result.put("error", e.getMessage());
            result.put("responseTimeMs", System.currentTimeMillis() - start);
            return ApiResponse.ok(result);
        }
    }

    @GetMapping("/{id}/tables")
    @Operation(summary = "列出数据源中的所有表")
    public ApiResponse<List<Map<String, Object>>> listTables(@PathVariable String id) {
        InsightDatasource ds = configService.getDatasourceById(id);
        if (ds == null) return ApiResponse.error("数据源不存在");

        if ("MONGODB".equalsIgnoreCase(ds.getDatasourceType())) {
            try {
                dsManager.cacheDatasource(ds);
                var mongo = dsManager.getMongoTemplate(ds);
                List<String> names = new ArrayList<>();
                mongo.getDb().listCollectionNames().forEach(names::add);
                List<Map<String, Object>> tables = names.stream().map(n -> {
                    Map<String, Object> t = new LinkedHashMap<>();
                    t.put("tableName", n);
                    t.put("type", "COLLECTION");
                    return t;
                }).toList();
                return ApiResponse.ok(tables);
            } catch (Exception e) {
                return ApiResponse.error("查询集合失败: " + e.getMessage());
            }
        }

        try {
            dsManager.cacheDatasource(ds);
            DataSource dataSource = dsManager.getSqlDataSource(ds);
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);

            List<Map<String, Object>> tables;
            if ("POSTGRESQL".equalsIgnoreCase(ds.getDatasourceType())) {
                tables = jdbc.queryForList(
                        "SELECT tablename AS table_name, obj_description((schemaname || '.' || tablename)::regclass, 'pg_class') AS remark "
                                + "FROM pg_catalog.pg_tables "
                                + "WHERE schemaname = 'public' ORDER BY tablename");
            } else {
                tables = jdbc.queryForList(
                        "SELECT TABLE_NAME AS table_name, TABLE_COMMENT AS remark "
                                + "FROM INFORMATION_SCHEMA.TABLES "
                                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_TYPE = 'BASE TABLE' "
                                + "ORDER BY TABLE_NAME");
            }
            return ApiResponse.ok(tables);
        } catch (Exception e) {
            return ApiResponse.error("查询表失败: " + e.getMessage());
        }
    }
}
