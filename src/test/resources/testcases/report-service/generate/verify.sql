SELECT '报告已生成' AS check_point, CAST(COUNT(*) AS CHAR) AS actual, '1' AS expected FROM op_analysis_report WHERE session_id = 'sess-rpt-001';

SELECT 'userId正确' AS check_point, user_id AS actual, 'rpt-test-user' AS expected FROM op_analysis_report WHERE session_id = 'sess-rpt-001' LIMIT 1;
