/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Card, Button, Typography, Space } from 'antd';
import { ArrowLeftOutlined } from '@ant-design/icons';
import { resumeService } from '@/service/resumeService';
import type { ResumeUploadResult } from '@/service/resumeService';
import { FileUploader } from '@/ui/components/FileUploader';
import { PageHeader } from '@/ui/components/PageHeader';

const { Text, Paragraph } = Typography;

export function ResumePage() {
  const navigate = useNavigate();
  const [uploadResult, setUploadResult] = useState<ResumeUploadResult | null>(null);

  const handleUploaded = (filePath: string, _fileName: string) => {
    setUploadResult({ filePath, fileType: 'resume', size: 0 });
  };

  return (
    <div style={{ maxWidth: 900, margin: '0 auto', padding: '24px 16px' }}>
      <Space style={{ marginBottom: 16 }}>
        <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/chat')}>
          返回对话
        </Button>
      </Space>

      <PageHeader
        title="简历诊断"
        breadcrumb={[{ label: '简历诊断', path: '/resume' }]}
      />

      {/* 上传区域 */}
      <Card title="上传简历" style={{ marginBottom: 24 }}>
        {!uploadResult ? (
          <>
            <Paragraph type="secondary">
              上传您的 PDF 简历，AI 将为您提供结构化的诊断分析和优化建议。
            </Paragraph>
            <FileUploader
              accept=".pdf"
              onUploaded={handleUploaded}
              uploadFn={(file) => resumeService.uploadResume(file)}
            />
          </>
        ) : (
          <div style={{ textAlign: 'center', padding: 20 }}>
            <Text type="success" strong>
              简历已上传成功
            </Text>
            <Paragraph
              type="secondary"
              style={{ marginTop: 8 }}
              copyable={{ text: uploadResult.filePath }}
            >
              {uploadResult.filePath}
            </Paragraph>
            <Button
              type="primary"
              onClick={() => navigate('/chat')}
              style={{ marginTop: 12 }}
            >
              前往对话获取诊断结果
            </Button>
          </div>
        )}
      </Card>

      {/* 诊断结果将在 Chat 页面通过 AI 对话展示 */}
      <Card title="诊断说明">
        <Paragraph type="secondary">
          简历诊断通过 AI 对话方式进行。上传简历后，请前往对话主页选择「简历诊断」功能，AI
          助手将基于您的简历内容提供：
        </Paragraph>
        <ul>
          <li>结构化信息解析（个人信息、教育经历、工作经历、技能等）</li>
          <li>综合评分及各维度评分</li>
          <li>针对性优化建议</li>
        </ul>
      </Card>
    </div>
  );
}
