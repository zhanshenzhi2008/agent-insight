package com.llm.insight.explorer.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "insight_column_config")
@CompoundIndex(name = "table_column_idx", def = "{'datasourceKey': 1, 'tableName': 1, 'columnName': 1}", unique = true)
public class InsightColumnConfig {

    @Id
    private String id;

    private String datasourceKey;
    private String tableName;

    /** 字段名（数据库中的实际列名） */
    private String columnName;

    /** 展示列名 */
    private String displayName;

    /** 字段类型：STRING / NUMBER / DATETIME / BOOLEAN / JSON / TEXT / ENUM */
    private String dataType;

    /** 渲染类型：TEXT / LINK / TAG / MONEY / DATE / DATETIME / BOOLEAN / JSON / IMAGE / HTML / RICHTEXT */
    private String renderType;

    /** 列宽 */
    private Integer width;

    /** 是否固定左侧 */
    private Boolean fixedLeft;

    /** 是否固定右侧 */
    private Boolean fixedRight;

    /** 列顺序 */
    private Integer orderIndex;

    /** 是否可排序 */
    private Boolean sortable;

    /** 是否可筛选 */
    private Boolean filterable;

    /** 是否可点击跳转（配合 linkPattern 使用） */
    private String linkPattern;

    /** 数字格式：#,##0.00 等 */
    private String numberFormat;

    /** 时间格式：yyyy-MM-dd 等 */
    private String dateFormat;

    /** 枚举值映射 {value: label} */
    private Map<String, String> enumLabels;

    /** 成功/失败/挂起 等特殊值映射 */
    private Map<String, String> valueLabels;

    /** TAG 颜色映射 */
    private Map<String, String> tagColors;

    /** 精度（小数位） */
    private Integer precision;

    /** 超过此长度截断显示 */
    private Integer maxDisplayLength;

    /** 是否隐藏（不在列表展示但可用于详情） */
    private Boolean hidden;

    /** 是否为时间字段（用于时间范围筛选默认绑定） */
    private Boolean timeField;

    private String createdBy;
    private LocalDateTime createdAt;
    private String updatedBy;
    private LocalDateTime updatedAt;
}
