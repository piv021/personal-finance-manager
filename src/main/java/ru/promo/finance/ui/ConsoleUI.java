package ru.promo.finance.ui;

import ru.promo.finance.domain.model.*;
import ru.promo.finance.domain.repository.UserRepository;
import ru.promo.finance.domain.repository.WalletRepository;
import ru.promo.finance.infrastructure.export.ReportExporter;
import ru.promo.finance.infrastructure.storage.FileStorageService;
import ru.promo.finance.service.*;

import java.io.*;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Интерактивный CLI-интерфейс приложения личных финансов.
 */
public class ConsoleUI {
    private final AuthService authService;
    private final WalletService walletService;
    private final BudgetService budgetService;
    private final CategoryService categoryService;
    private final StatisticsService statisticsService;
    private final AlertService alertService;
    private final ReportExporter reportExporter;
    private final FileStorageService fileStorageService;
    private final UserRepository userRepository;
    private final WalletRepository walletRepository;

    private User currentUser;
    private boolean running = true;
    private final Scanner scanner;
    private final PrintStream out;

    public ConsoleUI(AuthService authService,
                     WalletService walletService,
                     BudgetService budgetService,
                     CategoryService categoryService,
                     StatisticsService statisticsService,
                     AlertService alertService,
                     ReportExporter reportExporter,
                     FileStorageService fileStorageService,
                     UserRepository userRepository,
                     WalletRepository walletRepository,
                     InputStream in,
                     PrintStream out) {
        this.authService = authService;
        this.walletService = walletService;
        this.budgetService = budgetService;
        this.categoryService = categoryService;
        this.statisticsService = statisticsService;
        this.alertService = alertService;
        this.reportExporter = reportExporter;
        this.fileStorageService = fileStorageService;
        this.userRepository = userRepository;
        this.walletRepository = walletRepository;
        this.scanner = new Scanner(in);
        this.out = out;
    }

    public void start() {
        printBanner();
        while (running && scanner.hasNextLine()) {
            String line = scanner.nextLine().trim();
            if (line.isEmpty()) continue;
            processCommand(line);
        }
        onExit();
    }

    public void processCommand(String rawLine) {
        List<String> tokens = parseTokens(rawLine);
        if (tokens.isEmpty()) return;

        String cmd = tokens.get(0).toLowerCase();
        List<String> args = tokens.subList(1, tokens.size());

        try {
            if (currentUser == null) {
                handleUnauthenticated(cmd, args);
            } else {
                handleAuthenticated(cmd, args);
            }
        } catch (IllegalArgumentException | IllegalStateException | NoSuchElementException e) {
            out.println("[Ошибка] " + e.getMessage());
        } catch (Exception e) {
            out.println("[Внутренняя ошибка] " + e.getMessage());
        }
    }

    private void handleUnauthenticated(String cmd, List<String> args) {
        switch (cmd) {
            case "register", "reg" -> handleRegister(args);
            case "login" -> handleLogin(args);
            case "help", "?" -> printUnauthenticatedHelp();
            case "exit", "quit" -> {
                running = false;
                out.println("Выход из программы. До свидания!");
            }
            default -> out.println("Команда '" + cmd + "' неизвестна. Сначала войдите в систему (login <логин> <пароль>) или зарегистрируйтесь (register <логин> <пароль>). Введите 'help' для справки.");
        }
    }

    private void handleAuthenticated(String cmd, List<String> args) {
        switch (cmd) {
            case "income" -> handleAddIncome(args);
            case "expense" -> handleAddExpense(args);
            case "budget" -> handleBudgetCommand(args);
            case "category" -> handleCategoryCommand(args);
            case "stats" -> handleStats(args);
            case "calc" -> handleCalc(args);
            case "transfer" -> handleTransfer(args);
            case "history" -> handleHistory(args);
            case "alerts" -> handleAlerts();
            case "export-csv" -> handleExportCsv(args);
            case "import-csv" -> handleImportCsv(args);
            case "export-json" -> handleExportJson(args);
            case "import-json" -> handleImportJson(args);
            case "load-sample" -> handleLoadSample();
            case "whoami" -> handleWhoami();
            case "save" -> handleSave();
            case "logout" -> handleLogout();
            case "help", "?" -> printAuthenticatedHelp();
            case "exit", "quit" -> {
                running = false;
                out.println("Сохранение данных и выход из программы...");
            }
            default -> out.println("Неизвестная команда '" + cmd + "'. Введите 'help' для просмотра списка доступных команд.");
        }
    }

