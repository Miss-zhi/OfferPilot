-- verify: a new user record was created after registration
SELECT 'new_user_exists' AS check_point, CAST(COUNT(*) AS CHAR) AS actual, '1' AS expected
FROM op_user WHERE username = 'test_register_user';
