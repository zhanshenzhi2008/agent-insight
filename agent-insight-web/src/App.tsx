import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { Layout, Menu } from 'antd';
import {
  SearchOutlined, DatabaseOutlined, FileTextOutlined,
  ApiOutlined, CodeOutlined, AppstoreOutlined, TableOutlined, SettingOutlined
} from '@ant-design/icons';
import { useNavigate, useLocation } from 'react-router-dom';
import React from 'react';

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

const { Header, Sider, Content } = Layout;

const AppLayout: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const navigate = useNavigate();
  const location = useLocation();

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
      ],
    },
  ];

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Header style={{ background: '#001529', color: '#fff', fontSize: 18, fontWeight: 600, display: 'flex', alignItems: 'center', padding: '0 24px' }}>
        Agent Insight 分析引擎
      </Header>
      <Layout>
        <Sider width={220} style={{ background: '#fff' }}>
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
    <AppLayout>
      <Routes>
        <Route path="/" element={<Navigate to="/search" replace />} />

        {/* Agent Insight 分析模块 */}
        <Route path="/search" element={<RequestSearchPage />} />
        <Route path="/overview" element={<RequestOverviewPage />} />
        <Route path="/overview/:requestId" element={<RequestOverviewPage />} />
        <Route path="/trace/:requestId" element={<TraceAnalysisPage />} />
        <Route path="/log/:requestId" element={<LogViewerPage />} />
        <Route path="/llm/:requestId" element={<LlmAnalysisPage />} />
        <Route path="/source" element={<SourceViewerPage />} />
        <Route path="/source/:agentName" element={<SourceViewerPage />} />

        {/* Data Explorer 模块（通用跨库查询） */}
        <Route path="/explorer/query" element={<TableExplorerPage />} />
        <Route path="/explorer/datasource" element={<DatasourcePage />} />
        <Route path="/explorer/table" element={<TableConfigPage />} />
        <Route path="/explorer/columns" element={<ColumnConfigPage />} />
        <Route path="/explorer/template" element={<QueryTemplatePage />} />
      </Routes>
    </AppLayout>
  </BrowserRouter>
);

export default App;
