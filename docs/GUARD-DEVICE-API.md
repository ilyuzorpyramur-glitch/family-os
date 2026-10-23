# GUARD — протокол устройства

Контракт между Android-приложением Family OS Guard и сервером. Серверная часть реализована
в M4.1; Android-клиент — следующий шаг.

## 1. Привязка устройства

Родитель в панели: **Guard → ребёнок → «Привязать»** → сервер показывает токен **один раз**.

```
POST /api/guard/children/{childId}/devices
{ "name": "Pixel Алисы", "platform": "android" }
→ { "device": { "id": "...", "name": "..." }, "token": "dev_..." }
```

Токен хранится на устройстве в `EncryptedSharedPreferences`; на сервере — только SHA-256.
Все запросы устройства: `Authorization: Bearer <device token>`.

## 2. Политика

```
GET /api/device/policy
→ {
    "child": { "id": "...", "name": "Алиса" },
    "policy": {
      "screen_limit_min": 120,
      "bedtime_start": "21:00",
      "bedtime_end": "07:00",
      "web_filter": 1,
      "filter_level": "balanced",
      "allow_install": 0
    },
    "limitMinutes": 140,     // с учётом одобренного продления на сегодня
    "usedMinutes": 45,
    "remainMinutes": 95,
    "serverTime": "2026-10-23T17:00:00.000Z"
  }
```

Устройство кэширует ответ и работает оффлайн. При конфликте времени доверять `serverTime`,
а не локальным часам.

## 3. Использование экрана

```
POST /api/device/usage
{
  "date": "2026-10-23",           // необязательно, по умолчанию сегодня
  "minutes": 45,
  "apps": [
    { "packageId": "com.google.android.youtube", "label": "YouTube", "minutes": 30 },
    { "packageId": "com.whatsapp", "label": "WhatsApp", "minutes": 15 }
  ]
}
→ { "ok": true }
```

Отправлять раз в 5–15 минут и при смене приложения. `minutes` — суммарное время за день,
не дельта. Повторная отправка перезаписывает день.

## 4. События

```
POST /api/device/events
{ "kind": "limit_hit", "payload": { "app": "com.google.android.youtube" }, "occurredAt": "..." }
```

Типы: `limit_hit`, `blocked_app`, `bedtime_started`, `web_blocked`, `sos`, `geofence_enter`,
`geofence_exit`, `tamper_attempt`, `install_blocked`, `device_boot`.

## 5. Запрос времени

```
POST /api/device/extension-request
{ "minutes": 15, "reason": "Досмотреть урок" }
→ { "id": "req_...", "status": "pending" }
```

Родитель одобряет в панели или через OpenClaw (`guard_approve_request`). После одобрения
`GET /api/device/policy` вернёт увеличенный `limitMinutes`.

## 6. Heartbeat

```
POST /api/device/heartbeat
→ { "serverTime": "...", "limitMinutes": 120 }
```

Раз в 15 минут. Отсутствие heartbeat 24 часа → устройство помечается «не на связи».

## 7. Что реализует Android-клиент

| Компонент | API Android | Замечание |
|---|---|---|
| Device Owner | `DevicePolicyManager` | QR-provisioning на новом устройстве |
| Экранное время | `UsageStatsManager` | разрешение `PACKAGE_USAGE_STATS` |
| Лимиты приложений | `AccessibilityService` + overlay | системные приложения в белом списке |
| Блокировка экрана | `DevicePolicyManager.lockNow()` / overlay | по достижении лимита и в bedtime |
| Фильтр веба | `VpnService` + DNS | локально, без расшифровки трафика |
| Геозоны | `GeofencingClient` | только при согласии, история 7 дней |
| SOS | foreground service + кнопка | приоритет над тихими часами |
| Защита | `setUninstallBlocked`, `setStatusBarDisabled` | аварийный сброс остаётся |

## 8. Безопасность

- Токен устройства не даёт доступа к деньгам, только к своему ребёнку.
- Сервер проверяет, что `child_id` устройства совпадает с записью; подмена `childId` в теле игнорируется.
- Все запросы — HTTPS; в проде `PUBLIC_URL` обязателен с `https://` (cookie Secure).
- Родительский токен не хранится на устройстве ребёнка.
