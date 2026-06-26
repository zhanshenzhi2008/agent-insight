import React, { useState, useEffect } from 'react';
import { Card, Table, Tabs, Tag, Spin, message, Descriptions, Badge, Empty, Select, Button } from 'antd';
import { useParams, useSearchParams } from 'react-router-dom';
import { traceApi } from '../../services/api';
import type { TaskDetail, TaskStep } from '../../types';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';

const stepLabels: Record<number, string> = {
  0: 'RAG',
  1: 'Template',
  2: 'Parser',
  3: 'Verifier',
  4: 'Action',
};

const TraceAnalysisPage: React.FC = () => {
  const { requestId } = useParams<{ requestId: string }>();
  const [searchParams] = useSearchParams();
  const agentName = searchParams.get('agent') || undefined;

  const [loading, setLoading] = useState(false);
  const [trace, setTrace] = useState<TaskDetail[]>([]);
  const [failedTasks, setFailedTasks] = useState<TaskDetail[]>([]);
  const [overview, setOverview] = useState<{ total: number; success: number; failed: number } | null>(null);

  const fetchTrace = async () => {
    if (!requestId) return;
    setLoading(true);
    try {
      const [traceRes, failedRes] = await Promise.all([
        traceApi.getTrace(requestId, agentName),
        traceApi.getFailed(requestId),
      ]);

      if (traceRes.data.code === 0) {
        setTrace(traceRes.data.data || []);
        const total = traceRes.data.data?.length || 0;
        const failed = (traceRes.data.data || []).filter((t: TaskDetail) => !t.success).length;
        setOverview({ total, success: total - failed, failed });
      }
      if (failedRes.data.code === 0) {
        setFailedTasks(failedRes.data.data || []);
      }
    } catch (e: any) {
      message.error('加载轨迹失败: ' + (e.message || ''));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { fetchTrace(); }, [requestId, agentName]);

  const expandedRowRender = (record: TaskDetail) => {
    const stepColumns: ColumnsType<TaskStep> = [
      { title: '#', dataIndex: 'step', width: 60, render: (v) => stepLabels[v] || v },
      { title: 'Template', dataIndex: 'template', width: 120, ellipsis: true },
      {
        title: '结果',
        dataIndex: 'success',
        width: 70,
        render: (v) => v ? <Badge status="success" text="成功" /> : <Badge status="error" text="失败" />,
      },
      {
        title: 'Input',
        dataIndex: 'input',
        ellipsis: true,
        render: (v) => v ? <pre style={{ maxWidth: 400, overflow: 'hidden', textOverflow: 'ellipsis', margin: 0 }}>{String(v).substring(0, 200)}</pre> : '-',
      },
      {
        title: 'Output',
        dataIndex: 'output',
        ellipsis: true,
        render: (v) => v ? <pre style={{ maxWidth: 400, overflow: 'hidden', textOverflow: 'ellipsis', margin: 0 }}>{String(v).substring(0, 200)}</pre> : '-',
      },
    ];

    return (
      <div style={{ padding: '8px 0' }}>
        <Descriptions size="small" column={3} style={{ marginBottom: 12 }}>
          <Descriptions.Item label="Full Path">{record.fullPath}</Descriptions.Item>
          <Descriptions.Item label="Agent">{record.agentName}</Descriptions.Item>
          <Descriptions.Item label="耗时">{record.duration ? `${(record.duration / 1000).toFixed(2)}s` : '-'}</Descriptions.Item>
          {record.sourceFile && (
            <Descriptions.Item label="源码位置">
              {record.sourceFile}:{record.sourceStartLine}-{record.sourceEndLine}
            </Descriptions.Item>
          )}
        </Descriptions>
        <Table
          columns={stepColumns}
          dataSource={record.steps}
          rowKey="id"
          pagination={false}
          size="small"
        />
      </div>
    );
  };

  const columns: ColumnsType<TaskDetail> = [
    {
      title: '#',
      dataIndex: 'taskIndex',
      key: 'taskIndex',
      width: 60,
    },
    {
      title: 'Task Unique Name',
      dataIndex: 'taskUniqueName',
      key: 'taskUniqueName',
      width: 200,
      ellipsis: true,
    },
    { title: 'Task Name', dataIndex: 'taskName', key: 'taskName', width: 150 },
    { title: 'Type', dataIndex: 'taskType', key: 'taskType', width: 100 },
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
      title: '耗时',
      dataIndex: 'duration',
      key: 'duration',
      width: 100,
      render: (v) => v ? `${(v / 1000).toFixed(2)}s` : '-',
    },
    {
      title: '开始时间',
      dataIndex: 'createTime',
      key: 'createTime',
      width: 170,
      render: (v) => v ? dayjs(v).format('HH:mm:ss.SSS') : '-',
    },
    {
      title: '步骤数',
      key: 'stepCount',
      width: 80,
      render: (_, r) => r.steps?.length || 0,
    },
  ];

  if (loading) return <Spin size="large" style={{ display: 'block', margin: '100px auto' }} />;

  return (
    <div>
      <Card
        title={`执行轨迹：${requestId}`}
        extra={
          <Select
            placeholder="选择 Agent"
            value={agentName}
            onChange={(v) => window.location.href = `/trace/${requestId}?agent=${v}`}
            style={{ width: 160 }}
            allowClear
          />
        }
      >
        {overview && (
          <div style={{ marginBottom: 16 }}>
            <Tag color="blue">总计 {overview.total}</Tag>
            <Tag color="green">成功 {overview.success}</Tag>
            <Tag color="red">失败 {overview.failed}</Tag>
          </div>
        )}
        <Table
          columns={columns}
          dataSource={trace}
          rowKey="id"
          expandable={{ expandedRowRender, expandRowByClick: true }}
          pagination={false}
          size="small"
          scroll={{ y: 500 }}
          locale={{ emptyText: <Empty description="暂无轨迹数据" /> }}
        />
      </Card>

      {failedTasks.length > 0 && (
        <Card title={`失败任务（${failedTasks.length} 个）`} style={{ marginTop: 16 }} bordered={false}>
          <Table
            columns={[
              { title: '#', dataIndex: 'taskIndex', width: 60 },
              { title: 'Task', dataIndex: 'taskUniqueName', ellipsis: true },
              {
                title: '错误信息',
                dataIndex: 'errorMessage',
                ellipsis: true,
                render: (v) => v ? <span style={{ color: '#fa541c' }}>{v}</span> : '-',
              },
              { title: '耗时', dataIndex: 'duration', render: (v) => v ? `${(v / 1000).toFixed(2)}s` : '-' },
            ]}
            dataSource={failedTasks}
            rowKey="id"
            pagination={false}
            size="small"
          />
        </Card>
      )}
    </div>
  );
};

export default TraceAnalysisPage;
