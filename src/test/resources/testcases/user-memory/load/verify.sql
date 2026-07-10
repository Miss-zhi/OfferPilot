SELECT 'accessCount已+1(skill-ms)' AS check_point, CAST(access_count AS CHAR) AS actual, '1' AS expected FROM op_user_memory WHERE user_id = 'mem-load-user' AND memory_key = 'skill-ms';

SELECT 'accessCount已+1(weak-design)' AS check_point, CAST(access_count AS CHAR) AS actual, '1' AS expected FROM op_user_memory WHERE user_id = 'mem-load-user' AND memory_key = 'weak-design';

SELECT 'lastAccessed已更新(skill-ms)' AS check_point, CASE WHEN last_accessed IS NOT NULL THEN 'NOT_NULL' ELSE 'NULL' END AS actual, 'NOT_NULL' AS expected FROM op_user_memory WHERE user_id = 'mem-load-user' AND memory_key = 'skill-ms';
