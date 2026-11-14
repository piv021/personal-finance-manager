package ru.promo.finance;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.promo.finance.domain.model.Budget;
import ru.promo.finance.domain.model.BudgetStatus;
import ru.promo.finance.domain.model.Transaction;
import ru.promo.finance.domain.model.Wallet;
import ru.promo.finance.service.AlertService;
import ru.promo.finance.service.BudgetService;
import ru.promo.finance.service.CategoryService;

import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

public class CategoryAndBudgetTest {
    private Wallet wallet;
    private CategoryService categoryService;
    private BudgetService budgetService;

    @BeforeEach
    void setUp() {
        wallet = new Wallet("user-1");
        categoryService = new CategoryService();
        categoryService.initDefaultCategories(wallet);
        budgetService = new BudgetService(new AlertService());
    }

    @Test
    void testAddCategorySuccess() {
        categoryService.addCategory(wallet, "Инвестиции");
        assertTrue(wallet.hasCategory("Инвестиции"));
    }

    @Test
    void testAddDuplicateCategoryThrows() {
        assertThrows(IllegalArgumentException.class, () -> categoryService.addCategory(wallet, "Еда"));
    }

    @Test
    void testRenameCategoryUpdatesTransactionsAndBudgets() {
        wallet.setBudget("Еда", new BigDecimal("5000"));
        wallet.addTransaction(new Transaction("user-1", ru.promo.finance.domain.model.TransactionType.EXPENSE, "Еда", new BigDecimal("1000"), "Обед"));

        categoryService.renameCategory(wallet, "Еда", "Продукты и кафе");

        assertFalse(wallet.hasCategory("Еда"));
        assertTrue(wallet.hasCategory("Продукты и кафе"));

        // Проверяем, что бюджет обновился под новым именем
        assertTrue(wallet.getBudget("Продукты и кафе").isPresent());
        assertEquals(new BigDecimal("5000"), wallet.getBudget("Продукты и кафе").get().getLimitAmount());

        // Проверяем, что транзакция обновилась
        assertEquals("Продукты и кафе", wallet.getTransactions().get(0).getCategory());
    }

    @Test
    void testDeleteCategory() {
        wallet.setBudget("Такси", new BigDecimal("3000"));
        assertTrue(categoryService.deleteCategory(wallet, "Такси"));
        assertFalse(wallet.hasCategory("Такси"));
        assertTrue(wallet.getBudget("Такси").isEmpty());
    }

    @Test
    void testSetAndEditBudget() {
        budgetService.setBudget(wallet, "Еда", new BigDecimal("4000"));
        assertTrue(wallet.getBudget("Еда").isPresent());
        assertEquals(new BigDecimal("4000"), wallet.getBudget("Еда").get().getLimitAmount());

        budgetService.editBudget(wallet, "Еда", new BigDecimal("4500"));
        assertEquals(new BigDecimal("4500"), wallet.getBudget("Еда").get().getLimitAmount());
    }

    @Test
    void testEditNonExistentBudgetThrows() {
        assertThrows(NoSuchElementException.class, () ->
                budgetService.editBudget(wallet, "Несуществующая", new BigDecimal("1000")));
    }

    @Test
    void testNegativeBudgetLimitThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                budgetService.setBudget(wallet, "Еда", new BigDecimal("-500")));
    }

    @Test
    void testBudgetStatusRemainingCalculation() {
        budgetService.setBudget(wallet, "Развлечения", new BigDecimal("3000"));
        wallet.addTransaction(new Transaction("user-1", ru.promo.finance.domain.model.TransactionType.EXPENSE, "Развлечения", new BigDecimal("2000"), "Кино"));

        BudgetStatus status = budgetService.getBudgetStatus(wallet, "Развлечения", null, null).orElseThrow();
        assertEquals(new BigDecimal("3000"), status.limitAmount());
        assertEquals(new BigDecimal("2000"), status.spentAmount());
        assertEquals(new BigDecimal("1000"), status.remainingAmount());
        assertFalse(status.isExceeded());
    }
}
