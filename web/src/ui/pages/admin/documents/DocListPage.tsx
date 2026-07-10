/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
import { useState, useEffect, useCallback, useRef } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  Card, Button, Table, Tag, Modal, Form, Select, Upload, Progress, Popconfirm, Space, Row, Col, Typography, Collapse, App,
} from 'antd';
import {
  PlusOutlined, DeleteOutlined, ReloadOutlined, SyncOutlined, InboxOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import {
  kbService,
  type DocItem, type DocDetail, type DocProgress,
} from '@/service/kbService';
import { useKbStore } from '@/store/kb-store';

const { Dragger } = Upload;
const { Title, Text } = Typography;

const STATUS_MAP: Record<string, { color: string; label: string }> = {
  UPLOADED: { color: 'default', label: '已上传' },
  PARSING: { color: 'processing', label: '解析中' },
  CHUNKING: { color: 'processing', label: '分块中' },
  EMBEDDING: { color: 'processing', label: '向量化中' },
  INDEXING: { color: 'processing', label: '索引中' },
  ACTIVE: { color: 'green', label: '已完成' },
  FAILED: { color: 'red', label: '失败' },
};

export function DocListPage() {
  const { kbId } = useParams<{ kbId: string }>();
  const navigate = useNavigate();
  const { message } = App.useApp();
  const currentKbName = useKbStore((s) => s.currentKbName);

  const [data, setData] = useState<DocItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [selectedRowKeys, setSelectedRowKeys] = useState<React.Key[]>([]);
  const [uploadModalOpen, setUploadModalOpen] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [strategy, setStrategy] = useState('AUTO');
  const [stats, setStats] = useState<{ documentCount: number; activeDocuments: number; failedDocuments: number } | null>(null);

  // Detail modal
  const [detailModalOpen, setDetailModalOpen] = useState(false);
  const [detailDoc, setDetailDoc] = useState<DocDetail | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);

  // Progress polling
  const pollingRefs = useRef<Map<string, ReturnType<typeof setInterval>>>(new Map());

  const loadData = useCallback(async () => {
    if (!kbId) return;
    setLoading(true);
    try {
      const [docs, kbStats] = await Promise.all([
        kbService.listDocs(kbId),
        kbService.getKbStats(kbId),
      ]);
      setData(docs);
      setStats(kbStats);
    } catch {
      message.error('加载文档列表失败');
    } finally {
      setLoading(false);
    }
  }, [kbId, message]);

  useEffect(() => {
    loadData();
    return () => {
      // Clear all polling intervals on unmount
      pollingRefs.current.forEach((timer) => clearInterval(timer));
    };
  }, [loadData]);

  const startPolling = (docId: string) => {
    if (!kbId) return;
    // Clear existing polling for this docId
    const existing = pollingRefs.current.get(docId);
    if (existing) clearInterval(existing);

    const timer = setInterval(async () => {
      try {
        const progress: DocProgress = await kbService.getDocProgress(kbId, docId);
        setData((prev) =>
          prev.map((d) =>
            d.docId === docId ? { ...d, status: progress.status, progress: progress.progress } : d,
          ),
        );
        if (progress.status === 'ACTIVE' || progress.status === 'FAILED') {
          clearInterval(timer);
          pollingRefs.current.delete(docId);
          if (progress.status === 'ACTIVE') {
            message.success('文档入库完成');
            loadData(); // Refresh full list for updated counts
          } else {
            message.error('文档入库失败');
          }
        }
      } catch {
        clearInterval(timer);
        pollingRefs.current.delete(docId);
      }
    }, 2000);
    pollingRefs.current.set(docId, timer);
  };

  const handleUpload = async (file: File) => {
    if (!kbId) return;
    setUploading(true);
    try {
      const formData = new FormData();
      formData.append('file', file);
      formData.append('chunkStrategy', strategy);
      const doc = await kbService.uploadDocs(kbId, formData);
      message.success(`${file.name} 上传成功，开始入库`);
      setUploadModalOpen(false);
      setData((prev) => [doc, ...prev]);
      startPolling(doc.docId);
    } catch {
      message.error('上传失败');
    } finally {
      setUploading(false);
    }
  };

  const handleDeleteDoc = async (docId: string) => {
    if (!kbId) return;
    try {
      await kbService.deleteDoc(kbId, docId);
      message.success('删除成功');
      loadData();
    } catch {
      message.error('删除失败');
    }
  };

  const handleReindex = async (docId: string) => {
    if (!kbId) return;
    try {
      await kbService.reindexDoc(kbId, docId);
      message.success('重建索引已触发');
      startPolling(docId);
    } catch {
      message.error('重建索引失败');
    }
  };

  const handleViewDetail = async (docId: string) => {
    if (!kbId) return;
    setDetailLoading(true);
    setDetailModalOpen(true);
    try {
      const detail = await kbService.getDoc(kbId, docId);
      setDetailDoc(detail);
    } catch {
      message.error('加载文档详情失败');
    } finally {
      setDetailLoading(false);
    }
  };

  const handleBatchDelete = async () => {
    if (selectedRowKeys.length === 0) {
      message.warning('请先选择要删除的文档');
      return;
    }
    if (!kbId) return;
    try {
      await Promise.all(selectedRowKeys.map((id) => kbService.deleteDoc(kbId, id as string)));
      message.success('批量删除成功');
      setSelectedRowKeys([]);
      loadData();
    } catch {
      message.error('批量删除失败');
    }
  };

  const formatFileSize = (bytes: number) => {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  };

  const columns: ColumnsType<DocItem> = [
    { title: '文件名', dataIndex: 'fileName', key: 'fileName', ellipsis: true },
    {
      title: '类型', dataIndex: 'fileType', key: 'fileType', width: 70,
      render: (t: string) => <Tag>{t.toUpperCase()}</Tag>,
    },
    {
      title: '大小', dataIndex: 'fileSize', key: 'fileSize', width: 90,
      render: (s: number) => formatFileSize(s),
    },
    { title: '分块数', dataIndex: 'chunkCount', key: 'chunkCount', width: 80 },
    {
      title: '策略', dataIndex: 'chunkStrategy', key: 'chunkStrategy', width: 80,
      render: (s: string) => <Tag>{s}</Tag>,
    },
    {
      title: '状态', dataIndex: 'status', key: 'status', width: 180,
      render: (s: string, record: DocItem) => (
        <Space direction="vertical" size={2} style={{ width: '100%' }}>
          <Tag color={STATUS_MAP[s]?.color || 'default'}>
            {STATUS_MAP[s]?.label || s}
          </Tag>
          {s !== 'ACTIVE' && s !== 'FAILED' && s !== 'UPLOADED' && (
            <Progress percent={record.progress} size="small" style={{ width: 120 }} />
          )}
        </Space>
      ),
    },
    {
      title: '上传时间', dataIndex: 'uploadedAt', key: 'uploadedAt', width: 160,
      render: (ts: string) => ts ? new Date(ts).toLocaleString() : '-',
    },
    {
      title: '操作', key: 'action', width: 200,
      render: (_: unknown, record: DocItem) => (
        <Space wrap>
          <Button size="small" type="link" onClick={() => handleViewDetail(record.docId)}>
            详情
          </Button>
          {record.status === 'ACTIVE' && (
            <Button size="small" type="link" onClick={() => handleReindex(record.docId)}>
              重建索引
            </Button>
          )}
          <Popconfirm title="确认删除此文档?" onConfirm={() => handleDeleteDoc(record.docId)}>
            <Button size="small" type="link" danger>删除</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div style={{ padding: 24 }}>
      {/* Page Header */}
      <Space style={{ marginBottom: 16 }}>
        <Button type="link" onClick={() => navigate('/admin/knowledge')}>
          知识库列表
        </Button>
        <Text type="secondary">&gt;</Text>
        <Text strong>{currentKbName || kbId}</Text>
      </Space>

      {/* Stats Cards */}
      {stats && (
        <Row gutter={16} style={{ marginBottom: 16 }}>
          <Col span={8}>
            <Card size="small">
              <Text type="secondary">文档总数</Text>
              <Title level={3} style={{ margin: 0 }}>{stats.documentCount}</Title>
            </Card>
          </Col>
          <Col span={8}>
            <Card size="small">
              <Text type="secondary">活跃文档</Text>
              <Title level={3} style={{ margin: 0, color: '#52c41a' }}>{stats.activeDocuments}</Title>
            </Card>
          </Col>
          <Col span={8}>
            <Card size="small">
              <Text type="secondary">失败文档</Text>
              <Title level={3} style={{ margin: 0, color: '#ff4d4f' }}>{stats.failedDocuments}</Title>
            </Card>
          </Col>
        </Row>
      )}

      {/* Action Bar */}
      <Space style={{ marginBottom: 12 }}>
        <Button
          type="primary"
          icon={<PlusOutlined />}
          onClick={() => setUploadModalOpen(true)}
        >
          上传文档
        </Button>
        <Select
          value={strategy}
          onChange={(v) => setStrategy(v)}
          style={{ width: 140 }}
          options={[
            { value: 'AUTO', label: '自动分块' },
            { value: 'FIXED', label: '固定大小' },
            { value: 'SEMANTIC', label: '语义分块' },
          ]}
        />
        <Button icon={<ReloadOutlined />} onClick={loadData}>刷新</Button>
        <Button danger icon={<DeleteOutlined />} onClick={handleBatchDelete}>
          批量删除
        </Button>
      </Space>

      {/* Table */}
      <Table
        dataSource={data}
        columns={columns}
        rowKey="docId"
        loading={loading}
        rowSelection={{
          selectedRowKeys,
          onChange: (keys) => setSelectedRowKeys(keys),
        }}
        pagination={{
          pageSize: 10,
          showSizeChanger: true,
          showTotal: (total) => `共 ${total} 条`,
        }}
      />

      {/* Upload Modal */}
      <Modal
        title="上传文档"
        open={uploadModalOpen}
        onCancel={() => setUploadModalOpen(false)}
        footer={null}
      >
        <Space direction="vertical" style={{ width: '100%' }} size="middle">
          <div>
            <Text strong>分块策略：</Text>
            <Select
              value={strategy}
              onChange={(v) => setStrategy(v)}
              style={{ width: 140, marginLeft: 8 }}
              options={[
                { value: 'AUTO', label: '自动分块' },
                { value: 'FIXED', label: '固定大小' },
                { value: 'SEMANTIC', label: '语义分块' },
              ]}
            />
          </div>
          <Dragger
            name="file"
            multiple={false}
            accept=".pdf,.txt,.md,.docx"
            showUploadList={false}
            disabled={uploading}
            beforeUpload={(file) => {
              handleUpload(file);
              return false;
            }}
          >
            <p className="ant-upload-drag-icon">
              <InboxOutlined />
            </p>
            <p className="ant-upload-text">
              {uploading ? '正在上传...' : '点击或拖拽文件到此区域上传'}
            </p>
            <p className="ant-upload-hint">
              支持 PDF、TXT、MD、DOCX 格式
            </p>
          </Dragger>
        </Space>
      </Modal>

      {/* Document Detail Modal */}
      <Modal
        title="文档详情"
        open={detailModalOpen}
        onCancel={() => { setDetailModalOpen(false); setDetailDoc(null); }}
        footer={null}
        width={720}
        loading={detailLoading}
      >
        {detailDoc && (
          <Space direction="vertical" style={{ width: '100%' }} size="middle">
            {/* Metadata */}
            <Card size="small" title="文档信息">
              <Row gutter={[16, 8]}>
                <Col span={12}><Text type="secondary">文件名：</Text>{detailDoc.fileName}</Col>
                <Col span={12}><Text type="secondary">类型：</Text>{detailDoc.fileType}</Col>
                <Col span={12}><Text type="secondary">大小：</Text>{formatFileSize(detailDoc.fileSize)}</Col>
                <Col span={12}><Text type="secondary">分块数：</Text>{detailDoc.chunkCount}</Col>
                <Col span={12}><Text type="secondary">策略：</Text>{detailDoc.chunkStrategy}</Col>
                <Col span={12}>
                  <Text type="secondary">状态：</Text>
                  <Tag color={STATUS_MAP[detailDoc.status]?.color}>
                    {STATUS_MAP[detailDoc.status]?.label || detailDoc.status}
                  </Tag>
                </Col>
                <Col span={12}>
                  <Text type="secondary">上传时间：</Text>
                  {detailDoc.uploadedAt ? new Date(detailDoc.uploadedAt).toLocaleString() : '-'}
                </Col>
                <Col span={12}>
                  <Text type="secondary">索引时间：</Text>
                  {detailDoc.indexedAt ? new Date(detailDoc.indexedAt).toLocaleString() : '-'}
                </Col>
                {detailDoc.errorMessage && (
                  <Col span={24}>
                    <Text type="secondary">错误信息：</Text>
                    <Text type="danger">{detailDoc.errorMessage}</Text>
                  </Col>
                )}
              </Row>
            </Card>

            {/* Chunks */}
            {detailDoc.chunks && detailDoc.chunks.length > 0 && (
              <Card size="small" title={`分块列表（共 ${detailDoc.chunks.length} 个）`}>
                <div style={{ maxHeight: 400, overflow: 'auto' }}>
                  <Collapse
                    items={detailDoc.chunks.map((chunk) => ({
                      key: `${chunk.chunkIndex}`,
                      label: `分块 #${chunk.chunkIndex}（${chunk.tokenCount} tokens）`,
                      children: <Text style={{ whiteSpace: 'pre-wrap' }}>{chunk.content}</Text>,
                    }))}
                  />
                </div>
              </Card>
            )}

            {/* Actions */}
            <Space>
              {detailDoc.status === 'ACTIVE' && (
                <Button
                  icon={<SyncOutlined />}
                  onClick={() => handleReindex(detailDoc.docId)}
                >
                  重建索引
                </Button>
              )}
              <Popconfirm title="确认删除此文档?" onConfirm={() => {
                handleDeleteDoc(detailDoc.docId);
                setDetailModalOpen(false);
                setDetailDoc(null);
              }}>
                <Button danger icon={<DeleteOutlined />}>删除</Button>
              </Popconfirm>
            </Space>
          </Space>
        )}
      </Modal>
    </div>
  );
}
