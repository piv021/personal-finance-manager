package ru.promo.finance;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.promo.finance.domain.model.Transaction;
import ru.promo.finance.domain.model.TransactionType;
import ru.promo.finance.domain.model.User;
import ru.promo.finance.domain.model.Wallet;
import ru.promo.finance.domain.repository.UserRepository;
import ru.promo.finance.infrastructure.export.ReportExporter;
import ru.promo.finance.infrastructure.storage.FileStorageService;
import ru.promo.finance.infrastructure.storage.InMemoryUserRepository;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class PersistenceAndExportTest {
    private Path tempDir;
    private FileStorageService storageService;
    private ReportExporter reportExporter;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("finance-test-io");
        storageService = new FileStorageService(tempDir);
        reportExporter = new ReportExporter(storageService);
    }

    @Test
    void testUserSaveAndLoad() {
        UserRepository repo1 = new InMemoryUserRepository();
        User u1 = new User("user1", "hash1", "salt1");
        User u2 = new User("user2", "hash2", "salt2");
        repo1.save(u1);
        repo1.save(u2);

        storageService.saveUsers(repo1);

        UserRepository repo2 = new InMemoryUserRepository();
        storageService.loadUsers(repo2);

        assertEquals(2, repo2.findAll().size());
        assertTrue(repo2.findByLogin("user1").isPresent());
        assertEquals("hash1", repo2.findByLogin("user1").get().getPasswordHash());
        assertTrue(repo2.findByLogin("user2").isPresent());
    }

    @Test
    void testWalletSaveAndLoad() {
        User user = new User("ivan", "hash", "salt");
        Wallet wallet = new Wallet(user.getId());
        wallet.setBudget("Еда", new BigDecimal("5000"));
        wallet.addTransaction(new Transaction(user.getId(), TransactionType.INCOME, "Зарплата", new BigDecimal("30000"), "Октябрь"));
        wallet.addTransaction(new Transaction(user.getId(), TransactionType.EXPENSE, "Еда", new BigDecimal("1200"), "Супермаркет"));

        storageService.saveWallet(user, wallet);

        Optional<Wallet> loadedOpt = storageService.loadWallet(user);
        assertTrue(loadedOpt.isPresent());
        Wallet loaded = loadedOpt.get();

        assertEquals(new BigDecimal("28800"), loaded.getBalance());
        assertEquals(2, loaded.getTransactions().size());
        assertTrue(loaded.getBudget("Еда").isPresent());
        assertEquals(new BigDecimal("5000"), loaded.getBudget("Еда").get().getLimitAmount());
        assertTrue(loaded.hasCategory("Зарплата"));
    }

    @Test
    void testCsvExportAndImport() throws IOException {
        Path csvFile = tempDir.resolve("transactions.csv");
        User user = new User("petr", "hash", "salt");
        Wallet wallet = new Wallet(user.getId());
        wallet.addTransaction(new Transaction(user.getId(), TransactionType.INCOME, "Бонус", new BigDecimal("5000"), "Премия"));
        wallet.addTransaction(new Transaction(user.getId(), TransactionType.EXPENSE, "Такси", new BigDecimal("600"), "Поездка домой"));

        reportExporter.exportTransactionsToCsv(wallet.getTransactions(), csvFile);
        assertTrue(Files.exists(csvFile));
        String content = Files.readString(csvFile);
        assertTrue(content.contains("Бонус"));
        assertTrue(content.contains("Такси"));

        Wallet freshWallet = new Wallet(user.getId());
        int imported = reportExporter.importTransactionsFromCsv(csvFile, freshWallet);
        assertEquals(2, imported);
        assertEquals(new BigDecimal("4400"), freshWallet.getBalance());
    }

    @Test
    void testJsonExportAndImport() throws IOException {
        Path jsonFile = tempDir.resolve("snapshot.json");
        User user = new User("olga", "hash", "salt");
        Wallet wallet = new Wallet(user.getId());
        wallet.setBudget("Развлечения", new BigDecimal("4000"));
        wallet.addTransaction(new Transaction(user.getId(), TransactionType.INCOME, "Фриланс", new BigDecimal("15000"), "Проект"));

        reportExporter.exportWalletToJson(wallet, user.getLogin(), jsonFile);
        assertTrue(Files.exists(jsonFile));

        Wallet importedWallet = reportExporter.importWalletFromJson(jsonFile, user.getId());
        assertEquals(new BigDecimal("15000"), importedWallet.getBalance());
        assertTrue(importedWallet.getBudget("Развлечения").isPresent());
    }
}
