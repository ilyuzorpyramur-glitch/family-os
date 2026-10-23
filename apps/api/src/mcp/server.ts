import type { FastifyInstance, FastifyReply, FastifyRequest } from "fastify";
import { z } from "zod";
import { bearerActor } from "../auth.js";
import { toMinor } from "../lib/money.js";
import { isValidISODate, monthStartISO, todayISO } from "../lib/time.js";
import { listAccounts } from "../repo/accounts.js";
import { listCategories } from "../repo/categories.js";
import { balanceByCurrency, listTransactions, saveTransaction, summaryByCurrency } from "../repo/transactions.js";
import { getZenMoneyToken } from "../repo/settings.js";
import { syncState, syncZenMoney } from "../zenmoney/sync.js";
import type { Ctx } from "../routes/api.js";

type JsonRpcId = string | number | null;
type JsonRpcRequest = {
  jsonrpc: "2.0";
  id?: JsonRpcId;
  method: string;
  params?: Record<string, unknown>;
};

const TOOLS = [
  {
    name: "accounts_list",
    description: "Список счетов семьи с балансами и валютами.",
    inputSchema: { type: "object", properties: {}, additionalProperties: false },
  },
  {
    name: "money_summary",
    description: "Доходы, расходы и балансы за период. По умолчанию — текущий месяц.",
    inputSchema: {
      type: "object",
      properties: {
        from: { type: "string", description: "YYYY-MM-DD" },
        to: { type: "string", description: "YYYY-MM-DD" },
      },
      additionalProperties: false,
    },
  },
  {
    name: "transactions_list",
    description: "Последние операции с фильтрами. Суммы в валюте операции.",
    inputSchema: {
      type: "object",
      properties: {
        from: { type: "string" },
        to: { type: "string" },
        limit: { type: "integer", minimum: 1, maximum: 200 },
        accountId: { type: "string" },
        categoryId: { type: "string" },
        q: { type: "string", description: "Поиск по контрагенту и комментарию" },
      },
      additionalProperties: false,
    },
  },
  {
    name: "categories_list",
    description: "Категории расходов и доходов.",
    inputSchema: { type: "object", properties: {}, additionalProperties: false },
  },
  {
    name: "add_transaction",
    description: "Добавить операцию. Расход указывается положительной суммой. Требует admin- или agent-токен.",
    inputSchema: {
      type: "object",
      properties: {
        amount: { type: "number", description: "Сумма в единицах валюты, например 12.50" },
        currency: { type: "string", description: "ISO-код, по умолчанию валюта счёта" },
        accountId: { type: "string" },
        categoryId: { type: "string" },
        kind: { type: "string", enum: ["expense", "income", "transfer"] },
        counterAccountId: { type: "string", description: "Для перевода" },
        date: { type: "string", description: "YYYY-MM-DD, по умолчанию сегодня" },
        payee: { type: "string" },
        comment: { type: "string" },
      },
      required: ["amount", "accountId"],
      additionalProperties: false,
    },
  },
  {
    name: "sync_now",
    description: "Синхронизировать счета и операции с Zen Money.",
    inputSchema: { type: "object", properties: {}, additionalProperties: false },
  },
];

const addSchema = z.object({
  amount: z.number().positive(),
  currency: z.string().length(3).optional(),
  accountId: z.string().min(1),
  categoryId: z.string().min(1).optional(),
  kind: z.enum(["expense", "income", "transfer"]).default("expense"),
  counterAccountId: z.string().min(1).optional(),
  date: z.string().optional(),
  payee: z.string().max(200).optional(),
  comment: z.string().max(1000).optional(),
});

function ok(id: JsonRpcId, result: unknown) {
  return { jsonrpc: "2.0", id, result };
}

function fail(id: JsonRpcId, code: number, message: string) {
  return { jsonrpc: "2.0", id, error: { code, message } };
}

