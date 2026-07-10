SELECT '过期记录已清理' AS check_point, CAST(COUNT(*) AS CHAR) AS actual, '1' AS expected FROM op_token_blacklist WHERE user_id = 'cleanup-user' AND create_by = 'test';

SELECT '保留未过期记录' AS check_point, token_jti AS actual, 'active-jti-001' AS expected FROM op_token_blacklist WHERE user_id = 'cleanup-user' AND create_by = 'test' LIMIT 1;
