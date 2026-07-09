/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.exception;

public class RateLimitException extends BusinessException {

    public RateLimitException(String message) {
        super(429, message);
    }
}
