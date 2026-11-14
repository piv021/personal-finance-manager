package ru.promo.finance.infrastructure.storage;

import ru.promo.finance.domain.model.*;
import ru.promo.finance.domain.repository.UserRepository;
import ru.promo.finance.domain.repository.WalletRepository;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Сервис постоянного хранения данных на диске (JSON-файлы).
 * Сохраняет пользователей и кошельки.
 */
public class FileStorageService {
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private final Path dataDir;
    private final Path usersFile;

    public FileStorageService(Path dataDir) {
        this.dataDir = dataDir;
        this.usersFile = dataDir.resolve("users.json");
        try {
            Files.createDirectories(dataDir);
        } catch (IOException e) {
            throw new RuntimeException("Не удалось создать директорию данных: " + dataDir, e);
        }
    }

    public FileStorageService() {
        this(Paths.get("data"));
    }

    // ================= USERS PERSISTENCE =================

    public synchronized void loadUsers(UserRepository userRepository) {
        if (!Files.exists(usersFile)) {
            return;
        }
        try {
            String json = Files.readString(usersFile, StandardCharsets.UTF_8);
            if (json.isBlank()) return;
            List<Object> list = SimpleJson.parseArray(json);
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) {
                    String id = String.valueOf(m.get("id"));
                    String login = String.valueOf(m.get("login"));
                    String passwordHash = String.valueOf(m.get("passwordHash"));
                    String salt = String.valueOf(m.get("salt"));
                    String createdAtStr = (String) m.get("createdAt");
                    LocalDateTime createdAt = createdAtStr != null ? LocalDateTime.parse(createdAtStr, ISO) : LocalDateTime.now();
                    User user = new User(id, login, passwordHash, salt, createdAt);
                    userRepository.save(user);
                }
            }
        } catch (Exception e) {
            System.err.println("[Предупреждение] Не удалось загрузить пользователей из файла: " + e.getMessage());
        }
    }

    public synchronized void saveUsers(UserRepository userRepository) {
        try {
            List<User> users = userRepository.findAll();
            List<Map<String, Object>> list = new ArrayList<>();
            for (User u : users) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("id", u.getId());
                map.put("login", u.getLogin());
                map.put("passwordHash", u.getPasswordHash());
                map.put("salt", u.getSalt());
                map.put("createdAt", u.getCreatedAt().format(ISO));
                list.add(map);
            }
            String json = SimpleJson.stringify(list);
            writeAtomically(usersFile, json);
        } catch (Exception e) {
            System.err.println("[Ошибка] Не удалось сохранить пользователей: " + e.getMessage());
        }
    }

    // ================= WALLET PERSISTENCE =================

    private Path getWalletFilePath(String userLogin) {
        String safeLogin = userLogin.replaceAll("[^a-zA-Z0-9_-]", "_");
        return dataDir.resolve("wallet_" + safeLogin + ".json");
    }

    public synchronized Optional<Wallet> loadWallet(User user) {
        Path file = getWalletFilePath(user.getLogin());
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            if (json.isBlank()) return Optional.empty();
            Map<String, Object> map = SimpleJson.parseObject(json);
            Wallet wallet = deserializeWallet(map, user.getId());
            return Optional.of(wallet);
        } catch (Exception e) {
            System.err.println("[Предупреждение] Ошибка при чтении кошелька для " + user.getLogin() + ": " + e.getMessage());
            return Optional.empty();
        }
    }

    public synchronized void saveWallet(User user, Wallet wallet) {
        Path file = getWalletFilePath(user.getLogin());
        try {
            Map<String, Object> map = serializeWallet(wallet, user.getLogin());
            String json = SimpleJson.stringify(map);
            writeAtomically(file, json);
        } catch (Exception e) {
            System.err.println("[Ошибка] Не удалось сохранить кошелёк пользователя " + user.getLogin() + ": " + e.getMessage());
        }
    }

    public synchronized void saveAllWallets(UserRepository userRepository, WalletRepository walletRepository) {
        saveUsers(userRepository);
        for (User user : userRepository.findAll()) {
            walletRepository.findByUserId(user.getId()).ifPresent(w -> saveWallet(user, w));
        }
    }

    // ================= SERIALIZATION HELPERS =================

    public Map<String, Object> serializeWallet(Wallet wallet, String login) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", wallet.getId());
        map.put("userId", wallet.getUserId());
        map.put("userLogin", login);

        // Categories
        map.put("categories", new ArrayList<>(wallet.getCategories()));

        // Budgets
        Map<String, Object> budgetsMap = new LinkedHashMap<>();
        for (Budget b : wallet.getBudgets().values()) {
            Map<String, Object> bMap = new LinkedHashMap<>();
            bMap.put("category", b.getCategory());
            bMap.put("limitAmount", b.getLimitAmount().toPlainString());
            budgetsMap.put(b.getCategory(), bMap);
        }
        map.put("budgets", budgetsMap);

        // Transactions
        List<Map<String, Object>> txList = new ArrayList<>();
        for (Transaction t : wallet.getTransactions()) {
            Map<String, Object> tMap = new LinkedHashMap<>();
            tMap.put("id", t.getId());
            tMap.put("userId", t.getUserId());
            tMap.put("type", t.getType().name());
            tMap.put("category", t.getCategory());
            tMap.put("amount", t.getAmount().toPlainString());
            tMap.put("note", t.getNote());
            tMap.put("timestamp", t.getTimestamp().format(ISO));
            txList.add(tMap);
        }
        map.put("transactions", txList);

        return map;
    }

    @SuppressWarnings("unchecked")
    public Wallet deserializeWallet(Map<String, Object> map, String fallbackUserId) {
        String id = (String) map.get("id");
        String userId = (String) map.getOrDefault("userId", fallbackUserId);

        Set<String> categories = new HashSet<>();
        if (map.get("categories") instanceof List<?> list) {
            for (Object o : list) {
                if (o != null) categories.add(String.valueOf(o));
            }
        }

        Map<String, Budget> budgets = new HashMap<>();
        if (map.get("budgets") instanceof Map<?, ?> bMap) {
            for (Map.Entry<?, ?> entry : bMap.entrySet()) {
                if (entry.getValue() instanceof Map<?, ?> item) {
                    String cat = String.valueOf(item.get("category"));
                    BigDecimal limit = new BigDecimal(String.valueOf(item.get("limitAmount")));
                    budgets.put(cat.toLowerCase(), new Budget(cat, limit));
                    categories.add(cat);
                }
            }
        }

        List<Transaction> transactions = new ArrayList<>();
        if (map.get("transactions") instanceof List<?> txList) {
            for (Object o : txList) {
                if (o instanceof Map<?, ?> tMap) {
                    String txId = tMap.get("id") != null ? String.valueOf(tMap.get("id")) : null;
                    String txUserId = tMap.get("userId") != null ? String.valueOf(tMap.get("userId")) : fallbackUserId;
                    TransactionType type = TransactionType.valueOf(String.valueOf(tMap.get("type")));
                    String cat = String.valueOf(tMap.get("category"));
                    BigDecimal amount = new BigDecimal(String.valueOf(tMap.get("amount")));
                    String note = tMap.get("note") != null ? String.valueOf(tMap.get("note")) : "";
                    String tsStr = tMap.get("timestamp") != null ? String.valueOf(tMap.get("timestamp")) : null;
                    LocalDateTime ts = tsStr != null ? LocalDateTime.parse(tsStr, ISO) : LocalDateTime.now();
                    transactions.add(new Transaction(txId, txUserId, type, cat, amount, note, ts));
                    categories.add(cat);
                }
            }
        }

        return new Wallet(id, userId, transactions, budgets, categories);
    }

    private void writeAtomically(Path target, String content) throws IOException {
        Path temp = Files.createTempFile(dataDir, "tmp_", ".tmp");
        Files.writeString(temp, content, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
