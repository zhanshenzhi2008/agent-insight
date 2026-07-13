package com.llm.insight.explorer.controller;

import com.llm.insight.common.ApiResponse;
import com.llm.insight.dto.AiModelConfigRequest;
import com.llm.insight.dto.AiModelConfigResponse;
import com.llm.insight.explorer.service.InsightAiModelConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/explorer/ai-models")
@RequiredArgsConstructor
@Tag(name = "AI 模型供应商", description = "agent-insight 自身业务：多模型供应商配置管理（带 token 加密）")
public class InsightAiModelConfigController {

    private final InsightAiModelConfigService service;

    @GetMapping
    @Operation(summary = "列出所有 AI 模型供应商配置（token 字段脱敏）")
    public ApiResponse<List<AiModelConfigResponse>> list() {
        return ApiResponse.ok(service.list());
    }

    @GetMapping("/{id}")
    @Operation(summary = "按 ID 获取配置（token 字段脱敏）")
    public ApiResponse<AiModelConfigResponse> getById(@PathVariable Long id) {
        return service.getById(id)
                .map(ApiResponse::ok)
                .orElse(ApiResponse.error("配置不存在: id=" + id));
    }

    @GetMapping("/by-vendor/{vendor}")
    @Operation(summary = "按 vendor 获取配置（token 字段脱敏）")
    public ApiResponse<AiModelConfigResponse> getByVendor(@PathVariable String vendor) {
        return service.getByVendor(vendor)
                .map(ApiResponse::ok)
                .orElse(ApiResponse.error("配置不存在: vendor=" + vendor));
    }

    @PostMapping
    @Operation(summary = "新建 AI 模型供应商配置（token 字段加密入库）")
    public ApiResponse<AiModelConfigResponse> create(@Valid @RequestBody AiModelConfigRequest req) {
        return ApiResponse.ok(service.create(req));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新 AI 模型供应商配置")
    public ApiResponse<AiModelConfigResponse> update(@PathVariable Long id,
                                                     @Valid @RequestBody AiModelConfigRequest req) {
        return ApiResponse.ok(service.update(id, req));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除 AI 模型供应商配置")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok();
    }
}