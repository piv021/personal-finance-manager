package ru.promo.finance;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Автономный тестовый раннер для запуска всех JUnit-тестов.
 */
public class TestRunner {

    public static void main(String[] args) {
        System.out.println("=================================================");
        System.out.println("        ЗАПУСК ТЕСТОВОГО НАБОРА PROMO IT         ");
        System.out.println("=================================================");

        Class<?>[] testClasses = new Class<?>[] {
                UserAndAuthTest.class,
                TransactionAndWalletTest.class,
                CategoryAndBudgetTest.class,
                AlertServiceTest.class,
                StatisticsServiceTest.class,
                TransferServiceTest.class,
                PersistenceAndExportTest.class,
                ConsoleUIIntegrationTest.class
        };

        int total = 0;
        int passed = 0;
        int failed = 0;
        long startTime = System.currentTimeMillis();

        for (Class<?> clazz : testClasses) {
            System.out.println("\n[ТЕСТ-КЛАСС] " + clazz.getSimpleName());

            List<Method> beforeMethods = new ArrayList<>();
            List<Method> testMethods = new ArrayList<>();

            for (Method m : clazz.getDeclaredMethods()) {
                if (m.isAnnotationPresent(BeforeEach.class)) {
                    beforeMethods.add(m);
                }
                if (m.isAnnotationPresent(Test.class)) {
                    testMethods.add(m);
                }
            }

            for (Method testMethod : testMethods) {
                total++;
                try {
                    Object instance = clazz.getDeclaredConstructor().newInstance();
                    for (Method b : beforeMethods) {
                        b.setAccessible(true);
                        b.invoke(instance);
                    }
                    testMethod.setAccessible(true);
                    testMethod.invoke(instance);
                    passed++;
                    System.out.println("  ✓ " + testMethod.getName() + " - УСПЕХ");
                } catch (InvocationTargetException e) {
                    failed++;
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    System.err.println("  ✗ " + testMethod.getName() + " - ОШИБКА: " + cause.getMessage());
                    cause.printStackTrace(System.err);
                } catch (Exception e) {
                    failed++;
                    System.err.println("  ✗ " + testMethod.getName() + " - ИСКЛЮЧЕНИЕ: " + e.getMessage());
                    e.printStackTrace(System.err);
                }
            }
        }

        long elapsed = System.currentTimeMillis() - startTime;
        System.out.println("\n=================================================");
        System.out.println("                 ИТОГИ ТЕСТИРОВАНИЯ              ");
        System.out.println("=================================================");
        System.out.printf("Всего тестов: %d | Успешно: %d | Ошибок: %d | Время: %d мс\n",
                total, passed, failed, elapsed);

        if (failed == 0) {
            System.out.println(">>> ВСЕ ТЕСТЫ ПРОЙДЕНЫ УСПЕШНО (100%)! <<<");
            System.exit(0);
        } else {
            System.err.println(">>> ОБНАРУЖЕНЫ ОШИБКИ В ТЕСТАХ! <<<");
            System.exit(1);
        }
    }
}