export function registerMcp(app: FastifyInstance, ctx: Ctx): void {
  const { config, db } = ctx;

  const auth = (request: FastifyRequest, reply: FastifyReply): boolean => {
    if (!bearerActor(request, config)) {
      reply.code(401).send({ jsonrpc: "2.0", id: null, error: { code: -32001, message: "UNAUTHORIZED" } });
      return false;
    }
    return true;
  };

  app.post("/mcp", async (request, reply) => {
    if (!auth(request, reply)) return;
    const body = request.body as JsonRpcRequest;

    if (!body || body.jsonrpc !== "2.0" || typeof body.method !== "string") {
      return reply.code(400).send(fail(null, -32600, "Invalid Request"));
    }

    if (body.method === "notifications/initialized" || body.method === "notifications/cancelled") {
      return reply.code(204).send();
    }

    if (body.method === "initialize") {
      const protocolVersion = (body.params?.protocolVersion as string) ?? "2025-06-18";
      return reply.send(
        ok(body.id ?? null, {
          protocolVersion,
          capabilities: { tools: { listChanged: false } },
          serverInfo: { name: "family-os", version: "0.1.0" },
          instructions:
            "Family OS: семейные финансы. Суммы в валюте операции. Для записи используйте add_transaction; все вызовы аудируются.",
        }),
      );
    }

    if (body.method === "ping") return reply.send(ok(body.id ?? null, {}));

    if (body.method === "tools/list") return reply.send(ok(body.id ?? null, { tools: TOOLS }));

    if (body.method === "tools/call") {
      const name = body.params?.name as string;
      const args = (body.params?.arguments ?? {}) as Record<string, unknown>;
      const started = Date.now();

      try {
        const result = await callTool(name, args, ctx);
        db.prepare("INSERT INTO agent_calls (tool, args, result, ok, duration_ms) VALUES (?, ?, ?, 1, ?)").run(
          name,
          JSON.stringify(args),
          JSON.stringify(result).slice(0, 4000),
          Date.now() - started,
        );
        return reply.send(
          ok(body.id ?? null, {
            content: [{ type: "text", text: JSON.stringify(result, null, 2) }],
            structuredContent: result,
          }),
        );
      } catch (error) {
        db.prepare("INSERT INTO agent_calls (tool, args, result, ok, duration_ms) VALUES (?, ?, ?, 0, ?)").run(
          name,
          JSON.stringify(args),
          (error as Error).message.slice(0, 1000),
          Date.now() - started,
        );
        return reply.send(
          ok(body.id ?? null, {
            content: [{ type: "text", text: `Ошибка: ${(error as Error).message}` }],
            isError: true,
          }),
        );
      }
    }

    return reply.code(404).send(fail(body.id ?? null, -32601, `Method not found: ${body.method}`));
  });

  app.get("/mcp", async (request, reply) => {
    if (!auth(request, reply)) return;
    return reply.send({
      ok: true,
      service: "family-os",
      transport: "streamable-http",
      endpoint: "/mcp",
      tools: TOOLS.map((t) => t.name),
      sync: syncState(db),
    });
  });

  async function callTool(name: string, args: Record<string, unknown>, context: Ctx): Promise<unknown> {
    const { db, config } = context;
    switch (name) {
      case "accounts_list": {
        const accounts = listAccounts(db, true);
        return {
          accounts: accounts.map((a) => ({
            id: a.id,
            title: a.title,
            type: a.type,
            currency: a.currency,
            balance: a.balance_minor / 100,
            archived: Boolean(a.archived),
          })),
        };
      }

      case "money_summary": {
        const from = typeof args.from === "string" && isValidISODate(args.from) ? args.from : monthStartISO();
        const to = typeof args.to === "string" && isValidISODate(args.to) ? args.to : todayISO();
        const byCurrency = summaryByCurrency(db, from, to).map((s) => ({
          currency: s.currency,
          income: s.income / 100,
          expense: s.expense / 100,
          net: s.net / 100,
        }));
        return {
          from,
          to,
          byCurrency,
          balances: balanceByCurrency(db).map((b) => ({ currency: b.currency, balance: b.balance / 100 })),
          asOf: new Date().toISOString(),
        };
      }

      case "transactions_list": {
        const rows = listTransactions(db, {
          from: args.from as string | undefined,
          to: args.to as string | undefined,
          limit: typeof args.limit === "number" ? args.limit : 50,
          accountId: args.accountId as string | undefined,
          categoryId: args.categoryId as string | undefined,
          q: args.q as string | undefined,
        });
        return {
          transactions: rows.map((t) => ({
            id: t.id,
            date: t.occurred_at,
            amount: t.amount_minor / 100,
            currency: t.currency,
            kind: t.kind,
            account: t.account_title,
            category: t.category_title,
            payee: t.payee,
            comment: t.comment,
          })),
        };
      }

      case "categories_list":
        return {
          categories: listCategories(db).map((c) => ({ id: c.id, title: c.title, kind: c.kind })),
        };

      case "add_transaction": {
        const parsed = addSchema.safeParse(args);
        if (!parsed.success) throw new Error(parsed.error.issues[0]?.message ?? "Некорректные аргументы");
        const input = parsed.data;
        const account = listAccounts(db, true).find((a) => a.id === input.accountId);
        if (!account) throw new Error("Счёт не найден");
        const date = input.date ?? todayISO();
        if (!isValidISODate(date)) throw new Error("Дата должна быть YYYY-MM-DD");
        const raw = toMinor(input.amount);
        const tx = saveTransaction(db, {
          date,
          amountMinor: input.kind === "income" ? Math.abs(raw) : -Math.abs(raw),
          currency: input.currency ?? account.currency,
          accountId: input.accountId,
          counterAccountId: input.counterAccountId,
          categoryId: input.categoryId ?? null,
          kind: input.kind,
          payee: input.payee ?? null,
          comment: input.comment ?? null,
          source: "agent",
          adjustBalance: true,
        });
        return { id: tx.id, date: tx.occurred_at, amount: tx.amount_minor / 100, currency: tx.currency, kind: tx.kind };
      }

      case "sync_now": {
        const token = getZenMoneyToken(db, config.secret);
        if (!token) throw new Error("Zen Money не подключён: нет токена");
        const stats = await syncZenMoney(db, token, config.baseCurrency);
        return stats;
      }

      default:
        throw new Error(`Неизвестный инструмент: ${name}`);
    }
  }
}
