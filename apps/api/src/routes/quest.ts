import type { FastifyInstance, FastifyRequest } from "fastify";
import { z } from "zod";
import { bearerActor, isAdminCookie } from "../auth.js";
import type { Ctx } from "./api.js";
import { findDeviceByToken, getChild } from "../repo/guard.js";
import {
  addQuestPhoto,
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
  createProposal,
  listProposals,
  priceProposal,
  rejectProposal,
  questPhotos,
  questOverview,
} from "../repo/quest.js";
import { imageUrl, saveImage } from "../uploads.js";

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
        approval: z.enum(["parent", "agent"]).default("parent"),
        photosRequired: z.number().int().min(0).max(10).default(0),
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

  app.get("/api/guard/shop/proposals", { preHandler: parentGuard }, async () => ({
    ok: true,
    data: listProposals(db).map((p) => ({ ...p, photoUrl: imageUrl(config.publicUrl, p.photo_path) })),
  }));

  app.post("/api/guard/shop/proposals/:id/price", { preHandler: parentGuard }, async (request, reply) => {
    const { id } = request.params as { id: string };
    const body = z.object({ priceCoins: z.number().int().min(1).max(100000) }).safeParse(request.body);
    if (!body.success) {
      return reply.code(400).send({ ok: false, error: { code: "VALIDATION", message: "Нужна цена" } });
    }
    try {
      const result = priceProposal(db, id, body.data.priceCoins, "parent");
      return { ok: true, data: result };
    } catch (error) {
      return reply.code(400).send({ ok: false, error: { code: "PRICE_FAILED", message: (error as Error).message } });
    }
  });

  app.post("/api/guard/shop/proposals/:id/reject", { preHandler: parentGuard }, async (request, reply) => {
    const { id } = request.params as { id: string };
    try {
      return { ok: true, data: rejectProposal(db, id, "parent") };
    } catch (error) {
      return reply.code(400).send({ ok: false, error: { code: "REJECT_FAILED", message: (error as Error).message } });
    }
  });

  // --- Device API для ребёнка ---

  app.get("/api/device/quests", { preHandler: deviceGuard }, async (request) => {
    const device = (request as FastifyRequest & { device: NonNullable<ReturnType<typeof deviceFromRequest>> }).device;
    return {
      ok: true,
      data: {
        profile: profileView(db, device.child_id),
        active: withPhotos(db, listQuests(db, { childId: device.child_id, status: "active", limit: 50 })),
        completed: withPhotos(db, listQuests(db, { childId: device.child_id, status: "completed", limit: 50 })),
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

  app.post("/api/device/uploads", { preHandler: deviceGuard }, async (request, reply) => {
    const body = z.object({ imageBase64: z.string().min(10) }).safeParse(request.body);
    if (!body.success) {
      return reply.code(400).send({ ok: false, error: { code: "VALIDATION", message: "Нужно изображение" } });
    }
    try {
      const path = saveImage(config.dataDir, body.data.imageBase64);
      return { ok: true, data: { path, url: imageUrl(config.publicUrl, path) } };
    } catch (error) {
      return reply.code(400).send({ ok: false, error: { code: "UPLOAD_FAILED", message: (error as Error).message } });
    }
  });

  app.post("/api/device/quests/:id/photos", { preHandler: deviceGuard }, async (request, reply) => {
    const device = (request as FastifyRequest & { device: NonNullable<ReturnType<typeof deviceFromRequest>> }).device;
    const { id } = request.params as { id: string };
    const quest = db.prepare("SELECT child_id FROM quests WHERE id = ?").get(id) as { child_id: string } | undefined;
    if (!quest || quest.child_id !== device.child_id) {
      return reply.code(404).send({ ok: false, error: { code: "NOT_FOUND", message: "Задание не найдено" } });
    }
    const body = z.object({ images: z.array(z.string().min(10)).min(1).max(5) }).safeParse(request.body);
    if (!body.success) {
      return reply.code(400).send({ ok: false, error: { code: "VALIDATION", message: "Нужны фото" } });
    }
    const saved = body.data.images.map((image) => addQuestPhoto(db, id, saveImage(config.dataDir, image)));
    return { ok: true, data: { photos: saved.map((p) => ({ id: p.id, url: imageUrl(config.publicUrl, p.path) })) } };
  });

  app.post("/api/device/shop/proposals", { preHandler: deviceGuard }, async (request, reply) => {
    const device = (request as FastifyRequest & { device: NonNullable<ReturnType<typeof deviceFromRequest>> }).device;
    const body = z
      .object({
        title: z.string().min(1).max(120),
        description: z.string().max(1000).optional(),
        imageBase64: z.string().min(10).optional(),
      })
      .safeParse(request.body);
    if (!body.success) {
      return reply.code(400).send({ ok: false, error: { code: "VALIDATION", message: body.error.issues[0]?.message } });
    }
    try {
      const photoPath = body.data.imageBase64 ? saveImage(config.dataDir, body.data.imageBase64) : null;
      const proposal = createProposal(db, {
        childId: device.child_id,
        title: body.data.title,
        description: body.data.description ?? null,
        photoPath,
      });
      return { ok: true, data: { ...proposal, photoUrl: imageUrl(config.publicUrl, proposal.photo_path) } };
    } catch (error) {
      return reply.code(400).send({ ok: false, error: { code: "PROPOSAL_FAILED", message: (error as Error).message } });
    }
  });

  app.get("/api/device/shop/proposals", { preHandler: deviceGuard }, async (request) => {
    const device = (request as FastifyRequest & { device: NonNullable<ReturnType<typeof deviceFromRequest>> }).device;
    return {
      ok: true,
      data: {
        proposals: listProposals(db, { childId: device.child_id }).map((p) => ({
          ...p,
          photoUrl: imageUrl(config.publicUrl, p.photo_path),
        })),
      },
    };
  });

  app.get("/api/device/shop", { preHandler: deviceGuard }, async (request) => {
    const device = (request as FastifyRequest & { device: NonNullable<ReturnType<typeof deviceFromRequest>> }).device;
    const pending = listPurchases(db, { childId: device.child_id, status: "pending" });
    return {
      ok: true,
      data: {
        profile: profileView(db, device.child_id),
        items: listShopItems(db, true)
          .filter((item) => item.stock === null || item.stock > 0)
          .map((item) => ({ ...item, photoUrl: imageUrl(config.publicUrl, item.photo_path) })),
        pending,
        proposals: listProposals(db, { childId: device.child_id }).map((proposal) => ({
          ...proposal,
          photoUrl: imageUrl(config.publicUrl, proposal.photo_path),
        })),
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

function withPhotos(db: any, quests: any[]) {
  return quests.map((quest) => ({
    ...quest,
    photos: questPhotos(db, quest.id).map((photo) => ({ id: photo.id, url: `/uploads/${photo.path}` })),
  }));
}
