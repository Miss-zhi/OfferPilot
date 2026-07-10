-- Create session with questions for generateReport test
INSERT INTO op_interview_session (session_id, user_id, session_type, interview_mode, status, started_at, created_at, updated_at, create_by, update_by)
VALUES ('sess-rpt-001', 'rpt-test-user', 'TECHNICAL', 'text', 'ACTIVE', NOW(), NOW(), NOW(), 'test', 'test');

INSERT INTO op_interview_question (session_id, question_id, question_text, answer_text, tech_score, expr_score, coverage_score, sort_order, created_at, updated_at, create_by, update_by)
VALUES ('sess-rpt-001', 'q-r1', '请介绍Redis的数据结构', 'String、Hash、List...', 90, 85, 80, 0, NOW(), NOW(), 'test', 'test');

INSERT INTO op_interview_question (session_id, question_id, question_text, answer_text, tech_score, expr_score, coverage_score, sort_order, created_at, updated_at, create_by, update_by)
VALUES ('sess-rpt-001', 'q-r2', '如何设计一个高并发系统', '缓存、异步、分库分表...', 75, 80, 70, 1, NOW(), NOW(), 'test', 'test');
