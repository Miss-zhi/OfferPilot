/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
import { useState } from 'react';
import {
  Card,
  Tabs,
  Table,
  Form,
  Input,
  Button,
  InputNumber,
  Select,
  Space,
  Tag,
  Typography,
  List,
  Empty,
  message,
} from 'antd';
import {
  SearchOutlined,
  PlusOutlined,
  MinusCircleOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
} from '@ant-design/icons';
import ReactEChartsCore from 'echarts-for-react';
import type { EChartsOption } from 'echarts';
import { salaryService } from '@/service/salaryService';
import type {
  SalarySearchResult,
  OfferCompareRequest,
  OfferComparisonResult,
  NegotiationScriptRequest,
  NegotiationScriptResult,
} from '@/service/salaryService';
import { PageHeader } from '@/ui/components/PageHeader';

const { Text, Paragraph } = Typography;

/* ========== 薪资搜索 Tab ========== */

function SalarySearchTab() {
  const [searchForm] = Form.useForm();
  const [result, setResult] = useState<SalarySearchResult | null>(null);
  const [searching, setSearching] = useState(false);

  const handleSearch = async () => {
    const { company, position } = searchForm.getFieldsValue();
    if (!company) {
      message.warning('请输入公司名称');
      return;
    }
    setSearching(true);
    try {
      const data = await salaryService.searchSalary(company, position || undefined);
      setResult(data);
    } catch {
      message.error('搜索失败');
    } finally {
      setSearching(false);
    }
  };

  const columns = [
    { title: '公司', dataIndex: 'company', key: 'company', width: 140 },
    { title: '职位', dataIndex: 'position', key: 'position', width: 120 },
    { title: '薪资范围', dataIndex: 'baseRange', key: 'baseRange', width: 140 },
    { title: '奖金', dataIndex: 'bonusRange', key: 'bonusRange', width: 120 },
    { title: '股票', dataIndex: 'stockInfo', key: 'stockInfo', width: 120 },
    {
      title: '来源',
      dataIndex: 'source',
      key: 'source',
      width: 100,
      render: (v: string) => (v ? <Tag>{v}</Tag> : '-'),
    },
  ];

  return (
    <div>
      <Card size="small" style={{ marginBottom: 16 }}>
        <Form form={searchForm} layout="inline">
          <Form.Item name="company" label="公司" rules={[{ required: true, message: '请输入公司名称' }]}>
            <Input placeholder="如：字节跳动" allowClear />
          </Form.Item>
          <Form.Item name="position" label="职位">
            <Input placeholder="如：后端开发" allowClear />
          </Form.Item>
          <Form.Item>
            <Button
              type="primary"
              icon={<SearchOutlined />}
              onClick={handleSearch}
              loading={searching}
            >
              搜索
            </Button>
          </Form.Item>
        </Form>
      </Card>

      {result && (
        <Card title={`搜索结果（共 ${result.total} 条）`}>
          <Table
            columns={columns}
            dataSource={result.salaries.map((s, i) => ({ ...s, key: i }))}
            pagination={{ pageSize: 10, showTotal: (t) => `共 ${t} 条` }}
            locale={{ emptyText: '未找到相关薪资数据' }}
          />
        </Card>
      )}

      {!result && !searching && (
        <Empty description="输入公司名称搜索薪资数据" style={{ marginTop: 48 }} />
      )}
    </div>
  );
}

/* ========== Offer 对比 Tab ========== */

