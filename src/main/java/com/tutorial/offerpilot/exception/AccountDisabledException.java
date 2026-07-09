/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.exception;

public class AccountDisabledException extends BusinessException {

    public AccountDisabledException() {
        super(403, "账号已被禁用");
    }
}
