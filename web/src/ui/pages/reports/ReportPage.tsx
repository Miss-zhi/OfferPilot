/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
import { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Card, Spin, Button, List, Typography, Space } from 'antd';
import { ArrowLeftOutlined } from '@ant-design/icons';
import { reportService } from '@/service/reportService';
import type { AnalysisReport } from '@/service/reportService';
import { ScoreRadarChart } from '@/ui/components/ScoreRadarChart';
import { PageHeader } from '@/ui/components/PageHeader';

const { Text, Paragraph } = Typography;

interface DimensionData {
  name: string;
  score: number;
}

interface DetailItem {
  question: string;
  score: number;
  comment: string;
}

interface ImprovementItem {
  dimension: string;
  suggestion: string;
}

export function ReportPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [report, setReport] = useState<AnalysisReport | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!id) return;
    reportService
      .getReport(id)
      .then(setReport)
      .catch(() => {
        // Silently handle, report stays null
      })
      .finally(() => setLoading(false));
  }, [id]);

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
        <Spin size="large" tip="加载报告..." />
      </div>
    );
  }

  if (!report) {
    return (
      <div style={{ padding: 24 }}>
        <PageHeader title="报告未找到" />
        <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/chat')}>
          返回对话
        </Button>
      </div>
    );
  }

  let dimensions: DimensionData[] = [];
  let details: DetailItem[] = [];
  let improvements: ImprovementItem[] = [];

  try {
    dimensions = JSON.parse(report.dimensionsJson || '[]');
    details = JSON.parse(report.detailsJson || '[]');
    improvements = JSON.parse(report.improvementsJson || '[]');
  } catch {
    // JSON parse error, use empty arrays
  }

  return (
    <div style={{ maxWidth: 900, margin: '0 auto', padding: '24px 16px' }}>
      <Space style={{ marginBottom: 16 }}>
        <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/chat')}>
          返回对话
        </Button>
      </Space>

      <PageHeader title="面试分析报告" />

      {/* 总评 */}
      <Card style={{ marginBottom: 24, textAlign: 'center' }}>
        <Text type="secondary">综合评分</Text>
        <div
          style={{
            fontSize: 56,
            fontWeight: 700,
            color: '#1677ff',
            lineHeight: 1.2,
          }}
        >
          {report.overallScore ?? '—'}
        </div>
        <Text type="secondary">{report.reportType}</Text>
      </Card>

      {/* 六维度雷达图 */}
      {dimensions.length > 0 && (
        <Card title="各维度评分" style={{ marginBottom: 24 }}>
          <ScoreRadarChart dimensions={dimensions} />
        </Card>
      )}

      {/* 各题详情 */}
      {details.length > 0 && (
        <Card title="各题详情" style={{ marginBottom: 24 }}>
          <List
            dataSource={details}
            renderItem={(item, idx) => (
              <List.Item>
                <div style={{ width: '100%' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 4 }}>
                    <Text strong>
                      Q{idx + 1}. {item.question}
                    </Text>
                    <Text type="success" strong>
                      {item.score} 分
                    </Text>
                  </div>
                  <Paragraph type="secondary" style={{ margin: 0 }}>
                    {item.comment}
                  </Paragraph>
                </div>
              </List.Item>
            )}
          />
        </Card>
      )}

      {/* 改进建议 */}
      {improvements.length > 0 && (
        <Card title="改进建议" style={{ marginBottom: 24 }}>
          <List
            dataSource={improvements}
            renderItem={(item) => (
              <List.Item>
                <div>
                  <Text strong>{item.dimension}：</Text>
                  <Text>{item.suggestion}</Text>
                </div>
              </List.Item>
            )}
          />
        </Card>
      )}
    </div>
  );
}
