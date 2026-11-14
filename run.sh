#!/usr/bin/env bash
set -e

# Определение базовой директории скрипта
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "=== Запуск Personal Finance Manager (PROMO IT) ==="

# Сборка если jar отсутствует
if [ ! -f "target/personal-finance-manager-1.0.0.jar" ]; then
    echo "Сборка проекта..."
    mkdir -p target/classes
    find src/main/java -name "*.java" > /tmp/pfm_sources.txt
    javac -d target/classes @/tmp/pfm_sources.txt
    jar cfe target/personal-finance-manager-1.0.0.jar ru.promo.finance.Main -C target/classes .
    rm -f /tmp/pfm_sources.txt
fi

# Запуск приложения
java -jar target/personal-finance-manager-1.0.0.jar "$@"
