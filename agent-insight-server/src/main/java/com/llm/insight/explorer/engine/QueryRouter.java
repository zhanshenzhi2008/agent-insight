package com.llm.insight.explorer.engine;

import com.llm.insight.explorer.document.InsightDatasource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 查询路由：根据数据源类型选择对应执行器。
 */
@Component
@RequiredArgsConstructor
public class QueryRouter {

    private final SqlQueryExecutor sqlExecutor;
    private final MongoQueryExecutor mongoExecutor;

    public QueryExecutor route(InsightDatasource ds) {
        return switch (ds.getDatasourceType().toUpperCase()) {
            case "MYSQL", "POSTGRESQL" -> sqlExecutor;
            case "MONGODB" -> mongoExecutor;
            default -> throw new IllegalArgumentException(
                    "Unsupported datasource type: " + ds.getDatasourceType());
        };
    }

    public QueryExecutor route(String datasourceType) {
        return switch (datasourceType.toUpperCase()) {
            case "MYSQL", "POSTGRESQL" -> sqlExecutor;
            case "MONGODB" -> mongoExecutor;
            default -> throw new IllegalArgumentException(
                    "Unsupported datasource type: " + datasourceType);
        };
    }
}
