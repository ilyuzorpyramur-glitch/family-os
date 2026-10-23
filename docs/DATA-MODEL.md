# DATA MODEL

Основные сущности. Имена — как в Prisma; поля сокращены до значимых.

## M1: что реализовано

Фактическая схема M1 — SQLite (`apps/api/src/db.ts`): `accounts`, `categories`,
`transactions`, `postings`, `sync_state`, `settings`, `agent_calls`, `bank_connections`.

Отличия от целевой Prisma-модели ниже:

- Нет `Family`/`User`/`Device`: инстанс однопользовательский, доступ по `ADMIN_TOKEN`.
- `Account.external_id` — id счёта в Zen Money; `balance_minor` приходит из API.
- `Transaction` хранит знаковую сумму в валюте операции; `kind` — expense/income/transfer;
  для перевода заполнен `counter_account_id`.
- `Posting` — проводки для инварианта Σ=0; категория участвует как «контр-счёт».
- `bank_connections` создана под будущие провайдеры, в M1 не используется.

Ниже — целевая модель для мультисемейной версии (M2+).

## Идентичность

```prisma
model Family {
  id        String   @id @default(cuid())
  name      String
  currency  String   @default("PLN")
  timezone  String   @default("Europe/Warsaw")
  createdAt DateTime @default(now())
}

model User {
  id           String  @id @default(cuid())
  familyId     String
  role         Role    @default(CHILD) // OWNER | ADULT | CHILD | AGENT
  name         String
  birthDate    DateTime?
  avatar       String?
  pinHash      String?
  locale       String  @default("ru")
  dailyKcal    Int?
  dailyProtein Int?
  dailyFat     Int?
  dailyCarbs   Int?
}

model Device {
  id         String   @id @default(cuid())
  userId     String
  platform   String   // android
  name       String
  pushToken  String?
  lastSeenAt DateTime?
  isManaged  Boolean  @default(false) // Device Owner
}

model Invite {
  id        String   @id @default(cuid())
  familyId  String
  role      Role
  code      String   @unique
  expiresAt DateTime
}
```

## Guard — родительский контроль

```prisma
model GuardPolicy {
  id            String  @id @default(cuid())
  familyId      String
  childId       String  @unique
  screenLimitMin Int?          // дневной лимит
  bedtimeStart  String?        // "21:00"
  bedtimeEnd    String?        // "07:00"
  webFilter     Boolean @default(true)
  filterLevel   FilterLevel @default(BALANCED) // OFF | KIDS | BALANCED | CUSTOM
  allowInstall  Boolean @default(false)
  updatedBy     String?
}

model AppRule {
  id        String @id @default(cuid())
  policyId  String
  packageId String
  mode      RuleMode // ALLOW | BLOCK | LIMIT
  limitMin  Int?
  schedule  Json?     // [{days:[1..7], from:"16:00", to:"18:00"}]
}

model GuardEvent {
  id        String   @id @default(cuid())
  childId   String
  kind      EventKind // UNLOCK_REQUEST | LIMIT_HIT | BLOCKED_APP | SOS | GEOFENCE | TAMPER
  payload   Json
  createdAt DateTime @default(now())
}

model Geofence {
  id        String  @id @default(cuid())
  childId   String
  label     String
  lat       Float
  lng       Float
  radiusM   Int
  notifyOn  Json    // {enter:true, exit:true}
}

model ExtensionRequest {   // ребёнок просит +15 минут
  id        String   @id @default(cuid())
  childId   String
  minutes   Int
  reason    String?
  status    String   @default("pending") // pending | approved | denied
  decidedBy String?
}
```

## Money — двойная запись

```prisma
model Account {
  id              String   @id @default(cuid())
  familyId        String
  name            String
  kind            AccountKind // CASH | BANK | CARD | SAVINGS | CRYPTO | DEBT
  currency        String
  balanceMinor    BigInt   @default(0) // хранится денормализованно
  isShared        Boolean  @default(true)
  ownerId         String?
  connectionId    String?
  archivedAt      DateTime?
}

model Transaction {
  id           String   @id @default(cuid())
  familyId     String
  occurredAt   DateTime
  description  String
  payee        String?
  amountMinor  BigInt   // знак = направление
  currency     String
  accountId    String
  categoryId   String?
  kind         TxKind  // EXPENSE | INCOME | TRANSFER | ADJUSTMENT
  source       TxSource // MANUAL | IMPORT | AGENT | RECURRING
  externalId   String?
  tags         String[]
  createdBy    String
  createdAt    DateTime @default(now())
}

model Posting {           // двойная запись
  id            String @id @default(cuid())
  transactionId String
  accountId     String
  amountMinor   BigInt  // сумма со знаком, Σ по транзакции = 0
}

model Category {
  id        String  @id @default(cuid())
  familyId  String
  parentId  String?
  name      String
  icon      String?
  kind      TxKind
  budgetMinor BigInt?  // месячный лимит
}

model Budget {
  id         String @id @default(cuid())
  familyId   String
  categoryId String?
  period     String  // "2026-10"
  limitMinor BigInt
  rollover   Boolean @default(false)
}

model RecurringRule {
  id          String   @id @default(cuid())
  familyId    String
  template    Json     // описание транзакции
  rrule       String   // RFC 5545
  nextRunAt   DateTime
  accountId   String
}

model BankConnection {
  id           String   @id @default(cuid())
  familyId     String
  provider     String   // gocardless | monobank | csv
  institutionId String
  credentials  Bytes    // pgcrypto
  consentUntil DateTime?
  lastSyncAt   DateTime?
  status       String   @default("active")
}

model ImportRule {        // категоризация по контрагенту
  id         String @id @default(cuid())
  familyId   String
  matchField String // payee | description
  pattern    String
  categoryId String
  priority   Int    @default(0)
}
```

