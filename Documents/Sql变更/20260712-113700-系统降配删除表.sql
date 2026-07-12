# Copyright (c) 2020-06-29 Qoder. All rights reserved.

-- OfferPilot 系统降配架构简化 — 删除不再使用的表
-- 执行前请务必备份数据库

-- 薪资谈判相关
DROP TABLE IF EXISTS salary_record;

-- 学习计划相关
DROP TABLE IF EXISTS study_plan;
DROP TABLE IF EXISTS knowledge_mastery;

-- 定时任务（学习计划提醒为主）
DROP TABLE IF EXISTS scheduled_task_log;

-- 注意：model_name 表保留，不删除
