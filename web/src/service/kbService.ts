/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
import http from '@/infra/http';
import { API } from '@/infra/constants';

/** 知识库列表项 */
export interface KbItem {
  kbId: string;
  name: string;
  description?: string;
  category?: string;
  visibility: string;
  status: string;
  documentCount: number;
  chunkCount: number;
}

/** 创建知识库请求 */
export interface CreateKbRequest {
  name: string;
  description?: string;
  category?: string;
}

/** 知识库统计 */
export interface KbStats {
  kbId: string;
  name: string;
  documentCount: number;
  chunkCount: number;
  activeDocuments: number;
  failedDocuments: number;
}

/** 文档列表项 */
export interface DocItem {
  docId: string;
  kbId: string;
  fileName: string;
  fileType: string;
  fileSize: number;
  chunkCount: number;
  chunkStrategy: string;
  status: string;
  progress: number;
  tags?: string;
  uploadedAt?: string;
  indexedAt?: string;
}

/** 分块预览 */
export interface ChunkPreview {
  chunkIndex: number;
  content: string;
  tokenCount: number;
}

/** 文档详情（含分块） */
export interface DocDetail extends DocItem {
  errorMessage?: string;
  metadataJson?: string;
  chunks: ChunkPreview[];
}

/** 检索请求 */
export interface SearchRequest {
  query: string;
  filterExpr?: string;
  topK?: number;
}

/** 检索结果命中 */
export interface SearchHit {
  docId: string;
  chunkIndex: number;
  content: string;
  score: number;
  tags?: string;
}

/** 检索结果 */
export interface SearchResult {
  total: number;
  latencyMs: number;
  hits: SearchHit[];
}

/** 文档进度 */
export interface DocProgress {
  status: string;
  progress: number;
}

export const kbService = {
  /** 知识库列表 */
  listKbs: () => http.get<KbItem[]>(API.KB_LIST).then((res) => res.data),

  /** 创建知识库 */
  createKb: (data: CreateKbRequest) =>
    http.post<KbItem>(API.KB_CREATE, data).then((res) => res.data),

  /** 删除知识库 */
  deleteKb: (kbId: string) => http.delete(API.KB_DELETE(kbId)),

  /** 知识库统计 */
  getKbStats: (kbId: string) =>
    http.get<KbStats>(API.KB_STATS(kbId)).then((res) => res.data),

  /** 文档列表 */
  listDocs: (kbId: string) =>
    http.get<DocItem[]>(API.KB_DOCS(kbId)).then((res) => res.data),

  /** 上传文档 */
  uploadDocs: (kbId: string, formData: FormData) =>
    http.post<DocItem>(API.KB_DOCS(kbId), formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    }).then((res) => res.data),

  /** 文档详情（含分块） */
  getDoc: (kbId: string, docId: string) =>
    http.get<DocDetail>(API.KB_DOC_DETAIL(kbId, docId)).then((res) => res.data),

  /** 删除文档 */
  deleteDoc: (kbId: string, docId: string) =>
    http.delete(API.KB_DOC_DELETE(kbId, docId)),

  /** 重建索引 */
  reindexDoc: (kbId: string, docId: string) =>
    http.post(API.KB_DOC_REINDEX(kbId, docId)),

  /** 入库进度 */
  getDocProgress: (kbId: string, docId: string) =>
    http.get<DocProgress>(API.KB_DOC_PROGRESS(kbId, docId)).then((res) => res.data),

  /** 检索测试 */
  search: (kbId: string, data: SearchRequest) =>
    http.post<SearchResult>(API.KB_SEARCH(kbId), data).then((res) => res.data),
};
