import type { FastifyInstance, FastifyRequest } from "fastify";
import { z } from "zod";
import { bearerActor, isAdminCookie } from "../auth.js";
import { createDevice, findDeviceByToken, getChild, listChildren } from "../repo/guard.js";
import { createPairCode, getPairCode, isPairUsable, markPairUsed, pairPayload, parsePairPayload, purgePairCodes } from "../repo/pairing.js";
import { addMeal, deleteMeal, listMeals, nutritionDay, localDate } from "../repo/nutrition.js";
import type { Ctx } from "./api.js";

function deviceFromRequest(request: FastifyRequest, ctx: Ctx) {
  const header = request.headers.authorization ?? "";
  if (!header.startsWith("Bearer ")) return null;
  const key = header.substring(7).trim();
  if (!key) return null;
  return findDeviceByToken(ctx.db, key);
}

/** Админ-токен, выданный по QR-сопряжению, живёт в том же поле, что и обычный. */
export function registerPairApi(app: FastifyInstance, ctx: Ctx): void {
  const { config, db } = ctx;

  const parentGuard = async (request: FastifyRequest, reply: any) => {
    if (!bearerActor(request, config) && !isAdminCookie(request, config)) {
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

  /** Родитель создаёт QR для подключения детского устройства. */
  app.post("/api/guard/pair/device", { preHandler: parentGuard }, async (request, reply) => {
    const body = z.object({ childId: z.string().min(1) }).safeParse(request.body);
    if (!body.success) {
      return reply.code(400).send({ ok: false, error: { code: "VALIDATION", message: "Нужен ребёнок" } });
    }
    const child = getChild(db, body.data.childId);
    if (!child) return reply.code(404).send({ ok: false, error: { code: "NOT_FOUND", message: "Ребёнок не найден" } });

    purgePairCodes(db);
    const pair = createPairCode(db, { kind: "device", childId: child.id });
    return {
      ok: true,
      data: {
        code: pair.code,
        expiresAt: pair.expires_at,
        payload: pairPayload(config.publicUrl, pair.code),
        childName: child.name,
      },
    };
  });

  /** Родительский вход по QR: код выдаёт панель, приложение обменивает на токен. */
  app.post("/api/guard/pair/parent", { preHandler: parentGuard }, async () => {
    purgePairCodes(db);
    const pair = createPairCode(db, { kind: "parent", ttlMinutes: 10 });
    return {
      ok: true,
      data: { code: pair.code, expiresAt: pair.expires_at, payload: pairPayload(config.publicUrl, pair.code) },
    };
  });

  /** Устройство или приложение обменивает код на токен. Без авторизации. */
  app.post("/api/pair/claim", async (request, reply) => {
    const body = z
      .object({ code: z.string().min(4).max(64), deviceName: z.string().max(64).optional(), url: z.string().optional() })
      .safeParse(request.body);
    if (!body.success) {
      return reply.code(400).send({ ok: false, error: { code: "VALIDATION", message: "Нужен код" } });
    }
    const parsed = parsePairPayload(body.data.code);
    const code = parsed.code ?? body.data.code.trim().toUpperCase();
    const pair = getPairCode(db, code);
    if (!isPairUsable(pair)) {
      return reply.code(400).send({ ok: false, error: { code: "PAIR_INVALID", message: "Код неверный или истёк" } });
    }
    markPairUsed(db, code);

    if (pair!.kind === "parent") {
      return {
        ok: true,
        data: { role: "parent", token: config.adminToken, serverUrl: config.publicUrl },
      };
    }

    const child = pair!.child_id ? getChild(db, pair!.child_id) : undefined;
    if (!child) {
      return reply.code(404).send({ ok: false, error: { code: "NOT_FOUND", message: "Ребёнок не найден" } });
    }
    const created = createDevice(db, child.id, body.data.deviceName?.trim() || child.name, "android");
    return {
      ok: true,
      data: {
        role: "child",
        token: created.token,
        deviceId: created.device.id,
        serverUrl: config.publicUrl,
        childId: child.id,
        childName: child.name,
      },
    };
  });

  /** Детское приложение: проверить, что QR ведёт на этот сервер. */
  app.get("/api/pair/info", async () => ({
    ok: true,
    data: { service: "family-os", url: config.publicUrl, version: "0.1.0" },
  }));

  // --- Калории ---

  app.get("/api/device/nutrition", { preHandler: deviceGuard }, async (request) => {
    const device = (request as FastifyRequest & { device: NonNullable<ReturnType<typeof deviceFromRequest>> }).device;
    const query = request.query as { date?: string };
    const child = getChild(db, device.child_id);
    const target = (child as unknown as { kcal_target?: number })?.kcal_target ?? 2000;
    return { ok: true, data: nutritionDay(db, device.child_id, query.date, target) };
  });

  app.post("/api/device/meals", { preHandler: deviceGuard }, async (request, reply) => {
    const device = (request as FastifyRequest & { device: NonNullable<ReturnType<typeof deviceFromRequest>> }).device;
    const body = z
      .object({
        title: z.string().min(1).max(120),
        kcal: z.number().min(0).max(10000),
        grams: z.number().min(0).max(5000).nullable().optional(),
        source: z.string().max(32).optional(),
      })
      .safeParse(request.body);
    if (!body.success) {
      return reply.code(400).send({ ok: false, error: { code: "VALIDATION", message: "Проверьте название и калории" } });
    }
    const meal = addMeal(db, { childId: device.child_id, ...body.data });
    const child = getChild(db, device.child_id);
    const target = (child as unknown as { kcal_target?: number })?.kcal_target ?? 2000;
    return { ok: true, data: { meal, day: nutritionDay(db, device.child_id, meal.date, target) } };
  });

  app.delete("/api/device/meals/:id", { preHandler: deviceGuard }, async (request, reply) => {
    const device = (request as FastifyRequest & { device: NonNullable<ReturnType<typeof deviceFromRequest>> }).device;
    const { id } = request.params as { id: string };
    const meal = db.prepare("SELECT * FROM meals WHERE id = ?").get(id) as { child_id: string } | undefined;
    if (!meal || meal.child_id !== device.child_id) {
      return reply.code(404).send({ ok: false, error: { code: "NOT_FOUND", message: "Запись не найдена" } });
    }
    deleteMeal(db, id);
    const child = getChild(db, device.child_id);
    const target = (child as unknown as { kcal_target?: number })?.kcal_target ?? 2000;
    return { ok: true, data: nutritionDay(db, device.child_id, localDate(), target) };
  });

  app.get("/api/guard/children/:id/nutrition", { preHandler: parentGuard }, async (request, reply) => {
    const { id } = request.params as { id: string };
    const child = getChild(db, id);
    if (!child) return reply.code(404).send({ ok: false, error: { code: "NOT_FOUND", message: "Ребёнок не найден" } });
    const query = request.query as { date?: string };
    const target = (child as unknown as { kcal_target?: number })?.kcal_target ?? 2000;
    return { ok: true, data: nutritionDay(db, id, query.date, target) };
  });

  app.post("/api/guard/children/:id/meals", { preHandler: parentGuard }, async (request, reply) => {
    const { id } = request.params as { id: string };
    const child = getChild(db, id);
    if (!child) return reply.code(404).send({ ok: false, error: { code: "NOT_FOUND", message: "Ребёнок не найден" } });
    const body = z
      .object({
        title: z.string().min(1).max(120),
        kcal: z.number().min(0).max(10000),
        grams: z.number().min(0).max(5000).nullable().optional(),
        date: z.string().max(10).optional(),
      })
      .safeParse(request.body);
    if (!body.success) {
      return reply.code(400).send({ ok: false, error: { code: "VALIDATION", message: "Проверьте название и калории" } });
    }
    const meal = addMeal(db, { childId: id, ...body.data, source: "parent" });
    const target = (child as unknown as { kcal_target?: number })?.kcal_target ?? 2000;
    return { ok: true, data: nutritionDay(db, id, meal.date, target) };
  });

  app.patch("/api/guard/children/:id/nutrition-target", { preHandler: parentGuard }, async (request, reply) => {
    const { id } = request.params as { id: string };
    if (!getChild(db, id)) return reply.code(404).send({ ok: false, error: { code: "NOT_FOUND", message: "Ребёнок не найден" } });
    const body = z.object({ kcalTarget: z.number().int().min(500).max(6000) }).safeParse(request.body);
    if (!body.success) return reply.code(400).send({ ok: false, error: { code: "VALIDATION", message: "Норма 500–6000" } });
    db.prepare("UPDATE children SET kcal_target = ? WHERE id = ?").run(body.data.kcalTarget, id);
    return { ok: true, data: { kcalTarget: body.data.kcalTarget } };
  });

  /** Сводка по всем детям для родительской вкладки. */
  app.get("/api/guard/nutrition", { preHandler: parentGuard }, async (request) => {
    const query = request.query as { date?: string };
    const date = query.date ?? localDate();
    return {
      ok: true,
      data: {
        date,
        children: listChildren(db).map((child) => {
          const target = (child as unknown as { kcal_target?: number })?.kcal_target ?? 2000;
          return { childId: child.id, name: child.name, ...nutritionDay(db, child.id, date, target) };
        }),
      },
    };
  });
}
