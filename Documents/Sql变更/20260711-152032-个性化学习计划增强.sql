-- Copyright (c) 2020-06-29 Qoder. All rights reserved.
-- 个性化学习计划增强：op_study_plan 新增字段

ALTER TABLE op_study_plan
    ADD COLUMN priority_order INT DEFAULT 0 COMMENT '优先级排序权重',
    ADD COLUMN last_updated DATETIME DEFAULT NULL COMMENT '最后更新时间',
    ADD COLUMN reminder_enabled TINYINT(1) DEFAULT 1 COMMENT '是否启用学习提醒';

-- op_analysis_report 的 report_type 字段已支持自定义值，
-- 周报直接使用 'WEEKLY_REPORT'，无需改表。
