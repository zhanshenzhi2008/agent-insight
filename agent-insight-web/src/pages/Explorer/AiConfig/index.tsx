import React, { useState, useEffect, useCallback } from 'react';
import {
  Card, Form, Input, InputNumber, Select, Button, Space, Alert, Row, Col, Table, Modal, Popconfirm,
  message,   Tabs, Tag,
} from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined, ReloadOutlined } from '@ant-design/icons';
import { explorerApi } from '../../../services/explorerApi';

// ============================================================================
// Types
// ============================================================================

interface VendorRow {
  id: number;
  vendor: string;
  displayName?: string;
  baseUrl?: string;
  apiVersion?: string;
  proxyHost?: string;
  proxyPort?: number;
  timeoutSeconds?: number;
  maxRetries?: number;
  status: 0 | 1;
  description?: string;
  token?: string | null;
  createTime: string;
  updateTime: string;
}

interface ModelInstanceRow {
  id: number;
  vendorId: number;
  vendor?: string;
  modelName: string;
  deploymentName?: string;
  capability: string;
  tier: string;
  priority: number;
  maxTokens?: number;
  temperature?: number;
  topP?: number;
  isActive: 0 | 1;
  isCurrent: 0 | 1;
  description?: string;
  createTime: string;
  updateTime: string;
}

interface VendorFormValues {
  vendor: string;
  displayName?: string;
  baseUrl?: string;
  apiVersion?: string;
  proxyHost?: string;
  proxyPort?: number;
  timeoutSeconds?: number;
  maxRetries?: number;
  status: 0 | 1;
  description?: string;
  token?: string;
}

interface ModelInstanceFormValues {
  vendorId: number;
  modelName: string;
  deploymentName?: string;
  capability: string;
  tier: string;
  priority?: number;
  maxTokens?: number;
  temperature?: number;
  topP?: number;
  isActive?: 0 | 1;
  isCurrent?: 0 | 1;
  description?: string;
}

// ============================================================================
// Constants
// ============================================================================

const VENDOR_TYPE_OPTIONS = [
  { value: 'openai', label: 'openai' },
  { value: 'ollama', label: 'ollama' },
  { value: 'deepseek', label: 'deepseek' },
  { value: 'anthropic', label: 'anthropic' },
  { value: 'azure', label: 'azure' },
  { value: 'google-genai', label: 'google-genai' },
];

const CAPABILITY_OPTIONS = [
  { value: 'CHAT', label: 'CHAT' },
  { value: 'EMBEDDING', label: 'EMBEDDING' },
  { value: 'VISION', label: 'VISION' },
  { value: 'TTS', label: 'TTS' },
  { value: 'RERANKER', label: 'RERANKER' },
];

const TIER_OPTIONS = [
  { value: 'PRODUCTION', label: 'PRODUCTION（生产）' },
  { value: 'LIGHT', label: 'LIGHT（轻量）' },
  { value: 'EXPERIMENTAL', label: 'EXPERIMENTAL（实验）' },
];

const TIER_COLOR: Record<string, string> = {
  PRODUCTION: 'green',
  LIGHT: 'blue',
  EXPERIMENTAL: 'orange',
};

const CAPABILITY_COLOR: Record<string, string> = {
  CHAT: 'blue',
  EMBEDDING: 'purple',
  VISION: 'cyan',
  TTS: 'magenta',
  RERANKER: 'gold',
};

// ============================================================================
// Vendor CRUD
// ============================================================================

