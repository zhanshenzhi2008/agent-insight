# Metadata-Driven Data Exploration System - Technical Design

| 版本 | 日期 | 作者 | 备注 |
|------|------|------|------|
| v1.0 | 2026-06-26 | - | 初稿 |

---

## 1. Overview

### 1.1 System Purpose

A generic metadata-driven data exploration system that enables querying tables from **any external database** (MySQL, PostgreSQL, MongoDB) without hardcoded entity classes. All table structures and column definitions are stored as metadata in MongoDB, making the system highly reusable across different business domains.

### 1.2 Core Design Principles

```
┌─────────────────────────────────────────────────────────────────┐
│                    Design Principles                              │
├─────────────────────────────────────────────────────────────────┤
│ 1. Metadata-First: All schema driven by config, not code      │
│ 2. Zero Entity: No hardcoded POJOs for target tables          │
│ 3. Multi-Datasource: Unified query across MySQL/PG/MongoDB     │
│ 4. Dynamic UI: Columns rendered from API, not compile-time    │
│ 5. Reusable: Same engine, different metadata = different apps  │
└─────────────────────────────────────────────────────────────────┘
```

---

## 2. Metadata Model (MongoDB Collections)

### 2.1 Collection Overview

```
┌──────────────────────────────────────────────────────────────────┐
│                      MongoDB Metadata Store                       │
├──────────────────────────────────────────────────────────────────┤
│  insight_datasource       - Data source connection configs      │
│  insight_table_config    - Table/collection exposure settings   │
│  insight_column_config   - Column definitions & display rules   │
│  insight_query_config    - Saved query templates                │
│  insight_query_history   - Query execution audit log            │
└──────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌──────────────────────────────────────────────────────────────────┐
│                    External Data Sources                           │
├─────────────┬─────────────┬─────────────┬────────────────────────┤
│   MySQL     │  PostgreSQL │   MongoDB   │   (Extensible)         │
│ 172.18.2.x  │  172.18.3.x│  172.18.4.x│                        │
└─────────────┴─────────────┴─────────────┴────────────────────────┘
```

### 2.2 insight_datasource

**Purpose**: Stores connection information for all external databases that can be queried.

```json
{
  "_id": ObjectId("..."),
  "datasourceKey": "prod_mysql_orders",
  "datasourceName": "Production Orders DB",
  "datasourceType": "MYSQL",
  "status": "ACTIVE",
  "connectionConfig": {
    "host": "172.18.2.10",
    "port": 3306,
    "database": "orders_db",
    "username": "readonly_user",
    "password": "encrypted_password",
    "connectionPoolSize": 5,
    "connectionTimeout": 30000,
    "socketTimeout": 60000,
    "extraParams": {
      "useSSL": false,
      "serverTimezone": "Asia/Shanghai"
    }
  },
  "description": "Primary orders database - read only",
  "tags": ["production", "orders", "mysql"],
  "allowedTables": ["orders", "order_items", "customers", "products"],
  "deniedTables": [],
  "createdBy": "admin",
  "createdAt": ISODate("2026-01-01T00:00:00Z"),
  "updatedBy": "admin",
  "updatedAt": ISODate("2026-01-01T00:00:00Z")
}
```

**Field Definitions**:

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| datasourceKey | String | Yes | Unique identifier for the datasource |
| datasourceName | String | Yes | Human-readable name |
| datasourceType | Enum | Yes | MYSQL / POSTGRESQL / MONGODB |
| status | Enum | Yes | ACTIVE / INACTIVE / ERROR |
| connectionConfig | Object | Yes | Connection parameters |
| tags | Array[String] | No | For categorization/filtering |
| allowedTables | Array[String] | No | Whitelist of accessible tables |
| deniedTables | Array[String] | No | Blacklist (deniedTables > allowedTables) |

**Indexes**:
```javascript
db.insight_datasource.createIndex({ "datasourceKey": 1 }, { unique: true })
db.insight_datasource.createIndex({ "status": 1, "datasourceType": 1 })
db.insight_datasource.createIndex({ "tags": 1 })
```

### 2.3 insight_table_config

**Purpose**: Defines which tables/collections from each datasource are exposed for querying.

```json
{
  "_id": ObjectId("..."),
  "tableKey": "prod_orders",
  "tableName": "orders",
  "datasourceKey": "prod_mysql_orders",
  "displayName": "Order List",
  "description": "Main order transactions",
  "tableType": "TABLE",
  "status": "ACTIVE",
  "queryConfig": {
    "defaultSortField": "created_at",
    "defaultSortOrder": "desc",
    "defaultPageSize": 20,
    "maxPageSize": 500,
    "enableExport": true,
    "exportFormats": ["CSV", "EXCEL", "JSON"],
    "enableAggregate": false,
    "rowLevelSecurity": {
      "field": "tenant_id",
      "valueFrom": "current_user"
    }
  },
  "metadata": {
    "category": "Orders",
    "icon": "shopping-cart",
    "color": "#1890ff"
  },
  "createdBy": "admin",
  "createdAt": ISODate("2026-01-01T00:00:00Z"),
  "updatedBy": "admin",
  "updatedAt": ISODate("2026-01-01T00:00:00Z")
}
```

**Field Definitions**:

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| tableKey | String | Yes | Unique identifier (datasourceKey.tableName) |
| tableName | String | Yes | Actual table/collection name in database |
| datasourceKey | String | Yes | Reference to insight_datasource |
| displayName | String | Yes | UI display name |
| tableType | Enum | Yes | TABLE / VIEW / QUERY (saved SQL) |
| queryConfig | Object | No | Query behavior settings |
| rowLevelSecurity | Object | No | Row-level access control rules |

### 2.4 insight_column_config

**Purpose**: Defines column metadata including display name, type, format, width, and render rules.

```json
{
  "_id": ObjectId("..."),
  "columnKey": "prod_orders.order_id",
  "tableKey": "prod_orders",
  "columnName": "order_id",
  "displayName": "Order ID",
  "description": "Unique order identifier",
  "dataType": "BIGINT",
  "columnType": "DIMENSION",
  "sortable": true,
  "filterable": true,
  "required": false,
  "nullable": true,
  "displayOrder": 1,
  "width": 100,
  "minWidth": 60,
  "maxWidth": 300,
  "fixedPosition": "LEFT",
  "formatConfig": {
    "type": "TEXT",
    "pattern": null,
    "prefix": null,
    "suffix": null,
    "truncateLength": 50
  },
  "renderConfig": {
    "type": "LINK",
    "props": {
      "href": "/orders/{value}",
      "target": "_blank"
    }
  },
  "filterConfig": {
    "type": "INPUT",
    "operator": "LIKE",
    "placeholder": "Search order ID..."
  },
  "aggregateConfig": {
    "enableSum": false,
    "enableAvg": false,
    "enableCount": true
  },
  "defaultValue": null,
  "enumValues": null,
  "createdBy": "admin",
  "createdAt": ISODate("2026-01-01T00:00:00Z")
}
```

