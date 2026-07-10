-- Insert test memories for loadUserMemory scenario
INSERT INTO op_user_memory (user_id, memory_key, memory_content, category, relevance_score, access_count, created_at, updated_at, create_by, update_by)
VALUES ('mem-load-user', 'skill-ms', '熟悉微服务', 'PROFILE', 1.0, 0, NOW(), NOW(), 'test', 'test');

INSERT INTO op_user_memory (user_id, memory_key, memory_content, category, relevance_score, access_count, created_at, updated_at, create_by, update_by)
VALUES ('mem-load-user', 'weak-design', '系统设计能力待提升', 'WEAK_POINT', 0.8, 0, NOW(), NOW(), 'test', 'test');
