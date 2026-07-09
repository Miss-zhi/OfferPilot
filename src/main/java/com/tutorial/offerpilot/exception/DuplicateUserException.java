/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.exception;

public class DuplicateUserException extends BusinessException {

    public DuplicateUserException() {
        super(409, "用户名已存在");
    }
}