**Column Data Types (dataType)**:

| Type | Description | Format Config Options |
|------|-------------|---------------------|
| VARCHAR | Text/String | truncateLength |
| TEXT | Long text | truncateLength, isHtml |
| INT | Integer | decimalPlaces=0 |
| BIGINT | Large integer | decimalPlaces=0 |
| DECIMAL | Decimal number | decimalPlaces, thousandsSeparator |
| DATE | Date | pattern: "yyyy-MM-dd" |
| DATETIME | Date + Time | pattern: "yyyy-MM-dd HH:mm:ss" |
| TIMESTAMP | Unix timestamp | pattern (auto-convert) |
| BOOLEAN | Boolean | trueLabel/falseLabel |
| ENUM | Fixed values | enumValues array |
| JSON | JSON object | isCollapsible |
| MONEY | Currency | currencySymbol, decimalPlaces |
| PERCENT | Percentage | decimalPlaces |
| HTML | HTML content | isRaw (sanitize) |

**Column Types (columnType)**:

| Type | Description |
|------|-------------|
| DIMENSION | Categorical field for grouping/filtering |
| MEASURE | Numeric field for aggregation |
| PRIMARY_KEY | Unique identifier column |
| FOREIGN_KEY | Reference to other tables |
| SYSTEM | System fields (created_at, updated_at) |

**Render Types (renderConfig.type)**:

| Type | Description | Props |
|------|-------------|-------|
| TEXT | Plain text | truncateLength |
| LINK | Clickable link | href, target |
| IMAGE | Image thumbnail | width, height, preview |
| TAG | Colored tag/badge | colorField, colorMap |
| STATUS | Status badge | statusMap |
| BOOLEAN | Yes/No indicator | trueLabel, falseLabel |
| ENUM | Value-to-label mapping | valueLabelMap |
| MONEY | Currency formatting | symbol, decimalPlaces |
| DATE | Date formatting | pattern |
| JSON | JSON viewer | expandDepth |
| CUSTOM | Custom renderer | rendererName |

### 2.5 insight_query_config

**Purpose**: Stores reusable query templates and saved queries.

```json
{
  "_id": ObjectId("..."),
  "queryKey": "prod_orders_pending",
  "queryName": "Pending Orders",
  "tableKey": "prod_orders",
  "queryType": "SAVED_QUERY",
  "description": "Orders awaiting fulfillment",
  "sqlTemplate": "SELECT * FROM orders WHERE status = 'PENDING' AND created_at >= ?",
  "parameters": [
    {
      "name": "startDate",
      "dataType": "DATE",
      "required": false,
      "defaultValue": "2026-01-01",
      "label": "Start Date",
      "placeholder": "Select start date"
    }
  ],
  "resultConfig": {
    "defaultColumns": ["order_id", "customer_name", "total_amount", "status"],
    "sortField": "created_at",
    "sortOrder": "desc"
  },
  "shareConfig": {
    "public": false,
    "allowedUsers": ["analyst1", "analyst2"],
    "allowedRoles": ["ANALYST"]
  },
  "executionCount": 156,
  "lastExecutedAt": ISODate("2026-06-25T10:30:00Z"),
  "createdBy": "analyst1",
  "createdAt": ISODate("2026-01-01T00:00:00Z")
}
```

### 2.6 insight_query_history

**Purpose**: Audit log for all query executions.

```json
{
  "_id": ObjectId("..."),
  "executionId": "exec_20260626_001",
  "queryId": "prod_orders_pending",
  "tableKey": "prod_orders",
  "datasourceKey": "prod_mysql_orders",
  "queryType": "SAVED_QUERY",
  "sqlExecuted": "SELECT * FROM orders WHERE status = 'PENDING'...",
  "parameters": {
    "startDate": "2026-01-01",
    "endDate": "2026-06-26"
  },
  "filterConditions": [
    {"field": "status", "operator": "=", "value": "PENDING"}
  ],
  "sortField": "created_at",
  "sortOrder": "desc",
  "page": 0,
  "pageSize": 20,
  "result": {
    "totalCount": 1250,
    "returnedCount": 20,
    "executionTimeMs": 145,
    "cached": false
  },
  "error": null,
  "clientInfo": {
    "ip": "192.168.1.100",
    "userAgent": "Mozilla/5.0...",
    "appId": "dashboard"
  },
  "userId": "user123",
  "username": "analyst1",
  "executedAt": ISODate("2026-06-26T08:15:00Z")
}
```

---

## 3. Dynamic Query Engine

### 3.1 Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────────┐
│                      Dynamic Query Engine                                │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  ┌──────────────┐     ┌──────────────┐     ┌──────────────────────┐    │
│  │ Query Router │────▶│  Executor    │────▶│  Result Transformer  │    │
│  │              │     │  Factory     │     │                      │    │
│  └──────────────┘     └──────────────┘     └──────────────────────┘    │
│         │                    │                        │                  │
│         │                    ▼                        ▼                  │
│         │             ┌──────────────┐     ┌──────────────────────┐    │
│         │             │ Connection   │     │  Column Config       │    │
│         │             │  Pool        │     │  Mapper              │    │
│         │             └──────────────┘     └──────────────────────┘    │
│         │                    │                        │                  │
│         └────────────────────┴────────────────────────┘                  │
│                                                                          │
│  ┌──────────────────────────────────────────────────────────────────┐   │
│  │                    Query Executor Implementations                 │   │
│  ├────────────────┬────────────────┬─────────────────────────────────┤   │
│  │ MySQLExecutor  │ PostgresExecutor│ MongoExecutor                  │   │
│  │ (JdbcTemplate) │ (JdbcTemplate) │ (MongoTemplate)                │   │
│  └────────────────┴────────────────┴─────────────────────────────────┘   │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

### 3.2 Core Interfaces

```java
// Query request DTO
public record DynamicQueryRequest(
    String tableKey,
    List<ColumnSelection> columns,
    List<FilterCondition> filters,
    List<SortField> sorts,
    int page,
    int pageSize,
    String userId
) {}

public record FilterCondition(
    String columnName,
    FilterOperator operator,
    Object value,
    LogicType logicType  // AND / OR
) {}

public record SortField(
    String columnName,
    SortOrder order
) {}

// Query result DTO
public record DynamicQueryResult(
    List<Map<String, Object>> data,
    List<ColumnMetadata> columns,
    long totalCount,
    int page,
    int pageSize,
    int totalPages,
    long executionTimeMs,
    String executionId
) {}

public record ColumnMetadata(
    String columnKey,
    String columnName,
    String displayName,
    String dataType,
    String renderType,
    int width,
    boolean sortable,
    boolean filterable,
    Map<String, Object> formatConfig,
    Map<String, Object> renderConfig
) {}
```

### 3.3 Query Executor Factory

