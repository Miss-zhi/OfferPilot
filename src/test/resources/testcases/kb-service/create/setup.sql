-- create KB test: pre-create a user for ownership
DELETE FROM op_user WHERE create_by = 'test';

INSERT INTO op_user (id, user_id, username, password_hash, email, role, enabled, created_at, updated_at, create_by, update_by)
VALUES (99001, 'u-kb-owner', 'kb_owner', '$2a$10$dummy', 'kb@test.com', 'USER', 1,
        '2024-01-01 00:00:00', '2024-01-01 00:00:00', 'test', 'test');
