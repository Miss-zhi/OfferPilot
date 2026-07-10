SELECT '问题记录数' AS check_point, CAST(COUNT(*) AS CHAR) AS actual, '1' AS expected FROM op_interview_question WHERE session_id = 'sess-save-001';

SELECT '技术评分' AS check_point, CAST(tech_score AS CHAR) AS actual, '85' AS expected FROM op_interview_question WHERE session_id = 'sess-save-001' LIMIT 1;

SELECT '知识点掌握记录数' AS check_point, CAST(COUNT(*) AS CHAR) AS actual, '1' AS expected FROM op_knowledge_mastery WHERE user_id = 'ia-test-user-1' AND knowledge_point = '请介绍一下Spring的依赖注入原理';
