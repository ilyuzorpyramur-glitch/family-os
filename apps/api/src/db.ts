import Database from "better-sqlite3";
import { mkdirSync } from "node:fs";
import { dirname } from "node:path";

export type DB = Database.Database;

const SCHEMA = `
PRAGMA journal_mode = WAL;
PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS settings (
  key        TEXT PRIMARY KEY,
  value      TEXT NOT NULL,
  updated_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS accounts (
  id           TEXT PRIMARY KEY,
  external_id  TEXT UNIQUE,
  title        TEXT NOT NULL,
  type         TEXT NOT NULL DEFAULT 'cash',
  currency     TEXT NOT NULL,
  balance_minor INTEGER NOT NULL DEFAULT 0,
  in_balance   INTEGER NOT NULL DEFAULT 1,
  archived     INTEGER NOT NULL DEFAULT 0,
  changed      INTEGER NOT NULL DEFAULT 0,
  source       TEXT NOT NULL DEFAULT 'zenmoney',
  updated_at   TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS categories (
  id                 TEXT PRIMARY KEY,
  external_id        TEXT UNIQUE,
  title              TEXT NOT NULL,
  parent_external_id TEXT,
  kind               TEXT NOT NULL DEFAULT 'expense' CHECK (kind IN ('expense','income')),
  changed            INTEGER NOT NULL DEFAULT 0,
  updated_at         TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS transactions (
  id                 TEXT PRIMARY KEY,
  external_id        TEXT UNIQUE,
  occurred_at        TEXT NOT NULL,
  amount_minor       INTEGER NOT NULL,
  currency           TEXT NOT NULL,
  account_id         TEXT NOT NULL REFERENCES accounts(id),
  counter_account_id TEXT REFERENCES accounts(id),
  category_id        TEXT REFERENCES categories(id),
  kind               TEXT NOT NULL CHECK (kind IN ('expense','income','transfer')),
  payee              TEXT,
  comment            TEXT,
  source             TEXT NOT NULL DEFAULT 'manual',
  changed            INTEGER NOT NULL DEFAULT 0,
  deleted            INTEGER NOT NULL DEFAULT 0,
  created_at         TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX IF NOT EXISTS idx_tx_date ON transactions(occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_tx_account ON transactions(account_id, occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_tx_category ON transactions(category_id, occurred_at DESC);

CREATE TABLE IF NOT EXISTS postings (
  id             INTEGER PRIMARY KEY AUTOINCREMENT,
  transaction_id TEXT NOT NULL REFERENCES transactions(id) ON DELETE CASCADE,
  account_id     TEXT REFERENCES accounts(id),
  category_id    TEXT REFERENCES categories(id),
  amount_minor   INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_postings_tx ON postings(transaction_id);

CREATE TABLE IF NOT EXISTS sync_state (
  id               INTEGER PRIMARY KEY CHECK (id = 1),
  server_timestamp INTEGER NOT NULL DEFAULT 0,
  last_sync_at     TEXT,
  last_error       TEXT
);
INSERT OR IGNORE INTO sync_state (id) VALUES (1);

CREATE TABLE IF NOT EXISTS agent_calls (
  id         INTEGER PRIMARY KEY AUTOINCREMENT,
  tool       TEXT NOT NULL,
  args       TEXT NOT NULL,
  result     TEXT,
  ok         INTEGER NOT NULL DEFAULT 1,
  duration_ms INTEGER,
  created_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS crash_reports (
  id         TEXT PRIMARY KEY,
  child_id   TEXT,
  device_id  TEXT,
  message    TEXT NOT NULL,
  stack      TEXT,
  app_version TEXT,
  created_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS bank_connections (
  id            TEXT PRIMARY KEY,
  provider      TEXT NOT NULL,
  label         TEXT,
  credentials   TEXT NOT NULL,
  consent_until TEXT,
  last_sync_at  TEXT,
  created_at    TEXT NOT NULL DEFAULT (datetime('now'))
);

-- Guard: родительский контроль

CREATE TABLE IF NOT EXISTS children (
  id         TEXT PRIMARY KEY,
  name       TEXT NOT NULL,
  birth_date TEXT,
  created_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS devices (
  id           TEXT PRIMARY KEY,
  child_id     TEXT NOT NULL REFERENCES children(id) ON DELETE CASCADE,
  name         TEXT NOT NULL,
  platform     TEXT NOT NULL DEFAULT 'android',
  token_hash   TEXT NOT NULL UNIQUE,
  push_token   TEXT,
  status       TEXT NOT NULL DEFAULT 'active',
  last_seen_at TEXT,
  created_at   TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS guard_policies (
  child_id         TEXT PRIMARY KEY REFERENCES children(id) ON DELETE CASCADE,
  screen_limit_min INTEGER NOT NULL DEFAULT 120,
  bedtime_start    TEXT NOT NULL DEFAULT '21:00',
  bedtime_end      TEXT NOT NULL DEFAULT '07:00',
  web_filter       INTEGER NOT NULL DEFAULT 1,
  filter_level     TEXT NOT NULL DEFAULT 'balanced',
  allow_install    INTEGER NOT NULL DEFAULT 0,
  extra_minutes    INTEGER NOT NULL DEFAULT 0,
  extra_date       TEXT,
  updated_at       TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS app_rules (
  id         TEXT PRIMARY KEY,
  child_id   TEXT NOT NULL REFERENCES children(id) ON DELETE CASCADE,
  package_id TEXT NOT NULL,
  label      TEXT,
  mode       TEXT NOT NULL DEFAULT 'limit',
  limit_min  INTEGER,
  created_at TEXT NOT NULL DEFAULT (datetime('now')),
  UNIQUE (child_id, package_id)
);

CREATE TABLE IF NOT EXISTS guard_events (
  id          TEXT PRIMARY KEY,
  child_id    TEXT NOT NULL REFERENCES children(id) ON DELETE CASCADE,
  device_id   TEXT,
  kind        TEXT NOT NULL,
  payload     TEXT,
  occurred_at TEXT NOT NULL,
  created_at  TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX IF NOT EXISTS idx_guard_events_child ON guard_events(child_id, occurred_at DESC);

CREATE TABLE IF NOT EXISTS extension_requests (
  id         TEXT PRIMARY KEY,
  child_id   TEXT NOT NULL REFERENCES children(id) ON DELETE CASCADE,
  device_id  TEXT,
  minutes    INTEGER NOT NULL,
  reason     TEXT,
  status     TEXT NOT NULL DEFAULT 'pending',
  decided_by TEXT,
  decided_at TEXT,
  created_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX IF NOT EXISTS idx_extension_status ON extension_requests(status, created_at DESC);

CREATE TABLE IF NOT EXISTS usage_daily (
  child_id   TEXT NOT NULL REFERENCES children(id) ON DELETE CASCADE,
  date       TEXT NOT NULL,
  minutes    INTEGER NOT NULL DEFAULT 0,
  updated_at TEXT NOT NULL DEFAULT (datetime('now')),
  PRIMARY KEY (child_id, date)
);

CREATE TABLE IF NOT EXISTS usage_apps (
  child_id   TEXT NOT NULL REFERENCES children(id) ON DELETE CASCADE,
  date       TEXT NOT NULL,
  package_id TEXT NOT NULL,
  label      TEXT,
  minutes    INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (child_id, date, package_id)
);

CREATE TABLE IF NOT EXISTS character_profiles (
  child_id           TEXT PRIMARY KEY REFERENCES children(id) ON DELETE CASCADE,
  xp                 INTEGER NOT NULL DEFAULT 0,
  coins              INTEGER NOT NULL DEFAULT 0,
  level              INTEGER NOT NULL DEFAULT 1,
  strength           INTEGER NOT NULL DEFAULT 0,
  intellect          INTEGER NOT NULL DEFAULT 0,
  creativity         INTEGER NOT NULL DEFAULT 0,
  kindness           INTEGER NOT NULL DEFAULT 0,
  discipline         INTEGER NOT NULL DEFAULT 0,
  streak_days        INTEGER NOT NULL DEFAULT 0,
  last_completed_date TEXT,
  updated_at         TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS quests (
  id           TEXT PRIMARY KEY,
  child_id     TEXT NOT NULL REFERENCES children(id) ON DELETE CASCADE,
  title        TEXT NOT NULL,
  description  TEXT,
  kind         TEXT NOT NULL DEFAULT 'once',
  xp           INTEGER NOT NULL DEFAULT 10,
  coins        INTEGER NOT NULL DEFAULT 5,
  stat         TEXT,
  due_at       TEXT,
  status       TEXT NOT NULL DEFAULT 'active',
  created_by   TEXT NOT NULL DEFAULT 'parent',
  created_at   TEXT NOT NULL DEFAULT (datetime('now')),
  completed_at TEXT,
  approved_at  TEXT
);

CREATE INDEX IF NOT EXISTS idx_quests_child ON quests(child_id, status, created_at DESC);

CREATE TABLE IF NOT EXISTS quest_events (
  id         TEXT PRIMARY KEY,
  child_id   TEXT NOT NULL,
  quest_id   TEXT,
  kind       TEXT NOT NULL,
  payload    TEXT,
  created_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS shop_items (
  id          TEXT PRIMARY KEY,
  title       TEXT NOT NULL,
  description TEXT,
  price_coins INTEGER NOT NULL,
  kind        TEXT NOT NULL DEFAULT 'privilege',
  value       TEXT,
  stock       INTEGER,
  photo_path  TEXT,
  active      INTEGER NOT NULL DEFAULT 1,
  created_by  TEXT NOT NULL DEFAULT 'parent',
  created_at  TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS shop_purchases (
  id          TEXT PRIMARY KEY,
  child_id    TEXT NOT NULL REFERENCES children(id) ON DELETE CASCADE,
  item_id     TEXT NOT NULL REFERENCES shop_items(id),
  price_coins INTEGER NOT NULL,
  status      TEXT NOT NULL DEFAULT 'pending',
  decided_by  TEXT,
  decided_at  TEXT,
  created_at  TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX IF NOT EXISTS idx_purchases_status ON shop_purchases(status, created_at DESC);

CREATE TABLE IF NOT EXISTS events (
  id           TEXT PRIMARY KEY,
  child_id     TEXT NOT NULL REFERENCES children(id) ON DELETE CASCADE,
  title        TEXT NOT NULL,
  geofence_id  TEXT,
  date         TEXT NOT NULL,
  start_time   TEXT NOT NULL,
  duration_min INTEGER NOT NULL DEFAULT 60,
  status       TEXT NOT NULL DEFAULT 'pending',
  note         TEXT,
  created_by   TEXT NOT NULL DEFAULT 'child',
  approved_by  TEXT,
  approved_at  TEXT,
  created_at   TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX IF NOT EXISTS idx_events_child_date ON events(child_id, date);

CREATE TABLE IF NOT EXISTS quest_photos (
  id         TEXT PRIMARY KEY,
  quest_id   TEXT NOT NULL REFERENCES quests(id) ON DELETE CASCADE,
  path       TEXT NOT NULL,
  created_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS shop_proposals (
  id          TEXT PRIMARY KEY,
  child_id    TEXT NOT NULL REFERENCES children(id) ON DELETE CASCADE,
  title       TEXT NOT NULL,
  description TEXT,
  photo_path  TEXT,
  status      TEXT NOT NULL DEFAULT 'pending',
  price_coins INTEGER,
  priced_by   TEXT,
  priced_at   TEXT,
  created_at  TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS geofence_state (
  child_id     TEXT NOT NULL,
  geofence_id  TEXT NOT NULL,
  inside       INTEGER NOT NULL DEFAULT 0,
  last_fine_at TEXT,
  updated_at   TEXT NOT NULL DEFAULT (datetime('now')),
  PRIMARY KEY (child_id, geofence_id)
);

CREATE TABLE IF NOT EXISTS location_pings (
  id         TEXT PRIMARY KEY,
  child_id   TEXT NOT NULL,
  lat        REAL NOT NULL,
  lng        REAL NOT NULL,
  accuracy   REAL,
  created_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX IF NOT EXISTS idx_pings_child ON location_pings(child_id, created_at DESC);

CREATE TABLE IF NOT EXISTS geofences (
  id           TEXT PRIMARY KEY,
  child_id     TEXT NOT NULL REFERENCES children(id) ON DELETE CASCADE,
  label        TEXT NOT NULL,
  lat          REAL NOT NULL,
  lng          REAL NOT NULL,
  radius_m     INTEGER NOT NULL DEFAULT 150,
  notify_enter INTEGER NOT NULL DEFAULT 1,
  notify_exit  INTEGER NOT NULL DEFAULT 1,
  created_at   TEXT NOT NULL DEFAULT (datetime('now'))
);
`;

