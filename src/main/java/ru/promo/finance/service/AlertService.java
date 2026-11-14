package ru.promo.finance.service;

import ru.promo.finance.domain.model.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Сервис проверки и генерации финансовых оповещений.
 * Реализует базовые и расширенные оповещения (превышение бюджета, 80% лимита,
 * превышение расходов над доходами, отрицательный/нулевой баланс).
 */
public class AlertService {

    /**
     * Проверяет оповещения для всего кошелька (включая все категории и баланс).
     */
    public List<Alert> checkAllAlerts(Wallet wallet, LocalDateTime from, LocalDateTime to) {
        List<Alert> alerts = new ArrayList<>();
        if (wallet == null) return alerts;

        // 1. Проверка бюджетов по каждой категории
        for (Budget budget : wallet.getBudgets().values()) {
            checkBudgetAlert(wallet, budget, from, to).ifPresent(alerts::add);
        }

        // 2. Расчет общих расходов и доходов
        BigDecimal totalIncome = BigDecimal.ZERO;
        BigDecimal totalExpense = BigDecimal.ZERO;

        for (Transaction t : wallet.getTransactions()) {
            if (from != null && t.getTimestamp().isBefore(from)) continue;
            if (to != null && t.getTimestamp().isAfter(to)) continue;

            if (t.getType().isIncome()) {
                totalIncome = totalIncome.add(t.getAmount());
            } else if (t.getType().isExpense()) {
                totalExpense = totalExpense.add(t.getAmount());
            }
        }

        // Оповещение: расходы превысили доходы
        if (totalExpense.compareTo(totalIncome) > 0 && totalIncome.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal diff = totalExpense.subtract(totalIncome);
            alerts.add(new Alert(
                    AlertType.EXPENSES_EXCEED_INCOME,
                    String.format("Внимание! Общие расходы (%,.2f) превысили доходы (%,.2f) на %,.2f!",
                            totalExpense, totalIncome, diff)
            ));
        }

        // Оповещение: отрицательный или нулевой баланс кошелька
        BigDecimal currentBalance = wallet.getBalance();
        if (currentBalance.compareTo(BigDecimal.ZERO) < 0) {
            alerts.add(new Alert(
                    AlertType.NEGATIVE_BALANCE,
                    String.format("Внимание! Отрицательный баланс кошелька: %,.2f!", currentBalance)
            ));
        } else if (currentBalance.compareTo(BigDecimal.ZERO) == 0 && !wallet.getTransactions().isEmpty()) {
            alerts.add(new Alert(
                    AlertType.ZERO_BALANCE,
                    "Внимание! Баланс кошелька равен 0.00."
            ));
        }

        return alerts;
    }

    /**
     * Проверяет оповещение по конкретному бюджету.
     */
    public Optional<Alert> checkBudgetAlert(Wallet wallet, Budget budget, LocalDateTime from, LocalDateTime to) {
        if (budget == null || budget.getLimitAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return Optional.empty();
        }

        BigDecimal spent = calculateSpentForCategory(wallet, budget.getCategory(), from, to);
        BudgetStatus status = BudgetStatus.of(budget.getCategory(), budget.getLimitAmount(), spent);

        if (status.isExceeded()) {
            BigDecimal overrun = status.remainingAmount().abs();
            return Optional.of(new Alert(
                    AlertType.BUDGET_EXCEEDED,
                    budget.getCategory(),
                    String.format("Превышен лимит бюджета по категории '%s'! Лимит: %,.2f, потрачено: %,.2f, перерасход: %,.2f (%.1f%%).",
                            budget.getCategory(), budget.getLimitAmount(), spent, overrun, status.percentUsed())
            ));
        } else if (status.isWarning80()) {
            return Optional.of(new Alert(
                    AlertType.BUDGET_WARNING_80,
                    budget.getCategory(),
                    String.format("Внимание: расходы по категории '%s' достигли %.1f%% от лимита (потрачено %,.2f из %,.2f).",
                            budget.getCategory(), status.percentUsed(), spent, budget.getLimitAmount())
            ));
        }

        return Optional.empty();
    }

    /**
     * Проверяет мгновенные оповещения при добавлении расхода.
     */
    public List<Alert> checkAlertsOnExpense(Wallet wallet, String category) {
        List<Alert> alerts = new ArrayList<>();
        wallet.getBudget(category).ifPresent(b -> checkBudgetAlert(wallet, b, null, null).ifPresent(alerts::add));

        BigDecimal currentBalance = wallet.getBalance();
        if (currentBalance.compareTo(BigDecimal.ZERO) < 0) {
            alerts.add(new Alert(
                    AlertType.NEGATIVE_BALANCE,
                    String.format("Внимание! После операции баланс стал отрицательным: %,.2f!", currentBalance)
            ));
        }
        return alerts;
    }

    public BigDecimal calculateSpentForCategory(Wallet wallet, String category, LocalDateTime from, LocalDateTime to) {
        BigDecimal spent = BigDecimal.ZERO;
        for (Transaction t : wallet.getTransactions()) {
            if (!t.getType().isExpense()) continue;
            if (!t.getCategory().equalsIgnoreCase(category)) continue;
            if (from != null && t.getTimestamp().isBefore(from)) continue;
            if (to != null && t.getTimestamp().isAfter(to)) continue;
            spent = spent.add(t.getAmount());
        }
        return spent;
    }
}
