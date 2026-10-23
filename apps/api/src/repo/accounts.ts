import type { DB } from "../db.js";

export type AccountRow = {
  id: string;
  external_id: string | null;
  title: string;
  type: string;
  currency: string;
  balance_minor: number;
  in_balance: number;
  archived: number;
  changed: number;
  source: string;
};

export type AccountInput = {
  id?: string;
  externalId?: string | null;
  title: string;
  type?: string;
  currency: string;
  balanceMinor?: number;
  inBalance?: boolean;
  archived?: boolean;
  changed?: number;
  source?: string;
};

export function upsertAccount(db: DB, input: AccountInput): AccountRow {
  const existing = input.externalId
    ? (db.prepare("SELECT * FROM accounts WHERE external_id = ?").get(input.externalId) as AccountRow | undefined)
    : undefined;

  if (existing) {
    if ((input.changed ?? 0) < existing.changed) return existing;
    db.prepare(
      `UPDATE accounts SET title = ?, type = ?, currency = ?, balance_minor = ?, in_balance = ?, archived = ?, changed = ?, updated_at = datetime('now')
       WHERE id = ?`,
    ).run(
      input.title,
      input.type ?? existing.type,
      input.currency,
      input.balanceMinor ?? existing.balance_minor,
      input.inBalance === false ? 0 : 1,
      input.archived ? 1 : 0,
      input.changed ?? existing.changed,
      existing.id,
    );
    return db.prepare("SELECT * FROM accounts WHERE id = ?").get(existing.id) as AccountRow;
  }

  const id = input.id ?? `acc_${crypto.randomUUID()}`;
  db.prepare(
    `INSERT INTO accounts (id, external_id, title, type, currency, balance_minor, in_balance, archived, changed, source)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
  ).run(
    id,
    input.externalId ?? null,
    input.title,
    input.type ?? "cash",
    input.currency,
    input.balanceMinor ?? 0,
    input.inBalance === false ? 0 : 1,
    input.archived ? 1 : 0,
    input.changed ?? 0,
    input.source ?? "zenmoney",
  );
  return db.prepare("SELECT * FROM accounts WHERE id = ?").get(id) as AccountRow;
}

export function listAccounts(db: DB, includeArchived = false): AccountRow[] {
  const where = includeArchived ? "" : "WHERE archived = 0";
  return db.prepare(`SELECT * FROM accounts ${where} ORDER BY currency, title`).all() as AccountRow[];
}

export function getAccount(db: DB, id: string): AccountRow | undefined {
  return db.prepare("SELECT * FROM accounts WHERE id = ?").get(id) as AccountRow | undefined;
}

export function adjustBalance(db: DB, accountId: string, deltaMinor: number): void {
  db.prepare("UPDATE accounts SET balance_minor = balance_minor + ?, updated_at = datetime('now') WHERE id = ?").run(
    deltaMinor,
    accountId,
  );
}
