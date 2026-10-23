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
`;

export function openDb(file: string): DB {
  if (file !== ":memory:") mkdirSync(dirname(file), { recursive: true });
  const db = new Database(file);
  db.pragma("foreign_keys = ON");
  db.exec(SCHEMA);
  return db;
}
