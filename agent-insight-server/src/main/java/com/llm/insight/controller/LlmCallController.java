package com.llm.insight.controller;

import com.llm.insight.common.ApiResponse;
import com.llm.insight.dto.response.LlmCallDTO;
import com.llm.insight.dto.response.LlmCallDetailDTO;
import com.llm.insight.dto.response.TokenUsageDTO;
import com.llm.insight.service.LlmCallAnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "LLM 调用分析", description = "LLM 调用记录分析")
public class LlmCallController {

    private final LlmCallAnalysisService llmCallAnalysisService;

    @GetMapping("/requests/{requestId}/llm-calls")
    @Operation(summary = "获取 LLM 调用列表")
    public ApiResponse<List<LlmCallDTO>> listLlmCalls(@PathVariable String requestId) {
        return ApiResponse.ok(llmCallAnalysisService.listLlmCalls(requestId));
    }

    @GetMapping("/requests/{requestId}/llm-calls/slow")
    @Operation(summary = "获取慢调用 TopN")
    public ApiResponse<List<LlmCallDTO>> getSlowCalls(
            @PathVariable String requestId,
            @RequestParam(defaultValue = "10") int topN) {
        return ApiResponse.ok(llmCallAnalysisService.getSlowCalls(requestId, topN));
    }

    @GetMapping("/requests/{requestId}/llm-calls/usage")
    @Operation(summary = "Token 消耗统计")
    public ApiResponse<TokenUsageDTO> getTokenUsage(@PathVariable String requestId) {
        return ApiResponse.ok(llmCallAnalysisService.getTokenUsage(requestId));
    }

    @GetMapping("/requests/{requestId}/llm-calls/failed")
    @Operation(summary = "获取失败调用列表")
    public ApiResponse<List<LlmCallDTO>> getFailedCalls(@PathVariable String requestId) {
        return ApiResponse.ok(llmCallAnalysisService.getFailedCalls(requestId));
    }

    @GetMapping("/llm-calls/{callId}/detail")
    @Operation(summary = "获取 LLM 调用详情（含完整 requestBody / responseBody）")
    public ApiResponse<LlmCallDetailDTO> getCallDetail(@PathVariable Long callId) {
        LlmCallDetailDTO detail = llmCallAnalysisService.getCallDetail(callId);
        if (detail == null) {
            return ApiResponse.error(404, "LLM 调用记录不存在: " + callId);
        }
        return ApiResponse.ok(detail);
    }
}
