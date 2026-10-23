import type { DB } from "../db.js";
import { decrypt, encrypt } from "../lib/crypto.js";

export function getSetting(db: DB, key: string): string | null {
  const row = db.prepare("SELECT value FROM settings WHERE key = ?").get(key) as { value: string } | undefined;
  return row?.value ?? null;
}

export function setSetting(db: DB, key: string, value: string): void {
  db.prepare(
    `INSERT INTO settings (key, value, updated_at) VALUES (?, ?, datetime('now'))
     ON CONFLICT(key) DO UPDATE SET value = excluded.value, updated_at = datetime('now')`,
  ).run(key, value);
}

export function deleteSetting(db: DB, key: string): void {
  db.prepare("DELETE FROM settings WHERE key = ?").run(key);
}

export function getZenMoneyToken(db: DB, secret: string): string | null {
  const stored = getSetting(db, "zenmoney.token");
  if (!stored) return null;
  try {
    return decrypt(stored, secret);
  } catch {
    return null;
  }
}

export function setZenMoneyToken(db: DB, secret: string, token: string): void {
  setSetting(db, "zenmoney.token", encrypt(token, secret));
  setSetting(db, "zenmoney.token_updated_at", new Date().toISOString());
}

export function maskToken(token: string): string {
  if (token.length <= 8) return "••••";
  return `${token.slice(0, 4)}…${token.slice(-4)}`;
}
