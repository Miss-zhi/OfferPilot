/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.entity;

import com.tutorial.offerpilot.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "op_salary_record", indexes = @Index(name = "idx_salary_user_company", columnList = "userId,companyName"))
@Getter
@Setter
public class SalaryRecord extends BaseEntity {

    @Column(nullable = false, length = 64)
    private String userId;

    @Column(nullable = false, length = 128)
    private String companyName;

    @Column(length = 128)
    private String position;

    @Column(precision = 10, scale = 2)
    private BigDecimal baseSalary;

    private Integer months;

    @Column(length = 256)
    private String bonusInfo;

    @Column(length = 256)
    private String stockInfo;

    @Column(columnDefinition = "TEXT")
    private String otherBenefits;

    @Column(precision = 10, scale = 2)
    private BigDecimal totalPackage;

    @Column(length = 128)
    private String source;

    @Column(columnDefinition = "TEXT")
    private String notes;
}
