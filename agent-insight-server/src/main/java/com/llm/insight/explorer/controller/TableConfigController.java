package com.llm.insight.explorer.controller;

import com.llm.insight.common.ApiResponse;
import com.llm.insight.explorer.document.InsightColumnConfig;
import com.llm.insight.explorer.document.InsightDatasource;
import com.llm.insight.explorer.document.InsightTableConfig;
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
            return ApiResponse.ok(configService.discoverTables(ds));
        } catch (Exception e) {
            return ApiResponse.error("发现失败: " + e.getMessage());
        }
    }

}
