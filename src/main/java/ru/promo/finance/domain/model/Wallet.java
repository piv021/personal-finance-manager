package ru.promo.finance.domain.model;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Кошелёк пользователя.
 * Привязан к пользователю, хранит текущий баланс, список операций,
 * бюджеты по категориям и набор категорий.
 */
public class Wallet {
    private final String id;
    private final String userId;
    private final List<Transaction> transactions;
    private final Map<String, Budget> budgets;
    private final Set<String> categories;

    public Wallet(String id, String userId, List<Transaction> transactions,
                  Map<String, Budget> budgets, Set<String> categories) {
        this.id = (id != null && !id.isBlank()) ? id : UUID.randomUUID().toString();
        this.userId = Objects.requireNonNull(userId, "userId не может быть null");
        this.transactions = new CopyOnWriteArrayList<>(transactions != null ? transactions : Collections.emptyList());
        this.budgets = new ConcurrentHashMap<>();
        if (budgets != null) {
            budgets.forEach((k, v) -> this.budgets.put(k.toLowerCase(), v));
        }
        this.categories = Collections.newSetFromMap(new ConcurrentHashMap<>());
        if (categories != null) {
            this.categories.addAll(categories);
        }
        // Добавляем категории из существующих операций и бюджетов
        this.transactions.forEach(t -> this.categories.add(t.getCategory()));
        this.budgets.values().forEach(b -> this.categories.add(b.getCategory()));
    }

    public Wallet(String userId) {
        this(UUID.randomUUID().toString(), userId, new ArrayList<>(), new HashMap<>(), new HashSet<>());
    }

    public String getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public List<Transaction> getTransactions() {
        return Collections.unmodifiableList(transactions);
    }

    public Map<String, Budget> getBudgets() {
        return Collections.unmodifiableMap(budgets);
    }

    public Set<String> getCategories() {
        return Collections.unmodifiableSet(categories);
    }

    /**
     * Текущий баланс кошелька: (доходы + входящие переводы) - (расходы + исходящие переводы).
     */
    public BigDecimal getBalance() {
        BigDecimal balance = BigDecimal.ZERO;
        for (Transaction t : transactions) {
            if (t.getType().isIncome()) {
                balance = balance.add(t.getAmount());
            } else if (t.getType().isExpense()) {
                balance = balance.subtract(t.getAmount());
            }
        }
        return balance;
    }

    public void addTransaction(Transaction transaction) {
        Objects.requireNonNull(transaction, "Транзакция не может быть null");
        transactions.add(transaction);
        categories.add(transaction.getCategory());
    }

    public void addCategory(String category) {
        if (category == null || category.trim().isEmpty()) {
            throw new IllegalArgumentException("Название категории не может быть пустым");
        }
        categories.add(category.trim());
    }

    public boolean hasCategory(String category) {
        if (category == null) return false;
        String trimmed = category.trim();
        return categories.stream().anyMatch(c -> c.equalsIgnoreCase(trimmed));
    }

    public void renameCategory(String oldName, String newName) {
        if (oldName == null || newName == null || newName.trim().isEmpty()) {
            throw new IllegalArgumentException("Категория не может быть пустой");
        }
        String oldTrimmed = oldName.trim();
        String newTrimmed = newName.trim();

        // Находим и удаляем старую категорию
        String actualOldName = categories.stream()
                .filter(c -> c.equalsIgnoreCase(oldTrimmed))
                .findFirst()
                .orElse(oldTrimmed);
        categories.remove(actualOldName);
        categories.add(newTrimmed);

        // Обновляем транзакции
        for (Transaction t : transactions) {
            if (t.getCategory().equalsIgnoreCase(oldTrimmed)) {
                t.setCategory(newTrimmed);
            }
        }

        // Обновляем бюджет
        Budget existingBudget = budgets.remove(oldTrimmed.toLowerCase());
        if (existingBudget != null) {
            existingBudget.setCategory(newTrimmed);
            budgets.put(newTrimmed.toLowerCase(), existingBudget);
        }
    }

    public boolean deleteCategory(String category) {
        if (category == null) return false;
        String trimmed = category.trim();
        String actualName = categories.stream()
                .filter(c -> c.equalsIgnoreCase(trimmed))
                .findFirst()
                .orElse(null);
        if (actualName == null) return false;
        categories.remove(actualName);
        budgets.remove(trimmed.toLowerCase());
        return true;
    }

    public void setBudget(String category, BigDecimal limitAmount) {
        if (category == null || category.trim().isEmpty()) {
            throw new IllegalArgumentException("Категория не может быть пустой");
        }
        String trimmed = category.trim();
        categories.add(trimmed);
        budgets.put(trimmed.toLowerCase(), new Budget(trimmed, limitAmount));
    }

    public Optional<Budget> getBudget(String category) {
        if (category == null) return Optional.empty();
        return Optional.ofNullable(budgets.get(category.trim().toLowerCase()));
    }

    public boolean removeBudget(String category) {
        if (category == null) return false;
        return budgets.remove(category.trim().toLowerCase()) != null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Wallet wallet)) return false;
        return Objects.equals(id, wallet.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Wallet{" +
                "id='" + id + '\'' +
                ", userId='" + userId + '\'' +
                ", balance=" + getBalance() +
                ", transactionsCount=" + transactions.size() +
                ", budgetsCount=" + budgets.size() +
                '}';
    }
}