function OfferCompareTab() {
  const [form] = Form.useForm();
  const [result, setResult] = useState<OfferComparisonResult | null>(null);
  const [comparing, setComparing] = useState(false);

  const handleCompare = async () => {
    try {
      const values = await form.validateFields();
      const data: OfferCompareRequest = { offers: values.offers };
      setComparing(true);
      const res = await salaryService.compareOffers(data);
      setResult(res);
    } catch {
      /* validation error handled by antd */
      if (form.getFieldsError().every((f) => f.errors.length === 0)) {
        message.error('对比失败');
      }
    } finally {
      setComparing(false);
    }
  };

  const barOption: EChartsOption | null = result
    ? {
        tooltip: { trigger: 'axis' },
        xAxis: {
          type: 'category',
          data: result.analyses.map((a) => a.company),
        },
        yAxis: { type: 'value', name: '年包（万）' },
        series: [
          {
            name: '年包',
            type: 'bar',
            data: result.analyses.map((a) => a.totalPackage),
            itemStyle: { color: '#1677ff', borderRadius: [4, 4, 0, 0] },
          },
        ],
      }
    : null;

  const compareColumns = [
    { title: '公司', dataIndex: 'company', key: 'company', width: 100 },
    {
      title: '年包（万）',
      dataIndex: 'totalPackage',
      key: 'totalPackage',
      width: 100,
      align: 'center' as const,
      render: (v: number) => (
        <Text strong style={{ color: '#1677ff' }}>
          {v}
        </Text>
      ),
    },
    {
      title: '优势',
      dataIndex: 'pros',
      key: 'pros',
      render: (pros: string[]) =>
        pros.map((p) => (
          <Tag key={p} icon={<CheckCircleOutlined />} color="green">
            {p}
          </Tag>
        )),
    },
    {
      title: '劣势',
      dataIndex: 'cons',
      key: 'cons',
      render: (cons: string[]) =>
        cons.map((c) => (
          <Tag key={c} icon={<CloseCircleOutlined />} color="red">
            {c}
          </Tag>
        )),
    },
  ];

  return (
    <div>
      <Card title="输入 Offer 信息" style={{ marginBottom: 16 }}>
        <Form form={form} layout="vertical">
          <Form.List name="offers" initialValue={[{}, {}]}>
            {(fields, { add, remove }) => (
              <>
                {fields.map(({ key, name, ...rest }) => (
                  <Card
                    key={key}
                    size="small"
                    title={`Offer ${name + 1}`}
                    style={{ marginBottom: 12 }}
                    extra={
                      fields.length > 2 && (
                        <Button
                          type="link"
                          danger
                          icon={<MinusCircleOutlined />}
                          onClick={() => remove(name)}
                        >
                          移除
                        </Button>
                      )
                    }
                  >
                    <Space wrap>
                      <Form.Item
                        {...rest}
                        name={[name, 'company']}
                        label="公司"
                        rules={[{ required: true, message: '必填' }]}
                      >
                        <Input placeholder="如：字节跳动" style={{ width: 140 }} />
                      </Form.Item>
                      <Form.Item
                        {...rest}
                        name={[name, 'position']}
                        label="职位"
                        rules={[{ required: true, message: '必填' }]}
                      >
                        <Input placeholder="如：后端开发" style={{ width: 140 }} />
                      </Form.Item>
                      <Form.Item
                        {...rest}
                        name={[name, 'base']}
                        label="月薪(k)"
                        rules={[{ required: true, message: '必填' }]}
                      >
                        <InputNumber min={0} placeholder="35" style={{ width: 100 }} />
                      </Form.Item>
                      <Form.Item
                        {...rest}
                        name={[name, 'months']}
                        label="月数"
                        initialValue={12}
                      >
                        <InputNumber min={1} max={24} style={{ width: 80 }} />
                      </Form.Item>
                      <Form.Item {...rest} name={[name, 'bonus']} label="奖金">
                        <Input placeholder="如：3个月年终" style={{ width: 140 }} />
                      </Form.Item>
                      <Form.Item {...rest} name={[name, 'stock']} label="股票">
                        <Input placeholder="如：50万/4年" style={{ width: 140 }} />
                      </Form.Item>
                      <Form.Item {...rest} name={[name, 'location']} label="地点">
                        <Input placeholder="如：北京" style={{ width: 100 }} />
                      </Form.Item>
                    </Space>
                  </Card>
                ))}
                <Button
                  type="dashed"
                  icon={<PlusOutlined />}
                  onClick={() => add({ months: 12 })}
                  block
                >
                  添加 Offer
                </Button>
              </>
            )}
          </Form.List>
          <Button
            type="primary"
            onClick={handleCompare}
            loading={comparing}
            style={{ marginTop: 16 }}
          >
            开始对比
          </Button>
        </Form>
      </Card>

      {result && (
        <>
          <Card title="年包对比" style={{ marginBottom: 16 }}>
            {barOption && (
              <ReactEChartsCore option={barOption} style={{ height: 300 }} notMerge />
            )}
          </Card>

          <Card title="详细分析" style={{ marginBottom: 16 }}>
            <Paragraph style={{ marginBottom: 16 }}>
              <Text strong>总结：</Text>
              {result.summary}
            </Paragraph>
            <Table
              columns={compareColumns}
              dataSource={result.analyses.map((a, i) => ({ ...a, key: i }))}
              pagination={false}
            />
          </Card>

          <Card>
            <Paragraph>
              <Text strong style={{ fontSize: 16, color: '#1677ff' }}>
                推荐：{result.recommendation}
              </Text>
            </Paragraph>
          </Card>
        </>
      )}
    </div>
  );
}

