import React, { useState, useEffect } from 'react';
import { Card, Descriptions, Spin, message, Tag, Table, Space, Row, Col, Statistic, Button } from 'antd';
import { useParams, useNavigate } from 'react-router-dom';
import { requestApi } from '../../services/api';
import type { RequestOverview, AgentInstance } from '../../types';
import dayjs from 'dayjs';

const RequestOverviewPage: React.FC = () => {
  const { requestId } = useParams<{ requestId: string }>();
  const navigate = useNavigate();
  const [loading, setLoading] = useState(false);
  const [data, setData] = useState<RequestOverview | null>(null);

  const fetchOverview = async (rid: string) => {
    setLoading(true);
    try {
      const res = await requestApi.overview(rid);
      if (res.data.code === 0) {
        setData(res.data.data);
      } else {
        message.error(res.data.message);
      }
    } catch (e: any) {
      message.error('加载概览失败: ' + (e.message || ''));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (requestId) {
      fetchOverview(requestId);
    }
  }, [requestId]);

  if (loading) return <Spin size="large" style={{ display: 'block', margin: '100px auto' }} />;
  if (!data) return <Card><Empty description="未找到该请求" /></Card>;

  const agentColumns = [
    { title: 'Agent 名称', dataIndex: 'agentName', key: 'agentName' },
    {
      title: '类型',
      dataIndex: 'entrance',
      key: 'entrance',
      render: (v: boolean) => v ? <Tag color="blue">入口</Tag> : <Tag>子 Agent</Tag>,
    },
    { title: '任务数', dataIndex: 'taskCount', key: 'taskCount' },
    {
      title: '结果',
      dataIndex: 'success',
      key: 'success',
      render: (v: boolean) => v ? <Tag color="success">成功</Tag> : <Tag color="error">失败</Tag>,
    },
    {
      title: '耗时',
      dataIndex: 'duration',
      key: 'duration',
      render: (v: number) => v ? `${(v / 1000).toFixed(2)}s` : '-',
    },
    {
      title: '操作',
      key: 'action',
      render: (_: any, r: AgentInstance) => (
        <Space>
          <Button type="link" size="small" onClick={() => navigate(`/trace/${requestId}?agent=${r.agentName}`)}>轨迹</Button>
        </Space>
      ),
    },
  ];

  return (
    <div>
      <Card
        title={`请求概览：${requestId}`}
        extra={<Space>
          <Button onClick={() => navigate(`/trace/${requestId}`)}>执行轨迹</Button>
          <Button onClick={() => navigate(`/log/${requestId}`)}>日志查看</Button>
          <Button onClick={() => navigate(`/llm/${requestId}`)}>LLM 分析</Button>
        </Space>}
      >
        <Row gutter={16} style={{ marginBottom: 24 }}>
          <Col span={6}><Statistic title="Agent" value={data.topAgentName} /></Col>
          <Col span={6}>
            <Statistic
              title="结果"
              value={data.success ? '成功' : '失败'}
              valueStyle={{ color: data.success ? '#52c41a' : '#fa541c' }}
            />
          </Col>
          <Col span={6}><Statistic title="总任务数" value={data.totalTaskCount} /></Col>
          <Col span={6}>
            <Statistic
              title="失败任务"
              value={data.failedTaskCount}
              valueStyle={{ color: data.failedTaskCount > 0 ? '#fa541c' : '#52c41a' }}
            />
          </Col>
        </Row>

        <Row gutter={16} style={{ marginBottom: 24 }}>
          <Col span={6}>
            <Statistic
              title="耗时"
              value={data.totalDuration ? (data.totalDuration / 1000).toFixed(2) : '-'}
              suffix="s"
            />
          </Col>
          <Col span={6}><Statistic title="LLM 调用次数" value={data.llmCallCount} /></Col>
          <Col span={6}><Statistic title="Prompt Tokens" value={data.totalPromptTokens} /></Col>
          <Col span={6}><Statistic title="Completion Tokens" value={data.totalCompletionTokens} /></Col>
        </Row>

        <Descriptions column={3} size="small">
          <Descriptions.Item label="Request ID">
            <code>{data.requestId}</code>
          </Descriptions.Item>
          <Descriptions.Item label="创建时间">
            {data.createTime ? dayjs(data.createTime).format('YYYY-MM-DD HH:mm:ss') : '-'}
          </Descriptions.Item>
        </Descriptions>
      </Card>

      <Card title="Agent 实例列表" style={{ marginTop: 16 }}>
        <Table
          columns={agentColumns}
          dataSource={data.agentInstances}
          rowKey="agentId"
          pagination={false}
          size="small"
        />
      </Card>
    </div>
  );
};

export default RequestOverviewPage;
