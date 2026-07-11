/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
import http from '@/infra/http';
import { API } from '@/infra/constants';
import { STORAGE_KEYS } from '@/infra/constants';

export interface ChatResponse {
  reply: string;
  sessionId: string;
}

export interface UploadResult {
  filePath: string;
  fileType: string;
  size: number;
}

export interface StreamCallbacks {
  onDelta: (text: string) => void;
  onDone: () => void;
  onError: (error: string) => void;
  onSessionId?: (sessionId: string) => void;
}

/** 支持的对话功能 */
export const CHAT_FUNCTIONS = [
  { key: 'resume', label: '简历诊断', icon: 'FileTextOutlined' },
  { key: 'mock_interview', label: '模拟面试', icon: 'CustomerServiceOutlined' },
  { key: 'interview_analysis', label: '面试分析', icon: 'BarChartOutlined' },
  { key: 'company_intel', label: '公司情报', icon: 'SearchOutlined' },
  { key: 'study_plan', label: '学习计划', icon: 'ScheduleOutlined' },
  { key: 'salary', label: '薪资谈判', icon: 'DollarOutlined' },
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

        while (true) {
          const { done, value } = await reader.read();
          if (done) break;

          buffer += decoder.decode(value, { stream: true });
          const lines = buffer.split('\n');
          buffer = lines.pop() || '';

          let eventType = '';
          for (const line of lines) {
            if (line.startsWith('event:')) {
              eventType = line.slice(6).trim();
            } else if (line.startsWith('data:')) {
              const data = line.slice(5).trim();
              if (eventType === 'delta') {
                callbacks.onDelta(data);
              } else if (eventType === 'session') {
                callbacks.onSessionId?.(data);
              } else if (eventType === 'done') {
                receivedDone = true;
                callbacks.onDone();
                return;
              } else if (eventType === 'error') {
                receivedDone = true;
                callbacks.onError(data);
                return;
              }
            }
          }
        }

        // 流结束但未收到 done 事件 — 视为正常结束（后端可能异常断开）
        if (!receivedDone) {
          callbacks.onDone();
        }
      } catch (err) {
        if (err instanceof DOMException && err.name === 'AbortError') return;
        callbacks.onError(err instanceof Error ? err.message : '连接异常');
      }
    })();

    return controller;
  },

  /** 同步对话（兜底） */
  sendMessageSync: (message: string, sessionId: string | null) =>
    http
      .post<ChatResponse>(API.CHAT_SYNC, { message, sessionId })
      .then((res) => res.data),

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
};
