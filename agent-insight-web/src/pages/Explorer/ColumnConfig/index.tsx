import React, { useState, useEffect } from 'react';
import {
  Card, Table, Select, Button, Space, message, Tag, Typography,
  Switch, Input, InputNumber, Popconfirm, Tooltip, Row, Col,
  Statistic, Badge, Alert, Spin, Drawer, Descriptions
} from 'antd';
import {
  ThunderboltOutlined, RobotOutlined, CheckCircleOutlined,
  DeleteOutlined, PlusOutlined,
  LoadingOutlined
} from '@ant-design/icons';
import { explorerApi } from '../../../services/explorerApi';
import type { ColumnsType } from 'antd/es/table';

const { Text } = Typography;

interface AiColumnAnalysis {
  columnName: string;
  recommendedRenderType: string;
  recommendedDataType: string;
  reason: string;
  suggestedDisplayName: string;
  suggestedDateFormat?: string;
  suggestedNumberFormat?: string;
  valueLabels?: Record<string, string>;
  tagColors?: Record<string, string>;
  isTimeField?: boolean;
  isHighlight?: boolean;
}

interface AnalyzedColumn {
  id?: string;
  columnName: string;
  displayName: string;
  dataType: string;
  renderType: string;
  nullRatio: number;
  distinctCount: number;
  distinctRatio: number;
  allSame: boolean;
  dateFormat?: string;
  numberFormat?: string;
  tagColors?: Record<string, string>;
  valueLabels?: Record<string, string>;
  timeField?: boolean;
  topValues?: Array<{ value: string; count: number; ratio: string }>;
  enabled?: boolean;
  hidden?: boolean;
  aiAnalysis?: AiColumnAnalysis;
}

const DATA_TYPES = ['STRING', 'NUMBER', 'DATETIME', 'BOOLEAN', 'JSON', 'TEXT', 'ENUM'];
const RENDER_TYPES = ['TEXT', 'TAG', 'BOOLEAN', 'MONEY', 'DATE', 'DATETIME', 'LINK', 'JSON', 'IMAGE', 'HTML'];

const NullBar: React.FC<{ ratio: number }> = ({ ratio }) => {
  const pct = Math.round(ratio * 100);
  return (
    <Tooltip title={`${pct}% 空值`}>
      <div style={{ width: 70, display: 'inline-block' }}>
        <div style={{
          width: '100%', height: 6, background: '#f0f0f0', borderRadius: 3, overflow: 'hidden'
        }}>
          <div style={{
            width: `${100 - pct}%`, height: '100%', background:
              pct > 50 ? '#ff4d4f' : pct > 20 ? '#faad14' : '#52c41a'
          }} />
        </div>
        <Text type="secondary" style={{ fontSize: 10 }}>{pct}%</Text>
      </div>
    </Tooltip>
  );
};