/* ========== 谈判话术 Tab ========== */

function NegotiationScriptTab() {
  const [form] = Form.useForm();
  const [result, setResult] = useState<NegotiationScriptResult | null>(null);
  const [generating, setGenerating] = useState(false);

  const handleGenerate = async () => {
    try {
      const values = await form.validateFields();
      const data: NegotiationScriptRequest = {
        currentOffer: values.currentOffer,
        targetSalary: values.targetSalary ?? 0,
        negotiationStyle: values.negotiationStyle ?? 'moderate',
      };
      setGenerating(true);
      const res = await salaryService.getNegotiationScript(data);
      setResult(res);
    } catch {
      if (form.getFieldsError().every((f) => f.errors.length === 0)) {
        message.error('生成失败');
      }
    } finally {
      setGenerating(false);
    }
  };

  return (
    <div>
      <Card title="生成谈判话术" style={{ marginBottom: 24 }}>
        <Form form={form} layout="vertical" style={{ maxWidth: 600 }}>
          <Form.Item
            name="currentOffer"
            label="当前 Offer"
            rules={[{ required: true, message: '请输入当前 offer 描述' }]}
          >
            <Input.TextArea
              rows={2}
              placeholder="如：腾讯 38k * 16，无股票"
            />
          </Form.Item>
          <Form.Item name="targetSalary" label="期望年薪（万）">
            <InputNumber min={0} placeholder="50" style={{ width: 200 }} />
          </Form.Item>
          <Form.Item name="negotiationStyle" label="谈判风格" initialValue="moderate">
            <Select style={{ width: 200 }}>
              <Select.Option value="moderate">温和</Select.Option>
              <Select.Option value="assertive">坚定</Select.Option>
              <Select.Option value="conservative">保守</Select.Option>
            </Select>
          </Form.Item>
          <Button
            type="primary"
            onClick={handleGenerate}
            loading={generating}
          >
            生成话术
          </Button>
        </Form>
      </Card>

      {result && (
        <div style={{ maxWidth: 700 }}>
          <Card title="开场白" style={{ marginBottom: 16 }}>
            <Paragraph style={{ fontSize: 15, margin: 0 }}>
              {result.openingLine}
            </Paragraph>
          </Card>

          <Card title="谈判论点" style={{ marginBottom: 16 }}>
            <List
              dataSource={result.talkingPoints}
              renderItem={(item, i) => (
                <List.Item>
                  <Text>
                    {i + 1}. {item}
                  </Text>
                </List.Item>
              )}
            />
          </Card>

          <Card title="反驳话术" style={{ marginBottom: 16 }}>
            <List
              dataSource={result.counterArguments}
              renderItem={(item, i) => (
                <List.Item>
                  <Text>
                    {i + 1}. {item}
                  </Text>
                </List.Item>
              )}
            />
          </Card>

          <Card title="结束语">
            <Paragraph style={{ fontSize: 15, margin: 0 }}>
              {result.closingLine}
            </Paragraph>
          </Card>
        </div>
      )}
    </div>
  );
}

/* ========== 主页面 ========== */

export function SalaryPage() {
  const tabItems = [
    {
      key: 'search',
      label: '薪资搜索',
      children: <SalarySearchTab />,
    },
    {
      key: 'compare',
      label: 'Offer 对比',
      children: <OfferCompareTab />,
    },
    {
      key: 'script',
      label: '谈判话术',
      children: <NegotiationScriptTab />,
    },
  ];

  return (
    <div style={{ maxWidth: 1000, margin: '0 auto', padding: '24px 16px' }}>
      <PageHeader title="薪资谈判" />
      <Tabs defaultActiveKey="search" items={tabItems} />
    </div>
  );
}
