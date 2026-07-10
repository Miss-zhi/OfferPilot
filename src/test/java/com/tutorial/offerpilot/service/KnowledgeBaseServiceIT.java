/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.service;

import static org.junit.jupiter.api.Assertions.*;

import com.tutorial.offerpilot.AbstractServiceIT;
import com.tutorial.offerpilot.dto.kb.CreateKbRequest;
import com.tutorial.offerpilot.dto.kb.KbResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collections;
import java.util.List;

@DisplayName("KnowledgeBaseService 集成测试")
class KnowledgeBaseServiceIT extends AbstractServiceIT {

    @Autowired
    private KnowledgeBaseService kbService;

    private static UserDetails adminUser() {
        return new User("admin", "pwd",
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    private static UserDetails normalUser(String userId) {
        return new User(userId, "pwd",
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));
    }

    // ==================== createKnowledgeBase ====================

    @Nested
    @DisplayName("createKnowledgeBase")
    class CreateTests {

        @Test
        @DisplayName("Admin 创建 → PUBLIC 可见性")
        void create_asAdmin_shouldReturnPublic() {
            CreateKbRequest req = new CreateKbRequest();
            req.setName("测试知识库");
            req.setDescription("集成测试用");
            req.setCategory("技术");

            KbResponse result = kbService.createKnowledgeBase(req, "admin-1", adminUser());

            assertNotNull(result.getKbId());
            assertTrue(result.getKbId().startsWith("kb-"));
            assertEquals("测试知识库", result.getName());
            assertEquals("PUBLIC", result.getVisibility());
            assertEquals("ACTIVE", result.getStatus());
            runVerify("kb-service/create");
        }

        @Test
        @DisplayName("普通用户创建 → PRIVATE 可见性")
        void create_asNormalUser_shouldReturnPrivate() {
            CreateKbRequest req = new CreateKbRequest();
            req.setName("我的私有库");
            req.setCategory("产品");

            KbResponse result = kbService.createKnowledgeBase(req, "u-123", normalUser("u-123"));

            assertEquals("PRIVATE", result.getVisibility());
        }
    }

    // ==================== listKnowledgeBases ====================

    @Nested
    @DisplayName("listKnowledgeBases")
    class ListTests {

        @Test
        @DisplayName("查询 → 返回 PUBLIC + 自己的 PRIVATE")
        void listAll_shouldReturnPublicAndOwnPrivate() {
            runSetup("kb-service/list");

            List<KbResponse> result = kbService.listKnowledgeBases("u-list-owner", normalUser("u-list-owner"));

            assertEquals(2, result.size());
            // Should contain both PUBLIC and own PRIVATE
            assertTrue(result.stream().anyMatch(kb -> "PUBLIC".equals(kb.getVisibility())));
            assertTrue(result.stream().anyMatch(kb -> "PRIVATE".equals(kb.getVisibility())));
            runVerify("kb-service/list");
        }

        @Test
        @DisplayName("其他用户查询 → 仅返回 PUBLIC")
        void listAll_otherUser_shouldOnlyReturnPublic() {
            runSetup("kb-service/list");

            List<KbResponse> result = kbService.listKnowledgeBases("u-other", normalUser("u-other"));

            assertEquals(1, result.size());
            assertEquals("PUBLIC", result.get(0).getVisibility());
        }
    }
}
