SELECT '报告已生成' AS check_point, CAST(COUNT(*) AS CHAR) AS actual, '1' AS expected FROM op_analysis_report WHERE session_id = 'sess-gen-001';

SELECT '报告类型' AS check_point, report_type AS actual, 'INTERVIEW_ANALYSIS' AS expected FROM op_analysis_report WHERE session_id = 'sess-gen-001' LIMIT 1;

SELECT 'userId正确' AS check_point, user_id AS actual, 'ia-test-user' AS expected FROM op_analysis_report WHERE session_id = 'sess-gen-001' LIMIT 1;

SELECT 'Session评分已更新' AS check_point, CASE WHEN overall_score IS NOT NULL THEN 'SET' ELSE 'NULL' END AS actual, 'SET' AS expected FROM op_interview_session WHERE session_id = 'sess-gen-001';
