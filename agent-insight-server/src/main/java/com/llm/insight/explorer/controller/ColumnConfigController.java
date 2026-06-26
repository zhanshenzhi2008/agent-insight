package com.llm.insight.explorer.controller;

import com.llm.insight.common.ApiResponse;
import com.llm.insight.explorer.document.InsightColumnConfig;
import com.llm.insight.explorer.service.ConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/explorer/columns")
@RequiredArgsConstructor
@Tag(name = "列配置", description = "列定义管理")
public class ColumnConfigController {

    private final ConfigService configService;

    @GetMapping
    @Operation(summary = "获取指定表的列配置")
    public ApiResponse<List<InsightColumnConfig>> list(
            @RequestParam String datasourceKey,
            @RequestParam String tableName) {
        return ApiResponse.ok(configService.getColumnConfigs(datasourceKey, tableName));
    }

    @PostMapping
    @Operation(summary = "批量创建列配置")
    public ApiResponse<Void> batchCreate(
            @RequestBody List<InsightColumnConfig> configs) {
        if (configs.isEmpty()) return ApiResponse.ok();
        String dsKey = configs.get(0).getDatasourceKey();
        String table = configs.get(0).getTableName();
        configService.saveColumnConfigs(dsKey, table, configs);
        return ApiResponse.ok();
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新列配置")
    public ApiResponse<InsightColumnConfig> update(@PathVariable String id,
                                                  @RequestBody InsightColumnConfig config) {
        return ApiResponse.ok(configService.updateColumnConfig(id, config));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除列配置")
    public ApiResponse<Void> delete(@PathVariable String id) {
        configService.deleteColumnConfig(id);
        return ApiResponse.ok();
    }
}
