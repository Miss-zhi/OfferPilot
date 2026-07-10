-- verify: last_login_at was updated after successful login
SELECT 'last_login_set' AS check_point, CAST(COUNT(*) AS CHAR) AS actual, '1' AS expected
FROM op_user WHERE username = 'login_user' AND last_login_at IS NOT NULL;
