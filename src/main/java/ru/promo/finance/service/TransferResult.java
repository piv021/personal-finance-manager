package ru.promo.finance.service;

import ru.promo.finance.domain.model.Alert;
import ru.promo.finance.domain.model.Transaction;

import java.util.List;

/**
 * Результат выполнения перевода между пользователями.
 */
public record TransferResult(
        Transaction senderTransaction,
        Transaction recipientTransaction,
        List<Alert> senderAlerts
) {}
