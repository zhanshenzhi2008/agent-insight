import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { Layout, Menu, App as AntdAppProvider } from 'antd';
import {
  SearchOutlined, DatabaseOutlined, FileTextOutlined,
  ApiOutlined, CodeOutlined, AppstoreOutlined, TableOutlined, SettingOutlined
} from '@ant-design/icons';
import { useNavigate, useLocation } from 'react-router-dom';
import React, { useState } from 'react';

import RequestSearchPage from './pages/RequestSearch';
import RequestOverviewPage from './pages/RequestOverview';
import TraceAnalysisPage from './pages/TraceAnalysis';
import LogViewerPage from './pages/LogViewer';
import LlmAnalysisPage from './pages/LlmAnalysis';
import SourceViewerPage from './pages/SourceViewer';
import TableExplorerPage from './pages/Explorer/TableExplorer';
import DatasourcePage from './pages/Explorer/Datasource';
import ColumnConfigPage from './pages/Explorer/ColumnConfig';
import TableConfigPage from './pages/Explorer/TableConfig';
import QueryTemplatePage from './pages/Explorer/QueryTemplate';
import AiConfigPage from './pages/Explorer/AiConfig';
import RequireAuth from './components/RequireAuth';

const { Header, Sider, Content } = Layout;

const AppLayout: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const navigate = useNavigate();
  const location = useLocation();
  const [collapsed, setCollapsed] = useState(false);

  const pathParts = location.pathname.split('/');
  const selectedKey = '/' + pathParts[1];

  const menuItems = [
    { key: '/search', icon: <SearchOutlined />, label: '请求检索' },
    { key: '/overview', icon: <DatabaseOutlined />, label: '请求概览' },
    { key: '/trace', icon: <FileTextOutlined />, label: '执行轨迹' },
    { key: '/log', icon: <FileTextOutlined />, label: '日志查看' },
    { key: '/llm', icon: <ApiOutlined />, label: 'LLM 分析' },
    { key: '/source', icon: <CodeOutlined />, label: '源码对照' },
    {
      key: '/explorer',
      icon: <AppstoreOutlined />,
      label: '数据浏览器',
      children: [
        { key: '/explorer/query', icon: <TableOutlined />, label: '数据查询' },
        { key: '/explorer/datasource', icon: <DatabaseOutlined />, label: '数据源' },
        { key: '/explorer/table', icon: <TableOutlined />, label: '表配置' },
        { key: '/explorer/columns', icon: <SettingOutlined />, label: '列配置' },
        { key: '/explorer/template', icon: <CodeOutlined />, label: '查询模板' },
        { key: '/explorer/ai-config', icon: <ApiOutlined />, label: 'AI 配置' },
      ],
    },
  ];

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Header style={{ background: '#001529', color: '#fff', fontSize: 18, fontWeight: 600, display: 'flex', alignItems: 'center', padding: '0 24px' }}>
        Agent Insight 分析引擎
      </Header>
      <Layout>
        <Sider
          collapsible
          collapsedWidth={64}
          breakpoint="lg"
          onBreakpoint={(broken) => setCollapsed(broken)}
          collapsed={collapsed}
          onCollapse={setCollapsed}
          style={{ background: '#fff' }}
        >
          <Menu
            mode="inline"
            selectedKeys={[selectedKey]}
            items={menuItems}
            onClick={({ key }) => navigate(key)}
            style={{ height: '100%', borderRight: 0 }}
          />
        </Sider>
        <Content style={{ padding: 24, minHeight: 280, overflow: 'auto' }}>
          {children}
        </Content>
      </Layout>
    </Layout>
  );
};

const App = () => (
  <BrowserRouter>
    <AntdAppProvider>
      <AppLayout>
        <Routes>
          <Route path="/" element={<Navigate to="/search" replace />} />

          {/* Agent Insight 分析模块 */}
          <Route path="/search" element={<RequireAuth permission="view:requests"><RequestSearchPage /></RequireAuth>} />
          <Route path="/overview" element={<RequireAuth permission="view:requests"><RequestOverviewPage /></RequireAuth>} />
          <Route path="/overview/:requestId" element={<RequireAuth permission="view:requests"><RequestOverviewPage /></RequireAuth>} />
          {/* 执行轨迹：列表页 + 详情页 */}
          <Route path="/trace" element={<RequireAuth permission="view:trace"><TraceAnalysisPage /></RequireAuth>} />
          <Route path="/trace/:requestId" element={<RequireAuth permission="view:trace"><TraceAnalysisPage /></RequireAuth>} />
          {/* 日志查看：列表页 + 详情页 */}
          <Route path="/log" element={<RequireAuth permission="view:logs"><LogViewerPage /></RequireAuth>} />
          <Route path="/log/:requestId" element={<RequireAuth permission="view:logs"><LogViewerPage /></RequireAuth>} />
          {/* LLM 分析：列表页 + 详情页 */}
          <Route path="/llm" element={<RequireAuth permission="view:llm"><LlmAnalysisPage /></RequireAuth>} />
          <Route path="/llm/:requestId" element={<RequireAuth permission="view:llm"><LlmAnalysisPage /></RequireAuth>} />
          <Route path="/source" element={<RequireAuth permission="view:source"><SourceViewerPage /></RequireAuth>} />
          <Route path="/source/:agentName" element={<RequireAuth permission="view:source"><SourceViewerPage /></RequireAuth>} />

          {/* Data Explorer 模块（通用跨库查询） */}
          <Route path="/explorer/query" element={<RequireAuth permission="explorer:query"><TableExplorerPage /></RequireAuth>} />
          <Route path="/explorer/datasource" element={<RequireAuth permission="explorer:datasource"><DatasourcePage /></RequireAuth>} />
          <Route path="/explorer/table" element={<RequireAuth permission="explorer:config"><TableConfigPage /></RequireAuth>} />
          <Route path="/explorer/columns" element={<RequireAuth permission="explorer:config"><ColumnConfigPage /></RequireAuth>} />
          <Route path="/explorer/template" element={<RequireAuth permission="explorer:query"><QueryTemplatePage /></RequireAuth>} />
          <Route path="/explorer/ai-config" element={<RequireAuth permission="explorer:config"><AiConfigPage /></RequireAuth>} />
        </Routes>
      </AppLayout>
    </AntdAppProvider>
  </BrowserRouter>
);

export default App;
