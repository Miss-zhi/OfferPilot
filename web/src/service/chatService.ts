/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
import { fetchEventSource } from '@microsoft/fetch-event-source';
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
  /** SSE 流式对话 */
  sendMessageStream: (
    message: string,
    sessionId: string | null,
    callbacks: StreamCallbacks,
  ) => {
    const controller = new AbortController();
    fetchEventSource(`/api${API.CHAT_STREAM}`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(getToken() ? { Authorization: `Bearer ${getToken()}` } : {}),
      },
      body: JSON.stringify({ message, sessionId }),
      signal: controller.signal,
      openWhenHidden: true,
      onmessage(event) {
        switch (event.event) {
          case 'delta':
            callbacks.onDelta(event.data);
            break;
          case 'done':
            callbacks.onDone();
            break;
          case 'error':
            callbacks.onError(event.data);
            break;
        }
      },
      onerror(err: unknown) {
        callbacks.onError(err instanceof Error ? err.message : '连接异常');
        throw err; // 停止重连
      },
    });
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
