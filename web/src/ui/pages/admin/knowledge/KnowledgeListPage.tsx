/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
import { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Card, Form, Input, Select, Button, Table, Tag, Modal, Space, Popconfirm, App,
} from 'antd';
import { PlusOutlined, SearchOutlined, ReloadOutlined, DeleteOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { kbService, type KbItem, type CreateKbRequest } from '@/service/kbService';
import { useKbStore } from '@/store/kb-store';

export function KnowledgeListPage() {
  const navigate = useNavigate();
  const { message } = App.useApp();
  const setCurrentKb = useKbStore((s) => s.setCurrentKb);

  const [data, setData] = useState<KbItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [selectedRowKeys, setSelectedRowKeys] = useState<React.Key[]>([]);
  const [pagination, setPagination] = useState({ current: 1, pageSize: 10, total: 0 });
  const [modalOpen, setModalOpen] = useState(false);
  const [editingItem, setEditingItem] = useState<KbItem | null>(null);
  const [queryForm] = Form.useForm();
  const [form] = Form.useForm();

  const loadData = useCallback(async (params?: Record<string, unknown>) => {
    setLoading(true);
    try {
      const list = await kbService.listKbs();
      const page = params?.page as number || 1;
      const size = params?.pageSize as number || 10;
      setData(list.slice((page - 1) * size, page * size));
      setPagination((prev) => ({ ...prev, current: page, pageSize: size, total: list.length }));
    } catch {
      message.error('加载知识库列表失败');
    } finally {
      setLoading(false);
    }
  }, [message]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const handleSearch = () => {
    loadData(queryForm.getFieldsValue());
  };

  const handleReset = () => {
    queryForm.resetFields();
    loadData();
  };

  const handleCreate = async () => {
    try {
      const values = await form.validateFields();
      await kbService.createKb(values as CreateKbRequest);
      message.success('创建成功');
      setModalOpen(false);
      form.resetFields();
      setEditingItem(null);
      loadData();
    } catch (err: unknown) {
      if (err instanceof Error && err.message) {
        message.error(err.message);
      }
    }
  };

  const handleDelete = async (kbId: string) => {
    try {
      await kbService.deleteKb(kbId);
      message.success('删除成功');
      loadData();
    } catch {
      message.error('删除失败');
    }
  };

  const handleBatchDelete = async () => {
    if (selectedRowKeys.length === 0) {
      message.warning('请先选择要删除的知识库');
      return;
    }
    try {
      await Promise.all(selectedRowKeys.map((id) => kbService.deleteKb(id as string)));
      message.success('批量删除成功');
      setSelectedRowKeys([]);
      loadData();
    } catch {
      message.error('批量删除失败');
    }
  };

  const visibilityTag = (visibility: string) => {
    switch (visibility) {
      case 'PUBLIC': return <Tag color="blue">公开</Tag>;
      case 'PRIVATE': return <Tag color="orange">私有</Tag>;
      default: return <Tag>{visibility}</Tag>;
    }
  };

  const statusTag = (status: string) => {
    switch (status) {
      case 'ACTIVE': return <Tag color="green">正常</Tag>;
      case 'INACTIVE': return <Tag color="default">停用</Tag>;
      default: return <Tag>{status}</Tag>;
    }
  };

  const columns: ColumnsType<KbItem> = [
    {
      title: '名称', dataIndex: 'name', key: 'name',
      render: (name: string, record: KbItem) => (
        <Button
          type="link"
          onClick={() => {
            setCurrentKb(record.kbId, record.name);
            navigate(`/admin/knowledge/${record.kbId}`);
          }}
        >
          {name}
        </Button>
      ),
    },
    { title: '分类', dataIndex: 'category', key: 'category', width: 100 },
    {
      title: '可见性', dataIndex: 'visibility', key: 'visibility', width: 80,
      render: (v: string) => visibilityTag(v),
    },
    { title: '文档数', dataIndex: 'documentCount', key: 'documentCount', width: 80 },
    { title: '向量数', dataIndex: 'chunkCount', key: 'chunkCount', width: 80 },
    {
      title: '状态', dataIndex: 'status', key: 'status', width: 80,
      render: (s: string) => statusTag(s),
    },
    {
      title: '操作', key: 'action', width: 80,
      render: (_: unknown, record: KbItem) => (
        <Popconfirm title="确认删除此知识库?" onConfirm={() => handleDelete(record.kbId)}>
          <Button size="small" type="link" danger>删除</Button>
        </Popconfirm>
      ),
    },
  ];

  return (
    <div style={{ padding: 24 }}>
      {/* Query Bar */}
      <Card size="small" style={{ marginBottom: 16 }}>
        <Form form={queryForm} layout="inline">
          <Form.Item name="name" label="名称">
            <Input placeholder="请输入" allowClear />
          </Form.Item>
          <Form.Item name="visibility" label="可见性">
            <Select placeholder="请选择" allowClear style={{ width: 120 }}>
              <Select.Option value="PUBLIC">公开</Select.Option>
              <Select.Option value="PRIVATE">私有</Select.Option>
            </Select>
          </Form.Item>
          <Form.Item>
            <Space>
              <Button type="primary" icon={<SearchOutlined />} onClick={handleSearch}>查询</Button>
              <Button icon={<ReloadOutlined />} onClick={handleReset}>重置</Button>
            </Space>
          </Form.Item>
        </Form>
      </Card>

      {/* Action Bar */}
      <Space style={{ marginBottom: 12 }}>
        <Button
          type="primary"
          icon={<PlusOutlined />}
          onClick={() => { setEditingItem(null); form.resetFields(); setModalOpen(true); }}
        >
          新增
        </Button>
        <Button danger icon={<DeleteOutlined />} onClick={handleBatchDelete}>
          批量删除
        </Button>
      </Space>

      {/* Table */}
      <Table
        dataSource={data}
        columns={columns}
        rowKey="kbId"
        loading={loading}
        rowSelection={{
          selectedRowKeys,
          onChange: (keys) => setSelectedRowKeys(keys),
        }}
        pagination={{
          current: pagination.current,
          pageSize: pagination.pageSize,
          total: pagination.total,
          showSizeChanger: true,
          showQuickJumper: true,
          showTotal: (total) => `共 ${total} 条`,
        }}
        onChange={(pag) => setPagination((prev) => ({ ...prev, ...pag }))}
      />

      {/* Create Modal */}
      <Modal
        title={editingItem ? '编辑知识库' : '新增知识库'}
        open={modalOpen}
        onOk={handleCreate}
        onCancel={() => { setModalOpen(false); form.resetFields(); setEditingItem(null); }}
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="name"
            label="名称"
            rules={[{ required: true, message: '请输入知识库名称' }]}
          >
            <Input placeholder="请输入知识库名称" />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={3} placeholder="请输入描述" />
          </Form.Item>
          <Form.Item name="category" label="分类">
            <Input placeholder="请输入分类" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
