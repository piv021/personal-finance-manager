package ru.promo.finance.service;

import ru.promo.finance.domain.model.User;
import ru.promo.finance.domain.model.Wallet;
import ru.promo.finance.domain.repository.UserRepository;
import ru.promo.finance.domain.repository.WalletRepository;
import ru.promo.finance.infrastructure.storage.FileStorageService;

import java.util.Objects;
import java.util.Optional;

/**
 * Сервис аутентификации и регистрации пользователей.
 */
public class AuthService {
    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final SecurityService securityService;
    private final FileStorageService fileStorageService;
    private final CategoryService categoryService;

    public AuthService(UserRepository userRepository,
                       WalletRepository walletRepository,
                       SecurityService securityService,
                       FileStorageService fileStorageService,
                       CategoryService categoryService) {
        this.userRepository = Objects.requireNonNull(userRepository);
        this.walletRepository = Objects.requireNonNull(walletRepository);
        this.securityService = Objects.requireNonNull(securityService);
        this.fileStorageService = Objects.requireNonNull(fileStorageService);
        this.categoryService = Objects.requireNonNull(categoryService);
    }

    public User register(String login, String password) {
        if (login == null || login.trim().isEmpty()) {
            throw new IllegalArgumentException("Логин не может быть пустым");
        }
        String trimmedLogin = login.trim();
        if (trimmedLogin.length() < 3) {
            throw new IllegalArgumentException("Логин должен содержать не менее 3 символов");
        }
        if (!trimmedLogin.matches("^[a-zA-Z0-9_-]+$")) {
            throw new IllegalArgumentException("Логин может содержать только латинские буквы, цифры, знаки подчеркивания и дефисы");
        }
        if (password == null || password.length() < 4) {
            throw new IllegalArgumentException("Пароль должен содержать не менее 4 символов");
        }
        if (userRepository.existsByLogin(trimmedLogin)) {
            throw new IllegalArgumentException("Пользователь с логином '" + trimmedLogin + "' уже существует");
        }

        String salt = securityService.generateSalt();
        String hash = securityService.hashPassword(password, salt);
        User user = new User(trimmedLogin, hash, salt);
        userRepository.save(user);

        // Создаем кошелек с дефолтными категориями
        Wallet wallet = new Wallet(user.getId());
        categoryService.initDefaultCategories(wallet);
        walletRepository.save(wallet);

        // Сохраняем пользователей и кошелек на диск
        fileStorageService.saveUsers(userRepository);
        fileStorageService.saveWallet(user, wallet);

        return user;
    }

    public User login(String login, String password) {
        if (login == null || login.trim().isEmpty()) {
            throw new IllegalArgumentException("Логин не может быть пустым");
        }
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Пароль не может быть пустым");
        }
        String trimmedLogin = login.trim();
        User user = userRepository.findByLogin(trimmedLogin)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь с логином '" + trimmedLogin + "' не найден"));

        if (!securityService.verifyPassword(password, user.getSalt(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Неверный пароль для пользователя '" + trimmedLogin + "'");
        }

        // Загружаем кошелек из файла, если он еще не в памяти
        Optional<Wallet> inMemWallet = walletRepository.findByUserId(user.getId());
        if (inMemWallet.isEmpty()) {
            Optional<Wallet> fileWallet = fileStorageService.loadWallet(user);
            if (fileWallet.isPresent()) {
                walletRepository.save(fileWallet.get());
            } else {
                Wallet newWallet = new Wallet(user.getId());
                categoryService.initDefaultCategories(newWallet);
                walletRepository.save(newWallet);
            }
        }

        return user;
    }

    public void logout(User user) {
        if (user == null) return;
        walletRepository.findByUserId(user.getId()).ifPresent(w -> {
            fileStorageService.saveWallet(user, w);
        });
        fileStorageService.saveUsers(userRepository);
    }
}
