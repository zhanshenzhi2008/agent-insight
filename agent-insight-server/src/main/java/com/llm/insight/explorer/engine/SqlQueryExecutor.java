package com.llm.insight.explorer.engine;

import com.llm.insight.explorer.document.InsightDatasource;
import com.llm.insight.explorer.dto.QueryRequest;
import com.llm.insight.explorer.dto.QueryResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.*;

/**
 * MySQL / PostgreSQL 通用 SQL 查询执行器。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SqlQueryExecutor implements QueryExecutor {

    private final DynamicDatasourceManager dsManager;

    @Override
    public QueryResponse execute(QueryRequest request, DynamicDatasourceManager manager) {
        long start = System.currentTimeMillis();

        InsightDatasource ds = manager.getDatasource(request.getDatasourceKey());
        DataSource dataSource = manager.getSqlDataSource(ds);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        String sql;
        String countSql;
        List<Object> params = new ArrayList<>();
        List<Object> countParams = new ArrayList<>();

        if (request.getFreeSql() != null && !request.getFreeSql().isBlank()) {
            sql = request.getFreeSql();
            countSql = buildCountSql(sql);
            countParams = new ArrayList<>(params);
        } else {
            SqlSegment seg = buildSelectSql(request);
            sql = seg.sql;
            params = seg.params;
            countSql = buildCountSql(sql);
            countParams = new ArrayList<>(params);
        }

        QueryResponse.QueryResponseBuilder builder = QueryResponse.builder()
                .status("ok")
                .executionTimeMs(System.currentTimeMillis() - start);

        if (Boolean.TRUE.equals(request.getCountOnly())) {
            Long total = jdbc.queryForObject(countSql, countParams.toArray(), Long.class);
            return builder.total(total).data(List.of()).build();
        }

        long total = jdbc.queryForObject(countSql, countParams.toArray(), Long.class);

        // 分页
        sql = sql + " LIMIT ? OFFSET ?";
        params.add(request.getPageSize());
        params.add(request.getPage() * request.getPageSize());

        List<Map<String, Object>> rows = jdbc.queryForList(sql, params.toArray());

        int totalPages = request.getPageSize() > 0
                ? (int) Math.ceil((double) total / request.getPageSize()) : 0;

        return builder
                .data(rows)
                .total(total)
                .page(request.getPage())
                .pageSize(request.getPageSize())
                .totalPages(totalPages)
                .hasNext(request.getPage() < totalPages - 1)
                .build();
    }

    @Override
    public List<Map<String, Object>> discoverColumns(String tableName, DynamicDatasourceManager manager) {
        InsightDatasource ds = resolveDatasource(manager);
        if (ds == null) return List.of();
        DataSource dataSource = manager.getSqlDataSource(ds);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        String schema = ds.getConnectionConfig().getDatabase();
        String type = ds.getDatasourceType().toUpperCase();

        String sql = switch (type) {
            case "MYSQL" ->
                "SELECT COLUMN_NAME, DATA_TYPE, COLUMN_COMMENT, IS_NULLABLE, COLUMN_KEY, COLUMN_DEFAULT "
                        + "FROM INFORMATION_SCHEMA.COLUMNS "
                        + "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? ORDER BY ORDINAL_POSITION";
            case "POSTGRESQL" ->
                "SELECT c.column_name::text AS COLUMN_NAME, "
                        + "c.data_type AS DATA_TYPE, "
                        + "pg_catalog.col_description(a.attrelid, a.attnum)::text AS COLUMN_COMMENT, "
                        + "c.is_nullable AS IS_NULLABLE, "
                        + "c.column_default AS COLUMN_DEFAULT "
                        + "FROM information_schema.columns c "
                        + "JOIN pg_catalog.pg_attribute a "
                        + "  ON a.attname = c.column_name "
                        + "  AND a.attrelid = ?::regclass "
                        + "WHERE c.table_schema = 'public' AND c.table_name = ? "
                        + "ORDER BY c.ordinal_position";
            default -> throw new IllegalArgumentException("Unsupported type: " + type);
        };

        List<Object> args = switch (type) {
            case "MYSQL" -> List.of(schema, tableName);
            case "POSTGRESQL" -> List.of(tableName, tableName);
            default -> List.of();
        };

        return jdbc.queryForList(sql, args.toArray());
    }

    /**
     * 发现数据源中的所有表（不含视图）。
     */
    public List<Map<String, Object>> discoverTables(DynamicDatasourceManager manager) {
        InsightDatasource ds = resolveDatasource(manager);
        if (ds == null || !"MYSQL".equalsIgnoreCase(ds.getDatasourceType())) {
            return List.of();
        }
        DataSource dataSource = manager.getSqlDataSource(ds);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        return jdbc.queryForList(
                "SELECT TABLE_NAME, TABLE_COMMENT, TABLE_TYPE "
                        + "FROM INFORMATION_SCHEMA.TABLES "
                        + "WHERE TABLE_SCHEMA = ? AND TABLE_TYPE = 'BASE TABLE' "
                        + "ORDER BY TABLE_NAME",
                ds.getConnectionConfig().getDatabase());
    }

    private InsightDatasource resolveDatasource(DynamicDatasourceManager manager) {
        for (InsightDatasource d : manager.getDatasourceCache().values()) {
            return d;
        }
        return null;
    }

    private String buildCountSql(String sql) {
        String upper = sql.toUpperCase();
        int fromIdx = upper.indexOf(" FROM ");
        if (fromIdx == -1) return "SELECT COUNT(*) FROM (" + sql + ") AS _cnt";

        int orderIdx = upper.lastIndexOf(" ORDER ");
        int limitIdx = upper.lastIndexOf(" LIMIT ");
        int offsetIdx = upper.lastIndexOf(" OFFSET ");

        int endIdx = upper.length();
        endIdx = orderIdx > 0 ? Math.min(endIdx, orderIdx) : endIdx;
        endIdx = limitIdx > 0 ? Math.min(endIdx, limitIdx) : endIdx;
        endIdx = offsetIdx > 0 ? Math.min(endIdx, offsetIdx) : endIdx;

        String body = sql.substring(fromIdx, endIdx);
        return "SELECT COUNT(*) " + body;
    }

    SqlSegment buildSelectSql(QueryRequest request) {
        StringBuilder sql = new StringBuilder();
        List<Object> params = new ArrayList<>();

        // SELECT
        if (request.getSelectFields() != null && !request.getSelectFields().isEmpty()) {
            sql.append("SELECT ").append(String.join(", ", request.getSelectFields()));
        } else {
            sql.append("SELECT *");
        }
        sql.append(" FROM ").append(request.getTableName());

        // WHERE
        if (request.getFilters() != null && !request.getFilters().isEmpty()) {
            sql.append(" WHERE ");
            for (int i = 0; i < request.getFilters().size(); i++) {
                QueryRequest.FilterCondition f = request.getFilters().get(i);
                if (i > 0) sql.append(" ").append(f.getCombine() != null ? f.getCombine() : "AND").append(" ");
                sql.append(buildWhereClause(f, params));
            }
        }

        // ORDER BY
        if (request.getOrders() != null && !request.getOrders().isEmpty()) {
            sql.append(" ORDER BY ");
            for (int i = 0; i < request.getOrders().size(); i++) {
                QueryRequest.SortCondition o = request.getOrders().get(i);
                if (i > 0) sql.append(", ");
                sql.append(o.getField()).append(" ").append(
                        "DESC".equalsIgnoreCase(o.getDirection()) ? "DESC" : "ASC");
            }
        }

        return new SqlSegment(sql.toString(), params);
    }

    private String buildWhereClause(QueryRequest.FilterCondition f, List<Object> params) {
        String col = f.getColumn();
        String op = f.getOperator().toUpperCase();

        return switch (op) {
            case "EQ" -> { params.add(f.getValue()); yield col + " = ?"; }
            case "NE" -> { params.add(f.getValue()); yield col + " != ?"; }
            case "GT" -> { params.add(f.getValue()); yield col + " > ?"; }
            case "GTE" -> { params.add(f.getValue()); yield col + " >= ?"; }
            case "LT" -> { params.add(f.getValue()); yield col + " < ?"; }
            case "LTE" -> { params.add(f.getValue()); yield col + " <= ?"; }
            case "LIKE" -> { params.add("%" + f.getValue() + "%"); yield col + " LIKE ?"; }
            case "IN" -> {
                Object[] vals = toArray(f.getValue());
                params.add(vals);
                yield col + " IN (" + placeholders(vals.length) + ")";
            }
            case "NOT_IN" -> {
                Object[] vals = toArray(f.getValue());
                params.add(vals);
                yield col + " NOT IN (" + placeholders(vals.length) + ")";
            }
            case "IS_NULL" -> col + " IS NULL";
            case "IS_NOT_NULL" -> col + " IS NOT NULL";
            case "BETWEEN" -> {
                params.add(f.getValue());
                params.add(f.getValue2());
                yield col + " BETWEEN ? AND ?";
            }
            default -> col + " = ?";
        };
    }

    private Object[] toArray(Object v) {
        if (v instanceof List) return ((List<?>) v).toArray();
        if (v.getClass().isArray()) {
            if (v instanceof Object[]) return (Object[]) v;
            Object[] arr = new Object[java.lang.reflect.Array.getLength(v)];
            for (int i = 0; i < arr.length; i++) arr[i] = java.lang.reflect.Array.get(v, i);
            return arr;
        }
        return new Object[]{v};
    }

    private String placeholders(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            if (i > 0) sb.append(", ");
            sb.append("?");
        }
        return sb.toString();
    }

    record SqlSegment(String sql, List<Object> params) {}
}
