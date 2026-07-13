// ============================================================================
// Agent Insight — MongoDB 测试数据造数脚本
// 来源：原 docs/04-测试文档.md §6.2（已搬迁至此）
// 用途：CI / 本地启动时执行，注入 Data Explorer 元数据
// 用法：
//   mongosh "${MONGODB_URI:-mongodb://localhost:27017/agent_insight}" \
//           init-insight-meta.js
// ----------------------------------------------------------------------------
// 约束：
//   - 所有时间字段固定为 ISODate 常量，避免 new Date() 漂移
//   - 使用 upsert 保证幂等可重跑
//   - 连接配置仅供测试（host/port 是 localhost）
// ============================================================================

const dbName = 'agent_insight';
const db = db.getSiblingDB(dbName);

// ----------------------------------------------------------------------------
// 1. insight_datasource — 数据源配置
// ----------------------------------------------------------------------------
db.insight_datasource.replaceOne(
  { datasourceKey: 'test_mysql_orders' },
  {
    datasourceKey: 'test_mysql_orders',
    datasourceName: '测试 MySQL 订单库',
    datasourceType: 'MYSQL',
    status: 'ACTIVE',
    connectionConfig: {
      host: 'localhost',
      port: 3306,
      database: 'test_orders',
      username: 'readonly_user',
      // ⚠️ 测试夹具：生产环境禁止明文密码（见 AGENTS.md §6.6）
      password: 'test_only_encrypted_placeholder',
      connectionPoolSize: 5,
      connectionTimeout: 30000,
      extraParams: { useSSL: false, serverTimezone: 'Asia/Shanghai' }
    },
    allowedTables: ['orders', 'customers'],
    deniedTables: [],
    description: 'CI 测试专用 — 订单库只读副本',
    tags: ['test', 'mysql'],
    createdBy: 'ci-bot',
    createdAt: ISODate('2026-06-30T10:00:00Z'),
    updatedBy: 'ci-bot',
    updatedAt: ISODate('2026-06-30T10:00:00Z')
  },
  { upsert: true }
);

db.insight_datasource.replaceOne(
  { datasourceKey: 'test_pg_users' },
  {
    datasourceKey: 'test_pg_users',
    datasourceName: '测试 PG 用户库',
    datasourceType: 'POSTGRESQL',
    status: 'ACTIVE',
    connectionConfig: {
      host: 'localhost',
      port: 5432,
      database: 'test_users',
      username: 'readonly_user',
      password: 'test_only_encrypted_placeholder',
      connectionPoolSize: 3,
      connectionTimeout: 30000
    },
    allowedTables: ['users', 'sessions'],
    deniedTables: ['password_reset_tokens'],
    tags: ['test', 'postgres'],
    createdBy: 'ci-bot',
    createdAt: ISODate('2026-06-30T10:00:00Z'),
    updatedBy: 'ci-bot',
    updatedAt: ISODate('2026-06-30T10:00:00Z')
  },
  { upsert: true }
);

// ----------------------------------------------------------------------------
// 2. insight_table_config — 表配置
// ----------------------------------------------------------------------------
db.insight_table_config.replaceOne(
  { tableKey: 'test_orders' },
  {
    tableKey: 'test_orders',
    tableName: 'orders',
    datasourceKey: 'test_mysql_orders',
    displayName: '订单列表',
    description: '测试用订单主表',
    tableType: 'TABLE',
    status: 'ACTIVE',
    queryConfig: {
      defaultSortField: 'created_at',
      defaultSortOrder: 'desc',
      defaultPageSize: 20,
      maxPageSize: 500,
      enableExport: true,
      exportFormats: ['CSV', 'JSON']
    },
    tags: ['test'],
    createdBy: 'ci-bot',
    createdAt: ISODate('2026-06-30T10:00:00Z'),
    updatedBy: 'ci-bot',
    updatedAt: ISODate('2026-06-30T10:00:00Z')
  },
  { upsert: true }
);

