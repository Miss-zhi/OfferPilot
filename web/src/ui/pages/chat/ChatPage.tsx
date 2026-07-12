/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
import { useState, useRef, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { Layout, Menu, Input, Button, Space, message } from 'antd';
import {
  FileTextOutlined,
  CustomerServiceOutlined,
  BarChartOutlined,
  SearchOutlined,
  ScheduleOutlined,
  DollarOutlined,
  SendOutlined,
  UploadOutlined,
  LogoutOutlined,
  ClearOutlined,
  SettingOutlined,
  AppstoreOutlined,
  DatabaseOutlined,
} from '@ant-design/icons';
import { useAuthStore } from '@/store/auth-store';
import { useChatStore } from '@/store/chat-store';
import type { ChatMessage } from '@/store/chat-store';
import { chatService } from '@/service/chatService';
import { CHAT_FUNCTIONS } from '@/service/chatService';
import type { ConfirmRequiredData, ConfirmItem } from '@/service/chatService';
import { ChatBubble } from '@/ui/components/ChatBubble';
import { SessionList } from '@/ui/components/SessionList';
import { FileUploader } from '@/ui/components/FileUploader';

const { Sider, Content, Header } = Layout;
const { TextArea } = Input;

const iconMap: Record<string, React.ReactNode> = {
  FileTextOutlined: <FileTextOutlined />,
  CustomerServiceOutlined: <CustomerServiceOutlined />,
  BarChartOutlined: <BarChartOutlined />,
  SearchOutlined: <SearchOutlined />,
  ScheduleOutlined: <ScheduleOutlined />,
  DollarOutlined: <DollarOutlined />,
  DatabaseOutlined: <DatabaseOutlined />,
};

export function ChatPage() {
  const navigate = useNavigate();
  const logout = useAuthStore((s) => s.logout);
  const username = useAuthStore((s) => s.username);
  const role = useAuthStore((s) => s.role);
  const { sessionId, messages, isStreaming, activeFunction, pendingConfirmation, setSessionId, addMessage, appendStreamContent, appendThinkingContent, setToolCall, addToolCall, setStreaming, setActiveFunction, setPendingConfirmation, createNewSession, loadSessions } = useChatStore();

  const [inputValue, setInputValue] = useState('');
  const [showUploader, setShowUploader] = useState(false);
  const [uploadedFilePath, setUploadedFilePath] = useState<string | null>(null);
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const abortRef = useRef<AbortController | null>(null);
  const currentToolRef = useRef<string | null>(null);
  const aiMessageIdRef = useRef<string | null>(null); // 当前流式 AI 消息 ID，HITL 确认后复用

  // 滚动到最新消息
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  // 组件挂载时加载会话列表
  useEffect(() => {
    loadSessions();
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  // 组件卸载时中止 SSE 连接
  useEffect(() => {
    return () => {
      abortRef.current?.abort();
    };
  }, []);

  const handleSend = useCallback(async () => {
    const text = inputValue.trim();
    if (!text || isStreaming) return;

    // 无会话时先创建
    let effectiveSessionId = sessionId;
    if (!effectiveSessionId) {
      try {
        effectiveSessionId = await createNewSession();
      } catch {
        message.error('创建会话失败');
        return;
      }
    }
    // 闭包捕获 sessionId 防止并发切换
    const capturedSessionId = effectiveSessionId;

    const userMessage: ChatMessage = {
      id: `msg-${Date.now()}`,
      role: 'user',
      content: uploadedFilePath ? `[文件: ${uploadedFilePath}]\n${text}` : text,
      thinkingContent: '',
      currentToolCall: null,
      toolCalls: [],
      timestamp: Date.now(),
    };
    addMessage(userMessage);
    setInputValue('');
    setUploadedFilePath(null);
    setStreaming(true);

    const aiMessageId = `msg-${Date.now() + 1}`;
    aiMessageIdRef.current = aiMessageId;
    addMessage({
      id: aiMessageId,
      role: 'ai',
      content: '',
      thinkingContent: '',
      currentToolCall: null,
      toolCalls: [],
      timestamp: Date.now(),
    });

    try {
      abortRef.current = chatService.sendMessageStream(
        userMessage.content,
        capturedSessionId,
        {
          onDelta(text) {
            appendStreamContent(aiMessageId, text);
          },
          onThinking(text) {
            appendThinkingContent(aiMessageId, text);
          },
          onThinkingStart() {
            // 思考开始 — 无需额外操作，ChatBubble 会根据 thinkingContent 自动展示
          },
          onThinkingEnd() {
            // 思考结束 — 无需额外操作
          },
          onToolCall(toolName) {
            currentToolRef.current = toolName;
            setToolCall(aiMessageId, toolName);
          },
          onToolCallEnd() {
            const tool = currentToolRef.current;
            if (tool) {
              addToolCall(aiMessageId, tool);
              currentToolRef.current = null;
            }
          },
          onSessionId(id) {
            setSessionId(id);
          },
          onConfirmRequired(data: ConfirmRequiredData) {
            setPendingConfirmation({
              sessionId: data.sessionId,
              aiMessageId,
              toolCalls: data.toolCalls,
            });
          },
          onDone() {
            currentToolRef.current = null;
            setToolCall(aiMessageId, null); // 确保清除工具状态
            setStreaming(false);
          },
          onError(error) {
            message.error(error);
            currentToolRef.current = null;
            setToolCall(aiMessageId, null);
            setStreaming(false);
          },
        },
      );
    } catch {
      message.error('发送失败，请重试');
      setStreaming(false);
    }
  }, [inputValue, isStreaming, sessionId, uploadedFilePath, addMessage, appendStreamContent, appendThinkingContent, setToolCall, addToolCall, setStreaming, setSessionId, setPendingConfirmation, createNewSession]);

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  /** HITL 确认 — 用户批准或拒绝工具调用后恢复 Agent 流 */
  const handleConfirm = useCallback((confirmed: boolean) => {
    const pc = pendingConfirmation;
    if (!pc) return;

    setPendingConfirmation(null);

    const confirmations: ConfirmItem[] = pc.toolCalls.map((tc) => ({
      toolCallId: tc.id,
      toolCallName: tc.name,
      toolCallInput: tc.input,
      confirmed,
    }));

    abortRef.current = chatService.confirmTools(
      pc.sessionId,
      confirmations,
      {
        onDelta(text) { appendStreamContent(pc.aiMessageId, text); },
        onThinking(text) { appendThinkingContent(pc.aiMessageId, text); },
        onThinkingStart() {},
        onThinkingEnd() {},
        onToolCall(toolName) {
          currentToolRef.current = toolName;
          setToolCall(pc.aiMessageId, toolName);
        },
        onToolCallEnd() {
          const tool = currentToolRef.current;
          if (tool) {
            addToolCall(pc.aiMessageId, tool);
            currentToolRef.current = null;
          }
        },
        onConfirmRequired(data: ConfirmRequiredData) {
          // 嵌套 HITL — 再次暂停等待确认
          setPendingConfirmation({
            sessionId: data.sessionId,
            aiMessageId: pc.aiMessageId,
            toolCalls: data.toolCalls,
          });
        },
        onDone() {
          currentToolRef.current = null;
          setToolCall(pc.aiMessageId, null);
          setStreaming(false);
        },
        onError(error) {
          message.error(error);
          currentToolRef.current = null;
          setToolCall(pc.aiMessageId, null);
          setStreaming(false);
        },
      },
    );
  }, [pendingConfirmation, appendStreamContent, appendThinkingContent, setToolCall, addToolCall, setStreaming, setPendingConfirmation]);

  const handleFileUploaded = (filePath: string, _fileName: string) => {
    setUploadedFilePath(filePath);
    setShowUploader(false);
  };

  const handleLogout = () => {
    logout();
    navigate('/login', { replace: true });
  };

  return (
    <Layout style={{ minHeight: '100vh' }}>
      {/* 左侧：会话列表 + 功能菜单 */}
      <Sider
        width={260}
        style={{ background: '#fff', borderRight: '1px solid #f0f0f0', display: 'flex', flexDirection: 'column' }}
      >
        <div
          style={{
            height: 48,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            fontWeight: 600,
            fontSize: 16,
            borderBottom: '1px solid #f0f0f0',
            flexShrink: 0,
          }}
        >
          OfferPilot
        </div>
        {/* 会话列表 */}
        <div style={{ flex: 1, overflow: 'hidden' }}>
          <SessionList />
        </div>
        {/* 底部功能导航 */}
        <div style={{ borderTop: '1px solid #f0f0f0', flexShrink: 0 }}>
          <Menu
            mode="inline"
            selectedKeys={[activeFunction]}
            onClick={({ key }) => {
              if (key === 'nav-models') {
                navigate('/admin/models');
              } else if (key === 'nav-knowledge') {
                navigate('/admin/knowledge');
              } else if (key === 'nav-settings') {
                navigate('/settings');
              } else {
                setActiveFunction(key);
              }
            }}
            items={[
              ...CHAT_FUNCTIONS.map((fn) => ({
                key: fn.key,
                icon: iconMap[fn.icon],
                label: fn.label,
              })),
              { type: 'divider' },
              {
                key: 'nav-knowledge',
                icon: <DatabaseOutlined />,
                label: '知识库管理',
              },
              ...(role === 'ADMIN'
                ? [
                    {
                      key: 'nav-models',
                      icon: <AppstoreOutlined />,
                      label: '模型管理',
                    } as const,
                  ]
                : []),
              {
                key: 'nav-settings',
                icon: <SettingOutlined />,
                label: '个人设置',
              },
            ]}
            style={{ borderRight: 0 }}
          />
        </div>
      </Sider>

      <Layout>
        {/* 顶部栏 */}
        <Header
          style={{
            background: '#fff',
            padding: '0 24px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            borderBottom: '1px solid #f0f0f0',
            height: 48,
          }}
        >
          <span style={{ fontWeight: 500 }}>
            {CHAT_FUNCTIONS.find((f) => f.key === activeFunction)?.label}
          </span>
          <Space>
            <span style={{ color: '#999', fontSize: 13 }}>{username}</span>
            <Button
              type="text"
              icon={<ClearOutlined />}
              onClick={() => createNewSession()}
              size="small"
            >
              新建对话
            </Button>
            <Button
              type="text"
              icon={<LogoutOutlined />}
              onClick={handleLogout}
              size="small"
            >
              退出
            </Button>
          </Space>
        </Header>

        {/* 消息区域 */}
        <Content
          style={{
            padding: 16,
            overflowY: 'auto',
            flex: 1,
            background: '#fafafa',
          }}
        >
          {messages.length === 0 && (
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                height: '100%',
                color: '#bbb',
                fontSize: 16,
              }}
            >
              开始你的求职之旅...
            </div>
          )}
          {messages.map((msg) => (
            <ChatBubble key={msg.id} message={msg} />
          ))}
          {isStreaming && messages[messages.length - 1]?.role === 'ai' && !messages[messages.length - 1]?.content && !messages[messages.length - 1]?.thinkingContent && !messages[messages.length - 1]?.currentToolCall && (
            <div style={{ padding: '0 16px', color: '#999' }}>AI 思考中...</div>
          )}
          <div ref={messagesEndRef} />
        </Content>

        {/* HITL 确认栏 */}
        {pendingConfirmation && (
          <div
            style={{
              padding: '12px 16px',
              borderTop: '2px solid #faad14',
              borderBottom: '1px solid #f0f0f0',
              background: '#fffbe6',
            }}
          >
            <div style={{ display: 'flex', alignItems: 'flex-start', gap: 8 }}>
              <span style={{ fontSize: 16, lineHeight: '24px' }}>⚠️</span>
              <div style={{ flex: 1 }}>
                <div style={{ fontWeight: 600, marginBottom: 4, fontSize: 14 }}>
                  以下工具需要您的确认才能继续：
                </div>
                <div style={{ display: 'flex', flexWrap: 'wrap', gap: 4, marginBottom: 8 }}>
                  {pendingConfirmation.toolCalls.map((tc, i) => (
                    <span
                      key={tc.id || i}
                      style={{
                        display: 'inline-block',
                        padding: '2px 8px',
                        background: '#fff',
                        border: '1px solid #d9d9d9',
                        borderRadius: 4,
                        fontSize: 12,
                        color: '#595959',
                      }}
                    >
                      {tc.name}
                      {tc.input?.url ? ` → ${(tc.input.url as string).slice(0, 50)}…` : ''}
                    </span>
                  ))}
                </div>
                <Space>
                  <Button
                    type="primary"
                    size="small"
                    onClick={() => handleConfirm(true)}
                  >
                    批准
                  </Button>
                  <Button
                    danger
                    size="small"
                    onClick={() => handleConfirm(false)}
                  >
                    拒绝
                  </Button>
                </Space>
              </div>
            </div>
          </div>
        )}

        {/* 输入区域 */}
        <div
          style={{
            padding: '12px 16px',
            borderTop: '1px solid #f0f0f0',
            background: '#fff',
          }}
        >
          {showUploader && (
            <div style={{ marginBottom: 12 }}>
              <FileUploader
                onUploaded={handleFileUploaded}
                uploadFn={(file) => chatService.uploadFile(file, 'general')}
                disabled={isStreaming}
              />
            </div>
          )}
          {uploadedFilePath && (
            <div style={{ marginBottom: 8, color: '#1677ff', fontSize: 13 }}>
              已上传: {uploadedFilePath}
              <Button
                type="link"
                size="small"
                onClick={() => setUploadedFilePath(null)}
              >
                移除
              </Button>
            </div>
          )}
          <div style={{ display: 'flex', gap: 8, alignItems: 'flex-end' }}>
            <Button
              icon={<UploadOutlined />}
              onClick={() => setShowUploader(!showUploader)}
              disabled={isStreaming}
            />
            <TextArea
              value={inputValue}
              onChange={(e) => setInputValue(e.target.value)}
              onKeyDown={handleKeyDown}
              placeholder="输入消息，Enter 发送，Shift+Enter 换行"
              autoSize={{ minRows: 1, maxRows: 5 }}
              disabled={isStreaming}
              style={{ flex: 1 }}
            />
            <Button
              type="primary"
              icon={<SendOutlined />}
              onClick={handleSend}
              loading={isStreaming}
              disabled={!inputValue.trim()}
            >
              发送
            </Button>
          </div>
        </div>
      </Layout>
    </Layout>
  );
}
