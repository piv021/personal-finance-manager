package ru.promo.finance.service;

import ru.promo.finance.domain.model.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Сервис подсчета статистики и формирования финансовых отчетов.
 * Поддерживает общие итоги, разбивку по категориям, расчет по выбранным категориям
 * и за произвольный временной период.
 */
public class StatisticsService {
    private final AlertService alertService;
    private final BudgetService budgetService;

    public StatisticsService(AlertService alertService, BudgetService budgetService) {
        this.alertService = alertService != null ? alertService : new AlertService();
        this.budgetService = budgetService != null ? budgetService : new BudgetService(this.alertService);
    }

    public StatisticsService() {
        this(new AlertService(), new BudgetService());
    }

    /**
     * Формирует финансовую сводку.
     *
     * @param wallet           кошелек
     * @param from             начало периода (null - без ограничения)
     * @param to               конец периода (null - без ограничения)
     * @param filterCategories набор категорий для фильтрации (null или пустой - все категории)
     * @return объект FinancialSummary
     */
    public FinancialSummary calculateSummary(Wallet wallet, LocalDateTime from, LocalDateTime to, Set<String> filterCategories) {
        if (wallet == null) {
            return new FinancialSummary(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    Collections.emptyMap(), Collections.emptyMap(), Collections.emptyList(),
                    Collections.emptyList(), Collections.emptySet(), Collections.emptyList(), from, to);
        }

        BigDecimal totalIncome = BigDecimal.ZERO;
        BigDecimal totalExpense = BigDecimal.ZERO;
        Map<String, BigDecimal> incomeByCategory = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        Map<String, BigDecimal> expenseByCategory = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        boolean hasFilter = filterCategories != null && !filterCategories.isEmpty();
        Set<String> normalizedFilter = new HashSet<>();
        if (hasFilter) {
            for (String c : filterCategories) {
                if (c != null && !c.isBlank()) {
                    normalizedFilter.add(c.trim().toLowerCase());
                }
            }
        }

        List<String> missingCategories = new ArrayList<>();
        if (hasFilter) {
            for (String requested : filterCategories) {
                if (requested == null || requested.isBlank()) continue;
                String reqTrimmed = requested.trim();
                boolean existsInWallet = wallet.hasCategory(reqTrimmed)
                        || wallet.getBudgets().keySet().stream().anyMatch(b -> b.equalsIgnoreCase(reqTrimmed))
                        || wallet.getTransactions().stream().anyMatch(t -> t.getCategory().equalsIgnoreCase(reqTrimmed));
                if (!existsInWallet) {
                    missingCategories.add(reqTrimmed);
                }
            }
        }

        for (Transaction t : wallet.getTransactions()) {
            if (from != null && t.getTimestamp().isBefore(from)) continue;
            if (to != null && t.getTimestamp().isAfter(to)) continue;

            if (hasFilter && !normalizedFilter.contains(t.getCategory().toLowerCase())) {
                continue;
            }

            if (t.getType().isIncome()) {
                totalIncome = totalIncome.add(t.getAmount());
                incomeByCategory.merge(t.getCategory(), t.getAmount(), BigDecimal::add);
            } else if (t.getType().isExpense()) {
                totalExpense = totalExpense.add(t.getAmount());
                expenseByCategory.merge(t.getCategory(), t.getAmount(), BigDecimal::add);
            }
        }

        // Бюджеты по категориям
        List<BudgetStatus> budgetStatuses = new ArrayList<>();
        for (Budget b : wallet.getBudgets().values()) {
            if (hasFilter && !normalizedFilter.contains(b.getCategory().toLowerCase())) {
                continue;
            }
            BigDecimal spent = expenseByCategory.getOrDefault(b.getCategory(), BigDecimal.ZERO);
            budgetStatuses.add(BudgetStatus.of(b.getCategory(), b.getLimitAmount(), spent));
        }
        budgetStatuses.sort(Comparator.comparing(BudgetStatus::category, String.CASE_INSENSITIVE_ORDER));

        // Оповещения
        List<Alert> alerts = alertService.checkAllAlerts(wallet, from, to);

        BigDecimal netBalance = totalIncome.subtract(totalExpense);

        return new FinancialSummary(
                totalIncome,
                totalExpense,
                netBalance,
                incomeByCategory,
                expenseByCategory,
                budgetStatuses,
                alerts,
                hasFilter ? filterCategories : Collections.emptySet(),
                missingCategories,
                from,
                to
        );
    }

    /**
     * Форматирует отчет в виде строки, точно соответствуя стилю из ТЗ.
     */
    public String formatSummaryText(FinancialSummary summary) {
        StringBuilder sb = new StringBuilder();

        if (summary.periodFrom() != null || summary.periodTo() != null) {
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
            sb.append("Период отчета: ");
            if (summary.periodFrom() != null) sb.append("с ").append(summary.periodFrom().format(dtf)).append(" ");
            if (summary.periodTo() != null) sb.append("по ").append(summary.periodTo().format(dtf));
            sb.append("\n\n");
        }

        // Уведомление о неизвестных категориях
        if (!summary.missingCategories().isEmpty()) {
            sb.append("Предупреждение: следующие категории не найдены в кошельке: ")
                    .append(String.join(", ", summary.missingCategories()))
                    .append("\n\n");
        }

        // Доходы
        sb.append(String.format("Общий доход: %,.1f\n", summary.totalIncome()));
        sb.append("Доходы по категориям:\n");
        if (summary.incomeByCategory().isEmpty()) {
            sb.append("  (нет данных)\n");
        } else {
            for (Map.Entry<String, BigDecimal> e : summary.incomeByCategory().entrySet()) {
                sb.append(String.format("  %s: %,.1f\n", e.getKey(), e.getValue()));
            }
        }

        // Расходы
        sb.append(String.format("Общие расходы: %,.1f\n", summary.totalExpense()));
        if (!summary.expenseByCategory().isEmpty()) {
            sb.append("Расходы по категориям:\n");
            for (Map.Entry<String, BigDecimal> e : summary.expenseByCategory().entrySet()) {
                sb.append(String.format("  %s: %,.1f\n", e.getKey(), e.getValue()));
            }
        }

        // Бюджет по категориям
        sb.append("Бюджет по категориям:\n");
        if (summary.budgetStatuses().isEmpty()) {
            sb.append("  (бюджеты не установлены)\n");
        } else {
            for (BudgetStatus bs : summary.budgetStatuses()) {
                sb.append(String.format("  %s: %,.1f, Оставшийся бюджет: %,.1f\n",
                        bs.category(), bs.limitAmount(), bs.remainingAmount()));
            }
        }

        // Оповещения
        if (!summary.alerts().isEmpty()) {
            sb.append("\nОповещения:\n");
            for (Alert alert : summary.alerts()) {
                sb.append("  [!] ").append(alert.message()).append("\n");
            }
        }

        return sb.toString();
    }
}
