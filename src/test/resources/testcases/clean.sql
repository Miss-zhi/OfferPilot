-- ============================================================
-- Global cleanup script (idempotent, DELETE WHERE create_by = 'test')
-- Delete order: child tables first, parent tables last
-- ============================================================

DELETE FROM kb_search_log WHERE create_by = 'test';
DELETE FROM kb_chunk WHERE create_by = 'test';
DELETE FROM kb_document WHERE create_by = 'test';
DELETE FROM op_interview_question WHERE create_by = 'test';
DELETE FROM op_analysis_report WHERE create_by = 'test';
DELETE FROM op_interview_session WHERE create_by = 'test';
DELETE FROM op_user_memory WHERE create_by = 'test';
DELETE FROM op_knowledge_mastery WHERE create_by = 'test';
DELETE FROM op_study_plan WHERE create_by = 'test';
DELETE FROM op_salary_record WHERE create_by = 'test';
DELETE FROM op_file WHERE create_by = 'test';
DELETE FROM op_token_blacklist WHERE create_by = 'test';
DELETE FROM op_scheduled_task_log WHERE create_by = 'test';
DELETE FROM kb_knowledge_base WHERE create_by = 'test';
DELETE FROM op_user WHERE create_by = 'test';
DELETE FROM op_model_name WHERE create_by = 'test';
DELETE FROM op_model_config WHERE create_by = 'test';
