/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */

/** API 路径常量 */
export const API = {
  // 认证
  AUTH_REGISTER: '/v1/auth/register',
  AUTH_LOGIN: '/v1/auth/login',
  // 对话
  CHAT_SYNC: '/v1/offerpilot/chat',
  CHAT_STREAM: '/v1/offerpilot/chat/stream',
  // 文件上传
  UPLOAD: '/v1/offerpilot/upload',
  // 报告
  REPORTS: '/v1/offerpilot/reports',
  REPORT_DETAIL: (id: string) => `/v1/offerpilot/reports/${id}`,
  // 知识库管理
  KB_LIST: '/v1/kb',
  KB_CREATE: '/v1/kb',
  KB_DELETE: (kbId: string) => `/v1/kb/${kbId}`,
  KB_STATS: (kbId: string) => `/v1/kb/${kbId}/stats`,
  KB_DOCS: (kbId: string) => `/v1/kb/${kbId}/docs`,
  KB_DOC_DETAIL: (kbId: string, docId: string) => `/v1/kb/${kbId}/docs/${docId}`,
  KB_DOC_DELETE: (kbId: string, docId: string) => `/v1/kb/${kbId}/docs/${docId}`,
  KB_DOC_PROGRESS: (kbId: string, docId: string) => `/v1/kb/${kbId}/docs/${docId}/progress`,
  KB_DOC_REINDEX: (kbId: string, docId: string) => `/v1/kb/${kbId}/docs/${docId}/reindex`,
  KB_SEARCH: (kbId: string) => `/v1/kb/${kbId}/search`,
  // 成长追踪
  PROGRESS: '/v1/offerpilot/progress',
  // 薪资
  SALARY_SEARCH: '/v1/offerpilot/salary/search',
  SALARY_COMPARE: '/v1/offerpilot/salary/compare',
  SALARY_NEGOTIATION_SCRIPT: '/v1/offerpilot/salary/negotiation-script',
  // 模型管理
  ADMIN_MODEL_CONFIGS: '/v1/admin/models',
  ADMIN_MODEL_CONFIG: (id: number) => `/v1/admin/models/${id}`,
  ADMIN_MODEL_REFRESH: (id: number) => `/v1/admin/models/${id}/refresh-models`,
  ADMIN_MODEL_SET_GLOBAL: (id: number) => `/v1/admin/models/${id}/set-global-default`,
  ADMIN_PROVIDER_PRESETS: '/v1/admin/models/provider-presets',
  USER_MODELS: '/v1/user/models',
  USER_DEFAULT_MODEL: '/v1/user/models/default',
  USER_PRIVATE_MODEL: '/v1/user/models/private',
} as const;



/** 会话存储 Key */
export const STORAGE_KEYS = {
  TOKEN: 'offerpilot_token',
  USER: 'offerpilot_user',
  SESSION_ID: 'offerpilot_session_id',
} as const;

/** 文件上传限制 */
export const UPLOAD_LIMITS = {
  MAX_SIZE: 20 * 1024 * 1024, // 20MB
  ACCEPT: '.pdf,.txt,.md,.docx,.mp3,.wav,.m4a',
} as const;
