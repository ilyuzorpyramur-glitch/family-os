import type { DB } from "../db.js";
import { toMinor } from "../lib/money.js";
import { getSetting, setSetting } from "../repo/settings.js";
import { upsertAccount } from "../repo/accounts.js";
import { categoryIdByExternal, upsertCategory } from "../repo/categories.js";
import { saveTransaction } from "../repo/transactions.js";
import { fetchZenDiff, type ZenDiff } from "./client.js";

export type SyncStats = {
  accounts: number;
  categories: number;
  transactions: number;
  deleted: number;
  skipped: number;
  serverTimestamp: number;
};

type InstrumentMap = Record<string, string>;

function loadInstruments(db: DB): InstrumentMap {
  const raw = getSetting(db, "zenmoney.instruments");
  if (!raw) return {};
  try {
    return JSON.parse(raw) as InstrumentMap;
  } catch {
    return {};
  }
}

export function applyZenDiff(db: DB, diff: ZenDiff, baseCurrency: string): SyncStats {
  const stats: SyncStats = {
    accounts: 0,
    categories: 0,
    transactions: 0,
    deleted: 0,
    skipped: 0,
    serverTimestamp: diff.serverTimestamp,
  };

  const instruments = loadInstruments(db);

  const apply = db.transaction(() => {
    for (const inst of diff.instrument ?? []) {
      instruments[String(inst.id)] = inst.shortTitle;
    }
    if (diff.instrument?.length) setSetting(db, "zenmoney.instruments", JSON.stringify(instruments));

    for (const account of diff.account ?? []) {
      const currency = instruments[String(account.instrument)] ?? baseCurrency;
      upsertAccount(db, {
        externalId: account.id,
        title: account.title,
        type: account.type,
        currency,
        balanceMinor: account.balance == null ? undefined : toMinor(account.balance),
        inBalance: account.inBalance,
        archived: account.archive,
        changed: account.changed,
      });
      stats.accounts += 1;
    }

    for (const tag of diff.tag ?? []) {
      const kind = tag.budgetIncome && !tag.budgetOutcome ? "income" : "expense";
      upsertCategory(db, {
        externalId: tag.id,
        title: tag.title,
        parentExternalId: tag.parent ?? null,
        kind,
        changed: tag.changed,
      });
      stats.categories += 1;
    }

    for (const tx of diff.transaction ?? []) {
      if (tx.deleted) {
        const row = db.prepare("SELECT id FROM transactions WHERE external_id = ?").get(tx.id) as { id: string } | undefined;
        if (row) {
          db.prepare("UPDATE transactions SET deleted = 1, changed = ? WHERE id = ?").run(tx.changed, row.id);
          db.prepare("DELETE FROM postings WHERE transaction_id = ?").run(row.id);
          stats.deleted += 1;
        }
        continue;
      }

      const outcomeAccount = db
        .prepare("SELECT id, currency FROM accounts WHERE external_id = ?")
        .get(tx.outcomeAccount) as { id: string; currency: string } | undefined;
      const incomeAccount = db
        .prepare("SELECT id, currency FROM accounts WHERE external_id = ?")
        .get(tx.incomeAccount) as { id: string; currency: string } | undefined;

      const categoryExternal = tx.tag?.[0];
      const categoryId = categoryExternal ? categoryIdByExternal(db, categoryExternal) : null;

      if (tx.outcome > 0 && tx.income > 0 && tx.outcomeAccount !== tx.incomeAccount) {
        if (!outcomeAccount || !incomeAccount) {
          stats.skipped += 1;
          continue;
        }
        saveTransaction(db, {
          externalId: tx.id,
          date: tx.date.slice(0, 10),
          amountMinor: -Math.abs(toMinor(tx.outcome)),
          currency: instruments[String(tx.outcomeInstrument)] ?? outcomeAccount.currency,
          accountId: outcomeAccount.id,
          counterAccountId: incomeAccount.id,
          kind: "transfer",
          payee: tx.payee ?? null,
          comment: tx.comment ?? null,
          source: "zenmoney",
          changed: tx.changed,
        });
        stats.transactions += 1;
        continue;
      }

      if (tx.outcome > 0) {
        if (!outcomeAccount) {
          stats.skipped += 1;
          continue;
        }
        saveTransaction(db, {
          externalId: tx.id,
          date: tx.date.slice(0, 10),
          amountMinor: -Math.abs(toMinor(tx.outcome)),
          currency: instruments[String(tx.outcomeInstrument)] ?? outcomeAccount.currency,
          accountId: outcomeAccount.id,
          categoryId,
          kind: "expense",
          payee: tx.payee ?? null,
          comment: tx.comment ?? null,
          source: "zenmoney",
          changed: tx.changed,
        });
        stats.transactions += 1;
        continue;
      }

      if (tx.income > 0) {
        if (!incomeAccount) {
          stats.skipped += 1;
          continue;
        }
        saveTransaction(db, {
          externalId: tx.id,
          date: tx.date.slice(0, 10),
          amountMinor: Math.abs(toMinor(tx.income)),
          currency: instruments[String(tx.incomeInstrument)] ?? incomeAccount.currency,
          accountId: incomeAccount.id,
          categoryId,
          kind: "income",
          payee: tx.payee ?? null,
          comment: tx.comment ?? null,
          source: "zenmoney",
          changed: tx.changed,
        });
        stats.transactions += 1;
        continue;
      }

      stats.skipped += 1;
    }

    for (const deletion of diff.deletion ?? []) {
      if (deletion.object === "transaction") {
        const row = db
          .prepare("SELECT id FROM transactions WHERE external_id = ?")
          .get(deletion.id) as { id: string } | undefined;
        if (row) {
          db.prepare("UPDATE transactions SET deleted = 1 WHERE id = ?").run(row.id);
          db.prepare("DELETE FROM postings WHERE transaction_id = ?").run(row.id);
          stats.deleted += 1;
        }
      }
    }

    db.prepare(
      `UPDATE sync_state SET server_timestamp = ?, last_sync_at = datetime('now'), last_error = NULL WHERE id = 1`,
    ).run(diff.serverTimestamp);
  });

  apply();
  return stats;
}

export async function syncZenMoney(db: DB, token: string, baseCurrency: string): Promise<SyncStats> {
  const state = db.prepare("SELECT server_timestamp FROM sync_state WHERE id = 1").get() as { server_timestamp: number };
  try {
    const diff = await fetchZenDiff(token, state.server_timestamp);
    return applyZenDiff(db, diff, baseCurrency);
  } catch (error) {
    db.prepare("UPDATE sync_state SET last_error = ? WHERE id = 1").run((error as Error).message);
    throw error;
  }
}

export function syncState(db: DB): {
  serverTimestamp: number;
  lastSyncAt: string | null;
  lastError: string | null;
} {
  return db
    .prepare("SELECT server_timestamp AS serverTimestamp, last_sync_at AS lastSyncAt, last_error AS lastError FROM sync_state WHERE id = 1")
    .get() as { serverTimestamp: number; lastSyncAt: string | null; lastError: string | null };
}
