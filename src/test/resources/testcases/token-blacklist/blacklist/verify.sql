SELECT '黑名单记录数' AS check_point, CAST(COUNT(*) AS CHAR) AS actual, '1' AS expected FROM op_token_blacklist WHERE user_id = 'bl-test-user';

SELECT '拉黑原因' AS check_point, reason AS actual, 'LOGOUT' AS expected FROM op_token_blacklist WHERE user_id = 'bl-test-user' LIMIT 1;

SELECT 'blacklistedAt不为空' AS check_point, CASE WHEN blacklisted_at IS NOT NULL THEN 'NOT_NULL' ELSE 'NULL' END AS actual, 'NOT_NULL' AS expected FROM op_token_blacklist WHERE user_id = 'bl-test-user' LIMIT 1;
