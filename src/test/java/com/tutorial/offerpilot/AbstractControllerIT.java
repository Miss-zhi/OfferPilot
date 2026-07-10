/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot;

import com.tutorial.offerpilot.entity.AppUser;
import com.tutorial.offerpilot.enums.UserRole;
import com.tutorial.offerpilot.repository.AppUserRepository;
import com.tutorial.offerpilot.security.JwtTokenProvider;
import com.tutorial.offerpilot.service.RateLimitService;
import io.agentscope.core.agent.Agent;
import io.milvus.v2.client.MilvusClientV2;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Abstract base class for Controller-layer integration tests.
 * <p>
 * Spins up a testcontainers MySQL instance, configures MockMvc for full HTTP
 * testing, and mocks external dependencies (Milvus, Redis, AgentScope).
 * </p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class AbstractControllerIT {

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>(
            DockerImageName.parse("mysql:8.0")
    ).withDatabaseName("offerpilot_test");

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected JwtTokenProvider jwtTokenProvider;

    @Autowired
    protected AppUserRepository userRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    /**
     * Mock external dependencies that aren't available in CI/test.
     */
    @MockBean
    protected MilvusClientV2 milvusClient;

    @MockBean
    protected StringRedisTemplate stringRedisTemplate;

    @MockBean
    protected RateLimitService rateLimitService;

    @MockBean
    protected Agent agent;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @BeforeEach
    void cleanDatabase() {
        executeSqlFile("testcases/clean.sql");
    }

    // ──────────────────────── auth helpers ────────────────────────────────

    /**
     * Create a test user and return a valid JWT token for that user.
     */
    protected String registerAndGetToken(String username, String password, UserRole role) {
        AppUser user = new AppUser();
        user.setUserId("u-" + username);
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setEmail(username + "@test.com");
        user.setRole(role);
        user.setEnabled(true);
        user.setCreateBy("test");
        user.setUpdateBy("test");
        userRepository.save(user);
        return jwtTokenProvider.generateToken(username);
    }

    /**
     * Shortcut: register a USER and return a JWT token.
     */
    protected String registerUserAndGetToken(String username) {
        return registerAndGetToken(username, "password123", UserRole.USER);
    }

    /**
     * Shortcut: register an ADMIN and return a JWT token.
     */
    protected String registerAdminAndGetToken(String username) {
        return registerAndGetToken(username, "admin456", UserRole.ADMIN);
    }

    /**
     * Build the "Bearer xxx" header value from a raw JWT token.
     */
    protected String bearer(String token) {
        return "Bearer " + token;
    }

    // ──────────────────────── SQL helpers ─────────────────────────────────

    /**
     * Execute a SQL file from the classpath (split by semicolons).
     */
    protected void executeSqlFile(String path) {
        String sql = readSqlFile(path);
        if (sql == null || sql.isBlank()) {
            throw new IllegalStateException("SQL file not found or empty: " + path);
        }
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
        try (InputStream is = new org.springframework.core.io.ClassPathResource(path).getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read SQL file: " + path, e);
        }
    }
}
