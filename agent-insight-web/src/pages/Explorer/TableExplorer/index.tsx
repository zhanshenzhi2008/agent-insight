import React, { useState, useEffect } from 'react';
import {
  Card, Select, Button, Space, Typography, Spin, Drawer,
  Descriptions, Tag, message, Divider
} from 'antd';
import { DatabaseOutlined, SettingOutlined } from '@ant-design/icons';
import DynamicTable from '../../components/DynamicTable';
import type { QueryResult } from '../../components/DynamicTable';
import { explorerApi } from '../../services/explorerApi';

const { Text } = Typography;

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

  useEffect(() => {
    explorerApi.listDatasources().then(r => {
      if (r.data.code === 0) setDatasources(r.data.data || []);
    });
  }, []);

  useEffect(() => {
    if (!selectedDs) return;
    explorerApi.listTables(selectedDs).then(r => {
      if (r.data.code === 0) setTables(r.data.data || []);
    });
    explorerApi.listColumns(selectedDs, selectedTable || '').then(r => {
      if (r.data.code === 0) setColumns(r.data.data || []);
    });
  }, [selectedDs]);

  useEffect(() => {
    if (!selectedDs || !selectedTable) return;
    loadData();
    explorerApi.listColumns(selectedDs, selectedTable).then(r => {
      if (r.data.code === 0) setColumns(r.data.data || []);
    });
  }, [selectedDs, selectedTable]);

  const loadData = async () => {
    if (!selectedDs || !selectedTable) return;
    setLoading(true);
    try {
      const res = await explorerApi.execute({
        datasourceKey: selectedDs,
        tableName: selectedTable,
        page,
        pageSize,
      });
      if (res.data.code === 0) {
        setResult(res.data.data);
      } else {
        message.error(res.data.message);
      }
    } catch (e: any) {
      message.error('查询失败: ' + (e.response?.data?.message || ''));
    } finally {
      setLoading(false);
    }
  };

  const handlePageChange = (p: number, ps: number) => {
    setPage(p);
    setPageSize(ps);
    if (selectedDs && selectedTable) {
      setLoading(true);
      explorerApi.execute({
        datasourceKey: selectedDs,
        tableName: selectedTable,
        page: p,
        pageSize: ps,
      }).then(r => {
        if (r.data.code === 0) setResult(r.data.data);
      }).finally(() => setLoading(false));
    }
  };

  const selectedTableConfig = tables.find(t => t.tableName === selectedTable);

  return (
    <div>
      <Card
        title={<><DatabaseOutlined /> 数据浏览器</>}
        extra={<Button icon={<SettingOutlined />} onClick={() => setConfigDrawer(true)}>配置管理</Button>}
      >
        <Space wrap style={{ marginBottom: 16 }}>
          <Text>数据源：</Text>
          <Select
            placeholder="选择数据源"
            value={selectedDs}
            onChange={(v) => { setSelectedDs(v); setSelectedTable(null); setResult(null); }}
            style={{ width: 200 }}
            options={datasources.map(d => ({ value: d.datasourceKey, label: d.datasourceName }))}
          />
          <Text>表：</Text>
          <Select
            placeholder="选择表"
            value={selectedTable}
            onChange={(v) => { setSelectedTable(v); setPage(0); }}
            style={{ width: 220 }}
            options={tables.map(t => ({ value: t.tableName, label: t.displayName || t.tableName }))}
          />
          <Button type="primary" onClick={loadData} loading={loading}>刷新</Button>
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
          </Descriptions>
        )}

        <DynamicTable
          result={result}
          loading={loading}
          onPageChange={handlePageChange}
        />
      </Card>

      <Drawer
        title="列配置预览"
        placement="right"
        width={400}
        open={configDrawer}
        onClose={() => setConfigDrawer(false)}
      >
        {!selectedDs || !selectedTable ? (
          <Text type="secondary">请先选择数据源和表</Text>
        ) : columns.length === 0 ? (
          <Text type="secondary">暂无列配置，将自动推断</Text>
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
    </div>
  );
};

export default TableExplorerPage;
