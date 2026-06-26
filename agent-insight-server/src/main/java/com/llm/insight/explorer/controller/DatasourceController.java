package com.llm.insight.explorer.controller;

import com.llm.insight.common.ApiResponse;
import com.llm.insight.explorer.document.InsightDatasource;
import com.llm.insight.explorer.service.ConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/explorer/datasources")
@RequiredArgsConstructor
@Tag(name = "数据源配置", description = "外部数据源管理")
public class DatasourceController {

    private final ConfigService configService;

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
}