```java
@Service
@RequiredArgsConstructor
public class QueryExecutorFactory {
    
    private final MySqlQueryExecutor mySqlExecutor;
    private final PostgresQueryExecutor postgresExecutor;
    private final MongoQueryExecutor mongoExecutor;
    private final DatasourceConfigService datasourceConfigService;
    
    public QueryExecutor getExecutor(String datasourceKey) {
        DatasourceConfig config = datasourceConfigService.getByKey(datasourceKey);
        
        return switch (config.getDatasourceType()) {
            case MYSQL -> mySqlExecutor;
            case POSTGRESQL -> postgresExecutor;
            case MONGODB -> mongoExecutor;
            default -> throw new UnsupportedDatasourceException(config.getDatasourceType());
        };
    }
}
```

### 3.4 MySQL/PostgreSQL Executor

```java
@Component
@RequiredArgsConstructor
public class MySqlQueryExecutor implements QueryExecutor {
    
    private final DataSourcePoolManager dataSourcePoolManager;
    private final SqlBuilder sqlBuilder;
    
    @Override
    public DynamicQueryResult execute(DynamicQueryRequest request, DatasourceConfig config) {
        long startTime = System.currentTimeMillis();
        
        // 1. Build SQL dynamically
        SqlStatement sql = sqlBuilder.build(request, config);
        
        // 2. Get connection from pool
        DataSource ds = dataSourcePoolManager.getDataSource(config.getDatasourceKey());
        
        // 3. Execute with JdbcTemplate
        NamedParameterJdbcTemplate template = new NamedParameterJdbcTemplate(ds);
        
        Map<String, Object> params = extractParams(request);
        
        // Count total
        long totalCount = countTotal(sql.countSql(), params, template);
        
        // Fetch page data
        List<Map<String, Object>> data = queryPage(
            sql.selectSql(), 
            params, 
            request.page(), 
            request.pageSize(),
            template
        );
        
        // 4. Record execution history
        recordHistory(request, sql, totalCount, System.currentTimeMillis() - startTime);
        
        return DynamicQueryResult.builder()
            .data(data)
            .totalCount(totalCount)
            .page(request.page())
            .pageSize(request.pageSize())
            .totalPages((int) Math.ceil((double) totalCount / request.pageSize()))
            .executionTimeMs(System.currentTimeMillis() - startTime)
            .executionId(generateExecutionId())
            .build();
    }
    
    private List<Map<String, Object>> queryPage(
            String sql, 
            Map<String, Object> params,
            int page, 
            int pageSize,
            NamedParameterJdbcTemplate template) {
        
        String pagedSql = sql + " LIMIT :limit OFFSET :offset";
        params.put("limit", pageSize);
        params.put("offset", page * pageSize);
        
        return template.queryForList(pagedSql, params);
    }
}
```

### 3.5 MongoDB Executor

```java
@Component
@RequiredArgsConstructor
public class MongoQueryExecutor implements QueryExecutor {
    
    private final MongoTemplatePoolManager mongoTemplatePoolManager;
    
    @Override
    public DynamicQueryResult execute(DynamicQueryRequest request, DatasourceConfig config) {
        long startTime = System.currentTimeMillis();
        
        MongoTemplate template = mongoTemplatePoolManager.getMongoTemplate(config);
        String collectionName = extractCollectionName(request.tableKey(), config);
        
        // 1. Build query
        Query query = buildMongoQuery(request);
        
        // 2. Count total
        long totalCount = template.count(query, Document.class, collectionName);
        
        // 3. Fetch page data
        query.with(PageRequest.of(request.page(), request.pageSize()));
        List<Document> documents = template.find(query, Document.class, collectionName);
        
        // 4. Convert to Map list
        List<Map<String, Object>> data = documents.stream()
            .map(doc -> (Map<String, Object>) new HashMap<>(doc))
            .toList();
        
        return DynamicQueryResult.builder()
            .data(data)
            .totalCount(totalCount)
            .page(request.page())
            .pageSize(request.pageSize())
            .totalPages((int) Math.ceil((double) totalCount / request.pageSize()))
            .executionTimeMs(System.currentTimeMillis() - startTime)
            .build();
    }
    
    private Query buildMongoQuery(DynamicQueryRequest request) {
        Query query = new Query();
        
        for (FilterCondition filter : request.filters()) {
            Criteria criteria = buildCriteria(filter);
            query.addCriteria(criteria);
        }
        
        for (SortField sort : request.sorts()) {
            query.with(Sort.by(
                sort.order() == SortOrder.asc ? Sort.Direction.ASC : Sort.Direction.DESC,
                sort.columnName()
            ));
        }
        
        return query;
    }
    
    private Criteria buildCriteria(FilterCondition filter) {
        return switch (filter.operator()) {
            case EQ -> Criteria.where(filter.columnName()).is(filter.value());
            case NE -> Criteria.where(filter.columnName()).ne(filter.value());
            case GT -> Criteria.where(filter.columnName()).gt(filter.value());
            case GTE -> Criteria.where(filter.columnName()).gte(filter.value());
            case LT -> Criteria.where(filter.columnName()).lt(filter.value());
            case LTE -> Criteria.where(filter.columnName()).lte(filter.value());
            case LIKE -> Criteria.where(filter.columnName()).regex(
                Pattern.compile(".*" + filter.value() + ".*", Pattern.CASE_INSENSITIVE)
            );
            case IN -> Criteria.where(filter.columnName()).in((Collection<?>) filter.value());
            case IS_NULL -> Criteria.where(filter.columnName()).isNull();
            case IS_NOT_NULL -> Criteria.where(filter.columnName()).ne(null);
            default -> throw new IllegalArgumentException("Unsupported operator: " + filter.operator());
        };
    }
}
```

### 3.6 Connection Pool Management

```java
@Service
@RequiredArgsConstructor
public class DataSourcePoolManager {
    
    private final DatasourceConfigService configService;
    private final Map<String, HikariDataSource> mysqlPools = new ConcurrentHashMap<>();
    private final Map<String, MongoClient> mongoClients = new ConcurrentHashMap<>();
    
    public DataSource getDataSource(String datasourceKey) {
        return mysqlPools.computeIfAbsent(datasourceKey, this::createMySqlDataSource);
    }
    
    private HikariDataSource createMySqlDataSource(String datasourceKey) {
        DatasourceConfig config = configService.getByKey(datasourceKey);
        ConnectionConfig conn = config.getConnectionConfig();
        
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(buildJdbcUrl(conn));
        hikariConfig.setUsername(conn.getUsername());
        hikariConfig.setPassword(conn.getPassword());
        hikariConfig.setMaximumPoolSize(conn.getConnectionPoolSize());
        hikariConfig.setConnectionTimeout(conn.getConnectionTimeout());
        hikariConfig.setIdleTimeout(600000);
        hikariConfig.setMaxLifetime(1800000);
        
        return new HikariDataSource(hikariConfig);
    }
    
    public void closePool(String datasourceKey) {
        HikariDataSource ds = mysqlPools.remove(datasourceKey);
        if (ds != null) {
            ds.close();
        }
    }
}
```

### 3.7 Dynamic Column Mapping

