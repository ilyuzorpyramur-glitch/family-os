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
  return db;
}
