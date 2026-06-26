package com.llm.insight.controller;

import com.llm.insight.common.ApiResponse;
import com.llm.insight.common.PageResult;
import com.llm.insight.dto.request.RequestSearchQuery;
import com.llm.insight.dto.response.AgentInstanceDTO;
import com.llm.insight.dto.response.RequestOverviewDTO;
import com.llm.insight.dto.response.RequestSummaryDTO;
import com.llm.insight.service.RequestSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/requests")
@RequiredArgsConstructor
@Tag(name = "请求检索", description = "Agent 请求检索与概览")
public class RequestController {

    private final RequestSearchService requestSearchService;

    @GetMapping
    @Operation(summary = "分页检索请求列表")
    public ApiResponse<PageResult<RequestSummaryDTO>> searchRequests(
            @ModelAttribute RequestSearchQuery query) {
        return ApiResponse.ok(requestSearchService.searchRequests(query));
    }

    @GetMapping("/{requestId}/overview")
    @Operation(summary = "获取请求概览")
    public ApiResponse<RequestOverviewDTO> getOverview(@PathVariable String requestId) {
        return ApiResponse.ok(requestSearchService.getOverview(requestId));
    }

    @GetMapping("/{requestId}/instances")
    @Operation(summary = "获取 Agent 实例列表")
    public ApiResponse<List<AgentInstanceDTO>> listInstances(@PathVariable String requestId) {
        return ApiResponse.ok(requestSearchService.listAgentInstances(requestId));
    }
}