    // ================= COMMAND HANDLERS =================

    private void handleRegister(List<String> args) {
        if (args.size() < 2) {
            out.println("Использование: register <логин> <пароль>");
            return;
        }
        String login = args.get(0);
        String password = args.get(1);
        User user = authService.register(login, password);
        this.currentUser = user;
        out.println("[Успех] Пользователь '" + user.getLogin() + "' успешно зарегистрирован и авторизован.");
        printUserPrompt();
    }

    private void handleLogin(List<String> args) {
        if (args.size() < 2) {
            out.println("Использование: login <логин> <пароль>");
            return;
        }
        String login = args.get(0);
        String password = args.get(1);
        User user = authService.login(login, password);
        this.currentUser = user;
        out.println("[Успех] Авторизация успешна. Добро пожаловать, " + user.getLogin() + "!");
        Wallet wallet = walletService.getWalletByUserId(user.getId());
        out.printf("Текущий баланс: %,.2f\n", wallet.getBalance());
        List<Alert> alerts = alertService.checkAllAlerts(wallet, null, null);
        if (!alerts.isEmpty()) {
            out.println("\n[!] Обратите внимание на активные оповещения:");
            alerts.forEach(a -> out.println("    " + a.message()));
        }
        printUserPrompt();
    }

    private void handleLogout() {
        if (currentUser != null) {
            authService.logout(currentUser);
            out.println("[Инфо] Пользователь '" + currentUser.getLogin() + "' вышел. Данные сохранены.");
            currentUser = null;
        }
    }

    private void handleAddIncome(List<String> args) {
        if (args.size() < 2) {
            out.println("Использование: income <категория> <сумма> [описание]");
            return;
        }
        String category = args.get(0);
        BigDecimal amount = parseBigDecimal(args.get(1), "Сумма дохода");
        String note = args.size() > 2 ? String.join(" ", args.subList(2, args.size())) : "";

        Transaction tx = walletService.addIncome(currentUser.getId(), category, amount, note);
        out.printf("[Успех] Добавлен доход: %s: %,.2f (%s)\n", tx.getCategory(), tx.getAmount(), tx.getNote());
        printCurrentBalance();
    }

    private void handleAddExpense(List<String> args) {
        if (args.size() < 2) {
            out.println("Использование: expense <категория> <сумма> [описание]");
            return;
        }
        String category = args.get(0);
        BigDecimal amount = parseBigDecimal(args.get(1), "Сумма расхода");
        String note = args.size() > 2 ? String.join(" ", args.subList(2, args.size())) : "";

        WalletService.ExpenseResult result = walletService.addExpense(currentUser.getId(), category, amount, note);
        Transaction tx = result.transaction();
        out.printf("[Успех] Добавлен расход: %s: %,.2f (%s)\n", tx.getCategory(), tx.getAmount(), tx.getNote());

        // Вывод оповещений при добавлении
        for (Alert alert : result.alerts()) {
            out.println("  [ОПОВЕЩЕНИЕ] " + alert.message());
        }

        printCurrentBalance();
    }

