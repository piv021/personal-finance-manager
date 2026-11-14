package ru.promo.finance;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.promo.finance.domain.repository.UserRepository;
import ru.promo.finance.domain.repository.WalletRepository;
import ru.promo.finance.infrastructure.export.ReportExporter;
import ru.promo.finance.infrastructure.storage.FileStorageService;
import ru.promo.finance.infrastructure.storage.InMemoryUserRepository;
import ru.promo.finance.infrastructure.storage.InMemoryWalletRepository;
import ru.promo.finance.service.*;
import ru.promo.finance.ui.ConsoleUI;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConsoleUIIntegrationTest {
    private Path tempDir;
    private UserRepository userRepository;
    private WalletRepository walletRepository;
    private FileStorageService fileStorageService;
    private ReportExporter reportExporter;
    private SecurityService securityService;
    private CategoryService categoryService;
    private AlertService alertService;
    private BudgetService budgetService;
    private StatisticsService statisticsService;
    private AuthService authService;
    private WalletService walletService;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("finance-test-ui");
        userRepository = new InMemoryUserRepository();
        walletRepository = new InMemoryWalletRepository();
        fileStorageService = new FileStorageService(tempDir);
        reportExporter = new ReportExporter(fileStorageService);
        securityService = new SecurityService();
        categoryService = new CategoryService();
        alertService = new AlertService();
        budgetService = new BudgetService(alertService);
        statisticsService = new StatisticsService(alertService, budgetService);
        authService = new AuthService(userRepository, walletRepository, securityService, fileStorageService, categoryService);
        walletService = new WalletService(walletRepository, userRepository, alertService, fileStorageService);
    }

    @Test
    void testFullInteractiveWorkflow() {
        String commands = String.join("\n",
                "register user1 pass123",
                "income Зарплата 60000",
                "income Бонус 3000",
                "budget set Еда 4000",
                "budget set Развлечения 3000",
                "budget set \"Коммунальные услуги\" 2500",
                "expense Еда 300",
                "expense Еда 500",
                "expense Развлечения 3000",
                "expense \"Коммунальные услуги\" 3000",
                "expense Такси 1500",
                "stats",
                "logout",
                "register user2 pass456",
                "logout",
                "login user1 pass123",
                "transfer user2 1000 \"Подарок\"",
                "history 5",
                "exit"
        ) + "\n";

        ByteArrayInputStream in = new ByteArrayInputStream(commands.getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        ConsoleUI ui = new ConsoleUI(
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
                in,
                new PrintStream(out, true, StandardCharsets.UTF_8)
        );

        ui.start();

        String output = out.toString(StandardCharsets.UTF_8);

        assertTrue(output.contains("успешно зарегистрирован и авторизован"));
        assertTrue(output.contains("Общий доход: 63"));
        assertTrue(output.contains("Общие расходы: 8"));
        assertTrue(output.contains("Коммунальные услуги"));
        assertTrue(output.contains("-500"));
        assertTrue(output.contains("пользователю 'user2' выполнен успешно"));
        assertTrue(output.contains("Сохранение данных и выход из программы"));
    }

    @Test
    void testLoadSampleCommand() {
        String commands = String.join("\n",
                "register demoUser demoPass",
                "load-sample",
                "exit"
        ) + "\n";

        ByteArrayInputStream in = new ByteArrayInputStream(commands.getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        ConsoleUI ui = new ConsoleUI(
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
                in,
                new PrintStream(out, true, StandardCharsets.UTF_8)
        );

        ui.start();

        String output = out.toString(StandardCharsets.UTF_8);
        assertTrue(output.contains("Пример данных успешно загружен"));
        assertTrue(output.contains("Общий доход: 63,000.0"));
        assertTrue(output.contains("Общие расходы: 8,300.0"));
    }
}
