import type { FastifyInstance, FastifyRequest } from "fastify";
import { z } from "zod";
import { bearerActor, isAdminCookie } from "../auth.js";
import { findDeviceByToken, getChild } from "../repo/guard.js";
import {
  childPayLimits,
  createPaymentRequest,
  decidePayment,
  getPayment,
  listPayments,
  parsePaymentQr,
  spentToday,
} from "../repo/payments.js";
import type { Ctx } from "./api.js";

function deviceFromRequest(request: FastifyRequest, ctx: Ctx) {
  const header = request.headers.authorization ?? "";
  if (!header.startsWith("Bearer ")) return null;
  const key = header.substring(7).trim();
  if (!key) return null;
  return findDeviceByToken(ctx.db, key);
}

/**
 * Оплата по QR СБП: ребёнок сканирует код, родитель подтверждает.
 * Деньги не двигаются внутри Family OS — платёж выполняет банковское приложение.
 */
export function registerPaymentApi(app: FastifyInstance, ctx: Ctx): void {
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

  /** Разобрать отсканированный код до запроса — чтобы показать сумму ребёнку. */
  app.post("/api/device/payments/parse", { preHandler: deviceGuard }, async (request, reply) => {
    const body = z.object({ qrPayload: z.string().min(4).max(2000) }).safeParse(request.body);
    if (!body.success) {
      return reply.code(400).send({ ok: false, error: { code: "VALIDATION", message: "Пустой QR" } });
    }
    const parsed = parsePaymentQr(body.data.qrPayload);
    return { ok: true, data: parsed };
  });

  app.get("/api/device/payments", { preHandler: deviceGuard }, async (request) => {
    const device = (request as FastifyRequest & { device: NonNullable<ReturnType<typeof deviceFromRequest>> }).device;
    const limits = childPayLimits(db, device.child_id);
    return {
      ok: true,
      data: {
        limits: { perRequest: limits.perRequest, daily: limits.daily, spentToday: spentToday(db, device.child_id) },
        items: listPayments(db, { childId: device.child_id, limit: 30 }),
      },
    };
  });

  app.post("/api/device/payments", { preHandler: deviceGuard }, async (request, reply) => {
    const device = (request as FastifyRequest & { device: NonNullable<ReturnType<typeof deviceFromRequest>> }).device;
    const body = z
      .object({
        qrPayload: z.string().min(4).max(2000),
        amount: z.number().min(0.01).max(100000).optional(),
        purpose: z.string().max(160).optional(),
      })
      .safeParse(request.body);
    if (!body.success) {
      return reply.code(400).send({ ok: false, error: { code: "VALIDATION", message: "Проверьте QR и сумму" } });
    }

    const parsed = parsePaymentQr(body.data.qrPayload);
    if (!parsed.isPayment) {
      return reply
        .code(400)
        .send({ ok: false, error: { code: "NOT_PAYMENT_QR", message: "Это не платёжный QR СБП" } });
    }
    const amount = body.data.amount ?? parsed.amount;
    if (!amount) {
      return reply
        .code(400)
        .send({ ok: false, error: { code: "NO_AMOUNT", message: "В QR нет суммы — введите её вручную" } });
    }

    const result = createPaymentRequest(db, {
      childId: device.child_id,
      amount,
      purpose: body.data.purpose ?? parsed.purpose,
      qrPayload: body.data.qrPayload,
      merchant: parsed.bank,
    });
    if (!result.ok) {
      return reply.code(409).send({ ok: false, error: { code: result.code, message: result.message } });
    }
    return { ok: true, data: result.payment };
  });

  /** Родитель: список запросов на оплату. */
  app.get("/api/guard/payments", { preHandler: parentGuard }, async (request) => {
    const query = request.query as { status?: string; childId?: string };
    return {
      ok: true,
      data: { items: listPayments(db, { status: query.status, childId: query.childId, limit: 60 }) },
    };
  });

  app.post("/api/guard/payments/:id/decide", { preHandler: parentGuard }, async (request, reply) => {
    const { id } = request.params as { id: string };
    const body = z
      .object({ status: z.enum(["paid", "declined"]), note: z.string().max(200).optional() })
      .safeParse(request.body);
    if (!body.success) {
      return reply.code(400).send({ ok: false, error: { code: "VALIDATION", message: "Нужен статус" } });
    }
    const payment = getPayment(db, id);
    if (!payment) return reply.code(404).send({ ok: false, error: { code: "NOT_FOUND", message: "Запрос не найден" } });
    if (payment.status !== "pending") {
      return reply.code(409).send({ ok: false, error: { code: "ALREADY_DECIDED", message: "Запрос уже обработан" } });
    }
    const updated = decidePayment(db, id, body.data.status, body.data.note);
    return { ok: true, data: updated };
  });

  /** Родитель: лимиты на оплату для ребёнка. */
  app.patch("/api/guard/children/:id/pay-limits", { preHandler: parentGuard }, async (request, reply) => {
    const { id } = request.params as { id: string };
    if (!getChild(db, id)) return reply.code(404).send({ ok: false, error: { code: "NOT_FOUND", message: "Ребёнок не найден" } });
    const body = z
      .object({
        perRequest: z.number().int().min(0).max(100000),
        daily: z.number().int().min(0).max(300000),
      })
      .safeParse(request.body);
    if (!body.success) return reply.code(400).send({ ok: false, error: { code: "VALIDATION", message: "Проверьте лимиты" } });
    db.prepare("UPDATE children SET pay_limit = ?, pay_daily_limit = ? WHERE id = ?").run(
      body.data.perRequest,
      body.data.daily,
      id,
    );
    return { ok: true, data: { perRequest: body.data.perRequest, daily: body.data.daily } };
  });
}
