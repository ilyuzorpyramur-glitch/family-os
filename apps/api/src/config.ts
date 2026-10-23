import { randomBytes } from "node:crypto";
import { existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, resolve } from "node:path";

export type Config = {
  host: string;
  port: number;
  dbFile: string;
  dataDir: string;
  secret: string;
  adminToken: string;
  agentToken: string | null;
  zenmoneyToken: string | null;
  baseCurrency: string;
  publicUrl: string;
};

function readOrCreate(path: string, generate: () => string): { value: string; created: boolean } {
  if (existsSync(path)) {
    return { value: readFileSync(path, "utf8").trim(), created: false };
  }
  mkdirSync(dirname(path), { recursive: true });
  const value = generate();
  writeFileSync(path, value + "\n", { mode: 0o600 });
  return { value, created: true };
}

export function loadConfig(env: NodeJS.ProcessEnv = process.env): Config {
  const dataDir = resolve(env.FAMILYOS_DATA_DIR ?? "data");
  mkdirSync(dataDir, { recursive: true });

  const secret = env.FAMILYOS_SECRET?.trim() || readOrCreate(resolve(dataDir, "secret.key"), () => randomBytes(32).toString("hex")).value;

  const admin = env.ADMIN_TOKEN?.trim()
    ? { value: env.ADMIN_TOKEN.trim(), created: false }
    : readOrCreate(resolve(dataDir, "admin-token.txt"), () => randomBytes(24).toString("base64url"));

  return {
    host: env.HOST ?? "0.0.0.0",
    port: Number(env.PORT ?? 3001),
    dbFile: env.FAMILYOS_DB ? resolve(env.FAMILYOS_DB) : resolve(dataDir, "familyos.db"),
    dataDir,
    secret,
    adminToken: admin.value,
    agentToken: env.AGENT_TOKEN?.trim() || null,
    zenmoneyToken: env.ZENMONEY_TOKEN?.trim() || null,
    baseCurrency: env.BASE_CURRENCY?.trim() || "PLN",
    publicUrl: env.PUBLIC_URL?.trim() || `http://localhost:${env.PORT ?? 3001}`,
  };
}
