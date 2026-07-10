/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
import ReactEChartsCore from 'echarts-for-react';
import type { EChartsOption } from 'echarts';

interface ScoreRadarChartProps {
  dimensions: { name: string; score: number }[];
}

export function ScoreRadarChart({ dimensions }: ScoreRadarChartProps) {
  const maxScore = Math.max(...dimensions.map((d) => d.score), 100);

  const option: EChartsOption = {
    radar: {
      indicator: dimensions.map((d) => ({
        name: d.name,
        max: maxScore,
      })),
      center: ['50%', '55%'],
      radius: '70%',
    },
    series: [
      {
        type: 'radar',
        data: [
          {
            value: dimensions.map((d) => d.score),
            name: '评分',
            areaStyle: { color: 'rgba(22, 119, 255, 0.2)' },
            lineStyle: { color: '#1677ff', width: 2 },
            itemStyle: { color: '#1677ff' },
          },
        ],
      },
    ],
  };

  return (
    <ReactEChartsCore
      option={option}
      style={{ height: 360, width: '100%' }}
      notMerge
    />
  );
}
