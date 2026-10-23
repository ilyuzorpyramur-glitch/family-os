import { test } from "node:test";
import assert from "node:assert/strict";
import { openDb } from "../db.js";
import { seedCategories } from "../repo/categories.js";
import { upsertAccount } from "../repo/accounts.js";
import { saveTransaction, summaryByCurrency } from "../repo/transactions.js";
import { applyZenDiff } from "../zenmoney/sync.js";
import type { ZenDiff } from "../zenmoney/client.js";

function fixture(): ZenDiff {
  return {
    serverTimestamp: 1_700_000_000,
    instrument: [
      { id: 1, title: "Polish Zloty", shortTitle: "PLN" },
      { id: 2, title: "Euro", shortTitle: "EUR" },
    ],
    account: [
      {
        id: "acc-1",
        changed: 100,
        title: "Карта",
        type: "ccard",
        instrument: 1,
        balance: 1234.56,
        inBalance: true,
        archive: false,
      },
      {
        id: "acc-2",
        changed: 101,
        title: "Наличные",
        type: "cash",
        instrument: 1,
        balance: 200,
        inBalance: true,
        archive: false,
      },
    ],
    tag: [
      { id: "tag-food", changed: 1, title: "Еда", budgetOutcome: true, budgetIncome: false },
      { id: "tag-salary", changed: 1, title: "Зарплата", budgetIncome: true, budgetOutcome: false },
    ],
    transaction: [
      {
        id: "tx-1",
        changed: 200,
        date: "2026-10-01",
        income: 0,
        outcome: 45.5,
        incomeAccount: "acc-1",
        outcomeAccount: "acc-1",
        incomeInstrument: 1,
        outcomeInstrument: 1,
        tag: ["tag-food"],
        payee: "Biedronka",
        comment: null,
      },
      {
        id: "tx-2",
        changed: 201,
        date: "2026-10-02",
        income: 5000,
        outcome: 0,
        incomeAccount: "acc-1",
        outcomeAccount: "acc-1",
        incomeInstrument: 1,
        outcomeInstrument: 1,
        tag: ["tag-salary"],
        payee: "Работа",
        comment: null,
      },
      {
        id: "tx-3",
        changed: 202,
        date: "2026-10-03",
        income: 200,
        outcome: 200,
        incomeAccount: "acc-2",
        outcomeAccount: "acc-1",
        incomeInstrument: 1,
        outcomeInstrument: 1,
        tag: [],
        payee: null,
        comment: "Снятие",
      },
    ],
    deletion: [],
  };
}

test("applyZenDiff: счета, категории, операции и идемпотентность", () => {
  const db = openDb(":memory:");
  seedCategories(db);

  const stats = applyZenDiff(db, fixture(), "PLN");
  assert.equal(stats.accounts, 2);
  assert.equal(stats.categories, 2);
  assert.equal(stats.transactions, 3);

  const accounts = db.prepare("SELECT title, balance_minor, currency FROM accounts ORDER BY title").all() as {
    title: string;
    balance_minor: number;
    currency: string;
  }[];
  assert.deepEqual(accounts, [
    { title: "Карта", balance_minor: 123456, currency: "PLN" },
    { title: "Наличные", balance_minor: 20000, currency: "PLN" },
  ]);

  const expense = db
    .prepare("SELECT amount_minor, currency, kind FROM transactions WHERE external_id = 'tx-1'")
    .get() as { amount_minor: number; currency: string; kind: string };
  assert.equal(expense.amount_minor, -4550);
  assert.equal(expense.kind, "expense");

  const transfer = db
    .prepare("SELECT amount_minor, counter_account_id FROM transactions WHERE external_id = 'tx-3'")
    .get() as { amount_minor: number; counter_account_id: string | null };
  assert.equal(transfer.amount_minor, -20000);
  assert.ok(transfer.counter_account_id);

  const sums = db.prepare("SELECT SUM(amount_minor) AS s FROM postings GROUP BY transaction_id").all() as { s: number }[];
  assert.ok(sums.length >= 3);
  for (const row of sums) assert.equal(row.s, 0);

  // повторный diff с тем же changed не должен дублировать
  const second = applyZenDiff(db, fixture(), "PLN");
  assert.equal(second.transactions, 3);
  const count = db.prepare("SELECT COUNT(*) AS c FROM transactions").get() as { c: number };
  assert.equal(count.c, 3);
});

test("saveTransaction: баланс меняется, проводки сбалансированы", () => {
  const db = openDb(":memory:");
  seedCategories(db);
  const account = upsertAccount(db, { title: "Кошелёк", currency: "PLN", balanceMinor: 10000 });

  saveTransaction(db, {
    date: "2026-10-23",
    amountMinor: -1250,
    currency: "PLN",
    accountId: account.id,
    categoryId: "sys:uncategorized",
    kind: "expense",
    payee: "Кофе",
    adjustBalance: true,
  });

  const balance = db.prepare("SELECT balance_minor FROM accounts WHERE id = ?").get(account.id) as { balance_minor: number };
  assert.equal(balance.balance_minor, 8750);

  const postingSum = db.prepare("SELECT SUM(amount_minor) AS s FROM postings").get() as { s: number };
  assert.equal(postingSum.s, 0);
});

test("summaryByCurrency считает расходы и доходы", () => {
  const db = openDb(":memory:");
  seedCategories(db);
  applyZenDiff(db, fixture(), "PLN");
  const [summary] = summaryByCurrency(db, "2026-10-01", "2026-10-31");
  assert.ok(summary);
  assert.equal(summary.currency, "PLN");
  assert.equal(summary.income, 500000);
  assert.equal(summary.expense, 4550);
  assert.equal(summary.net, 495450);
});
