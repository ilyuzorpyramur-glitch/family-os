import type { FastifyInstance } from "fastify";
import { isAdminCookie, sessionValue } from "../auth.js";
import { safeEqual } from "../lib/crypto.js";
import { isValidISODate, monthStartISO, todayISO } from "../lib/time.js";
import { listAccounts } from "../repo/accounts.js";
import { balanceByCurrency, listTransactions, summaryByCurrency } from "../repo/transactions.js";
import { deleteSetting, getSetting, getZenMoneyToken, maskToken, setZenMoneyToken } from "../repo/settings.js";
import { syncState, syncZenMoney } from "../zenmoney/sync.js";
import { dashboardPage, deviceTokenPage, docsPage, guardPage, loginPage, questsPage, settingsPage } from "../web/pages.js";
import type { Ctx } from "./api.js";
import {
  createChild,
  createDevice,
  decideExtensionRequest,
  getChild,
  guardOverview,
  listEvents,
  updatePolicy,
} from "../repo/guard.js";
import {
  createQuest,
  createShopItem,
  decidePurchase,
  decideQuest,
  questOverview,
} from "../repo/quest.js";

const MONTHS = [
  "январь",
  "февраль",
  "март",
  "апрель",
  "май",
  "июнь",
  "июль",
  "август",
  "сентябрь",
  "октябрь",
  "ноябрь",
  "декабрь",
];

