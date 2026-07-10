SELECT '记忆记录数' AS check_point, CAST(COUNT(*) AS CHAR) AS actual, '1' AS expected FROM op_user_memory WHERE user_id = 'mem-user-1';

SELECT '记忆Key' AS check_point, memory_key AS actual, 'skill-java' AS expected FROM op_user_memory WHERE user_id = 'mem-user-1';

SELECT '记忆类别' AS check_point, category AS actual, 'PROFILE' AS expected FROM op_user_memory WHERE user_id = 'mem-user-1';
