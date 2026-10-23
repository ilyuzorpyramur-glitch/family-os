# packages/openclaw-plugin

MCP-сервер для OpenClaw + скиллы.

## Состав

```
src/
  server.ts        MCP bootstrap (stdio/http)
  tools/           по одному файлу на инструмент
  client.ts        типизированный клиент Family OS API
  auth.ts          токен, scope, confirm-flow
skills/
  family-digest/SKILL.md
  money-review/SKILL.md
  pantry-check/SKILL.md
  guard-conflict/SKILL.md
```

## Принципы

- Только типизированные инструменты, без SQL.
- Каждый вызов — в `agent_audit`.
- Запись денег и правил — через черновик + `confirm_token`.
- Ответы компактные: агент не должен тонуть в 500 транзакциях.

## Запуск (план)

```bash
pnpm --filter @familyos/openclaw-plugin build
node dist/server.js   # stdio, подключается в конфиге OpenClaw
```

## Статус

M2. До этого — спецификация инструментов в `docs/INTEGRATIONS.md`.
