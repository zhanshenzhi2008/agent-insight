package com.llm.insight.explorer.controller;

import com.llm.insight.common.ApiResponse;
import com.llm.insight.dto.AiModelInstanceRequest;
import com.llm.insight.dto.AiModelInstanceResponse;
import com.llm.insight.explorer.service.InsightModelInstanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * AI 模型实例管理 API。
 * <p>
 * 一个模型实例 = (vendor, modelName, capability) 三元组唯一 + tier/priority 路由元数据。
 * 设置 {@code isCurrent=1} 会自动把同 (capability, tier) 下其它实例的 {@code isCurrent} 降为 0。
 */
@RestController
@RequestMapping("/api/v1/explorer/ai-model-instances")
@RequiredArgsConstructor
@Tag(name = "AI 模型实例", description = "agent-insight 自身业务：按 capability/tier 路由的模型实例管理")
public class AiModelInstanceController {

    private final InsightModelInstanceService service;

    @GetMapping
    @Operation(summary = "列出所有模型实例（按 id 升序）")
    public ApiResponse<List<AiModelInstanceResponse>> list() {
        return ApiResponse.ok(service.list());
    }

    @GetMapping("/{id}")
    @Operation(summary = "按 ID 获取模型实例")
    public ApiResponse<AiModelInstanceResponse> getById(@PathVariable Long id) {
        return service.getById(id)
                .map(ApiResponse::ok)
                .orElse(ApiResponse.error("模型实例不存在: id=" + id));
    }

    @GetMapping("/by-vendor/{vendorId}")
    @Operation(summary = "按 vendor 列出实例")
    public ApiResponse<List<AiModelInstanceResponse>> listByVendor(@PathVariable Long vendorId) {
        return ApiResponse.ok(service.listByVendor(vendorId));
    }

    @GetMapping("/by-capability/{capability}")
    @Operation(summary = "按 capability 列出实例（按 priority 升序）")
    public ApiResponse<List<AiModelInstanceResponse>> listByCapability(@PathVariable String capability) {
        return ApiResponse.ok(service.listByCapability(capability));
    }

    @PostMapping
    @Operation(summary = "新建模型实例")
    public ApiResponse<AiModelInstanceResponse> create(@Valid @RequestBody AiModelInstanceRequest req) {
        return ApiResponse.ok(service.create(req));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新模型实例")
    public ApiResponse<AiModelInstanceResponse> update(@PathVariable Long id,
                                                       @Valid @RequestBody AiModelInstanceRequest req) {
        return ApiResponse.ok(service.update(id, req));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除模型实例")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok();
    }
}