-- Create session for saveAnalysis test
INSERT INTO op_interview_session (session_id, user_id, session_type, interview_mode, status, started_at, created_at, updated_at, create_by, update_by)
VALUES ('sess-save-001', 'ia-test-user', 'TECHNICAL', 'text', 'ACTIVE', NOW(), NOW(), NOW(), 'test', 'test');
