import type { FastifyInstance, FastifyRequest } from "fastify";
import { z } from "zod";
import { bearerActor, isAdminCookie } from "../auth.js";
import type { Ctx } from "./api.js";
import { localDateISO } from "../lib/time.js";
import { ensureProfile, profileView, refreshRecurring } from "../repo/quest.js";
import { activeSession } from "../repo/sessions.js";
import { addBlocked, listBlocked, listVisits, recordVisits, removeBlocked } from "../repo/sites.js";
import { homeData, processLocation } from "../repo/geo.js";
import { imageUrl, saveImage } from "../uploads.js";
import {
  addEvent,
  currentBlockReason,
  createChild,
  createDevice,
  createGeofence,
  deleteGeofence,
  getChildProfile,
  listGeofences,
  setChildAvatar,
  updateChildProfile,
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

  app.get("/api/guard/sites", { preHandler: parentGuard }, async (request) => {
    const q = request.query as { childId?: string };
    return { ok: true, data: listVisits(db, { childId: q.childId, limit: 150 }) };
  });

  app.get("/api/guard/sites/blocked", { preHandler: parentGuard }, async () => ({ ok: true, data: listBlocked(db) }));

  app.post("/api/guard/sites/blocked", { preHandler: parentGuard }, async (request, reply) => {
    const body = z.object({ domain: z.string().min(3).max(200), reason: z.string().max(200).optional() }).safeParse(request.body);
    if (!body.success) {
      return reply.code(400).send({ ok: false, error: { code: "VALIDATION", message: "Нужен домен" } });
    }
    return { ok: true, data: addBlocked(db, body.data.domain, body.data.reason ?? null) };
  });

  app.delete("/api/guard/sites/blocked/:domain", { preHandler: parentGuard }, async (request) => {
    const { domain } = request.params as { domain: string };
    removeBlocked(db, decodeURIComponent(domain));
    return { ok: true };
  });

  app.get("/api/guard/usage/apps", { preHandler: parentGuard }, async (request) => {
    const q = request.query as { childId?: string; date?: string };
    const date = q.date ?? localDateISO();
    const rows = db
      .prepare(
        `SELECT ua.child_id, ua.package_id, ua.label, ua.minutes FROM usage_apps ua
         WHERE ua.date = ? ${q.childId ? "AND ua.child_id = ?" : ""}
         ORDER BY ua.minutes DESC LIMIT 100`,
      )
      .all(...(q.childId ? [date, q.childId] : [date]));
    return { ok: true, data: { date, apps: rows } };
  });

  app.get("/api/guard/crashes", { preHandler: parentGuard }, async () => ({
    ok: true,
    data: db.prepare("SELECT * FROM crash_reports ORDER BY created_at DESC LIMIT 50").all(),
  }));

  app.delete("/api/guard/crashes", { preHandler: parentGuard }, async () => {
    db.prepare("DELETE FROM crash_reports").run();
    return { ok: true };
  });

  app.get("/api/guard/children/:id/profile", { preHandler: parentGuard }, async (request, reply) => {
    const { id } = request.params as { id: string };
    const child = getChildProfile(db, id);
    if (!child) return reply.code(404).send({ ok: false, error: { code: "NOT_FOUND", message: "Ребёнок не найден" } });
    return { ok: true, data: { ...child, avatarUrl: imageUrl(config.publicUrl, child.avatar_path) } };
  });

  app.patch("/api/guard/children/:id/profile", { preHandler: parentGuard }, async (request, reply) => {
    const { id } = request.params as { id: string };
    const body = z
      .object({
        nickname: z.string().max(40).nullable().optional(),
        school: z.string().max(120).nullable().optional(),
        grade: z.string().max(40).nullable().optional(),
        birthDate: z.string().nullable().optional(),
      })
      .safeParse(request.body);
    if (!body.success) {
      return reply.code(400).send({ ok: false, error: { code: "VALIDATION", message: body.error.issues[0]?.message } });
    }
    const child = updateChildProfile(db, id, {
      nickname: body.data.nickname,
      school: body.data.school,
      grade: body.data.grade,
    });
    if (!child) return reply.code(404).send({ ok: false, error: { code: "NOT_FOUND", message: "Ребёнок не найден" } });
    if (body.data.birthDate !== undefined) {
      db.prepare("UPDATE children SET birth_date = ? WHERE id = ?").run(body.data.birthDate, id);
    }
    return { ok: true, data: child };
  });

  app.post("/api/guard/children/:id/avatar", { preHandler: parentGuard }, async (request, reply) => {
    const { id } = request.params as { id: string };
    const body = z.object({ imageBase64: z.string().min(10) }).safeParse(request.body);
    if (!body.success) {
      return reply.code(400).send({ ok: false, error: { code: "VALIDATION", message: "Нужно изображение" } });
    }
    try {
      const path = saveImage(config.dataDir, body.data.imageBase64);
      setChildAvatar(db, id, path);
      return { ok: true, data: { avatarUrl: imageUrl(config.publicUrl, path) } };
    } catch (error) {
      return reply.code(400).send({ ok: false, error: { code: "UPLOAD_FAILED", message: (error as Error).message } });
    }
  });

  app.get("/api/guard/children/:id/geofences", { preHandler: parentGuard }, async (request) => {
    const { id } = request.params as { id: string };
    return { ok: true, data: listGeofences(db, id) };
  });

  app.post("/api/guard/children/:id/geofences", { preHandler: parentGuard }, async (request, reply) => {
    const { id } = request.params as { id: string };
    if (!getChild(db, id)) return reply.code(404).send({ ok: false, error: { code: "NOT_FOUND", message: "Ребёнок не найден" } });
    const body = z
      .object({
        label: z.string().min(1).max(80),
        lat: z.number().min(-90).max(90),
        lng: z.number().min(-180).max(180),
        radiusM: z.number().int().min(30).max(5000).optional(),
        notifyEnter: z.boolean().optional(),
        notifyExit: z.boolean().optional(),
        fineCoins: z.number().int().min(0).max(1000).optional(),
        scheduleStart: z.string().regex(/^\d{2}:\d{2}$/).nullable().optional(),
        scheduleEnd: z.string().regex(/^\d{2}:\d{2}$/).nullable().optional(),
        fineEveryMin: z.number().int().min(1).max(120).optional(),
        autoQuestId: z.string().nullable().optional(),
      })
      .safeParse(request.body);
    if (!body.success) {
      return reply.code(400).send({ ok: false, error: { code: "VALIDATION", message: body.error.issues[0]?.message } });
    }
    if (body.data.autoQuestId) {
      const quest = db.prepare("SELECT child_id FROM quests WHERE id = ?").get(body.data.autoQuestId) as
        | { child_id: string }
        | undefined;
      if (!quest || quest.child_id !== id) {
        return reply.code(400).send({ ok: false, error: { code: "VALIDATION", message: "Задание для автовыполнения не найдено" } });
      }
      db.prepare("UPDATE quests SET approval = 'agent' WHERE id = ?").run(body.data.autoQuestId);
    }
    return { ok: true, data: createGeofence(db, { childId: id, ...body.data }) };
  });

  app.delete("/api/guard/geofences/:id", { preHandler: parentGuard }, async (request) => {
    const { id } = request.params as { id: string };
    return { ok: true, data: { deleted: deleteGeofence(db, id) } };
  });

  // --- Device API: авторизация токеном устройства ---

  const deviceGuard = async (request: FastifyRequest, reply: any) => {
    const device = deviceFromRequest(request, ctx);
    if (!device) {
      return reply.code(401).send({ ok: false, error: { code: "UNAUTHORIZED", message: "Неверный токен устройства" } });
    }
    (request as FastifyRequest & { device?: typeof device }).device = device;
  };

  app.post("/api/device/location", { preHandler: deviceGuard }, async (request, reply) => {
    const device = (request as FastifyRequest & { device: NonNullable<ReturnType<typeof deviceFromRequest>> }).device;
    const body = z
      .object({
        lat: z.number().min(-90).max(90),
        lng: z.number().min(-180).max(180),
        accuracy: z.number().min(0).optional(),
      })
      .safeParse(request.body);
    if (!body.success) {
      return reply.code(400).send({ ok: false, error: { code: "VALIDATION", message: "Нужны координаты" } });
    }
    const result = processLocation(db, device.child_id, device.id, body.data.lat, body.data.lng, body.data.accuracy);
    touchDevice(db, device.id);
    return { ok: true, data: result };
  });

  app.get("/api/device/home", { preHandler: deviceGuard }, async (request) => {
    const device = (request as FastifyRequest & { device: NonNullable<ReturnType<typeof deviceFromRequest>> }).device;
    refreshRecurring(db, device.child_id);
    return { ok: true, data: homeData(db, device.child_id, config.publicUrl) };
  });

  app.get("/api/device/profile", { preHandler: deviceGuard }, async (request) => {
    const device = (request as FastifyRequest & { device: NonNullable<ReturnType<typeof deviceFromRequest>> }).device;
    const child = getChildProfile(db, device.child_id);
    return {
      ok: true,
      data: {
        child: child
          ? {
              id: child.id,
              name: child.name,
              nickname: child.nickname,
              school: child.school,
              grade: child.grade,
              birthDate: child.birth_date,
              avatarUrl: imageUrl(config.publicUrl, child.avatar_path),
            }
          : null,
        profile: profileView(db, device.child_id),
        extension: { minutes: 30, costCoins: 50 },
      },
    };
  });

  app.patch("/api/device/profile", { preHandler: deviceGuard }, async (request, reply) => {
    const device = (request as FastifyRequest & { device: NonNullable<ReturnType<typeof deviceFromRequest>> }).device;
    const body = z
      .object({
        nickname: z.string().max(40).nullable().optional(),
        school: z.string().max(120).nullable().optional(),
        grade: z.string().max(40).nullable().optional(),
      })
      .safeParse(request.body);
    if (!body.success) {
      return reply.code(400).send({ ok: false, error: { code: "VALIDATION", message: body.error.issues[0]?.message } });
    }
    const child = updateChildProfile(db, device.child_id, body.data);
    return { ok: true, data: child };
  });

  app.post("/api/device/profile/avatar", { preHandler: deviceGuard }, async (request, reply) => {
    const device = (request as FastifyRequest & { device: NonNullable<ReturnType<typeof deviceFromRequest>> }).device;
    const body = z.object({ imageBase64: z.string().min(10) }).safeParse(request.body);
    if (!body.success) {
      return reply.code(400).send({ ok: false, error: { code: "VALIDATION", message: "Нужно изображение" } });
    }
    try {
      const path = saveImage(config.dataDir, body.data.imageBase64);
      setChildAvatar(db, device.child_id, path);
      return { ok: true, data: { avatarUrl: imageUrl(config.publicUrl, path) } };
    } catch (error) {
      return reply.code(400).send({ ok: false, error: { code: "UPLOAD_FAILED", message: (error as Error).message } });
    }
  });

  app.post("/api/device/crash", { preHandler: deviceGuard }, async (request, reply) => {
    const device = (request as FastifyRequest & { device: NonNullable<ReturnType<typeof deviceFromRequest>> }).device;
    const body = z
      .object({
        message: z.string().max(500),
        stack: z.string().max(8000).optional(),
        appVersion: z.string().max(40).optional(),
      })
      .safeParse(request.body);
    if (!body.success) {
      return reply.code(400).send({ ok: false, error: { code: "VALIDATION", message: "Нужно сообщение" } });
    }
    db.prepare(
      "INSERT INTO crash_reports (id, child_id, device_id, message, stack, app_version) VALUES (?, ?, ?, ?, ?, ?)",
    ).run(
      `crash_${crypto.randomUUID()}`,
      device.child_id,
      device.id,
      body.data.message,
      body.data.stack ?? null,
      body.data.appVersion ?? null,
    );
    return { ok: true };
  });

  app.get("/api/device/policy", { preHandler: deviceGuard }, async (request) => {
    const device = (request as FastifyRequest & { device: NonNullable<ReturnType<typeof deviceFromRequest>> }).device;
    touchDevice(db, device.id);
    const policy = ensurePolicy(db, device.child_id);
    const today = localDateISO();
    const usage = usageForDate(db, device.child_id, today);
    const profile = profileView(db, device.child_id);
    return {
      ok: true,
      data: {
        child: { id: device.child_id, name: getChild(db, device.child_id)?.name ?? "" },
        device: { id: device.id, name: device.name },
        policy,
        limitMinutes: effectiveLimit(policy, today),
        usedMinutes: usage.minutes,
        blockReason: currentBlockReason(policy, usage.minutes),
        remainMinutes: Math.max(0, effectiveLimit(policy, today) - usage.minutes),
        coins: profile.coins,
        session: (() => {
          const active = activeSession(db, device.child_id);
          if (!active) return null;
          const quest = db.prepare("SELECT title FROM quests WHERE id = ?").get(active.quest_id) as
            | { title: string }
            | undefined;
          return { id: active.id, questId: active.quest_id, title: quest?.title ?? "Задание", endsAt: active.ends_at };
        })(),
        extension: { minutes: 30, costCoins: 50 },
        serverTime: new Date().toISOString(),
      },
    };
  });

  app.post("/api/device/sites", { preHandler: deviceGuard }, async (request, reply) => {
    const device = (request as FastifyRequest & { device: NonNullable<ReturnType<typeof deviceFromRequest>> }).device;
    const body = z.object({ domains: z.array(z.string().max(200)).max(200) }).safeParse(request.body);
    if (!body.success) {
      return reply.code(400).send({ ok: false, error: { code: "VALIDATION", message: "Нужен список доменов" } });
    }
    return { ok: true, data: recordVisits(db, device.child_id, body.data.domains) };
  });

  app.get("/api/device/sites/blocklist", { preHandler: deviceGuard }, async () => ({
    ok: true,
    data: { domains: listBlocked(db).map((row) => row.domain) },
  }));

  app.get("/api/device/geofences", { preHandler: deviceGuard }, async (request) => {
    const device = (request as FastifyRequest & { device: NonNullable<ReturnType<typeof deviceFromRequest>> }).device;
    return {
      ok: true,
      data: listGeofences(db, device.child_id).map((g) => ({
        id: g.id,
        label: g.label,
        lat: g.lat,
        lng: g.lng,
        radiusM: g.radius_m,
        notifyEnter: Boolean(g.notify_enter),
        notifyExit: Boolean(g.notify_exit),
      })),
    };
  });

  app.post("/api/device/geofences/event", { preHandler: deviceGuard }, async (request, reply) => {
    const device = (request as FastifyRequest & { device: NonNullable<ReturnType<typeof deviceFromRequest>> }).device;
    const body = z
      .object({ geofenceId: z.string().min(1), type: z.enum(["enter", "exit"]) })
      .safeParse(request.body);
    if (!body.success) {
      return reply.code(400).send({ ok: false, error: { code: "VALIDATION", message: body.error.issues[0]?.message } });
    }
    const geofence = listGeofences(db, device.child_id).find((g) => g.id === body.data.geofenceId);
    if (!geofence) return reply.code(404).send({ ok: false, error: { code: "NOT_FOUND", message: "Геозона не найдена" } });
    const event = addEvent(db, {
      childId: device.child_id,
      deviceId: device.id,
      kind: body.data.type === "enter" ? "geofence_enter" : "geofence_exit",
      payload: { geofenceId: geofence.id, label: geofence.label },
    });
    return { ok: true, data: event };
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
      .object({ minutes: z.number().int().min(30).max(30).default(30), reason: z.string().max(300).optional() })
      .safeParse(request.body ?? {});
    if (!body.success) {
      return reply.code(400).send({ ok: false, error: { code: "VALIDATION", message: body.error.issues[0]?.message } });
    }

    const COST = 50;
    const profile = ensureProfile(db, device.child_id);
    if (profile.coins < COST) {
      return reply.code(400).send({
        ok: false,
        error: { code: "NOT_ENOUGH_COINS", message: `Продление стоит ${COST} монет. У ребёнка ${profile.coins}.` },
      });
    }
    const charged = db
      .prepare("UPDATE character_profiles SET coins = coins - ?, updated_at = datetime('now') WHERE child_id = ? AND coins >= ?")
      .run(COST, device.child_id, COST);
    if (charged.changes === 0) {
      return reply.code(400).send({ ok: false, error: { code: "NOT_ENOUGH_COINS", message: "Недостаточно монет" } });
    }

    let req;
    try {
      req = createExtensionRequest(db, {
        childId: device.child_id,
        deviceId: device.id,
        minutes: 30,
        reason: body.data.reason ?? null,
        costCoins: COST,
      });
    } catch (error) {
      db.prepare("UPDATE character_profiles SET coins = coins + ? WHERE child_id = ?").run(COST, device.child_id);
      throw error;
    }
    addEvent(db, {
      childId: device.child_id,
      deviceId: device.id,
      kind: "extension_requested",
      payload: { requestId: req.id, minutes: req.minutes },
    });
    return { ok: true, data: req };
  });
}
