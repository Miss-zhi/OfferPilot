-- verify: all KBs exist
SELECT 'kb_total' AS check_point, CAST(COUNT(*) AS CHAR) AS actual, '2' AS expected
FROM kb_knowledge_base WHERE create_by = 'test';
