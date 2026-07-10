-- list KB test: create multiple KBs with different visibility
DELETE FROM kb_knowledge_base WHERE create_by = 'test';
DELETE FROM op_user WHERE create_by = 'test';

INSERT INTO op_user (id, user_id, username, password_hash, email, role, enabled, created_at, updated_at, create_by, update_by)
VALUES (99001, 'u-list-owner', 'list_owner', '$2a$10$dummy', 'list@test.com', 'USER', 1,
        '2024-01-01 00:00:00', '2024-01-01 00:00:00', 'test', 'test');

INSERT INTO kb_knowledge_base (id, kb_id, name, description, milvus_collection, category, owner_id, visibility, status, document_count, chunk_count, created_at, updated_at, create_by, update_by)
VALUES (99001, 'kb-list-1', '公共知识库', 'public KB', 'col_pub', '技术', NULL, 'PUBLIC', 'ACTIVE', 0, 0,
        '2024-01-01 00:00:00', '2024-01-01 00:00:00', 'test', 'test');

INSERT INTO kb_knowledge_base (id, kb_id, name, description, milvus_collection, category, owner_id, visibility, status, document_count, chunk_count, created_at, updated_at, create_by, update_by)
VALUES (99002, 'kb-list-2', '私有知识库', 'private KB', 'col_priv', '产品', 'u-list-owner', 'PRIVATE', 'ACTIVE', 0, 0,
        '2024-01-01 00:00:00', '2024-01-01 00:00:00', 'test', 'test');
