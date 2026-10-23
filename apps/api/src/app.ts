import Fastify, { type FastifyInstance } from "fastify";
import cookie from "@fastify/cookie";
import formbody from "@fastify/formbody";
import type { Config } from "./config.js";
import type { DB } from "./db.js";
import { seedCategories } from "./repo/categories.js";
import { registerApi } from "./routes/api.js";
import { registerGuardApi } from "./routes/guard.js";
import { registerWeb } from "./routes/web.js";
import { registerMcp } from "./mcp/server.js";

export type AppOptions = {
  config: Config;
  db: DB;
  logger?: boolean;
};

export async function buildApp({ config, db, logger = false }: AppOptions): Promise<FastifyInstance> {
  const app = Fastify({
    logger: logger ? { level: process.env.LOG_LEVEL ?? "info" } : false,
    bodyLimit: 2 * 1024 * 1024,
    trustProxy: true,
  });

  await app.register(cookie);
  await app.register(formbody);

  seedCategories(db);

  registerApi(app, { config, db });
  registerGuardApi(app, { config, db });
  registerMcp(app, { config, db });
  registerWeb(app, { config, db });

  app.setErrorHandler((error, request, reply) => {
    const err = error as Error & { statusCode?: number };
    request.log.error({ err }, "request failed");
    const status = err.statusCode ?? 500;
    if (request.url.startsWith("/api/") || request.url.startsWith("/mcp")) {
      return reply.code(status).send({
        ok: false,
        error: { code: status === 400 ? "BAD_REQUEST" : "INTERNAL", message: err.message },
      });
    }
    return reply.code(status).type("text/html; charset=utf-8").send(`<h1>${status}</h1><p>${err.message}</p>`);
  });

  return app;
}
