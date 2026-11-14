package ru.promo.finance.domain.model;

/**
 * Тип финансовой операции.
 */
public enum TransactionType {
    INCOME("Доход"),
    EXPENSE("Расход"),
    TRANSFER_IN("Входящий перевод"),
    TRANSFER_OUT("Исходящий перевод");

    private final String displayName;

    TransactionType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isIncome() {
        return this == INCOME || this == TRANSFER_IN;
    }

    public boolean isExpense() {
        return this == EXPENSE || this == TRANSFER_OUT;
    }
}