export function openDb(file: string): DB {
  if (file !== ":memory:") mkdirSync(dirname(file), { recursive: true });
  const db = new Database(file);
  db.pragma("foreign_keys = ON");
  db.exec(SCHEMA);
  addColumn(db, "quests", "approval", "approval TEXT NOT NULL DEFAULT 'parent'");
  addColumn(db, "quests", "photos_required", "photos_required INTEGER NOT NULL DEFAULT 0");
  addColumn(db, "shop_items", "photo_path", "photo_path TEXT");
  addColumn(db, "extension_requests", "cost_coins", "cost_coins INTEGER NOT NULL DEFAULT 0");
  addColumn(db, "children", "avatar_path", "avatar_path TEXT");
  addColumn(db, "children", "nickname", "nickname TEXT");
  addColumn(db, "children", "school", "school TEXT");
  addColumn(db, "children", "grade", "grade TEXT");
  addColumn(db, "geofences", "fine_coins", "fine_coins INTEGER NOT NULL DEFAULT 0");
  addColumn(db, "geofences", "schedule_start", "schedule_start TEXT");
  addColumn(db, "geofences", "schedule_end", "schedule_end TEXT");
  addColumn(db, "geofences", "fine_every_min", "fine_every_min INTEGER NOT NULL DEFAULT 5");
  addColumn(db, "geofences", "auto_quest_id", "auto_quest_id TEXT");
  addColumn(db, "devices", "last_lat", "last_lat REAL");
  addColumn(db, "devices", "last_lng", "last_lng REAL");
  addColumn(db, "devices", "last_location_at", "last_location_at TEXT");
  addColumn(db, "quests", "shared", "shared INTEGER NOT NULL DEFAULT 0");
  addColumn(db, "events", "description", "description TEXT");
  addColumn(db, "events", "lat", "lat REAL");
  addColumn(db, "events", "lng", "lng REAL");
  return db;
}

function addColumn(db: DB, table: string, column: string, definition: string): void {
  const columns = db.prepare(`PRAGMA table_info(${table})`).all() as { name: string }[];
  if (!columns.some((c) => c.name === column)) {
    db.exec(`ALTER TABLE ${table} ADD COLUMN ${definition}`);
  }
}
