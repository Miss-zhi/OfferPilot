/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
import http from '@/infra/http';
import { API } from '@/infra/constants';

export interface MasteryInfo {
  first: number;
  current: number;
  trend: string;
}

export interface StudyPlanInfo {
  completed: number;
  total: number;
}

export interface ProgressResponse {
  period: string;
  interviewCount: number;
  scoreTrend: number[];
  knowledgeMastery: Record<string, MasteryInfo>;
  studyPlan: StudyPlanInfo;
}

export const progressService = {
  getProgress: (range = 'month') =>
    http
      .get<ProgressResponse>(API.PROGRESS, { params: { range } })
      .then((res) => res.data),
};
