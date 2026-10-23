import type { FastifyInstance, FastifyRequest } from "fastify";
import { z } from "zod";
import { isAdminCookie, bearerActor } from "../auth.js";
import { findDeviceByToken } from "../repo/guard.js";
import { calendarForMonth, createEvent, decideEvent, getEvent, listEvents } from "../repo/events.js";
import type { Ctx } from "./api.js";
import { localDateISO } from "../lib/time.js";

function deviceFromRequest(request: FastifyRequest, ctx: Ctx) {
  const header = request.headers.authorization ?? "";
  if (!header.startsWith("Bearer ")) return null;
  const key = header.substring(7).trim();
  if (!key) return null;
  return findDeviceByToken(ctx.db, key);
}

export function registerEventsApi(app: FastifyInstance, ctx: Ctx): void {
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

  app.get("/api/device/calendar", { preHandler: deviceGuard }, async (request, reply) => {
    const device = (request as FastifyRequest & { device: NonNullable<ReturnType<typeof deviceFromRequest>> }).device;
    const q = request.query as { month?: string };
    const month = q.month && /^\d{4}-\d{2}$/.test(q.month) ? q.month : localDateISO().slice(0, 7);
    return { ok: true, data: { month, days: calendarForMonth(db, device.child_id, month) } };
  });

  app.get("/api/device/child-events", { preHandler: deviceGuard }, async (request) => {
    const device = (request as FastifyRequest & { device: NonNullable<ReturnType<typeof deviceFromRequest>> }).device;
    const q = request.query as { month?: string };
    const month = q.month && /^\d{4}-\d{2}$/.test(q.month) ? q.month : localDateISO().slice(0, 7);
    return {
      ok: true,
      data: listEvents(db, { childId: device.child_id, from: `${month}-01`, to: `${month}-31` }),
    };
  });

  app.post("/api/device/child-events", { preHandler: deviceGuard }, async (request, reply) => {
    const device = (request as FastifyRequest & { device: NonNullable<ReturnType<typeof deviceFromRequest>> }).device;
    const body = z
      .object({
        title: z.string().min(1).max(120),
        description: z.string().max(500).optional(),
        geofenceId: z.string().nullable().optional(),
        lat: z.number().min(-90).max(90).optional(),
        lng: z.number().min(-180).max(180).optional(),
        date: z.string().regex(/^\d{4}-\d{2}-\d{2}$/),
        startTime: z.string().regex(/^\d{2}:\d{2}$/),
        durationMin: z.number().int().min(15).max(720).optional(),
        note: z.string().max(500).optional(),
      })
      .safeParse(request.body);
    if (!body.success) {
      return reply.code(400).send({ ok: false, error: { code: "VALIDATION", message: body.error.issues[0]?.message } });
    }
    const event = createEvent(db, {
      childId: device.child_id,
      title: body.data.title,
      geofenceId: body.data.geofenceId ?? null,
      date: body.data.date,
      startTime: body.data.startTime,
      durationMin: body.data.durationMin,
      note: body.data.note ?? null,
      description: body.data.description ?? null,
      lat: body.data.lat ?? null,
      lng: body.data.lng ?? null,
      createdBy: "child",
    });
    return { ok: true, data: event };
  });

  app.get("/api/guard/child-events", { preHandler: parentGuard }, async (request) => {
    const q = request.query as { status?: string; childId?: string };
    return { ok: true, data: listEvents(db, { status: q.status, childId: q.childId }) };
  });

  app.post("/api/guard/child-events/:id/decide", { preHandler: parentGuard }, async (request, reply) => {
    const { id } = request.params as { id: string };
    const body = z
      .object({
        approve: z.boolean(),
        startTime: z.string().regex(/^\d{2}:\d{2}$/).optional(),
        durationMin: z.number().int().min(15).max(720).optional(),
        title: z.string().min(1).max(120).optional(),
      })
      .safeParse(request.body);
    if (!body.success) {
      return reply.code(400).send({ ok: false, error: { code: "VALIDATION", message: body.error.issues[0]?.message } });
    }
    try {
      const event = decideEvent(db, id, body.data.approve, "parent", {
        startTime: body.data.startTime,
        durationMin: body.data.durationMin,
        title: body.data.title,
      });
      return { ok: true, data: event };
    } catch (error) {
      return reply.code(400).send({ ok: false, error: { code: "EVENT_REJECTED", message: (error as Error).message } });
    }
  });

  app.patch("/api/guard/child-events/:id", { preHandler: parentGuard }, async (request, reply) => {
    const { id } = request.params as { id: string };
    const event = getEvent(db, id);
    if (!event) return reply.code(404).send({ ok: false, error: { code: "NOT_FOUND", message: "Событие не найдено" } });
    const body = z
      .object({
        startTime: z.string().regex(/^\d{2}:\d{2}$/).optional(),
        durationMin: z.number().int().min(15).max(720).optional(),
        title: z.string().min(1).max(120).optional(),
        description: z.string().max(500).nullable().optional(),
      })
      .safeParse(request.body);
    if (!body.success) {
      return reply.code(400).send({ ok: false, error: { code: "VALIDATION", message: body.error.issues[0]?.message } });
    }
    db.prepare("UPDATE events SET start_time = ?, duration_min = ?, title = ?, description = ? WHERE id = ?").run(
      body.data.startTime ?? event.start_time,
      body.data.durationMin ?? event.duration_min,
      body.data.title ?? event.title,
      body.data.description === undefined ? event.description : body.data.description,
      id,
    );
    return { ok: true, data: getEvent(db, id) };
  });
}
