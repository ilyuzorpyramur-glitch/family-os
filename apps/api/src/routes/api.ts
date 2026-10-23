import type { FastifyInstance } from "fastify";
import { z } from "zod";
import type { Config } from "../config.js";
import type { DB } from "../db.js";
import { bearerActor, isAuthorized } from "../auth.js";
import { toMinor } from "../lib/money.js";
import { isValidISODate, monthStartISO, todayISO } from "../lib/time.js";
import { listAccounts, upsertAccount } from "../repo/accounts.js";
import { listCategories } from "../repo/categories.js";
import { balanceByCurrency, listTransactions, saveTransaction, summaryByCategory, summaryByCurrency } from "../repo/transactions.js";
import { deleteSetting, getSetting, getZenMoneyToken, maskToken, setZenMoneyToken } from "../repo/settings.js";
import { syncState, syncZenMoney } from "../zenmoney/sync.js";

export type Ctx = { config: Config; db: DB };

const addTxSchema = z.object({
  date: z.string().optional(),
  amount: z.union([z.number(), z.string()]),
  currency: z.string().length(3).optional(),
  accountId: z.string().min(1),
  categoryId: z.string().min(1).nullable().optional(),
  kind: z.enum(["expense", "income", "transfer"]).default("expense"),
  payee: z.string().max(200).optional(),
  comment: z.string().max(1000).optional(),
  counterAccountId: z.string().min(1).optional(),
});

