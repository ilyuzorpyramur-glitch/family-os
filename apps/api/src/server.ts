import { existsSync } from "node:fs";
import { resolve } from "node:path";
import { buildApp } from "./app.js";
import { loadConfig } from "./config.js";
import { openDb } from "./db.js";
import { getSetting, getZenMoneyToken, setZenMoneyToken } from "./repo/settings.js";

async function main(): Promise<void> {
  const config = loadConfig();
  const db = openDb(config.dbFile);

  if (config.zenmoneyToken && !getSetting(db, "zenmoney.token")) {
    setZenMoneyToken(db, config.secret, config.zenmoneyToken);
    console.log("[family-os] Zen Money token imported from environment");
  }

  const app = await buildApp({ config, db, logger: true });

  await app.listen({ host: config.host, port: config.port });
  console.log(`[family-os] listening on http://${config.host}:${config.port}`);
  console.log(`[family-os] database: ${config.dbFile}`);

  if (!process.env.ADMIN_TOKEN) {
    const tokenFile = resolve(config.dataDir, "admin-token.txt");
    if (existsSync(tokenFile)) console.log(`[family-os] admin token: ${tokenFile}`);
  }

  const shutdown = async (signal: string) => {
    console.log(`[family-os] ${signal}, shutting down`);
    await app.close();
    db.close();
    process.exit(0);
  };
  process.on("SIGINT", () => void shutdown("SIGINT"));
  process.on("SIGTERM", () => void shutdown("SIGTERM"));
}

main().catch((error) => {
  console.error("[family-os] fatal:", error);
  process.exit(1);
});

// Тихая ссылка, чтобы линтер не ругался на неиспользуемый импорт в некоторых сборках.
void getZenMoneyToken;
