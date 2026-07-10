/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
import http from '@/infra/http';
import { API } from '@/infra/constants';

/* ========== 薪资搜索 ========== */

export interface SalaryItem {
  company: string;
  position: string;
  baseRange: string;
  bonusRange: string;
  stockInfo: string;
  source: string;
  relevanceScore: number;
}

export interface SalarySearchResult {
  total: number;
  salaries: SalaryItem[];
}

/* ========== Offer 对比 ========== */

export interface OfferItem {
  company: string;
  position: string;
  base: number;
  months: number;
  bonus: string;
  stock: string;
  location: string;
}

export interface OfferCompareRequest {
  offers: OfferItem[];
}

export interface OfferAnalysis {
  company: string;
  totalPackage: number;
  pros: string[];
  cons: string[];
}

export interface OfferComparisonResult {
  summary: string;
  analyses: OfferAnalysis[];
  recommendation: string;
}

/* ========== 谈判话术 ========== */

export interface NegotiationScriptRequest {
  currentOffer: string;
  targetSalary: number;
  negotiationStyle: string;
}

export interface NegotiationScriptResult {
  openingLine: string;
  talkingPoints: string[];
  counterArguments: string[];
  closingLine: string;
}

/* ========== Service ========== */

export const salaryService = {
  searchSalary: (company: string, position?: string) =>
    http
      .get<SalarySearchResult>(API.SALARY_SEARCH, {
        params: { company, ...(position ? { position } : {}) },
      })
      .then((res) => res.data),

  compareOffers: (data: OfferCompareRequest) =>
    http
      .post<OfferComparisonResult>(API.SALARY_COMPARE, data)
      .then((res) => res.data),

  getNegotiationScript: (data: NegotiationScriptRequest) =>
    http
      .post<NegotiationScriptResult>(API.SALARY_NEGOTIATION_SCRIPT, data)
      .then((res) => res.data),
};
