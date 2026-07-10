-- verify: KB was created with correct fields
SELECT 'kb_count' AS check_point, CAST(COUNT(*) AS CHAR) AS actual, '1' AS expected
FROM kb_knowledge_base WHERE create_by = 'test';

SELECT 'kb_name' AS check_point, CAST(MAX(name) AS CHAR) AS actual, '测试知识库' AS expected
FROM kb_knowledge_base WHERE create_by = 'test';