## Pantry — запасы

```prisma
model Product {
  id        String  @id @default(cuid())
  familyId  String
  name      String
  barcode   String?
  brand     String?
  unit      String  @default("pcs") // pcs | g | ml
  kcal100   Float?
  protein100 Float?
  fat100    Float?
  carbs100  Float?
  imageUrl  String?
  category  String?
}

model Location {
  id       String @id @default(cuid())
  familyId String
  name     String  // Кухня, Морозилка, Кладовая
  parentId String?
}

model StockBatch {
  id         String   @id @default(cuid())
  productId  String
  locationId String
  amount     Float
  unit       String
  expiresAt  DateTime?
  purchasedAt DateTime?
  priceMinor BigInt?
  openedAt   DateTime?
}

model StockMovement {
  id        String   @id @default(cuid())
  batchId   String?
  productId String
  delta     Float
  reason    MovementReason // PURCHASE | CONSUME | WASTE | ADJUST | COOK
  refId     String?
  createdAt DateTime @default(now())
}

model ShoppingList {
  id      String @id @default(cuid())
  familyId String
  name    String
  status  String @default("open")
}

model ShoppingItem {
  id        String  @id @default(cuid())
  listId    String
  productId String?
  text      String
  amount    Float?
  unit      String?
  done      Boolean @default(false)
}
```

## Nutrition

```prisma
model Meal {
  id        String   @id @default(cuid())
  userId    String
  eatenAt   DateTime
  kind      MealKind // BREAKFAST | LUNCH | DINNER | SNACK
  notes     String?
  photoUrl  String?
}

model MealItem {
  id        String  @id @default(cuid())
  mealId    String
  productId String? // или recipeId
  recipeId  String?
  name      String
  grams     Float
  kcal      Float
  protein   Float
  fat       Float
  carbs     Float
}

model Recipe {
  id         String @id @default(cuid())
  familyId   String
  title      String
  servings   Int
  steps      Json
  kcalPerServing Float?
  tags       String[]
}

model RecipeIngredient {
  id        String @id @default(cuid())
  recipeId  String
  productId String?
  text      String
  grams     Float
}
```

## Quest — геймификация

```prisma
model Quest {
  id          String   @id @default(cuid())
  familyId    String
  assigneeId  String?
  title       String
  description String?
  kind        QuestKind // ONCE | DAILY | WEEKLY | HABIT
  xp          Int      @default(10)
  rewardMinor BigInt?
  dueAt       DateTime?
  schedule    Json?    // дни недели / интервал
  requiresProof Boolean @default(false)
  createdBy   String
}

model QuestCompletion {
  id         String   @id @default(cuid())
  questId    String
  userId     String
  status     String   @default("pending") // pending | approved | rejected
  proofUrl   String?
  xpAwarded  Int?
  completedAt DateTime @default(now())
  approvedBy String?
}

model Achievement {
  id       String @id @default(cuid())
  familyId String
  code     String  // streak_7, budget_hero, chef_10
  title    String
  xp       Int
  rule     Json
}

model UserAchievement {
  userId        String
  achievementId String
  unlockedAt    DateTime @default(now())
  @@id([userId, achievementId])
}

model Reward {
  id        String  @id @default(cuid())
  familyId  String
  title     String
  costXp    Int
  kind      String  // screen_time | money | privilege | custom
  value     Json
}

model RewardRedemption {
  id        String @id @default(cuid())
  rewardId  String
  userId    String
  status    String @default("pending")
  createdAt DateTime @default(now())
}
```

## Copilot — агент

```prisma
model AgentToken {
  id         String   @id @default(cuid())
  familyId   String
  name       String
  scopes     String[] // money:read, money:write, guard:write, ...
  tokenHash  String
  expiresAt  DateTime?
  lastUsedAt DateTime?
}

model AgentAudit {
  id        String   @id @default(cuid())
  familyId  String
  tokenId   String?
  tool      String
  args      Json
  result    Json?
  ok        Boolean
  createdAt DateTime @default(now())
}

model Automation {
  id         String  @id @default(cuid())
  familyId   String
  name       String
  trigger    Json    // {type:"cron"|"event"|"threshold", ...}
  actions    Json
  enabled    Boolean @default(true)
  lastRunAt  DateTime?
}

model Notification {
  id        String   @id @default(cuid())
  familyId  String
  userId    String
  kind      String
  payload   Json
  readAt    DateTime?
  createdAt DateTime @default(now())
}
```

## Правила целостности

1. `Σ Posting.amountMinor = 0` для каждой транзакции (проверка в сервисе + триггер).
2. `Account.balanceMinor` = сумма проводок; пересчёт ночным job, расхождение → алерт.
3. `StockBatch.amount ≥ 0`; списание больше остатка — ошибка, не «минус».
4. Все таблицы с `familyId` — индекс `(familyId, updatedAt)` для дельта-синка.
5. Удаление — мягкое (`archivedAt` / `deletedAt`), кроме детских событий по запросу на удаление.
