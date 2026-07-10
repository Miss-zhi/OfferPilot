-- register test: pre-create an existing user to test duplicate detection
DELETE FROM op_user WHERE create_by = 'test';

INSERT INTO op_user (id, user_id, username, password_hash, email, role, enabled, created_at, updated_at, create_by, update_by)
VALUES (99001, 'u-existing', 'existing_user', '$2a$10$dummy_hash_for_test_user', 'existing@test.com', 'USER', 1,
        '2024-01-01 00:00:00', '2024-01-01 00:00:00', 'test', 'test');
