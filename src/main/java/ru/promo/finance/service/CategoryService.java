package ru.promo.finance.service;

import ru.promo.finance.domain.model.Wallet;

import java.util.*;

/**
 * Сервис управления категориями доходов и расходов.
 */
public class CategoryService {

    public static final Set<String> DEFAULT_CATEGORIES = Set.of(
            "Еда",
            "Развлечения",
            "Коммунальные услуги",
            "Такси",
            "Зарплата",
            "Бонус",
            "Перевод"
    );

    public void addCategory(Wallet wallet, String category) {
        if (wallet == null) throw new IllegalArgumentException("Кошелек не может быть null");
        if (category == null || category.trim().isEmpty()) {
            throw new IllegalArgumentException("Название категории не может быть пустым");
        }
        String trimmed = category.trim();
        if (wallet.hasCategory(trimmed)) {
            throw new IllegalArgumentException("Категория '" + trimmed + "' уже существует");
        }
        wallet.addCategory(trimmed);
    }

    public void renameCategory(Wallet wallet, String oldName, String newName) {
        if (wallet == null) throw new IllegalArgumentException("Кошелек не может быть null");
        if (oldName == null || oldName.trim().isEmpty()) {
            throw new IllegalArgumentException("Старое название категории не может быть пустым");
        }
        if (newName == null || newName.trim().isEmpty()) {
            throw new IllegalArgumentException("Новое название категории не может быть пустым");
        }
        if (!wallet.hasCategory(oldName)) {
            throw new NoSuchElementException("Категория '" + oldName + "' не найдена");
        }
        if (wallet.hasCategory(newName) && !oldName.equalsIgnoreCase(newName)) {
            throw new IllegalArgumentException("Категория с названием '" + newName + "' уже существует");
        }
        wallet.renameCategory(oldName, newName);
    }

    public boolean deleteCategory(Wallet wallet, String category) {
        if (wallet == null) return false;
        if (category == null || category.trim().isEmpty()) {
            throw new IllegalArgumentException("Название категории не может быть пустым");
        }
        if (!wallet.hasCategory(category)) {
            throw new NoSuchElementException("Категория '" + category + "' не найдена");
        }
        return wallet.deleteCategory(category);
    }

    public Set<String> getCategories(Wallet wallet) {
        if (wallet == null) return Collections.emptySet();
        return new TreeSet<>(wallet.getCategories());
    }

    public void initDefaultCategories(Wallet wallet) {
        if (wallet == null) return;
        for (String cat : DEFAULT_CATEGORIES) {
            wallet.addCategory(cat);
        }
    }
}
