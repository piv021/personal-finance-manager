package ru.promo.finance.domain.model;

import java.time.LocalDateTime;

/**
 * Оповещение о финансовом состоянии.
 */
public record Alert(
        AlertType type,
        String category,
        String message,
        LocalDateTime timestamp
) {
    public Alert(AlertType type, String category, String message) {
        this(type, category, message, LocalDateTime.now());
    }

    public Alert(AlertType type, String message) {
        this(type, null, message, LocalDateTime.now());
    }
}
