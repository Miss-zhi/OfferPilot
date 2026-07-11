-- Copyright (c) 2020-06-29 Qoder. All rights reserved.

-- 面试录音六维分析增强 — op_interview_question 表字段扩展
-- 新增自信度评分和时间分配评估字段，支撑 6 维分析报告

ALTER TABLE op_interview_question
    ADD COLUMN confidence_score INT DEFAULT NULL COMMENT '自信度评分 0-100',
    ADD COLUMN time_allocation VARCHAR(32) DEFAULT NULL COMMENT '时间分配评估: GOOD/TOO_SHORT/TOO_LONG';
