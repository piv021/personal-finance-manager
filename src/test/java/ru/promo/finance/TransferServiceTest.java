package ru.promo.finance;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.promo.finance.domain.model.TransactionType;
import ru.promo.finance.domain.model.User;
import ru.promo.finance.domain.model.Wallet;
import ru.promo.finance.domain.repository.UserRepository;
import ru.promo.finance.domain.repository.WalletRepository;
import ru.promo.finance.infrastructure.storage.FileStorageService;
import ru.promo.finance.infrastructure.storage.InMemoryUserRepository;
import ru.promo.finance.infrastructure.storage.InMemoryWalletRepository;
import ru.promo.finance.service.AlertService;
import ru.promo.finance.service.TransferResult;
import ru.promo.finance.service.WalletService;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class TransferServiceTest {
    private UserRepository userRepository;
    private WalletRepository walletRepository;
    private WalletService walletService;
    private User alice;
    private User bob;
    private Wallet aliceWallet;
    private Wallet bobWallet;

    @BeforeEach
    void setUp() throws IOException {
        Path tempDir = Files.createTempDirectory("finance-test-transfer");
        userRepository = new InMemoryUserRepository();
        walletRepository = new InMemoryWalletRepository();
        AlertService alertService = new AlertService();
        FileStorageService fileStorageService = new FileStorageService(tempDir);
        walletService = new WalletService(walletRepository, userRepository, alertService, fileStorageService);

        alice = new User("alice", "hash1", "salt1");
        bob = new User("bob", "hash2", "salt2");
        userRepository.save(alice);
        userRepository.save(bob);

        aliceWallet = new Wallet(alice.getId());
        bobWallet = new Wallet(bob.getId());
        walletRepository.save(aliceWallet);
        walletRepository.save(bobWallet);

        // Даем Alice начальный баланс 10000
        walletService.addIncome(alice.getId(), "Зарплата", new BigDecimal("10000"), "Начальный баланс");
    }

    @Test
    void testSuccessfulTransferBetweenUsers() {
        TransferResult result = walletService.transfer(alice, "bob", new BigDecimal("3000"), "Возврат долга");

        assertNotNull(result);
        assertEquals(TransactionType.TRANSFER_OUT, result.senderTransaction().getType());
        assertEquals(TransactionType.TRANSFER_IN, result.recipientTransaction().getType());
        assertEquals(new BigDecimal("3000"), result.senderTransaction().getAmount());
        assertEquals(new BigDecimal("3000"), result.recipientTransaction().getAmount());

        Wallet updatedAlice = walletService.getWalletByUserId(alice.getId());
        Wallet updatedBob = walletService.getWalletByUserId(bob.getId());

        assertEquals(new BigDecimal("7000"), updatedAlice.getBalance());
        assertEquals(new BigDecimal("3000"), updatedBob.getBalance());
    }

    @Test
    void testTransferInsufficientFundsThrows() {
        assertThrows(IllegalStateException.class, () ->
                walletService.transfer(alice, "bob", new BigDecimal("15000"), "Слишком много"));
    }

    @Test
    void testTransferToSelfThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                walletService.transfer(alice, "alice", new BigDecimal("1000"), "Себе"));
    }

    @Test
    void testTransferToNonExistentUserThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                walletService.transfer(alice, "charlie", new BigDecimal("1000"), "Призраку"));
    }

    @Test
    void testTransferZeroOrNegativeThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                walletService.transfer(alice, "bob", BigDecimal.ZERO, "Ноль"));
        assertThrows(IllegalArgumentException.class, () ->
                walletService.transfer(alice, "bob", new BigDecimal("-500"), "Минус"));
    }
}
