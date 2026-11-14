package ru.promo.finance.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Сервис безопасности: генерация соли и хэширование паролей (SHA-256 + salt).
 */
public class SecurityService {
    private static final int SALT_BYTES = 16;
    private static final int ITERATIONS = 1000;
    private final SecureRandom random = new SecureRandom();

    public String generateSalt() {
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    public String hashPassword(String password, String salt) {
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Пароль не может быть пустым");
        }
        if (salt == null || salt.isEmpty()) {
            throw new IllegalArgumentException("Соль не может быть пустой");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = (password + salt).getBytes(StandardCharsets.UTF_8);
            for (int i = 0; i < ITERATIONS; i++) {
                hash = digest.digest(hash);
            }
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Алгоритм SHA-256 не найден", e);
        }
    }

    public boolean verifyPassword(String password, String salt, String expectedHash) {
        if (password == null || salt == null || expectedHash == null) {
            return false;
        }
        String actualHash = hashPassword(password, salt);
        return MessageDigest.isEqual(
                actualHash.getBytes(StandardCharsets.UTF_8),
                expectedHash.getBytes(StandardCharsets.UTF_8)
        );
    }
}
