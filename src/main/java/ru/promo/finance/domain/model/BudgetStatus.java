package ru.promo.finance.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Состояние бюджета по категории.
 * Хранит лимит, фактические траты, оставшийся бюджет (может быть отрицательным!),
 * процент расхода и флаги превышения.
 */
public record BudgetStatus(
        String category,
        BigDecimal limitAmount,
        BigDecimal spentAmount,
        BigDecimal remainingAmount,
        double percentUsed,
        boolean isExceeded,
        boolean isWarning80
) {
    public static BudgetStatus of(String category, BigDecimal limit, BigDecimal spent) {
        BigDecimal remaining = limit.subtract(spent);
        double percent = 0.0;
        if (limit.compareTo(BigDecimal.ZERO) > 0) {
            percent = spent.multiply(BigDecimal.valueOf(100))
                    .divide(limit, 2, RoundingMode.HALF_UP)
                    .doubleValue();
        }
        boolean exceeded = spent.compareTo(limit) > 0;
        boolean warning80 = !exceeded && percent >= 80.0;
        return new BudgetStatus(category, limit, spent, remaining, percent, exceeded, warning80);
    }
}
