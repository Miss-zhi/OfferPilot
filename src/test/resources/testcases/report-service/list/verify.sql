SELECT '报告总数' AS check_point, CAST(COUNT(*) AS CHAR) AS actual, '2' AS expected FROM op_analysis_report WHERE user_id = 'rpt-list-user' AND create_by = 'test';