    private void handleBudgetCommand(List<String> args) {
        if (args.isEmpty()) {
            out.println("Использование: budget <set|edit|delete|list> [аргументы]");
            return;
        }
        String sub = args.get(0).toLowerCase();
        Wallet wallet = walletService.getWalletByUserId(currentUser.getId());

        switch (sub) {
            case "set" -> {
                if (args.size() < 3) {
                    out.println("Использование: budget set <категория> <лимит>");
                    return;
                }
                String cat = args.get(1);
                BigDecimal limit = parseBigDecimal(args.get(2), "Лимит бюджета");
                budgetService.setBudget(wallet, cat, limit);
                out.printf("[Успех] Установлен бюджет для категории '%s': %,.2f\n", cat, limit);
            }
            case "edit" -> {
                if (args.size() < 3) {
                    out.println("Использование: budget edit <категория> <новый_лимит>");
                    return;
                }
                String cat = args.get(1);
                BigDecimal limit = parseBigDecimal(args.get(2), "Новый лимит бюджета");
                budgetService.editBudget(wallet, cat, limit);
                out.printf("[Успех] Изменен бюджет для категории '%s': %,.2f\n", cat, limit);
            }
            case "delete", "remove" -> {
                if (args.size() < 2) {
                    out.println("Использование: budget delete <категория>");
                    return;
                }
                String cat = args.get(1);
                boolean removed = budgetService.removeBudget(wallet, cat);
                if (removed) {
                    out.println("[Успех] Бюджет для категории '" + cat + "' удален.");
                } else {
                    out.println("[Инфо] Бюджет для категории '" + cat + "' не был установлен.");
                }
            }
            case "list" -> {
                List<BudgetStatus> statuses = budgetService.getAllBudgetStatuses(wallet, null, null);
                if (statuses.isEmpty()) {
                    out.println("Бюджеты не установлены. Установите бюджет командой: budget set <категория> <лимит>");
                    return;
                }
                List<String> headers = List.of("Категория", "Лимит", "Израсходовано", "Остаток", "% Использования", "Статус");
                List<List<String>> rows = new ArrayList<>();
                for (BudgetStatus bs : statuses) {
                    String statusText = bs.isExceeded() ? "ПРЕВЫШЕН!" : (bs.isWarning80() ? "Внимание (>=80%)" : "Норма");
                    rows.add(List.of(
                            bs.category(),
                            String.format("%,.2f", bs.limitAmount()),
                            String.format("%,.2f", bs.spentAmount()),
                            String.format("%,.2f", bs.remainingAmount()),
                            String.format("%.1f%%", bs.percentUsed()),
                            statusText
                    ));
                }
                out.println(TableFormatter.renderTable(headers, rows));
            }
            default -> out.println("Неизвестная подкоманда 'budget " + sub + "'. Допустимо: set, edit, delete, list.");
        }
    }

    private void handleCategoryCommand(List<String> args) {
        if (args.isEmpty()) {
            out.println("Использование: category <add|rename|delete|list> [аргументы]");
            return;
        }
        String sub = args.get(0).toLowerCase();
        Wallet wallet = walletService.getWalletByUserId(currentUser.getId());

        switch (sub) {
            case "add" -> {
                if (args.size() < 2) {
                    out.println("Использование: category add <название>");
                    return;
                }
                String name = args.get(1);
                categoryService.addCategory(wallet, name);
                out.println("[Успех] Добавлена новая категория: " + name);
            }
            case "rename" -> {
                if (args.size() < 3) {
                    out.println("Использование: category rename <старое_название> <новое_название>");
                    return;
                }
                String oldName = args.get(1);
                String newName = args.get(2);
                categoryService.renameCategory(wallet, oldName, newName);
                out.println("[Успех] Категория '" + oldName + "' успешно переименована в '" + newName + "' (все операции обновлены).");
            }
            case "delete" -> {
                if (args.size() < 2) {
                    out.println("Использование: category delete <название>");
                    return;
                }
                String name = args.get(1);
                categoryService.deleteCategory(wallet, name);
                out.println("[Успех] Категория '" + name + "' удалена.");
            }
            case "list" -> {
                Set<String> categories = categoryService.getCategories(wallet);
                out.println("Список доступных категорий (" + categories.size() + "):");
                for (String c : categories) {
                    boolean hasBudget = wallet.getBudget(c).isPresent();
                    out.println("  • " + c + (hasBudget ? " [установлен бюджет: " + wallet.getBudget(c).get().getLimitAmount() + "]" : ""));
                }
            }
            default -> out.println("Неизвестная подкоманда: category " + sub + ". Допустимо: add, rename, delete, list.");
        }
    }

    private void handleStats(List<String> args) {
        Wallet wallet = walletService.getWalletByUserId(currentUser.getId());

        LocalDateTime from = null;
        LocalDateTime to = null;
        Set<String> filterCategories = null;
        Path exportFile = null;

        for (int i = 0; i < args.size(); i++) {
            String arg = args.get(i);
            if (arg.equalsIgnoreCase("--file") && i + 1 < args.size()) {
                exportFile = Paths.get(args.get(++i));
            } else if (arg.equalsIgnoreCase("--categories") && i + 1 < args.size()) {
                String[] cats = args.get(++i).split(",");
                filterCategories = new HashSet<>(Arrays.asList(cats));
            } else if (arg.equalsIgnoreCase("--from") && i + 1 < args.size()) {
                from = parseDate(args.get(++i)).atStartOfDay();
            } else if (arg.equalsIgnoreCase("--to") && i + 1 < args.size()) {
                to = parseDate(args.get(++i)).atTime(LocalTime.MAX);
            }
        }

        FinancialSummary summary = statisticsService.calculateSummary(wallet, from, to, filterCategories);
        String report = statisticsService.formatSummaryText(summary);

        if (exportFile != null) {
            try {
                reportExporter.exportReportToText(report, exportFile);
                out.println("[Успех] Финансовый отчёт сохранён в файл: " + exportFile.toAbsolutePath());
            } catch (IOException e) {
                out.println("[Ошибка] Не удалось записать отчёт в файл: " + e.getMessage());
            }
        } else {
            out.println(report);
        }
    }

