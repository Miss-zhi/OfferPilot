/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
import http from '@/infra/http';
import { API } from '@/infra/constants';

// ========== Types ==========

export interface ModelConfigItem {
  id: number;
  provider: string;
  baseUrl: string;
  apiKey: string;
  apiFormat: string;
  authHeaderType: string;
  modelListUrl: string;
  defaultModelName: string;
  isEnabled: boolean;
  isGlobalDefault: boolean;
  isPrivate: boolean;
  modelNames: string[];
  createdAt: string;
  updatedAt: string;
}

export interface CreateModelConfigRequest {
  provider: string;
  apiKey: string;
  modelListUrl?: string;
}

export interface UpdateModelConfigRequest {
  provider?: string;
  apiKey?: string;
  modelListUrl?: string;
  defaultModelName?: string;
  isEnabled?: boolean;
}

export interface SetGlobalDefaultRequest {
  modelName: string;
}

export interface ProviderPresetItem {
  provider: string;
  displayName: string;
  defaultBaseUrl: string;
  defaultModelListUrl: string;
  apiFormat: string;
  authHeaderType: string;
  keyTemplate: string;
}

export interface UserModelItem {
  configId: number;
  provider: string;
  modelName: string;
  isGlobalDefault: boolean;
  isUserDefault: boolean;
}

export interface SetDefaultModelRequest {
  modelConfigId: number;
  modelName: string;
}

export interface PrivateModelRequest {
  provider: string;
  apiKey: string;
  modelListUrl?: string;
  modelName: string;
}

// ========== Admin API ==========

export const modelConfigService = {
  /** 管理员-获取模型配置列表 */
  listConfigs: () =>
    http.get<ModelConfigItem[]>(API.ADMIN_MODEL_CONFIGS).then((res) => res.data),

  /** 管理员-新增模型配置 */
  createConfig: (data: CreateModelConfigRequest) =>
    http.post<ModelConfigItem>(API.ADMIN_MODEL_CONFIGS, data).then((res) => res.data),

  /** 管理员-更新模型配置 */
  updateConfig: (id: number, data: UpdateModelConfigRequest) =>
    http.put<ModelConfigItem>(API.ADMIN_MODEL_CONFIG(id), data).then((res) => res.data),

  /** 管理员-删除模型配置 */
  deleteConfig: (id: number) =>
    http.delete(API.ADMIN_MODEL_CONFIG(id)).then((res) => res.data),

  /** 管理员-重新拉取模型名称 */
  refreshModels: (id: number) =>
    http.post<string[]>(API.ADMIN_MODEL_REFRESH(id)).then((res) => res.data),

  /** 管理员-设置全局默认 */
  setGlobalDefault: (id: number, data: SetGlobalDefaultRequest) =>
    http.put<ModelConfigItem>(API.ADMIN_MODEL_SET_GLOBAL(id), data).then((res) => res.data),

  /** 管理员-获取 Provider 预设列表 */
  listProviderPresets: () =>
    http.get<ProviderPresetItem[]>(API.ADMIN_PROVIDER_PRESETS).then((res) => res.data),

  // ========== User API ==========

  /** 用户-获取可用模型列表 */
  getAvailableModels: () =>
    http.get<UserModelItem[]>(API.USER_MODELS).then((res) => res.data),

  /** 用户-设置默认模型 */
  setDefaultModel: (data: SetDefaultModelRequest) =>
    http.put(API.USER_DEFAULT_MODEL, data).then((res) => res.data),

  /** 用户-新增私有模型 */
  createPrivateModel: (data: PrivateModelRequest) =>
    http.post<ModelConfigItem>(API.USER_PRIVATE_MODEL, data).then((res) => res.data),
};
