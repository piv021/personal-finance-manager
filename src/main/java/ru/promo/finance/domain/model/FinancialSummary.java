package ru.promo.finance.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Полный финансовый отчёт / сводка.
 */
public record FinancialSummary(
        BigDecimal totalIncome,
        BigDecimal totalExpense,
        BigDecimal netBalance,
        Map<String, BigDecimal> incomeByCategory,
        Map<String, BigDecimal> expenseByCategory,
        List<BudgetStatus> budgetStatuses,
        List<Alert> alerts,
        Set<String> filterCategories,
        List<String> missingCategories,
        LocalDateTime periodFrom,
        LocalDateTime periodTo
) {}
