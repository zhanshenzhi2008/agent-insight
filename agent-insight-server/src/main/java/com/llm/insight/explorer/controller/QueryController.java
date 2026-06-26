package com.llm.insight.explorer.controller;

import com.llm.insight.common.ApiResponse;
import com.llm.insight.explorer.document.InsightQueryHistory;
import com.llm.insight.explorer.dto.QueryRequest;
import com.llm.insight.explorer.dto.QueryResponse;
import com.llm.insight.explorer.engine.QueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * 通用数据查询执行 API。
 * 核心接口：POST /execute —— 返回动态列头 + 数据。
 */
@RestController
@RequestMapping("/api/v1/explorer/query")
@RequiredArgsConstructor
@Tag(name = "数据查询", description = "跨数据源通用查询 API")
public class QueryController {

    private final QueryService queryService;
    private final MongoTemplate mongoTemplate;

    @PostMapping("/execute")
    @Operation(summary = "执行通用查询（返回动态列头 + 分页数据）")
    public ApiResponse<QueryResponse> execute(@RequestBody QueryRequest request) {
        long start = System.currentTimeMillis();
        QueryResponse response = queryService.execute(request);
        long elapsed = System.currentTimeMillis() - start;

        // 异步记录执行历史
        recordHistory(request, response, elapsed);

        return new ApiResponse<>(response.getStatus().equals("ok") ? 0 : -1,
                response.getStatus(), response);
    }

    private void recordHistory(QueryRequest request, QueryResponse response, long elapsed) {
        try {
            InsightQueryHistory history = InsightQueryHistory.builder()
                    .datasourceKey(request.getDatasourceKey())
                    .tableName(request.getTableName())
                    .queryType(request.getSavedQueryId() != null ? "SAVED_QUERY" :
                            (request.getFreeSql() != null ? "FREE_SQL" : "DIRECT"))
                    .executedQuery(request.getFreeSql() != null ? request.getFreeSql()
                            : "SELECT ... FROM " + request.getTableName())
                    .resultCount(response.getData() != null ? response.getData().size() : 0)
                    .executionTimeMs(elapsed)
                    .status(response.getStatus())
                    .errorMessage(response.getError())
                    .executedAt(LocalDateTime.now())
                    .build();
            mongoTemplate.save(history);
        } catch (Exception e) {
            // 记录失败不影响查询
        }
    }
}