    private void handleCalc(List<String> args) {
        if (args.isEmpty()) {
            out.println("Использование: calc <категория1> [категория2 ...]");
            out.println("Пример: calc Еда Развлечения Такси");
            return;
        }
        Wallet wallet = walletService.getWalletByUserId(currentUser.getId());
        Set<String> requested = new LinkedHashSet<>(args);
        FinancialSummary summary = statisticsService.calculateSummary(wallet, null, null, requested);
        out.println(statisticsService.formatSummaryText(summary));
    }

    private void handleTransfer(List<String> args) {
        if (args.size() < 2) {
            out.println("Использование: transfer <логин_получателя> <сумма> [описание]");
            return;
        }
        String recipientLogin = args.get(0);
        BigDecimal amount = parseBigDecimal(args.get(1), "Сумма перевода");
        String note = args.size() > 2 ? String.join(" ", args.subList(2, args.size())) : "";

        TransferResult result = walletService.transfer(currentUser, recipientLogin, amount, note);
        out.printf("[Успех] Перевод %,.2f пользователю '%s' выполнен успешно.\n", amount, recipientLogin);
        if (!result.senderAlerts().isEmpty()) {
            result.senderAlerts().forEach(a -> out.println("  [ОПОВЕЩЕНИЕ] " + a.message()));
        }
        printCurrentBalance();
    }

    private void handleHistory(List<String> args) {
        Wallet wallet = walletService.getWalletByUserId(currentUser.getId());
        List<Transaction> txs = new ArrayList<>(wallet.getTransactions());
        if (txs.isEmpty()) {
            out.println("История операций пуста.");
            return;
        }
        int limit = 20;
        if (!args.isEmpty()) {
            try {
                limit = Integer.parseInt(args.get(0));
            } catch (NumberFormatException ignored) {}
        }

        // Сортировка по убыванию даты
        txs.sort((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()));
        if (txs.size() > limit) {
            txs = txs.subList(0, limit);
        }

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
        List<String> headers = List.of("Дата", "Тип", "Категория", "Сумма", "Примечание");
        List<List<String>> rows = new ArrayList<>();
        for (Transaction t : txs) {
            String sign = t.getType().isIncome() ? "+" : "-";
            rows.add(List.of(
                    t.getTimestamp().format(dtf),
                    t.getType().getDisplayName(),
                    t.getCategory(),
                    String.format("%s%,.2f", sign, t.getAmount()),
                    t.getNote()
            ));
        }
        out.println(TableFormatter.renderTable(headers, rows));
    }

    private void handleAlerts() {
        Wallet wallet = walletService.getWalletByUserId(currentUser.getId());
        List<Alert> alerts = alertService.checkAllAlerts(wallet, null, null);
        if (alerts.isEmpty()) {
            out.println("[Отлично] Финансовых нарушений и предупреждений нет. Все бюджеты в норме.");
        } else {
            out.println("Текущие финансовые оповещения (" + alerts.size() + "):");
            for (Alert a : alerts) {
                out.println("  [!] " + a.message());
            }
        }
    }

    private void handleExportCsv(List<String> args) {
        if (args.isEmpty()) {
            out.println("Использование: export-csv <путь_к_файлу.csv>");
            return;
        }
        Path path = Paths.get(args.get(0));
        Wallet wallet = walletService.getWalletByUserId(currentUser.getId());
        try {
            reportExporter.exportTransactionsToCsv(wallet.getTransactions(), path);
            out.println("[Успех] " + wallet.getTransactions().size() + " операций экспортировано в " + path.toAbsolutePath());
        } catch (IOException e) {
            out.println("[Ошибка экспорта CSV] " + e.getMessage());
        }
    }

    private void handleImportCsv(List<String> args) {
        if (args.isEmpty()) {
            out.println("Использование: import-csv <путь_к_файлу.csv>");
            return;
        }
        Path path = Paths.get(args.get(0));
        Wallet wallet = walletService.getWalletByUserId(currentUser.getId());
        try {
            int imported = reportExporter.importTransactionsFromCsv(path, wallet);
            walletRepository.save(wallet);
            out.println("[Успех] Импортировано " + imported + " новых операций из " + path.toAbsolutePath());
            printCurrentBalance();
        } catch (Exception e) {
            out.println("[Ошибка импорта CSV] " + e.getMessage());
        }
    }

