import React, { useState, useEffect } from 'react';
import {
  App, Card, Table, Button, Space, Tag, Modal, Form, Input,
  Select, Popconfirm, Typography, Divider, Alert
} from 'antd';
import { PlusOutlined, DeleteOutlined, EditOutlined, ApiOutlined, CheckCircleOutlined, ThunderboltOutlined, CloseCircleOutlined } from '@ant-design/icons';
import { explorerApi } from '../../../services/explorerApi';

const { Text } = Typography;

interface Datasource {
  id?: string;
  datasourceKey: string;
  datasourceName: string;
  datasourceType: 'MYSQL' | 'POSTGRESQL' | 'MONGODB';
  status: string;
  connectionConfig: {
    host: string;
    port: number;
    database: string;
    username: string;
    password?: string;
    connectionPoolSize?: number;
    extraParams?: Record<string, string>;
  };
  description?: string;
  tags?: string[];
}

const DATASOURCE_TYPE_OPTIONS = [
  { value: 'MYSQL', label: 'MySQL' },
  { value: 'POSTGRESQL', label: 'PostgreSQL' },
  { value: 'MONGODB', label: 'MongoDB' },
];

const DatasourcePage: React.FC = () => {
  const [data, setData] = useState<Datasource[]>([]);
  const [loading, setLoading] = useState(false);
  const [modal, setModal] = useState<{ open: boolean; editing?: Datasource }>({ open: false });
  const [testing, setTesting] = useState(false);
  const [testResult, setTestResult] = useState<{ connected: boolean; message: string; responseTimeMs?: number } | null>(null);
  const [form] = Form.useForm();
  const { message } = App.useApp();

  const fetchData = async () => {
    setLoading(true);
    try {
      const res = await explorerApi.listDatasources();
      if (res.data.code === 0) setData(res.data.data);
    } catch (e: any) {
      message.error('加载失败: ' + (e.message || ''));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { fetchData(); }, []);

  const openCreate = () => {
    form.resetFields();
    setTestResult(null);
    setModal({ open: true });
  };

  const openEdit = (record: Datasource) => {
    form.setFieldsValue({
      ...record,
      password: '',
    });
    setTestResult(null);
    setModal({ open: true, editing: record });
  };

  const handleTestSaved = async (record: Datasource) => {
    if (!record.id) return;
    try {
      const res = await explorerApi.testDatasourceConnection(record.id);
      const payload: any = res.data;
      if (payload.code === 0) {
        const data = payload.data;
        if (data.connected) {
          message.success(`连接成功 (${data.responseTimeMs}ms)`);
        } else {
          message.error(`连接失败：${data.error || '未知错误'}`);
        }
      } else {
        message.error(payload.message || '测试失败');
      }
    } catch (e: any) {
      message.error(e.response?.data?.message || e.message || '测试失败');
    }
  };

  const handleTestConnection = async () => {
    let values: any;
    try {
      values = await form.validateFields();
    } catch {
      message.warning('请先填写必填项');
      return;
    }
    // 编辑模式下，密码留空时回填原密码，保证测试通过
    if (modal.editing && (!values.connectionConfig?.password)) {
      values.connectionConfig = {
        ...(values.connectionConfig || {}),
        password: modal.editing.connectionConfig?.password,
      };
    }
    setTesting(true);
    setTestResult(null);
    try {
      const res = await explorerApi.testDatasourceByConfig(values);
      const payload: any = res.data;
      if (payload.code === 0) {
        const data = payload.data;
        setTestResult({
          connected: !!data.connected,
          message: data.connected
            ? `连接成功（${data.responseTimeMs}ms）`
            : `连接失败：${data.error || '未知错误'}`,
          responseTimeMs: data.responseTimeMs,
        });
        if (data.connected) message.success(`连接成功 (${data.responseTimeMs}ms)`);
        else message.error('连接失败');
      } else {
        setTestResult({ connected: false, message: payload.message || '请求失败' });
        message.error(payload.message || '测试失败');
      }
    } catch (e: any) {
      const errMsg = e.response?.data?.message || e.message || '测试失败';
      setTestResult({ connected: false, message: errMsg });
      message.error(errMsg);
    } finally {
      setTesting(false);
    }
  };

  const handleSubmit = async () => {
    const values = await form.validateFields();
    try {
      if (modal.editing?.id) {
        await explorerApi.updateDatasource(modal.editing.id, values);
        message.success('更新成功');
      } else {
        await explorerApi.createDatasource(values);
        message.success('创建成功');
      }
      setModal({ open: false });
      fetchData();
    } catch (e: any) {
      message.error(e.response?.data?.message || '操作失败');
    }
  };

  const handleDelete = async (id: string) => {
    try {
      await explorerApi.deleteDatasource(id);
      message.success('删除成功');
      fetchData();
    } catch (e: any) {
      message.error('删除失败: ' + (e.response?.data?.message || ''));
    }
  };

  const columns = [
    { title: 'Key', dataIndex: 'datasourceKey', key: 'datasourceKey', render: (v: string) => <code>{v}</code> },
    { title: '名称', dataIndex: 'datasourceName', key: 'datasourceName' },
    {
      title: '类型',
      dataIndex: 'datasourceType',
      key: 'datasourceType',
      render: (v: string) => {
        const map: Record<string, string> = { MYSQL: 'MySQL', POSTGRESQL: 'PostgreSQL', MONGODB: 'MongoDB' };
        return <Tag color="blue">{map[v] || v}</Tag>;
      },
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      render: (v: string) => (
        <Tag color={v === 'ACTIVE' ? 'green' : 'red'} icon={v === 'ACTIVE' ? <CheckCircleOutlined /> : undefined}>
          {v}
        </Tag>
      ),
    },
    {
      title: '连接',
      key: 'conn',
      render: (_: any, r: Datasource) => (
        <Text type="secondary" style={{ fontSize: 12 }}>
          {r.connectionConfig?.host}:{r.connectionConfig?.port}/{r.connectionConfig?.database}
        </Text>
      ),
    },
{
        title: '操作',
        key: 'action',
        render: (_: any, r: Datasource) => (
          <Space>
            <Button type="link" size="small" icon={<ThunderboltOutlined />} onClick={() => handleTestSaved(r)}>测试</Button>
            <Button type="link" size="small" icon={<EditOutlined />} onClick={() => openEdit(r)} />
            <Popconfirm title="确认删除？会级联删除表/列配置" onConfirm={() => handleDelete(r.id!)}>
              <Button type="link" size="small" danger icon={<DeleteOutlined />} />
            </Popconfirm>
          </Space>
        ),
      },
  ];

  return (
    <div>
      <Card
        title={<><ApiOutlined /> 数据源配置</>}
        extra={<Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>新建数据源</Button>}
      >
        <Table columns={columns} dataSource={data} rowKey="id" loading={loading} size="small" />
      </Card>

      <Modal
        title={modal.editing ? '编辑数据源' : '新建数据源'}
        open={modal.open}
        onCancel={() => setModal({ open: false })}
        onOk={handleSubmit}
        width={600}
        okText="保存"
        cancelText="取消"
        footer={[
          <Button
            key="test"
            icon={<ThunderboltOutlined />}
            loading={testing}
            onClick={handleTestConnection}
          >
            测试连接
          </Button>,
          <Button key="cancel" onClick={() => setModal({ open: false })}>
            取消
          </Button>,
          <Button key="ok" type="primary" loading={loading} onClick={handleSubmit}>
            保存
          </Button>,
        ]}
      >
        <Form form={form} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item name="datasourceKey" label="数据源 Key" rules={[{ required: true }]}>
            <Input placeholder="如: prod_mysql_llm" disabled={!!modal.editing} />
          </Form.Item>
          <Form.Item name="datasourceName" label="名称" rules={[{ required: true }]}>
            <Input placeholder="如: 生产环境 LLM Agent 数据库" />
          </Form.Item>
          <Form.Item name="datasourceType" label="类型" rules={[{ required: true }]}>
            <Select options={DATASOURCE_TYPE_OPTIONS} />
          </Form.Item>
          <Divider>连接配置</Divider>
          <Space style={{ display: 'flex' }} size="middle">
            <Form.Item name={['connectionConfig', 'host']} label="Host" rules={[{ required: true }]} style={{ flex: 1 }}>
              <Input placeholder="172.18.2.10" />
            </Form.Item>
            <Form.Item name={['connectionConfig', 'port']} label="Port" rules={[{ required: true }]} style={{ width: 100 }}>
              <Input type="number" placeholder="3306" />
            </Form.Item>
          </Space>
          <Form.Item name={['connectionConfig', 'database']} label="数据库" rules={[{ required: true }]}>
            <Input placeholder="llm_agent" />
          </Form.Item>
          <Space style={{ display: 'flex' }} size="middle">
<Form.Item name={['connectionConfig', 'username']} label="用户名" style={{ flex: 1 }}>
            <Input placeholder="readonly（可留空）" />
          </Form.Item>
            <Form.Item name={['connectionConfig', 'password']} label="密码" style={{ flex: 1 }}>
              <Input.Password placeholder={modal.editing ? '不修改请留空' : '可留空（部分库允许匿名）'} />
            </Form.Item>
          </Space>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={2} placeholder="用途说明..." />
          </Form.Item>
        </Form>

        {testResult && (
          <Alert
            style={{ marginTop: 8 }}
            type={testResult.connected ? 'success' : 'error'}
            showIcon
            icon={testResult.connected ? <CheckCircleOutlined /> : <CloseCircleOutlined />}
            title={testResult.message}
            description={testResult.responseTimeMs != null ? `耗时 ${testResult.responseTimeMs}ms` : undefined}
          />
        )}
      </Modal>
    </div>
  );
};

export default DatasourcePage;
