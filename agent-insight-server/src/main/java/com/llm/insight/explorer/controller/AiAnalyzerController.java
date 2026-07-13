package com.llm.insight.explorer.controller;

import com.llm.insight.common.ApiResponse;
import com.llm.insight.explorer.ai.AiChatService;
import com.llm.insight.explorer.ai.AiChatService.AiColumnAnalysis;
import com.llm.insight.explorer.ai.AiChatService.NlQueryResult;
import com.llm.insight.explorer.ai.AiProperties;
import com.llm.insight.explorer.dto.QueryResponse;
import com.llm.insight.explorer.engine.ColumnAnalyzerService.AnalyzedColumn;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * AI 增强分析 API。
 * 三大核心能力：
 * 1. AI 增强列分析（基于语义推断渲染类型）
 * 2. 自然语言查询（NL → FilterCondition）
 * 3. 结果摘要（AI 自动解读数据）
 */
@RestController
@RequestMapping("/api/v1/explorer/ai")
@RequiredArgsConstructor
@Tag(name = "AI 增强", description = "Spring AI 2.x 驱动的智能分析功能")
public class AiAnalyzerController {

    private final AiChatService aiChatService;
    private final AiProperties aiProps;

    // ===== AI 配置 =====

    @GetMapping("/status")
    @Operation(summary = "AI 功能状态")
    public ApiResponse<AiProperties> status() {
        return ApiResponse.ok(aiProps);
    }

    @PutMapping("/config")
    @Operation(summary = "更新 AI 功能配置")
    public ApiResponse<AiProperties> updateConfig(@RequestBody AiProperties props) {
        // 只更新开关，不改模型配置
        aiProps.setEnabled(props.isEnabled());
        aiProps.setColumnAnalysisEnabled(props.isColumnAnalysisEnabled());
        aiProps.setNlQueryEnabled(props.isNlQueryEnabled());
        aiProps.setSummarizationEnabled(props.isSummarizationEnabled());
        return ApiResponse.ok(aiProps);
    }

    // ===== AI 增强列分析 =====

    @PostMapping("/analyze/column")
    @Operation(summary = "AI 增强单列分析（基于语义推断最佳配置）")
    public ApiResponse<AiChatService.AiColumnAnalysis> analyzeColumn(
            @RequestParam String columnName,
            @RequestParam String displayName,
            @RequestParam String dataType,
            @RequestParam String renderType,
            @RequestBody(required = false) Map<String, Object> analysisData
    ) {
        @SuppressWarnings("unchecked")
        List<String> sampleValues = (List<String>) (analysisData != null ? analysisData.get("sampleValues") : null);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> topValues = (List<Map<String, Object>>) (analysisData != null ? analysisData.get("topValues") : null);

        AiColumnAnalysis result = aiChatService.analyzeColumnWithAi(
                columnName, displayName, dataType, renderType,
                sampleValues != null ? sampleValues : List.of(),
                topValues != null ? topValues : List.of()
        );

        return ApiResponse.ok(result);
    }

    @PostMapping("/analyze/batch")
    @Operation(summary = "AI 批量增强分析（对所有列进行语义增强）")
    public ApiResponse<List<AiChatService.AiColumnAnalysis>> batchAnalyze(
            @RequestBody List<AnalyzedColumn> columns
    ) {
        if (!aiProps.isColumnAnalysisEnabled()) {
            return ApiResponse.error("AI 列分析未启用，请在配置中开启");
        }

        List<AiColumnAnalysis> results = columns.stream()
                .map(col -> aiChatService.analyzeColumnWithAi(
                        col.getColumnName(),
                        col.getDisplayName(),
                        col.getDataType(),
                        col.getRenderType(),
                        col.getTopValues() != null
                                ? col.getTopValues().stream().map(m -> String.valueOf(m.get("value"))).toList()
                                : List.of(),
                        col.getTopValues() != null ? col.getTopValues() : List.of()))
                .toList();

        return ApiResponse.ok(results);
    }

    // ===== 自然语言查询 =====

    @PostMapping("/nl-query")
    @Operation(summary = "自然语言转查询条件")
    public ApiResponse<NlQueryResult> nlQuery(
            @RequestParam String query,
            @RequestBody List<AnalyzedColumn> columns
    ) {
        if (!aiProps.isNlQueryEnabled()) {
            return ApiResponse.error("自然语言查询未启用");
        }

        NlQueryResult result = aiChatService.translateToFilters(query, columns);
        return ApiResponse.ok(result);
    }

    // ===== 结果摘要 =====

    @PostMapping("/summarize")
    @Operation(summary = "AI 摘要查询结果")
    public ApiResponse<String> summarize(
            @RequestBody Map<String, Object> payload
    ) {
        if (!aiProps.isSummarizationEnabled()) {
            return ApiResponse.error("结果摘要未启用");
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) payload.get("rows");
        @SuppressWarnings("unchecked")
        List<AnalyzedColumn> columns = (List<AnalyzedColumn>) payload.get("columns");
        String tableName = (String) payload.getOrDefault("tableName", "");
        String userQuestion = (String) payload.getOrDefault("userQuestion", "");

        String summary = aiChatService.summarizeResults(rows, columns, tableName, userQuestion);
        return ApiResponse.ok(summary);
    }

    // ===== 通用对话（调试/测试） =====

    @PostMapping("/chat")
    @Operation(summary = "通用 AI 对话（用于测试模型连接）")
    public ApiResponse<String> chat(@RequestBody Map<String, String> payload) {
        if (!aiProps.isEnabled()) {
            return ApiResponse.error("AI 功能未启用");
        }

        String systemPrompt = payload.getOrDefault("system",
                "你是一个友好的数据助手，用中文回答。");
        String message = payload.get("message");

        String response = aiChatService.chat(systemPrompt, message);
        return ApiResponse.ok(response);
    }
}
