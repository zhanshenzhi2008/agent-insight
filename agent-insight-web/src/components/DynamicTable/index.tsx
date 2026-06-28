import React, { useMemo } from 'react';
import {
  Table, Tag, Space, Tooltip, Typography, Empty
} from 'antd';
import type { ColumnsType, TableProps } from 'antd/es/table';
import dayjs from 'dayjs';

const { Text } = Typography;

export interface ColumnMeta {
  key: string;
  label: string;
  dataType: 'STRING' | 'NUMBER' | 'DATETIME' | 'BOOLEAN' | 'JSON' | 'TEXT';
  renderType: 'TEXT' | 'LINK' | 'TAG' | 'MONEY' | 'DATE' | 'DATETIME' | 'BOOLEAN' | 'JSON' | 'IMAGE' | 'HTML';
  width?: number;
  sortable?: boolean;
  filterable?: boolean;
  format?: string;
  enumLabels?: Record<string, string>;
  valueLabels?: Record<string, string>;
  tagColors?: Record<string, string>;
  linkPattern?: string;
  precision?: number;
  maxDisplayLength?: number;
}

export interface QueryResult {
  status: string;
  data: Record<string, any>[];
  total: number;
  page: number;
  pageSize: number;
  totalPages: number;
  hasNext: boolean;
  columns: ColumnMeta[];
  executionTimeMs: number;
  error?: string;
}

interface DynamicTableProps {
  result: QueryResult | null;
  loading?: boolean;
  onPageChange?: (page: number, pageSize: number) => void;
  onSort?: (field: string, direction: string) => void;
  rowKey?: string;
  scrollX?: number;
}

const renderCell = (value: any, col: ColumnMeta): React.ReactNode => {
  if (value === null || value === undefined) {
    return <Text type="secondary">-</Text>;
  }

  const label = col.valueLabels?.[String(value)]
    ?? col.enumLabels?.[String(value)];

  switch (col.renderType) {
    case 'TAG': {
      const color = col.tagColors?.[String(value)] ?? 'default';
      return <Tag color={color}>{label ?? String(value)}</Tag>;
    }

    case 'BOOLEAN':
      return (
        <Tag color={value ? 'green' : 'red'}>
          {col.valueLabels?.[String(value)] ?? (value ? '是' : '否')}
        </Tag>
      );

    case 'DATE':
      return value
        ? dayjs(value).format(col.format ?? 'YYYY-MM-DD')
        : '-';

    case 'DATETIME':
      return value
        ? dayjs(value).format(col.format ?? 'YYYY-MM-DD HH:mm:ss')
        : '-';

    case 'MONEY': {
      const num = Number(value);
      if (isNaN(num)) return String(value);
      const fmt = col.format ?? '#,##0.00';
      return (
        <Text style={{ fontVariantNumeric: 'tabular-nums' }}>
          {fmt.includes(',')
            ? num.toLocaleString('zh-CN', {
                minimumFractionDigits: col.precision ?? 2,
                maximumFractionDigits: col.precision ?? 2,
              })
            : num.toFixed(col.precision ?? 2)}
        </Text>
      );
    }

    case 'LINK': {
      const url = col.linkPattern
        ? col.linkPattern.replace(`{${col.key}}`, String(value))
        : String(value);
      return (
        <a href={url} target="_blank" rel="noopener noreferrer">
          {label ?? String(value)}
        </a>
      );
    }

    case 'JSON':
      return (
        <Tooltip title={<pre style={{ margin: 0, fontSize: 11 }}>{JSON.stringify(value, null, 2)}</pre>}>
          <code style={{ cursor: 'pointer', fontSize: 11 }}>
            {JSON.stringify(value).substring(0, col.maxDisplayLength ?? 50)}...
          </code>
        </Tooltip>
      );

    case 'IMAGE':
      return value ? <img src={String(value)} alt="" style={{ maxHeight: 40 }} /> : null;

    case 'HTML':
      return <div dangerouslySetInnerHTML={{ __html: String(value) }} />;

    default: {
      const str = String(value);
      const maxLen = col.maxDisplayLength ?? 100;
      if (str.length > maxLen) {
        return (
          <Tooltip title={str}>
            <span>{str.substring(0, maxLen)}...</span>
          </Tooltip>
        );
      }
      return str;
    }
  }
};

const DynamicTable: React.FC<DynamicTableProps> = ({
  result,
  loading,
  onPageChange,
  onSort,
  rowKey = 'id',
  scrollX,
}) => {
  const columns = useMemo<ColumnsType<any>>(() => {
    if (!result?.columns) return [];
    return result.columns.map((col) => ({
      key: col.key,
      title: col.label,
      dataIndex: col.key,
      width: col.width,
      sorter: col.sortable ? true : false,
      ellipsis: true,
      render: (value: any) => renderCell(value, col),
    }));
  }, [result?.columns]);

  const pagination = result ? {
    current: result.page + 1,
    pageSize: result.pageSize,
    total: result.total,
    showSizeChanger: true,
    showQuickJumper: true,
    showTotal: (total: number) => `共 ${total} 条`,
    onChange: (page: number, pageSize: number) => onPageChange?.(page - 1, pageSize),
  } : false;

  const handleChange: TableProps<any>['onChange'] = (_p, _f, sorter) => {
    if (onSort && !Array.isArray(sorter)) {
      const field = typeof sorter.field === 'string' ? sorter.field : '';
      const dir = sorter.order === 'ascend' ? 'ASC' : sorter.order === 'descend' ? 'DESC' : '';
      if (field && dir) onSort(field, dir);
    }
  };

  if (!result) {
    return <Empty description="暂无数据" style={{ marginTop: 80 }} />;
  }

  if (result.status === 'error') {
    return (
      <Empty
        description={
          <Text type="danger">{result.error || '查询失败'}</Text>
        }
        style={{ marginTop: 80 }}
      />
    );
  }

  return (
    <div>
      <div style={{ marginBottom: 8, color: '#999', fontSize: 12 }}>
        <Space>
          <span>查询耗时: {result.executionTimeMs}ms</span>
          <span>共 {result.total} 条</span>
        </Space>
      </div>
      <Table
        columns={columns}
        dataSource={result.data}
        rowKey={rowKey}
        loading={loading}
        pagination={pagination}
        onChange={handleChange}
        size="small"
        scroll={scrollX ? { x: scrollX } : undefined}
      />
    </div>
  );
};

export default DynamicTable;
