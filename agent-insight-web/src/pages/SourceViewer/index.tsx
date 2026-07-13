import React, { useState, useEffect, useRef } from 'react';
import { Card, Input, Select, Spin, message, Tree, Empty, Button, Space, Tabs } from 'antd';
import { useParams, useSearchParams } from 'react-router-dom';
import Editor, { type Monaco } from '@monaco-editor/react';
import type { editor } from 'monaco-editor';
import { sourceApi } from '../../services/api';
import type { ScriptFile, SourceLineMapping } from '../../types';

const SourceViewerPage: React.FC = () => {
  const { agentName: urlAgentName } = useParams<{ agentName: string }>();
  const [searchParams] = useSearchParams();
  const [searchAgentName, setSearchAgentName] = useState(urlAgentName || searchParams.get('agent') || '');
  const [loading, setLoading] = useState(false);
  const [scripts, setScripts] = useState<ScriptFile[]>([]);
  const [selectedScript, setSelectedScript] = useState<string | null>(null);
  const [sourceContent, setSourceContent] = useState('');
  const [contentLoading, setContentLoading] = useState(false);
  const [mapping, setMapping] = useState<SourceLineMapping | null>(null);
  const [taskUniqueName, setTaskUniqueName] = useState('');

  const editorRef = useRef<editor.IStandaloneCodeEditor | null>(null);
  const monacoRef = useRef<Monaco | null>(null);
  const decorationRef = useRef<string[]>([]);

  const fetchScripts = async (agent: string) => {
    if (!agent.trim()) return;
    setLoading(true);
    try {
      const res = await sourceApi.listScripts(agent);
      if (res.data.code === 0) {
        setScripts(res.data.data || []);
        if ((res.data.data || []).length > 0) {
          loadScript(res.data.data[0].fullPath);
        }
      } else {
        message.error(res.data.message);
      }
    } catch (e: any) {
      message.error('加载脚本列表失败: ' + (e.message || ''));
    } finally {
      setLoading(false);
    }
  };

  const loadScript = async (path: string) => {
    setSelectedScript(path);
    setContentLoading(true);
    try {
      const res = await sourceApi.content(path);
      if (res.data.code === 0) setSourceContent(res.data.data);
    } catch (e) {
      setSourceContent('/* 加载失败 */');
    } finally {
      setContentLoading(false);
    }
  };

  const applyLineDecorations = () => {
    const editor = editorRef.current;
    const monaco = monacoRef.current;
    if (!editor || !monaco || !mapping) return;

    const { startLine, endLine } = mapping;
    const decorations: editor.IModelDeltaDecoration[] = [];

    for (let line = startLine; line <= endLine; line++) {
      decorations.push({
        range: new monaco.Range(line, 1, line, 1),
        options: {
          isWholeLine: true,
          className: 'source-line-highlight',
          glyphMarginClassName: undefined,
          overviewRuler: {
            color: '#1677ff',
            position: monaco.editor.OverviewRulerLane.Full,
          },
        },
      });
    }

    decorationRef.current = editor.deltaDecorations(decorationRef.current, decorations);
    editor.revealLineInCenter(startLine);
  };

  useEffect(() => {
    if (mapping && editorRef.current) {
      applyLineDecorations();
    }
  }, [mapping]);

  const handleMapping = async () => {
    if (!searchAgentName || !taskUniqueName.trim()) return;
    try {
      const res = await sourceApi.mapping(searchAgentName, taskUniqueName);
      if (res.data.code === 0) {
        setMapping(res.data.data);
        if (res.data.data?.filePath) {
          loadScript(res.data.data.filePath);
        }
      } else {
        message.info('未找到该任务的源码映射');
        setMapping(null);
      }
    } catch (e) {
      message.error('映射查询失败');
      setMapping(null);
    }
  };

  useEffect(() => {
    if (searchAgentName) fetchScripts(searchAgentName);
  }, []);

  const treeData = scripts.map(s => ({
    title: s.fileName,
    key: s.fullPath,
    isLeaf: true,
  }));

  return (
    <Card title="源码对照视图">
      <Space style={{ marginBottom: 16 }} wrap>
        <Input
          placeholder="Agent 名称"
          value={searchAgentName}
          onChange={(e) => setSearchAgentName(e.target.value)}
          onPressEnter={() => fetchScripts(searchAgentName)}
          style={{ width: 200 }}
        />
        <Button type="primary" onClick={() => fetchScripts(searchAgentName)}>加载脚本</Button>

        <Input
          placeholder="taskUniqueName（如 task_analyze）"
          value={taskUniqueName}
          onChange={(e) => setTaskUniqueName(e.target.value)}
          onPressEnter={handleMapping}
          style={{ width: 260 }}
        />
        <Button onClick={handleMapping}>定位源码行</Button>
        {mapping && (
          <span style={{ color: '#52c41a' }}>
            {mapping.filePath}:{mapping.startLine}-{mapping.endLine}
          </span>
        )}
      </Space>

      <Tabs
        tabBarExtraContent={
          <Select
            placeholder="选择脚本"
            value={selectedScript}
            onChange={loadScript}
            style={{ width: 200 }}
            options={scripts.map(s => ({ value: s.fullPath, label: s.fileName }))}
          />
        }
        items={[{
          key: 'browse',
          label: '源码浏览',
          children: (
          <div style={{ display: 'flex', height: 'calc(100vh - 250px)' }}>
            <div style={{ width: 200, borderRight: '1px solid #f0f0f0', overflow: 'auto', flexShrink: 0 }}>
              {loading ? <Spin style={{ margin: 20 }} /> : (
                scripts.length === 0
                  ? <Empty description="请先输入 Agent 名称加载脚本" />
                  : <Tree treeData={treeData} selectedKeys={selectedScript ? [selectedScript] : []} onSelect={(keys) => loadScript(keys[0] as string)} />
              )}
            </div>
            <div style={{ flex: 1, overflow: 'hidden' }}>
              <Spin spinning={contentLoading}>
                <Editor
                  height="100%"
                  language="java"
                  value={sourceContent}
                  theme="vs-dark"
                  onMount={(editor, monaco) => {
                    editorRef.current = editor;
                    monacoRef.current = monaco;
                    if (mapping) applyLineDecorations();
                  }}
                  options={{
                    readOnly: true,
                    lineNumbers: 'on',
                    minimap: { enabled: false },
                    fontSize: 13,
                    scrollBeyondLastLine: false,
                    glyphMargin: true,
                  }}
                />
              </Spin>
            </div>
          </div>
          ),
        }]}
      />
    </Card>
  );
};

export default SourceViewerPage;
