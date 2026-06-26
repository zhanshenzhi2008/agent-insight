package com.llm.insight.explorer.engine;

import com.llm.insight.explorer.document.InsightColumnConfig;
import com.llm.insight.explorer.document.InsightDatasource;
import com.llm.insight.explorer.document.InsightTableConfig;
import com.llm.insight.explorer.dto.QueryResponse;
import com.llm.insight.explorer.service.*;
import com.llm.insight.explorer.service.ConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 通用查询服务。
 * 接收 QueryRequest → 路由到对应执行器 → 注入列元数据 → 返回 QueryResponse。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QueryService {

    private final DynamicDatasourceManager dsManager;
    private final QueryRouter router;
    private final ConfigService configService;

    public QueryResponse execute(com.llm.insight.explorer.dto.QueryRequest request) {
        // 1. 获取数据源
        InsightDatasource ds = configService.getDatasource(request.getDatasourceKey());
        if (ds == null) {
            return QueryResponse.builder()
                    .status("error")
                    .error("数据源不存在: " + request.getDatasourceKey())
                    .build();
        }

        // 2. 缓存数据源供连接池管理器使用
        dsManager.cacheDatasource(ds);

        // 3. 路由到执行器
        QueryExecutor executor = router.route(ds);

        // 4. 执行查询
        QueryResponse response;
        try {
            response = executor.execute(request, dsManager);
        } catch (Exception e) {
            log.error("查询执行失败: datasource={}, table={}",
                    request.getDatasourceKey(), request.getTableName(), e);
            return QueryResponse.builder()
                    .status("error")
                    .error("查询失败: " + e.getMessage())
                    .build();
        }

        // 5. 注入列元数据（从配置中获取，用于前端动态渲染）
        List<QueryResponse.ColumnMeta> columns = resolveColumns(request, ds);
        response.setColumns(columns);

        return response;
    }

    private List<QueryResponse.ColumnMeta> resolveColumns(
            com.llm.insight.explorer.dto.QueryRequest request,
            InsightDatasource ds) {

        List<InsightColumnConfig> configs = configService.getColumnConfigs(
                ds.getDatasourceKey(),
                request.getTableName()
        );

        if (!configs.isEmpty()) {
            return configs.stream()
                    .filter(c -> !Boolean.TRUE.equals(c.getHidden()))
                    .sorted((a, b) -> {
                        int ai = a.getOrderIndex() != null ? a.getOrderIndex() : 999;
                        int bi = b.getOrderIndex() != null ? b.getOrderIndex() : 999;
                        return Integer.compare(ai, bi);
                    })
                    .map(this::toColumnMeta)
                    .toList();
        }

        // 无配置时，从数据中自动推断列
        if (request.getTableName() != null && responseHasData(request)) {
            return inferColumnsFromData(request, ds);
        }

        return List.of();
    }

    private boolean responseHasData(com.llm.insight.explorer.dto.QueryRequest request) {
        try {
            QueryExecutor executor = router.route(
                    dsManager.getDatasource(request.getDatasourceKey()));
            QueryResponse r = executor.execute(request, dsManager);
            return r.getData() != null && !r.getData().isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    private List<QueryResponse.ColumnMeta> inferColumnsFromData(
            com.llm.insight.explorer.dto.QueryRequest request,
            InsightDatasource ds) {

        try {
            com.llm.insight.explorer.dto.QueryRequest inferReq =
                    com.llm.insight.explorer.dto.QueryRequest.builder()
                            .datasourceKey(request.getDatasourceKey())
                            .tableName(request.getTableName())
                            .page(0)
                            .pageSize(1)
                            .build();

            QueryExecutor executor = router.route(ds);
            QueryResponse r = executor.execute(inferReq, dsManager);

            if (r.getData() != null && !r.getData().isEmpty()) {
                return r.getData().get(0).keySet().stream()
                        .map(key -> QueryResponse.ColumnMeta.builder()
                                .key(key)
                                .label(key)
                                .dataType(inferType(r.getData().get(0).get(key)))
                                .renderType(inferRenderType(r.getData().get(0).get(key)))
                                .sortable(true)
                                .filterable(true)
                                .build())
                        .toList();
            }
        } catch (Exception e) {
            log.warn("自动推断列失败: {}", e.getMessage());
        }
        return List.of();
    }

    private String inferType(Object value) {
        if (value == null) return "STRING";
        if (value instanceof Number) return "NUMBER";
        if (value instanceof Boolean) return "BOOLEAN";
        if (value instanceof java.util.Date) return "DATETIME";
        return "STRING";
    }

    private String inferRenderType(Object value) {
        if (value == null) return "TEXT";
        if (value instanceof Number) return "MONEY";
        if (value instanceof Boolean) return "BOOLEAN";
        if (value instanceof java.util.Date) return "DATETIME";
        return "TEXT";
    }

    private QueryResponse.ColumnMeta toColumnMeta(InsightColumnConfig c) {
        return QueryResponse.ColumnMeta.builder()
                .key(c.getColumnName())
                .label(c.getDisplayName())
                .dataType(c.getDataType())
                .renderType(c.getRenderType())
                .width(c.getWidth())
                .sortable(Boolean.TRUE.equals(c.getSortable()))
                .filterable(Boolean.TRUE.equals(c.getFilterable()))
                .format(c.getNumberFormat() != null ? c.getNumberFormat() : c.getDateFormat())
                .enumLabels(c.getEnumLabels())
                .valueLabels(c.getValueLabels())
                .tagColors(c.getTagColors())
                .linkPattern(c.getLinkPattern())
                .precision(c.getPrecision())
                .maxDisplayLength(c.getMaxDisplayLength())
                .build();
    }
}
