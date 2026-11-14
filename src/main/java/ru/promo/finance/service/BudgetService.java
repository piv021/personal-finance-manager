package ru.promo.finance.service;

import ru.promo.finance.domain.model.Budget;
import ru.promo.finance.domain.model.BudgetStatus;
import ru.promo.finance.domain.model.Wallet;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Сервис управления бюджетами по категориям расходов.
 */
public class BudgetService {
    private final AlertService alertService;

    public BudgetService(AlertService alertService) {
        this.alertService = alertService != null ? alertService : new AlertService();
    }

    public BudgetService() {
        this(new AlertService());
    }

    public void setBudget(Wallet wallet, String category, BigDecimal limitAmount) {
        if (wallet == null) throw new IllegalArgumentException("Кошелек не может быть null");
        if (category == null || category.trim().isEmpty()) {
            throw new IllegalArgumentException("Категория не может быть пустой");
        }
        if (limitAmount == null || limitAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Лимит бюджета должен быть неотрицательным числом");
        }
        wallet.setBudget(category.trim(), limitAmount);
    }

    public void editBudget(Wallet wallet, String category, BigDecimal newLimit) {
        if (wallet == null) throw new IllegalArgumentException("Кошелек не может быть null");
        if (category == null || category.trim().isEmpty()) {
            throw new IllegalArgumentException("Категория не может быть пустой");
        }
        if (!wallet.getBudget(category).isPresent()) {
            throw new NoSuchElementException("Бюджет для категории '" + category + "' не найден");
        }
        setBudget(wallet, category, newLimit);
    }

    public boolean removeBudget(Wallet wallet, String category) {
        if (wallet == null) return false;
        return wallet.removeBudget(category);
    }

    public Optional<BudgetStatus> getBudgetStatus(Wallet wallet, String category, LocalDateTime from, LocalDateTime to) {
        if (wallet == null || category == null) return Optional.empty();
        return wallet.getBudget(category).map(b -> {
            BigDecimal spent = alertService.calculateSpentForCategory(wallet, b.getCategory(), from, to);
            return BudgetStatus.of(b.getCategory(), b.getLimitAmount(), spent);
        });
    }

    public List<BudgetStatus> getAllBudgetStatuses(Wallet wallet, LocalDateTime from, LocalDateTime to) {
        if (wallet == null) return Collections.emptyList();
        List<BudgetStatus> list = new ArrayList<>();
        for (Budget b : wallet.getBudgets().values()) {
            BigDecimal spent = alertService.calculateSpentForCategory(wallet, b.getCategory(), from, to);
            list.add(BudgetStatus.of(b.getCategory(), b.getLimitAmount(), spent));
        }
        // Сортировка по имени категории
        list.sort(Comparator.comparing(BudgetStatus::category, String.CASE_INSENSITIVE_ORDER));
        return list;
    }
}
