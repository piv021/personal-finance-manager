package ru.promo.finance.service;

import ru.promo.finance.domain.model.*;
import ru.promo.finance.domain.repository.UserRepository;
import ru.promo.finance.domain.repository.WalletRepository;
import ru.promo.finance.infrastructure.storage.FileStorageService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Основной сервис для работы с кошельком: доходы, расходы, переводы.
 */
public class WalletService {
    private final WalletRepository walletRepository;
    private final UserRepository userRepository;
    private final AlertService alertService;
    private final FileStorageService fileStorageService;

    public WalletService(WalletRepository walletRepository,
                         UserRepository userRepository,
                         AlertService alertService,
                         FileStorageService fileStorageService) {
        this.walletRepository = Objects.requireNonNull(walletRepository);
        this.userRepository = Objects.requireNonNull(userRepository);
        this.alertService = Objects.requireNonNull(alertService);
        this.fileStorageService = Objects.requireNonNull(fileStorageService);
    }

    public Wallet getWalletByUserId(String userId) {
        return walletRepository.findByUserId(userId)
                .orElseThrow(() -> new NoSuchElementException("Кошелек для пользователя ID " + userId + " не найден"));
    }

    /**
     * Добавление дохода в кошелек.
     */
    public Transaction addIncome(String userId, String category, BigDecimal amount, String note) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Сумма дохода должна быть строго положительной");
        }
        if (category == null || category.trim().isEmpty()) {
            throw new IllegalArgumentException("Категория дохода не может быть пустой");
        }
        Wallet wallet = getWalletByUserId(userId);
        Transaction tx = new Transaction(userId, TransactionType.INCOME, category.trim(), amount, note);
        wallet.addTransaction(tx);
        walletRepository.save(wallet);
        return tx;
    }

    /**
     * Добавление расхода в кошелек.
     * Возвращает пару (транзакция, список возникших оповещений).
     */
    public record ExpenseResult(Transaction transaction, List<Alert> alerts) {}

    public ExpenseResult addExpense(String userId, String category, BigDecimal amount, String note) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Сумма расхода должна быть строго положительной");
        }
        if (category == null || category.trim().isEmpty()) {
            throw new IllegalArgumentException("Категория расхода не может быть пустой");
        }
        Wallet wallet = getWalletByUserId(userId);
        Transaction tx = new Transaction(userId, TransactionType.EXPENSE, category.trim(), amount, note);
        wallet.addTransaction(tx);
        walletRepository.save(wallet);

        // Проверяем оповещения по категории и балансу
        List<Alert> alerts = alertService.checkAlertsOnExpense(wallet, category.trim());
        return new ExpenseResult(tx, alerts);
    }

    /**
     * Перевод между кошельками пользователей.
     * При переводе фиксируется расход у отправителя и доход у получателя.
     */
    public TransferResult transfer(User sender, String recipientLogin, BigDecimal amount, String note) {
        if (sender == null) {
            throw new IllegalArgumentException("Отправитель не может быть null");
        }
        if (recipientLogin == null || recipientLogin.trim().isEmpty()) {
            throw new IllegalArgumentException("Логин получателя не может быть пустым");
        }
        String trimmedRecipient = recipientLogin.trim();
        if (sender.getLogin().equalsIgnoreCase(trimmedRecipient)) {
            throw new IllegalArgumentException("Нельзя перевести средства самому себе");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Сумма перевода должна быть строго положительной");
        }

        User recipient = userRepository.findByLogin(trimmedRecipient)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь-получатель '" + trimmedRecipient + "' не найден"));

        Wallet senderWallet = getWalletByUserId(sender.getId());
        BigDecimal senderBalance = senderWallet.getBalance();
        if (senderBalance.compareTo(amount) < 0) {
            throw new IllegalStateException(String.format(
                    "Недостаточно средств для перевода. Доступный баланс: %,.2f, сумма перевода: %,.2f",
                    senderBalance, amount
            ));
        }

        // Загружаем кошелек получателя (если не в памяти)
        Wallet recipientWallet = walletRepository.findByUserId(recipient.getId())
                .orElseGet(() -> {
                    Optional<Wallet> fileWallet = fileStorageService.loadWallet(recipient);
                    Wallet w = fileWallet.orElseGet(() -> new Wallet(recipient.getId()));
                    walletRepository.save(w);
                    return w;
                });

        LocalDateTime now = LocalDateTime.now();
        String senderNote = (note != null && !note.isBlank())
                ? "Перевод для " + recipient.getLogin() + " (" + note.trim() + ")"
                : "Перевод для " + recipient.getLogin();
        String recipientNote = (note != null && !note.isBlank())
                ? "Перевод от " + sender.getLogin() + " (" + note.trim() + ")"
                : "Перевод от " + sender.getLogin();

        Transaction outTx = new Transaction(
                UUID.randomUUID().toString(),
                sender.getId(),
                TransactionType.TRANSFER_OUT,
                "Перевод",
                amount,
                senderNote,
                now
        );
        Transaction inTx = new Transaction(
                UUID.randomUUID().toString(),
                recipient.getId(),
                TransactionType.TRANSFER_IN,
                "Перевод",
                amount,
                recipientNote,
                now
        );

        senderWallet.addTransaction(outTx);
        recipientWallet.addTransaction(inTx);

        walletRepository.save(senderWallet);
        walletRepository.save(recipientWallet);

        // Сохраняем получателя сразу на диск, чтобы его кошелек не потерялся
        fileStorageService.saveWallet(recipient, recipientWallet);

        List<Alert> alerts = alertService.checkAlertsOnExpense(senderWallet, "Перевод");
        return new TransferResult(outTx, inTx, alerts);
    }
}
