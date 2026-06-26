package com.llm.insight.explorer.engine;

import com.llm.insight.explorer.document.InsightDatasource;
import com.llm.insight.explorer.dto.QueryRequest;
import com.llm.insight.explorer.dto.QueryResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * MongoDB 查询执行器。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MongoQueryExecutor implements QueryExecutor {

    private final DynamicDatasourceManager dsManager;

    @Override
    public QueryResponse execute(QueryRequest request, DynamicDatasourceManager manager) {
        long start = System.currentTimeMillis();

        InsightDatasource ds = manager.getDatasource(request.getDatasourceKey());
        MongoTemplate mongo = manager.getMongoTemplate(ds);

        QueryResponse.QueryResponseBuilder builder = QueryResponse.builder()
                .status("ok")
                .executionTimeMs(System.currentTimeMillis() - start);

        if (Boolean.TRUE.equals(request.getCountOnly())) {
            Query countQuery = buildQuery(request);
            long total = mongo.count(countQuery, ds.getConnectionConfig().getDatabase() + "." + request.getTableName());
            return builder.total(total).data(List.of()).build();
        }

        Query query = buildQuery(request);

        // 分页
        int page = request.getPage() != null ? request.getPage() : 0;
        int pageSize = request.getPageSize() != null ? request.getPageSize() : 20;
        query.skip((long) page * pageSize).limit(pageSize);

        // 排序
        if (request.getOrders() != null) {
            for (QueryRequest.SortCondition o : request.getOrders()) {
                Sort.Direction dir = "DESC".equalsIgnoreCase(o.getDirection())
                        ? Sort.Direction.DESC : Sort.Direction.ASC;
                query.with(Sort.by(dir, o.getField()));
            }
        }

        List<Document> docs = mongo.find(query, Document.class, request.getTableName());
        List<Map<String, Object>> rows = docs.stream()
                .map(this::documentToMap)
                .collect(Collectors.toList());

        // 总数
        Query countQuery = buildQuery(request);
        long total = mongo.count(countQuery, request.getTableName());
        int totalPages = (int) Math.ceil((double) total / pageSize);

        return builder
                .data(rows)
                .total(total)
                .page(page)
                .pageSize(pageSize)
                .totalPages(totalPages)
                .hasNext(page < totalPages - 1)
                .build();
    }

    @Override
    public List<Map<String, Object>> discoverColumns(String tableName, DynamicDatasourceManager manager) {
        // 实际调用时 datasourceKey 通过 manager.datasourceCache 传入
        InsightDatasource ds = null;
        for (InsightDatasource d : manager.getDatasourceCache().values()) {
            ds = d;
            break;
        }
        if (ds == null) return List.of();
        MongoTemplate mongo = manager.getMongoTemplate(ds);

        Document sample = mongo.findOne(new Query(), Document.class, tableName);
        if (sample == null) return List.of();

        List<Map<String, Object>> columns = new ArrayList<>();
        extractColumns("", sample, columns);
        return columns;
    }

    private Query buildQuery(QueryRequest request) {
        Query query = new Query();

        if (request.getFilters() != null) {
            for (QueryRequest.FilterCondition f : request.getFilters()) {
                Criteria criteria = buildCriteria(f);
                query.addCriteria(criteria);
            }
        }

        return query;
    }

    private Criteria buildCriteria(QueryRequest.FilterCondition f) {
        String field = f.getColumn();
        String op = f.getOperator().toUpperCase();

        return switch (op) {
            case "EQ" -> Criteria.where(field).is(f.getValue());
            case "NE" -> Criteria.where(field).ne(f.getValue());
            case "GT" -> Criteria.where(field).gt(f.getValue());
            case "GTE" -> Criteria.where(field).gte(f.getValue());
            case "LT" -> Criteria.where(field).lt(f.getValue());
            case "LTE" -> Criteria.where(field).lte(f.getValue());
            case "LIKE" -> Criteria.where(field).regex(
                    f.getValue().toString(), "i");
            case "IN" -> Criteria.where(field).in(toList(f.getValue()));
            case "NOT_IN" -> Criteria.where(field).nin(toList(f.getValue()));
            case "IS_NULL" -> Criteria.where(field).isNull();
            case "IS_NOT_NULL" -> Criteria.where(field).ne(null);
            case "BETWEEN" -> Criteria.where(field).gte(f.getValue()).lte(f.getValue2());
            default -> Criteria.where(field).is(f.getValue());
        };
    }

    @SuppressWarnings("unchecked")
    private List<Object> toList(Object v) {
        if (v instanceof List) return (List<Object>) v;
        if (v.getClass().isArray()) {
            Object[] arr = v instanceof Object[] ? (Object[]) v : null;
            if (arr == null) {
                int len = java.lang.reflect.Array.getLength(v);
                arr = new Object[len];
                for (int i = 0; i < len; i++) arr[i] = java.lang.reflect.Array.get(v, i);
            }
            return Arrays.asList(arr);
        }
        return List.of(v);
    }

    private void extractColumns(String prefix, Document doc, List<Map<String, Object>> columns) {
        for (String key : doc.keySet()) {
            Object val = doc.get(key);
            String fullKey = prefix.isEmpty() ? key : prefix + "." + key;
            Map<String, Object> col = new LinkedHashMap<>();
            col.put("columnName", fullKey);
            col.put("dataType", inferMongoType(val));
            columns.add(col);

            if (val instanceof Document nested) {
                extractColumns(fullKey, nested, columns);
            }
        }
    }

    private String inferMongoType(Object val) {
        if (val == null) return "NULL";
        if (val instanceof String) return "STRING";
        if (val instanceof Integer || val instanceof Long || val instanceof Double) return "NUMBER";
        if (val instanceof Boolean) return "BOOLEAN";
        if (val instanceof java.util.Date) return "DATETIME";
        if (val instanceof List) return "ARRAY";
        if (val instanceof Document) return "OBJECT";
        return "STRING";
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> documentToMap(Document doc) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (String key : doc.keySet()) {
            Object val = doc.get(key);
            if (val instanceof Document nested) {
                map.put(key, documentToMap(nested));
            } else {
                map.put(key, val);
            }
        }
        return map;
    }
}