const VendorPanel: React.FC = () => {
  const [form] = Form.useForm<VendorFormValues>();
  const [list, setList] = useState<VendorRow[]>([]);
  const [loading, setLoading] = useState(false);
  const [editing, setEditing] = useState<VendorRow | null>(null);
  const [modalOpen, setModalOpen] = useState(false);

  const loadList = useCallback(async () => {
    setLoading(true);
    try {
      const res = await explorerApi.listAiVendors();
      const payload = (res.data as any);
      if (payload.code === 0) {
        setList(payload.data || []);
      } else {
        message.error('加载供应商失败');
      }
    } catch (e: any) {
      message.error('加载供应商失败: ' + (e.message || ''));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { loadList(); }, [loadList]);

  const openCreate = () => { setEditing(null); setModalOpen(true); };
  const openEdit = (row: VendorRow) => { setEditing(row); setModalOpen(true); };

  useEffect(() => {
    if (!modalOpen) return;
    if (editing) {
      form.setFieldsValue({ ...editing, token: '' });
    } else {
      form.resetFields();
      form.setFieldsValue({ vendor: 'openai', status: 1, timeoutSeconds: 30, maxRetries: 3 });
    }
  }, [modalOpen, editing, form]);

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      const payload: any = { ...values };
      if (editing && (!values.token || values.token === '')) delete payload.token;
      setLoading(true);
      const res = editing
        ? await explorerApi.updateAiVendor(editing.id, payload)
        : await explorerApi.createAiVendor(payload);
      if ((res.data as any).code === 0) {
        message.success(editing ? '已更新' : '已新建');
        setModalOpen(false);
        loadList();
      } else {
        message.error((res.data as any).message || '保存失败');
      }
    } catch (e: any) {
      if (e?.errorFields) return;
      message.error('保存失败: ' + (e?.message || ''));
    } finally {
      setLoading(false);
    }
  };

  const handleDelete = async (id: number) => {
    try {
      const res = await explorerApi.deleteAiVendor(id);
      if ((res.data as any).code === 0) {
        message.success('已删除');
        loadList();
      } else {
        message.error((res.data as any).message || '删除失败');
      }
    } catch (e: any) {
      message.error('删除失败: ' + (e?.message || ''));
    }
  };

  const columns = [
    { title: 'ID', dataIndex: 'id', width: 80 },
    { title: 'Vendor', dataIndex: 'vendor', width: 110, render: (v: string) => <code>{v}</code> },
    { title: 'Display Name', dataIndex: 'displayName', width: 130 },
    {
      title: 'Base URL', dataIndex: 'baseUrl', ellipsis: true,
      render: (v?: string) => v ? <span title={v}>{v}</span> : <span style={{ color: '#bbb' }}>默认</span>,
    },
    {
      title: 'API Version', dataIndex: 'apiVersion', width: 120,
      render: (v?: string) => v || '—',
    },
    {
      title: 'Token', dataIndex: 'token', width: 90,
      render: (v?: string | null) =>
        v ? <span style={{ color: '#52c41a' }}>已配置</span> : <span style={{ color: '#999' }}>未配置</span>,
    },
    {
      title: '状态', dataIndex: 'status', width: 70,
      render: (s: number) => <Tag color={s === 1 ? 'green' : 'red'}>{s === 1 ? '启用' : '禁用'}</Tag>,
    },
    { title: '备注', dataIndex: 'description', ellipsis: true },
    {
      title: '操作', width: 140,
      render: (_: any, row: VendorRow) => (
        <Space>
          <Button size="small" icon={<EditOutlined />} onClick={() => openEdit(row)}>编辑</Button>
          <Popconfirm title="确认删除该供应商？" onConfirm={() => handleDelete(row.id)}
            okText="删除" cancelText="取消" okButtonProps={{ danger: true }}>
            <Button size="small" danger icon={<DeleteOutlined />}>删除</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <>
      <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: 12 }}>
        <Space>
          <Button icon={<ReloadOutlined />} onClick={loadList} loading={loading}>刷新</Button>
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>新增供应商</Button>
        </Space>
      </div>
      <Table<VendorRow>
        rowKey="id"
        dataSource={list}
        columns={columns}
        loading={loading}
        size="middle"
        pagination={{ pageSize: 10, showSizeChanger: false }}
        scroll={{ x: 1000 }}
      />
      <Modal
        title={editing ? '编辑供应商' : '新增供应商'}
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={handleSubmit}
        confirmLoading={loading}
        width={640}
        okText="保存"
        cancelText="取消"
        destroyOnHidden
      >
        <Form form={form} layout="vertical" preserve={false}>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="vendor" label="Vendor" rules={[{ required: true, message: '请选择 vendor' }]}>
                <Select options={VENDOR_TYPE_OPTIONS} disabled={!!editing} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="displayName" label="Display Name">
                <Input placeholder="前端展示名，可与 vendor 同名" />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="status" label="状态" rules={[{ required: true }]}>
                <Select options={[{ value: 1, label: '启用' }, { value: 0, label: '禁用' }]} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="apiVersion" label="API Version（Azure 专用）">
                <Input placeholder="如 2024-02-15" />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="baseUrl" label="Base URL（留空走 Spring AI 默认）">
            <Input placeholder="如 https://api.openai.com" />
          </Form.Item>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="proxyHost" label="代理 Host">
                <Input placeholder="如 127.0.0.1" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="proxyPort" label="代理 Port">
                <InputNumber min={1} max={65535} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="timeoutSeconds" label="超时（秒）">
                <InputNumber min={5} max={300} defaultValue={30} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="maxRetries" label="最大重试次数">
                <InputNumber min={0} max={10} defaultValue={3} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item
            name="token"
            label={editing ? 'API Key（留空=不更新）' : 'API Key'}
          >
            <Input.Password autoComplete="new-password"
              placeholder={editing ? '已配置 ●●●●●●，留空保持' : '选填'} />
          </Form.Item>
          <Form.Item name="description" label="备注">
            <Input.TextArea rows={2} maxLength={500} showCount />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
};

// ============================================================================
// Model Instance CRUD
// ============================================================================

const ModelInstancePanel: React.FC = () => {
  const [form] = Form.useForm<ModelInstanceFormValues>();
  const [vendors, setVendors] = useState<VendorRow[]>([]);
  const [list, setList] = useState<ModelInstanceRow[]>([]);
  const [loading, setLoading] = useState(false);
  const [editing, setEditing] = useState<ModelInstanceRow | null>(null);
  const [modalOpen, setModalOpen] = useState(false);
  const [filterCapability, setFilterCapability] = useState<string>('');
  const [filterVendorId, setFilterVendorId] = useState<number | undefined>();

  const loadAll = useCallback(async () => {
    setLoading(true);
    try {
      const [vendorRes, instanceRes] = await Promise.all([
        explorerApi.listAiVendors(),
        explorerApi.listAiModelInstances(),
      ]);
      const vp = (vendorRes.data as any);
      const ip = (instanceRes.data as any);
      if (vp.code === 0) setVendors(vp.data || []);
      if (ip.code === 0) setList(ip.data || []);
    } catch (e: any) {
      message.error('加载失败: ' + (e.message || ''));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { loadAll(); }, [loadAll]);

  const openCreate = () => { setEditing(null); setModalOpen(true); };
  const openEdit = (row: ModelInstanceRow) => { setEditing(row); setModalOpen(true); };

  useEffect(() => {
    if (!modalOpen) return;
    if (editing) {
      form.setFieldsValue({ ...editing });
    } else {
      form.resetFields();
      form.setFieldsValue({
        vendorId: filterVendorId || (vendors[0]?.id),
        capability: 'CHAT',
        tier: 'PRODUCTION',
        priority: 1,
        isActive: 1,
        isCurrent: 0,
      });
    }
  }, [modalOpen, editing, form, vendors, filterVendorId]);

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      setLoading(true);
      const res = editing
        ? await explorerApi.updateAiModelInstance(editing.id, values)
        : await explorerApi.createAiModelInstance(values);
      if ((res.data as any).code === 0) {
        message.success(editing ? '已更新' : '已新建');
        setModalOpen(false);
        loadAll();
      } else {
        message.error((res.data as any).message || '保存失败');
      }
    } catch (e: any) {
      if (e?.errorFields) return;
      message.error('保存失败: ' + (e?.message || ''));
    } finally {
      setLoading(false);
    }
  };

  const handleDelete = async (id: number) => {
    try {
      const res = await explorerApi.deleteAiModelInstance(id);
      if ((res.data as any).code === 0) {
        message.success('已删除');
        loadAll();
      } else {
        message.error((res.data as any).message || '删除失败');
      }
    } catch (e: any) {
      message.error('删除失败: ' + (e?.message || ''));
    }
  };

  const filtered = list.filter(r =>
    (!filterCapability || r.capability === filterCapability) &&
    (!filterVendorId || r.vendorId === filterVendorId)
  );

  const columns = [
    { title: 'ID', dataIndex: 'id', width: 80 },
    { title: 'Vendor', dataIndex: 'vendor', width: 110, render: (v?: string) => <code>{v || '—'}</code> },
    { title: 'Model Name', dataIndex: 'modelName', width: 160, ellipsis: true },
    { title: 'Deployment', dataIndex: 'deploymentName', width: 140, ellipsis: true,
      render: (v?: string) => v || <span style={{ color: '#bbb' }}>—</span> },
    {
      title: 'Capability', dataIndex: 'capability', width: 110,
      render: (v: string) => <Tag color={CAPABILITY_COLOR[v] || 'default'}>{v}</Tag>,
    },
    {
      title: 'Tier', dataIndex: 'tier', width: 110,
      render: (v: string) => <Tag color={TIER_COLOR[v] || 'default'}>{v}</Tag>,
    },
    { title: 'Pri', dataIndex: 'priority', width: 50 },
    { title: 'MaxTokens', dataIndex: 'maxTokens', width: 90 },
    { title: 'Temp', dataIndex: 'temperature', width: 65, render: (v?: number) => v ?? '—' },
    { title: 'TopP', dataIndex: 'topP', width: 65, render: (v?: number) => v ?? '—' },
    {
      title: '激活', dataIndex: 'isActive', width: 65,
      render: (s: number) => <Tag color={s === 1 ? 'green' : 'red'}>{s === 1 ? '✓' : '✗'}</Tag>,
    },
    {
      title: '当前', dataIndex: 'isCurrent', width: 65,
      render: (s: number) => <Tag color={s === 1 ? 'blue' : 'default'}>{s === 1 ? '★' : '—'}</Tag>,
    },
    { title: '备注', dataIndex: 'description', ellipsis: true },
    {
      title: '操作', width: 140,
      render: (_: any, row: ModelInstanceRow) => (
        <Space>
          <Button size="small" icon={<EditOutlined />} onClick={() => openEdit(row)}>编辑</Button>
          <Popconfirm title="确认删除该模型实例？" onConfirm={() => handleDelete(row.id)}
            okText="删除" cancelText="取消" okButtonProps={{ danger: true }}>
            <Button size="small" danger icon={<DeleteOutlined />}>删除</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <>
      <div style={{ display: 'flex', gap: 12, marginBottom: 12, alignItems: 'center', flexWrap: 'wrap' }}>
        <span>供应商:</span>
        <Select allowClear placeholder="全部" style={{ width: 150 }}
          onChange={v => setFilterVendorId(v)}>
          {vendors.map(v => <Select.Option key={v.id} value={v.id}>{v.vendor}</Select.Option>)}
        </Select>
        <span>能力:</span>
        <Select allowClear placeholder="全部" style={{ width: 130 }}
          onChange={v => setFilterCapability(v)}>
          {CAPABILITY_OPTIONS.map(o => <Select.Option key={o.value} value={o.value}>{o.label}</Select.Option>)}
        </Select>
        <Space style={{ marginLeft: 'auto' }}>
          <Button icon={<ReloadOutlined />} onClick={loadAll} loading={loading}>刷新</Button>
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>新增模型实例</Button>
        </Space>
      </div>
      <Table<ModelInstanceRow>
        rowKey="id"
        dataSource={filtered}
        columns={columns}
        loading={loading}
        size="middle"
        pagination={{ pageSize: 10, showSizeChanger: false }}
        scroll={{ x: 1300 }}
      />
      <Modal
        title={editing ? '编辑模型实例' : '新增模型实例'}
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={handleSubmit}
        confirmLoading={loading}
        width={640}
        okText="保存"
        cancelText="取消"
        destroyOnHidden
      >
        <Form form={form} layout="vertical" preserve={false}>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="vendorId" label="供应商" rules={[{ required: true, message: '请选择供应商' }]}>
                <Select placeholder="选择供应商">
                  {vendors.map(v => <Select.Option key={v.id} value={v.id}>{v.vendor}</Select.Option>)}
                </Select>
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="modelName" label="Model Name" rules={[{ required: true, message: '请填写模型名' }]}>
                <Input placeholder="如 gpt-4o, claude-3.5-sonnet" />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="deploymentName" label="Deployment Name（Azure 专用）">
            <Input placeholder="如 gpt-4o-prod" />
          </Form.Item>
          <Row gutter={16}>
            <Col span={8}>
              <Form.Item name="capability" label="Capability" rules={[{ required: true }]}>
                <Select options={CAPABILITY_OPTIONS} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="tier" label="Tier" rules={[{ required: true }]}>
                <Select options={TIER_OPTIONS} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="priority" label="Priority（越小越优先）">
                <InputNumber min={1} max={99} defaultValue={1} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={8}>
              <Form.Item name="maxTokens" label="Max Tokens">
                <InputNumber min={1} max={100000} placeholder="如 8192" style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="temperature" label="Temperature">
                <InputNumber min={0} max={2} step={0.05} placeholder="如 0.7" style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="topP" label="Top P">
                <InputNumber min={0} max={1} step={0.05} placeholder="如 1.0" style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={8}>
              <Form.Item name="isActive" label="激活" initialValue={1}>
                <Select options={[{ value: 1, label: '启用' }, { value: 0, label: '禁用' }]} />
              </Form.Item>
            </Col>
            <Col span={16}>
              <Form.Item name="isCurrent" label="设为当前（路由时优先）" tooltip="设为当前后，同 tier 下其他实例的 isCurrent 自动降为 0" initialValue={0}>
                <Select options={[{ value: 1, label: '是（路由优先）' }, { value: 0, label: '否' }]} />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="description" label="备注">
            <Input.TextArea rows={2} maxLength={500} showCount />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
};

// ============================================================================
// Root Component
// ============================================================================

const AiConfigPage: React.FC = () => {
  const tabItems = [
    {
      key: 'vendors',
      label: '供应商凭证',
      children: <VendorPanel />,
    },
    {
      key: 'instances',
      label: '模型实例',
      children: <ModelInstancePanel />,
    },
  ];

  return (
    <Card
      title="AI 配置管理"
      extra={<Space><Tag color="blue">insight_ai_vendor</Tag><Tag color="purple">insight_model_instance</Tag></Space>}
    >
      <Alert
        message="表结构说明"
        description={
          <ul style={{ margin: 0, paddingLeft: 16 }}>
            <li><strong>供应商凭证</strong>：管理 base_url / token / proxy / timeout 等凭证信息，一个 vendor 一条记录</li>
            <li><strong>模型实例</strong>：管理模型名 / capability / tier / 调用参数（temperature / maxTokens 等），按 <code>isCurrent=1</code> 路由</li>
            <li>Token / API Key 以 <strong>AES-256-GCM</strong> 加密后入库，列表返回时脱敏为 <code>******</code></li>
            <li>一个 Key 可用多个模型：同 vendor 下添加多条 model instance 记录即可</li>
          </ul>
        }
        type="info"
        style={{ marginBottom: 16 }}
      />
      <Tabs items={tabItems} defaultActiveKey="vendors" />
    </Card>
  );
};

export default AiConfigPage;