export function registerApi(app: FastifyInstance, ctx: Ctx): void {
  const { config, db } = ctx;

  app.get("/api/health", async () => ({ ok: true, service: "family-os", version: "0.1.0" }));

  const guard = async (request: Parameters<typeof isAuthorized>[0], reply: { code: (n: number) => { send: (b: unknown) => unknown } }) => {
    if (!isAuthorized(request, config)) {
      return reply.code(401).send({ ok: false, error: { code: "UNAUTHORIZED", message: "Нужен Bearer-токен" } });
    }
  };

  app.get("/api/status", { preHandler: guard }, async () => {
    const hasToken = Boolean(getZenMoneyToken(db, config.secret));
    return {
      ok: true,
      data: {
        zenmoney: { connected: hasToken, masked: hasToken ? maskToken(getZenMoneyToken(db, config.secret)!) : null },
        sync: syncState(db),
        baseCurrency: config.baseCurrency,
      },
    };
  });

  app.get("/api/accounts", { preHandler: guard }, async () => {
    const accounts = listAccounts(db, true);
    return { ok: true, data: accounts.map((a) => ({ ...a, balance: a.balance_minor / 100 })) };
  });

  app.get("/api/categories", { preHandler: guard }, async () => ({ ok: true, data: listCategories(db) }));

  app.get("/api/transactions", { preHandler: guard }, async (request) => {
    const q = request.query as Record<string, string | undefined>;
    const rows = listTransactions(db, {
      limit: q.limit ? Number(q.limit) : 50,
      from: q.from,
      to: q.to,
      accountId: q.accountId,
      categoryId: q.categoryId,
      q: q.q,
    });
    return {
      ok: true,
      data: rows.map((t) => ({ ...t, amount: t.amount_minor / 100, deleted: Boolean(t.deleted) })),
    };
  });

  app.get("/api/summary", { preHandler: guard }, async (request) => {
    const q = request.query as Record<string, string | undefined>;
    const from = q.from && isValidISODate(q.from) ? q.from : monthStartISO();
    const to = q.to && isValidISODate(q.to) ? q.to : todayISO();
    return {
      ok: true,
      data: {
        from,
        to,
        byCurrency: summaryByCurrency(db, from, to).map((s) => ({
          ...s,
          income: s.income / 100,
          expense: s.expense / 100,
          net: s.net / 100,
        })),
        balances: balanceByCurrency(db).map((b) => ({ ...b, balance: b.balance / 100 })),
      },
    };
  });

  app.post("/api/transactions", { preHandler: guard }, async (request, reply) => {
    const parsed = addTxSchema.safeParse(request.body);
    if (!parsed.success) {
      return reply.code(400).send({ ok: false, error: { code: "VALIDATION", message: parsed.error.issues[0]?.message } });
    }
    const input = parsed.data;
    const account = listAccounts(db, true).find((a) => a.id === input.accountId);
    if (!account) return reply.code(404).send({ ok: false, error: { code: "NOT_FOUND", message: "Счёт не найден" } });

    const date = input.date ?? todayISO();
    if (!isValidISODate(date)) {
      return reply.code(400).send({ ok: false, error: { code: "VALIDATION", message: "Дата должна быть YYYY-MM-DD" } });
    }

    const raw = toMinor(input.amount);
    const amountMinor = input.kind === "income" ? Math.abs(raw) : -Math.abs(raw);

    try {
      const tx = saveTransaction(db, {
        date,
        amountMinor,
        currency: input.currency ?? account.currency,
        accountId: input.accountId,
        counterAccountId: input.counterAccountId,
        categoryId: input.categoryId ?? null,
        kind: input.kind,
        payee: input.payee,
        comment: input.comment,
        source: bearerActor(request, config) === "agent" ? "agent" : "manual",
        adjustBalance: true,
      });
      return { ok: true, data: { ...tx, amount: tx.amount_minor / 100 } };
    } catch (error) {
      return reply.code(400).send({ ok: false, error: { code: "TX_REJECTED", message: (error as Error).message } });
    }
  });

  app.get("/api/money/overview", { preHandler: guard }, async (request) => {
    const q = request.query as Record<string, string | undefined>;
    const from = q.from && isValidISODate(q.from) ? q.from : monthStartISO();
    const to = q.to && isValidISODate(q.to) ? q.to : todayISO();
    const accounts = listAccounts(db, true).map((a) => ({ ...a, balance: a.balance_minor / 100 }));
    const totals = summaryByCurrency(db, from, to).map((s) => ({
      ...s,
      income: s.income / 100,
      expense: s.expense / 100,
      net: s.net / 100,
    }));
    const byCategory = summaryByCategory(db, from, to).map((c) => ({ ...c, total: c.total / 100 }));
    const recent = listTransactions(db, { limit: 20 }).map((t) => ({ ...t, amount: t.amount_minor / 100 }));
    return { ok: true, data: { from, to, currency: config.baseCurrency, accounts, totals, byCategory, recent } };
  });

  app.post("/api/accounts", { preHandler: guard }, async (request, reply) => {
    const body = z
      .object({
        title: z.string().min(1).max(80),
        type: z.enum(["cash", "ccard", "checking", "savings", "credit"]).default("cash"),
        currency: z.string().length(3).optional(),
        balance: z.union([z.number(), z.string()]).optional(),
      })
      .safeParse(request.body);
    if (!body.success) {
      return reply.code(400).send({ ok: false, error: { code: "VALIDATION", message: body.error.issues[0]?.message } });
    }
    const account = upsertAccount(db, {
      title: body.data.title.trim(),
      type: body.data.type,
      currency: (body.data.currency ?? config.baseCurrency).toUpperCase(),
      balanceMinor: body.data.balance === undefined ? 0 : Math.round(Number(body.data.balance) * 100),
      source: "local",
    });
    return { ok: true, data: { ...account, balance: account.balance_minor / 100 } };
  });

  app.post("/api/accounts/:id/archive", { preHandler: guard }, async (request, reply) => {
    const { id } = request.params as { id: string };
    const account = listAccounts(db, true).find((a) => a.id === id);
    if (!account) return reply.code(404).send({ ok: false, error: { code: "NOT_FOUND", message: "Счёт не найден" } });
    db.prepare("UPDATE accounts SET archived = 1, updated_at = datetime('now') WHERE id = ?").run(id);
    return { ok: true };
  });

  app.post("/api/categories", { preHandler: guard }, async (request, reply) => {
    const body = z
      .object({ title: z.string().min(1).max(60), kind: z.enum(["expense", "income"]).default("expense") })
      .safeParse(request.body);
    if (!body.success) {
      return reply.code(400).send({ ok: false, error: { code: "VALIDATION", message: body.error.issues[0]?.message } });
    }
    const id = `cat_${crypto.randomUUID()}`;
    db.prepare("INSERT INTO categories (id, external_id, title, kind) VALUES (?, ?, ?, ?)").run(
      id,
      id,
      body.data.title.trim(),
      body.data.kind,
    );
    return { ok: true, data: { id, title: body.data.title.trim(), kind: body.data.kind } };
  });

  app.post("/api/zenmoney/token", { preHandler: guard }, async (request, reply) => {
    const body = z.object({ token: z.string().min(10) }).safeParse(request.body);
    if (!body.success) {
      return reply.code(400).send({ ok: false, error: { code: "VALIDATION", message: "Токен слишком короткий" } });
    }
    setZenMoneyToken(db, config.secret, body.data.token.trim());
    return { ok: true };
  });

  app.delete("/api/zenmoney/token", { preHandler: guard }, async () => {
    deleteSetting(db, "zenmoney.token");
    return { ok: true };
  });

  app.post("/api/zenmoney/sync", { preHandler: guard }, async (_request, reply) => {
    const token = getZenMoneyToken(db, config.secret);
    if (!token) {
      return reply.code(409).send({ ok: false, error: { code: "NO_TOKEN", message: "Сначала задайте токен Zen Money" } });
    }
    try {
      const stats = await syncZenMoney(db, token, config.baseCurrency);
      return { ok: true, data: stats };
    } catch (error) {
      return reply.code(502).send({ ok: false, error: { code: "SYNC_FAILED", message: (error as Error).message } });
    }
  });
}

export function agentTokenInfo(db: DB, config: Config): { configured: boolean; masked: string | null } {
  const token = getZenMoneyToken(db, config.secret);
  return { configured: Boolean(getSetting(db, "zenmoney.token")), masked: token ? maskToken(token) : null };
}