```java
@Service
@RequiredArgsConstructor
public class ColumnConfigMapper {
    
    private final ColumnConfigRepository columnConfigRepository;
    
    public List<ColumnMetadata> getColumns(String tableKey) {
        List<ColumnConfig> configs = columnConfigRepository.findByTableKeyOrderByDisplayOrder(tableKey);
        
        return configs.stream()
            .map(this::toMetadata)
            .toList();
    }
    
    private ColumnMetadata toMetadata(ColumnConfig config) {
        return ColumnMetadata.builder()
            .columnKey(config.getColumnKey())
            .columnName(config.getColumnName())
            .displayName(config.getDisplayName())
            .dataType(config.getDataType())
            .renderType(config.getRenderConfig().getType())
            .width(config.getWidth())
            .sortable(config.getSortable())
            .filterable(config.getFilterable())
            .formatConfig(config.getFormatConfig())
            .renderConfig(config.getRenderConfig())
            .build();
    }
    
    public Map<String, ColumnConfig> getColumnMap(String tableKey) {
        return columnConfigRepository.findByTableKey(tableKey)
            .stream()
            .collect(Collectors.toMap(ColumnConfig::getColumnName, c -> c));
    }
}
```

---

## 4. REST API Design

### 4.1 API Structure Overview

```
/api/v1
├── /datasources              # Data source management
│   ├── GET    /              # List all datasources
│   ├── POST   /              # Create datasource
│   ├── GET    /{key}         # Get datasource detail
│   ├── PUT    /{key}         # Update datasource
│   ├── DELETE /{key}         # Delete datasource
│   ├── POST   /{key}/test    # Test connection
│   └── GET    /{key}/tables  # List tables in datasource
│
├── /tables                   # Table configuration
│   ├── GET    /              # List all tables
│   ├── POST   /              # Create table config
│   ├── GET    /{tableKey}    # Get table detail
│   ├── PUT    /{tableKey}    # Update table config
│   ├── DELETE /{tableKey}    # Delete table config
│   ├── GET    /{tableKey}/columns   # Get table columns
│   └── POST   /{tableKey}/columns    # Add/update columns
│
├── /columns                  # Column configuration
│   ├── GET    /{columnKey}   # Get column detail
│   ├── PUT    /{columnKey}   # Update column
│   └── DELETE /{columnKey}   # Delete column
│
├── /query                    # Query execution
│   ├── POST   /execute       # Execute dynamic query
│   ├── POST   /preview       # Preview query (no pagination)
│   ├── GET    /{tableKey}    # Query table with filters
│   └── POST   /export        # Export query results
│
├── /saved-queries            # Saved query management
│   ├── GET    /              # List saved queries
│   ├── POST   /              # Create saved query
│   ├── GET    /{queryKey}    # Get saved query
│   ├── PUT    /{queryKey}    # Update saved query
│   └── DELETE /{queryKey}    # Delete saved query
│
└── /query-history            # Query history
    ├── GET    /              # List history (paginated)
    ├── GET    /{executionId} # Get execution detail
    └── GET    /stats         # Query statistics
```

### 4.2 API Specifications

#### 4.2.1 Query Execution API

**POST /api/v1/query/execute**

Execute a dynamic query with filters, sorting, and pagination.

Request:
```json
{
  "tableKey": "prod_orders",
  "columns": ["order_id", "customer_name", "total_amount", "status"],
  "filters": [
    {
      "column": "status",
      "operator": "EQ",
      "value": "PENDING"
    },
    {
      "column": "created_at",
      "operator": "GTE",
      "value": "2026-01-01"
    }
  ],
  "sorts": [
    {"column": "created_at", "order": "DESC"}
  ],
  "page": 0,
  "pageSize": 20
}
```

Response:
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "executionId": "exec_20260626_001",
    "columns": [
      {
        "columnKey": "prod_orders.order_id",
        "columnName": "order_id",
        "displayName": "Order ID",
        "dataType": "BIGINT",
        "renderType": "LINK",
        "width": 120,
        "sortable": true,
        "filterable": true,
        "formatConfig": {},
        "renderConfig": {
          "type": "LINK",
          "props": {"href": "/orders/{value}"}
        }
      },
      {
        "columnKey": "prod_orders.customer_name",
        "columnName": "customer_name",
        "displayName": "Customer",
        "dataType": "VARCHAR",
        "renderType": "TEXT",
        "width": 150,
        "sortable": true,
        "filterable": true
      },
      {
        "columnKey": "prod_orders.total_amount",
        "columnName": "total_amount",
        "displayName": "Amount",
        "dataType": "DECIMAL",
        "renderType": "MONEY",
        "width": 120,
        "sortable": true,
        "filterable": false,
        "formatConfig": {
          "currencySymbol": "¥",
          "decimalPlaces": 2
        }
      },
      {
        "columnKey": "prod_orders.status",
        "columnName": "status",
        "displayName": "Status",
        "dataType": "VARCHAR",
        "renderType": "TAG",
        "width": 100,
        "sortable": true,
        "filterable": true,
        "renderConfig": {
          "type": "TAG",
          "props": {
            "colorMap": {
              "PENDING": "orange",
              "PROCESSING": "blue",
              "COMPLETED": "green",
              "CANCELLED": "red"
            }
          }
        }
      }
    ],
    "data": [
      {
        "order_id": 10001,
        "customer_name": "Zhang San",
        "total_amount": 299.99,
        "status": "PENDING",
        "created_at": "2026-06-20 10:30:00"
      },
      {
        "order_id": 10002,
        "customer_name": "Li Si",
        "total_amount": 159.00,
        "status": "PENDING",
        "created_at": "2026-06-20 09:15:00"
      }
    ],
    "pagination": {
      "page": 0,
      "pageSize": 20,
      "totalElements": 1250,
      "totalPages": 63
    },
    "executionTimeMs": 145
  }
}
```

#### 4.2.2 Table Query API (Simplified)

**GET /api/v1/query/{tableKey}**

Query a table with query parameters.

Request:
```
GET /api/v1/query/prod_orders?status=EQ:PENDING&created_at=GTE:2026-01-01&sort=created_at:desc&page=0&size=20
```

Response: Same as execute API

#### 4.2.3 Export API

**POST /api/v1/query/export**

Export query results to file.

Request:
```json
{
  "tableKey": "prod_orders",
  "columns": ["order_id", "customer_name", "total_amount"],
  "filters": [
    {"column": "status", "operator": "EQ", "value": "PENDING"}
  ],
  "format": "CSV",
  "filename": "pending_orders_20260626"
}
```

Response:
```
Content-Type: text/csv
Content-Disposition: attachment; filename="pending_orders_20260626.csv"

