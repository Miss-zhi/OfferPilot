-- Expired token blacklist entry (expired 1 hour ago)
INSERT INTO op_token_blacklist (token_jti, user_id, expire_at, blacklisted_at, reason, created_at, updated_at, create_by, update_by)
VALUES ('expired-jti-001', 'cleanup-user', DATE_SUB(NOW(), INTERVAL 1 HOUR), DATE_SUB(NOW(), INTERVAL 2 HOUR), 'LOGOUT', NOW(), NOW(), 'test', 'test');

-- Expired token blacklist entry (expired 1 day ago)
INSERT INTO op_token_blacklist (token_jti, user_id, expire_at, blacklisted_at, reason, created_at, updated_at, create_by, update_by)
VALUES ('expired-jti-002', 'cleanup-user', DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), 'PASSWORD_CHANGE', NOW(), NOW(), 'test', 'test');

-- Active (non-expired) token blacklist entry (expires in 1 day)
INSERT INTO op_token_blacklist (token_jti, user_id, expire_at, blacklisted_at, reason, created_at, updated_at, create_by, update_by)
VALUES ('active-jti-001', 'cleanup-user', DATE_ADD(NOW(), INTERVAL 1 DAY), NOW(), 'LOGOUT', NOW(), NOW(), 'test', 'test');
