# apps/api

Backend Family OS: Fastify + Prisma + PostgreSQL.

## Планируемая структура

```
src/
  modules/
    identity/   семьи, пользователи, роли, приглашения
    guard/      политики, события, геозоны
    money/      счета, транзакции, бюджеты, провайдеры банков
    pantry/     товары, партии, движения, списки
    nutrition/  приёмы пищи, рецепты, нормы
    quest/      задания, XP, достижения
    copilot/    MCP-шлюз, автоматизации, аудит
    notify/     уведомления и каналы
  plugins/      auth, rbac, prisma, errors, rate-limit
  jobs/         bank-sync, expiry-check, digest, recalc-balances
  server.ts
prisma/
  schema.prisma
  migrations/
```

## Контракты

- REST `/v1/...`, OpenAPI генерируется из zod-схем.
- Ошибки: `{ error: { code, message, details? } }`.
- Пагинация: cursor (`?after=<id>&limit=50`).
- Все ответы содержат `requestId` в заголовке.

## Статус

M0: каркас не создан. Первый шаг — health-check, Prisma-схема identity + money,
CI на lint/typecheck/test.
