/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableJpaAuditing
@EnableJpaRepositories("com.tutorial.offerpilot.repository")
@EntityScan("com.tutorial.offerpilot.entity")
@EnableScheduling
public class OfferPilotApplication {

    public static void main(String[] args) {
        SpringApplication.run(OfferPilotApplication.class, args);
    }
}
