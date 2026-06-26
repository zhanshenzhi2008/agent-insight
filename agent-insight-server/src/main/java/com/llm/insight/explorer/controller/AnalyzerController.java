package com.llm.insight.explorer.controller;

import com.llm.insight.common.ApiResponse;
import com.llm.insight.explorer.engine.ColumnAnalyzerService;
import com.llm.insight.explorer.engine.ColumnAnalyzerService.AnalyzedColumn;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * AI 智能分析 API。
 * 采样外部表数据，自动推断列类型、渲染器、格式规则。
 */
@RestController
@RequestMapping("/api/v1/explorer/ai")
@RequiredArgsConstructor
@Tag(name = "智能分析", description = "AI 智能配置分析")
public class AnalyzerController {

    private final ColumnAnalyzerService analyzer;

    @PostMapping("/analyze")
    @Operation(summary = "智能分析表字段（采样 100 条，推断类型/渲染器/格式/标签）")
    public ApiResponse<List<AnalyzedColumn>> analyze(
            @RequestParam String datasourceKey,
            @RequestParam String tableName) {
        try {
            List<AnalyzedColumn> result = analyzer.analyze(datasourceKey, tableName);
            return ApiResponse.ok(result);
        } catch (Exception e) {
            return ApiResponse.error("分析失败: " + e.getMessage());
        }
    }
}
