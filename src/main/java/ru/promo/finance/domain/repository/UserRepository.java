package ru.promo.finance.domain.repository;

import ru.promo.finance.domain.model.User;

import java.util.List;
import java.util.Optional;

/**
 * Репозиторий пользователей.
 */
public interface UserRepository {
    Optional<User> findById(String id);
    Optional<User> findByLogin(String login);
    User save(User user);
    List<User> findAll();
    boolean existsByLogin(String login);
    void deleteById(String id);
    void clear();
}
