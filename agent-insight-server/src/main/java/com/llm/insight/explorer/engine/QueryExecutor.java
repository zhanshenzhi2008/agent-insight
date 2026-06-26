package com.llm.insight.explorer.engine;

import com.llm.insight.explorer.dto.QueryRequest;
import com.llm.insight.explorer.dto.QueryResponse;

import java.util.List;
import java.util.Map;

/**
 * 查询执行器接口。
 * 每种数据库类型（MySQL / PostgreSQL / MongoDB）一个实现。
 */
public interface QueryExecutor {

    /**
     * 执行分页查询，返回列元数据 + 数据行。
     */
    QueryResponse execute(QueryRequest request, DynamicDatasourceManager dsManager);

    /**
     * 查询表的所有列信息（用于自动发现列）
     */
    List<Map<String, Object>> discoverColumns(String tableName, DynamicDatasourceManager dsManager);

    /**
     * 获取支持的操作符列表
     */
    default List<String> supportedOperators() {
        return List.of("EQ", "NE", "GT", "GTE", "LT", "LTE", "LIKE", "IN", "NOT_IN", "IS_NULL", "IS_NOT_NULL", "BETWEEN");
    }
}
