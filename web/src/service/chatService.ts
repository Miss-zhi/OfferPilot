/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
import http from '@/infra/http';
import { API } from '@/infra/constants';
import { STORAGE_KEYS } from '@/infra/constants';

// ============================================================
// 现有类型
// ============================================================

export interface ChatResponse {
  reply: string;
  sessionId: string;
}

export interface UploadResult {
  filePath: string;
  fileType: string;
  size: number;
}

/** 后端 confirm_required 事件中的单个工具调用 */
export interface ToolCallForConfirm {
  id: string;
  name: string;
  input: Record<string, unknown>;
}

/** 后端 confirm_required 事件数据 */
export interface ConfirmRequiredData {
  sessionId: string;
  toolCalls: ToolCallForConfirm[];
}

/** POST /confirm 请求体中的单条确认 */
export interface ConfirmItem {
  toolCallId: string;
  toolCallName: string;
  toolCallInput: Record<string, unknown>;
  confirmed: boolean;
}

// ============================================================
// 会话历史类型
// ============================================================

/** 会话列表项 */
export interface SessionListItem {
  sessionId: string;
  title: string;
  activeFunction: string;
  messageCount: number;
  createdAt: string;
  updatedAt: string;
}

/** 后端返回的消息实体 */
export interface ChatMessageFromServer {
  id: number;
  sessionId: string;
  role: string;
  content: string;
  thinkingContent: string | null;
  toolCalls: string | null;
  seq: number;
  createdAt: string;
}

/** 搜索结果项 */
export interface SearchResultItem {
  sessionId: string;
  sessionTitle: string;
  matchSnippet: string;
  matchCount: number;
}

export interface StreamCallbacks {
  onDelta: (text: string) => void;
  onThinking?: (text: string) => void;
  onThinkingStart?: () => void;
  onThinkingEnd?: () => void;
  onToolCall?: (toolName: string) => void;
  onToolCallEnd?: () => void;
  onConfirmRequired?: (data: ConfirmRequiredData) => void;
  onDone: () => void;
  onError: (error: string) => void;
  onSessionId?: (sessionId: string) => void;
}

/** 支持的对话功能 */
export const CHAT_FUNCTIONS = [
  { key: 'resume', label: '简历诊断', icon: 'FileTextOutlined' },
  { key: 'mock_interview', label: '模拟面试', icon: 'CustomerServiceOutlined' },
  { key: 'interview_analysis', label: '面试分析', icon: 'BarChartOutlined' },
] as const;

const getToken = () => localStorage.getItem(STORAGE_KEYS.TOKEN);

