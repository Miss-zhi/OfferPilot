/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
import { Typography } from 'antd';
import type { ChatMessage } from '@/store/chat-store';
import { MarkdownRenderer } from './MarkdownRenderer';

const { Text } = Typography;

interface ChatBubbleProps {
  message: ChatMessage;
}

export function ChatBubble({ message }: ChatBubbleProps) {
  const isUser = message.role === 'user';

  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        alignItems: isUser ? 'flex-end' : 'flex-start',
        marginBottom: 16,
        padding: '0 16px',
      }}
    >
      <Text type="secondary" style={{ fontSize: 12, marginBottom: 4 }}>
        {isUser ? '我' : 'AI 助手'}
      </Text>
      <div
        style={{
          maxWidth: '75%',
          padding: '10px 16px',
          borderRadius: 12,
          background: isUser ? '#1677ff' : '#f0f2f5',
          color: isUser ? '#fff' : '#000',
          wordBreak: 'break-word',
        }}
      >
        {isUser ? (
          <span style={{ whiteSpace: 'pre-wrap' }}>{message.content}</span>
        ) : (
          <MarkdownRenderer content={message.content} />
        )}
      </div>
      <Text type="secondary" style={{ fontSize: 11, marginTop: 2 }}>
        {new Date(message.timestamp).toLocaleTimeString()}
      </Text>
    </div>
  );
}
