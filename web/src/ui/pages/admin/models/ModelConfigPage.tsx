/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
import { useState, useEffect, useCallback } from 'react';
import {
  Card, Button, Table, Tag, Modal, Form, Input, Select, Switch, Popconfirm, Space, App,
} from 'antd';
import { PlusOutlined, ReloadOutlined, SyncOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import {
  modelConfigService,
  type ModelConfigItem,
  type ProviderPresetItem,
  type CreateModelConfigRequest,
  type UpdateModelConfigRequest,
} from '@/service/modelConfigService';

export function ModelConfigPage() {
  const { message } = App.useApp();

  const [data, setData] = useState<ModelConfigItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [presets, setPresets] = useState<ProviderPresetItem[]>([]);
  const [modalOpen, setModalOpen] = useState(false);
  const [editModalOpen, setEditModalOpen] = useState(false);
  const [globalModalOpen, setGlobalModalOpen] = useState(false);
  const [editingItem, setEditingItem] = useState<ModelConfigItem | null>(null);
  const [selectedGlobalId, setSelectedGlobalId] = useState<number | null>(null);
  const [form] = Form.useForm();
  const [editForm] = Form.useForm();
  const [globalForm] = Form.useForm();

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const list = await modelConfigService.listConfigs();
      setData(list);
    } catch {
      message.error('加载模型配置列表失败');
    } finally {
      setLoading(false);
    }
  }, [message]);

  const loadPresets = useCallback(async () => {
    try {
      const list = await modelConfigService.listProviderPresets();
      setPresets(list);
    } catch {
      // presets are optional for display
    }
  }, []);

  useEffect(() => {
    loadData();
    loadPresets();
  }, [loadData, loadPresets]);

  const handleCreate = async () => {
    try {
      const values = await form.validateFields();
      await modelConfigService.createConfig(values as CreateModelConfigRequest);
      message.success('创建成功，正在拉取模型列表...');
      setModalOpen(false);
      form.resetFields();
      loadData();
    } catch {
      // validation or API error
    }
  };

  const handleEdit = async () => {
    if (!editingItem) return;
    try {
      const values = await editForm.validateFields();
      await modelConfigService.updateConfig(editingItem.id, values as UpdateModelConfigRequest);
      message.success('更新成功');
      setEditModalOpen(false);
      setEditingItem(null);
      editForm.resetFields();
      loadData();
    } catch {
      // validation or API error
    }
  };

  const handleDelete = async (id: number) => {
    try {
      await modelConfigService.deleteConfig(id);
      message.success('删除成功');
      loadData();
    } catch {
      message.error('删除失败');
    }
  };

  const handleRefresh = async (id: number) => {
    try {
      await modelConfigService.refreshModels(id);
      message.success('模型列表已刷新');
      loadData();
    } catch {
      message.error('刷新失败');
    }
  };

  const handleToggleEnabled = async (item: ModelConfigItem) => {
    try {
      await modelConfigService.updateConfig(item.id, { isEnabled: !item.isEnabled });
      message.success(item.isEnabled ? '已禁用' : '已启用');
      loadData();
    } catch {
      message.error('操作失败');
    }
  };

  const handleSetGlobalDefault = async () => {
    if (!selectedGlobalId) return;
    try {
      const values = await globalForm.validateFields();
      await modelConfigService.setGlobalDefault(selectedGlobalId, { modelName: values.modelName });
      message.success('全局默认模型设置成功');
      setGlobalModalOpen(false);
      setSelectedGlobalId(null);
      globalForm.resetFields();
      loadData();
    } catch {
      // validation or API error
    }
  };

  const columns: ColumnsType<ModelConfigItem> = [
    {
      title: 'Provider', dataIndex: 'provider', key: 'provider', width: 120,
      render: (v: string) => {
        const preset = presets.find((p) => p.provider === v);
        return <Tag color="blue">{preset?.displayName || v}</Tag>;
      },
    },
    {
      title: 'Base URL', dataIndex: 'baseUrl', key: 'baseUrl', width: 200,
      ellipsis: true,
    },
    {
      title: 'API Key', dataIndex: 'apiKey', key: 'apiKey', width: 140,
      render: (v: string) => <code>{v}</code>,
    },
    {
      title: '默认模型', dataIndex: 'defaultModelName', key: 'defaultModelName', width: 120,
    },
    {
      title: '启用', dataIndex: 'isEnabled', key: 'isEnabled', width: 60,
      render: (v: boolean, record: ModelConfigItem) => (
        <Switch
          size="small"
          checked={v}
          onChange={() => handleToggleEnabled(record)}
        />
      ),
    },
    {
      title: '全局默认', dataIndex: 'isGlobalDefault', key: 'isGlobalDefault', width: 80,
      render: (v: boolean) => v ? <Tag color="green">是</Tag> : <Tag>否</Tag>,
    },
    {
      title: '可用模型', dataIndex: 'modelNames', key: 'modelNames', width: 200,
      render: (names: string[]) => (
        <Space size={[0, 4]} wrap>
          {names?.slice(0, 5).map((n) => (
            <Tag key={n} color="default">{n}</Tag>
          ))}
          {names && names.length > 5 && <Tag>+{names.length - 5}</Tag>}
        </Space>
      ),
    },
    {
      title: '操作', key: 'action', width: 240, fixed: 'right',
      render: (_: unknown, record: ModelConfigItem) => (
        <Space size="small">
          <Button
            size="small"
            icon={<SyncOutlined />}
            onClick={() => handleRefresh(record.id)}
          >
            刷新
          </Button>
          <Button
            size="small"
            type="primary"
            onClick={() => {
              setSelectedGlobalId(record.id);
              globalForm.resetFields();
              setGlobalModalOpen(true);
            }}
          >
            全局默认
          </Button>
          <Button
            size="small"
            onClick={() => {
              setEditingItem(record);
              editForm.setFieldsValue({
                provider: record.provider,
                modelListUrl: record.modelListUrl,
                defaultModelName: record.defaultModelName,
              });
              setEditModalOpen(true);
            }}
          >
            编辑
          </Button>
          <Popconfirm title="确认删除此模型配置?" onConfirm={() => handleDelete(record.id)}>
            <Button size="small" danger>删除</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div style={{ padding: 24 }}>
      {/* Action Bar */}
      <Space style={{ marginBottom: 12 }}>
        <Button
          type="primary"
          icon={<PlusOutlined />}
          onClick={() => { form.resetFields(); setModalOpen(true); }}
        >
          新增模型配置
        </Button>
        <Button icon={<ReloadOutlined />} onClick={loadData}>
          刷新
        </Button>
      </Space>

      {/* Table */}
      <Card size="small">
        <Table
          dataSource={data}
          columns={columns}
          rowKey="id"
          loading={loading}
          scroll={{ x: 1200 }}
          pagination={{ showSizeChanger: true, showTotal: (total) => `共 ${total} 条` }}
        />
      </Card>

      {/* Create Modal */}
      <Modal
        title="新增模型配置"
        open={modalOpen}
        onOk={handleCreate}
        onCancel={() => { setModalOpen(false); form.resetFields(); }}
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="provider"
            label="Provider"
            rules={[{ required: true, message: '请选择 Provider' }]}
          >
            <Select placeholder="请选择 Provider">
              {presets.map((p) => (
                <Select.Option key={p.provider} value={p.provider}>
                  {p.displayName}
                </Select.Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item
            name="apiKey"
            label="API Key"
            rules={[{ required: true, message: '请输入 API Key' }]}
          >
            <Input.Password placeholder="请输入 API Key" />
          </Form.Item>
          <Form.Item name="modelListUrl" label="模型列表链接">
            <Input placeholder="不填则使用默认链接" />
          </Form.Item>
        </Form>
      </Modal>

      {/* Edit Modal */}
      <Modal
        title="编辑模型配置"
        open={editModalOpen}
        onOk={handleEdit}
        onCancel={() => { setEditModalOpen(false); setEditingItem(null); editForm.resetFields(); }}
      >
        <Form form={editForm} layout="vertical">
          <Form.Item name="provider" label="Provider">
            <Select placeholder="请选择 Provider" disabled>
              {presets.map((p) => (
                <Select.Option key={p.provider} value={p.provider}>
                  {p.displayName}
                </Select.Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item name="apiKey" label="API Key（留空则不修改）">
            <Input.Password placeholder="留空则不修改" />
          </Form.Item>
          <Form.Item name="modelListUrl" label="模型列表链接">
            <Input placeholder="修改后会自动重新拉取模型列表" />
          </Form.Item>
          <Form.Item name="defaultModelName" label="默认模型名称">
            <Input placeholder="手动指定默认模型名称" />
          </Form.Item>
        </Form>
      </Modal>

      {/* Set Global Default Modal */}
      <Modal
        title="设置全局默认模型"
        open={globalModalOpen}
        onOk={handleSetGlobalDefault}
        onCancel={() => { setGlobalModalOpen(false); setSelectedGlobalId(null); globalForm.resetFields(); }}
      >
        <Form form={globalForm} layout="vertical">
          <Form.Item
            name="modelName"
            label="模型名称"
            rules={[{ required: true, message: '请输入模型名称' }]}
          >
            <Select
              placeholder="请选择模型名称"
              showSearch
              options={
                data
                  .find((item) => item.id === selectedGlobalId)
                  ?.modelNames?.map((n) => ({ label: n, value: n })) || []
              }
            />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
