import type { FastifyInstance, FastifyRequest } from "fastify";
import { z } from "zod";
import { bearerActor, isAdminCookie } from "../auth.js";
import type { Ctx } from "./api.js";
import { localDateISO } from "../lib/time.js";
import {
  addEvent,
  createChild,
  createDevice,
  createExtensionRequest,
  decideExtensionRequest,
  effectiveLimit,
  ensurePolicy,
  findDeviceByToken,
  getChild,
  guardOverview,
  listChildren,
  listDevices,
  listEvents,
  listExtensionRequests,
  recordUsage,
  touchDevice,
  updatePolicy,
  usageForDate,
} from "../repo/guard.js";

function deviceFromRequest(request: FastifyRequest, ctx: Ctx) {
  const header = request.headers.authorization;
  if (!header?.startsWith("Bearer ")) return null;
  const token = (header.split(" ")[1] ?? "").trim();
  if (!token) return null;
  return findDeviceByToken(ctx.db, token) ?? null;
}

export function registerGuardApi(app: FastifyInstance, ctx: Ctx): void {
  const { config, db } = ctx;

  const parentGuard = async (request: FastifyRequest, reply: any) => {
    const actor = bearerActor(request, config);
    if (!actor && !isAdminCookie(request, config)) {
      return reply.code(401).send({ ok: false, error: { code: "UNAUTHORIZED", message: "Нужен токен родителя" } });
    }
  };

  app.get("/api/guard/overview", { preHandler: parentGuard }, async () => ({ ok: true, data: guardOverview(db) }));

  app.get("/api/guard/children", { preHandler: parentGuard }, async () => ({
    ok: true,
    data: listChildren(db).map((c) => ({ ...c, policy: ensurePolicy(db, c.id), devices: listDevices(db, c.id).map((d) => ({ id: d.id, name: d.name, status: d.status, lastSeenAt: d.last_seen_at })) })),
  }));

  app.post("/api/guard/children", { preHandler: parentGuard }, async (request, reply) => {
    const body = z
      .object({ name: z.string().min(1).max(60), birthDate: z.string().optional() })
      .safeParse(request.body);
    if (!body.success) {
      return reply.code(400).send({ ok: false, error: { code: "VALIDATION", message: body.error.issues[0]?.message } });
    }
    return { ok: true, data: createChild(db, body.data.name.trim(), body.data.birthDate ?? null) };
  });

  app.patch("/api/guard/children/:id/policy", { preHandler: parentGuard }, async (request, reply) => {
    const { id } = request.params as { id: string };
    if (!getChild(db, id)) return reply.code(404).send({ ok: false, error: { code: "NOT_FOUND", message: "Ребёнок не найден" } });
    const body = z
      .object({
        screenLimitMin: z.number().int().min(0).max(1440).optional(),
        bedtimeStart: z.string().regex(/^\d{2}:\d{2}$/).optional(),
        bedtimeEnd: z.string().regex(/^\d{2}:\d{2}$/).optional(),
        webFilter: z.boolean().optional(),
        filterLevel: z.enum(["off", "kids", "balanced", "custom"]).optional(),
        allowInstall: z.boolean().optional(),
      })
      .safeParse(request.body);
    if (!body.success) {
      return reply.code(400).send({ ok: false, error: { code: "VALIDATION", message: body.error.issues[0]?.message } });
    }
    return { ok: true, data: updatePolicy(db, id, body.data) };
  });

  app.post("/api/guard/children/:id/devices", { preHandler: parentGuard }, async (request, reply) => {
    const { id } = request.params as { id: string };
    if (!getChild(db, id)) return reply.code(404).send({ ok: false, error: { code: "NOT_FOUND", message: "Ребёнок не найден" } });
    const body = z
      .object({ name: z.string().min(1).max(80), platform: z.enum(["android"]).default("android") })
      .safeParse(request.body);
    if (!body.success) {
      return reply.code(400).send({ ok: false, error: { code: "VALIDATION", message: body.error.issues[0]?.message } });
    }
    const created = createDevice(db, id, body.data.name.trim(), body.data.platform);
    return {
      ok: true,
      data: { device: { id: created.device.id, name: created.device.name, platform: created.device.platform }, token: created.token },
      warning: "Токен показывается один раз. Сохраните его в приложении на устройстве ребёнка.",
    };
  });

  app.get("/api/guard/events", { preHandler: parentGuard }, async (request) => {
    const q = request.query as { childId?: string; limit?: string };
    return { ok: true, data: listEvents(db, q.childId, q.limit ? Number(q.limit) : 50) };
  });

  app.get("/api/guard/requests", { preHandler: parentGuard }, async (request) => {
    const q = request.query as { status?: string };
    return { ok: true, data: listExtensionRequests(db, q.status ?? "pending") };
  });

  app.post("/api/guard/requests/:id/decide", { preHandler: parentGuard }, async (request, reply) => {
    const { id } = request.params as { id: string };
    const body = z
      .object({ approve: z.boolean(), minutes: z.number().int().min(1).max(240).optional() })
      .safeParse(request.body);
    if (!body.success) {
      return reply.code(400).send({ ok: false, error: { code: "VALIDATION", message: body.error.issues[0]?.message } });
    }
    try {
      const result = decideExtensionRequest(db, id, body.data.approve, "parent", body.data.minutes);
      return { ok: true, data: result };
    } catch (error) {
      return reply.code(400).send({ ok: false, error: { code: "REQUEST_REJECTED", message: (error as Error).message } });
    }
  });

  // --- Device API: авторизация токеном устройства ---

  const deviceGuard = async (request: FastifyRequest, reply: any) => {
    const device = deviceFromRequest(request, ctx);
    if (!device) {
      return reply.code(401).send({ ok: false, error: { code: "UNAUTHORIZED", message: "Неверный токен устройства" } });
    }
    (request as FastifyRequest & { device?: typeof device }).device = device;
  };

  app.get("/api/device/policy", { preHandler: deviceGuard }, async (request) => {
    const device = (request as FastifyRequest & { device: NonNullable<ReturnType<typeof deviceFromRequest>> }).device;
    touchDevice(db, device.id);
    const policy = ensurePolicy(db, device.child_id);
    const today = localDateISO();
    const usage = usageForDate(db, device.child_id, today);
    return {
      ok: true,
      data: {
        child: { id: device.child_id, name: getChild(db, device.child_id)?.name ?? "" },
        device: { id: device.id, name: device.name },
        policy,
        limitMinutes: effectiveLimit(policy, today),
        usedMinutes: usage.minutes,
        remainMinutes: Math.max(0, effectiveLimit(policy, today) - usage.minutes),
        serverTime: new Date().toISOString(),
      },
    };
  });

  app.post("/api/device/usage", { preHandler: deviceGuard }, async (request, reply) => {
    const device = (request as FastifyRequest & { device: NonNullable<ReturnType<typeof deviceFromRequest>> }).device;
    const body = z
      .object({
        date: z.string().regex(/^\d{4}-\d{2}-\d{2}$/).optional(),
        minutes: z.number().min(0).max(1440),
        apps: z
          .array(z.object({ packageId: z.string().min(1), label: z.string().max(120).optional(), minutes: z.number().min(0) }))
          .max(200)
          .default([]),
      })
      .safeParse(request.body);
    if (!body.success) {
      return reply.code(400).send({ ok: false, error: { code: "VALIDATION", message: body.error.issues[0]?.message } });
    }
    recordUsage(db, device.child_id, body.data.date ?? localDateISO(), body.data.minutes, body.data.apps);
    touchDevice(db, device.id);
    return { ok: true };
  });

  app.post("/api/device/events", { preHandler: deviceGuard }, async (request, reply) => {
    const device = (request as FastifyRequest & { device: NonNullable<ReturnType<typeof deviceFromRequest>> }).device;
    const body = z
      .object({
        kind: z.string().min(1).max(60),
        payload: z.unknown().optional(),
        occurredAt: z.string().optional(),
      })
      .safeParse(request.body);
    if (!body.success) {
      return reply.code(400).send({ ok: false, error: { code: "VALIDATION", message: body.error.issues[0]?.message } });
    }
    const event = addEvent(db, {
      childId: device.child_id,
      deviceId: device.id,
      kind: body.data.kind,
      payload: body.data.payload,
      occurredAt: body.data.occurredAt,
    });
    touchDevice(db, device.id);
    return { ok: true, data: event };
  });

  app.post("/api/device/heartbeat", { preHandler: deviceGuard }, async (request) => {
    const device = (request as FastifyRequest & { device: NonNullable<ReturnType<typeof deviceFromRequest>> }).device;
    touchDevice(db, device.id);
    const policy = ensurePolicy(db, device.child_id);
    return { ok: true, data: { serverTime: new Date().toISOString(), limitMinutes: effectiveLimit(policy) } };
  });

  app.post("/api/device/extension-request", { preHandler: deviceGuard }, async (request, reply) => {
    const device = (request as FastifyRequest & { device: NonNullable<ReturnType<typeof deviceFromRequest>> }).device;
    const body = z
      .object({ minutes: z.number().int().min(1).max(120), reason: z.string().max(300).optional() })
      .safeParse(request.body);
    if (!body.success) {
      return reply.code(400).send({ ok: false, error: { code: "VALIDATION", message: body.error.issues[0]?.message } });
    }
    const req = createExtensionRequest(db, {
      childId: device.child_id,
      deviceId: device.id,
      minutes: body.data.minutes,
      reason: body.data.reason ?? null,
    });
    addEvent(db, {
      childId: device.child_id,
      deviceId: device.id,
      kind: "extension_requested",
      payload: { requestId: req.id, minutes: req.minutes },
    });
    return { ok: true, data: req };
  });
}
