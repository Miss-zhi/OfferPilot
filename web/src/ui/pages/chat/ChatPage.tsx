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
} from '@ant-design/icons';
import { useAuthStore } from '@/store/auth-store';
import { useChatStore } from '@/store/chat-store';
import type { ChatMessage } from '@/store/chat-store';
import { chatService } from '@/service/chatService';
import { CHAT_FUNCTIONS } from '@/service/chatService';
import { ChatBubble } from '@/ui/components/ChatBubble';
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
};

export function ChatPage() {
  const navigate = useNavigate();
  const logout = useAuthStore((s) => s.logout);
  const username = useAuthStore((s) => s.username);
  const { sessionId, messages, isStreaming, activeFunction, setSessionId, addMessage, appendStreamContent, setStreaming, setActiveFunction, resetSession } = useChatStore();

  const [inputValue, setInputValue] = useState('');
  const [showUploader, setShowUploader] = useState(false);
  const [uploadedFilePath, setUploadedFilePath] = useState<string | null>(null);
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const abortRef = useRef<AbortController | null>(null);

  // 滚动到最新消息
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  // 组件卸载时中止 SSE 连接
  useEffect(() => {
    return () => {
      abortRef.current?.abort();
    };
  }, []);

  const handleSend = useCallback(async () => {
    const text = inputValue.trim();
    if (!text || isStreaming) return;

    const userMessage: ChatMessage = {
      id: `msg-${Date.now()}`,
      role: 'user',
      content: uploadedFilePath ? `[文件: ${uploadedFilePath}]\n${text}` : text,
      timestamp: Date.now(),
    };
    addMessage(userMessage);
    setInputValue('');
    setUploadedFilePath(null);
    setStreaming(true);

    const aiMessageId = `msg-${Date.now() + 1}`;
    addMessage({
      id: aiMessageId,
      role: 'ai',
      content: '',
      timestamp: Date.now(),
    });

    try {
      abortRef.current = chatService.sendMessageStream(
        userMessage.content,
        sessionId,
        {
          onDelta(text) {
            appendStreamContent(aiMessageId, text);
          },
          onDone() {
            setStreaming(false);
          },
          onError(error) {
            message.error(error);
            setStreaming(false);
          },
        },
      );
    } catch {
      message.error('发送失败，请重试');
      setStreaming(false);
    }
  }, [inputValue, isStreaming, sessionId, uploadedFilePath, addMessage, appendStreamContent, setStreaming]);

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

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
      {/* 左侧功能菜单 */}
      <Sider
        width={220}
        style={{ background: '#fff', borderRight: '1px solid #f0f0f0' }}
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
          }}
        >
          OfferPilot
        </div>
        <Menu
          mode="inline"
          selectedKeys={[activeFunction]}
          onClick={({ key }) => setActiveFunction(key)}
          items={CHAT_FUNCTIONS.map((fn) => ({
            key: fn.key,
            icon: iconMap[fn.icon],
            label: fn.label,
          }))}
          style={{ borderRight: 0 }}
        />
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
              onClick={resetSession}
              size="small"
            >
              清空对话
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
          {isStreaming && messages[messages.length - 1]?.role === 'ai' && !messages[messages.length - 1]?.content && (
            <div style={{ padding: '0 16px', color: '#999' }}>AI 思考中...</div>
          )}
          <div ref={messagesEndRef} />
        </Content>

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
