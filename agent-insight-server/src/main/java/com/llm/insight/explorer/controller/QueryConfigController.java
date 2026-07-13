package com.llm.insight.explorer.controller;

import com.llm.insight.common.ApiResponse;
import com.llm.insight.explorer.document.InsightQueryConfig;
import com.llm.insight.explorer.service.ConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/explorer/queries")
@RequiredArgsConstructor
@Tag(name = "查询配置", description = "保存的查询模板管理")
public class QueryConfigController {

    private final ConfigService configService;

    @GetMapping
    @Operation(summary = "获取数据源下的查询配置列表")
    public ApiResponse<List<InsightQueryConfig>> list(
            @RequestParam(required = false) String datasourceKey) {
        if (datasourceKey != null) {
            return ApiResponse.ok(configService.listQueryConfigs(datasourceKey));
        }
        return ApiResponse.ok(List.of());
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取查询详情")
    public ApiResponse<InsightQueryConfig> get(@PathVariable String id) {
        return ApiResponse.ok(configService.getQueryConfig(id));
    }

    @PostMapping
    @Operation(summary = "创建查询配置")
    public ApiResponse<InsightQueryConfig> create(@RequestBody InsightQueryConfig config) {
        return ApiResponse.ok(configService.createQueryConfig(config));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新查询配置")
    public ApiResponse<InsightQueryConfig> update(@PathVariable String id,
                                                  @RequestBody InsightQueryConfig config) {
        return ApiResponse.ok(configService.updateQueryConfig(id, config));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除查询配置")
    public ApiResponse<Void> delete(@PathVariable String id) {
        configService.deleteQueryConfig(id);
        return ApiResponse.ok();
    }
}
