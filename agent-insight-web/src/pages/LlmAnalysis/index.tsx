import React, { useState, useEffect } from 'react';
import { Card, Table, Tabs, Tag, Spin, message, Row, Col, Statistic, Empty, Modal, Descriptions, Button, Alert } from 'antd';
import { useParams } from 'react-router-dom';
import { llmApi } from '../../services/api';
import type { LlmCall, TokenUsage } from '../../types';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';

const LlmAnalysisPage: React.FC = () => {
  const { requestId } = useParams<{ requestId: string }>();
  const [loading, setLoading] = useState(false);
  const [calls, setCalls] = useState<LlmCall[]>([]);
  const [tokenUsage, setTokenUsage] = useState<TokenUsage | null>(null);
  const [slowCalls, setSlowCalls] = useState<LlmCall[]>([]);
  const [failedCalls, setFailedCalls] = useState<LlmCall[]>([]);
  const [selectedCall, setSelectedCall] = useState<LlmCall | null>(null);
  const [callDetail, setCallDetail] = useState<any>(null);
  const [detailLoading, setDetailLoading] = useState(false);

  const fetchAll = async () => {
    if (!requestId) return;
    setLoading(true);
    try {
      const [callsRes, usageRes, slowRes, failedRes] = await Promise.all([
        llmApi.list(requestId),
        llmApi.tokenUsage(requestId),
        llmApi.slowCalls(requestId, 5),
        llmApi.failedCalls(requestId),
      ]);
      if (callsRes.data.code === 0) setCalls(callsRes.data.data || []);
      if (usageRes.data.code === 0) setTokenUsage(usageRes.data.data);
      if (slowRes.data.code === 0) setSlowCalls(slowRes.data.data || []);
      if (failedRes.data.code === 0) setFailedCalls(failedRes.data.data || []);
    } catch (e: any) {
      message.error('加载失败: ' + (e.message || ''));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { fetchAll(); }, [requestId]);

  const openDetail = async (call: LlmCall) => {
    setSelectedCall(call);
    setDetailLoading(true);
    try {
      const res = await llmApi.detail(call.id);
      if (res.data.code === 0) setCallDetail(res.data.data);
    } catch (e) {
      setCallDetail(null);
    } finally {
      setDetailLoading(false);
    }
  };

  const columns: ColumnsType<LlmCall> = [
    { title: 'ID', dataIndex: 'id', width: 70 },
    { title: 'Agent', dataIndex: 'agent', width: 120 },
    { title: 'Template', dataIndex: 'templateName', width: 100, ellipsis: true },
    { title: 'Model', dataIndex: 'modelType', width: 100 },
    {
      title: '耗时',
      dataIndex: 'spendTime',
      width: 100,
      render: (v) => v ? `${(v / 1000).toFixed(2)}s` : '-',
    },
    {
      title: 'Tokens',
      children: [
        { title: 'Prompt', dataIndex: 'promptTokens', width: 80 },
        { title: 'Completion', dataIndex: 'completionTokens', width: 90 },
        { title: 'Total', dataIndex: 'totalTokens', width: 80 },
      ],
    },
    {
      title: '结果',
      dataIndex: 'successExpression',
      width: 80,
      render: (v) => v
        ? <Tag color="success">成功</Tag>
        : <Tag color="error">失败</Tag>,
    },
    {
      title: '时间',
      dataIndex: 'createTime',
      width: 170,
      render: (v) => v ? dayjs(v).format('HH:mm:ss.SSS') : '-',
    },
    {
      title: '操作',
      key: 'action',
      width: 80,
      render: (_, r) => <Button type="link" size="small" onClick={() => openDetail(r)}>详情</Button>,
    },
  ];

  if (loading) return <Spin size="large" style={{ display: 'block', margin: '100px auto' }} />;

  return (
    <div>
      <Card title={`LLM 调用分析：${requestId}`}>
        {tokenUsage && (
          <Row gutter={16} style={{ marginBottom: 24 }}>
            <Col span={6}><Statistic title="调用次数" value={tokenUsage.callCount} /></Col>
            <Col span={6}><Statistic title="Prompt Tokens" value={tokenUsage.totalPromptTokens} /></Col>
            <Col span={6}><Statistic title="Completion Tokens" value={tokenUsage.totalCompletionTokens} /></Col>
            <Col span={6}><Statistic title="总 Tokens" value={tokenUsage.totalTokens} /></Col>
          </Row>
        )}

        <Tabs
          defaultActiveKey="all"
          items={[{
            key: 'all',
            label: `全部（${calls.length}）`,
            children: <Table columns={columns} dataSource={calls} rowKey="id" pagination={{ pageSize: 20 }} size="small" />,
          }, {
            key: 'slow',
            label: `慢调用（${slowCalls.length}）`,
            children: slowCalls.length === 0
              ? <Empty description="无慢调用" />
              : <Table columns={columns} dataSource={slowCalls} rowKey="id" pagination={false} size="small" />,
          }, {
            key: 'failed',
            label: `失败（${failedCalls.length}）`,
            children: failedCalls.length === 0
              ? <Empty description="无失败调用" />
              : <Table columns={columns} dataSource={failedCalls} rowKey="id" pagination={false} size="small" />,
          }]}
        />
      </Card>

      <Modal
        title={`LLM 调用详情 #${selectedCall?.id}`}
        open={!!selectedCall}
        onCancel={() => { setSelectedCall(null); setCallDetail(null); }}
        width={900}
        footer={<Button onClick={() => setSelectedCall(null)}>关闭</Button>}
      >
        <Spin spinning={detailLoading}>
          {callDetail && (
            <>
              <Descriptions size="small" column={2}>
                <Descriptions.Item label="Agent">{callDetail.agent}</Descriptions.Item>
                <Descriptions.Item label="Template">{callDetail.templateName}</Descriptions.Item>
                <Descriptions.Item label="Model">{callDetail.modelType}</Descriptions.Item>
                <Descriptions.Item label="耗时">{(callDetail.spendTime / 1000).toFixed(2)}s</Descriptions.Item>
                <Descriptions.Item label="Prompt Tokens">{callDetail.promptTokens}</Descriptions.Item>
                <Descriptions.Item label="Completion Tokens">{callDetail.completionTokens}</Descriptions.Item>
              </Descriptions>

              <Alert message="Request Body" type="info" style={{ marginTop: 16 }} />
              <pre style={{ background: '#f5f5f5', padding: 12, maxHeight: 300, overflow: 'auto', fontSize: 11 }}>
                {callDetail.requestBody || '(空)'}
              </pre>

              <Alert message="Response Body" type="success" style={{ marginTop: 12 }} />
              <pre style={{ background: '#f5f5f5', padding: 12, maxHeight: 300, overflow: 'auto', fontSize: 11 }}>
                {callDetail.responseBody || '(空)'}
              </pre>
            </>
          )}
        </Spin>
      </Modal>
    </div>
  );
};

export default LlmAnalysisPage;