order_id,customer_name,total_amount
10001,Zhang San,299.99
10002,Li Si,159.00
```

#### 4.2.4 Data Source Management

**POST /api/v1/datasources**

Create a new datasource.

Request:
```json
{
  "datasourceKey": "prod_mysql_orders",
  "datasourceName": "Production Orders DB",
  "datasourceType": "MYSQL",
  "connectionConfig": {
    "host": "172.18.2.10",
    "port": 3306,
    "database": "orders_db",
    "username": "readonly_user",
    "password": "encrypted_password"
  },
  "description": "Primary orders database",
  "tags": ["production", "orders"]
}
```

Response:
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "datasourceKey": "prod_mysql_orders",
    "status": "ACTIVE",
    "createdAt": "2026-06-26T08:00:00Z"
  }
}
```

**POST /api/v1/datasources/{key}/test**

Test datasource connection.

Request: Empty body (uses existing config)

Response:
```json
{
  "code": 0,
  "message": "Connection successful",
  "data": {
    "connected": true,
    "serverVersion": "MySQL 8.0.32",
    "catalog": "orders_db",
    "tables": ["orders", "order_items", "customers", "products"],
    "responseTimeMs": 85
  }
}
```

#### 4.2.5 Column Configuration API

**POST /api/v1/tables/{tableKey}/columns**

Bulk add/update columns for a table (can import from database schema).

Request:
```json
{
  "action": "SYNC_FROM_DB",
  "preserveCustomConfig": true
}
```

Response:
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "tableKey": "prod_orders",
    "syncedColumns": 12,
    "preservedColumns": ["order_id.renderType", "total_amount.formatConfig"],
    "newColumns": ["shipping_address", "promotion_code"],
    "removedColumns": []
  }
}
```

---

## 5. Frontend Dynamic Table

### 5.1 Component Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                    Dynamic Table Architecture                         │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │                    DynamicTable Component                     │   │
│  │                                                               │   │
│  │  ┌────────────────┐  ┌────────────────┐  ┌───────────────┐  │   │
│  │  │ ColumnHeader   │  │  ColumnHeader   │  │  ColumnHeader │  │   │
│  │  │ (order_id)     │  │  (customer_name) │  │  (amount)     │  │   │
│  │  └───────┬────────┘  └───────┬────────┘  └───────┬───────┘  │   │
│  │          │                    │                    │          │   │
│  │          └────────────────────┼────────────────────┘          │   │
│  │                               │                                 │   │
│  │  ┌────────────────────────────▼──────────────────────────────┐ │   │
│  │  │                    Cell Renderer Factory                     │ │   │
│  │  │  ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐        │ │   │
│  │  │  │  Text    │ │  Link   │ │  Tag    │ │  Money  │  ...   │ │   │
│  │  │  │Renderer │ │Renderer │ │Renderer │ │Renderer │        │ │   │
│  │  │  └─────────┘ └─────────┘ └─────────┘ └─────────┘        │ │   │
│  │  └──────────────────────────────────────────────────────────┘ │   │
│  │                                                               │   │
│  │  ┌──────────────────────────────────────────────────────────┐  │   │
│  │  │                    Pagination Controls                    │  │   │
│  │  └──────────────────────────────────────────────────────────┘  │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

### 5.2 Dynamic Table Component

```tsx
// components/DynamicTable/index.tsx
import React, { useEffect, useState, useMemo } from 'react';
import { Table, Card, Space, Button, Input, Select, Tooltip } from 'antd';
import type { TableProps, TableColumnType } from 'antd';
import { useDynamicQuery } from '@/hooks/useDynamicQuery';
import { useColumnConfig } from '@/hooks/useColumnConfig';
import { CellRenderer } from './CellRenderer';
import { FilterPanel } from './FilterPanel';
import { Pagination } from './Pagination';

interface DynamicTableProps {
  tableKey: string;
  onRowClick?: (record: Record<string, unknown>) => void;
  showFilterPanel?: boolean;
  showExportButton?: boolean;
  onExport?: (format: 'CSV' | 'EXCEL' | 'JSON') => void;
}

export const DynamicTable: React.FC<DynamicTableProps> = ({
  tableKey,
  onRowClick,
  showFilterPanel = true,
  showExportButton = true,
  onExport
}) => {
  // Fetch column configuration
  const { columns: columnMetas, loading: columnsLoading } = useColumnConfig(tableKey);
  
  // Dynamic query hook
  const {
    data,
    totalCount,
    loading,
    page,
    pageSize,
    filters,
    sorts,
    setPage,
    setPageSize,
    setFilters,
    setSorts,
    refresh
  } = useDynamicQuery(tableKey);

  // Transform column metadata to Ant Design columns
  const antdColumns: TableColumnType<Record<string, unknown>>[] = useMemo(() => {
    return columnMetas.map(col => ({
      key: col.columnName,
      dataIndex: col.columnName,
      title: col.displayName,
      width: col.width,
      sortable: col.sortable,
      render: (value: unknown, record: Record<string, unknown>) => (
        <CellRenderer
          value={value}
          column={col}
          record={record}
        />
      ),
      ...col.filterable && {
        filterDropdown: ({ setSelectedKeys, selectedKeys, confirm, clearFilters }) => (
          <FilterPanel
            column={col}
            value={selectedKeys[0]}
            onChange={(val) => setSelectedKeys(val ? [val] : [])}
            onConfirm={confirm}
            onReset={clearFilters}
          />
        ),
        onFilter: (value, record) => {
          const recordValue = record[col.columnName];
          return String(recordValue).includes(String(value));
        }
      }
    }));
  }, [columnMetas]);

  // Table change handler for sort
  const handleTableChange: TableProps['onChange'] = (pagination, _filters, sorter) => {
    setPage(pagination.current - 1);
    setPageSize(pagination.pageSize);
    
    if (!Array.isArray(sorter)) {
      if (sorter.order) {
        setSorts([{
          column: sorter.field as string,
          order: sorter.order === 'ascend' ? 'ASC' : 'DESC'
        }]);
      } else {
        setSorts([]);
      }
    }
  };

  if (columnsLoading) {
    return <Card loading />;
  }

  return (
    <Card
      title={columnMetas[0]?.tableKey}
      extra={
        <Space>
          <Button onClick={refresh}>Refresh</Button>
          {showExportButton && (
            <Select
              placeholder="Export"
              onChange={onExport}
              options={[
                { label: 'CSV', value: 'CSV' },
                { label: 'Excel', value: 'EXCEL' },
                { label: 'JSON', value: 'JSON' }
              ]}
            />
          )}
        </Space>
      }
    >
      <Table
        columns={antdColumns}
        dataSource={data}
        loading={loading}
        rowKey={(record) => String(record[getPrimaryKey(columnMetas)])}
        onRow={(record) => ({
          onClick: () => onRowClick?.(record),
          style: { cursor: onRowClick ? 'pointer' : 'default' }
        })}
        onChange={handleTableChange}
        pagination={{
          current: page + 1,
          pageSize,
          total: totalCount,
          showSizeChanger: true,
          showQuickJumper: true,
          showTotal: (total) => `Total ${total} records`
        }}
        scroll={{ x: 'max-content' }}
        size="middle"
      />
    </Card>
  );
};
```

### 5.3 Cell Renderer Factory

```tsx
// components/DynamicTable/CellRenderer.tsx
import React from 'react';
import { Link, Tag, Tooltip, Typography } from 'antd';
import { TextRenderer } from './renderers/TextRenderer';
import { LinkRenderer } from './renderers/LinkRenderer';
import { TagRenderer } from './renderers/TagRenderer';
import { MoneyRenderer } from './renderers/MoneyRenderer';
import { DateRenderer } from './renderers/DateRenderer';
import { BooleanRenderer } from './renderers/BooleanRenderer';
import { ImageRenderer } from './renderers/ImageRenderer';
import { JsonRenderer } from './renderers/JsonRenderer';