    private void handleExportJson(List<String> args) {
        if (args.isEmpty()) {
            out.println("Использование: export-json <путь_к_файлу.json>");
            return;
        }
        Path path = Paths.get(args.get(0));
        Wallet wallet = walletService.getWalletByUserId(currentUser.getId());
        try {
            reportExporter.exportWalletToJson(wallet, currentUser.getLogin(), path);
            out.println("[Успех] Снимок кошелька сохранён в " + path.toAbsolutePath());
        } catch (IOException e) {
            out.println("[Ошибка экспорта JSON] " + e.getMessage());
        }
    }

    private void handleImportJson(List<String> args) {
        if (args.isEmpty()) {
            out.println("Использование: import-json <путь_к_файлу.json>");
            return;
        }
        Path path = Paths.get(args.get(0));
        try {
            Wallet wallet = reportExporter.importWalletFromJson(path, currentUser.getId());
            walletRepository.save(wallet);
            out.println("[Успех] Данные кошелька успешно загружены из снимка: " + path.toAbsolutePath());
            printCurrentBalance();
        } catch (Exception e) {
            out.println("[Ошибка импорта JSON] " + e.getMessage());
        }
    }

    /**
     * Загрузка данных из примера ТЗ для быстрой проверки и демонстрации.
     */
    private void handleLoadSample() {
        Wallet wallet = walletService.getWalletByUserId(currentUser.getId());
        out.println("Загрузка тестовых данных из примера ТЗ...");

        // Расходы:
        // Еда: 300, 500
        // Развлечения: 3000
        // Коммунальные услуги: 3000
        // Такси: 1500
        walletService.addExpense(currentUser.getId(), "Еда", new BigDecimal("300"), "Обед");
        walletService.addExpense(currentUser.getId(), "Еда", new BigDecimal("500"), "Ужин");
        walletService.addExpense(currentUser.getId(), "Развлечения", new BigDecimal("3000"), "Кино и боулинг");
        walletService.addExpense(currentUser.getId(), "Коммунальные услуги", new BigDecimal("3000"), "Оплата ЖКХ");
        walletService.addExpense(currentUser.getId(), "Такси", new BigDecimal("1500"), "Поездка в аэропорт");

        // Доходы:
        // Зарплата: 20000, 40000
        // Бонус: 3000
        walletService.addIncome(currentUser.getId(), "Зарплата", new BigDecimal("20000"), "Аванс");
        walletService.addIncome(currentUser.getId(), "Зарплата", new BigDecimal("40000"), "Основная часть");
        walletService.addIncome(currentUser.getId(), "Бонус", new BigDecimal("3000"), "Квартальная премия");

        // Бюджеты:
        // Еда: 4000
        // Развлечения: 3000
        // Коммунальные услуги: 2500
        budgetService.setBudget(wallet, "Еда", new BigDecimal("4000"));
        budgetService.setBudget(wallet, "Развлечения", new BigDecimal("3000"));
        budgetService.setBudget(wallet, "Коммунальные услуги", new BigDecimal("2500"));

        out.println("[Успех] Пример данных успешно загружен! Ниже представлена сводка (команда stats):");
        out.println();
        handleStats(Collections.emptyList());
    }

    private void handleWhoami() {
        Wallet wallet = walletService.getWalletByUserId(currentUser.getId());
        out.println("Пользователь: " + currentUser.getLogin() + " (ID: " + currentUser.getId() + ")");
        out.printf("Баланс: %,.2f\n", wallet.getBalance());
        out.println("Операций в истории: " + wallet.getTransactions().size());
        out.println("Бюджетов установлено: " + wallet.getBudgets().size());
    }

    private void handleSave() {
        Wallet wallet = walletService.getWalletByUserId(currentUser.getId());
        fileStorageService.saveWallet(currentUser, wallet);
        fileStorageService.saveUsers(userRepository);
        out.println("[Успех] Все данные успешно сохранены на диск.");
    }

    // ================= HELPERS =================

