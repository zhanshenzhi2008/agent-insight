package com.llm.insight.controller;

import com.llm.insight.common.ApiResponse;
import com.llm.insight.dto.response.LogFileDTO;
import com.llm.insight.dto.response.LogSearchResultDTO;
import com.llm.insight.service.LogViewerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/requests")
@RequiredArgsConstructor
@Tag(name = "日志查看", description = "Per-Request 日志文件查看")
public class LogController {

    private final LogViewerService logViewerService;

    @GetMapping("/{requestId}/log")
    @Operation(summary = "获取日志文件内容（分页）")
    public ApiResponse<LogFileDTO> readLogFile(
            @PathVariable String requestId,
            @Parameter(description = "用户名", required = true) @RequestParam String username,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5000") int pageSize) {
        return ApiResponse.ok(logViewerService.readLogFile(requestId, username, page, pageSize));
    }

    @GetMapping("/{requestId}/log/search")
    @Operation(summary = "日志内容搜索")
    public ApiResponse<List<LogSearchResultDTO>> searchInLog(
            @PathVariable String requestId,
            @Parameter(description = "用户名", required = true) @RequestParam String username,
            @Parameter(description = "搜索关键词", required = true) @RequestParam String keyword,
            @RequestParam(defaultValue = "false") boolean regex) {
        return ApiResponse.ok(logViewerService.searchInLog(requestId, username, keyword, regex));
    }

    @GetMapping("/{requestId}/log/download")
    @Operation(summary = "下载原始日志文件")
    public ResponseEntity<byte[]> downloadLog(
            @PathVariable String requestId,
            @Parameter(description = "用户名", required = true) @RequestParam String username) {
        byte[] content = logViewerService.downloadLogFile(requestId, username);
        if (content == null || content.length == 0) {
            return ResponseEntity.notFound().build();
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_PLAIN);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(requestId + ".log").build());
        return new ResponseEntity<>(content, headers, HttpStatus.OK);
    }
}
