/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import io.milvus.v2.client.MilvusClientV2;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Abstract base class for Service-layer integration tests.
 * <p>
 * Spins up a testcontainers MySQL instance, applies JPA schema auto-creation,
 * and provides {@link #runSetup} / {@link #runVerify} helpers that operate
 * against SQL files under {@code testcases/{scenario}/}.
 * </p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class AbstractServiceIT {

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>(
            DockerImageName.parse("mysql:8.0")
    ).withDatabaseName("offerpilot_test");

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    /**
     * Mock Milvus client — service IT tests only need DB, not vector search.
     */
    @MockBean
    protected MilvusClientV2 milvusClient;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    /**
     * Clean all test data before each test case.
     * Test data is identified by {@code create_by = 'test'}.
     */
    @BeforeEach
    void cleanDatabase() {
        executeSqlFile("testcases/clean.sql");
    }

    // ────────────────────────────── helpers ──────────────────────────────

    /**
     * Execute a setup.sql for the given scenario.
     * <p>
     * Reads from {@code testcases/{scenario}/setup.sql} and executes each statement.
     * </p>
     */
    protected void runSetup(String scenario) {
        executeSqlFile("testcases/" + scenario + "/setup.sql");
    }

    /**
     * Execute a verify.sql for the given scenario and assert every check-point.
     * <p>
     * Each SELECT in verify.sql must return three columns:
     * {@code check_point / actual / expected}.
     * The base class runs each SELECT and asserts {@code actual == expected}.
     * </p>
     */
    protected void runVerify(String scenario) {
        String sql = readSqlFile("testcases/" + scenario + "/verify.sql");
        if (sql == null || sql.isBlank()) {
            throw new IllegalStateException("verify.sql not found for scenario: " + scenario);
        }

        // Split by semicolons and execute each SELECT individually
        String[] statements = sql.split(";");
        for (String stmt : statements) {
            String trimmed = stmt.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(trimmed);
            for (Map<String, Object> row : rows) {
                String checkPoint = String.valueOf(row.get("check_point"));
                String actual = String.valueOf(row.get("actual"));
                String expected = String.valueOf(row.get("expected"));
                assertEquals(expected, actual,
                        "Verify failed for [" + scenario + "] → " + checkPoint);
            }
        }
    }

    // ─────────────────────────── internal helpers ─────────────────────────

    private void executeSqlFile(String path) {
        String sql = readSqlFile(path);
        if (sql == null || sql.isBlank()) {
            throw new IllegalStateException("SQL file not found or empty: " + path);
        }
        // Split by semicolons to support multiple statements
        // JdbcTemplate.execute supports multi-statement when MySQL allows it
        String[] statements = sql.split(";");
        for (String stmt : statements) {
            String trimmed = stmt.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("--")) {
                continue;
            }
            jdbcTemplate.execute(trimmed);
        }
    }

    private String readSqlFile(String path) {
        try {
            ClassPathResource resource = new ClassPathResource(path);
            if (!resource.exists()) {
                return null;
            }
            try (InputStream is = resource.getInputStream()) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read SQL file: " + path, e);
        }
    }
}
