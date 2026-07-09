/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.exception;

public class FileUploadException extends BusinessException {

    public FileUploadException(String message) {
        super(400, message);
    }
}
