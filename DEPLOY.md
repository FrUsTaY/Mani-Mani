# Руководство по развёртыванию Mani-Mani Sync-Lab на Ubuntu Server 24.04 LTS

Настоящее руководство содержит исчерпывающие пошаговые инструкции по установке, конфигурированию, эксплуатации и безопасному обновлению сервиса **Mani-Mani Sync-Lab** на удалённом сервере под управлением **Ubuntu 24.04 LTS**.

---

## Содержание

1. [Архитектура и безопасность](#1-архитектура-и-безопасность)
2. [Системные требования](#2-системные-требования)
3. [Установка Docker и Docker Compose на Ubuntu 24.04](#3-установка-docker-и-docker-compose-на-ubuntu-2404)
4. [Подготовка директорий и прав доступа](#4-подготовка-директорий-и-прав-доступа)
5. [Конфигурация окружения (.env)](#5-конфигурация-окружения-env)
6. [Сборка и запуск сервиса](#6-сборка-и-запуск-сервиса)
7. [Управление пользователями и API-ключами](#7-управление-пользователями-и-api-ключами)
8. [Проверка работоспособности (Health check и API)](#8-проверка-работоспособности-health-check-и-api)
9. [Резервное копирование и восстановление SQLite](#9-резервное-копирование-и-восстановление-sqlite)
10. [Процедура обновления сервиса без потери данных](#10-процедура-обновления-сервиса-без-потери-данных)
11. [Рекомендации по сетевой безопасности и SSL (Nginx / Certbot)](#11-рекомендации-по-сетевой-безопасности-и-ssl-nginx--certbot)

---

## 1. Архитектура и безопасность

- **Стек**: Python 3.11, FastAPI, Uvicorn, SQLite3 (режим WAL), Docker Compose.
- **Изоляция привилегий**: Контейнер работает от имени непривилегированного пользователя `appuser` (UID `10001`, GID `10001`). Запуск от `root` запрещён.
- **Хранение данных**: База данных SQLite монтируется через Docker Volume (`/data`), данные хранятся на хост-системе вне контейнера.
- **Мультитенантность**: Полная изоляция операций и счетов по `user_id`.
- **Аутентификация**: Авторизация клиентов (Android-приложения) осуществляется по криптографическим токенам (`sk_live_...`). В базе данных хранятся исключительно SHA-256 хэши ключей. В открытом виде ключ показывается только один раз при генерации.

---

## 2. Системные требования

- **ОС**: Ubuntu Server 24.04 LTS (Noble Numbat) x86_64 или aarch64.
- **CPU**: минимум 1 vCPU (рекомендуется 2 vCPU).
- **RAM**: минимум 1 ГБ (рекомендуется 2 ГБ для стабильной работы и фоновых задач).
- **Диск**: 10 ГБ свободного пространства (SSD/NVMe).
- **Сетевые порты**: 8000 TCP (или 80/443 при использовании обратного прокси Nginx).

---

## 3. Установка Docker и Docker Compose на Ubuntu 24.04

Если Docker ещё не установлен на сервере, выполните официальную установку Docker Engine:

```bash
# Обновление индекса пакетов и установка зависимостей
sudo apt update && sudo apt install -y ca-certificates curl gnupg lsb-release

# Добавление официального GPG-ключа Docker
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg

# Подключение официального репозитория Docker
echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
  $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | \
  sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

# Установка Docker Engine, CLI, containerd и Docker Compose v2
sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

# Проверка работоспособности
docker --version
docker compose version
```

---

## 4. Подготовка директорий и прав доступа

Рекомендуемое место размещения сервиса: `/opt/sync-lab` или домашний каталог администратора:

```bash
# Создание рабочей директории сервиса
sudo mkdir -p /opt/sync-lab
cd /opt/sync-lab

# Клонирование или копирование файлов проекта (ветка feature/sync-lab-server)
# Файлы: Dockerfile, docker-compose.yml, .env.example, requirements.txt, pyproject.toml, src/
```

### Настройка прав доступа к директории базы данных

Контейнер запускается от пользователя с UID `10001` (`appuser`). Хостовая директория `./data` должна иметь владельца с этим UID:

```bash
# Создание директории данных
sudo mkdir -p /opt/sync-lab/data

# Назначение владельца для UID 10001:10001
sudo chown -R 10001:10001 /opt/sync-lab/data
sudo chmod 750 /opt/sync-lab/data
```

---

## 5. Конфигурация окружения (.env)

Скопируйте шаблон конфигурации и настройте параметры:

```bash
cp .env.example .env
chmod 600 .env
```

Содержимое `.env`:
```ini
# Внешний порт на хосте
SYNC_LAB_PORT=8000

# Хостовая директория для хранения базы данных SQLite
SYNC_LAB_HOST_DATA_DIR=./data

# Уровень логирования (INFO, DEBUG, WARNING, ERROR)
SYNC_LAB_LOG_LEVEL=INFO

# Разрешённые CORS-источники (* для мобильных приложений)
SYNC_LAB_ALLOWED_ORIGINS=*

# Режим headless для браузерного сборщика
SYNC_LAB_HEADLESS=true
```

---

## 6. Сборка и запуск сервиса

Запустите сервис в фоновом режиме:

```bash
docker compose up -d --build
```

### Проверка состояния:

```bash
# Просмотр статуса контейнера
docker compose ps

# Просмотр логов в реальном времени
docker compose logs -f sync-lab
```

Успешный вывод логов:
```text
INFO: Started server process [...]
INFO: Waiting for application startup.
INFO: Initializing SQLite database at /data/sync_lab.db
INFO: Application startup complete.
INFO: Uvicorn running on http://0.0.0.0:8000 (Press CTRL+C to quit)
```

---

## 7. Управление пользователями и API-ключами

Все административные команды выполняются через CLI внутри запущенного контейнера с сохранением в примонтированную базу.

### Шаг 7.1. Создание пользователя

```bash
docker compose exec sync-lab python -m sync_lab.cli user create --username "android_client"
```

Пример вывода:
```text
User created successfully:
  ID:       usr_5a278696ad0ec754
  Username: android_client
  Active:   True
```

### Шаг 7.2. Генерация API-ключа

Сгенерируйте токен для приложения, указав `ID` пользователя:

```bash
docker compose exec sync-lab python -m sync_lab.cli api-key create \
  --user-id "usr_5a278696ad0ec754" \
  --name "Samsung S24 Ultra"
```

Пример вывода:
```text
API Key generated successfully!
Store this key securely. It cannot be recovered later!

  Raw Secret Key:  sk_live_qT9VbX90KjA2_... (скопируйте этот токен!)
  Prefix:          sk_live_qT9V...
  User ID:         usr_5a278696ad0ec754
  Name:            Samsung S24 Ultra
```

> **Важно**: Сохраните `Raw Secret Key`. В базе данных сохраняется только SHA-256 хэш, восстановить исходный ключ невозможно.

### Шаг 7.3. Просмотр списка активных ключей

```bash
docker compose exec sync-lab python -m sync_lab.cli api-key list --user-id "usr_5a278696ad0ec754"
```

### Шаг 7.4. Отзыв ключа в случае утери телефона

```bash
docker compose exec sync-lab python -m sync_lab.cli api-key revoke --prefix "sk_live_qT9V..."
```

---

## 8. Проверка работоспособности (Health check и API)

### 8.1. Публичный Health check

```bash
curl -i http://localhost:8000/health
```

Ожидаемый ответ:
```json
HTTP/1.1 200 OK
content-type: application/json

{"status":"ok","database":"connected","uptime_seconds":15.42}
```

### 8.2. Проверка неавторизованного доступа (ожидается 401 Unauthorized)

```bash
curl -i http://localhost:8000/api/v1/accounts
```

Ответ:
```json
HTTP/1.1 401 Unauthorized
{"detail":"Missing API key. Provide 'X-API-Key' or 'Authorization: Bearer <key>'"}
```

### 8.3. Запрос счетов с авторизацией

```bash
curl -s -H "X-API-Key: sk_live_ВАШ_КЛЮЧ" http://localhost:8000/api/v1/accounts | jq .
```

### 8.4. Запрос транзакций с пагинацией

```bash
curl -s -H "X-API-Key: sk_live_ВАШ_КЛЮЧ" \
  "http://localhost:8000/api/v1/transactions?limit=20&offset=0" | jq .
```

### 8.5. Инкрементальная синхронизация (получение операций после timestamp)

```bash
curl -s -H "X-API-Key: sk_live_ВАШ_КЛЮЧ" \
  "http://localhost:8000/api/v1/transactions?since_timestamp=1772600000000" | jq .
```

---

## 9. Резервное копирование и восстановление SQLite

База данных SQLite использует режим WAL (`Write-Ahead Logging`), что позволяет безопасно создавать полные горячие копии без остановки сервиса.

### 9.1. Создание горячей резервной копии (Online Backup)

```bash
# Создание директории для бэкапов
sudo mkdir -p /opt/sync-lab/backups

# Создание консистентного snapshot базы данных с временной меткой
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
docker compose exec sync-lab python -c "
import sqlite3
src = sqlite3.connect('/data/sync_lab.db')
dst = sqlite3.connect('/data/sync_lab_backup_${TIMESTAMP}.db')
with dst:
    src.backup(dst)
dst.close()
src.close()
"

# Перемещение снапшота в каталог бэкапов хоста
sudo mv /opt/sync-lab/data/sync_lab_backup_${TIMESTAMP}.db /opt/sync-lab/backups/
sudo gzip /opt/sync-lab/backups/sync_lab_backup_${TIMESTAMP}.db

echo "Бэкап успешно создан: /opt/sync-lab/backups/sync_lab_backup_${TIMESTAMP}.db.gz"
```

### 9.2. Настройка автоматического резервного копирования в Cron

Добавьте задачу в планировщик `crontab -e`:

```bash
# Ежедневно в 03:00 ночи
0 3 * * * /bin/bash -c 'docker compose -f /opt/sync-lab/docker-compose.yml exec -T sync-lab python -c "import sqlite3; s=sqlite3.connect(\"/data/sync_lab.db\"); d=sqlite3.connect(\"/data/backup_daily.db\"); s.backup(d); d.close(); s.close()"'
```

### 9.3. Восстановление из резервной копии

В случае сбоя или необходимости восстановления данных:

```bash
# 1. Останавливаем сервис
docker compose stop sync-lab

# 2. Архивируем текущую повреждённую базу (на всякий случай)
sudo mv /opt/sync-lab/data/sync_lab.db /opt/sync-lab/data/sync_lab_corrupted_$(date +%s).db
sudo rm -f /opt/sync-lab/data/sync_lab.db-wal /opt/sync-lab/data/sync_lab.db-shm

# 3. Распаковываем и восстанавливаем целевой бэкап
sudo gunzip -c /opt/sync-lab/backups/sync_lab_backup_20261102_120000.db.gz > /opt/sync-lab/data/sync_lab.db

# 4. Проверяем владельца файла (UID 10001)
sudo chown 10001:10001 /opt/sync-lab/data/sync_lab.db
sudo chmod 640 /opt/sync-lab/data/sync_lab.db

# 5. Запускаем сервис
docker compose start sync-lab

# 6. Проверяем логи и здоровье
docker compose logs --tail=20 sync-lab
curl -s http://localhost:8000/health
```

---

## 10. Процедура обновления сервиса без потери данных

При получении новых коммитов или переключении версий сервиса:

```bash
cd /opt/sync-lab

# 1. Создаём резервную копию базы перед обновлением (обязательно!)
docker compose exec sync-lab python -c "
import sqlite3
s = sqlite3.connect('/data/sync_lab.db')
d = sqlite3.connect('/data/pre_update_backup.db')
s.backup(d); d.close(); s.close()
"

# 2. Получаем обновлённый код из Git
git pull origin feature/sync-lab-server

# 3. Пересобираем контейнер с новым кодом
docker compose build --no-cache sync-lab

# 4. Перезапускаем сервис с минимальным временем простоя
docker compose up -d --no-deps sync-lab

# 5. Проверяем статус контейнера и логи
docker compose ps
docker compose logs --tail=30 sync-lab

# 6. Проверяем health check
curl -f http://localhost:8000/health
```

Все таблицы SQLite автоматически обновляются внутренними схемами миграций `_init_db()` и `_migrate_schema()` без удаления существующих транзакций и пользователей.

---

## 11. Рекомендации по сетевой безопасности и SSL (Nginx / Certbot)

Для взаимодействия мобильного приложения с сервером через Интернет обязательно настройте обратный прокси с HTTPS:

```nginx
# /etc/nginx/sites-available/sync-lab.conf
server {
    server_name sync.your-domain.ru;

    location / {
        proxy_pass http://127.0.0.1:8000;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        # Таймауты для стабильного удержания соединений
        proxy_connect_timeout 60s;
        proxy_read_timeout 60s;
    }
}
```

Выпуск бесплатного сертификата Let's Encrypt:
```bash
sudo certbot --nginx -d sync.your-domain.ru
```
