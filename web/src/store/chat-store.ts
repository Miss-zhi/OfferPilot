/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
import { create } from 'zustand';
import type { ToolCallForConfirm, SessionListItem, ChatMessageFromServer } from '@/service/chatService';
import { chatService } from '@/service/chatService';

/** 思考内容最大字符数上限，超出后停止追加 */
const MAX_THINKING_CHARS = 10000;

/** 待用户确认的 HITL 信息 */
export interface PendingConfirmation {
  sessionId: string;
  aiMessageId: string;
  toolCalls: ToolCallForConfirm[];
}

export interface ChatMessage {
  id: string;
  role: 'user' | 'ai';
  content: string;
  thinkingContent: string;
  currentToolCall: string | null;
  toolCalls: string[];
  timestamp: number;
}

export interface ChatState {
  sessionId: string | null;
  messages: ChatMessage[];
  isStreaming: boolean;
  activeFunction: string;
  pendingConfirmation: PendingConfirmation | null;
  /** 会话列表 */
  sessions: SessionListItem[];
  /** 当前活跃会话 ID */
  currentSessionId: string | null;

  setSessionId: (id: string) => void;
  addMessage: (msg: ChatMessage) => void;
  appendStreamContent: (msgId: string, chunk: string) => void;
  appendThinkingContent: (msgId: string, chunk: string) => void;
  setToolCall: (msgId: string, toolName: string | null) => void;
  addToolCall: (msgId: string, toolName: string) => void;
  setStreaming: (streaming: boolean) => void;
  setActiveFunction: (fn: string) => void;
  setPendingConfirmation: (pc: PendingConfirmation | null) => void;
  resetSession: () => void;
  clearMessages: () => void;
  /** 加载会话列表 */
  loadSessions: () => Promise<void>;
  /** 切换会话并加载历史消息 */
  switchSession: (sessionId: string) => Promise<void>;
  /** 创建新会话 */
  createNewSession: () => Promise<string>;
  /** 删除会话 */
  deleteSession: (sessionId: string) => Promise<void>;
  /** 重命名会话 */
  renameSession: (sessionId: string, title: string) => Promise<void>;
}

export const useChatStore = create<ChatState>((set, get) => ({
  sessionId: null,
  messages: [],
  isStreaming: false,
  activeFunction: 'mock_interview',
  pendingConfirmation: null,
  sessions: [],
  currentSessionId: null,

  setSessionId: (id) => set({ sessionId: id }),

  addMessage: (msg) =>
    set((state) => ({ messages: [...state.messages, msg] })),

  appendStreamContent: (msgId, chunk) =>
    set((state) => ({
      messages: state.messages.map((m) =>
        m.id === msgId ? { ...m, content: m.content + chunk } : m,
      ),
    })),

  appendThinkingContent: (msgId, chunk) =>
    set((state) => ({
      messages: state.messages.map((m) => {
        if (m.id !== msgId) return m;
        if (m.thinkingContent.length >= MAX_THINKING_CHARS) return m;
        return { ...m, thinkingContent: m.thinkingContent + chunk };
      }),
    })),

  setToolCall: (msgId, toolName) =>
    set((state) => ({
      messages: state.messages.map((m) =>
        m.id === msgId ? { ...m, currentToolCall: toolName } : m,
      ),
    })),

  addToolCall: (msgId, toolName) =>
    set((state) => ({
      messages: state.messages.map((m) =>
        m.id === msgId ? { ...m, toolCalls: [...m.toolCalls, toolName], currentToolCall: null } : m,
      ),
    })),

  setStreaming: (streaming) => set({ isStreaming: streaming }),

  setActiveFunction: (fn) => set({ activeFunction: fn }),

  setPendingConfirmation: (pc) => set({ pendingConfirmation: pc }),

  resetSession: () =>
    set((state) => ({
      sessionId: null,
      currentSessionId: null,
      messages: [],
      isStreaming: false,
      pendingConfirmation: null,
    })),

  clearMessages: () => set({ messages: [] }),

  // ============================================================
  // 会话历史管理 actions
  // ============================================================

  loadSessions: async () => {
    try {
      const sessions = await chatService.listSessions();
      set({ sessions });
    } catch {
      // 静默失败，会话列表为空
    }
  },

  switchSession: async (sessionId) => {
    try {
      const serverMessages = await chatService.getMessages(sessionId);
      const messages: ChatMessage[] = serverMessages.map((m) => ({
        id: `msg-hist-${m.id}`,
        role: m.role.toLowerCase() as 'user' | 'ai',
        content: m.content,
        thinkingContent: m.thinkingContent || '',
        currentToolCall: null,
        toolCalls: m.toolCalls ? JSON.parse(m.toolCalls) : [],
        timestamp: new Date(m.createdAt).getTime(),
      }));
      set({
        sessionId,
        currentSessionId: sessionId,
        messages,
        pendingConfirmation: null,
      });
    } catch {
      // 加载失败，保持当前状态
    }
  },

  createNewSession: async () => {
    const { activeFunction } = get();
    const session = await chatService.createSession(activeFunction);
    set({
      sessionId: session.sessionId,
      currentSessionId: session.sessionId,
      messages: [],
      isStreaming: false,
      pendingConfirmation: null,
    });
    // 刷新列表
    const sessions = await chatService.listSessions();
    set({ sessions });
    return session.sessionId;
  },

  deleteSession: async (sessionId) => {
    await chatService.deleteSession(sessionId);
    const { currentSessionId } = get();
    if (currentSessionId === sessionId) {
      set({
        sessionId: null,
        currentSessionId: null,
        messages: [],
      });
    }
    const sessions = await chatService.listSessions();
    set({ sessions });
  },

  renameSession: async (sessionId, title) => {
    await chatService.renameSession(sessionId, title);
    set((state) => ({
      sessions: state.sessions.map((s) =>
        s.sessionId === sessionId ? { ...s, title } : s,
      ),
    }));
  },
}));
