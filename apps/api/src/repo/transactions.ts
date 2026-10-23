import type { DB } from "../db.js";
import { adjustBalance, getAccount } from "./accounts.js";

export type TxKind = "expense" | "income" | "transfer";

export type TxRow = {
  id: string;
  external_id: string | null;
  occurred_at: string;
  amount_minor: number;
  currency: string;
  account_id: string;
  counter_account_id: string | null;
  category_id: string | null;
  kind: TxKind;
  payee: string | null;
  comment: string | null;
  source: string;
  changed: number;
  deleted: number;
};

export type TxInput = {
  id?: string;
  externalId?: string | null;
  date: string;
  amountMinor: number;
  currency: string;
  accountId: string;
  counterAccountId?: string | null;
  categoryId?: string | null;
  kind: TxKind;
  payee?: string | null;
  comment?: string | null;
  source?: string;
  changed?: number;
  deleted?: boolean;
  adjustBalance?: boolean;
};

const UNCATEGORIZED_EXPENSE = "sys:uncategorized";
const UNCATEGORIZED_INCOME = "sys:uncategorized-income";

export function saveTransaction(db: DB, input: TxInput): TxRow {
  const account = getAccount(db, input.accountId);
  if (!account) throw new Error(`Счёт не найден: ${input.accountId}`);

  if (input.kind === "transfer" && !input.counterAccountId) {
    throw new Error("Для перевода нужен второй счёт");
  }

  const amount = input.amountMinor;
  if (!Number.isInteger(amount) || amount === 0) throw new Error("Сумма должна быть целым числом копеек и не равна нулю");

  const categoryId =
    input.categoryId ??
    (input.kind === "expense" ? UNCATEGORIZED_EXPENSE : input.kind === "income" ? UNCATEGORIZED_INCOME : null);

  const run = db.transaction((): TxRow => {
    const existing = input.externalId
      ? (db.prepare("SELECT * FROM transactions WHERE external_id = ?").get(input.externalId) as TxRow | undefined)
      : undefined;

    if (existing && (input.changed ?? 0) < existing.changed) return existing;

    const id = existing?.id ?? input.id ?? `tx_${crypto.randomUUID()}`;
    const deleted = input.deleted ? 1 : 0;

    if (existing) {
      db.prepare(
        `UPDATE transactions SET occurred_at = ?, amount_minor = ?, currency = ?, account_id = ?, counter_account_id = ?,
           category_id = ?, kind = ?, payee = ?, comment = ?, source = ?, changed = ?, deleted = ?
         WHERE id = ?`,
      ).run(
        input.date,
        amount,
        input.currency,
        input.accountId,
        input.counterAccountId ?? null,
        categoryId,
        input.kind,
        input.payee ?? null,
        input.comment ?? null,
        input.source ?? existing.source,
        input.changed ?? existing.changed,
        deleted,
        id,
      );
      db.prepare("DELETE FROM postings WHERE transaction_id = ?").run(id);
    } else {
      db.prepare(
        `INSERT INTO transactions (id, external_id, occurred_at, amount_minor, currency, account_id, counter_account_id,
           category_id, kind, payee, comment, source, changed, deleted)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
      ).run(
        id,
        input.externalId ?? null,
        input.date,
        amount,
        input.currency,
        input.accountId,
        input.counterAccountId ?? null,
        categoryId,
        input.kind,
        input.payee ?? null,
        input.comment ?? null,
        input.source ?? "manual",
        input.changed ?? 0,
        deleted,
      );
    }

    if (!deleted) {
      const insertPosting = db.prepare(
        "INSERT INTO postings (transaction_id, account_id, category_id, amount_minor) VALUES (?, ?, ?, ?)",
      );
      if (input.kind === "transfer") {
        insertPosting.run(id, input.accountId, null, -Math.abs(amount));
        insertPosting.run(id, input.counterAccountId!, null, Math.abs(amount));
      } else if (input.kind === "expense") {
        insertPosting.run(id, input.accountId, null, -Math.abs(amount));
        insertPosting.run(id, null, categoryId, Math.abs(amount));
      } else {
        insertPosting.run(id, input.accountId, null, Math.abs(amount));
        insertPosting.run(id, null, categoryId, -Math.abs(amount));
      }
    }

    if (input.adjustBalance && !existing) {
      if (input.kind === "transfer") {
        adjustBalance(db, input.accountId, -Math.abs(amount));
        adjustBalance(db, input.counterAccountId!, Math.abs(amount));
      } else {
        adjustBalance(db, input.accountId, amount);
      }
    }

    return db.prepare("SELECT * FROM transactions WHERE id = ?").get(id) as TxRow;
  });

  return run();
}

export type TxListOptions = {
  limit?: number;
  from?: string;
  to?: string;
  accountId?: string;
  categoryId?: string;
  q?: string;
  includeDeleted?: boolean;
};

export type TxView = TxRow & {
  account_title: string;
  counter_account_title: string | null;
  category_title: string | null;
};

export function listTransactions(db: DB, opts: TxListOptions = {}): TxView[] {
  const where: string[] = [];
  const params: unknown[] = [];

  if (!opts.includeDeleted) where.push("t.deleted = 0");
  if (opts.from) {
    where.push("t.occurred_at >= ?");
    params.push(opts.from);
  }
  if (opts.to) {
    where.push("t.occurred_at <= ?");
    params.push(opts.to);
  }
  if (opts.accountId) {
    where.push("(t.account_id = ? OR t.counter_account_id = ?)");
    params.push(opts.accountId, opts.accountId);
  }
  if (opts.categoryId) {
    where.push("t.category_id = ?");
    params.push(opts.categoryId);
  }
  if (opts.q) {
    where.push("(t.payee LIKE ? OR t.comment LIKE ?)");
    params.push(`%${opts.q}%`, `%${opts.q}%`);
  }

  const sql = `
    SELECT t.*, a.title AS account_title, ca.title AS counter_account_title, c.title AS category_title
    FROM transactions t
    JOIN accounts a ON a.id = t.account_id
    LEFT JOIN accounts ca ON ca.id = t.counter_account_id
    LEFT JOIN categories c ON c.id = t.category_id
    ${where.length ? "WHERE " + where.join(" AND ") : ""}
    ORDER BY t.occurred_at DESC, t.created_at DESC
    LIMIT ?`;

  params.push(Math.min(Math.max(opts.limit ?? 50, 1), 500));
  return db.prepare(sql).all(...params) as TxView[];
}

export type CurrencySummary = {
  currency: string;
  income: number;
  expense: number;
  net: number;
};

export function summaryByCurrency(db: DB, from: string, to: string): CurrencySummary[] {
  return db
    .prepare(
      `SELECT currency,
              COALESCE(SUM(CASE WHEN kind = 'income' THEN amount_minor ELSE 0 END), 0) AS income,
              COALESCE(SUM(CASE WHEN kind = 'expense' THEN -amount_minor ELSE 0 END), 0) AS expense
       FROM transactions
       WHERE deleted = 0 AND kind IN ('income','expense') AND occurred_at >= ? AND occurred_at <= ?
       GROUP BY currency
       ORDER BY currency`,
    )
    .all(from, to)
    .map((row) => {
      const r = row as { currency: string; income: number; expense: number };
      return { ...r, net: r.income - r.expense };
    });
}

export type CategorySummary = {
  category_id: string;
  category_title: string;
  currency: string;
  total: number;
};

export function summaryByCategory(db: DB, from: string, to: string): CategorySummary[] {
  return db
    .prepare(
      `SELECT t.category_id, COALESCE(c.title, 'Без категории') AS category_title, t.currency,
              SUM(-t.amount_minor) AS total
       FROM transactions t
       LEFT JOIN categories c ON c.id = t.category_id
       WHERE t.deleted = 0 AND t.kind = 'expense' AND t.occurred_at >= ? AND t.occurred_at <= ?
       GROUP BY t.category_id, t.currency
       ORDER BY total DESC`,
    )
    .all(from, to) as CategorySummary[];
}

export function balanceByCurrency(db: DB): { currency: string; balance: number }[] {
  return db
    .prepare(
      `SELECT currency, SUM(balance_minor) AS balance FROM accounts
       WHERE archived = 0 AND in_balance = 1 GROUP BY currency ORDER BY currency`,
    )
    .all() as { currency: string; balance: number }[];
}
