package ru.promo.finance.infrastructure.storage;

import ru.promo.finance.domain.model.User;
import ru.promo.finance.domain.repository.UserRepository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory реализация репозитория пользователей.
 */
public class InMemoryUserRepository implements UserRepository {
    private final Map<String, User> usersById = new ConcurrentHashMap<>();
    private final Map<String, String> idByLoginLower = new ConcurrentHashMap<>();

    @Override
    public Optional<User> findById(String id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(usersById.get(id));
    }

    @Override
    public Optional<User> findByLogin(String login) {
        if (login == null) return Optional.empty();
        String id = idByLoginLower.get(login.trim().toLowerCase());
        if (id == null) return Optional.empty();
        return Optional.ofNullable(usersById.get(id));
    }

    @Override
    public User save(User user) {
        Objects.requireNonNull(user, "User cannot be null");
        usersById.put(user.getId(), user);
        idByLoginLower.put(user.getLogin().trim().toLowerCase(), user.getId());
        return user;
    }

    @Override
    public List<User> findAll() {
        return new ArrayList<>(usersById.values());
    }

    @Override
    public boolean existsByLogin(String login) {
        if (login == null) return false;
        return idByLoginLower.containsKey(login.trim().toLowerCase());
    }

    @Override
    public void deleteById(String id) {
        if (id == null) return;
        User removed = usersById.remove(id);
        if (removed != null) {
            idByLoginLower.remove(removed.getLogin().trim().toLowerCase());
        }
    }

    @Override
    public void clear() {
        usersById.clear();
        idByLoginLower.clear();
    }
}