const AiInsightDrawer: React.FC<{
  column: AnalyzedColumn | null;
  onClose: () => void;
  onApply: (analysis: AiColumnAnalysis) => void;
  analyzing: boolean;
}> = ({ column, onClose, onApply, analyzing }) => {
  if (!column) return null;

  return (
    <Drawer
      title={<><ThunderboltOutlined /> AI 列分析详情 — {column.columnName}</>}
      placement="right"
      styles={{ wrapper: { width: 420 } }}
      open={!!column}
      onClose={onClose}
      extra={
        column.aiAnalysis ? (
          <Button type="primary" icon={<CheckCircleOutlined />} onClick={() => onApply(column.aiAnalysis!)}>
            应用 AI 推荐
          </Button>
        ) : analyzing ? (
          <Spin indicator={<LoadingOutlined spin />} />
        ) : null
      }
    >
      {column.aiAnalysis ? (
        <>
          <Descriptions column={1} bordered size="small" style={{ marginBottom: 16 }}>
            <Descriptions.Item label="推荐渲染类型">
              <Tag color="blue">{column.aiAnalysis.recommendedRenderType}</Tag>
            </Descriptions.Item>
            <Descriptions.Item label="推荐数据类型">
              <Tag>{column.aiAnalysis.recommendedDataType}</Tag>
            </Descriptions.Item>
            <Descriptions.Item label="推荐展示名">{column.aiAnalysis.suggestedDisplayName}</Descriptions.Item>
            {column.aiAnalysis.suggestedDateFormat && (
              <Descriptions.Item label="日期格式">{column.aiAnalysis.suggestedDateFormat}</Descriptions.Item>
            )}
            {column.aiAnalysis.suggestedNumberFormat && (
              <Descriptions.Item label="数字格式">{column.aiAnalysis.suggestedNumberFormat}</Descriptions.Item>
            )}
            <Descriptions.Item label="时间字段">
              <Tag color={column.aiAnalysis.isTimeField ? 'orange' : 'default'}>
                {column.aiAnalysis.isTimeField ? '是' : '否'}
              </Tag>
            </Descriptions.Item>
          </Descriptions>
          <Text strong>AI 判断理由：</Text>
          <div style={{ marginTop: 8, padding: 12, background: '#f6ffed', borderRadius: 6, border: '1px solid #b7eb8f' }}>
            {column.aiAnalysis.reason}
          </div>
          {column.aiAnalysis.valueLabels && Object.keys(column.aiAnalysis.valueLabels).length > 0 && (
            <>
              <Text strong style={{ marginTop: 16, display: 'block' }}>值标签映射：</Text>
              <div style={{ marginTop: 8 }}>
                {Object.entries(column.aiAnalysis.valueLabels).map(([k, v]) => (
                  <Tag key={k} style={{ marginBottom: 4 }}>{k} → {v}</Tag>
                ))}
              </div>
            </>
          )}
        </>
      ) : analyzing ? (
        <div style={{ textAlign: 'center', marginTop: 60 }}>
          <Spin size="large" indicator={<LoadingOutlined style={{ fontSize: 32 }} spin />} />
          <div style={{ marginTop: 16 }}>AI 正在分析中...</div>
        </div>
      ) : (
        <Alert message="点击「AI 分析」按钮获取 AI 推荐配置" type="info" showIcon />
      )}
    </Drawer>
  );
};

