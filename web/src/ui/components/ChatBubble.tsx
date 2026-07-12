/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
import { useState } from 'react';
import { Typography, Tag } from 'antd';
import { BulbOutlined, DownOutlined, UpOutlined } from '@ant-design/icons';
import type { ChatMessage } from '@/store/chat-store';
import { MarkdownRenderer } from './MarkdownRenderer';

const { Text } = Typography;

/** 工具名称 → 中文摘要映射 */
const TOOL_LABEL_MAP: Record<string, string> = {
  smart_search: '正在综合搜索知识库…',
  search_questions: '正在搜索面试题库…',
  search_answers: '正在搜索优秀答案…',
  search_resources: '正在搜索学习资源…',
  search: '正在联网搜索最新信息…',
  parse_resume: '正在解析简历文件…',
  evaluate_resume: '正在评估简历质量…',
  generate_next_question: '正在生成面试题目…',
  analyze_answer: '正在分析回答质量…',
  transcribe_audio: '正在转写录音文件…',
};

interface ChatBubbleProps {
  message: ChatMessage;
}

export function ChatBubble({ message }: ChatBubbleProps) {
  const isUser = message.role === 'user';
  const [thinkingExpanded, setThinkingExpanded] = useState(false);

  const hasThinking = message.thinkingContent.length > 0;
  const hasToolCalls = message.toolCalls.length > 0;
  const isToolRunning = message.currentToolCall !== null;

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
          <>
            {/* 思考过程可折叠区域 */}
            {hasThinking && (
              <div style={{ marginBottom: 8 }}>
                <div
                  onClick={() => setThinkingExpanded(!thinkingExpanded)}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 6,
                    cursor: 'pointer',
                    color: '#8c8c8c',
                    fontSize: 13,
                    userSelect: 'none',
                    padding: '4px 0',
                  }}
                >
                  <BulbOutlined />
                  <span>思考过程{hasToolCalls ? `（${message.toolCalls.length} 次工具调用）` : ''}</span>
                  {thinkingExpanded ? <UpOutlined style={{ fontSize: 10 }} /> : <DownOutlined style={{ fontSize: 10 }} />}
                </div>
                {thinkingExpanded && (
                  <div
                    style={{
                      maxHeight: 300,
                      overflowY: 'auto',
                      padding: '8px 12px',
                      margin: '4px 0',
                      background: 'rgba(0,0,0,0.04)',
                      borderRadius: 8,
                      fontSize: 12,
                      color: '#595959',
                      lineHeight: 1.6,
                      whiteSpace: 'pre-wrap',
                    }}
                  >
                    {message.thinkingContent}
                  </div>
                )}
              </div>
            )}

            {/* 工具调用状态 Tag */}
            {isToolRunning && (
              <div style={{ marginBottom: 8 }}>
                <Tag color="processing" icon={<span>🔍</span>}>
                  {TOOL_LABEL_MAP[message.currentToolCall!] ?? `正在执行 ${message.currentToolCall}…`}
                </Tag>
              </div>
            )}

            {/* 已完成工具调用链 */}
            {hasToolCalls && !isToolRunning && !thinkingExpanded && (
              <div style={{ marginBottom: 8, display: 'flex', flexWrap: 'wrap', gap: 4 }}>
                {message.toolCalls.map((tool, i) => (
                  <Tag key={i} color="success" style={{ fontSize: 11 }}>
                    {TOOL_LABEL_MAP[tool]?.replace('正在', '已完成').replace('…', '') ?? tool}
                  </Tag>
                ))}
              </div>
            )}

            {/* 正文 */}
            {message.content ? (
              <MarkdownRenderer content={message.content} />
            ) : (
              hasThinking && !thinkingExpanded && (
                <Text type="secondary" style={{ fontSize: 13 }}>正在思考中…</Text>
              )
            )}
          </>
        )}
      </div>
      <Text type="secondary" style={{ fontSize: 11, marginTop: 2 }}>
        {new Date(message.timestamp).toLocaleTimeString()}
      </Text>
    </div>
  );
}
