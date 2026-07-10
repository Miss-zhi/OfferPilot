SELECT '记录数(不应重复)' AS check_point, CAST(COUNT(*) AS CHAR) AS actual, '1' AS expected FROM op_user_memory WHERE user_id = 'mem-user-2' AND memory_key = 'skill-db';

SELECT '内容已更新' AS check_point, memory_content AS actual, '精通MySQL优化' AS expected FROM op_user_memory WHERE user_id = 'mem-user-2' AND memory_key = 'skill-db';
