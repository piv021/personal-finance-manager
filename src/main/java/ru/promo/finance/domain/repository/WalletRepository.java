package ru.promo.finance.domain.repository;

import ru.promo.finance.domain.model.Wallet;

import java.util.List;
import java.util.Optional;

/**
 * Репозиторий кошельков.
 */
public interface WalletRepository {
    Optional<Wallet> findById(String id);
    Optional<Wallet> findByUserId(String userId);
    Wallet save(Wallet wallet);
    List<Wallet> findAll();
    void deleteByUserId(String userId);
    void clear();
}
