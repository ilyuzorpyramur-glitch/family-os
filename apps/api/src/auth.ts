import { createHmac } from "node:crypto";
import type { FastifyRequest } from "fastify";
import type { Config } from "./config.js";
import { safeEqual } from "./lib/crypto.js";

export type Actor = "admin" | "agent";

export function sessionValue(config: Config): string {
  return createHmac("sha256", config.secret).update("familyos:admin").digest("hex");
}

export function isAdminCookie(request: FastifyRequest, config: Config): boolean {
  const cookie = request.cookies["fo_session"];
  return typeof cookie === "string" && safeEqual(cookie, sessionValue(config));
}

export function bearerActor(request: FastifyRequest, config: Config): Actor | null {
  const header = request.headers.authorization;
  if (!header?.startsWith("Bearer ")) return null;
  const token = header.slice("Bearer ".length).trim();
  if (safeEqual(token, config.adminToken)) return "admin";
  if (config.agentToken && safeEqual(token, config.agentToken)) return "agent";
  return null;
}

export function isAuthorized(request: FastifyRequest, config: Config): boolean {
  return Boolean(bearerActor(request, config)) || isAdminCookie(request, config);
}
