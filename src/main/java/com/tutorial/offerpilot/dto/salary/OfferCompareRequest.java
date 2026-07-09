/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.dto.salary;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class OfferCompareRequest {

    @NotEmpty(message = "至少提供两个 offer")
    private List<OfferItem> offers;

    @Data
    public static class OfferItem {
        private String company;
        private String position;
        private Double base;
        private Integer months;
        private String bonus;
        private String stock;
        private String location;
    }
}
