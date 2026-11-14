package ru.promo.finance.domain.model;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Бюджет для категории расходов.
 */
public class Budget {
    private String category;
    private BigDecimal limitAmount;

    public Budget(String category, BigDecimal limitAmount) {
        if (category == null || category.trim().isEmpty()) {
            throw new IllegalArgumentException("Категория бюджета не может быть пустой");
        }
        Objects.requireNonNull(limitAmount, "Лимит бюджета не может быть null");
        if (limitAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Лимит бюджета не может быть отрицательным");
        }
        this.category = category.trim();
        this.limitAmount = limitAmount;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        if (category == null || category.trim().isEmpty()) {
            throw new IllegalArgumentException("Категория бюджета не может быть пустой");
        }
        this.category = category.trim();
    }

    public BigDecimal getLimitAmount() {
        return limitAmount;
    }

    public void setLimitAmount(BigDecimal limitAmount) {
        Objects.requireNonNull(limitAmount, "Лимит бюджета не может быть null");
        if (limitAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Лимит бюджета не может быть отрицательным");
        }
        this.limitAmount = limitAmount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Budget budget)) return false;
        return category.equalsIgnoreCase(budget.category);
    }

    @Override
    public int hashCode() {
        return category.toLowerCase().hashCode();
    }

    @Override
    public String toString() {
        return "Budget{" +
                "category='" + category + '\'' +
                ", limitAmount=" + limitAmount +
                '}';
    }
}
