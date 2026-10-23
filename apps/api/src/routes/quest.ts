import type { FastifyInstance, FastifyRequest } from "fastify";
import { z } from "zod";
import { bearerActor, isAdminCookie } from "../auth.js";
import type { Ctx } from "./api.js";
import { findDeviceByToken, getChild } from "../repo/guard.js";
import {
  buyItem,
  completeQuest,
  createQuest,
  createShopItem,
  decidePurchase,
  decideQuest,
  listPurchases,
  listQuests,
  listShopItems,
  profileView,
  questOverview,
} from "../repo/quest.js";

function deviceFromRequest(request: FastifyRequest, ctx: Ctx) {
  const header = request.headers.authorization ?? "";
  if (!header.startsWith("Bearer ")) return null;
  const key = header.substring(7).trim();
  if (!key) return null;
  return findDeviceByToken(ctx.db, key);
}

export function registerQuestApi(app: FastifyInstance, ctx: Ctx): void {
  const { config, db } = ctx;

  const parentGuard = async (request: FastifyRequest, reply: any) => {
    const actor = bearerActor(request, config);
    if (!actor && !isAdminCookie(request, config)) {
      return reply.code(401).send({ ok: false, error: { code: "UNAUTHORIZED", message: "Нужен токен родителя" } });
    }
  };

  const deviceGuard = async (request: FastifyRequest, reply: any) => {
    const device = deviceFromRequest(request, ctx);
    if (!device) {
      return reply.code(401).send({ ok: false, error: { code: "UNAUTHORIZED", message: "Неверный токен устройства" } });
    }
    (request as FastifyRequest & { device?: typeof device }).device = device;
  };

  app.get("/api/guard/quests/overview", { preHandler: parentGuard }, async () => ({ ok: true, data: questOverview(db) }));

  app.post("/api/guard/children/:id/quests", { preHandler: parentGuard }, async (request, reply) => {
    const { id } = request.params as { id: string };
    if (!getChild(db, id)) return reply.code(404).send({ ok: false, error: { code: "NOT_FOUND", message: "Ребёнок не найден" } });

    const body = z
      .object({
        title: z.string().min(1).max(120),
        description: z.string().max(1000).optional(),
        kind: z.enum(["once", "daily", "weekly", "habit"]).default("once"),
        xp: z.number().int().min(0).max(1000).optional(),
        coins: z.number().int().min(0).max(1000).optional(),
        stat: z.enum(["strength", "intellect", "creativity", "kindness", "discipline"]).optional(),
        dueAt: z.string().optional(),
      })
      .safeParse(request.body);

    if (!body.success) {
      return reply.code(400).send({ ok: false, error: { code: "VALIDATION", message: body.error.issues[0]?.message } });
    }

    const author = bearerActor(request, config) === "agent" ? "agent" : "parent";
    const quest = createQuest(db, { childId: id, ...body.data, createdBy: author });
    return { ok: true, data: quest };
  });

  app.post("/api/guard/quests/:id/decide", { preHandler: parentGuard }, async (request, reply) => {
    const { id } = request.params as { id: string };
    const body = z.object({ approve: z.boolean() }).safeParse(request.body);
    if (!body.success) {
      return reply.code(400).send({ ok: false, error: { code: "VALIDATION", message: body.error.issues[0]?.message } });
    }
    try {
      const quest = decideQuest(db, id, body.data.approve, "parent");
      return { ok: true, data: { quest, profile: profileView(db, quest.child_id) } };
    } catch (error) {
      return reply.code(400).send({ ok: false, error: { code: "QUEST_REJECTED", message: (error as Error).message } });
    }
  });

  app.get("/api/guard/shop", { preHandler: parentGuard }, async () => ({
    ok: true,
    data: { items: listShopItems(db, false), purchases: listPurchases(db) },
  }));

  app.post("/api/guard/shop/items", { preHandler: parentGuard }, async (request, reply) => {
    const body = z
      .object({
        title: z.string().min(1).max(120),
        description: z.string().max(500).optional(),
        priceCoins: z.number().int().min(1).max(100000),
        kind: z.enum(["privilege", "money", "screen_time", "custom"]).default("privilege"),
        value: z.string().max(200).optional(),
        stock: z.number().int().min(0).max(10000).nullable().optional(),
      })
      .safeParse(request.body);
    if (!body.success) {
      return reply.code(400).send({ ok: false, error: { code: "VALIDATION", message: body.error.issues[0]?.message } });
    }
    const item = createShopItem(db, {
      ...body.data,
      createdBy: bearerActor(request, config) === "agent" ? "agent" : "parent",
    });
    return { ok: true, data: item };
  });

  app.post("/api/guard/shop/purchases/:id/decide", { preHandler: parentGuard }, async (request, reply) => {
    const { id } = request.params as { id: string };
    const body = z.object({ approve: z.boolean() }).safeParse(request.body);
    if (!body.success) {
      return reply.code(400).send({ ok: false, error: { code: "VALIDATION", message: body.error.issues[0]?.message } });
    }
    try {
      const purchase = decidePurchase(db, id, body.data.approve, "parent");
      return { ok: true, data: { purchase, profile: profileView(db, purchase.child_id) } };
    } catch (error) {
      return reply.code(400).send({ ok: false, error: { code: "PURCHASE_REJECTED", message: (error as Error).message } });
    }
  });

  // --- Device API для ребёнка ---

  app.get("/api/device/quests", { preHandler: deviceGuard }, async (request) => {
    const device = (request as FastifyRequest & { device: NonNullable<ReturnType<typeof deviceFromRequest>> }).device;
    return {
      ok: true,
      data: {
        profile: profileView(db, device.child_id),
        active: listQuests(db, { childId: device.child_id, status: "active", limit: 50 }),
        completed: listQuests(db, { childId: device.child_id, status: "completed", limit: 50 }),
        purchases: listPurchases(db, { childId: device.child_id, limit: 20 }),
      },
    };
  });

  app.post("/api/device/quests/:id/complete", { preHandler: deviceGuard }, async (request, reply) => {
    const device = (request as FastifyRequest & { device: NonNullable<ReturnType<typeof deviceFromRequest>> }).device;
    const { id } = request.params as { id: string };
    const quest = db.prepare("SELECT child_id FROM quests WHERE id = ?").get(id) as { child_id: string } | undefined;
    if (!quest || quest.child_id !== device.child_id) {
      return reply.code(404).send({ ok: false, error: { code: "NOT_FOUND", message: "Задание не найдено" } });
    }
    try {
      return { ok: true, data: completeQuest(db, id, "child") };
    } catch (error) {
      return reply.code(400).send({ ok: false, error: { code: "QUEST_REJECTED", message: (error as Error).message } });
    }
  });

  app.get("/api/device/shop", { preHandler: deviceGuard }, async (request) => {
    const device = (request as FastifyRequest & { device: NonNullable<ReturnType<typeof deviceFromRequest>> }).device;
    const pending = listPurchases(db, { childId: device.child_id, status: "pending" });
    return {
      ok: true,
      data: {
        profile: profileView(db, device.child_id),
        items: listShopItems(db, true).filter((item) => item.stock === null || item.stock > 0),
        pending,
      },
    };
  });

  app.post("/api/device/shop/:id/buy", { preHandler: deviceGuard }, async (request, reply) => {
    const device = (request as FastifyRequest & { device: NonNullable<ReturnType<typeof deviceFromRequest>> }).device;
    const { id } = request.params as { id: string };
    try {
      const purchase = buyItem(db, device.child_id, id);
      return { ok: true, data: purchase };
    } catch (error) {
      return reply.code(400).send({ ok: false, error: { code: "PURCHASE_REJECTED", message: (error as Error).message } });
    }
  });
}
