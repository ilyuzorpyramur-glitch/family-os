import type { FastifyInstance, FastifyRequest } from "fastify";
import type { Ctx } from "./api.js";
import { bearerActor, isAdminCookie } from "../auth.js";
import { findDeviceByToken } from "../repo/guard.js";

/**
 * Один запрос вместо шести: приложение забирает всё состояние разом.
 * Внутри переиспользуются существующие обработчики через inject.
 */
export function registerBootstrapApi(app: FastifyInstance, ctx: Ctx): void {
  const { config, db } = ctx;

  const parentGuard = async (request: FastifyRequest, reply: any) => {
    if (!bearerActor(request, config) && !isAdminCookie(request, config)) {
      return reply.code(401).send({ ok: false, error: { code: "UNAUTHORIZED", message: "Нужен токен родителя" } });
    }
  };

  const deviceGuard = async (request: FastifyRequest, reply: any) => {
    const header = request.headers.authorization ?? "";
    const key = header.startsWith("Bearer ") ? header.substring(7).trim() : "";
    const device = key ? findDeviceByToken(db, key) : null;
    if (!device) {
      return reply.code(401).send({ ok: false, error: { code: "UNAUTHORIZED", message: "Неверный токен устройства" } });
    }
  };

  async function grab(authorization: string, pairs: [string, string][]): Promise<Record<string, unknown>> {
    const responses = await Promise.all(
      pairs.map(async ([key, path]) => {
        const response = await app.inject({ method: "GET", url: path, headers: { authorization } });
        try {
          return [key, JSON.parse(response.body).data] as const;
        } catch {
          return [key, null] as const;
        }
      }),
    );
    return Object.fromEntries(responses);
  }

  app.get("/api/device/bootstrap", { preHandler: deviceGuard }, async (request) => {
    const authorization = request.headers.authorization ?? "";
    const month = new Date().toISOString().slice(0, 7);
    const data = await grab(authorization, [
      ["policy", "/api/device/policy"],
      ["home", "/api/device/home"],
      ["quests", "/api/device/quests"],
      ["shop", "/api/device/shop"],
      ["recipes", "/api/device/recipes"],
      ["products", "/api/device/products"],
      ["sites", "/api/device/sites/blocklist"],
      ["calendar", `/api/device/calendar?month=${month}`],
    ]);
    return { ok: true, data: { ...data, month, fetchedAt: new Date().toISOString() } };
  });

  app.get("/api/guard/bootstrap", { preHandler: parentGuard }, async (request) => {
    const authorization = request.headers.authorization ?? "";
    const data = await grab(authorization, [
      ["overview", "/api/guard/overview"],
      ["events", "/api/guard/child-events?status=pending"],
      ["money", "/api/money/overview"],
      ["children", "/api/guard/children"],
      ["rules", "/api/guard/sites/rules"],
      ["visits", "/api/guard/sites"],
      ["usage", "/api/guard/usage/apps"],
      ["recipes", "/api/recipes"],
      ["products", "/api/products"],
      ["suggest", "/api/shopping/suggest"],
    ]);
    return { ok: true, data: { ...data, fetchedAt: new Date().toISOString() } };
  });
}
