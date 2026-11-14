package ru.promo.finance.domain.model;

/**
 * Типы оповещений.
 */
public enum AlertType {
    BUDGET_EXCEEDED("Превышение бюджета"),
    BUDGET_WARNING_80("Предупреждение о приближении к лимиту (>=80%)"),
    EXPENSES_EXCEED_INCOME("Расходы превысили доходы"),
    NEGATIVE_BALANCE("Отрицательный баланс"),
    ZERO_BALANCE("Нулевой баланс");

    private final String description;

    AlertType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
