/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
import { useState, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  Card, Button, Input, InputNumber, Table, Tag, Space, Typography, App,
} from 'antd';
import { SearchOutlined, ArrowLeftOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import {
  kbService,
  type SearchHit, type SearchResult,
} from '@/service/kbService';
import { useKbStore } from '@/store/kb-store';

const { TextArea } = Input;
const { Text, Title } = Typography;

export function SearchTestPage() {
  const { kbId } = useParams<{ kbId: string }>();
  const navigate = useNavigate();
  const { message } = App.useApp();
  const currentKbName = useKbStore((s) => s.currentKbName);

  const [query, setQuery] = useState('');
  const [topK, setTopK] = useState(5);
  const [searching, setSearching] = useState(false);
  const [result, setResult] = useState<SearchResult | null>(null);

  const handleSearch = useCallback(async () => {
    if (!kbId) return;
    if (!query.trim()) {
      message.warning('请输入查询文本');
      return;
    }
    setSearching(true);
    try {
      const res = await kbService.search(kbId, { query: query.trim(), topK });
      setResult(res);
    } catch {
      message.error('检索失败');
    } finally {
      setSearching(false);
    }
  }, [kbId, query, topK, message]);

  const scoreColor = (score: number) => {
    if (score < 0.3) return 'green';
    if (score < 0.6) return 'orange';
    return 'red';
  };

  const columns: ColumnsType<SearchHit> = [
    {
      title: '相似度', dataIndex: 'score', key: 'score', width: 100,
      render: (s: number) => (
        <Tag color={scoreColor(s)}>{(s * 100).toFixed(1)}%</Tag>
      ),
    },
    { title: '文档ID', dataIndex: 'docId', key: 'docId', width: 150, ellipsis: true },
    { title: '分块索引', dataIndex: 'chunkIndex', key: 'chunkIndex', width: 80 },
    {
      title: '内容摘要', dataIndex: 'content', key: 'content',
      render: (c: string) => (
        <Text ellipsis={{ tooltip: c }} style={{ maxWidth: 400, display: 'inline-block' }}>
          {c}
        </Text>
      ),
    },
    {
      title: '标签', dataIndex: 'tags', key: 'tags', width: 100,
      render: (t: string) => t ? <Tag>{t}</Tag> : '-',
    },
  ];

  return (
    <div style={{ padding: 24 }}>
      {/* Page Header */}
      <Space style={{ marginBottom: 16 }}>
        <Button
          type="link"
          icon={<ArrowLeftOutlined />}
          onClick={() => navigate(`/admin/knowledge/${kbId}`)}
        >
          返回文档列表
        </Button>
        <Text type="secondary">|</Text>
        <Text strong>{currentKbName || kbId}</Text>
        <Text type="secondary">- 检索测试</Text>
      </Space>

      {/* Search Input */}
      <Card size="small" style={{ marginBottom: 16 }}>
        <Space direction="vertical" style={{ width: '100%' }} size="middle">
          <TextArea
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="输入查询文本，测试向量检索效果..."
            rows={3}
            onPressEnter={(e) => {
              if (!e.shiftKey) {
                e.preventDefault();
                handleSearch();
              }
            }}
          />
          <Space>
            <Text>Top-K：</Text>
            <InputNumber
              min={1}
              max={50}
              value={topK}
              onChange={(v) => setTopK(v || 5)}
              style={{ width: 80 }}
            />
            <Button
              type="primary"
              icon={<SearchOutlined />}
              onClick={handleSearch}
              loading={searching}
            >
              搜索
            </Button>
          </Space>
        </Space>
      </Card>

      {/* Result */}
      {result && (
        <Card
          size="small"
          title={
            <Space>
              <Text>检索结果</Text>
              <Tag color="blue">共 {result.total} 条命中</Tag>
              <Tag color="default">耗时 {result.latencyMs}ms</Tag>
            </Space>
          }
        >
          {result.hits.length > 0 ? (
            <Table
              dataSource={result.hits}
              columns={columns}
              rowKey={(record) => `${record.docId}-${record.chunkIndex}`}
              pagination={{ pageSize: 10, showTotal: (total) => `共 ${total} 条` }}
              size="small"
            />
          ) : (
            <Text type="secondary">无检索结果，尝试修改查询文本</Text>
          )}
        </Card>
      )}
    </div>
  );
}
