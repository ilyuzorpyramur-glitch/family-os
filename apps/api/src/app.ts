import Fastify, { type FastifyInstance } from "fastify";
import { createReadStream, existsSync } from "node:fs";
import { join } from "node:path";
import cookie from "@fastify/cookie";
import formbody from "@fastify/formbody";
import type { Config } from "./config.js";
import type { DB } from "./db.js";
import { seedCategories } from "./repo/categories.js";
import { seedDefaultMoney } from "./repo/money-seed.js";
import { registerApi } from "./routes/api.js";
import { registerGuardApi } from "./routes/guard.js";
import { registerQuestApi } from "./routes/quest.js";
import { registerEventsApi } from "./routes/events.js";
import { registerBankApi } from "./routes/bank.js";
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
  seedDefaultMoney(db, config.baseCurrency);

  app.get("/uploads/:name", async (request, reply) => {
    const { name } = request.params as { name: string };
    if (!/^[a-f0-9-]{36}\.jpg$/i.test(name)) return reply.code(404).send();
    const file = join(config.dataDir, "uploads", name);
    if (!existsSync(file)) return reply.code(404).send();
    return reply.type("image/jpeg").send(createReadStream(file));
  });

  registerApi(app, { config, db });
  registerGuardApi(app, { config, db });
  registerQuestApi(app, { config, db });
  registerEventsApi(app, { config, db });
  registerBankApi(app, { config, db });
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
