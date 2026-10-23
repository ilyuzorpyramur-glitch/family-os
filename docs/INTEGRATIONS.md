# INTEGRATIONS

Внешние системы, зачем они нужны и чем ограничены. Все секреты — только в ENV/Vault,
никогда в репозитории.

## Банки

### GoCardless Bank Account Data (ex-Nordigen) — основной путь для EU/UK

- Покрытие: тысячи банков EEA/UK, PSD2.
- Модель: приложение → `institution_id` → redirect → `requisition` → `account_id` → транзакции.
- Ограничения: бесплатный tier с лимитами на количество подключений и запросов; согласие
  требует периодического продления (сейчас ~90 дней, проверять документацию); не все банки
  отдают остатки в реальном времени.
- Реализация: `apps/api/src/modules/money/providers/gocardless.ts`
  - `createRequisition()`, `exchangeCode()`, `listAccounts()`, `fetchTransactions(from,to)`.
  - Маппинг: `transactionAmount.amount` → minor units, `bookingDate` → `occurredAt`,
    `remittanceInformationUnstructured` → `description`.
  - Дедупликация: `externalId = transactionId` (GoCardless стабилен).

### Monobank — Украина

- Персональный токен из приложения, `GET /personal/client-info`, `GET /personal/statement/{account}/{from}/{to}`.
- Ограничение: не чаще 1 запроса в 60 секунд на statement; история 31 день за запрос.
- Реализация: инкрементальная синхронизация по `time` последней операции.

### ПриватБанк / другие

- Официальные API по договору; до этого — импорт выписок CSV/OFX.

### Zen Money

- Публичного API для записи нет. Работаем через **экспорт CSV** (и, при необходимости,
  пользовательский импорт JSON), без опоры на неофициальные эндпоинты.
- Маппинг колонок настраивается: дата, счёт, категория, сумма, валюта, контрагент, заметка.
- Импорт идемпотентен: повторная загрузка файла не создаёт дублей (хэш строки).

## Продукты и штрихкоды

### Open Food Facts

- `GET https://world.openfoodfacts.org/api/v2/product/{barcode}.json` — бесплатно.
- Требуется осмысленный `User-Agent` с контактом; уважать rate limit, кэшировать локально.
- Даёт `nutriments` (kcal, protein, fat, carbohydrates на 100 г), `product_name`, `brands`, `image_url`.
- Качество данных неровное: помечаем источник и позволяем правку; агрегируем «доверие» по числу правок.

### USDA FoodData Central

- Бесплатный API key, хорошее покрытие базовых продуктов; используем как fallback и для
  продуктов без штрихкода (поиск по названию).

### Локальные продукты

- Если штрихкод не найден — создаём `Product` с `source=LOCAL`, предлагаем сфотографировать
  этикетку; OCR — позже (M6).

## Telegram

- Bot API через grammY, webhook (не long polling в проде).
- Команды: `/start`, `/today`, `/spent`, `/budget`, `/quests`, `/approve <id>`, `/mute`.
- Привязка аккаунта: одноразовый код из веб-панели → `/start <code>`.
- Группа семьи — только уведомления; личные финансы — только в личку.
- Голосовые сообщения — транскрипция и разбор траты (M6).

## OpenClaw

- MCP-сервер в `packages/openclaw-plugin`, транспорт stdio (локально) и HTTP (удалённо).
- Инструменты (v1):

| Инструмент | Scope | Назначение |
|---|---|---|
| `family_overview` | `family:read` | состав, роли, активные устройства |
| `money_summary` | `money:read` | остатки, доходы/расходы за период |
| `money_search` | `money:read` | поиск транзакций по фильтрам |
| `money_add_draft` | `money:write` | черновик транзакции |
| `budget_status` | `money:read` | исполнение бюджетов |
| `pantry_status` | `pantry:read` | остатки, просрочка, список покупок |
| `pantry_add` | `pantry:write` | добавить продукт/партию |
| `nutrition_day` | `nutrition:read` | калории и БЖУ за день |
| `quest_list` | `quest:read` | задания и статусы |
| `quest_create` | `quest:write` | создать задание |
| `guard_status` | `guard:read` | лимиты и события ребёнка |
| `guard_set_limit` | `guard:write` + confirm | изменить лимит |
| `notify_send` | `notify:write` | отправить уведомление члену семьи |

- Скиллы OpenClaw: «семейный дайджест», «разбор трат», «план питания на неделю»,
  «проверка запасов», «конфликт экранного времени».
- Ответственность: агент не ходит в БД; всё через API с токеном и scope.

## Push и уведомления

- FCM для Android (позже — APNs). Fallback — Telegram, если push не доставлен.
- Тихие часы: 22:00–07:00, кроме SOS и критичных событий guard.

## Карты и геозоны

- OpenStreetMap + Nominatim для адресов (уважать usage policy: 1 req/s, кэш).
- Геозоны — локально на устройстве (Geofencing API), сервер только хранит полигоны.

## Платежи за подписку (позже)

- Stripe (EU) или Paddle как merchant of record. До этого — self-hosted бесплатно.

## Таблица статусов

| Интеграция | Статус | Блокер |
|---|---|---|
| Open Food Facts | планируется M3 | — |
| GoCardless | планируется M2 | нужен аккаунт и redirect URI |
| Monobank | опционально M2 | токен пользователя |
| Zen Money CSV | планируется M1 | — |
| Telegram | планируется M2 | токен бота |
| OpenClaw MCP | планируется M2 | — |
| FCM | планируется M4 | проект Firebase |
