package com.llm.insight.explorer.controller;

import com.llm.insight.common.ApiResponse;
import com.llm.insight.dto.AiVendorRequest;
import com.llm.insight.dto.AiVendorResponse;
import com.llm.insight.explorer.service.InsightAiVendorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * AI 模型供应商（凭证层）管理 API。
 * <p>
 * 一个 vendor 唯一一条记录，集中管理 base_url / token / api_version / proxy / timeout。
 * 模型实例（{@code /api/v1/explorer/ai-model-instances}）通过 {@code vendorId} 关联。
 */
@RestController
@RequestMapping("/api/v1/explorer/ai-vendors")
@RequiredArgsConstructor
@Tag(name = "AI 模型供应商", description = "agent-insight 自身业务：AI 模型供应商凭证管理（token 加密）")
public class AiVendorController {

    private final InsightAiVendorService service;

    @GetMapping
    @Operation(summary = "列出所有 vendor（token 字段脱敏）")
    public ApiResponse<List<AiVendorResponse>> list() {
        return ApiResponse.ok(service.list());
    }

    @GetMapping("/{id}")
    @Operation(summary = "按 ID 获取 vendor（token 字段脱敏）")
    public ApiResponse<AiVendorResponse> getById(@PathVariable Long id) {
        return service.getById(id)
                .map(ApiResponse::ok)
                .orElse(ApiResponse.error("vendor 不存在: id=" + id));
    }

    @GetMapping("/by-vendor/{vendor}")
    @Operation(summary = "按 vendor 名获取（token 字段脱敏）")
    public ApiResponse<AiVendorResponse> getByVendor(@PathVariable String vendor) {
        return service.getByVendor(vendor)
                .map(ApiResponse::ok)
                .orElse(ApiResponse.error("vendor 不存在: vendor=" + vendor));
    }

    @PostMapping
    @Operation(summary = "新建 vendor（token 字段加密入库）")
    public ApiResponse<AiVendorResponse> create(@Valid @RequestBody AiVendorRequest req) {
        return ApiResponse.ok(service.create(req));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新 vendor")
    public ApiResponse<AiVendorResponse> update(@PathVariable Long id,
                                                 @Valid @RequestBody AiVendorRequest req) {
        return ApiResponse.ok(service.update(id, req));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除 vendor")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok();
    }
}