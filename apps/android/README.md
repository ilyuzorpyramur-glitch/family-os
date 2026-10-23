# apps/android

Android-приложение: два режима в одной кодовой базе — родитель и ребёнок.

## Модули Gradle (план)

```
:app            точка входа, навигация, DI
:core:data      Room, сетевой слой, синхронизация
:core:ui        дизайн-система, компоненты
:feature:money  быстрый ввод, счета, бюджеты
:feature:pantry штрихкоды, остатки, покупки
:feature:food   дневник, сканер, рецепты
:feature:quest  задания, XP
:feature:guard  Device Owner, UsageStats, VPN-фильтр, геозоны
```

## Ключевые технологии

- Kotlin 2.x, Compose, Material 3.
- Room + WorkManager (оффлайн-очередь и синхронизация).
- Hilt, Retrofit/Ktor Client, kotlinx.serialization.
- ML Kit Barcode Scanning (bundled model — работает оффлайн).
- DevicePolicyManager, UsageStatsManager, VpnService, GeofencingClient.

## Разрешения и доступы

| Доступ | Зачем | Как получаем |
|---|---|---|
| Device Owner | лимиты, запрет удаления | QR/ADB provisioning |
| Usage Access | экранное время | настройки, ведём пользователя |
| Accessibility | блокировка приложений (fallback) | явное согласие |
| VPN | DNS-фильтрация | consent-диалог |
| Location (background) | геозоны | разрешение + уведомление |
| Notifications | лимиты, SOS | runtime |

## Статус

M0: проект не создан. M1 — экран быстрого ввода траты + оффлайн-очередь.
