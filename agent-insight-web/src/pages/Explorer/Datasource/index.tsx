import React, { useState, useEffect } from 'react';
import {
  Card, Table, Button, Space, Tag, Modal, Form, Input,
  Select, message, Popconfirm, Typography, Divider
} from 'antd';
import { PlusOutlined, DeleteOutlined, EditOutlined, ApiOutlined, CheckCircleOutlined } from '@ant-design/icons';
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
  const [form] = Form.useForm();

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
    setModal({ open: true });
  };

  const openEdit = (record: Datasource) => {
    form.setFieldsValue({
      ...record,
      password: '',
    });
    setModal({ open: true, editing: record });
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
            <Form.Item name={['connectionConfig', 'username']} label="用户名" rules={[{ required: true }]} style={{ flex: 1 }}>
              <Input placeholder="readonly" />
            </Form.Item>
            <Form.Item name={['connectionConfig', 'password']} label="密码" style={{ flex: 1 }}>
              <Input.Password placeholder={modal.editing ? '不修改请留空' : ''} />
            </Form.Item>
          </Space>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={2} placeholder="用途说明..." />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default DatasourcePage;
