# Mani-Mani Sync Lab

Экспериментальный модуль в корне репозитория Mani-Mani для исследования прямой банковской синхронизации с личными кабинетами банков (на первом этапе — **ВТБ Онлайн**) без использования сторонних платных API, SMS и агрегаторов.

---

## 1. Архитектура и принципы переиспользования

Архитектура Sync Lab спроектирована с учетом последующего переноса логики в Android-приложение на Kotlin:

| Компонент | Назначение в Sync Lab | Статус переносимости в Android / Kotlin |
|-----------|------------------------|-----------------------------------------|
| **`core/models.py`** | Модели счетов, транзакций, статусов, сессий | **1:1 перенос**. Напрямую соответствует сущностям Room: `AccountEntity`, `TransactionEntity`, `PendingNotificationEntity`. |
| **`core/normalizer.py`** | Очистка сумм, определение типов (EXPENSE / INCOME / TRANSFER), парсинг дат | **Адаптация для Kotlin**. Чистая бизнес-логика без зависимости от платформы (аналог `BankNotificationParser.kt`). |
| **`core/interfaces.py`** | Контракты `BaseBankAdapter`, `BaseTransactionRepository` | **1:1 перенос**. В Kotlin объявляются интерфейсы `BankAdapter` и репозитории. |
| **`storage/sqlite_repo.py`** | Хранение счетов, сырых данных и нормализованных операций, дедупликация, JSON-экспорт | **Адаптация под Room**. Схема SQLite идентична структуре сущностей Mani-Mani. |
| **`adapters/vtb/network_interceptor.py`** | Пассивный перехват ответов API банка без вмешательства в запросы | **Адаптация под Android WebView**. На Android реализуется через `WebViewClient.shouldInterceptRequest` или JS interface. |
| **`adapters/vtb/auth_manager.py`** | Запуск Chromium с постоянным профилем, обнаружение логина без сохранения паролей | **Исследовательский инструмент**. На Android сессией управляет нативный `WebView` с `CookieManager`. |
| **`adapters/vtb/history_collector.py`** | Анализ перехваченных JSON-ответов API и извлечение из DOM | **Адаптация**. Логика разбора JSON структур переносится на Moshi/Kotlinx Serialization. |

---

## 2. Безопасность

- **Никаких сохраненных паролей, PIN-кодов и SMS-кодов**: пользователь авторизуется в браузере самостоятельно.
- **Никакого обхода CAPTCHA или MFA**: исследуются только штатные пользовательские сценарии.
- **Пассивный сбор**: запросы не модифицируются, используются только данные штатных ответов личного кабинета.
- **Изоляция данных**: директории с профилем браузера (`user_data/`), базой данных (`data/`), логами и экспортом (`exports/`) исключены из Git через `.gitignore`.

---

## 3. Установка и запуск

### 3.1. Виртуальное окружение
Окружение уже настроено в `sync-lab/.venv` (Python 3.11.5 + Playwright Chromium).

Для ручной активации в PowerShell:
```powershell
.\sync-lab\.venv\Scripts\Activate.ps1
```

### 3.2. Запуск тестов
```powershell
.\sync-lab\.venv\Scripts\pytest sync-lab\tests
```

### 3.3. Команды CLI

1. **Проверка авторизации в ВТБ Онлайн (Этап B)**:
   Открывает браузер с официальной страницей входа ВТБ Онлайн. Проверяет доступность страницы и сохранение профиля:
   ```powershell
   .\sync-lab\.venv\Scripts\python -m sync_lab.cli test-auth --keep-open
   ```

2. **Полный цикл синхронизации (Этапы B, C, D)**:
   Ожидает авторизации пользователя, пассивно перехватывает запросы истории операций, нормализует их, сохраняет в локальный SQLite и создает экспорт:
   ```powershell
   .\sync-lab\.venv\Scripts\python -m sync_lab.cli sync
   ```

3. **Просмотр сохраненных операций в базе данных**:
   ```powershell
   .\sync-lab\.venv\Scripts\python -m sync_lab.cli status
   ```

4. **Экспорт в формат JSON для Mani-Mani**:
   ```powershell
   .\sync-lab\.venv\Scripts\python -m sync_lab.cli export-json -o sync-lab/exports/vtb_backup.json
   ```

5. **Запуск HTTP REST API сервера (локально)**:
   ```powershell
   .\sync-lab\.venv\Scripts\python -m sync_lab.cli server --host 0.0.0.0 --port 8000
   ```

6. **Управление пользователями и API-ключами**:
   ```powershell
   # Создать пользователя
   .\sync-lab\.venv\Scripts\python -m sync_lab.cli user create --username "android_client"

   # Создать API-ключ для пользователя
   .\sync-lab\.venv\Scripts\python -m sync_lab.cli api-key create --user-id "<user_id>" --name "My Phone"

   # Список активных ключей
   .\sync-lab\.venv\Scripts\python -m sync_lab.cli api-key list --user-id "<user_id>"
   ```

---

## 4. Развёртывание на сервере (Docker & Ubuntu 24.04)

Сервис полностью подготовлен к развёртыванию в Docker:
- Изолированный контейнер с пользователем `appuser` (non-root UID `10001`).
- Примонтированный volume для SQLite базы (`./data -> /data`).
- Инкрементальная синхронизация с Android по параметру `since_timestamp`.
- Аутентификация мобильных клиентов по SHA-256 API-ключам (`X-API-Key`).
- Полные инструкции по установке, запуску, бэкапам и обновлениям описаны в **[DEPLOY.md](DEPLOY.md)**.
