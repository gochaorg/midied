#!/usr/bin/env bash
BIN_DIR=$(dirname $(readlink -f ${BASH_SOURCE[0]}))

# Проверяем, есть ли BIN_DIR в PATH
# Оборачиваем в двоеточия для точного совпадения целой директории
if [[ ":$PATH:" != *":$BIN_DIR:"* ]]; then
    # Если нет, добавляем в начало (чтобы приоритет был выше)
    export PATH="$BIN_DIR:$PATH"
fi