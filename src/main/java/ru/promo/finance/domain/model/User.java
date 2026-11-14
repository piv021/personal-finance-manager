package ru.promo.finance.domain.model;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Пользователь системы.
 */
public class User {
    private final String id;
    private final String login;
    private final String passwordHash;
    private final String salt;
    private final LocalDateTime createdAt;

    public User(String id, String login, String passwordHash, String salt, LocalDateTime createdAt) {
        this.id = (id != null && !id.isBlank()) ? id : UUID.randomUUID().toString();
        if (login == null || login.trim().isEmpty()) {
            throw new IllegalArgumentException("Логин не может быть пустым");
        }
        this.login = login.trim();
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash не может быть null");
        this.salt = Objects.requireNonNull(salt, "salt не может быть null");
        this.createdAt = (createdAt != null) ? createdAt : LocalDateTime.now();
    }

    public User(String login, String passwordHash, String salt) {
        this(UUID.randomUUID().toString(), login, passwordHash, salt, LocalDateTime.now());
    }

    public String getId() {
        return id;
    }

    public String getLogin() {
        return login;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getSalt() {
        return salt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User user)) return false;
        return login.equalsIgnoreCase(user.login);
    }

    @Override
    public int hashCode() {
        return login.toLowerCase().hashCode();
    }

    @Override
    public String toString() {
        return "User{" +
                "id='" + id + '\'' +
                ", login='" + login + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
}
