package ru.promo.finance;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.promo.finance.domain.model.FinancialSummary;
import ru.promo.finance.domain.model.Transaction;
import ru.promo.finance.domain.model.TransactionType;
import ru.promo.finance.domain.model.Wallet;
import ru.promo.finance.service.AlertService;
import ru.promo.finance.service.BudgetService;
import ru.promo.finance.service.StatisticsService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class StatisticsServiceTest {
    private StatisticsService statisticsService;
    private Wallet wallet;

    @BeforeEach
    void setUp() {
        statisticsService = new StatisticsService(new AlertService(), new BudgetService());
        wallet = new Wallet("user-sample-test");

        // Расходы:
        // Еда: 300, 500
        // Развлечения: 3000
        // Коммунальные услуги: 3000
        // Такси: 1500
        wallet.addTransaction(new Transaction("user-sample-test", TransactionType.EXPENSE, "Еда", new BigDecimal("300"), "Обед"));
        wallet.addTransaction(new Transaction("user-sample-test", TransactionType.EXPENSE, "Еда", new BigDecimal("500"), "Ужин"));
        wallet.addTransaction(new Transaction("user-sample-test", TransactionType.EXPENSE, "Развлечения", new BigDecimal("3000"), "Кино"));
        wallet.addTransaction(new Transaction("user-sample-test", TransactionType.EXPENSE, "Коммунальные услуги", new BigDecimal("3000"), "ЖКХ"));
        wallet.addTransaction(new Transaction("user-sample-test", TransactionType.EXPENSE, "Такси", new BigDecimal("1500"), "Такси"));

        // Доходы:
        // Зарплата: 20000, 40000
        // Бонус: 3000
        wallet.addTransaction(new Transaction("user-sample-test", TransactionType.INCOME, "Зарплата", new BigDecimal("20000"), "Аванс"));
        wallet.addTransaction(new Transaction("user-sample-test", TransactionType.INCOME, "Зарплата", new BigDecimal("40000"), "ЗП"));
        wallet.addTransaction(new Transaction("user-sample-test", TransactionType.INCOME, "Бонус", new BigDecimal("3000"), "Премия"));

        // Бюджеты:
        // Еда: 4000
        // Развлечения: 3000
        // Коммунальные услуги: 2500
        wallet.setBudget("Еда", new BigDecimal("4000"));
        wallet.setBudget("Развлечения", new BigDecimal("3000"));
        wallet.setBudget("Коммунальные услуги", new BigDecimal("2500"));
    }

    @Test
    void testExactSpecificationSampleOutput() {
        FinancialSummary summary = statisticsService.calculateSummary(wallet, null, null, null);

        // Общий доход: 63,000.0
        assertEquals(new BigDecimal("63000"), summary.totalIncome());

        // Доходы по категориям: Зарплата: 60,000.0, Бонус: 3,000.0
        assertEquals(new BigDecimal("60000"), summary.incomeByCategory().get("Зарплата"));
        assertEquals(new BigDecimal("3000"), summary.incomeByCategory().get("Бонус"));

        // Общие расходы: 8,300.0
        assertEquals(new BigDecimal("8300"), summary.totalExpense());

        // Бюджет по категориям:
        // Коммунальные услуги: 2500.0, Оставшийся бюджет: -500.0
        // Еда: 4000.0, Оставшийся бюджет: 3200.0
        // Развлечения: 3000.0, Оставшийся бюджет: 0.0
        var utilitiesBudget = summary.budgetStatuses().stream()
                .filter(b -> b.category().equalsIgnoreCase("Коммунальные услуги"))
                .findFirst().orElseThrow();
        assertEquals(new BigDecimal("2500"), utilitiesBudget.limitAmount());
        assertEquals(new BigDecimal("-500"), utilitiesBudget.remainingAmount());

        var foodBudget = summary.budgetStatuses().stream()
                .filter(b -> b.category().equalsIgnoreCase("Еда"))
                .findFirst().orElseThrow();
        assertEquals(new BigDecimal("4000"), foodBudget.limitAmount());
        assertEquals(new BigDecimal("3200"), foodBudget.remainingAmount());

        var entBudget = summary.budgetStatuses().stream()
                .filter(b -> b.category().equalsIgnoreCase("Развлечения"))
                .findFirst().orElseThrow();
        assertEquals(new BigDecimal("3000"), entBudget.limitAmount());
        assertEquals(BigDecimal.ZERO, entBudget.remainingAmount());

        // Проверяем форматированный текст
        String text = statisticsService.formatSummaryText(summary);
        assertTrue(text.contains("Общий доход: 63"));
        assertTrue(text.contains("Общие расходы: 8"));
        assertTrue(text.contains("Коммунальные услуги"));
        assertTrue(text.contains("-500"));
    }

    @Test
    void testFilterByMultipleCategories() {
        // Запрос только по Еда и Такси
        FinancialSummary summary = statisticsService.calculateSummary(wallet, null, null, Set.of("Еда", "Такси"));

        assertEquals(BigDecimal.ZERO, summary.totalIncome());
        // Еда (800) + Такси (1500) = 2300
        assertEquals(new BigDecimal("2300"), summary.totalExpense());
        assertEquals(new BigDecimal("800"), summary.expenseByCategory().get("Еда"));
        assertEquals(new BigDecimal("1500"), summary.expenseByCategory().get("Такси"));
        assertNull(summary.expenseByCategory().get("Развлечения"));
    }

    @Test
    void testMissingCategoryNotification() {
        FinancialSummary summary = statisticsService.calculateSummary(wallet, null, null, Set.of("Еда", "НесуществующаяКатегория"));
        assertTrue(summary.missingCategories().contains("НесуществующаяКатегория"));
        String text = statisticsService.formatSummaryText(summary);
        assertTrue(text.contains("НесуществующаяКатегория"));
    }

    @Test
    void testDateRangeFiltering() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime past = now.minusDays(5);
        LocalDateTime future = now.plusDays(5);

        FinancialSummary summary = statisticsService.calculateSummary(wallet, past, future, null);
        assertEquals(new BigDecimal("63000"), summary.totalIncome());

        // Тест с периодом до всех операций
        FinancialSummary emptySummary = statisticsService.calculateSummary(wallet, now.minusDays(10), now.minusDays(9), null);
        assertEquals(BigDecimal.ZERO, emptySummary.totalIncome());
        assertEquals(BigDecimal.ZERO, emptySummary.totalExpense());
    }
}
