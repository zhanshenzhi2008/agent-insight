import React, { useState, useEffect } from 'react';
import {
  Card, Select, Button, Space, Typography, Drawer,
  Descriptions, Tag, message, Input, Tooltip, Modal, Form, Empty, Spin
} from 'antd';
import {
  DatabaseOutlined, SettingOutlined, ReloadOutlined,
  FilterOutlined, ClearOutlined, RobotOutlined, ThunderboltOutlined
} from '@ant-design/icons';
import DynamicTable from '../../../components/DynamicTable';
import type { QueryResult } from '../../../components/DynamicTable';
import { explorerApi } from '../../../services/explorerApi';
import type { QueryFilter, QueryOrder } from '../../../services/explorerApi';

const { Text } = Typography;

interface FilterRow {
  key: string;
  column: string;
  operator: QueryFilter['operator'];
  value?: any;
  value2?: any;
  combine?: 'AND' | 'OR';
}

const TableExplorerPage: React.FC = () => {
  const [datasources, setDatasources] = useState<any[]>([]);
  const [tables, setTables] = useState<any[]>([]);
  const [columns, setColumns] = useState<any[]>([]);
  const [selectedDs, setSelectedDs] = useState<string | null>(null);
  const [selectedTable, setSelectedTable] = useState<string | null>(null);
  const [result, setResult] = useState<QueryResult | null>(null);
  const [loading, setLoading] = useState(false);
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(20);
  const [configDrawer, setConfigDrawer] = useState(false);
  const [filterDrawer, setFilterDrawer] = useState(false);
  const [filters, setFilters] = useState<FilterRow[]>([]);
  const [sortField, setSortField] = useState<string | undefined>();
  const [sortDir, setSortDir] = useState<'ASC' | 'DESC'>('DESC');
  const [nlModalOpen, setNlModalOpen] = useState(false);
  const [nlText, setNlText] = useState('');
  const [nlLoading, setNlLoading] = useState(false);
  const [aiStatus, setAiStatus] = useState<any>(null);

  useEffect(() => {
    explorerApi.listDatasources().then((r: any) => {
      if (r.data.code === 0) setDatasources(r.data.data || []);
    });
    explorerApi.getAiStatus().then((r: any) => {
      if (r.data.code === 0) setAiStatus(r.data.data);
    }).catch(() => setAiStatus({ enabled: false }));
  }, []);

  useEffect(() => {
    if (!selectedDs) return;
    explorerApi.listTables(selectedDs).then((r: any) => {
      if (r.data.code === 0) setTables(r.data.data || []);
    });
  }, [selectedDs]);

  useEffect(() => {
    if (!selectedDs || !selectedTable) return;
    loadData();
    explorerApi.listColumns(selectedDs, selectedTable).then((r: any) => {
      if (r.data.code === 0) setColumns(r.data.data || []);
    });
  }, [selectedDs, selectedTable]);

  const buildRequest = (pageOverride?: number, pageSizeOverride?: number): any => {
    const payload: any = {
      datasourceKey: selectedDs,
      tableName: selectedTable,
      page: pageOverride ?? page,
      pageSize: pageSizeOverride ?? pageSize,
    };
    const validFilters: QueryFilter[] = filters
      .filter(f => f.column && f.operator)
      .map(({ key, ...rest }) => rest);
    if (validFilters.length > 0) payload.filters = validFilters;
    if (sortField) payload.orders = [{ field: sortField, direction: sortDir } as QueryOrder];
    return payload;
  };

  const loadData = async () => {
    if (!selectedDs || !selectedTable) return;
    setLoading(true);
    try {
      const res = await explorerApi.execute(buildRequest());
      if (res.data.code === 0) {
        setResult(res.data.data);
      } else {
        message.error(res.data.message || '查询失败');
      }
    } catch (e: any) {
      message.error('查询失败: ' + (e.response?.data?.message || e.message));
    } finally {
      setLoading(false);
    }
  };

  const handlePageChange = (p: number, ps: number) => {
    setPage(p);
    setPageSize(ps);
    if (selectedDs && selectedTable) {
      setLoading(true);
      explorerApi.execute(buildRequest(p, ps)).then((r: any) => {
        if (r.data.code === 0) setResult(r.data.data);
      }).finally(() => setLoading(false));
    }
  };

  const handleSort = (field: string, direction: string) => {
    setSortField(field);
    setSortDir(direction as 'ASC' | 'DESC');
    setPage(0);
    if (selectedDs && selectedTable) {
      setLoading(true);
      explorerApi.execute(buildRequest(0, pageSize)).then((r: any) => {
        if (r.data.code === 0) setResult(r.data.data);
      }).finally(() => setLoading(false));
    }
  };

  const addFilterRow = () => {
    setFilters(prev => [
      ...prev,
      {
        key: `${Date.now()}-${Math.random()}`,
        column: columns[0]?.columnName || '',
        operator: 'EQ',
        value: '',
      },
    ]);
  };

  const updateFilterRow = (key: string, patch: Partial<FilterRow>) => {
    setFilters(prev => prev.map(f => f.key === key ? { ...f, ...patch } : f));
  };

  const removeFilterRow = (key: string) => {
    setFilters(prev => prev.filter(f => f.key !== key));
  };

  const clearFilters = () => setFilters([]);

  const applyFiltersAndReload = () => {
    setFilterDrawer(false);
    setPage(0);
    loadData();
  };

  const onNlConfirm = async () => {
    if (!nlText.trim()) return;
    if (!columns.length) { message.warning('请先选择表'); return; }
    setNlLoading(true);
    try {
      const res = await explorerApi.nlQuery(nlText, columns);
      if (res.data.code === 0) {
        const aiFilters: QueryFilter[] = res.data.data || [];
        const rows: FilterRow[] = aiFilters.map((f, i) => ({
          key: `ai-${Date.now()}-${i}`,
          column: f.column,
          operator: f.operator,
          value: f.value,
          value2: f.value2,
        }));
        setFilters(rows);
        message.success(`AI 已识别 ${rows.length} 个条件`);
        setNlModalOpen(false);
        setFilterDrawer(true);
      } else {
        const err = (res.data as any).message || 'AI 解析失败';
        message.error(err);
      }
    } catch (e: any) {
      message.error('AI 解析失败: ' + (e.message || ''));
    } finally {
      setNlLoading(false);
    }
  };

  const summarize = async () => {
    if (!result?.data?.length) {
      message.warning('当前结果无数据');
      return;
    }
    Modal.confirm({
      title: 'AI 智能摘要',
      icon: <RobotOutlined />,
      content: (
        <Form layout="vertical" style={{ marginTop: 12 }}>
          <Form.Item label="请输入您关心的问题">
            <Input.TextArea
              id="nlq-summary"
              rows={3}
              placeholder="例如：统计失败率并指出主要原因"
            />
          </Form.Item>
        </Form>
      ),
      onOk: async () => {
        const el = document.getElementById('nlq-summary') as HTMLTextAreaElement | null;
        const q = el?.value || '';
        try {
          const res = await explorerApi.summarizeResults(result.data, columns, selectedTable || '', q);
          if (res.data.code === 0) {
            Modal.info({
              title: 'AI 摘要',
              width: 600,
              content: <div style={{ whiteSpace: 'pre-wrap' }}>{res.data.data}</div>,
            });
          } else {
            const err = (res.data as any).message || '摘要失败';
            message.error(err);
          }
        } catch (e: any) {
          message.error('摘要失败: ' + (e.message || ''));
        }
      },
    });
  };

  const selectedTableConfig = tables.find(t => t.tableName === selectedTable);
  const activeFilterCount = filters.filter(f => f.column && f.operator).length;

  return (
    <div>
      <Card
        title={<><DatabaseOutlined /> 数据浏览器</>}
        extra={
          <Space>
            <Button
              icon={<RobotOutlined />}
              onClick={summarize}
              disabled={!result?.data?.length}
            >
              AI 摘要
            </Button>
            <Button
              icon={<SettingOutlined />}
              onClick={() => setConfigDrawer(true)}
            >
              列配置
            </Button>
          </Space>
        }
      >
        <Space wrap style={{ marginBottom: 16 }}>
          <Text>数据源：</Text>
          <Select
            placeholder="选择数据源"
            value={selectedDs}
            onChange={(v) => { setSelectedDs(v); setSelectedTable(null); setResult(null); setFilters([]); }}
            style={{ width: 200 }}
            options={datasources.map(d => ({ value: d.datasourceKey, label: d.datasourceName }))}
          />
          <Text>表：</Text>
          <Select
            placeholder="选择表"
            value={selectedTable}
            onChange={(v) => { setSelectedTable(v); setPage(0); setFilters([]); setSortField(undefined); }}
            style={{ width: 220 }}
            options={tables.map(t => ({ value: t.tableName, label: t.displayName || t.tableName }))}
          />
          <Tooltip title="点击配置过滤条件">
            <Button
              icon={<FilterOutlined />}
              onClick={() => setFilterDrawer(true)}
              disabled={!selectedTable}
            >
              过滤 {activeFilterCount > 0 && <Tag color="processing" style={{ marginLeft: 4 }}>{activeFilterCount}</Tag>}
            </Button>
          </Tooltip>
          {(sortField || activeFilterCount > 0) && (
            <Button
              icon={<ClearOutlined />}
              onClick={() => { setFilters([]); setSortField(undefined); }}
              danger
            >
              清除条件
            </Button>
          )}
          <Button type="primary" icon={<ReloadOutlined />} onClick={loadData} loading={loading}>
            刷新
          </Button>
        </Space>

        {selectedTableConfig && (
          <Descriptions size="small" style={{ marginBottom: 12 }}>
            <Descriptions.Item label="描述">{selectedTableConfig.description || '-'}</Descriptions.Item>
            <Descriptions.Item label="默认分页">{selectedTableConfig.pageSize || 20}</Descriptions.Item>
            <Descriptions.Item label="列配置">
              {columns.length > 0
                ? <Tag color="green">已配置 {columns.length} 列</Tag>
                : <Tag>自动推断</Tag>}
            </Descriptions.Item>
            {sortField && (
              <Descriptions.Item label="排序">
                <Tag>{sortField} {sortDir === 'DESC' ? '↓' : '↑'}</Tag>
              </Descriptions.Item>
            )}
          </Descriptions>
        )}

        <DynamicTable
          result={result}
          loading={loading}
          onPageChange={handlePageChange}
          onSort={handleSort}
        />
      </Card>

      {/* 列配置预览 */}
      <Drawer
        title="列配置预览"
        placement="right"
        styles={{ wrapper: { width: 400 } }}
        open={configDrawer}
        onClose={() => setConfigDrawer(false)}
      >
        {!selectedDs || !selectedTable ? (
          <Text type="secondary">请先选择数据源和表</Text>
        ) : columns.length === 0 ? (
          <Empty description="暂无列配置，将自动推断" />
        ) : (
          columns.map((col: any) => (
            <div key={col.id || col.columnName} style={{ marginBottom: 12, padding: 8, border: '1px solid #f0f0f0', borderRadius: 4 }}>
              <Space>
                <Text strong>{col.displayName || col.columnName}</Text>
                <Tag>{col.dataType}</Tag>
                <Tag color="blue">{col.renderType}</Tag>
              </Space>
              <br />
              <Text type="secondary" style={{ fontSize: 12 }}>
                {col.columnName} | width={col.width} | order={col.orderIndex}
              </Text>
            </div>
          ))
        )}
      </Drawer>

      {/* 过滤配置 */}
      <Drawer
        title="过滤条件"
        placement="right"
        styles={{ wrapper: { width: 620 } }}
        open={filterDrawer}
        onClose={() => setFilterDrawer(false)}
        extra={
          <Space>
            <Button
              icon={<RobotOutlined />}
              onClick={() => setNlModalOpen(true)}
              disabled={!aiStatus?.enabled || !columns.length}
            >
              自然语言
            </Button>
            <Button onClick={clearFilters} disabled={filters.length === 0}>清空</Button>
            <Button type="primary" icon={<ThunderboltOutlined />} onClick={applyFiltersAndReload}>
              应用
            </Button>
          </Space>
        }
      >
        {!columns.length ? (
          <Empty description="请先选择表" />
        ) : filters.length === 0 ? (
          <Empty description="暂无过滤条件，点击下方新增">
            <Button type="primary" onClick={addFilterRow}>新增条件</Button>
          </Empty>
        ) : (
          <Space direction="vertical" style={{ width: '100%' }}>
            {filters.map((f, idx) => (
              <Space key={f.key} wrap style={{ width: '100%', display: 'flex' }}>
                {idx > 0 && (
                  <Select
                    size="small"
                    defaultValue="AND"
                    style={{ width: 70 }}
                    options={[
                      { value: 'AND', label: 'AND' },
                      { value: 'OR', label: 'OR' },
                    ]}
                    onChange={v => updateFilterRow(f.key, { combine: v as any })}
                  />
                )}
                <Select
                  size="small"
                  showSearch
                  optionFilterProp="label"
                  value={f.column}
                  style={{ width: 160 }}
                  options={columns.map((c: any) => ({ value: c.columnName, label: c.columnName }))}
                  onChange={v => updateFilterRow(f.key, { column: v })}
                />
                <Select
                  size="small"
                  value={f.operator}
                  style={{ width: 110 }}
                  options={[
                    { value: 'EQ', label: '=' },
                    { value: 'NE', label: '!=' },
                    { value: 'GT', label: '>' },
                    { value: 'GTE', label: '>=' },
                    { value: 'LT', label: '<' },
                    { value: 'LTE', label: '<=' },
                    { value: 'LIKE', label: 'LIKE' },
                    { value: 'IN', label: 'IN' },
                    { value: 'NOT_IN', label: 'NOT IN' },
                    { value: 'IS_NULL', label: 'IS NULL' },
                    { value: 'IS_NOT_NULL', label: 'NOT NULL' },
                    { value: 'BETWEEN', label: 'BETWEEN' },
                  ]}
                  onChange={v => updateFilterRow(f.key, { operator: v })}
                />
                {!['IS_NULL', 'IS_NOT_NULL'].includes(f.operator) && (
                  <Input
                    size="small"
                    placeholder={f.operator === 'IN' || f.operator === 'NOT_IN' ? '逗号分隔' : '值'}
                    style={{ width: 160 }}
                    value={f.value ?? ''}
                    onChange={e => updateFilterRow(f.key, { value: e.target.value })}
                  />
                )}
                {f.operator === 'BETWEEN' && (
                  <Input
                    size="small"
                    placeholder="值2"
                    style={{ width: 120 }}
                    value={f.value2 ?? ''}
                    onChange={e => updateFilterRow(f.key, { value2: e.target.value })}
                  />
                )}
                <Button
                  size="small"
                  type="link"
                  danger
                  onClick={() => removeFilterRow(f.key)}
                >
                  删除
                </Button>
              </Space>
            ))}
            <Button block icon={<FilterOutlined />} onClick={addFilterRow}>新增条件</Button>
          </Space>
        )}
      </Drawer>

      {/* 自然语言 */}
      <Modal
        title={<><RobotOutlined /> 自然语言转查询条件</>}
        open={nlModalOpen}
        onCancel={() => setNlModalOpen(false)}
        onOk={onNlConfirm}
        confirmLoading={nlLoading}
        okText="解析"
        cancelText="取消"
      >
        <p style={{ color: '#666' }}>
          示例：<code>近 7 天失败的请求、agentName = price、promptTokens 大于 1000</code>
        </p>
        <Input.TextArea
          rows={4}
          value={nlText}
          onChange={e => setNlText(e.target.value)}
          placeholder="请输入您想查询的内容..."
        />
        {nlLoading && (
          <div style={{ textAlign: 'center', marginTop: 12 }}>
            <Spin /> <Text type="secondary">AI 解析中...</Text>
          </div>
        )}
      </Modal>
    </div>
  );
};

export default TableExplorerPage;