import React, { useState, useEffect } from 'react';
import { Card, Input, Button, Spin, message, Tag, Space, Table, Empty } from 'antd';
import { useParams } from 'react-router-dom';
import { logApi } from '../../services/api';
import type { LogFile, LogSearchResult } from '../../types';

const LogViewerPage: React.FC = () => {
  const { requestId } = useParams<{ requestId: string }>();
  const [loading, setLoading] = useState(false);
  const [logData, setLogData] = useState<LogFile | null>(null);
  const [page, setPage] = useState(0);
  const [searchKeyword, setSearchKeyword] = useState('');
  const [searchResults, setSearchResults] = useState<LogSearchResult[]>([]);
  const [searching, setSearching] = useState(false);
  const [username] = useState('default');

  const fetchLog = async (p: number) => {
    if (!requestId) return;
    setLoading(true);
    try {
      const res = await logApi.getFile(requestId, username, p);
      if (res.data.code === 0) {
        setLogData(res.data.data);
      } else {
        message.error(res.data.message);
      }
    } catch (e: any) {
      message.error('加载日志失败: ' + (e.message || ''));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    setPage(0);
    fetchLog(0);
  }, [requestId]);

  const handleSearch = async () => {
    if (!requestId || !searchKeyword.trim()) return;
    setSearching(true);
    try {
      const res = await logApi.search(requestId, username, searchKeyword);
      if (res.data.code === 0) {
        setSearchResults(res.data.data || []);
      }
    } catch (e: any) {
      message.error('搜索失败: ' + (e.message || ''));
    } finally {
      setSearching(false);
    }
  };

  const highlightLine = (line: string, keyword: string) => {
    if (!keyword) return <span>{line}</span>;
    const parts = line.split(new RegExp(`(${keyword.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')})`, 'gi'));
    return (
      <span>
        {parts.map((part, i) =>
          part.toLowerCase() === keyword.toLowerCase()
            ? <mark key={i} style={{ backgroundColor: 'yellow' }}>{part}</mark>
            : part
        )}
      </span>
    );
  };

  if (loading && !logData) return <Spin size="large" style={{ display: 'block', margin: '100px auto' }} />;

  return (
    <div>
      <Card
        title={`Per-Request 日志：${requestId}`}
        extra={
          <Space>
            <Button onClick={() => window.open(logApi.download(requestId!, username), '_blank')}>
              下载原始日志
            </Button>
            <Button onClick={() => fetchLog(page)}>刷新</Button>
          </Space>
        }
      >
        {logData?.status === 'not_found' && (
          <Empty description="日志文件不存在或已过期删除" />
        )}

        {logData?.status === 'too_large' && (
          <Empty description="日志文件超过 100MB，请使用下载接口获取" />
        )}

        {logData?.lines && (
          <>
            <Space style={{ marginBottom: 12 }}>
              <Input
                placeholder="搜索关键词"
                value={searchKeyword}
                onChange={(e) => setSearchKeyword(e.target.value)}
                onPressEnter={handleSearch}
                style={{ width: 300 }}
              />
              <Button type="primary" onClick={handleSearch} loading={searching}>搜索</Button>
              {searchResults.length > 0 && (
                <Tag color="blue">{searchResults.length} 条匹配</Tag>
              )}
            </Space>

            {searchResults.length > 0 && (
              <Table
                title={() => `搜索结果（${searchResults.length} 条）`}
                dataSource={searchResults}
                rowKey="lineNumber"
                pagination={{ pageSize: 50 }}
                size="small"
                columns={[
                  { title: '行号', dataIndex: 'lineNumber', width: 80 },
                  {
                    title: '内容',
                    dataIndex: 'lineContent',
                    render: (v) => <pre style={{ margin: 0, fontFamily: 'monospace', fontSize: 12 }}>{highlightLine(v, searchKeyword)}</pre>,
                  },
                ]}
              />
            )}

            <div style={{
              background: '#1e1e1e',
              color: '#d4d4d4',
              padding: '8px 12px',
              fontFamily: 'monospace',
              fontSize: 12,
              borderRadius: 4,
              maxHeight: 'calc(100vh - 300px)',
              overflow: 'auto',
            }}>
              {logData.lines.map((line, i) => (
                <div key={i} className="log-line">
                  <span style={{ color: '#858585', marginRight: 12 }}>{logData.startLine + i + 1}</span>
                  {highlightLine(line, searchKeyword)}
                </div>
              ))}
            </div>

            <div style={{ marginTop: 12, textAlign: 'center' }}>
              <Button
                disabled={logData.startLine === 0}
                onClick={() => { const p = page - 1; setPage(p); fetchLog(p); }}
              >
                上一页
              </Button>
              <span style={{ margin: '0 16px' }}>
                第 {page + 1} 页 {logData.hasMore ? '(有更多)' : '(已加载全部)'}
              </span>
              <Button
                disabled={!logData.hasMore}
                onClick={() => { const p = page + 1; setPage(p); fetchLog(p); }}
              >
                下一页
              </Button>
            </div>
          </>
        )}
      </Card>
    </div>
  );
};

export default LogViewerPage;
