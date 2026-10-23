# Family OS

Семейный центр в одном приложении: родительский контроль, деньги, домашние запасы,
питание и геймификация — с агентом OpenClaw, который всё это связывает,
автоматизирует и объясняет.

> **Статус: M1 (деньги) + Guard (сервер и Android).** Работает учёт денег из Zen Money,
> веб-панель, MCP для OpenClaw, серверный родительский контроль и Android-клиент Guard
> (APK собирается в GitHub Actions). Модули Pantry/Nutrition/Quest — спроектированы.

## Что уже работает

- **Zen Money** — синхронизация счетов по валютам, категорий, доходов/расходов и переводов
  через официальный API (`api.zenmoney.ru/v8/diff/`) по токену с [zerro.app/token](https://zerro.app/token).
- **Двойная запись** — каждая операция раскладывается на проводки, сумма которых равна нулю.
- **Веб-панель** — балансы, итоги месяца, счета и последние операции. Тёмная тема, мобильная вёрстка.
- **Guard (Android)** — APK в артефактах GitHub Actions: лимиты, bedtime, блокировка через
  Accessibility, запрос «+15 минут», отправка статистики каждые 15 минут. Инструкция —
  [apps/android/README.md](apps/android/README.md).
- **MCP для OpenClaw** — `accounts_list`, `money_summary`, `transactions_list`,
  `categories_list`, `add_transaction`, `sync_now`, `guard_overview`, `guard_events`,
  `guard_set_limit`, `guard_approve_request`. Все вызовы аудируются.
- **Self-hosted** — один контейнер, SQLite в томе, без внешних зависимостей кроме Zen Money.

## Быстрый старт (Docker)

```bash
cp .env.example .env
# впишите ADMIN_TOKEN и FAMILYOS_SECRET:
#   openssl rand -base64 24   → ADMIN_TOKEN
#   openssl rand -hex 32      → FAMILYOS_SECRET
docker compose up -d --build
```

Откройте `http://localhost:3001`, войдите с `ADMIN_TOKEN`, затем
**Настройки → Zen Money** → вставьте токен с [zerro.app/token](https://zerro.app/token) →
**Синхронизировать**.

## Локальная разработка

```bash
npm install
ADMIN_TOKEN=dev-token FAMILYOS_DATA_DIR=./data npm run dev
# http://localhost:3001
```

Тесты: `npm test` (7 тестов: API, MCP, импорт, двойная запись, сводка, Guard, Guard MCP).

## Подключение OpenClaw

```json
{
  "mcpServers": {
    "family-os": {
      "url": "https://your-host/mcp",
      "headers": { "Authorization": "Bearer $AGENT_TOKEN" }
    }
  }
}
```

Инструменты возвращают суммы в валюте операции и `asOf`. Записывающие действия
(`add_transaction`) помечаются `source=agent` и попадают в `agent_calls`.

## Архитектура

```
Браузер / Android ── HTTP ──┐
OpenClaw ── MCP /mcp ───────┼── Fastify (Node 24) ── SQLite (WAL)
Telegram (план) ────────────┘         │
                                      ├── Zen Money API (v8/diff)
                                      └── AES-256-GCM для токенов
```

Подробно: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md),
[docs/DATA-MODEL.md](docs/DATA-MODEL.md),
[docs/DECISIONS/0004-sqlite-first.md](docs/DECISIONS/0004-sqlite-first.md).

## Структура

```
apps/api/src
  routes/       REST /api и веб-страницы
  mcp/          MCP-сервер (JSON-RPC, Streamable HTTP)
  zenmoney/     клиент и импорт diff
  repo/         счета, категории, операции, настройки
  web/          серверный HTML без сборки
  test/         node:test
docs/           продукт, архитектура, модель данных, ADR, модули
```

## Модули

| Модуль | Статус |
|---|---|
| Money (Zen Money, двойная запись, бюджеты) | работает: счета, операции, сводки; бюджеты — далее |
| Guard (родительский контроль) | сервер и Android-клиент; фильтр веба и геозоны — далее |
| Pantry (запасы, Grocy-подобно) | дизайн: [docs/modules/PANTRY.md](docs/modules/PANTRY.md) |
| Nutrition (калории, штрихкоды) | дизайн: [docs/modules/NUTRITION.md](docs/modules/NUTRITION.md) |
| Quest (XP, задания, награды) | дизайн: [docs/modules/QUEST.md](docs/modules/QUEST.md) |
| Copilot (OpenClaw, MCP, Telegram) | MCP работает; Telegram — далее |

## Безопасность

- Один администратор на инстанс (мультироли — в M1.5). Токены — только в заголовке или HttpOnly-cookie.
- Токен Zen Money шифруется AES-256-GCM ключом `FAMILYOS_SECRET`.
- Секреты не попадают в логи; MCP-вызовы аудируются в `agent_calls`.
- Подробности: [docs/SECURITY-PRIVACY.md](docs/SECURITY-PRIVACY.md).

## Лицензия

MIT — см. [LICENSE](LICENSE).
