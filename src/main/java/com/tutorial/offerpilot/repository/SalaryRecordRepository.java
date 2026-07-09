/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.repository;

import com.tutorial.offerpilot.entity.SalaryRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SalaryRecordRepository extends JpaRepository<SalaryRecord, Long> {

    List<SalaryRecord> findByUserId(String userId);

    List<SalaryRecord> findByUserIdAndCompanyName(String userId, String companyName);
}
