/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
import { useEffect, useState } from 'react';
import {
  Card,
  Col,
  Row,
  Spin,
  Statistic,
  Table,
  Tag,
  Typography,
  Progress,
} from 'antd';
import {
  TrophyOutlined,
  BookOutlined,
  RiseOutlined,
  FallOutlined,
  MinusOutlined,
} from '@ant-design/icons';
import ReactEChartsCore from 'echarts-for-react';
import type { EChartsOption } from 'echarts';
import { progressService } from '@/service/progressService';
import type { ProgressResponse, MasteryInfo } from '@/service/progressService';
import { PageHeader } from '@/ui/components/PageHeader';

const { Text } = Typography;

export function ProgressPage() {
  const [data, setData] = useState<ProgressResponse | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    progressService
      .getProgress('month')
      .then(setData)
      .catch(() => {
        /* handle silently */
      })
      .finally(() => setLoading(false));
  }, []);

  if (loading) {
    return (
      <div
        style={{
          minHeight: '100vh',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
        }}
      >
        <Spin size="large" tip="加载成长数据..." />
      </div>
    );
  }

  if (!data) {
    return (
      <div style={{ maxWidth: 1000, margin: '0 auto', padding: 24 }}>
        <PageHeader title="成长追踪" />
        <Text type="secondary">暂无成长数据，完成面试后将自动生成。</Text>
      </div>
    );
  }

  /* ====== ECharts 折线图 ====== */
  const trendOption: EChartsOption = {
    tooltip: { trigger: 'axis' },
    xAxis: {
      type: 'category',
      data: data.scoreTrend.map((_, i) => `第 ${i + 1} 次`),
    },
    yAxis: {
      type: 'value',
      min: 0,
      max: 100,
    },
    series: [
      {
        data: data.scoreTrend,
        type: 'line',
        smooth: true,
        lineStyle: { color: '#1677ff', width: 2 },
        itemStyle: { color: '#1677ff' },
        areaStyle: { color: 'rgba(22,119,255,0.1)' },
      },
    ],
  };

  /* ====== 知识点表格列 ====== */
  const masteryColumns = [
    {
      title: '知识点',
      dataIndex: 'point',
      key: 'point',
      width: 200,
    },
    {
      title: '首次评分',
      dataIndex: 'first',
      key: 'first',
      width: 100,
      align: 'center' as const,
    },
    {
      title: '当前评分',
      dataIndex: 'current',
      key: 'current',
      width: 100,
      align: 'center' as const,
      render: (v: number) => (
        <Text strong style={{ color: '#1677ff' }}>
          {v}
        </Text>
      ),
    },
    {
      title: '趋势',
      dataIndex: 'trend',
      key: 'trend',
      width: 80,
      align: 'center' as const,
      render: (trend: string) => {
        if (trend === 'up') return <Tag color="green" icon={<RiseOutlined />}>上升</Tag>;
        if (trend === 'down') return <Tag color="red" icon={<FallOutlined />}>下降</Tag>;
        return <Tag icon={<MinusOutlined />}>持平</Tag>;
      },
    },
  ];

  const masteryData = Object.entries(data.knowledgeMastery || {}).map(
    ([point, info]: [string, MasteryInfo]) => ({
      key: point,
      point,
      ...info,
    }),
  );

  const planProgress =
    data.studyPlan.total > 0
      ? Math.round((data.studyPlan.completed / data.studyPlan.total) * 100)
      : 0;

  return (
    <div style={{ maxWidth: 1000, margin: '0 auto', padding: '24px 16px' }}>
      <PageHeader title="成长追踪" />

      {/* 统计卡片 */}
      <Row gutter={16} style={{ marginBottom: 24 }}>
        <Col xs={24} sm={12}>
          <Card>
            <Statistic
              title="面试总次数"
              value={data.interviewCount}
              prefix={<TrophyOutlined />}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12}>
          <Card>
            <Statistic
              title="学习计划"
              value={`${data.studyPlan.completed} / ${data.studyPlan.total}`}
              prefix={<BookOutlined />}
            />
            {data.studyPlan.total > 0 && (
              <Progress
                percent={planProgress}
                size="small"
                style={{ marginTop: 12 }}
              />
            )}
          </Card>
        </Col>
      </Row>

      {/* 评分趋势折线图 */}
      <Card title="评分趋势" style={{ marginBottom: 24 }}>
        {data.scoreTrend.length > 0 ? (
          <ReactEChartsCore
            option={trendOption}
            style={{ height: 320 }}
            notMerge
          />
        ) : (
          <Text type="secondary">暂无评分数据</Text>
        )}
      </Card>

      {/* 知识点掌握度表格 */}
      <Card title="知识点掌握度">
        <Table
          columns={masteryColumns}
          dataSource={masteryData}
          pagination={false}
          locale={{ emptyText: '暂无知识点数据' }}
        />
      </Card>
    </div>
  );
}
