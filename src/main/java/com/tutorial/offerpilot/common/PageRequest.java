/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.common;

import lombok.Data;

@Data
public class PageRequest {

    private int pageNo = 1;
    private int pageSize = 20;

    public int getOffset() {
        return (pageNo - 1) * pageSize;
    }
}