const ColumnConfigPage: React.FC = () => {
  const [datasources, setDatasources] = useState<any[]>([]);
  const [tables, setTables] = useState<any[]>([]);
  const [selectedDs, setSelectedDs] = useState<string>('');
  const [selectedTable, setSelectedTable] = useState<string>('');
  const [, setColumns] = useState<AnalyzedColumn[]>([]);
  const [allColumns, setAllColumns] = useState<AnalyzedColumn[]>([]);
  const [analyzing, setAnalyzing] = useState(false);
  const [aiBatchLoading, setAiBatchLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [aiStatus, setAiStatus] = useState<any>(null);
  const [activeTab, setActiveTab] = useState('enabled');
  const [aiDrawerColumn, setAiDrawerColumn] = useState<AnalyzedColumn | null>(null);
  const [aiDrawerLoading, setAiDrawerLoading] = useState(false);

  // 加载数据源和 AI 状态
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
    if (selectedTable) loadExistingConfig();
  }, [selectedDs]);

  useEffect(() => {
    if (!selectedDs || !selectedTable) return;
    loadExistingConfig();
  }, [selectedTable]);

  const loadExistingConfig = async () => {
    try {
      const r = await explorerApi.listColumns(selectedDs, selectedTable);
      if (r.data.code === 0) {
        const existing = r.data.data || [];
        setColumns(existing.filter((c: any) => !c.hidden));
        setAllColumns(existing);
      }
    } catch {}
  };

  // 统计推断分析
  const handleAIAnalyze = async () => {
    if (!selectedDs || !selectedTable) { message.warning('请先选择数据源和表'); return; }
    setAnalyzing(true);
    try {
      const res = await explorerApi.analyzeColumns(selectedDs, selectedTable);
      if (res.data.code === 0) {
        const analyzed = res.data.data as AnalyzedColumn[];
        const merged = analyzed.map(a => {
          const existing = allColumns.find((c: any) => c.columnName === a.columnName);
          return {
            ...a,
            enabled: existing ? !existing.hidden : true,
            displayName: existing?.displayName || a.displayName,
            dataType: existing?.dataType || a.dataType,
            renderType: existing?.renderType || a.renderType,
            dateFormat: existing?.dateFormat || a.dateFormat,
            numberFormat: existing?.numberFormat || a.numberFormat,
            tagColors: existing?.tagColors || a.tagColors,
            valueLabels: existing?.valueLabels || a.valueLabels,
            timeField: existing?.timeField ?? a.timeField,
          };
        });
        setAllColumns(merged);
        setColumns(merged.filter(c => c.enabled));
        message.success(`分析完成，发现 ${analyzed.length} 个字段`);
      } else {
        const err = (res.data as any).message || '分析失败';
        message.error(err);
      }
    } catch (e: any) {
      message.error('分析失败: ' + (e.response?.data?.message || e.message));
    } finally {
      setAnalyzing(false);
    }
  };

  // AI 语义增强（单列）
  const handleSingleColumnAi = async (col: AnalyzedColumn) => {
    setAiDrawerColumn({ ...col });
    setAiDrawerLoading(true);
    try {
      const res = await explorerApi.analyzeColumnWithAi(
        col.columnName, col.displayName, col.dataType, col.renderType,
        {
          sampleValues: col.topValues?.map(tv => tv.value) || [],
          topValues: col.topValues || [],
        }
      );
      if (res.data.code === 0 && res.data.data) {
        const analysis = res.data.data;
        // 更新抽屉中的数据
        setAiDrawerColumn(prev => prev ? { ...prev, aiAnalysis: analysis } : null);
        // 更新列数据
        const updated = allColumns.map(c =>
          c.columnName === col.columnName ? { ...c, aiAnalysis: analysis } : c);
        setAllColumns(updated);
        setColumns(updated.filter(c => c.enabled));
      }
    } catch (e: any) {
      message.error('AI 分析失败: ' + (e.response?.data?.message || e.message));
    } finally {
      setAiDrawerLoading(false);
    }
  };

  // AI 批量增强（对所有已分析列调用 AI）
  const handleAiBatchEnhance = async () => {
    if (!aiStatus?.enabled) { message.warning('请先在配置中启用 AI 功能'); return; }
    const enabledCols = allColumns.filter(c => c.enabled);
    if (enabledCols.length === 0) { message.warning('没有已启用的列'); return; }

    setAiBatchLoading(true);
    let success = 0;
    for (const col of enabledCols) {
      try {
        const res = await explorerApi.analyzeColumnWithAi(
          col.columnName, col.displayName, col.dataType, col.renderType,
          {
            sampleValues: col.topValues?.map(tv => tv.value) || [],
            topValues: col.topValues || [],
          }
        );
        if (res.data.code === 0 && res.data.data) {
          const analysis = res.data.data;
          const updated = allColumns.map(c =>
            c.columnName === col.columnName ? { ...c, aiAnalysis: analysis } : c);
          setAllColumns(updated);
          setColumns(updated.filter(c => c.enabled));
          success++;
        }
      } catch {}
      await new Promise(r => setTimeout(r, 200)); // 避免请求过快
    }
    setAiBatchLoading(false);
    message.success(`AI 语义增强完成：${success}/${enabledCols.length} 列`);
  };

  // 应用 AI 推荐
  const applyAiRecommendation = (analysis: AiColumnAnalysis) => {
    if (!aiDrawerColumn) return;
    const updated = allColumns.map(c =>
      c.columnName === analysis.columnName ? {
        ...c,
        displayName: analysis.suggestedDisplayName || c.displayName,
        dataType: analysis.recommendedDataType || c.dataType,
        renderType: analysis.recommendedRenderType || c.renderType,
        dateFormat: analysis.suggestedDateFormat || c.dateFormat,
        numberFormat: analysis.suggestedNumberFormat || c.numberFormat,
        tagColors: analysis.tagColors || c.tagColors,
        valueLabels: analysis.valueLabels || c.valueLabels,
        timeField: analysis.isTimeField ?? c.timeField,
        aiAnalysis: analysis,
      } : c);
    setAllColumns(updated);
    setColumns(updated.filter(c => c.enabled));
    setAiDrawerColumn(null);
    message.success('AI 推荐已应用到该列');
  };

  // 保存
  const handleSave = async () => {
    if (!selectedDs || !selectedTable) return;
    setSaving(true);
    try {
      const toSave = allColumns.map((c, idx) => ({
        columnName: c.columnName,
        displayName: c.displayName || c.columnName,
        dataType: c.dataType || 'STRING',
        renderType: c.renderType || 'TEXT',
        hidden: !c.enabled,
        sortable: true,
        filterable: true,
        timeField: c.timeField || false,
        dateFormat: c.dateFormat,
        numberFormat: c.numberFormat,
        tagColors: c.tagColors,
        valueLabels: c.valueLabels,
        orderIndex: idx,
      }));
      await explorerApi.saveColumns(toSave as any[]);
      message.success('配置保存成功');
    } catch (e: any) {
      message.error('保存失败: ' + (e.response?.data?.message || ''));
    } finally {
      setSaving(false);
    }
  };

  const toggleEnabled = (colName: string, enabled: boolean) => {
    const updated = allColumns.map(c => c.columnName === colName ? { ...c, enabled } : c);
    setAllColumns(updated);
    setColumns(updated.filter(c => c.enabled));
  };

  const toggleAll = (enabled: boolean) => {
    const updated = allColumns.map(c => ({ ...c, enabled }));
    setAllColumns(updated);
    setColumns(updated.filter(c => c.enabled));
  };

  const updateColumn = (colName: string, updates: Partial<AnalyzedColumn>) => {
    const updated = allColumns.map(c => c.columnName === colName ? { ...c, ...updates } : c);
    setAllColumns(updated);
    setColumns(updated.filter(c => c.enabled));
  };

  const addColumn = () => {
    const name = `custom_field_${Date.now()}`;
    const newCol: AnalyzedColumn = {
      columnName: name, displayName: '自定义字段', dataType: 'STRING',
      renderType: 'TEXT', nullRatio: 0, distinctCount: 0, distinctRatio: 0, allSame: false, enabled: true,
    };
    const updated = [...allColumns, newCol];
    setAllColumns(updated);
    setColumns(updated.filter(c => c.enabled));
  };

  const enabledCount = allColumns.filter(c => c.enabled).length;
  const totalCount = allColumns.length;
  const aiEnhancedCount = allColumns.filter(c => c.aiAnalysis).length;
  const displayColumns = activeTab === 'enabled'
    ? allColumns.filter(c => c.enabled)
    : allColumns.filter(c => !c.enabled);

  const columns_def: ColumnsType<AnalyzedColumn> = [
    { title: '启用', key: 'enabled', width: 60, fixed: 'left', render: (_, r) => (
        <Switch size="small" checked={r.enabled} onChange={v => toggleEnabled(r.columnName, v)} />
      )},
    { title: '字段名', dataIndex: 'columnName', key: 'columnName', width: 160, fixed: 'left',
      render: (v: string, r) => (
        <Space>
          <Text code style={{ fontSize: 12 }}>{v}</Text>
          {r.timeField && <Tag color="orange" style={{ fontSize: 10 }}>时间</Tag>}
          {r.aiAnalysis && <Tag color="purple" icon={<ThunderboltOutlined />} style={{ fontSize: 10 }}>AI</Tag>}
        </Space>
      )},
    { title: '展示名', key: 'displayName', width: 150, render: (_, r) => (
        <Input value={r.displayName} size="small" style={{ width: 140 }}
          onChange={e => updateColumn(r.columnName, { displayName: e.target.value })} />
      )},
    { title: '数据类型', dataIndex: 'dataType', key: 'dataType', width: 110, render: (v: string, r) => (
        <Select value={v} size="small" style={{ width: 100 }}
          options={DATA_TYPES.map(t => ({ value: t, label: t }))}
          onChange={val => updateColumn(r.columnName, { dataType: val })} />
      )},
    { title: '渲染类型', dataIndex: 'renderType', key: 'renderType', width: 110, render: (v: string, r) => (
        <Select value={v} size="small" style={{ width: 100 }}
          options={RENDER_TYPES.map(t => ({ value: t, label: t }))}
          onChange={val => updateColumn(r.columnName, { renderType: val })} />
      )},
    { title: '顺序', key: 'order', width: 70, render: (_, r) => (
        <InputNumber value={allColumns.indexOf(r)} size="small" min={0} style={{ width: 60 }}
          onChange={v => {
            if (v === null) return;
            const idx = allColumns.indexOf(r);
            const updated = [...allColumns];
            updated.splice(idx, 1);
            updated.splice(v, 0, r);
            setAllColumns(updated);
            setColumns(updated.filter(c => c.enabled));
          }} />
      )},
    { title: '日期格式', key: 'dateFormat', width: 130, render: (_, r) => (
        <Input value={r.dateFormat} size="small" placeholder="yyyy-MM-dd HH:mm:ss" style={{ width: 120 }}
          onChange={e => updateColumn(r.columnName, { dateFormat: e.target.value })} />
      )},
    { title: '数字格式', key: 'numberFormat', width: 110, render: (_, r) => (
        <Input value={r.numberFormat} size="small" placeholder="#,##0.00" style={{ width: 100 }}
          onChange={e => updateColumn(r.columnName, { numberFormat: e.target.value })} />
      )},
    { title: '空值率', key: 'nullRatio', width: 90, render: (_, r) => <NullBar ratio={r.nullRatio || 0} /> },
    { title: '去重/总行', key: 'distinct', width: 110, render: (_, r) => (
        <Text type="secondary" style={{ fontSize: 11 }}>
          {r.distinctCount} / {(r.distinctRatio * 100).toFixed(0)}%
        </Text>
      )},
    { title: '样本', key: 'topValues', width: 160, render: (_, r) => (
        r.topValues && r.topValues.length > 0 ? (
          <Space wrap size={2}>
            {r.topValues.slice(0, 2).map((tv, i) => (
              <Tag key={i} color={r.tagColors?.[tv.value] || 'default'} style={{ fontSize: 10, maxWidth: 80 }}>
                {tv.value}
              </Tag>
            ))}
          </Space>
        ) : <Text type="secondary">-</Text>
      )},
    {
      title: 'AI 分析',
      key: 'ai',
      width: 80,
      render: (_, r) => (
        <Tooltip title="AI 语义分析">
          <Button
            type="text"
            size="small"
            icon={aiDrawerColumn?.columnName === r.columnName && aiDrawerLoading
              ? <LoadingOutlined /> : <ThunderboltOutlined />}
            onClick={() => handleSingleColumnAi(r)}
            disabled={!aiStatus?.enabled}
          />
        </Tooltip>
      ),
    },
    { title: '操作', key: 'action', width: 70, fixed: 'right', render: (_, r) => (
        <Popconfirm title="移除此字段?" onConfirm={() => {
          const updated = allColumns.filter(c => c.columnName !== r.columnName);
          setAllColumns(updated);
          setColumns(updated.filter(c => c.enabled));
        }}>
          <Button type="link" size="small" danger icon={<DeleteOutlined />} />
        </Popconfirm>
      )},
  ];

  return (
    <div>
      <Card title={<><RobotOutlined /> 智能列配置</>}>
        <Space style={{ marginBottom: 12 }} wrap>
          <Select placeholder="数据源" value={selectedDs || undefined}
            onChange={v => { setSelectedDs(v); setSelectedTable(''); setAllColumns([]); setColumns([]); }}
            style={{ width: 180 }}
            options={datasources.map(d => ({ value: d.datasourceKey, label: d.datasourceName }))} />
          <Select placeholder="选择表" value={selectedTable || undefined}
            onChange={v => setSelectedTable(v)} style={{ width: 200 }}
            options={tables.map(t => ({ value: t.tableName, label: t.displayName || t.tableName }))}
            disabled={!selectedDs} />
        </Space>
      </Card>

      {selectedTable && (
        <>
          {/* 统计栏 */}
          <Card style={{ marginTop: 8 }}>
            <Row gutter={16}>
              <Col span={3}><Statistic title="字段总数" value={totalCount} styles={{ content: { color: '#1890ff', fontSize: 20 } }} /></Col>
              <Col span={3}><Statistic title="已启用" value={enabledCount} prefix={<CheckCircleOutlined />} styles={{ content: { color: '#52c41a', fontSize: 20 } }} /></Col>
              <Col span={3}><Statistic title="AI 增强" value={aiEnhancedCount} prefix={<ThunderboltOutlined />} styles={{ content: { color: '#722ed1', fontSize: 20 } }} /></Col>
              <Col span={9} style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                {/* AI 状态指示 */}
                <Badge status={aiStatus?.enabled ? 'success' : 'default'}
                  text={<Text type="secondary">{aiStatus?.enabled ? 'AI 已启用' : 'AI 未启用'}</Text>} />
                <Button type="primary" icon={<ThunderboltOutlined />}
                  onClick={handleAIAnalyze} loading={analyzing} disabled={!selectedDs || !selectedTable}>
                  {analyzing ? '分析中...' : '统计推断'}
                </Button>
                <Button icon={<ThunderboltOutlined />} onClick={handleAiBatchEnhance}
                  loading={aiBatchLoading} disabled={!aiStatus?.enabled || totalCount === 0}
                  style={{ background: '#f9f0ff', borderColor: '#722ed1', color: '#722ed1' }}>
                  AI 语义增强
                </Button>
              </Col>
              <Col span={6} style={{ textAlign: 'right', display: 'flex', alignItems: 'center', gap: 8 }}>
                <Button icon={<CheckCircleOutlined />} onClick={() => toggleAll(true)} disabled={totalCount === 0}>全启用</Button>
                <Button onClick={() => toggleAll(false)} disabled={totalCount === 0}>全禁用</Button>
                <Button type="primary" icon={<CheckCircleOutlined />} onClick={handleSave}
                  loading={saving} disabled={totalCount === 0}>
                  保存 {enabledCount > 0 && `(${enabledCount} 列)`}
                </Button>
              </Col>
            </Row>
          </Card>

          {/* 操作栏 */}
          <Card style={{ marginTop: 8, padding: '8px 12px' }}>
            <Space>
              <Button icon={<PlusOutlined />} onClick={addColumn}>手动添加</Button>
            </Space>
            <div style={{ float: 'right' }}>
              <Space>
                <Badge count={enabledCount} offset={[6, 0]}><Button size="small" onClick={() => setActiveTab('enabled')}>已启用</Button></Badge>
                <Badge count={totalCount - enabledCount} offset={[6, 0]}><Button size="small" onClick={() => setActiveTab('disabled')}>已禁用</Button></Badge>
              </Space>
            </div>
          </Card>

          {/* 列配置表 */}
          <Card style={{ marginTop: 8 }}>
            <Table columns={columns_def} dataSource={displayColumns} rowKey="columnName"
              pagination={false} size="small" scroll={{ x: 1800 }} />
          </Card>
        </>
      )}

      {/* AI 详情抽屉 */}
      <AiInsightDrawer
        column={aiDrawerColumn}
        onClose={() => setAiDrawerColumn(null)}
        onApply={applyAiRecommendation}
        analyzing={aiDrawerLoading}
      />
    </div>
  );
};

export default ColumnConfigPage;