export const chatService = {
  /** SSE 流式对话 — 使用原生 fetch + ReadableStream，无自动重连 */
  sendMessageStream: (
    message: string,
    sessionId: string | null,
    callbacks: StreamCallbacks,
  ) => {
    const controller = new AbortController();
    let receivedDone = false;
    /** 无活动超时计时器 — 防止后端未发 done 导致 isStreaming 永久卡死 */
    let inactivityTimer: ReturnType<typeof setTimeout> | null = null;
    const INACTIVITY_TIMEOUT_MS = 120_000; // 2 分钟无事件则视为异常

    const resetInactivityTimer = () => {
      if (inactivityTimer) clearTimeout(inactivityTimer);
      if (!receivedDone) {
        inactivityTimer = setTimeout(() => {
          if (!receivedDone) {
            controller.abort(); // 触发 AbortError → catch 静默忽略
            callbacks.onDone();
          }
        }, INACTIVITY_TIMEOUT_MS);
      }
    };

    /** 处理单个 SSE 事件 */
    const handleEvent = (eventType: string, data: string) => {
      switch (eventType) {
        case 'delta':
          callbacks.onDelta(data);
          break;
        case 'thinking':
          callbacks.onThinking?.(data);
          break;
        case 'thinking_start':
          callbacks.onThinkingStart?.();
          break;
        case 'thinking_end':
          callbacks.onThinkingEnd?.();
          break;
        case 'tool_call':
          callbacks.onToolCall?.(data);
          break;
        case 'tool_call_end':
          callbacks.onToolCallEnd?.();
          break;
        case 'session':
          callbacks.onSessionId?.(data);
          break;
        case 'confirm_required':
          receivedDone = true;
          callbacks.onConfirmRequired?.(JSON.parse(data));
          break;
        case 'done':
          receivedDone = true;
          callbacks.onDone();
          break;
        case 'error':
          receivedDone = true;
          callbacks.onError(data);
          break;
      }
    };
    
    /** 解析 buffer 中的残留的 SSE 事件（流结束后调用） */
    const processBuffer = (remaining: string) => {
      if (!remaining.trim()) return;
      let eventType = '';
      const lines = remaining.split('\n');
      for (const line of lines) {
        if (line.startsWith('event:')) {
          eventType = line.slice(6).trim();
        } else if (line.startsWith('data:')) {
          const data = line.slice(5).trim();
          handleEvent(eventType, data);
        }
      }
    };

    (async () => {
      try {
        const response = await fetch(`/api${API.CHAT_STREAM}`, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            ...(getToken() ? { Authorization: `Bearer ${getToken()}` } : {}),
          },
          body: JSON.stringify({ message, sessionId }),
          signal: controller.signal,
        });

        if (!response.ok) {
          // 尝试解析后端返回的错误信息
          let errMsg = `HTTP ${response.status}`;
          try {
            const errBody = await response.json();
            if (errBody?.message) errMsg = errBody.message;
          } catch {
            // 无法解析 JSON — 使用默认错误信息
          }
          callbacks.onError(errMsg);
          return;
        }

        const reader = response.body?.getReader();
        if (!reader) {
          callbacks.onError('无法读取响应流');
          return;
        }

        const decoder = new TextDecoder();
        let buffer = '';
        resetInactivityTimer();

        while (true) {
          const { done, value } = await reader.read();
          if (done) break;

          resetInactivityTimer(); // 每收到数据重置超时
          buffer += decoder.decode(value, { stream: true });
          const lines = buffer.split('\n');
          buffer = lines.pop() || '';

          let eventType = '';
          for (const line of lines) {
            if (line.startsWith('event:')) {
              eventType = line.slice(6).trim();
            } else if (line.startsWith('data:')) {
              const data = line.slice(5).trim();
              handleEvent(eventType, data);
              if (eventType === 'done' || eventType === 'error' || eventType === 'confirm_required') {
                if (inactivityTimer) clearTimeout(inactivityTimer);
                return;
              }
            }
          }
        }

        // 流结束 — 先处理 buffer 中可能残留的最后一个事件（无尾部换行的情况）
        if (!receivedDone) {
          processBuffer(buffer);
        }
        // 兜底：buffer 中也无 done 事件 — 视为后端异常断开，正常结束
        if (!receivedDone) {
          callbacks.onDone();
        }
      } catch (err) {
        if (err instanceof DOMException && err.name === 'AbortError') return;
        callbacks.onError(err instanceof Error ? err.message : '连接异常');
      } finally {
        if (inactivityTimer) clearTimeout(inactivityTimer);
      }
    })();

    return controller;
  },

  /** 同步对话（兜底） */
  sendMessageSync: (message: string, sessionId: string | null) =>
    http
      .post<ChatResponse>(API.CHAT_SYNC, { message, sessionId })
      .then((res) => res.data),

  /** HITL 确认 — 发送用户确认结果，恢复 Agent SSE 流 */
  confirmTools: (
    sessionId: string,
    confirmations: ConfirmItem[],
    callbacks: StreamCallbacks,
  ) => {
    const controller = new AbortController();
    let receivedDone = false;
    let inactivityTimer: ReturnType<typeof setTimeout> | null = null;
    const INACTIVITY_TIMEOUT_MS = 120_000;

    const resetInactivityTimer = () => {
      if (inactivityTimer) clearTimeout(inactivityTimer);
      if (!receivedDone) {
        inactivityTimer = setTimeout(() => {
          if (!receivedDone) {
            controller.abort();
            callbacks.onDone();
          }
        }, INACTIVITY_TIMEOUT_MS);
      }
    };

    const handleEvent = (eventType: string, data: string) => {
      switch (eventType) {
        case 'delta':
          callbacks.onDelta(data);
          break;
        case 'thinking':
          callbacks.onThinking?.(data);
          break;
        case 'thinking_start':
          callbacks.onThinkingStart?.();
          break;
        case 'thinking_end':
          callbacks.onThinkingEnd?.();
          break;
        case 'tool_call':
          callbacks.onToolCall?.(data);
          break;
        case 'tool_call_end':
          callbacks.onToolCallEnd?.();
          break;
        case 'session':
          callbacks.onSessionId?.(data);
          break;
        case 'confirm_required':
          receivedDone = true;
          callbacks.onConfirmRequired?.(JSON.parse(data));
          break;
        case 'done':
          receivedDone = true;
          callbacks.onDone();
          break;
        case 'error':
          receivedDone = true;
          callbacks.onError(data);
          break;
      }
    };

    const processBuffer = (remaining: string) => {
      if (!remaining.trim()) return;
      let eventType = '';
      const lines = remaining.split('\n');
      for (const line of lines) {
        if (line.startsWith('event:')) {
          eventType = line.slice(6).trim();
        } else if (line.startsWith('data:')) {
          const data = line.slice(5).trim();
          handleEvent(eventType, data);
        }
      }
    };

    (async () => {
      try {
        const response = await fetch(`/api${API.CHAT_CONFIRM}`, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            ...(getToken() ? { Authorization: `Bearer ${getToken()}` } : {}),
          },
          body: JSON.stringify({ sessionId, confirmations }),
          signal: controller.signal,
        });

        if (!response.ok) {
          let errMsg = `HTTP ${response.status}`;
          try {
            const errBody = await response.json();
            if (errBody?.message) errMsg = errBody.message;
          } catch {
            // 无法解析 JSON — 使用默认错误信息
          }
          callbacks.onError(errMsg);
          return;
        }

        const reader = response.body?.getReader();
        if (!reader) {
          callbacks.onError('无法读取响应流');
          return;
        }

        const decoder = new TextDecoder();
        let buffer = '';
        resetInactivityTimer();

        while (true) {
          const { done, value } = await reader.read();
          if (done) break;

          resetInactivityTimer();
          buffer += decoder.decode(value, { stream: true });
          const lines = buffer.split('\n');
          buffer = lines.pop() || '';

          let eventType = '';
          for (const line of lines) {
            if (line.startsWith('event:')) {
              eventType = line.slice(6).trim();
            } else if (line.startsWith('data:')) {
              const data = line.slice(5).trim();
              handleEvent(eventType, data);
              if (eventType === 'done' || eventType === 'error' || eventType === 'confirm_required') {
                if (inactivityTimer) clearTimeout(inactivityTimer);
                return;
              }
            }
          }
        }

        if (!receivedDone) {
          processBuffer(buffer);
        }
        if (!receivedDone) {
          callbacks.onDone();
        }
      } catch (err) {
        if (err instanceof DOMException && err.name === 'AbortError') return;
        callbacks.onError(err instanceof Error ? err.message : '连接异常');
      } finally {
        if (inactivityTimer) clearTimeout(inactivityTimer);
      }
    })();

    return controller;
  },

  /** 上传文件 */
  uploadFile: (file: File, type: string) => {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('type', type);
    return http
      .post<UploadResult>(API.UPLOAD, formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      })
      .then((res) => res.data);
  },

  // ============================================================
  // 会话历史管理
  // ============================================================

  /** 获取当前用户所有会话 */
  listSessions: () =>
    http.get<SessionListItem[]>(API.CHAT_SESSIONS).then((res) => res.data),

  /** 创建新会话 */
  createSession: (activeFunction: string) =>
    http.post<SessionListItem>(API.CHAT_SESSIONS, { activeFunction }).then((res) => res.data),

  /** 删除会话 */
  deleteSession: (sessionId: string) =>
    http.delete<void>(API.CHAT_SESSION(sessionId)),

  /** 重命名会话 */
  renameSession: (sessionId: string, title: string) =>
    http.patch<void>(API.CHAT_SESSION(sessionId), { title }),

  /** 获取会话全部消息 */
  getMessages: (sessionId: string) =>
    http.get<ChatMessageFromServer[]>(API.CHAT_SESSION_MESSAGES(sessionId)).then((res) => res.data),

  /** 保存一条消息 */
  saveMessage: (sessionId: string, data: {
    role: string;
    content: string;
    thinkingContent?: string;
    toolCalls?: string;
  }) =>
    http.post<ChatMessageFromServer>(API.CHAT_SESSION_MESSAGES(sessionId), data).then((res) => res.data),

  /** 全文搜索 */
  searchSessions: (keyword: string) =>
    http.get<SearchResultItem[]>(API.CHAT_SESSION_SEARCH, { params: { q: keyword } }).then((res) => res.data),
};