const { Text } = Typography;

interface CellRendererProps {
  value: unknown;
  column: ColumnMetadata;
  record: Record<string, unknown>;
}

export const CellRenderer: React.FC<CellRendererProps> = ({ value, column, record }) => {
  const renderType = column.renderType || getDefaultRenderType(column.dataType);
  
  const rendererProps = {
    value,
    column,
    record,
    formatConfig: column.formatConfig || {},
    renderConfig: column.renderConfig || {}
  };

  switch (renderType) {
    case 'LINK':
      return <LinkRenderer {...rendererProps} />;
    case 'TAG':
      return <TagRenderer {...rendererProps} />;
    case 'MONEY':
      return <MoneyRenderer {...rendererProps} />;
    case 'DATE':
    case 'DATETIME':
      return <DateRenderer {...rendererProps} />;
    case 'BOOLEAN':
      return <BooleanRenderer {...rendererProps} />;
    case 'IMAGE':
      return <ImageRenderer {...rendererProps} />;
    case 'JSON':
      return <JsonRenderer {...rendererProps} />;
    case 'TEXT':
    default:
      return <TextRenderer {...rendererProps} />;
  }
};

function getDefaultRenderType(dataType: string): string {
  const typeMap: Record<string, string> = {
    'VARCHAR': 'TEXT',
    'TEXT': 'TEXT',
    'INT': 'TEXT',
    'BIGINT': 'TEXT',
    'DECIMAL': 'MONEY',
    'DATE': 'DATE',
    'DATETIME': 'DATE',
    'BOOLEAN': 'BOOLEAN',
    'ENUM': 'TAG',
    'JSON': 'JSON'
  };
  return typeMap[dataType] || 'TEXT';
}
```

### 5.4 Individual Cell Renderers

```tsx
// components/DynamicTable/renderers/MoneyRenderer.tsx
import React from 'react';
import { Typography } from 'antd';

const { Text } = Typography;

export const MoneyRenderer: React.FC<{
  value: number;
  formatConfig: { currencySymbol?: string; decimalPlaces?: number };
}> = ({ value, formatConfig }) => {
  if (value == null) return <Text type="secondary">-</Text>;
  
  const { currencySymbol = '¥', decimalPlaces = 2 } = formatConfig;
  const formatted = new Intl.NumberFormat('zh-CN', {
    minimumFractionDigits: decimalPlaces,
    maximumFractionDigits: decimalPlaces
  }).format(value);
  
  return <Text>{currencySymbol}{formatted}</Text>;
};

// components/DynamicTable/renderers/TagRenderer.tsx
export const TagRenderer: React.FC<{
  value: string;
  renderConfig: { type: string; props: { colorMap?: Record<string, string> } };
}> = ({ value, renderConfig }) => {
  const colorMap = renderConfig.props?.colorMap || {};
  const color = colorMap[value] || 'default';
  
  return <Tag color={color}>{value}</Tag>;
};

// components/DynamicTable/renderers/DateRenderer.tsx
export const DateRenderer: React.FC<{
  value: string | Date;
  formatConfig: { pattern?: string };
}> = ({ value, formatConfig }) => {
  if (!value) return <Text type="secondary">-</Text>;
  
  const date = typeof value === 'string' ? new Date(value) : value;
  const pattern = formatConfig.pattern || 'yyyy-MM-dd HH:mm:ss';
  
  const formatted = formatDate(date, pattern);
  return <Text>{formatted}</Text>;
};

function formatDate(date: Date, pattern: string): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  const hours = String(date.getHours()).padStart(2, '0');
  const minutes = String(date.getMinutes()).padStart(2, '0');
  const seconds = String(date.getSeconds()).padStart(2, '0');
  
  return pattern
    .replace('yyyy', String(year))
    .replace('MM', month)
    .replace('dd', day)
    .replace('HH', hours)
    .replace('mm', minutes)
    .replace('ss', seconds);
}
```

### 5.5 Custom Hooks

```tsx
// hooks/useDynamicQuery.ts
import { useState, useEffect, useCallback } from 'react';
import { queryApi } from '@/services/api';

export function useDynamicQuery(tableKey: string) {
  const [data, setData] = useState<Record<string, unknown>[]>([]);
  const [columns, setColumns] = useState<ColumnMetadata[]>([]);
  const [totalCount, setTotalCount] = useState(0);
  const [loading, setLoading] = useState(false);
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(20);
  const [filters, setFilters] = useState<FilterCondition[]>([]);
  const [sorts, setSorts] = useState<SortField[]>([]);

  const executeQuery = useCallback(async () => {
    setLoading(true);
    try {
      const response = await queryApi.execute({
        tableKey,
        columns: [],  // empty = all columns
        filters,
        sorts,
        page,
        pageSize
      });
      
      setData(response.data);
      setColumns(response.columns);
      setTotalCount(response.pagination.totalElements);
    } finally {
      setLoading(false);
    }
  }, [tableKey, filters, sorts, page, pageSize]);

  useEffect(() => {
    executeQuery();
  }, [executeQuery]);

  return {
    data,
    columns,
    totalCount,
    loading,
    page,
    pageSize,
    filters,
    sorts,
    setPage,
    setPageSize,
    setFilters,
    setSorts,
    refresh: executeQuery
  };
}

// hooks/useColumnConfig.ts
export function useColumnConfig(tableKey: string) {
  const [columns, setColumns] = useState<ColumnMetadata[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    queryApi.getTableColumns(tableKey)
      .then(res => setColumns(res.data))
      .finally(() => setLoading(false));
  }, [tableKey]);

  return { columns, loading };
}
```

---

## 6. Data Flow Diagrams

### 6.1 Query Execution Flow

```
┌─────────┐     ┌──────────┐     ┌─────────────┐     ┌────────────────┐
│ Client  │────▶│  API      │────▶│ Query       │────▶│ Datasource     │
│ (React) │     │ Controller│     │ Executor    │     │ (MySQL/PG/MG) │
└─────────┘     └──────────┘     └─────────────┘     └────────────────┘
                    │                   │                     │
                    │                   │                     │
                    │              ┌─────▼─────┐               │
                    │              │ Column    │               │
                    │              │ Config    │               │
                    │              │ Mapper    │               │
                    │              └───────────┘               │
                    │                                        │
         ┌──────────▼──────────┐                             │
         │ Query History       │                             │
         │ Recorder            │                             │
         └─────────────────────┘                             │
                                                                │
    ┌───────────────────────────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────────────────────────────────┐
