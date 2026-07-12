/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.agent.tool;

import com.tutorial.offerpilot.dto.kb.KbResponse;
import com.tutorial.offerpilot.dto.tool.KbListResult;
import com.tutorial.offerpilot.service.KnowledgeBaseService;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 知识库列表查询工具。
 * 当用户询问"知识库里有什么""有哪些知识库"等元信息查询时，Agent 应调用此工具。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ListKnowledgeBasesTool {

    private final KnowledgeBaseService kbService;

    @Tool(name = "list_knowledge_bases", description = "列出当前用户可访问的所有知识库及其文档数量统计"
            + "。当用户问'知识库里有什么'、'有哪些知识库'、'当前知识库列表'时使用此工具。")
    public KbListResult listKnowledgeBases(
            @ToolParam(name = "name_filter", description = "按名称模糊过滤（可选），如 'java'", required = false) String nameFilter) {
        log.info("list_knowledge_bases called: nameFilter={}", nameFilter);

        // 从 Spring Security 上下文获取当前用户
        String userId = getCurrentUserId();
        List<KbResponse> kbs = kbService.listKnowledgeBases(userId, null);

        List<KbListResult.KbInfo> items = kbs.stream()
                .filter(kb -> nameFilter == null || nameFilter.isBlank()
                        || (kb.getName() != null && kb.getName().toLowerCase().contains(nameFilter.toLowerCase())))
                .map(kb -> {
                    KbListResult.KbInfo info = new KbListResult.KbInfo();
                    info.setKbId(kb.getKbId());
                    info.setName(kb.getName());
                    info.setDescription(kb.getDescription());
                    info.setVisibility(kb.getVisibility());
                    info.setDocumentCount(kb.getDocumentCount());
                    info.setChunkCount(kb.getChunkCount());
                    return info;
                })
                .collect(Collectors.toList());

        KbListResult result = new KbListResult();
        result.setTotal(items.size());
        result.setItems(items);

        log.info("list_knowledge_bases result: total={}", items.size());
        return result;
    }

    private String getCurrentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserDetails userDetails) {
            return userDetails.getUsername();
        }
        log.warn("No authenticated user found in SecurityContext, returning empty result");
        return "";
    }
}
