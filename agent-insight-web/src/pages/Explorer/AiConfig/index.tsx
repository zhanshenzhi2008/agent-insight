import React, { useState, useEffect } from 'react';
import { Card, Form, Input, Select, Switch, Button, Space, message, Alert, Divider } from 'antd';
import { explorerApi } from '../../../services/explorerApi';

interface AiConfigForm {
  enabled: boolean;
  columnAnalysisEnabled: boolean;
  nlQueryEnabled: boolean;
  summarizationEnabled: boolean;
}

interface AiStatusResponse {
  code: number;
  data: {
    enabled: boolean;
    columnAnalysisEnabled: boolean;
    nlQueryEnabled: boolean;
    summarizationEnabled: boolean;
  };
}

const AiConfigPage: React.FC = () => {
  const [form] = Form.useForm<AiConfigForm>();
  const [loading, setLoading] = useState(false);
  const [initialLoading, setInitialLoading] = useState(true);

  useEffect(() => {
    loadConfig();
  }, []);

  const loadConfig = async () => {
    setInitialLoading(true);
    try {
      const res = await explorerApi.getAiStatus();
      const payload = res.data as AiStatusResponse;
      if (payload.code === 0) {
        const data = payload.data;
        form.setFieldsValue({
          enabled: data.enabled ?? false,
          columnAnalysisEnabled: data.columnAnalysisEnabled ?? true,
          nlQueryEnabled: data.nlQueryEnabled ?? true,
          summarizationEnabled: data.summarizationEnabled ?? true,
        });
      } else {
        message.error('加载 AI 配置失败');
      }
    } catch (e: any) {
      message.error('加载 AI 配置失败: ' + (e.message || ''));
    } finally {
      setInitialLoading(false);
    }
  };

  const handleSave = async (values: AiConfigForm) => {
    setLoading(true);
    try {
      const res = await explorerApi.updateAiConfig(values);
      const payload = res.data as { code: number };
      if (payload.code === 0) {
        message.success('AI 配置保存成功');
      } else {
        message.error('保存失败');
      }
    } catch (e: any) {
      message.error('保存失败: ' + (e.message || ''));
    } finally {
      setLoading(false);
    }
  };

  return (
    <Card title="AI 配置">
      <Alert
        message="配置说明"
        description={
          <ul style={{ margin: 0, paddingLeft: 16 }}>
            <li>以下配置通过 PUT /api/v1/explorer/ai/config 接口实时生效</li>
            <li>
              <strong>Provider / API Key / Base URL / Model</strong>：当前后端仅支持通过配置文件修改，
              不支持运行时切换。修改请编辑 application.yml 中的 spring.ai.* 或 agent-insight.ai.* 配置项
            </li>
            <li>支持切换的模型供应商：openai、ollama、deepseek、anthropic、google</li>
          </ul>
        }
        type="info"
        style={{ marginBottom: 24 }}
      />

      <Form
        form={form}
        layout="vertical"
        onFinish={handleSave}
        initialValues={{
          enabled: false,
          columnAnalysisEnabled: true,
          nlQueryEnabled: true,
          summarizationEnabled: true,
        }}
      >
        <Divider>功能开关</Divider>

        <Form.Item
          name="enabled"
          label="启用 AI 功能"
          valuePropName="checked"
          extra="开启后将允许使用 AI 分析、自然语言查询、结果摘要等功能"
        >
          <Switch />
        </Form.Item>

        <Form.Item
          name="columnAnalysisEnabled"
          label="AI 列分析"
          valuePropName="checked"
          extra="基于语义推断最佳渲染类型（图片/链接/颜色等）"
        >
          <Switch />
        </Form.Item>

        <Form.Item
          name="nlQueryEnabled"
          label="自然语言查询"
          valuePropName="checked"
          extra="将自然语言描述转换为查询过滤条件"
        >
          <Switch />
        </Form.Item>

        <Form.Item
          name="summarizationEnabled"
          label="结果摘要"
          valuePropName="checked"
          extra="使用 AI 自动解读查询结果并生成摘要"
        >
          <Switch />
        </Form.Item>

        <Divider>模型配置（仅配置文件可改）</Divider>

        <Form.Item label="Provider">
          <Select disabled placeholder="openai / ollama / deepseek / anthropic / google">
            <Select.Option value="openai">openai</Select.Option>
            <Select.Option value="ollama">ollama</Select.Option>
            <Select.Option value="deepseek">deepseek</Select.Option>
            <Select.Option value="anthropic">anthropic</Select.Option>
            <Select.Option value="google">google</Select.Option>
          </Select>
          <div style={{ fontSize: 12, color: '#999', marginTop: 4 }}>
            请在 application.yml 中配置 spring.ai.openai.api-key 等字段
          </div>
        </Form.Item>

        <Form.Item label="API Key">
          <Input.Password disabled placeholder="请在配置文件中设置 spring.ai.openai.api-key" />
        </Form.Item>

        <Form.Item label="Base URL">
          <Input.Password disabled placeholder="请在配置文件中设置 spring.ai.openai.base-url" />
        </Form.Item>

        <Form.Item label="Model">
          <Input disabled placeholder="如 gpt-4o，请配置 agent-insight.ai.default-model" />
        </Form.Item>

        <Form.Item>
          <Space>
            <Button type="primary" htmlType="submit" loading={loading}>
              保存配置
            </Button>
            <Button onClick={loadConfig} loading={initialLoading}>
              重置
            </Button>
          </Space>
        </Form.Item>
      </Form>
    </Card>
  );
};

export default AiConfigPage;
