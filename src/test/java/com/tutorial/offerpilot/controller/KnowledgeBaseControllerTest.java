/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.controller;

import com.tutorial.offerpilot.dto.kb.CreateKbRequest;
import com.tutorial.offerpilot.dto.kb.KbResponse;
import com.tutorial.offerpilot.exception.BusinessException;
import com.tutorial.offerpilot.exception.GlobalExceptionHandler;
import com.tutorial.offerpilot.service.KnowledgeBaseService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("KnowledgeBaseController Web 层测试")
class KnowledgeBaseControllerTest {

    private MockMvc mockMvc;

    @Mock
    private KnowledgeBaseService kbService;

    @InjectMocks
    private KnowledgeBaseController controller;

    private static final String USER_ID = "testuser";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();

        User user = new User(USER_ID, "", List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private KbResponse buildKbResponse(String kbId, String name) {
        KbResponse kb = new KbResponse();
        kb.setKbId(kbId);
        kb.setName(name);
        kb.setDescription("测试知识库");
        kb.setCategory("tech");
        kb.setVisibility("PRIVATE");
        kb.setStatus("ACTIVE");
        kb.setDocumentCount(0);
        kb.setChunkCount(0);
        return kb;
    }

    // ==================== POST /api/v1/kb ====================

    @Nested
    @DisplayName("POST /api/v1/kb")
    class CreateKbTests {

        @Test
        @DisplayName("正常创建 → 201 CREATED + KbResponse")
        void createKb_shouldReturn201() throws Exception {
            KbResponse kb = buildKbResponse("kb-001", "Java面试题库");
            when(kbService.createKnowledgeBase(any(CreateKbRequest.class), eq(USER_ID), any(UserDetails.class)))
                    .thenReturn(kb);

            mockMvc.perform(post("/api/v1/kb")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name": "Java面试题库", "description": "涵盖Java基础与进阶", "category": "tech"}"""))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.kbId").value("kb-001"))
                    .andExpect(jsonPath("$.data.name").value("Java面试题库"))
                    .andExpect(jsonPath("$.data.description").value("测试知识库"))
                    .andExpect(jsonPath("$.data.category").value("tech"))
                    .andExpect(jsonPath("$.data.visibility").value("PRIVATE"))
                    .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                    .andExpect(jsonPath("$.data.documentCount").value(0));

            verify(kbService).createKnowledgeBase(any(CreateKbRequest.class), eq(USER_ID), any(UserDetails.class));
        }

        @Test
        @DisplayName("名称为空 → 400 参数校验失败")
        void createKb_blankName_shouldReturn400() throws Exception {
            mockMvc.perform(post("/api/v1/kb")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name": "", "description": "desc"}"""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400));

            verify(kbService, never()).createKnowledgeBase(any(), anyString(), any());
        }

        @Test
        @DisplayName("仅 name 必填 → description 和 category 可选")
        void createKb_onlyName_shouldSucceed() throws Exception {
            KbResponse kb = buildKbResponse("kb-002", "最小知识库");
            kb.setDescription(null);
            kb.setCategory(null);
            when(kbService.createKnowledgeBase(any(CreateKbRequest.class), eq(USER_ID), any(UserDetails.class)))
                    .thenReturn(kb);

            mockMvc.perform(post("/api/v1/kb")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name": "最小知识库"}"""))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.kbId").value("kb-002"))
                    .andExpect(jsonPath("$.data.name").value("最小知识库"));
        }

        @Test
        @DisplayName("服务层异常 → 异常透传")
        void createKb_serviceError_shouldPropagate() throws Exception {
            when(kbService.createKnowledgeBase(any(CreateKbRequest.class), eq(USER_ID), any(UserDetails.class)))
                    .thenThrow(new BusinessException(409, "知识库名称已存在"));

            mockMvc.perform(post("/api/v1/kb")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name": "重复名称"}"""))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value(409))
                    .andExpect(jsonPath("$.message").value("知识库名称已存在"));
        }
    }

    // ==================== GET /api/v1/kb ====================

    @Nested
    @DisplayName("GET /api/v1/kb")
    class ListKbTests {

        @Test
        @DisplayName("正常查询 → 200 + 知识库列表")
        void listKb_shouldReturn200() throws Exception {
            KbResponse kb1 = buildKbResponse("kb-001", "Java面试题库");
            KbResponse kb2 = buildKbResponse("kb-002", "系统设计题库");
            kb2.setDocumentCount(5);
            when(kbService.listKnowledgeBases(eq(USER_ID), any(UserDetails.class)))
                    .thenReturn(List.of(kb1, kb2));

            mockMvc.perform(get("/api/v1/kb"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.length()").value(2))
                    .andExpect(jsonPath("$.data[0].kbId").value("kb-001"))
                    .andExpect(jsonPath("$.data[0].name").value("Java面试题库"))
                    .andExpect(jsonPath("$.data[1].kbId").value("kb-002"))
                    .andExpect(jsonPath("$.data[1].documentCount").value(5));

            verify(kbService).listKnowledgeBases(eq(USER_ID), any(UserDetails.class));
        }

        @Test
        @DisplayName("无知识库 → 返回空列表")
        void listKb_empty_shouldReturnEmptyList() throws Exception {
            when(kbService.listKnowledgeBases(eq(USER_ID), any(UserDetails.class))).thenReturn(List.of());

            mockMvc.perform(get("/api/v1/kb"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data").isEmpty());
        }

        @Test
        @DisplayName("服务层异常 → 500 内部错误")
        void listKb_serviceError_shouldReturn500() throws Exception {
            when(kbService.listKnowledgeBases(eq(USER_ID), any(UserDetails.class)))
                    .thenThrow(new RuntimeException("DB error"));

            mockMvc.perform(get("/api/v1/kb"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code").value(500));
        }
    }

    // ==================== DELETE /api/v1/kb/{kbId} ====================

    @Nested
    @DisplayName("DELETE /api/v1/kb/{kbId}")
    class DeleteKbTests {

        @Test
        @DisplayName("正常删除 → 204 No Content")
        void deleteKb_shouldReturn204() throws Exception {
            doNothing().when(kbService).deleteKnowledgeBase(eq("kb-001"), eq(USER_ID), any(UserDetails.class));

            mockMvc.perform(delete("/api/v1/kb/kb-001"))
                    .andExpect(status().isNoContent())
                    .andExpect(content().string(""));

            verify(kbService).deleteKnowledgeBase(eq("kb-001"), eq(USER_ID), any(UserDetails.class));
        }

        @Test
        @DisplayName("知识库不存在 → 404")
        void deleteKb_notFound_shouldReturn404() throws Exception {
            doThrow(new BusinessException(404, "知识库不存在"))
                    .when(kbService).deleteKnowledgeBase(eq("kb-999"), eq(USER_ID), any(UserDetails.class));

            mockMvc.perform(delete("/api/v1/kb/kb-999"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value(404))
                    .andExpect(jsonPath("$.message").value("知识库不存在"));
        }

        @Test
        @DisplayName("无权删除 → 403 Forbidden")
        void deleteKb_forbidden_shouldReturn403() throws Exception {
            doThrow(new BusinessException(403, "无权删除此知识库"))
                    .when(kbService).deleteKnowledgeBase(eq("kb-others"), eq(USER_ID), any(UserDetails.class));

            mockMvc.perform(delete("/api/v1/kb/kb-others"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value(403))
                    .andExpect(jsonPath("$.message").value("无权删除此知识库"));
        }
    }
}
