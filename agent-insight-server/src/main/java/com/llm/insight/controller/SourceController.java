package com.llm.insight.controller;

import com.llm.insight.common.ApiResponse;
import com.llm.insight.dto.response.ScriptFileDTO;
import com.llm.insight.dto.response.SourceLineMappingDTO;
import com.llm.insight.service.SourceViewerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "源码对照", description = "Agent 脚本源码查看与行号映射")
public class SourceController {

    private final SourceViewerService sourceViewerService;

    @GetMapping("/agents/{agentName}/scripts")
    @Operation(summary = "获取 Agent 脚本文件列表")
    public ApiResponse<List<ScriptFileDTO>> listScripts(@PathVariable String agentName) {
        return ApiResponse.ok(sourceViewerService.listScripts(agentName));
    }

    @GetMapping("/scripts/content")
    @Operation(summary = "获取脚本源码内容")
    public ApiResponse<String> getScriptContent(
            @Parameter(description = "脚本文件完整路径", required = true) @RequestParam String path,
            @Parameter(description = "起始行号") @RequestParam(required = false) Integer startLine,
            @Parameter(description = "结束行号") @RequestParam(required = false) Integer endLine) {
        return ApiResponse.ok(sourceViewerService.getScriptContent(path, startLine, endLine));
    }

    @GetMapping("/agents/{agentName}/scripts/mapping")
    @Operation(summary = "taskUniqueName 到源码行号的映射")
    public ApiResponse<SourceLineMappingDTO> mapTaskToLine(
            @PathVariable String agentName,
            @Parameter(description = "任务唯一标识", required = true) @RequestParam String taskUniqueName) {
        return ApiResponse.ok(sourceViewerService.mapTaskToLine(agentName, taskUniqueName));
    }
}
