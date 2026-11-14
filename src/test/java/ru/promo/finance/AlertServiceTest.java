package ru.promo.finance;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.promo.finance.domain.model.Alert;
import ru.promo.finance.domain.model.AlertType;
import ru.promo.finance.domain.model.Transaction;
import ru.promo.finance.domain.model.TransactionType;
import ru.promo.finance.domain.model.Wallet;
import ru.promo.finance.service.AlertService;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class AlertServiceTest {
    private AlertService alertService;
    private Wallet wallet;

    @BeforeEach
    void setUp() {
        alertService = new AlertService();
        wallet = new Wallet("user-alert-test");
    }

    @Test
    void testBudgetExceededAlert() {
        wallet.setBudget("Коммунальные услуги", new BigDecimal("2500"));
        wallet.addTransaction(new Transaction("user-alert-test", TransactionType.EXPENSE, "Коммунальные услуги", new BigDecimal("3000"), "ЖКХ"));

        List<Alert> alerts = alertService.checkAllAlerts(wallet, null, null);
        assertTrue(alerts.stream().anyMatch(a -> a.type() == AlertType.BUDGET_EXCEEDED));
        Alert alert = alerts.stream().filter(a -> a.type() == AlertType.BUDGET_EXCEEDED).findFirst().orElseThrow();
        assertEquals("Коммунальные услуги", alert.category());
        assertTrue(alert.message().contains("перерасход"));
        assertTrue(alert.message().contains("500"));
    }

    @Test
    void testBudgetWarning80Alert() {
        wallet.setBudget("Еда", new BigDecimal("4000"));
        // Тратим 3300 (82.5% от 4000)
        wallet.addTransaction(new Transaction("user-alert-test", TransactionType.EXPENSE, "Еда", new BigDecimal("3300"), "Продукты"));

        List<Alert> alerts = alertService.checkAllAlerts(wallet, null, null);
        assertTrue(alerts.stream().anyMatch(a -> a.type() == AlertType.BUDGET_WARNING_80));
    }

    @Test
    void testNoBudgetAlertWhenWithinLimit() {
        wallet.setBudget("Еда", new BigDecimal("4000"));
        wallet.addTransaction(new Transaction("user-alert-test", TransactionType.EXPENSE, "Еда", new BigDecimal("1000"), "Обед"));

        List<Alert> alerts = alertService.checkAllAlerts(wallet, null, null);
        assertFalse(alerts.stream().anyMatch(a -> a.type() == AlertType.BUDGET_EXCEEDED));
        assertFalse(alerts.stream().anyMatch(a -> a.type() == AlertType.BUDGET_WARNING_80));
    }

    @Test
    void testExpensesExceedIncomeAlert() {
        wallet.addTransaction(new Transaction("user-alert-test", TransactionType.INCOME, "Зарплата", new BigDecimal("5000"), "Аванс"));
        wallet.addTransaction(new Transaction("user-alert-test", TransactionType.EXPENSE, "Аренда", new BigDecimal("15000"), "Квартира"));

        List<Alert> alerts = alertService.checkAllAlerts(wallet, null, null);
        assertTrue(alerts.stream().anyMatch(a -> a.type() == AlertType.EXPENSES_EXCEED_INCOME));
        assertTrue(alerts.stream().anyMatch(a -> a.type() == AlertType.NEGATIVE_BALANCE));
    }

    @Test
    void testNegativeBalanceOnExpenseCheck() {
        // Баланс 0, делаем расход 1000
        wallet.addTransaction(new Transaction("user-alert-test", TransactionType.EXPENSE, "Такси", new BigDecimal("1000"), "Поездка"));
        List<Alert> alerts = alertService.checkAlertsOnExpense(wallet, "Такси");
        assertTrue(alerts.stream().anyMatch(a -> a.type() == AlertType.NEGATIVE_BALANCE));
    }
}