│                      Response Building                           │
├─────────────────────────────────────────────────────────────────┤
│ 1. Execute query on target datasource                            │
│ 2. Transform results to generic Map<String, Object> format     │
│ 3. Enrich with column metadata (displayName, renderType, etc.)  │
│ 4. Calculate pagination metadata                                 │
│ 5. Return DynamicQueryResult to client                          │
└─────────────────────────────────────────────────────────────────┘
```

### 6.2 Column Configuration Sync Flow

```
┌─────────────┐     ┌──────────────┐     ┌─────────────┐     ┌──────────────┐
│ Admin User  │────▶│ Column Sync  │────▶│ DB Schema   │────▶│ Metadata     │
│ (Trigger)   │     │ API          │     │ Reader      │     │ Merger       │
└─────────────┘     └──────────────┘     └─────────────┘     └──────────────┘
                                                                          │
                                                                          ▼
                        ┌──────────────────────────────────────────────────┐
                        │           insight_column_config                   │
                        │  ┌─────────┐ ┌─────────┐ ┌─────────┐            │
                        │  │ column1 │ │ column2 │ │ column3 │            │
                        │  │ (keep)  │ │ (update)│ │ (new)   │            │
                        │  └─────────┘ └─────────┘ └─────────┘            │
                        └──────────────────────────────────────────────────┘
                                                                          │
                            ┌──────────────────────────────────────────────┘
                            │
                            ▼
                    ┌───────────────┐
                    │  UI Update   │
                    │ (React Table)│
                    └───────────────┘
```

### 6.3 Multi-Datasource Query Routing

```
                         ┌─────────────────┐
                         │ Query Request   │
                         │ {tableKey:      │
                         │  "prod_orders"} │
                         └────────┬────────┘
                                  │
                                  ▼
                    ┌─────────────────────────────┐
                    │   Table Config Lookup       │
                    │   insight_table_config      │
                    └─────────────┬───────────────┘
                                  │
                                  ▼
                    ┌─────────────────────────────┐
                    │   Datasource Lookup         │
                    │   insight_datasource       │
                    │   datasourceKey:           │
                    │   "prod_mysql_orders"      │
                    └─────────────┬───────────────┘
                                  │
                                  ▼
                    ┌─────────────────────────────┐
                    │   Route to Executor         │
                    │   ┌─────────┬─────────┐    │
                    │   │  MySQL  │  PG     │    │
                    │   │Executor │Executor │... │
                    │   └───┬─────┴────┬────┘    │
                    │       │          │         │
                    └───────┼──────────┼─────────┘
                            │          │
                            ▼          ▼
                    ┌───────────┐ ┌───────────┐
                    │  MySQL    │ │ PostgreSQL│
                    │172.18.2.x │ │172.18.3.x │
                    └───────────┘ └───────────┘
```

---

## 7. Technology Choices

### 7.1 Backend Technology Stack

| Component | Technology | Version | Purpose |
|-----------|------------|---------|---------|
| Framework | Spring Boot | 3.5.x | Core application framework |
| Language | Java | 21 | Primary language |
| Metadata Store | MongoDB | 7.x | Store all metadata collections |
| Query (SQL) | JdbcTemplate | - | Execute MySQL/PostgreSQL queries |
| Query (NoSQL) | MongoTemplate | - | Execute MongoDB queries |
| Connection Pool | HikariCP | - | High-performance connection pooling |
| API Docs | Knife4j | 4.x | Swagger/OpenAPI documentation |
| Validation | Hibernate Validator | - | Request validation |
| JSON | Jackson | - | JSON serialization |
| Utils | Lombok | - | Reduce boilerplate |

### 7.2 Multi-Datasource Connection Strategy

```java
@Configuration
public class MultiDataSourceConfig {
    
    // MongoDB for metadata (primary store)
    @Bean
    public MongoTemplate metadataMongoTemplate(MongoClient mongoClient) {
        return new MongoTemplate(mongoClient, "insight_metadata");
    }
    
    // Dynamic datasource pools (lazy initialization)
    @Bean
    public DynamicDataSourcePoolManager dynamicDataSourcePoolManager(
            DatasourceConfigRepository configRepository) {
        return new DynamicDataSourcePoolManager(configRepository);
    }
}

public class DynamicDataSourcePoolManager {
    
    private final Map<String, HikariDataSource> sqlPools = new ConcurrentHashMap<>();
    private final Map<String, MongoClient> mongoClients = new ConcurrentHashMap<>();
    
    // Lazy create and cache connection pools
    public DataSource getDataSource(String key) {
        return sqlPools.computeIfAbsent(key, this::createFromConfig);
    }
    
    public MongoClient getMongoClient(String key) {
        return mongoClients.computeIfAbsent(key, this::createMongoFromConfig);
    }
    
    // Connection pool health check
    public PoolHealth checkHealth(String key) {
        // Test connection, return metrics
    }
    
