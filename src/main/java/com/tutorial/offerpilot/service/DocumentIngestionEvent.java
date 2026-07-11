/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.service;

import org.springframework.context.ApplicationEvent;

/**
 * 文档入库事件，在事务提交后由 ApplicationEventPublisher 发布，
 * 触发 DocumentIngestionService 的异步入库流程。
 */
public class DocumentIngestionEvent extends ApplicationEvent {

    private final String docId;

    public DocumentIngestionEvent(Object source, String docId) {
        super(source);
        this.docId = docId;
    }

    public String getDocId() {
        return docId;
    }
}
