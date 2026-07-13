import React, { useState, useEffect } from 'react';
import {
  Card, Table, Select, Button, Space, Tag, Modal, Form,
  Input, Switch, Popconfirm, message, Typography
} from 'antd';
import {
  PlusOutlined, DeleteOutlined, EditOutlined,
  CheckCircleOutlined, StopOutlined
} from '@ant-design/icons';
import { explorerApi } from '../../../services/explorerApi';

const { Text } = Typography;

interface QueryTemplate {
  id?: string;
  datasourceKey: string;
  queryName: string;
  displayName: string;
  queryTemplate: string;
  queryType: 'SQL' | 'MONGODB_AGG';
  description?: string;
  enabled?: boolean;
  tags?: string[];
}

const QueryTemplatePage: React.FC = () => {
  const [datasources, setDatasources] = useState<any[]>([]);
  const [templates, setTemplates] = useState<QueryTemplate[]>([]);
  const [selectedDs, setSelectedDs] = useState<string>('');
  const [loading, setLoading] = useState(false);
  const [modal, setModal] = useState<{ open: boolean; editing?: QueryTemplate }>({ open: false });
  const [form] = Form.useForm();

  useEffect(() => {
    explorerApi.listDatasources().then((r: any) => {
      if (r.data.code === 0) setDatasources(r.data.data || []);
    });
  }, []);

  useEffect(() => {
    if (!selectedDs) return;
    setLoading(true);
    explorerApi.listQueryTemplates(selectedDs).then((r: any) => {
      if (r.data.code === 0) setTemplates(r.data.data || []);
    }).finally(() => setLoading(false));
  }, [selectedDs]);

  const openCreate = () => {
    form.resetFields();
    form.setFieldsValue({ datasourceKey: selectedDs, queryType: 'SQL', enabled: true });
    setModal({ open: true });
  };

  const openEdit = (record: QueryTemplate) => {
    form.setFieldsValue(record);
    setModal({ open: true, editing: record });
  };

  const handleSubmit = async () => {
    const values = await form.validateFields();
    try {
      if (modal.editing?.id) {
        await explorerApi.updateQueryTemplate(modal.editing.id, values);
        message.success('更新成功');
      } else {
        await explorerApi.createQueryTemplate({ ...values, datasourceKey: selectedDs });
        message.success('创建成功');
      }
      setModal({ open: false });
      const res = await explorerApi.listQueryTemplates(selectedDs);
      if (res.data.code === 0) setTemplates(res.data.data || []);
    } catch (e: any) {
      message.error('操作失败: ' + (e.response?.data?.message || ''));
    }
  };

  const toggleEnabled = async (record: QueryTemplate, enabled: boolean) => {
    try {
      await explorerApi.updateQueryTemplate(record.id!, { ...record, enabled });
      const res = await explorerApi.listQueryTemplates(selectedDs);
      if (res.data.code === 0) setTemplates(res.data.data || []);
      message.success(enabled ? '已启用' : '已禁用');
    } catch {
      message.error('操作失败');
    }
  };

  const handleDelete = async (id: string) => {
    try {
      await explorerApi.deleteQueryTemplate(id);
      const res = await explorerApi.listQueryTemplates(selectedDs);
      if (res.data.code === 0) setTemplates(res.data.data || []);
      message.success('删除成功');
    } catch {
      message.error('删除失败');
    }
  };

  const columns = [
    {
      title: '启用',
      key: 'enabled',
      width: 70,
      render: (_: any, r: QueryTemplate) => (
        <Switch
          size="small"
          checked={r.enabled}
          onChange={v => toggleEnabled(r, v)}
          checkedChildren={<CheckCircleOutlined />}
          unCheckedChildren={<StopOutlined />}
        />
      ),
    },
    { title: '名称', dataIndex: 'displayName', key: 'displayName', render: (v: string) => (
      <Text strong>{v}</Text>
    )},
    { title: 'Key', dataIndex: 'queryName', key: 'queryName', render: (v: string) => <Text code>{v}</Text> },
    {
      title: '类型',
      dataIndex: 'queryType',
      key: 'queryType',
      render: (v: string) => v === 'MONGODB_AGG'
        ? <Tag color="orange">MongoDB</Tag>
        : <Tag color="blue">SQL</Tag>,
    },
    { title: '描述', dataIndex: 'description', key: 'description', render: (v: string) => v || '-' },
    {
      title: '操作',
      key: 'action',
      width: 120,
      render: (_: any, r: QueryTemplate) => (
        <Space size={4}>
          <Button type="link" size="small" icon={<EditOutlined />} onClick={() => openEdit(r)} />
          <Popconfirm title="确认删除?" onConfirm={() => handleDelete(r.id!)}>
            <Button type="link" size="small" danger icon={<DeleteOutlined />} />
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div>
      <Card
        title="查询模板"
        extra={
          <Space>
            <Select
              placeholder="选择数据源"
              value={selectedDs || undefined}
              onChange={v => setSelectedDs(v)}
              style={{ width: 180 }}
              options={datasources.map(d => ({ value: d.datasourceKey, label: d.datasourceName }))}
            />
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={openCreate}
              disabled={!selectedDs}
            >
              新建模板
            </Button>
          </Space>
        }
      >
        <Table
          columns={columns}
          dataSource={templates}
          rowKey="id"
          loading={loading}
          size="small"
          pagination={false}
          expandable={{
            expandedRowRender: (r) => (
              <pre style={{ fontSize: 11, background: '#f5f5f5', padding: 8, borderRadius: 4 }}>
                {r.queryTemplate}
              </pre>
            ),
          }}
        />
      </Card>

      <Modal
        title={modal.editing ? '编辑查询模板' : '新建查询模板'}
        open={modal.open}
        onCancel={() => setModal({ open: false })}
        onOk={handleSubmit}
        width={680}
      >
        <Form form={form} layout="vertical" style={{ marginTop: 16 }}>
          <Space style={{ display: 'flex' }} size="middle">
            <Form.Item name="queryName" label="模板 Key" rules={[{ required: true }]} style={{ flex: 1 }}>
              <Input placeholder="如: active_requests" disabled={!!modal.editing} />
            </Form.Item>
            <Form.Item name="displayName" label="展示名" rules={[{ required: true }]} style={{ flex: 1 }}>
              <Input placeholder="如: 活跃请求统计" />
            </Form.Item>
          </Space>
          <Space style={{ display: 'flex' }} size="middle">
            <Form.Item name="queryType" label="查询类型" rules={[{ required: true }]}>
              <Select
                options={[
                  { value: 'SQL', label: 'SQL' },
                  { value: 'MONGODB_AGG', label: 'MongoDB 聚合' },
                ]}
              />
            </Form.Item>
            <Form.Item name="enabled" label="启用" valuePropName="checked">
              <Switch defaultChecked />
            </Form.Item>
          </Space>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={2} placeholder="用途说明..." />
          </Form.Item>
          <Form.Item
            name="queryTemplate"
            label="查询语句 / 聚合管道"
            rules={[{ required: true }]}
            extra={
              <Text type="secondary" style={{ fontSize: 11 }}>
                SQL: SELECT * FROM table WHERE status = :status | MongoDB: [&#123;&quot;$match&quot;: &#123;status: &quot;:status&quot;&#125;&#125;]
              </Text>
            }
          >
            <Input.TextArea
              rows={6}
              placeholder={
                "-- MySQL/PostgreSQL:\nSELECT * FROM orders WHERE status = :status LIMIT 100\n\n-- MongoDB:\n[{$match: {status: ':status'}}]"
              }
            />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default QueryTemplatePage;