export function registerWeb(app: FastifyInstance, ctx: Ctx): void {
  const { config, db } = ctx;

  const setSession = (reply: Parameters<FastifyInstance["get"]>[1] extends never ? never : any) => {
    reply.setCookie("fo_session", sessionValue(config), {
      path: "/",
      httpOnly: true,
      sameSite: "lax",
      secure: config.publicUrl.startsWith("https://"),
      maxAge: 60 * 60 * 24 * 30,
    });
  };

  const requirePage = async (request: any, reply: any) => {
    if (!isAdminCookie(request, config)) return reply.redirect("/login");
  };

  app.get("/", async (_request, reply) => reply.redirect(isAdminCookie(_request, config) ? "/app" : "/login"));

  app.get("/login", async (_request, reply) => {
    reply.type("text/html; charset=utf-8");
    return loginPage();
  });

  app.post("/login", async (request, reply) => {
    const body = (request.body ?? {}) as { token?: string };
    const token = (body.token ?? "").trim();
    if (!token || !safeEqual(token, config.adminToken)) {
      reply.type("text/html; charset=utf-8");
      return reply.code(401).send(loginPage("Неверный токен"));
    }
    setSession(reply);
    return reply.redirect("/app");
  });

  app.post("/logout", async (_request, reply) => {
    reply.clearCookie("fo_session", { path: "/" });
    return reply.redirect("/login");
  });

  app.get("/app", { preHandler: requirePage }, async (request, reply) => {
    const q = request.query as Record<string, string | undefined>;
    const now = new Date();
    const from = q.from && isValidISODate(q.from) ? q.from : monthStartISO(now);
    const to = q.to && isValidISODate(q.to) ? q.to : todayISO();

    const sync = syncState(db);
    const token = getZenMoneyToken(db, config.secret);

    reply.type("text/html; charset=utf-8");
    return dashboardPage({
      balances: balanceByCurrency(db),
      summary: summaryByCurrency(db, from, to),
      accounts: listAccounts(db),
      transactions: listTransactions(db, { from, to, limit: 60 }),
      from,
      to,
      monthLabel: `${MONTHS[now.getMonth()]} ${now.getFullYear()}`,
      lastSyncAt: sync.lastSyncAt,
      lastError: sync.lastError,
      hasToken: Boolean(token),
      flash: q.flash ?? null,
    });
  });

  app.post("/app/sync", { preHandler: requirePage }, async (_request, reply) => {
    const token = getZenMoneyToken(db, config.secret);
    if (!token) return reply.redirect("/app/settings?flash=err:Сначала%20добавьте%20токен");
    try {
      const stats = await syncZenMoney(db, token, config.baseCurrency);
      return reply.redirect(`/app?flash=${encodeURIComponent(`Синхронизировано: ${stats.transactions} операций, ${stats.accounts} счетов`)}`);
    } catch (error) {
      return reply.redirect(`/app?flash=${encodeURIComponent(`err:${(error as Error).message}`)}`);
    }
  });

  app.get("/app/settings", { preHandler: requirePage }, async (request, reply) => {
    const q = request.query as Record<string, string | undefined>;
    const sync = syncState(db);
    const hasToken = Boolean(getSetting(db, "zenmoney.token"));
    const token = getZenMoneyToken(db, config.secret);

    reply.type("text/html; charset=utf-8");
    return settingsPage({
      hasToken,
      tokenMasked: token ? maskToken(token) : null,
      lastSyncAt: sync.lastSyncAt,
      lastError: sync.lastError,
      flash: q.flash ?? null,
      agentTokenConfigured: Boolean(config.agentToken),
      publicUrl: config.publicUrl.replace(/\/$/, ""),
    });
  });

  app.post("/app/settings/token", { preHandler: requirePage }, async (request, reply) => {
    const body = (request.body ?? {}) as { token?: string };
    const token = (body.token ?? "").trim();
    if (token.length < 10) return reply.redirect(`/app/settings?flash=${encodeURIComponent("err:Токен слишком короткий")}`);
    setZenMoneyToken(db, config.secret, token);
    return reply.redirect("/app/settings?flash=token");
  });

  app.post("/app/settings/token/delete", { preHandler: requirePage }, async (_request, reply) => {
    deleteSetting(db, "zenmoney.token");
    return reply.redirect("/app/settings?flash=token_deleted");
  });

  app.get("/app/guard", { preHandler: requirePage }, async (request, reply) => {
    const q = request.query as Record<string, string | undefined>;
    reply.type("text/html; charset=utf-8");
    return guardPage(guardOverview(db), listEvents(db, undefined, 30), q.flash ?? null);
  });

  app.post("/app/guard/children", { preHandler: requirePage }, async (request, reply) => {
    const body = (request.body ?? {}) as { name?: string };
    const name = (body.name ?? "").trim();
    if (!name) return reply.redirect(`/app/guard?flash=${encodeURIComponent("err:Введите имя")}`);
    createChild(db, name);
    return reply.redirect("/app/guard?flash=guard_child");
  });

  app.post("/app/guard/children/:id/policy", { preHandler: requirePage }, async (request, reply) => {
    const { id } = request.params as { id: string };
    const body = (request.body ?? {}) as Record<string, string | undefined>;
    if (!getChild(db, id)) return reply.redirect(`/app/guard?flash=${encodeURIComponent("err:Ребёнок не найден")}`);

    const limit = Number(body.screenLimitMin);
    const timeRe = /^\d{2}:\d{2}$/;
    updatePolicy(db, id, {
      screenLimitMin: Number.isFinite(limit) ? Math.max(0, Math.min(1440, Math.round(limit))) : undefined,
      bedtimeStart: body.bedtimeStart && timeRe.test(body.bedtimeStart) ? body.bedtimeStart : undefined,
      bedtimeEnd: body.bedtimeEnd && timeRe.test(body.bedtimeEnd) ? body.bedtimeEnd : undefined,
    });
    return reply.redirect("/app/guard?flash=guard_policy");
  });

  app.post("/app/guard/children/:id/devices", { preHandler: requirePage }, async (request, reply) => {
    const { id } = request.params as { id: string };
    const body = (request.body ?? {}) as { name?: string };
    const child = getChild(db, id);
    if (!child) return reply.redirect(`/app/guard?flash=${encodeURIComponent("err:Ребёнок не найден")}`);
    const name = (body.name ?? "Телефон").trim() || "Телефон";
    const created = createDevice(db, id, name);
    reply.type("text/html; charset=utf-8");
    return deviceTokenPage(child.name, created.device.name, created.token);
  });

  app.post("/app/guard/requests/:id/decide", { preHandler: requirePage }, async (request, reply) => {
    const { id } = request.params as { id: string };
    const body = (request.body ?? {}) as { approve?: string };
    const approve = body.approve === "yes";
    try {
      decideExtensionRequest(db, id, approve, "parent");
      return reply.redirect(`/app/guard?flash=${approve ? "guard_approved" : "guard_denied"}`);
    } catch (error) {
      return reply.redirect(`/app/guard?flash=${encodeURIComponent(`err:${(error as Error).message}`)}`);
    }
  });

  app.get("/app/quests", { preHandler: requirePage }, async (request, reply) => {
    const q = request.query as Record<string, string | undefined>;
    reply.type("text/html; charset=utf-8");
    return questsPage(questOverview(db), q.flash ?? null);
  });

  app.post("/app/quests/children/:id/create", { preHandler: requirePage }, async (request, reply) => {
    const { id } = request.params as { id: string };
    const body = (request.body ?? {}) as Record<string, string | undefined>;
    const title = (body.title ?? "").trim();
    if (!title) return reply.redirect(`/app/quests?flash=${encodeURIComponent("err:Введите название задания")}`);
    const xp = Number(body.xp);
    const coins = Number(body.coins);
    const stat = body.stat && body.stat.length > 0 ? body.stat : undefined;
    createQuest(db, {
      childId: id,
      title,
      xp: Number.isFinite(xp) ? xp : 10,
      coins: Number.isFinite(coins) ? coins : 5,
      stat: stat as "strength" | "intellect" | "creativity" | "kindness" | "discipline" | undefined,
      createdBy: "parent",
    });
    return reply.redirect("/app/quests?flash=quest_created");
  });

  app.post("/app/quests/:id/decide", { preHandler: requirePage }, async (request, reply) => {
    const { id } = request.params as { id: string };
    const body = (request.body ?? {}) as { approve?: string };
    const approve = body.approve === "yes";
    try {
      decideQuest(db, id, approve, "parent");
      return reply.redirect(`/app/quests?flash=${approve ? "quest_approved" : "quest_denied"}`);
    } catch (error) {
      return reply.redirect(`/app/quests?flash=${encodeURIComponent(`err:${(error as Error).message}`)}`);
    }
  });

  app.post("/app/quests/purchases/:id/decide", { preHandler: requirePage }, async (request, reply) => {
    const { id } = request.params as { id: string };
    const body = (request.body ?? {}) as { approve?: string };
    const approve = body.approve === "yes";
    try {
      decidePurchase(db, id, approve, "parent");
      return reply.redirect(`/app/quests?flash=${approve ? "purchase_approved" : "purchase_denied"}`);
    } catch (error) {
      return reply.redirect(`/app/quests?flash=${encodeURIComponent(`err:${(error as Error).message}`)}`);
    }
  });

  app.post("/app/quests/shop/items", { preHandler: requirePage }, async (request, reply) => {
    const body = (request.body ?? {}) as Record<string, string | undefined>;
    const title = (body.title ?? "").trim();
    const price = Number(body.priceCoins);
    if (!title || !Number.isFinite(price) || price < 1) {
      return reply.redirect(`/app/quests?flash=${encodeURIComponent("err:Название и цена обязательны")}`);
    }
    const stockRaw = (body.stock ?? "").trim();
    const stock = stockRaw === "" ? null : Math.max(0, Math.round(Number(stockRaw)));
    createShopItem(db, {
      title,
      priceCoins: price,
      kind: (body.kind as "privilege" | "money" | "screen_time" | "custom") ?? "privilege",
      stock: Number.isFinite(stock ?? NaN) ? stock : null,
      createdBy: "parent",
    });
    return reply.redirect("/app/quests?flash=shop_item");
  });

  app.get("/docs", { preHandler: requirePage }, async (_request, reply) => {
    reply.type("text/html; charset=utf-8");
    return docsPage(config.publicUrl.replace(/\/$/, ""));
  });

  // Небольшая страница 404 вместо JSON — для браузера.
  app.setNotFoundHandler((request, reply) => {
    if (request.url.startsWith("/api/") || request.url.startsWith("/mcp")) {
      return reply.code(404).send({ ok: false, error: { code: "NOT_FOUND", message: "Маршрут не найден" } });
    }
    return reply.code(404).type("text/html; charset=utf-8").send(
      `<body style="background:#0b0f14;color:#e6edf3;font-family:sans-serif;padding:40px">
        <h1>404</h1><p>Страница не найдена. <a style="color:#60a5fa" href="/app">На главную</a></p></body>`,
    );
  });
}
