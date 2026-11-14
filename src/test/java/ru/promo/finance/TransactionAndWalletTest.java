package ru.promo.finance;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.promo.finance.domain.model.Transaction;
import ru.promo.finance.domain.model.TransactionType;
import ru.promo.finance.domain.model.User;
import ru.promo.finance.domain.model.Wallet;
import ru.promo.finance.domain.repository.UserRepository;
import ru.promo.finance.domain.repository.WalletRepository;
import ru.promo.finance.infrastructure.storage.FileStorageService;
import ru.promo.finance.infrastructure.storage.InMemoryUserRepository;
import ru.promo.finance.infrastructure.storage.InMemoryWalletRepository;
import ru.promo.finance.service.AlertService;
import ru.promo.finance.service.CategoryService;
import ru.promo.finance.service.WalletService;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class TransactionAndWalletTest {
    private WalletRepository walletRepository;
    private UserRepository userRepository;
    private WalletService walletService;
    private User testUser;
    private Wallet testWallet;

    @BeforeEach
    void setUp() throws IOException {
        Path tempDir = Files.createTempDirectory("finance-test-wallet");
        walletRepository = new InMemoryWalletRepository();
        userRepository = new InMemoryUserRepository();
        AlertService alertService = new AlertService();
        FileStorageService fileStorageService = new FileStorageService(tempDir);
        walletService = new WalletService(walletRepository, userRepository, alertService, fileStorageService);

        testUser = new User("tester", "hash", "salt");
        userRepository.save(testUser);
        testWallet = new Wallet(testUser.getId());
        new CategoryService().initDefaultCategories(testWallet);
        walletRepository.save(testWallet);
    }

    @Test
    void testInitialBalanceIsZero() {
        assertEquals(BigDecimal.ZERO, testWallet.getBalance());
        assertTrue(testWallet.getTransactions().isEmpty());
    }

    @Test
    void testAddIncomeIncreasesBalance() {
        Transaction tx = walletService.addIncome(testUser.getId(), "Зарплата", new BigDecimal("50000.00"), "Октябрь");
        assertNotNull(tx);
        assertEquals(new BigDecimal("50000.00"), tx.getAmount());
        assertEquals("Зарплата", tx.getCategory());
        assertEquals(TransactionType.INCOME, tx.getType());

        Wallet updated = walletService.getWalletByUserId(testUser.getId());
        assertEquals(new BigDecimal("50000.00"), updated.getBalance());
        assertEquals(1, updated.getTransactions().size());
    }

    @Test
    void testAddExpenseDecreasesBalance() {
        walletService.addIncome(testUser.getId(), "Зарплата", new BigDecimal("50000.00"), "Октябрь");
        WalletService.ExpenseResult res = walletService.addExpense(testUser.getId(), "Еда", new BigDecimal("3500.00"), "Продукты");

        assertNotNull(res.transaction());
        assertEquals(TransactionType.EXPENSE, res.transaction().getType());
        assertEquals(new BigDecimal("3500.00"), res.transaction().getAmount());

        Wallet updated = walletService.getWalletByUserId(testUser.getId());
        assertEquals(new BigDecimal("46500.00"), updated.getBalance());
    }

    @Test
    void testAddZeroOrNegativeAmountThrowsException() {
        assertThrows(IllegalArgumentException.class, () ->
                walletService.addIncome(testUser.getId(), "Зарплата", BigDecimal.ZERO, "Тест"));
        assertThrows(IllegalArgumentException.class, () ->
                walletService.addIncome(testUser.getId(), "Зарплата", new BigDecimal("-100"), "Тест"));
        assertThrows(IllegalArgumentException.class, () ->
                walletService.addExpense(testUser.getId(), "Еда", BigDecimal.ZERO, "Тест"));
        assertThrows(IllegalArgumentException.class, () ->
                walletService.addExpense(testUser.getId(), "Еда", new BigDecimal("-50"), "Тест"));
    }

    @Test
    void testEmptyCategoryThrowsException() {
        assertThrows(IllegalArgumentException.class, () ->
                walletService.addIncome(testUser.getId(), "", new BigDecimal("100"), "Тест"));
        assertThrows(IllegalArgumentException.class, () ->
                walletService.addExpense(testUser.getId(), "   ", new BigDecimal("100"), "Тест"));
    }

    @Test
    void testMultipleTransactionsCalculation() {
        walletService.addIncome(testUser.getId(), "Зарплата", new BigDecimal("60000"), "Основная");
        walletService.addIncome(testUser.getId(), "Бонус", new BigDecimal("3000"), "Квартальный");
        walletService.addExpense(testUser.getId(), "Еда", new BigDecimal("800"), "Супермаркет");
        walletService.addExpense(testUser.getId(), "Развлечения", new BigDecimal("3000"), "Концерт");
        walletService.addExpense(testUser.getId(), "Коммунальные услуги", new BigDecimal("3000"), "ЖКХ");
        walletService.addExpense(testUser.getId(), "Такси", new BigDecimal("1500"), "Такси");

        // Total income: 63000, Total expense: 8300, Balance: 54700
        Wallet updated = walletService.getWalletByUserId(testUser.getId());
        assertEquals(new BigDecimal("54700"), updated.getBalance());
        assertEquals(6, updated.getTransactions().size());
    }
}
