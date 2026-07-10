SELECT '记忆已删除' AS check_point, CAST(COUNT(*) AS CHAR) AS actual, '0' AS expected FROM op_user_memory WHERE user_id = 'mem-rm-user' AND memory_key = 'weak-point';
