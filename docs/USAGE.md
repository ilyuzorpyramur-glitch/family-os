# USAGE — эксплуатация

## 1. Переменные окружения

| Переменная | Обязательна | Назначение |
|---|---|---|
| `ADMIN_TOKEN` | да | вход в панель и API |
| `FAMILYOS_SECRET` | да | шифрование токена Zen Money |
| `AGENT_TOKEN` | нет | отдельный токен для OpenClaw |
| `ZENMONEY_TOKEN` | нет | начальный токен Zen Money (можно задать в UI) |
| `BASE_CURRENCY` | нет | валюта по умолчанию, `PLN` |
| `PUBLIC_URL` | нет | внешний адрес для ссылок и secure-cookie |
| `PORT` | нет | `3001` |
| `FAMILYOS_DATA_DIR` | нет | `./data`, внутри SQLite и `admin-token.txt` |

Если `ADMIN_TOKEN` не задан, сервер создаст `data/admin-token.txt` при первом запуске.

## 2. Запуск

```bash
docker compose up -d --build
docker compose logs -f family-os
```

Без Docker:

```bash
npm install
npm run build
ADMIN_TOKEN=... FAMILYOS_SECRET=... node apps/api/dist/server.js
```

## 3. Zen Money

1. Откройте [zerro.app/token](https://zerro.app/token) и авторизуйтесь в Дзен-мани.
2. Скопируйте токен.
3. В Family OS: **Настройки → Zen Money → Сохранить**.
4. Нажмите **Синхронизировать** на главной.

Токен даёт доступ ко всем данным Дзен-мани — храните его как пароль. Отзыв: удалить
токен в настройках Family OS (доступ к Zen Money это не отзывает; отзывайте на стороне Zen Money).

Синхронизация инкрементальная: сервер хранит `serverTimestamp` и запрашивает только изменения.
Первая синхронизация может занять несколько секунд.

## 4. OpenClaw

MCP-эндпоинт: `POST /mcp`, JSON-RPC 2.0, Streamable HTTP.

Пример проверки:

```bash
curl -s http://localhost:3001/mcp \
  -H "Authorization: Bearer $AGENT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"money_summary","arguments":{}}}'
```

Конфиг OpenClaw — в `README.md`. Если `AGENT_TOKEN` не задан, работает `ADMIN_TOKEN`
(не рекомендуется: агент получит полный доступ).

## 5. Бэкап и восстановление

```bash
docker compose stop family-os
docker run --rm -v family-os_familyos-data:/data -v "$PWD":/backup alpine \
  tar czf /backup/familyos-$(date +%F).tgz -C /data .
docker compose start family-os
```

Восстановление: распаковать в том при остановленном контейнере.

## 6. Обновление

```bash
git pull
docker compose up -d --build
```

Миграции схемы выполняются при старте (`CREATE TABLE IF NOT EXISTS`). Для будущих
изменений схемы — отдельные SQL-миграции в `apps/api/src/db.ts`.

## 7. Типовые проблемы

| Симптом | Причина | Решение |
|---|---|---|
| 401 при входе | неверный `ADMIN_TOKEN` | посмотрите `data/admin-token.txt` или `.env` |
| «Zen Money отклонил токен» | токен истёк/отозван | получите новый на zerro.app/token |
| Пустые счета после синхронизации | не было первой синхронизации | нажмите «Синхронизировать» ещё раз |
| Суммы в другой валюте | счёт в валюте, отличной от базовой | это нормально; конвертация появится позже |
