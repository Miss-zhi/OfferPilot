/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
import { Typography, Breadcrumb } from 'antd';
import { HomeOutlined } from '@ant-design/icons';

const { Title } = Typography;

interface PageHeaderProps {
  title: string;
  breadcrumb?: { label: string; path?: string }[];
}

export function PageHeader({ title, breadcrumb }: PageHeaderProps) {
  const items = breadcrumb
    ? [
        { title: <HomeOutlined />, path: '/chat' },
        ...breadcrumb.map((b) => ({
          title: b.label,
          ...(b.path ? { path: b.path } : {}),
        })),
      ]
    : undefined;

  return (
    <div style={{ marginBottom: 24 }}>
      {items && (
        <Breadcrumb
          items={items.map((item) => ({
            title: item.path ? <a href={item.path}>{item.title}</a> : item.title,
          }))}
          style={{ marginBottom: 8 }}
        />
      )}
      <Title level={4} style={{ margin: 0 }}>
        {title}
      </Title>
    </div>
  );
}
