-- Create session with questions for generateReport test
INSERT INTO op_interview_session (session_id, user_id, session_type, interview_mode, status, overall_score, question_count, started_at, created_at, updated_at, create_by, update_by)
VALUES ('sess-gen-001', 'ia-test-user', 'TECHNICAL', 'voice', 'ACTIVE', NULL, 2, NOW(), NOW(), NOW(), 'test', 'test');

INSERT INTO op_interview_question (session_id, question_id, question_text, answer_text, tech_score, expr_score, coverage_score, sort_order, created_at, updated_at, create_by, update_by)
VALUES ('sess-gen-001', 'q-001', '请介绍Java的内存模型', '堆栈方法区...', 85, 80, 90, 0, NOW(), NOW(), 'test', 'test');

INSERT INTO op_interview_question (session_id, question_id, question_text, answer_text, tech_score, expr_score, coverage_score, sort_order, created_at, updated_at, create_by, update_by)
VALUES ('sess-gen-001', 'q-002', '什么是GC，如何调优', 'GC是垃圾回收...', 70, 75, 65, 1, NOW(), NOW(), 'test', 'test');
