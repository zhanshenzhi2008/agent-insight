import React, { useState, useEffect } from 'react';
import {
  Card, Table, Select, Button, Space, Tag, Modal, Form,
  Input, Switch, InputNumber, message, Popconfirm, Typography, Divider
} from 'antd';
import {
  PlusOutlined, DeleteOutlined, EditOutlined, ReloadOutlined,
  CheckCircleOutlined, StopOutlined, TableOutlined
} from '@ant-design/icons';
import { explorerApi } from '../../../services/explorerApi';

const { Text } = Typography;

interface TableConfig {
  id?: string;
  datasourceKey: string;
  tableName: string;
  displayName: string;
  description?: string;
  enabled?: boolean;
  defaultSortField?: string;
  defaultSortDirection?: string;
  pageSize?: number;
  allowFreeQuery?: boolean;
}

const TableConfigPage: React.FC = () => {
  const [datasources, setDatasources] = useState<any[]>([]);
  const [tables, setTables] = useState<TableConfig[]>([]);
  const [selectedDs, setSelectedDs] = useState<string>('');
  const [loading, setLoading] = useState(false);
  const [modal, setModal] = useState<{ open: boolean; editing?: TableConfig }>({ open: false });
  const [form] = Form.useForm();
  const [discovering, setDiscovering] = useState(false);

  useEffect(() => {
    explorerApi.listDatasources().then((r: any) => {
      if (r.data.code === 0) setDatasources(r.data.data || []);
    });
  }, []);

  useEffect(() => {
    if (!selectedDs) return;
    setLoading(true);
    explorerApi.listTables(selectedDs).then((r: any) => {
      if (r.data.code === 0) setTables(r.data.data || []);
    }).finally(() => setLoading(false));
  }, [selectedDs]);

  const handleDiscover = async () => {
    if (!selectedDs) return;
    setDiscovering(true);
    try {
      const res = await explorerApi.discoverTables(selectedDs);
      if (res.data.code === 0) {
        const externalTables = res.data.data || [];
        const existingNames = tables.map(t => t.tableName);
        const newOnes = externalTables
          .filter((t: any) => !existingNames.includes(t.tableName))
          .map((t: any) => ({
            datasourceKey: selectedDs,
            tableName: t.tableName,
            displayName: t.tableName,
            description: t.remark || '',
            enabled: true,
            pageSize: 20,
            allowFreeQuery: false,
          }));
        if (newOnes.length === 0) {
          message.info('所有表已配置');
        } else {
          // 批量导入
          for (const t of newOnes) {
            await explorerApi.createTable(t);
          }
          message.success(`已导入 ${newOnes.length} 个表`);
          const res2 = await explorerApi.listTables(selectedDs);
          if (res2.data.code === 0) setTables(res2.data.data || []);
        }
      }
    } catch (e: any) {
      message.error('发现表失败: ' + (e.response?.data?.message || ''));
    } finally {
      setDiscovering(false);
    }
  };

  const openCreate = () => {
    form.resetFields();
    form.setFieldsValue({ datasourceKey: selectedDs, enabled: true, pageSize: 20, allowFreeQuery: false });
    setModal({ open: true });
  };

  const openEdit = (record: TableConfig) => {
    form.setFieldsValue(record);
    setModal({ open: true, editing: record });
  };

  const handleSubmit = async () => {
    const values = await form.validateFields();
    try {
      if (modal.editing?.id) {
        await explorerApi.updateTable(modal.editing.id, values);
        message.success('更新成功');
      } else {
        await explorerApi.createTable({ ...values, datasourceKey: selectedDs });
        message.success('创建成功');
      }
      setModal({ open: false });
      const res = await explorerApi.listTables(selectedDs);
      if (res.data.code === 0) setTables(res.data.data || []);
    } catch (e: any) {
      message.error('操作失败: ' + (e.response?.data?.message || ''));
    }
  };

  const toggleEnabled = async (record: TableConfig, enabled: boolean) => {
    try {
      await explorerApi.updateTable(record.id!, { ...record, enabled });
      const res = await explorerApi.listTables(selectedDs);
      if (res.data.code === 0) setTables(res.data.data || []);
      message.success(enabled ? '已启用' : '已禁用');
    } catch (e: any) {
      message.error('操作失败');
    }
  };

  const handleDelete = async (id: string) => {
    try {
      await explorerApi.deleteTable(id);
      const res = await explorerApi.listTables(selectedDs);
      if (res.data.code === 0) setTables(res.data.data || []);
      message.success('删除成功');
    } catch (e: any) {
      message.error('删除失败');
    }
  };

  const columns = [
    {
      title: '启用',
      key: 'enabled',
      width: 70,
      render: (_: any, r: TableConfig) => (
        <Switch
          size="small"
          checked={r.enabled}
          onChange={v => toggleEnabled(r, v)}
          checkedChildren={<CheckCircleOutlined />}
          unCheckedChildren={<StopOutlined />}
        />
      ),
    },
    { title: '表名', dataIndex: 'tableName', key: 'tableName', render: (v: string) => <Text code>{v}</Text> },
    { title: '展示名', dataIndex: 'displayName', key: 'displayName' },
    { title: '描述', dataIndex: 'description', key: 'description', render: (v: string) => v || '-' },
    {
      title: '分页',
      dataIndex: 'pageSize',
      key: 'pageSize',
      render: (v: number) => v || 20,
    },
    {
      title: '自由SQL',
      dataIndex: 'allowFreeQuery',
      key: 'allowFreeQuery',
      render: (v: boolean) => v ? <Tag color="orange">允许</Tag> : <Tag>禁止</Tag>,
    },
    {
      title: '操作',
      key: 'action',
      width: 120,
      render: (_: any, r: TableConfig) => (
        <Space size={4}>
          <Button type="link" size="small" icon={<EditOutlined />} onClick={() => openEdit(r)} />
          <Popconfirm title="删除此表配置?" onConfirm={() => handleDelete(r.id!)}>
            <Button type="link" size="small" danger icon={<DeleteOutlined />} />
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div>
      <Card
        title={<><TableOutlined /> 表配置</>}
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
              icon={<ReloadOutlined />}
              onClick={handleDiscover}
              loading={discovering}
              disabled={!selectedDs}
            >
              从数据库导入表
            </Button>
            <Button type="primary" icon={<PlusOutlined />} onClick={openCreate} disabled={!selectedDs}>
              手动添加表
            </Button>
          </Space>
        }
      >
        <Table
          columns={columns}
          dataSource={tables}
          rowKey="id"
          loading={loading}
          size="small"
          pagination={{ pageSize: 20, size: 'small' }}
        />
      </Card>

      <Modal
        title={modal.editing ? '编辑表配置' : '新增表配置'}
        open={modal.open}
        onCancel={() => setModal({ open: false })}
        onOk={handleSubmit}
        width={520}
      >
        <Form form={form} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item name="tableName" label="表名（数据库中实际名称）" rules={[{ required: true }]}>
            <Input placeholder="如: log_llm_agent_main" disabled={!!modal.editing} />
          </Form.Item>
          <Form.Item name="displayName" label="展示名称" rules={[{ required: true }]}>
            <Input placeholder="如: Agent 主表" />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={2} />
          </Form.Item>
          <Divider />
          <Space style={{ display: 'flex' }} size="middle">
            <Form.Item name="pageSize" label="默认分页大小" style={{ width: 140 }}>
              <InputNumber min={5} max={500} defaultValue={20} />
            </Form.Item>
            <Form.Item name="allowFreeQuery" label="允许自由SQL" valuePropName="checked">
              <Switch />
            </Form.Item>
            <Form.Item name="enabled" label="启用" valuePropName="checked">
              <Switch defaultChecked />
            </Form.Item>
          </Space>
          <Space style={{ display: 'flex' }} size="middle">
            <Form.Item name="defaultSortField" label="默认排序字段" style={{ flex: 1 }}>
              <Input placeholder="如: create_time" />
            </Form.Item>
            <Form.Item name="defaultSortDirection" label="排序方向" style={{ width: 120 }}>
              <Select
                options={[
                  { value: 'DESC', label: '降序' },
                  { value: 'ASC', label: '升序' },
                ]}
              />
            </Form.Item>
          </Space>
        </Form>
      </Modal>
    </div>
  );
};

export default TableConfigPage;
