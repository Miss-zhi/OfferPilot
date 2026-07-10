SELECT '仅一条问题记录' AS check_point, CAST(COUNT(*) AS CHAR) AS actual, '1' AS expected FROM op_interview_question WHERE session_id = 'sess-save-002';

SELECT '评分已更新-techScore' AS check_point, CAST(tech_score AS CHAR) AS actual, '90' AS expected FROM op_interview_question WHERE session_id = 'sess-save-002' LIMIT 1;

SELECT '回答已更新' AS check_point, answer_text AS actual, '微服务架构...' AS expected FROM op_interview_question WHERE session_id = 'sess-save-002' LIMIT 1;
SELECT '仅一条问题记录' AS check_point, CAST(COUNT(*) AS CHAR) AS actual, '1' AS expected FROM op_interview_question WHERE session_id = 'sess-save-002';

SELECT '评分已更新-techScore' AS check_point, CAST(tech_score AS CHAR) AS actual, '90' AS expected FROM op_interview_question WHERE session_id = 'sess-save-002' LIMIT 1;

SELECT '回答已更新' AS check_point, answer_text AS actual, '微服务架构...' AS expected FROM op_interview_question WHERE session_id = 'sess-save-002' LIMIT 1;
