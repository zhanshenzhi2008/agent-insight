import React, { useState, useEffect } from 'react';
import {
  Card, Form, Input, InputNumber, Select, Switch, Button, Space, message, Alert, Row, Col, Table, Modal, Popconfirm,
} from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined, ReloadOutlined } from '@ant-design/icons';
import { explorerApi } from '../../../services/explorerApi';

const VENDOR_OPTIONS = [
  { value: 'openai', label: 'openai' },
  { value: 'ollama', label: 'ollama' },
  { value: 'deepseek', label: 'deepseek' },
  { value: 'anthropic', label: 'anthropic' },
  { value: 'google-genai', label: 'google-genai' },
];

interface AiModelRow {
  id: number;
  vendor: string;
  models: string;
  baseUrl?: string;
  status: 0 | 1;
  description?: string;
  temperature?: number;
  token?: string | null;
  createTime: string;
  updateTime: string;
}

interface AiModelFormValues {
  vendor: string;
  models: string;
  baseUrl?: string;
  status: 0 | 1;
  description?: string;
  temperature?: number;
  token?: string;
}

const AiConfigPage: React.FC = () => {
  const [form] = Form.useForm<AiModelFormValues>();
  const [list, setList] = useState<AiModelRow[]>([]);
  const [loading, setLoading] = useState(false);
  const [editing, setEditing] = useState<AiModelRow | null>(null);
  const [modalOpen, setModalOpen] = useState(false);

  useEffect(() => {
    loadList();
  }, []);

  const loadList = async () => {
    setLoading(true);
    try {
      const res = await explorerApi.listAiModels();
      const payload = res.data as { code: number; data: AiModelRow[] };
      if (payload.code === 0) {
        setList(payload.data || []);
      } else {
        message.error('加载 AI 模型配置失败');
      }
    } catch (e: any) {
      message.error('加载 AI 模型配置失败: ' + (e.message || ''));
    } finally {
      setLoading(false);
    }
  };

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({
      vendor: 'openai',
      models: 'gpt-4o',
      status: 1,
      temperature: 0.3,
    });
    setModalOpen(true);
  };

  const openEdit = (row: AiModelRow) => {
    setEditing(row);
    form.setFieldsValue({
      vendor: row.vendor,
      models: row.models,
      baseUrl: row.baseUrl,
      status: row.status,
      description: row.description,
      temperature: row.temperature,
      token: '', // 编辑时不回显密钥，避免误改；留空=不更新
    });
    setModalOpen(true);
  };

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      // token 留空时不要把空串发给后端（语义上 = 不更新）
      const payload: any = { ...values };
      if (editing && (!values.token || values.token === '')) {
        delete payload.token;
      }
      setLoading(true);
      if (editing) {
        const res = await explorerApi.updateAiModel(editing.id, payload);
        if ((res.data as any).code === 0) {
          message.success('已更新');
          setModalOpen(false);
          loadList();
        } else {
          message.error('更新失败');
        }
      } else {
        const res = await explorerApi.createAiModel(payload);
        if ((res.data as any).code === 0) {
          message.success('已新建');
          setModalOpen(false);
          loadList();
        } else {
          message.error('新建失败');
        }
      }
    } catch (e: any) {
      if (e?.errorFields) return; // 表单校验失败
      message.error('保存失败: ' + (e?.message || ''));
    } finally {
      setLoading(false);
    }
  };

  const handleDelete = async (id: number) => {
    try {
      const res = await explorerApi.deleteAiModel(id);
      if ((res.data as any).code === 0) {
        message.success('已删除');
        loadList();
      } else {
        message.error('删除失败');
      }
    } catch (e: any) {
      message.error('删除失败: ' + (e?.message || ''));
    }
  };

  const columns = [
    { title: 'ID', dataIndex: 'id', width: 80 },
    {
      title: 'Vendor',
      dataIndex: 'vendor',
      width: 110,
      render: (v: string) => <code>{v}</code>,
    },
    {
      title: 'Models',
      dataIndex: 'models',
      ellipsis: true,
      render: (v: string) => <span title={v}>{v}</span>,
    },
    {
      title: 'Base URL',
      dataIndex: 'baseUrl',
      width: 220,
      ellipsis: true,
      render: (v?: string) => v || <span style={{ color: '#bbb' }}>（默认）</span>,
    },
    {
      title: 'Temperature',
      dataIndex: 'temperature',
      width: 100,
      render: (v?: number) => (v == null ? '—' : v),
    },
    {
      title: 'Token',
      dataIndex: 'token',
      width: 100,
      render: (v?: string | null) =>
        v ? <span style={{ color: '#52c41a' }}>已配置</span> : <span style={{ color: '#999' }}>未配置</span>,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (s: number) => (s === 1 ? '启用' : '禁用'),
    },
    {
      title: '备注',
      dataIndex: 'description',
      ellipsis: true,
      render: (v?: string) => v || '—',
    },
    {
      title: '操作',
      width: 140,
      fixed: 'right' as const,
      render: (_: any, row: AiModelRow) => (
        <Space>
          <Button size="small" icon={<EditOutlined />} onClick={() => openEdit(row)}>
            编辑
          </Button>
          <Popconfirm
            title="确认删除该供应商？"
            onConfirm={() => handleDelete(row.id)}
            okText="删除"
            cancelText="取消"
            okButtonProps={{ danger: true }}
          >
            <Button size="small" danger icon={<DeleteOutlined />}>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <Card
      title="AI 配置"
      extra={
        <Space>
          <Button icon={<ReloadOutlined />} onClick={loadList} loading={loading}>
            刷新
          </Button>
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            新增供应商
          </Button>
        </Space>
      }
    >
      <Alert
        message="配置说明"
        description={
          <ul style={{ margin: 0, paddingLeft: 16 }}>
            <li>此页管理 agent-insight 自身业务表 <code>insight_ai_model</code>（最多 8 行）</li>
            <li>Token / API Key 在服务端以 <strong>AES-256-GCM</strong> 加密后入库；列表返回时脱敏为 <code>******</code></li>
            <li>编辑时 Token 字段留空 = <strong>不更新</strong>；要清除密钥请填写空字符串</li>
            <li>Spring AI 启动时仍以 <code>application.yml</code> 中的 <code>spring.ai.*</code> / <code>agent-insight.ai.*</code> 为主；
                此表用于运行时切换 & 多模型并存场景</li>
          </ul>
        }
        type="info"
        style={{ marginBottom: 16 }}
      />

      <Row gutter={16}>
        <Col span={24}>
          <Table<AiModelRow>
            rowKey="id"
            dataSource={list}
            columns={columns}
            loading={loading}
            size="middle"
            pagination={{ pageSize: 10, showSizeChanger: false }}
            scroll={{ x: 1100 }}
          />
        </Col>
      </Row>

      <Modal
        title={editing ? '编辑 AI 供应商' : '新增 AI 供应商'}
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={handleSubmit}
        confirmLoading={loading}
        width={640}
        okText="保存"
        cancelText="取消"
        destroyOnClose
      >
        <Form form={form} layout="vertical" preserve={false}>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                name="vendor"
                label="Vendor"
                rules={[{ required: true, message: '请选择 vendor' }]}
              >
                <Select
                  options={VENDOR_OPTIONS}
                  disabled={!!editing}
                />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                name="status"
                label="状态"
                rules={[{ required: true, message: '请选择状态' }]}
              >
                <Select
                  options={[
                    { value: 1, label: '启用' },
                    { value: 0, label: '禁用' },
                  ]}
                />
              </Form.Item>
            </Col>
          </Row>

          <Form.Item
            name="models"
            label="Models（多个用逗号分隔）"
            rules={[{ required: true, message: '请填写模型名' }]}
          >
            <Input placeholder="如 gpt-4o, deepseek-chat, qwen2.5" />
          </Form.Item>

          <Form.Item name="baseUrl" label="Base URL（可选，留空走 Spring AI 默认）">
            <Input placeholder="如 https://api.openai.com  /  http://localhost:11434" />
          </Form.Item>

          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="temperature" label="Temperature (0~2)">
                <InputNumber min={0} max={2} step={0.1} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                name="token"
                label={editing ? 'API Key / Token（留空=不更新）' : 'API Key / Token（可选）'}
              >
                <Input.Password
                  autoComplete="new-password"
                  placeholder={editing ? '已配置 ●●●●●●，留空保持' : '选填，可后续在编辑中补充'}
                />
              </Form.Item>
            </Col>
          </Row>

          <Form.Item name="description" label="备注">
            <Input.TextArea rows={2} maxLength={500} showCount />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
};

export default AiConfigPage;