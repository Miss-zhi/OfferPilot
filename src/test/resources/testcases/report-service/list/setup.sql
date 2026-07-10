-- Pre-insert two analysis reports for list/get tests
INSERT INTO op_analysis_report (report_id, user_id, session_id, report_type, overall_score, dimensions_json, details_json, improvements_json, created_at, updated_at, create_by, update_by)
VALUES ('rpt-get-001', 'rpt-list-user', 'sess-a-001', 'INTERVIEW_ANALYSIS', 85,
        '{"techScore":85,"exprScore":80,"coverageScore":90}',
        '[{"question":"Q1","techScore":85}]',
        '["加强基础知识"]',
        NOW(), NOW(), 'test', 'test');

INSERT INTO op_analysis_report (report_id, user_id, session_id, report_type, overall_score, dimensions_json, details_json, improvements_json, created_at, updated_at, create_by, update_by)
VALUES ('rpt-get-002', 'rpt-list-user', 'sess-a-002', 'RESUME_ANALYSIS', 70,
        '{"completeness":70,"expression":65,"match":75}',
        '[{"question":"简历分析"}]',
        '["补充项目经验"]',
        NOW(), NOW(), 'test', 'test');
