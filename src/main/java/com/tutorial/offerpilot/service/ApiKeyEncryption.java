/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * API Key AES 加密/解密工具。
 * 密钥从环境变量 app.encryption.secret-key 注入。
 */
@Slf4j
@Component
public class ApiKeyEncryption {

    private static final String ALGORITHM = "AES";
    private final SecretKeySpec secretKey;

    public ApiKeyEncryption(@Value("${app.encryption.secret-key:dev-key-change-in-production}") String secretKeyStr) {
        // AES-128 需要 16 字节密钥，不足补齐，超出截断
        byte[] keyBytes = new byte[16];
        byte[] srcBytes = secretKeyStr.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(srcBytes, 0, keyBytes, 0, Math.min(srcBytes.length, 16));
        this.secretKey = new SecretKeySpec(keyBytes, ALGORITHM);
    }

    /**
     * 加密 API Key。
     */
    public String encrypt(String plainText) {
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            log.error("Failed to encrypt API key", e);
            throw new RuntimeException("Failed to encrypt API key", e);
        }
    }

    /**
     * 解密 API Key。
     */
    public String decrypt(String cipherText) {
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            byte[] decoded = Base64.getDecoder().decode(cipherText);
            return new String(cipher.doFinal(decoded), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Failed to decrypt API key", e);
            throw new RuntimeException("Failed to decrypt API key", e);
        }
    }

    /**
     * 对 API Key 脱敏显示：保留前4位和后4位，中间用 * 替换。
     * 长度 <= 8 则全部替换为 ****。
     */
    public static String mask(String apiKey) {
        if (apiKey == null || apiKey.isEmpty()) {
            return "";
        }
        if (apiKey.length() <= 8) {
            return "****";
        }
        return apiKey.substring(0, 4) + "-****-" + apiKey.substring(apiKey.length() - 4);
    }
}
