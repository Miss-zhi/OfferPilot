/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.converter;

import com.tutorial.offerpilot.dto.kb.KbResponse;
import com.tutorial.offerpilot.entity.KbKnowledgeBase;
import org.springframework.stereotype.Component;

@Component
public class KbConverter {

    public KbResponse toResponse(KbKnowledgeBase kb) {
        KbResponse resp = new KbResponse();
        resp.setKbId(kb.getKbId());
        resp.setName(kb.getName());
        resp.setDescription(kb.getDescription());
        resp.setCategory(kb.getCategory());
        resp.setVisibility(kb.getVisibility());
        resp.setStatus(kb.getStatus());
        resp.setDocumentCount(kb.getDocumentCount());
        resp.setChunkCount(kb.getChunkCount());
        return resp;
    }
}