    private void printBanner() {
        out.println("=============================================================");
        out.println("   ПРИЛОЖЕНИЕ ДЛЯ УПРАВЛЕНИЯ ЛИЧНЫМИ ФИНАНСАМИ (PROMO IT)    ");
        out.println("=============================================================");
        out.println("Для начала работы авторизуйтесь: login <логин> <пароль>");
        out.println("или зарегистрируйтесь: register <логин> <пароль>");
        out.println("Для получения списка команд введите 'help'");
        out.println("-------------------------------------------------------------");
    }

    private void printUserPrompt() {
        out.println("Вы вошли как: " + currentUser.getLogin() + ". Введите команду (или 'help'):");
    }

    private void printCurrentBalance() {
        if (currentUser != null) {
            Wallet wallet = walletService.getWalletByUserId(currentUser.getId());
            out.printf("Текущий баланс: %,.2f\n", wallet.getBalance());
        }
    }

    private void onExit() {
        if (currentUser != null) {
            authService.logout(currentUser);
        }
        fileStorageService.saveAllWallets(userRepository, walletRepository);
    }

    private void printUnauthenticatedHelp() {
        out.println("""
                Доступные команды до авторизации:
                  register <логин> <пароль> - регистрация нового пользователя
                  login <логин> <пароль>    - вход в существующий аккаунт
                  help                      - показать эту справку
                  exit                      - завершение работы приложения
                """);
    }

    private void printAuthenticatedHelp() {
        out.println("""
                === СПРАВКА ПО КОМАНДАМ ===
                Управление доходами и расходами:
                  income <категория> <сумма> [описание]
                    Пример: income Зарплата 50000 "Основная часть"
                  expense <категория> <сумма> [описание]
                    Пример: expense "Коммунальные услуги" 3000 "Квартплата"

                Бюджеты:
                  budget set <категория> <лимит>     - установить лимит на категорию
                  budget edit <категория> <лимит>    - изменить существующий лимит
                  budget delete <категория>          - удалить лимит
                  budget list                        - таблица всех бюджетов и их состояния

                Категории:
                  category add <название>            - добавить категорию
                  category rename <старое> <новое>   - переименовать категорию (с обновлением транзакций)
                  category delete <название>         - удалить категорию
                  category list                      - список доступных категорий

                Отчеты и статистика:
                  stats                              - полная финансовая сводка (формат из ТЗ)
                  stats --file <путь>                - сохранить сводку в текстовый файл
                  stats --categories <кат1,кат2>     - подсчет по выбранным категориям
                  stats --from YYYY-MM-DD --to YYYY-MM-DD - статистика за период дат
                  calc <кат1> [кат2 ...]             - быстрый подсчет по набору категорий

                Переводы между пользователями:
                  transfer <получатель> <сумма> [описание]
                    Пример: transfer ivan 1500 "За обед"

                История и оповещения:
                  history [лимит]                    - таблица последних операций (по умолч. 20)
                  alerts                             - список текущих финансовых оповещений
                  load-sample                        - загрузить демо-данные из ТЗ для быстрой проверки

                Экспорт / импорт:
                  export-csv <путь.csv>              - выгрузить операции в CSV
                  import-csv <путь.csv>              - загрузить операции из CSV
                  export-json <путь.json>            - выгрузить полный снимок кошелька в JSON
                  import-json <путь.json>            - загрузить снимок кошелька из JSON

                Системные:
                  whoami                             - информация о текущем пользователе
                  save                               - принудительное сохранение данных в файл
                  logout                             - выйти из учетной записи
                  exit                               - сохранить и выйти из приложения
                """);
    }

    private BigDecimal parseBigDecimal(String s, String fieldName) {
        try {
            BigDecimal bd = new BigDecimal(s.trim().replace(',', '.'));
            return bd;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(fieldName + " должна быть корректным числом (получено: '" + s + "')");
        }
    }

    private LocalDate parseDate(String s) {
        try {
            return LocalDate.parse(s.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Неверный формат даты: '" + s + "'. Ожидается: ГГГГ-ММ-ДД (например, 2026-11-14)");
        }
    }

    public static List<String> parseTokens(String line) {
        List<String> tokens = new ArrayList<>();
        if (line == null || line.isBlank()) return tokens;
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        char quoteChar = '\0';

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if ((c == '"' || c == '\'') && !inQuotes) {
                inQuotes = true;
                quoteChar = c;
            } else if (inQuotes && c == quoteChar) {
                inQuotes = false;
                quoteChar = '\0';
            } else if (!inQuotes && Character.isWhitespace(c)) {
                if (current.length() > 0) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) {
            tokens.add(current.toString());
        }
        return tokens;
    }
}
