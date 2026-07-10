-- verify: a new user record was created after registration
SELECT 'user_count' AS check_point, CAST(COUNT(*) AS CHAR) AS actual, '2' AS expected
FROM op_user WHERE create_by = 'test';

SELECT 'new_user_exists' AS check_point, CAST(COUNT(*) AS CHAR) AS actual, '1' AS expected
FROM op_user WHERE username = 'test_register_user' AND create_by = 'test';
