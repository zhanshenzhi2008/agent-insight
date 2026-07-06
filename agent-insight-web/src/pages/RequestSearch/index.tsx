import React, { useState, useEffect } from 'react';
import { Table, Card, Input, Button, Select, DatePicker, Space, Tag, message, AutoComplete } from 'antd';
import { SearchOutlined, ReloadOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { requestApi } from '../../services/api';
import type { RequestSummary } from '../../types';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';

const { RangePicker } = DatePicker;

const statusMap: Record<number, { color: string; text: string }> = {
  1: { color: 'processing', text: '运行中' },
  2: { color: 'success', text: '成功' },
  3: { color: 'warning', text: '挂起' },
  5: { color: 'default', text: '结束' },
};

const RequestSearchPage: React.FC = () => {
  const navigate = useNavigate();
  const [loading, setLoading] = useState(false);
  const [data, setData] = useState<RequestSummary[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [form, setForm] = useState({
    requestId: '',
    agentName: '',
    status: undefined as string | undefined,
    startTime: undefined as string | undefined,
    endTime: undefined as string | undefined,
  });
  const [agentOptions, setAgentOptions] = useState<{ value: string; label: string }[]>([]);

  const fetchData = async () => {
    setLoading(true);
    try {
      const res = await requestApi.search({
        requestId: form.requestId || undefined,
        agentName: form.agentName || undefined,
        status: form.status || undefined,
        startTime: form.startTime,
        endTime: form.endTime,
        page: page - 1,
        size: pageSize,
      });
      if (res.data.code === 0) {
        setData(res.data.data.content);
        setTotal(res.data.data.totalElements);
      } else {
        message.error(res.data.message);
      }
    } catch (e: any) {
      message.error(e.message || '查询失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchData();
  }, [page, pageSize]);

  useEffect(() => {
    const fetchAgents = async () => {
      if (!form.requestId) return;
      try {
        const res = await requestApi.instances(form.requestId);
        if (res.data.code === 0) {
          const names = [...new Set((res.data.data || []).map((a: any) => a.agentName).filter(Boolean))];
          setAgentOptions(names.map((n) => ({ value: n, label: n })));
        }
      } catch {
        // ignore
      }
    };
    fetchAgents();
  }, [form.requestId]);

  const columns: ColumnsType<RequestSummary> = [
    {
      title: 'Request ID',
      dataIndex: 'requestId',
      key: 'requestId',
      width: 200,
      ellipsis: true,
      render: (v) => <code style={{ fontSize: 12 }}>{v}</code>,
    },
    {
      title: 'Agent',
      dataIndex: 'topAgentName',
      key: 'topAgentName',
      width: 150,
    },
    {
      title: '状态',
      dataIndex: 'taskStatus',
      key: 'taskStatus',
      width: 100,
      render: (v) => {
        const s = statusMap[v] || { color: 'default', text: String(v) };
        return <Tag color={s.color}>{s.text}</Tag>;
      },
    },
    {
      title: '结果',
      dataIndex: 'success',
      key: 'success',
      width: 80,
      render: (v) => v
        ? <Tag color="success">成功</Tag>
        : <Tag color="error">失败</Tag>,
    },
    {
      title: '任务数',
      children: [
        { title: '总计', dataIndex: 'totalTaskCount', key: 'total', width: 70 },
        {
          title: '失败',
          dataIndex: 'failedTaskCount',
          key: 'failed',
          width: 70,
          render: (v) => v > 0 ? <span style={{ color: '#fa541c' }}>{v}</span> : v,
        },
      ],
    },
    {
      title: '耗时',
      dataIndex: 'totalDuration',
      key: 'totalDuration',
      width: 100,
      render: (v) => v ? `${(v / 1000).toFixed(2)}s` : '-',
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      key: 'createTime',
      width: 170,
      render: (v) => v ? dayjs(v).format('YYYY-MM-DD HH:mm:ss') : '-',
    },
    {
      title: '子 Agent',
      dataIndex: 'subAgentNames',
      key: 'subAgentNames',
      width: 150,
      ellipsis: true,
      render: (v) => v?.map((a: string) => <Tag key={a}>{a}</Tag>),
    },
    {
      title: '操作',
      key: 'action',
      width: 280,
      fixed: 'right',
      render: (_, record) => (
        <Space size="small">
          <Button type="link" size="small" onClick={() => navigate(`/overview/${record.requestId}`)}>概览</Button>
          <Button type="link" size="small" onClick={() => navigate(`/trace/${record.requestId}`)}>轨迹</Button>
          <Button type="link" size="small" onClick={() => navigate(`/log/${record.requestId}`)}>日志</Button>
          <Button type="link" size="small" onClick={() => navigate(`/llm/${record.requestId}`)}>LLM</Button>
        </Space>
      ),
    },
  ];

  return (
    <Card
      title="请求检索"
      extra={<Button icon={<ReloadOutlined />} onClick={fetchData}>刷新</Button>}
    >
      <Space wrap style={{ marginBottom: 16 }}>
        <Input
          placeholder="Request ID"
          value={form.requestId}
          onChange={(e) => setForm({ ...form, requestId: e.target.value })}
          style={{ width: 200 }}
          allowClear
        />
        <AutoComplete
          placeholder="Agent 名称"
          value={form.agentName}
          options={agentOptions}
          onSearch={(v) => setForm({ ...form, agentName: v })}
          onSelect={(v) => setForm({ ...form, agentName: v })}
          onChange={(v) => setForm({ ...form, agentName: typeof v === 'string' ? v : v })}
          style={{ width: 180 }}
          allowClear
          filterOption={(input, option) =>
            (option?.label ?? '').toLowerCase().includes(input.toLowerCase())
          }
        />
        <Select
          placeholder="状态"
          value={form.status}
          onChange={(v) => setForm({ ...form, status: v })}
          style={{ width: 120 }}
          allowClear
          options={[
            { value: 'success', label: '成功' },
            { value: 'failed', label: '失败' },
          ]}
        />
        <RangePicker
          showTime
          onChange={(dates) => {
            setForm({
              ...form,
              startTime: dates?.[0]?.toISOString(),
              endTime: dates?.[1]?.toISOString(),
            });
          }}
        />
        <Button type="primary" icon={<SearchOutlined />} onClick={() => { setPage(1); fetchData(); }}>
          搜索
        </Button>
      </Space>

      <Table
        columns={columns}
        dataSource={data}
        rowKey={(record) => record.agentId ?? record.requestId}
        loading={loading}
        scroll={{ x: 1200 }}
        pagination={{
          current: page,
          pageSize,
          total,
          showSizeChanger: true,
          showQuickJumper: true,
          showTotal: (t) => `共 ${t} 条`,
          onChange: (p, ps) => { setPage(p); setPageSize(ps); },
        }}
      />
    </Card>
  );
};

export default RequestSearchPage;
