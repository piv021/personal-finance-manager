package ru.promo.finance;

import ru.promo.finance.domain.repository.UserRepository;
import ru.promo.finance.domain.repository.WalletRepository;
import ru.promo.finance.infrastructure.export.ReportExporter;
import ru.promo.finance.infrastructure.storage.FileStorageService;
import ru.promo.finance.infrastructure.storage.InMemoryUserRepository;
import ru.promo.finance.infrastructure.storage.InMemoryWalletRepository;
import ru.promo.finance.service.*;
import ru.promo.finance.ui.ConsoleUI;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Главный класс приложения управления личными финансами (PROMO IT).
 */
public class Main {

    public static void main(String[] args) {
        Path dataDir = Paths.get("data");
        if (args.length > 0 && args[0] != null && !args[0].isBlank()) {
            dataDir = Paths.get(args[0]);
        }

        // Инициализация репозиториев
        UserRepository userRepository = new InMemoryUserRepository();
        WalletRepository walletRepository = new InMemoryWalletRepository();

        // Инициализация инфраструктурных сервисов
        FileStorageService fileStorageService = new FileStorageService(dataDir);
        ReportExporter reportExporter = new ReportExporter(fileStorageService);

        // Инициализация доменных сервисов
        SecurityService securityService = new SecurityService();
        CategoryService categoryService = new CategoryService();
        AlertService alertService = new AlertService();
        BudgetService budgetService = new BudgetService(alertService);
        StatisticsService statisticsService = new StatisticsService(alertService, budgetService);
        AuthService authService = new AuthService(userRepository, walletRepository, securityService, fileStorageService, categoryService);
        WalletService walletService = new WalletService(walletRepository, userRepository, alertService, fileStorageService);

        // Загрузка сохранённых пользователей из хранилища
        fileStorageService.loadUsers(userRepository);

        // Регистрация shutdown hook для гарантированного сохранения данных при выходе
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            fileStorageService.saveAllWallets(userRepository, walletRepository);
        }));

        // Запуск интерфейса командной строки
        ConsoleUI consoleUI = new ConsoleUI(
                authService,
                walletService,
                budgetService,
                categoryService,
                statisticsService,
                alertService,
                reportExporter,
                fileStorageService,
                userRepository,
                walletRepository,
                System.in,
                System.out
        );

        consoleUI.start();
    }
}
