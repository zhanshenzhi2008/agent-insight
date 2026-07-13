package com.llm.insight.explorer.engine;

import com.llm.insight.explorer.document.InsightColumnConfig;
import com.llm.insight.explorer.document.InsightDatasource;
import com.llm.insight.explorer.service.ConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 列智能分析服务。
 * 从外部表采样数据，自动推断每列的类型、渲染器、格式规则和标签映射。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ColumnAnalyzerService {

    private final DynamicDatasourceManager dsManager;
    private final ConfigService configService;

    /**
     * 分析指定表的列，生成推荐配置。
     * 采样 100 条记录，推断每列的特征。
     */
    public List<AnalyzedColumn> analyze(String datasourceKey, String tableName) {
        InsightDatasource ds = dsManager.getDatasource(datasourceKey);
        if (ds == null) throw new IllegalArgumentException("数据源不存在: " + datasourceKey);

        String type = ds.getDatasourceType().toUpperCase();

        if ("MONGODB".equals(type)) {
            return analyzeMongo(ds, tableName);
        } else {
            return analyzeSql(ds, tableName);
        }
    }

    // ===== SQL 数据库分析 =====

    private List<AnalyzedColumn> analyzeSql(InsightDatasource ds, String tableName) {
        dsManager.cacheDatasource(ds);
        DataSource dataSource = dsManager.getSqlDataSource(ds);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        String schema = ds.getConnectionConfig().getDatabase();
        String type = ds.getDatasourceType();

        // 1. 获取列元数据
        List<Map<String, Object>> columnMeta = getSqlColumnMeta(jdbc, type, schema, tableName);

        // 2. 采样数据（100 条）
        List<Map<String, Object>> sample = jdbc.queryForList(
                "SELECT * FROM " + tableName + " LIMIT 100");

        if (sample.isEmpty()) {
            // 无数据时仅返回元数据
            return columnMeta.stream()
                    .map(m -> AnalyzedColumn.builder()
                            .columnName(String.valueOf(m.get("COLUMN_NAME")))
                            .displayName(String.valueOf(m.get("COLUMN_NAME")))
                            .dataType(inferTypeFromSqlType(String.valueOf(m.get("DATA_TYPE"))))
                            .renderType("TEXT")
                            .nullRatio(0.0)
                            .distinctCount(0L)
                            .build())
                    .toList();
        }

        // 3. 逐列分析
        Map<String, Object> firstRow = sample.get(0);
        List<AnalyzedColumn> result = new ArrayList<>();

        for (Map.Entry<String, Object> entry : firstRow.entrySet()) {
            String colName = entry.getKey();
            String sampleVal = entry.getValue() != null ? String.valueOf(entry.getValue()) : null;

            // 列元数据
            Map<String, Object> meta = columnMeta.stream()
                    .filter(m -> colName.equals(m.get("COLUMN_NAME")))
                    .findFirst()
                    .orElse(Map.of());

            // 采样该列所有值
            List<String> colValues = sample.stream()
                    .map(row -> row.get(colName))
                    .map(v -> v != null ? String.valueOf(v) : null)
                    .toList();

            AnalyzedColumn analyzed = analyzeColumn(colName, colValues, sample.size(),
                    String.valueOf(meta.getOrDefault("COLUMN_COMMENT", "")),
                    String.valueOf(meta.get("DATA_TYPE")));
            result.add(analyzed);
        }

        return result;
    }

    private List<Map<String, Object>> getSqlColumnMeta(JdbcTemplate jdbc, String dbType,
                                                       String schema, String tableName) {
        String type = dbType.toUpperCase();
        if ("MYSQL".equals(type)) {
            return jdbc.queryForList(
                    "SELECT COLUMN_NAME, DATA_TYPE, COLUMN_COMMENT, IS_NULLABLE "
                            + "FROM INFORMATION_SCHEMA.COLUMNS "
                            + "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? ORDER BY ORDINAL_POSITION",
                    schema, tableName);
        } else if ("POSTGRESQL".equals(type)) {
            // 标准 JDBC ? 占位符，兼容所有 PostgreSQL 版本
            // 列注释通过 pg_catalog.col_description() + regclass OID 获取
            return jdbc.queryForList(
                    "SELECT c.column_name::text AS COLUMN_NAME, "
                            + "c.data_type AS DATA_TYPE, "
                            + "pg_catalog.col_description(a.attrelid, a.attnum)::text AS COLUMN_COMMENT, "
                            + "c.is_nullable AS IS_NULLABLE "
                            + "FROM information_schema.columns c "
                            + "JOIN pg_catalog.pg_attribute a "
                            + "  ON a.attname = c.column_name "
                            + "  AND a.attrelid = ?::regclass "
                            + "WHERE c.table_schema = 'public' AND c.table_name = ? "
                            + "ORDER BY c.ordinal_position",
                    tableName, tableName);
        }
        return List.of();
    }

    // ===== MongoDB 分析 =====

    private List<AnalyzedColumn> analyzeMongo(InsightDatasource ds, String tableName) {
        dsManager.cacheDatasource(ds);
        var mongo = dsManager.getMongoTemplate(ds);
        var docs = mongo.find(
                new org.springframework.data.mongodb.core.query.Query()
                        .limit(100),
                org.bson.Document.class, tableName);

        if (docs == null || docs.isEmpty()) {
            log.warn("[analyzeMongo] collection={} 采样 0 条文档，触发 fallback", tableName);
            // Fallback 1: 已有字段配置（用户已配置过的列）
            List<InsightColumnConfig> configured = configService.getColumnConfigs(ds.getDatasourceKey(), tableName);
            if (configured != null && !configured.isEmpty()) {
                log.info("[analyzeMongo] fallback 到 insight_column_config，{} 条", configured.size());
                return configured.stream()
                        .map(c -> AnalyzedColumn.builder()
                                .columnName(c.getColumnName())
                                .displayName(c.getDisplayName() != null ? c.getDisplayName() : c.getColumnName())
                                .dataType(c.getDataType() != null ? c.getDataType() : "STRING")
                                .renderType(c.getRenderType() != null ? c.getRenderType() : "TEXT")
                                .nullRatio(0.0)
                                .distinctCount(0L)
                                .distinctRatio(0.0)
                                .allSame(false)
                                .tagColors(Map.of())
                                .valueLabels(Map.of())
                                .topValues(List.of())
                                .build())
                        .toList();
            }
            // Fallback 2: 完全无数据 → 抛出友好异常，前端 message.error 会显示
            throw new IllegalStateException(
                    "数据源 [" + ds.getDatasourceKey() + "] 的 collection [" + tableName
                            + "] 当前 0 条文档，且未配置字段。请先在外部数据源写入数据，或在「表配置 → 字段配置」中手动添加列。");
        }

        // 收集所有字段（含嵌套）
        Set<String> allFields = new LinkedHashSet<>();
        for (var doc : docs) {
            collectFields("", doc, allFields);
        }

        List<AnalyzedColumn> result = new ArrayList<>();
        for (String field : allFields) {
            List<String> values = docs.stream()
                    .map(doc -> getNestedValue(doc, field))
                    .map(v -> v != null ? String.valueOf(v) : null)
                    .toList();

            AnalyzedColumn analyzed = analyzeColumn(
                    field, values, docs.size(),
                    field, inferMongoType(values.stream().findFirst().orElse(null)));
            result.add(analyzed);
        }

        return result;
    }

    private void collectFields(String prefix, org.bson.Document doc, Set<String> set) {
        for (String key : doc.keySet()) {
            String fullKey = prefix.isEmpty() ? key : prefix + "." + key;
            Object val = doc.get(key);
            set.add(fullKey);
            if (val instanceof org.bson.Document nested) {
                collectFields(fullKey, nested, set);
            }
        }
    }

    private Object getNestedValue(org.bson.Document doc, String field) {
        String[] parts = field.split("\\.");
        Object current = doc;
        for (String p : parts) {
            if (current instanceof org.bson.Document d) {
                current = d.get(p);
            } else {
                return null;
            }
        }
        return current;
    }

    // ===== 核心分析逻辑 =====

    private AnalyzedColumn analyzeColumn(String colName, List<String> values,
                                         int totalRows, String comment, String rawType) {
        // 过滤 null
        List<String> nonNull = values.stream().filter(Objects::nonNull).toList();
        int nullCount = totalRows - nonNull.size();
        double nullRatio = nullCount / (double) totalRows;

        // 统计
        long distinct = nonNull.stream().distinct().count();
        boolean allSame = distinct <= 1;

        // 最常见的值（用于枚举标签推断）
        Map<String, Long> valueFreq = nonNull.stream()
                .collect(java.util.stream.Collectors.groupingBy(v -> v, java.util.stream.Collectors.counting()));
        List<Map.Entry<String, Long>> topValues = valueFreq.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(20)
                .toList();

        // 类型推断
        String dataType = inferDataType(nonNull, allSame);
        String renderType = inferRenderType(dataType, nonNull, allSame, nullRatio, distinct, totalRows);

        // 格式推断
        String dateFormat = inferDateFormat(nonNull);
        String numberFormat = inferNumberFormat(nonNull, dataType);
        Map<String, String> tagColors = inferTagColors(topValues);
        Map<String, String> valueLabels = inferValueLabels(dataType, topValues, distinct);

        // 显示名
        String displayName = comment != null && !comment.isBlank()
                ? comment : cnName(colName);

        // 是否时间字段
        boolean isTimeField = dataType.equals("DATETIME") && (
                colName.toLowerCase().contains("time")
                        || colName.toLowerCase().contains("date")
                        || colName.toLowerCase().endsWith("_at")
                        || colName.toLowerCase().endsWith("_time")
                        || colName.toLowerCase().equals("create_time")
                        || colName.toLowerCase().equals("update_time"));

        return AnalyzedColumn.builder()
                .columnName(colName)
                .displayName(displayName)
                .dataType(dataType)
                .renderType(renderType)
                .nullRatio(nullRatio)
                .distinctCount(distinct)
                .distinctRatio(distinct / (double) totalRows)
                .allSame(allSame)
                .dateFormat(dateFormat)
                .numberFormat(numberFormat)
                .tagColors(tagColors)
                .valueLabels(valueLabels)
                .timeField(isTimeField)
                .topValues(topValues.stream().limit(10)
                        .map(e -> {
                            Map<String, Object> m = new java.util.LinkedHashMap<>();
                            m.put("value", e.getKey());
                            m.put("count", e.getValue());
                            m.put("ratio", String.format("%.1f%%", e.getValue() * 100.0 / totalRows));
                            return m;
                        })
                        .toList())
                .build();
    }

    private String inferDataType(List<String> values, boolean allSame) {
        if (values.isEmpty()) return "STRING";

        boolean allNumber = true;
        boolean allBoolean = true;
        boolean allDate = true;

        for (String v : values) {
            if (allNumber && !isNumber(v)) allNumber = false;
            if (allBoolean && !isBoolean(v)) allBoolean = false;
            if (allDate && !isDate(v)) allDate = false;
            if (!allNumber && !allBoolean && !allDate) break;
        }

        if (allBoolean) return "BOOLEAN";
        if (allNumber) return "NUMBER";
        if (allDate) return "DATETIME";
        if (isJsonLike(values)) return "JSON";
        if (allSame) return "ENUM";
        return "STRING";
    }

    private String inferRenderType(String dataType, List<String> values,
                                   boolean allSame, double nullRatio, long distinct, int totalRows) {
        if (dataType.equals("BOOLEAN")) return "BOOLEAN";
        if (dataType.equals("DATETIME")) return "DATETIME";
        if (dataType.equals("NUMBER")) {
            if (isMoneyColumn(values)) return "MONEY";
            return "TEXT";
        }
        if (dataType.equals("JSON")) return "JSON";

        // STRING 推断
        if (allSame) return "TAG";
        if (distinct <= 5 && distinct >= 2) return "TAG"; // 低基数用 TAG

        String lowerSample = values.stream().findFirst().orElse("");
        if (lowerSample.startsWith("http://") || lowerSample.startsWith("https://")) {
            return "LINK";
        }
        if (lowerSample.matches(".*\\.(jpg|jpeg|png|gif|svg|webp)")) {
            return "IMAGE";
        }

        // 高基数+中等唯一率 → 文本
        if (distinct / (double) totalRows > 0.5) return "TEXT";

        return "TEXT";
    }

    private String inferDateFormat(List<String> values) {
        if (values.isEmpty()) return null;
        String v = values.get(0);
        if (v == null) return null;

        if (v.matches("\\d{4}-\\d{2}-\\d{2}")) return "yyyy-MM-dd";
        if (v.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}")) return "yyyy-MM-dd HH:mm:ss";
        if (v.matches("\\d{4}/\\d{2}/\\d{2}")) return "yyyy/MM/dd";
        if (v.matches("\\d{13}")) return "epoch_ms";
        if (v.matches("\\d{10}")) return "epoch_s";
        return null;
    }

    private String inferNumberFormat(List<String> values, String dataType) {
        if (!dataType.equals("NUMBER") || values.isEmpty()) return null;
        String v = values.stream().filter(Objects::nonNull).findFirst().orElse("");
        // 超过千位有逗号
        try {
            double d = Double.parseDouble(v.replace(",", ""));
            if (d >= 1000) return "#,##0.##";
            if (d < 1) return "0.######";
        } catch (NumberFormatException ignored) {}
        return null;
    }

    private boolean isMoneyColumn(List<String> values) {
        List<String> keywords = List.of(
                "price", "cost", "amount", "money", "fee", "total", "sum",
                "budget", "salary", "revenue", "profit", "discount"
        );
        return false; // 后续可增强
    }

    private Map<String, String> inferTagColors(List<Map.Entry<String, Long>> topValues) {
        Map<String, String> colors = new LinkedHashMap<>();
        String[] palette = {
                "success", "processing", "error", "warning",
                "default", "magenta", "red", "volcano",
                "orange", "gold", "lime", "green",
                "cyan", "blue", "geekblue", "purple"
        };
        int i = 0;
        for (var e : topValues) {
            colors.put(e.getKey(), palette[i++ % palette.length]);
            if (i >= 8) break;
        }
        return colors;
    }

    private Map<String, String> inferValueLabels(String dataType,
                                                 List<Map.Entry<String, Long>> topValues, long distinct) {
        if (!dataType.equals("BOOLEAN") && distinct > 10) return Map.of();
        if (dataType.equals("BOOLEAN")) {
            Map<String, String> labels = new LinkedHashMap<>();
            for (var e : topValues) {
                String v = e.getKey().toLowerCase();
                if (v.equals("1") || v.equals("true") || v.equals("yes") || v.equals("y") || v.equals("success") || v.equals("success")) {
                    labels.put(e.getKey(), "成功");
                } else if (v.equals("0") || v.equals("false") || v.equals("no") || v.equals("n") || v.equals("fail") || v.equals("error")) {
                    labels.put(e.getKey(), "失败");
                } else if (v.equals("pending") || v.equals("running") || v.equals("processing")) {
                    labels.put(e.getKey(), "进行中");
                }
            }
            return labels;
        }
        return Map.of();
    }

    private boolean isNumber(String v) {
        if (v == null || v.isBlank()) return false;
        try { Double.parseDouble(v.replace(",", "")); return true; } catch (Exception e) { return false; }
    }

    private boolean isBoolean(String v) {
        if (v == null) return false;
        String l = v.toLowerCase().trim();
        return l.equals("true") || l.equals("false")
                || l.equals("0") || l.equals("1")
                || l.equals("yes") || l.equals("no")
                || l.equals("y") || l.equals("n")
                || l.equals("success") || l.equals("fail")
                || l.equals("error") || l.equals("pending");
    }

    private boolean isDate(String v) {
        if (v == null) return false;
        return v.matches("\\d{4}-\\d{2}-\\d{2}")
                || v.matches("\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}")
                || v.matches("\\d{4}/\\d{2}/\\d{2}")
                || v.matches("\\d{13}")
                || v.matches("\\d{10}");
    }

    private boolean isJsonLike(List<String> values) {
        if (values.isEmpty()) return false;
        String v = values.stream().filter(Objects::nonNull).findFirst().orElse("");
        return (v.startsWith("{") && v.endsWith("}"))
                || (v.startsWith("[") && v.endsWith("]"));
    }

    private String inferTypeFromSqlType(String sqlType) {
        if (sqlType == null) return "STRING";
        String t = sqlType.toLowerCase();
        if (t.contains("int") || t.contains("float") || t.contains("double")
                || t.contains("decimal") || t.contains("numeric") || t.contains("real")) {
            return "NUMBER";
        }
        if (t.contains("bool")) return "BOOLEAN";
        if (t.contains("date") || t.contains("time")) return "DATETIME";
        if (t.contains("text") || t.contains("char") || t.contains("varchar")) return "STRING";
        if (t.contains("json")) return "JSON";
        return "STRING";
    }

    private String inferMongoType(Object val) {
        if (val == null) return "STRING";
        if (val instanceof Number) return "NUMBER";
        if (val instanceof Boolean) return "BOOLEAN";
        if (val instanceof java.util.Date) return "DATETIME";
        if (val instanceof List) return "STRING";
        if (val instanceof org.bson.Document) return "JSON";
        return "STRING";
    }

    private String cnName(String en) {
        if (en == null || en.isEmpty()) return en;
        // 简单中文化：下划线分隔、转驼峰
        return Arrays.stream(en.split("[_]"))
                .map(s -> s.isEmpty() ? "" : s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase())
                .reduce((a, b) -> a + b)
                .orElse(en);
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class AnalyzedColumn {
        private String columnName;
        private String displayName;
        private String dataType;
        private String renderType;
        private Double nullRatio;
        private Long distinctCount;
        private Double distinctRatio;
        private Boolean allSame;
        private String dateFormat;
        private String numberFormat;
        private Map<String, String> tagColors;
        private Map<String, String> valueLabels;
        private Boolean timeField;
        private List<Map<String, Object>> topValues;
    }
}