    // Graceful shutdown
    public void closeAll() {
        sqlPools.values().forEach(HikariDataSource::close);
        mongoClients.values().forEach(MongoClient::close);
    }
}
```

### 7.3 Frontend Technology Stack

| Component | Technology | Version | Purpose |
|-----------|------------|---------|---------|
| Framework | React | 18.x | UI framework |
| Build Tool | Vite | 5.x | Fast development and build |
| UI Library | Ant Design | 5.x | Enterprise-grade components |
| State | Zustand / React Query | - | State management |
| HTTP | Axios | - | API calls |
| Table | Ant Design Table | - | Dynamic table component |
| Date | Day.js | - | Date formatting |
| Export | xlsx / export2csv | - | Excel/CSV export |

---

## 8. Implementation Plan

### 8.1 Phase 1: Core Infrastructure (Week 1-2)

```
┌────────────────────────────────────────────────────────────────┐
│  Phase 1: Core Infrastructure                                   │
├────────────────────────────────────────────────────────────────┤
│                                                                │
│  1.1 Project Setup                                             │
│     ├── Initialize Spring Boot project with MongoDB            │
│     ├── Configure multi-datasource architecture               │
│     └── Set up logging and monitoring                         │
│                                                                │
│  1.2 MongoDB Collections                                       │
│     ├── insight_datasource schema and repository              │
│     ├── insight_table_config schema and repository           │
│     ├── insight_column_config schema and repository          │
│     └── insight_query_history schema and repository           │
│                                                                │
│  1.3 Datasource Connection Management                          │
│     ├── Connection pool manager for MySQL/PostgreSQL          │
│     ├── Connection pool manager for MongoDB                    │
│     ├── Connection testing and health check                   │
│     └── Secure credential storage (encryption)                │
│                                                                │
│  Milestone: Can add datasources and test connections          │
└────────────────────────────────────────────────────────────────┘
```

### 8.2 Phase 2: Query Engine (Week 3-4)

```
┌────────────────────────────────────────────────────────────────┐
│  Phase 2: Query Engine                                         │
├────────────────────────────────────────────────────────────────┤
│                                                                │
│  2.1 Dynamic Query Builder                                     │
│     ├── SQL query builder for MySQL/PostgreSQL                │
│     ├── Query builder for MongoDB                             │
│     ├── Dynamic WHERE clause builder                          │
│     ├── Dynamic ORDER BY builder                              │
│     └── Pagination SQL generator                             │
│                                                                │
│  2.2 Query Executor                                            │
│     ├── MySQL query executor                                  │
│     ├── PostgreSQL query executor                             │
│     ├── MongoDB query executor                               │
│     └── Result transformer (Map<String, Object>)              │
│                                                                │
│  2.3 Column Metadata Integration                              │
│     ├── Fetch column configs from MongoDB                     │
│     ├── Map raw results to column metadata                    │
│     └── Include column metadata in response                   │
│                                                                │
│  Milestone: Can execute queries against any datasource        │
└────────────────────────────────────────────────────────────────┘
```

### 8.3 Phase 3: REST API (Week 5-6)

```
┌────────────────────────────────────────────────────────────────┐
│  Phase 3: REST API                                             │
├────────────────────────────────────────────────────────────────┤
│                                                                │
│  3.1 DataSource Management APIs                                │
│     ├── CRUD for datasources                                  │
│     ├── Connection test endpoint                              │
│     └── Table listing from datasource                         │
│                                                                │
│  3.2 Table & Column Configuration APIs                        │
│     ├── CRUD for table configs                                │
│     ├── CRUD for column configs                               │
│     ├── Sync columns from database schema                     │
│     └── Bulk column configuration import/export               │
│                                                                │
│  3.3 Query Execution APIs                                     │
│     ├── POST /query/execute                                   │
│     ├── GET /query/{tableKey}                                │
│     ├── POST /query/export                                    │
│     └── GET /query/history                                    │
│                                                                │
│  3.4 API Documentation                                         │
│     ├── Swagger/Knife4j integration                          │
│     └── API documentation UI                                  │
│                                                                │
│  Milestone: Complete API for external consumption             │
└────────────────────────────────────────────────────────────────┘
```

### 8.4 Phase 4: Frontend (Week 7-8)

```
┌────────────────────────────────────────────────────────────────┐
│  Phase 4: Frontend                                             │
├────────────────────────────────────────────────────────────────┤
│                                                                │
│  4.1 Dynamic Table Component                                   │
│     ├── Column metadata-driven rendering                      │
│     ├── Type-specific cell renderers                          │
│     ├── Built-in filter panel                                │
│     ├── Sort and pagination                                   │
│     └── Column visibility toggle                             │
│                                                                │
│  4.2 Configuration UI                                          │
│     ├── DataSource management UI                              │
│     ├── Table configuration UI                                │
│     ├── Column configuration UI                               │
│     └── Visual column editor                                 │
│                                                                │
│  4.3 Query Interface                                          │
│     ├── Query builder with filters                           │
│     ├── Saved queries management                             │
│     └── Export functionality                                  │
│                                                                │
│  Milestone: Complete working frontend application              │
└────────────────────────────────────────────────────────────────┘
```

### 8.5 Phase 5: Testing & Polish (Week 9-10)

```
┌────────────────────────────────────────────────────────────────┐
│  Phase 5: Testing & Polish                                      │
├────────────────────────────────────────────────────────────────┤
│                                                                │
│  5.1 Integration Testing                                       │
│     ├── Multi-datasource query tests                          │
│     ├── Column mapping tests                                  │
│     └── Error handling tests                                  │
│                                                                │
│  5.2 Performance Testing                                       │
│     ├── Connection pool stress test                           │
│     ├── Large result set pagination                           │
│     └── Concurrent query handling                            │
│                                                                │
│  5.3 Security Review                                           │
│     ├── SQL injection prevention                              │
│     ├── Credential encryption verification                    │
│     └── Row-level security implementation                     │
│                                                                │
│  5.4 Documentation & Deployment                               │
│     ├── User documentation                                    │
│     ├── API documentation                                     │
│     └── Docker deployment configuration                       │
│                                                                │
│  Milestone: Production-ready system                            │
└────────────────────────────────────────────────────────────────┘
```

---

## 9. Security Considerations

### 9.1 SQL Injection Prevention

```
┌────────────────────────────────────────────────────────────────┐
│  SQL Injection Prevention Strategy                              │
├────────────────────────────────────────────────────────────────┤
│                                                                │
│  1. Whitelist Table Access                                     │
│     ├── Only allow queries on configured tables               │
│     ├── Block dangerous SQL keywords (DROP, DELETE, etc.)    │
│     └── Validate tableKey against insight_table_config       │
│                                                                │
│  2. Parameterized Queries                                      │
│     ├── All WHERE conditions use NamedParameters              │
│     ├── Never concatenate user input into SQL                 │
│     └── Example: WHERE status = :status (not "status = " + v) │
│                                                                │
│  3. Column Name Validation                                     │
│     ├── Validate column names against insight_column_config   │
│     ├── Block dynamic column names from user input            │
│     └── Allow only alphanumeric + underscore                 │
│                                                                │
│  4. Value Sanitization                                         │
│     ├── Escape special characters                             │
│     ├── Type-check all values                                 │
│     └── Limit string lengths                                  │
│                                                                │
└────────────────────────────────────────────────────────────────┘
```

### 9.2 Credential Management

```
┌────────────────────────────────────────────────────────────────┐
│  Credential Security                                            │
├────────────────────────────────────────────────────────────────┤
│                                                                │
│  1. Encryption at Rest                                          │
│     ├── Encrypt password field with AES-256                   │
│     ├── Store encryption key in environment variable          │
│     └── Decrypt only at connection time                       │
│                                                                │
│  2. Connection Isolation                                        │
│     ├── Read-only database user for query execution           │
│     ├── No admin privileges in query context                  │
│     └── Separate connection pools per datasource              │
│                                                                │
│  3. Audit Logging                                              │
│     ├── Log all query executions                              │
│     ├── Log datasource connection attempts                     │
│     └── Store execution history in MongoDB                    │
│                                                                │
└────────────────────────────────────────────────────────────────┘
```

---

## 10. Summary

This technical design provides a complete blueprint for building a **generic, metadata-driven data exploration system** with the following key characteristics:

| Aspect | Design Decision |
|--------|-----------------|
| **Metadata Store** | MongoDB for all configuration (flexible schema) |
| **Query Engine** | Strategy pattern with executors for MySQL/PostgreSQL/MongoDB |
| **No Entity Classes** | All table structures driven by `insight_column_config` |
| **Dynamic UI** | React table renders columns from API response |
| **Reusability** | Same engine, different metadata = different applications |
| **Security** | Whitelist-based access, parameterized queries, encrypted credentials |

The system enables users to:
1. Configure external datasources without code changes
2. Define table views and column display rules via metadata
3. Execute queries against any supported database type
4. Display results in a dynamic table with type-specific renderers
5. Extend to new datasource types by implementing `QueryExecutor`
