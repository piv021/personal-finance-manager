package ru.promo.finance.infrastructure.storage;

import ru.promo.finance.domain.model.Wallet;
import ru.promo.finance.domain.repository.WalletRepository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory реализация репозитория кошельков.
 */
public class InMemoryWalletRepository implements WalletRepository {
    private final Map<String, Wallet> walletsById = new ConcurrentHashMap<>();
    private final Map<String, String> walletIdByUserId = new ConcurrentHashMap<>();

    @Override
    public Optional<Wallet> findById(String id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(walletsById.get(id));
    }

    @Override
    public Optional<Wallet> findByUserId(String userId) {
        if (userId == null) return Optional.empty();
        String id = walletIdByUserId.get(userId);
        if (id == null) return Optional.empty();
        return Optional.ofNullable(walletsById.get(id));
    }

    @Override
    public Wallet save(Wallet wallet) {
        Objects.requireNonNull(wallet, "Wallet cannot be null");
        walletsById.put(wallet.getId(), wallet);
        walletIdByUserId.put(wallet.getUserId(), wallet.getId());
        return wallet;
    }

    @Override
    public List<Wallet> findAll() {
        return new ArrayList<>(walletsById.values());
    }

    @Override
    public void deleteByUserId(String userId) {
        if (userId == null) return;
        String id = walletIdByUserId.remove(userId);
        if (id != null) {
            walletsById.remove(id);
        }
    }

    @Override
    public void clear() {
        walletsById.clear();
        walletIdByUserId.clear();
    }
}
