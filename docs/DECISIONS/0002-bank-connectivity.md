# ADR 0002 — банковская связность

**Дата:** 2026-10-23
**Статус:** принято (с пересмотром после M2)

## Контекст

Нужен импорт операций и остатков из банков без ручного ввода. Family OS — не банк,
лицензии на платежи нет. Пользователи — EU (Польша) и, возможно, Украина.

## Варианты

1. **GoCardless Bank Account Data (ex-Nordigen).** PSD2-агрегатор, бесплатный tier,
   широчайшее покрытие EU/UK. Минус: согласие нужно продлевать, задержки у некоторых банков.
2. **Salt Edge.** Хорошее покрытие, но платно уже на старте.
3. **Tink / Plaid.** Plaid почти не работает в EU; Tink дорогой и требует партнёрства.
4. **Прямые API банков.** Только Monobank/Приват дают публичный доступ; в EU — редкость.
5. **Только CSV/OFX.** Бесплатно и всегда работает, но ручной труд.

## Решение

- Основной путь: **GoCardless** (EU/UK).
- Дополнительно: **Monobank** (UA) и **CSV/OFX** как универсальный fallback.
- Все провайдеры — за одним интерфейсом `BankProvider`:

```ts
interface BankProvider {
  listInstitutions(country: string): Promise<Institution[]>
  createLink(familyId: string, institutionId: string): Promise<{ url: string; ref: string }>
  completeLink(ref: string): Promise<{ connectionId: string; accounts: ExternalAccount[] }>
  fetchAccounts(connectionId: string): Promise<ExternalAccount[]>
  fetchTransactions(connectionId: string, from: Date, to: Date): Promise<ExternalTx[]>
  revoke(connectionId: string): Promise<void>
}
```

- Данные не покидают систему: агрегатор видит запросы, но не хранит нашу историю.
- Раз в сутки — синхронизация; ручная кнопка «обновить» с rate limit.

## Последствия

- Нужен публичный HTTPS-redirect и зарегистрированное приложение GoCardless.
- Храним `consentUntil`, напоминаем за 7 дней до истечения.
- При отзыве согласия история остаётся, новые операции не приходят.
- CSV-импорт обязан быть безупречным: это путь, который не сломается никогда.
