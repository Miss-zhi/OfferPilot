/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
import http from '@/infra/http';
import { API } from '@/infra/constants';

export interface AnalysisReport {
  reportId: string;
  userId: string;
  sessionId: string;
  reportType: string;
  overallScore: number;
  dimensionsJson: string;
  detailsJson: string;
  improvementsJson: string;
  createdAt?: string;
}

export const reportService = {
  listReports: () =>
    http.get<AnalysisReport[]>(API.REPORTS).then((res) => res.data),

  getReport: (id: string) =>
    http
      .get<AnalysisReport>(API.REPORT_DETAIL(id))
      .then((res) => res.data),
};
