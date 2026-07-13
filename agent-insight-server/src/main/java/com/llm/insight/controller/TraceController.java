package com.llm.insight.controller;

import com.llm.insight.common.ApiResponse;
import com.llm.insight.dto.response.*;
import com.llm.insight.service.TraceAnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "执行轨迹", description = "Agent 执行轨迹分析")
public class TraceController {

    private final TraceAnalysisService traceAnalysisService;

    @GetMapping("/requests/{requestId}/trace")
    @Operation(summary = "获取完整执行轨迹（按 taskIndex 排序）")
    public ApiResponse<List<TaskDetailDTO>> getExecutionTrace(
            @PathVariable String requestId,
            @RequestParam(required = false) String agentName) {
        return ApiResponse.ok(traceAnalysisService.getExecutionTrace(requestId, agentName));
    }

    @GetMapping("/requests/{requestId}/trace/tree")
    @Operation(summary = "获取任务树结构（用于可视化）")
    public ApiResponse<TaskTreeDTO> buildTaskTree(
            @PathVariable String requestId,
            @RequestParam(required = false) String agentName) {
        return ApiResponse.ok(traceAnalysisService.buildTaskTree(requestId, agentName));
    }

    @GetMapping("/trace/{taskDetailId}/steps")
    @Operation(summary = "获取任务步骤明细")
    public ApiResponse<List<TaskStepDTO>> getTaskSteps(@PathVariable Long taskDetailId) {
        return ApiResponse.ok(traceAnalysisService.getTaskSteps(taskDetailId));
    }

    @GetMapping("/requests/{requestId}/trace/failed")
    @Operation(summary = "获取失败任务列表")
    public ApiResponse<List<TaskDetailDTO>> getFailedTasks(@PathVariable String requestId) {
        return ApiResponse.ok(traceAnalysisService.getFailedTasks(requestId));
    }
}
