/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.exception;

public class InvalidCredentialsException extends BusinessException {

    public InvalidCredentialsException() {
        super(401, "用户名或密码错误");
    }
}
