import type { FastifyInstance, FastifyRequest } from "fastify";
import { randomBytes, createHash } from "node:crypto";
import { z } from "zod";
import { bearerActor, isAdminCookie } from "../auth.js";
import { getSetting, maskToken, setSetting } from "../repo/settings.js";
import { encrypt, decrypt } from "../lib/crypto.js";
import { listAccounts } from "../repo/accounts.js";
import { saveTransaction } from "../repo/transactions.js";
import type { Ctx } from "./api.js";
import { isValidISODate, todayISO } from "../lib/time.js";

type BankTx = {
  date: string;
  amount: number;
  payee?: string;
  comment?: string;
  currency?: string;
  accountId?: string;
  externalId?: string;
};

function bankTokenFrom(db: Ctx["db"], secret: string): string | null {
  const stored = getSetting(db, "bank.token");
  if (!stored) return null;
  try {
    return decrypt(stored, secret);
  } catch {
    return null;
  }
}

export function registerBankApi(app: FastifyInstance, ctx: Ctx): void {
  const { config, db } = ctx;

  const parentGuard = async (request: FastifyRequest, reply: any) => {
    if (!bearerActor(request, config) && !isAdminCookie(request, config)) {
      return reply.code(401).send({ ok: false, error: { code: "UNAUTHORIZED", message: "Нужен токен родителя" } });
    }
  };

  app.get("/api/bank/status", { preHandler: parentGuard }, async () => {
    const token = bankTokenFrom(db, config.secret);
    return {
      ok: true,
      data: {
        connected: Boolean(token),
        masked: token ? maskToken(token) : null,
        importUrl: `${config.publicUrl || ""}/api/bank/import`,
      },
    };
  });

  app.post("/api/bank/token", { preHandler: parentGuard }, async () => {
    const token = `bank_${randomBytes(24).toString("base64url")}`;
    setSetting(db, "bank.token", encrypt(token, config.secret));
    setSetting(db, "bank.token_at", new Date().toISOString());
    return { ok: true, data: { token, importUrl: `${config.publicUrl || ""}/api/bank/import` } };
  });

  app.delete("/api/bank/token", { preHandler: parentGuard }, async () => {
    setSetting(db, "bank.token", "");
    return { ok: true };
  });

  /** Приём операций от агрегатора/скрипта синхронизации банка. */
  app.post("/api/bank/import", async (request, reply) => {
    const header = request.headers.authorization ?? "";
    const key = header.startsWith("Bearer ") ? header.substring(7).trim() : "";
    const expected = bankTokenFrom(db, config.secret);
    if (!expected || !key || key !== expected) {
      return reply.code(401).send({ ok: false, error: { code: "UNAUTHORIZED", message: "Неверный токен банка" } });
    }

    const body = z
      .object({
        transactions: z
          .array(
            z.object({
              date: z.string(),
              amount: z.number(),
              payee: z.string().max(200).optional(),
              comment: z.string().max(500).optional(),
              currency: z.string().length(3).optional(),
              accountId: z.string().optional(),
              externalId: z.string().max(200).optional(),
            }),
          )
          .min(1)
          .max(500),
        accountId: z.string().optional(),
      })
      .safeParse(request.body);
    if (!body.success) {
      return reply.code(400).send({ ok: false, error: { code: "VALIDATION", message: body.error.issues[0]?.message } });
    }

    const accounts = listAccounts(db, true).filter((a) => a.archived === 0);
    const fallback = accounts[0];
    if (!fallback) return reply.code(400).send({ ok: false, error: { code: "NO_ACCOUNTS", message: "Нет счетов" } });

    let imported = 0;
    const errors: string[] = [];
    for (const tx of body.data.transactions as BankTx[]) {
      const account =
        accounts.find((a) => a.id === (tx.accountId ?? body.data.accountId)) ??
        accounts.find((a) => a.currency === (tx.currency ?? fallback.currency)) ??
        fallback;
      if (!isValidISODate(tx.date) || !Number.isFinite(tx.amount) || tx.amount === 0) {
        errors.push(`Пропущено: ${tx.date} ${tx.amount}`);
        continue;
      }
      const externalId =
        tx.externalId ??
        `bank:${createHash("sha1")
          .update(`${account.id}|${tx.date}|${tx.amount}|${tx.payee ?? ""}|${tx.comment ?? ""}`)
          .digest("hex")}`;
      try {
        saveTransaction(db, {
          date: tx.date,
          amountMinor: Math.round(tx.amount * 100),
          currency: tx.currency ?? account.currency,
          accountId: account.id,
          kind: tx.amount > 0 ? "income" : "expense",
          payee: tx.payee ?? null,
          comment: tx.comment ?? null,
          externalId,
          changed: Date.now(),
          source: "bank",
          adjustBalance: true,
        });
        imported += 1;
      } catch (error) {
        errors.push((error as Error).message);
      }
    }
    setSetting(db, "bank.last_sync_at", new Date().toISOString());
    return { ok: true, data: { imported, skipped: errors.length, errors: errors.slice(0, 5) } };
  });

  /** Импорт выписки CSV: дата;сумма;контрагент;комментарий */
  app.post("/api/money/import/csv", { preHandler: parentGuard }, async (request, reply) => {
    const body = z
      .object({ csv: z.string().min(3).max(200_000), accountId: z.string().optional() })
      .safeParse(request.body);
    if (!body.success) {
      return reply.code(400).send({ ok: false, error: { code: "VALIDATION", message: "Нужен CSV-текст" } });
    }
    const accounts = listAccounts(db, true).filter((a) => a.archived === 0);
    const account = accounts.find((a) => a.id === body.data.accountId) ?? accounts[0];
    if (!account) return reply.code(400).send({ ok: false, error: { code: "NO_ACCOUNTS", message: "Нет счетов" } });

    let imported = 0;
    let skipped = 0;
    const lines = body.data.csv.split(/\r?\n/).filter((line) => line.trim().length > 0);
    for (const line of lines) {
      // Разделитель: ; или таб. Одиночная запятая может быть десятичным разделителем.
      const parts = (line.includes(";") || line.includes("\t") ? line.split(/[;\t]/) : line.split(",")).map((part) =>
        part.trim(),
      );
      if (parts.length < 2) {
        skipped += 1;
        continue;
      }
      const date = parts[0] ?? "";
      const rawAmount = (parts[1] ?? "0").replace(/\s/g, "").replace(",", ".");
      const amount = Number(rawAmount);
      if (!isValidISODate(date) || !Number.isFinite(amount) || amount === 0) {
        skipped += 1;
        continue;
      }
      const payee = parts[2] ?? null;
      const comment = parts[3] ?? null;
      const externalId = `csv:${createHash("sha1").update(`${account.id}|${line}`).digest("hex")}`;
      try {
        saveTransaction(db, {
          date,
          amountMinor: Math.round(amount * 100),
          currency: account.currency,
          accountId: account.id,
          kind: amount > 0 ? "income" : "expense",
          payee,
          comment,
          externalId,
          changed: Date.now(),
          source: "import",
          adjustBalance: true,
        });
        imported += 1;
      } catch {
        skipped += 1;
      }
    }
    return { ok: true, data: { imported, skipped, date: todayISO() } };
  });
}
