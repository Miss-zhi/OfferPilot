-- login test: create a user that can log in
DELETE FROM op_user WHERE create_by = 'test';

INSERT INTO op_user (id, user_id, username, password_hash, email, role, enabled, created_at, updated_at, create_by, update_by)
VALUES (99001, 'u-login-test', 'login_user', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
        'login@test.com', 'USER', 1, '2024-01-01 00:00:00', '2024-01-01 00:00:00', 'test', 'test');

-- The password_hash above is BCrypt for 'password123'
