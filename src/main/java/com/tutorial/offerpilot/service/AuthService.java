/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.service;

import com.tutorial.offerpilot.dto.auth.AuthResponse;
import com.tutorial.offerpilot.dto.auth.LoginRequest;
import com.tutorial.offerpilot.dto.auth.RegisterRequest;
import com.tutorial.offerpilot.entity.AppUser;
import com.tutorial.offerpilot.enums.UserRole;
import com.tutorial.offerpilot.exception.AccountDisabledException;
import com.tutorial.offerpilot.exception.DuplicateUserException;
import com.tutorial.offerpilot.exception.InvalidCredentialsException;
import com.tutorial.offerpilot.repository.AppUserRepository;
import com.tutorial.offerpilot.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        if (userRepository.existsByUsername(req.getUsername())) {
            throw new DuplicateUserException();
        }

        AppUser user = new AppUser();
        user.setUserId("u-" + UUID.randomUUID().toString().substring(0, 8));
        user.setUsername(req.getUsername());
        user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        user.setEmail(req.getEmail());
        user.setRole(UserRole.USER);
        user.setEnabled(true);
        userRepository.save(user);

        String token = jwtTokenProvider.generateToken(user.getUsername());
        log.info("User registered: {}", user.getUsername());
        return new AuthResponse(token, user.getUserId(), user.getUsername(), UserRole.USER);
    }

    @Transactional
    public AuthResponse login(LoginRequest req) {
        AppUser user = userRepository.findByUsername(req.getUsername())
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        if (!user.getEnabled()) {
            throw new AccountDisabledException();
        }

        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        String token = jwtTokenProvider.generateToken(user.getUsername());
        log.info("User logged in: {}", user.getUsername());
        return new AuthResponse(token, user.getUserId(), user.getUsername(), user.getRole());
    }
}
