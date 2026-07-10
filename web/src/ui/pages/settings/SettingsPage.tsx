/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
import { useState, useEffect, useCallback } from 'react';
import {
  Card, Table, Button, Tag, Modal, Form, Input, Select, App, Space,
} from 'antd';
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import {
  modelConfigService,
  type UserModelItem,
  type ProviderPresetItem,
  type PrivateModelRequest,
} from '@/service/modelConfigService';

export function SettingsPage() {
  const { message } = App.useApp();

  const [data, setData] = useState<UserModelItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [presets, setPresets] = useState<ProviderPresetItem[]>([]);
  const [privateModalOpen, setPrivateModalOpen] = useState(false);
  const [form] = Form.useForm();

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const list = await modelConfigService.getAvailableModels();
      setData(list);
    } catch {
      message.error('加载模型列表失败');
    } finally {
      setLoading(false);
    }
  }, [message]);

  const loadPresets = useCallback(async () => {
    try {
      const list = await modelConfigService.listProviderPresets();
      setPresets(list);
    } catch {
      // optional
    }
  }, []);

  useEffect(() => {
    loadData();
    loadPresets();
  }, [loadData, loadPresets]);

  const handleSetDefault = async (configId: number, modelName: string) => {
    try {
      await modelConfigService.setDefaultModel({ modelConfigId: configId, modelName });
      message.success('默认模型设置成功');
      loadData();
    } catch {
      message.error('设置失败');
    }
  };

  const handleCreatePrivate = async () => {
    try {
      const values = await form.validateFields();
      await modelConfigService.createPrivateModel(values as PrivateModelRequest);
      message.success('私有模型创建成功，已设为默认');
      setPrivateModalOpen(false);
      form.resetFields();
      loadData();
    } catch {
      // validation or API error
    }
  };

  const columns: ColumnsType<UserModelItem> = [
    {
      title: 'Provider', dataIndex: 'provider', key: 'provider', width: 140,
      render: (v: string) => {
        const preset = presets.find((p) => p.provider === v);
        return <Tag color="blue">{preset?.displayName || v}</Tag>;
      },
    },
    {
      title: '模型名称', dataIndex: 'modelName', key: 'modelName', width: 200,
      render: (v: string) => <code>{v}</code>,
    },
    {
      title: '全局默认', dataIndex: 'isGlobalDefault', key: 'isGlobalDefault', width: 80,
      render: (v: boolean) => v ? <Tag color="green">是</Tag> : null,
    },
    {
      title: '我的默认', dataIndex: 'isUserDefault', key: 'isUserDefault', width: 80,
      render: (v: boolean) => v ? <Tag color="orange">当前使用</Tag> : null,
    },
    {
      title: '操作', key: 'action', width: 100,
      render: (_: unknown, record: UserModelItem) => (
        <Button
          size="small"
          type={record.isUserDefault ? 'default' : 'primary'}
          disabled={record.isUserDefault}
          onClick={() => handleSetDefault(record.configId, record.modelName)}
        >
          {record.isUserDefault ? '已默认' : '设为默认'}
        </Button>
      ),
    },
  ];

  return (
    <div style={{ padding: 24, maxWidth: 900, margin: '0 auto' }}>
      <h2>个人设置 - 模型偏好</h2>

      <Space style={{ marginBottom: 16 }}>
        <Button icon={<ReloadOutlined />} onClick={loadData}>
          刷新
        </Button>
        <Button
          type="primary"
          icon={<PlusOutlined />}
          onClick={() => { form.resetFields(); setPrivateModalOpen(true); }}
        >
          自定义私有模型
        </Button>
      </Space>

      <Card size="small" title="可用模型列表">
        <Table
          dataSource={data}
          columns={columns}
          rowKey={(record) => `${record.configId}-${record.modelName}`}
          loading={loading}
          pagination={false}
        />
      </Card>

      {/* Private Model Modal */}
      <Modal
        title="自定义私有模型"
        open={privateModalOpen}
        onOk={handleCreatePrivate}
        onCancel={() => { setPrivateModalOpen(false); form.resetFields(); }}
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
          <Form.Item
            name="modelName"
            label="模型名称"
            rules={[{ required: true, message: '请输入模型名称' }]}
          >
            <Input placeholder="例如: qwen-max, gpt-4" />
          </Form.Item>
          <Form.Item name="modelListUrl" label="模型列表链接（可选）">
            <Input placeholder="不填则使用默认链接" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
