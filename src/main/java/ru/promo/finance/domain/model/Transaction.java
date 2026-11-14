package ru.promo.finance.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Финансовая операция (доход, расход, перевод).
 */
public class Transaction {
    private final String id;
    private final String userId;
    private final TransactionType type;
    private String category;
    private final BigDecimal amount;
    private final String note;
    private final LocalDateTime timestamp;

    public Transaction(String id, String userId, TransactionType type, String category,
                       BigDecimal amount, String note, LocalDateTime timestamp) {
        this.id = (id != null && !id.isBlank()) ? id : UUID.randomUUID().toString();
        this.userId = Objects.requireNonNull(userId, "userId не может быть null");
        this.type = Objects.requireNonNull(type, "type не может быть null");
        if (category == null || category.trim().isEmpty()) {
            throw new IllegalArgumentException("Категория не может быть пустой");
        }
        this.category = category.trim();
        Objects.requireNonNull(amount, "amount не может быть null");
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Сумма операции должна быть строго больше нуля");
        }
        this.amount = amount;
        this.note = (note != null) ? note.trim() : "";
        this.timestamp = (timestamp != null) ? timestamp : LocalDateTime.now();
    }

    public Transaction(String userId, TransactionType type, String category,
                       BigDecimal amount, String note) {
        this(UUID.randomUUID().toString(), userId, type, category, amount, note, LocalDateTime.now());
    }

    public String getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public TransactionType getType() {
        return type;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        if (category == null || category.trim().isEmpty()) {
            throw new IllegalArgumentException("Категория не может быть пустой");
        }
        this.category = category.trim();
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getNote() {
        return note;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Transaction that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Transaction{" +
                "id='" + id + '\'' +
                ", type=" + type +
                ", category='" + category + '\'' +
                ", amount=" + amount +
                ", note='" + note + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}
