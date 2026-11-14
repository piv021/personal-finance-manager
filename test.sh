#!/usr/bin/env bash
set -e

# Определение базовой директории скрипта
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "=== Запуск тестов Personal Finance Manager ==="

# Пути к зависимостям JUnit 5
M2_REPO="${HOME}/.m2/repository"
JUNIT_JARS="${M2_REPO}/org/junit/jupiter/junit-jupiter-api/5.11.4/junit-jupiter-api-5.11.4.jar:\
${M2_REPO}/org/junit/jupiter/junit-jupiter-engine/5.11.4/junit-jupiter-engine-5.11.4.jar:\
${M2_REPO}/org/junit/platform/junit-platform-commons/1.11.4/junit-platform-commons-1.11.4.jar:\
${M2_REPO}/org/junit/platform/junit-platform-engine/1.11.4/junit-platform-engine-1.11.4.jar:\
${M2_REPO}/org/opentest4j/opentest4j/1.3.0/opentest4j-1.3.0.jar:\
${M2_REPO}/org/apiguardian/apiguardian-api/1.1.2/apiguardian-api-1.1.2.jar"

# Компиляция основных классов
mkdir -p target/classes target/test-classes
find src/main/java -name "*.java" > /tmp/pfm_main_src.txt
javac -d target/classes @/tmp/pfm_main_src.txt
rm -f /tmp/pfm_main_src.txt

# Компиляция тестовых классов
find src/test/java -name "*.java" > /tmp/pfm_test_src.txt
javac -cp "target/classes:${JUNIT_JARS}" -d target/test-classes @/tmp/pfm_test_src.txt
rm -f /tmp/pfm_test_src.txt

# Запуск тестов через TestRunner
java -cp "target/classes:target/test-classes:${JUNIT_JARS}" ru.promo.finance.TestRunner