db.insight_table_config.replaceOne(
  { tableKey: 'test_users' },
  {
    tableKey: 'test_users',
    tableName: 'users',
    datasourceKey: 'test_pg_users',
    displayName: '用户列表',
    tableType: 'TABLE',
    status: 'ACTIVE',
    queryConfig: {
      defaultSortField: 'id',
      defaultSortOrder: 'asc',
      defaultPageSize: 20,
      maxPageSize: 500,
      enableExport: false,
      exportFormats: []
    },
    tags: ['test'],
    createdBy: 'ci-bot',
    createdAt: ISODate('2026-06-30T10:00:00Z'),
    updatedBy: 'ci-bot',
    updatedAt: ISODate('2026-06-30T10:00:00Z')
  },
  { upsert: true }
);

// ----------------------------------------------------------------------------
// 3. insight_column_config — 列配置（test_orders）
// ----------------------------------------------------------------------------
const orderColumns = [
  { columnName: 'order_id',    displayName: '订单 ID', dataType: 'BIGINT',       columnType: 'PRIMARY_KEY', displayOrder: 1, width: 100, sortable: true,  filterable: true, nullable: false, formatConfig: { type: 'TEXT' } },
  { columnName: 'user_id',     displayName: '用户 ID', dataType: 'BIGINT',       columnType: 'FOREIGN_KEY', displayOrder: 2, width: 100, sortable: true,  filterable: true, nullable: false, formatConfig: { type: 'TEXT' } },
  { columnName: 'amount',      displayName: '金额',   dataType: 'DECIMAL',      columnType: 'COLUMN',      displayOrder: 3, width: 120, sortable: true,  filterable: true, nullable: false, formatConfig: { type: 'DECIMAL', precision: 2, prefix: '¥' } },
  { columnName: 'status',      displayName: '状态',   dataType: 'VARCHAR',      columnType: 'COLUMN',      displayOrder: 4, width: 80,  sortable: true,  filterable: true, nullable: false, formatConfig: { type: 'TEXT' }, renderConfig: { type: 'TAG', props: { colorMap: { PAID: 'green', PENDING: 'orange', REFUNDED: 'red' } } } },
  { columnName: 'created_at',  displayName: '创建时间', dataType: 'DATETIME',   columnType: 'COLUMN',      displayOrder: 5, width: 180, sortable: true,  filterable: true, nullable: false, formatConfig: { type: 'DATETIME', pattern: 'yyyy-MM-dd HH:mm:ss' } }
];

orderColumns.forEach((col, idx) => {
  db.insight_column_config.replaceOne(
    { columnKey: `test_orders.${col.columnName}` },
    {
      columnKey: `test_orders.${col.columnName}`,
      tableKey: 'test_orders',
      ...col,
      createdBy: 'ci-bot',
      createdAt: ISODate('2026-06-30T10:00:00Z'),
      updatedBy: 'ci-bot',
      updatedAt: ISODate('2026-06-30T10:00:00Z')
    },
    { upsert: true }
  );
});

// ----------------------------------------------------------------------------
// 4. insight_query_history — 查询历史（审计）
// ----------------------------------------------------------------------------
db.insight_query_history.replaceOne(
  { requestId: 'qhist_test_001' },
  {
    requestId: 'qhist_test_001',
    tableKey: 'test_orders',
    datasourceKey: 'test_mysql_orders',
    executedSql: 'SELECT order_id, user_id, amount FROM orders WHERE status = ? LIMIT 20 OFFSET 0',
    filters: [{ column: 'status', operator: 'EQ', value: 'PAID' }],
    sorts: [{ column: 'created_at', order: 'desc' }],
    page: 0,
    pageSize: 20,
    totalCount: 0,
    durationMs: 45,
    success: true,
    errorMessage: null,
    executedBy: 'test-user',
    createdAt: ISODate('2026-06-30T10:05:00Z')
  },
  { upsert: true }
);

// ----------------------------------------------------------------------------
// 验证（可选）
// ----------------------------------------------------------------------------
print('--- Data Explorer 元数据造数完成 ---');
print('datasource  :', db.insight_datasource.countDocuments());
print('table_config:', db.insight_table_config.countDocuments());
print('column_config:', db.insight_column_config.countDocuments());
print('query_history:', db.insight_query_history.countDocuments());