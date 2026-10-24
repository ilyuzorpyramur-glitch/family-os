import { randomBytes, randomInt } from "node:crypto";
import type { DB } from "../db.js";

export type PairKind = "device" | "parent";

export type PairCode = {
  code: string;
  kind: PairKind;
  child_id: string | null;
  device_id: string | null;
  expires_at: string;
  used_at: string | null;
};

/** Короткий код для QR: 8 символов без похожих букв. */
function newCode(): string {
  const alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  let out = "";
  for (let i = 0; i < 8; i += 1) out += alphabet[randomInt(0, alphabet.length)];
  return out;
}

export function createPairCode(
  db: DB,
  input: { kind: PairKind; childId?: string | null; deviceId?: string | null; ttlMinutes?: number },
): PairCode {
  const code = newCode();
  const ttl = input.ttlMinutes ?? 15;
  const expires = new Date(Date.now() + ttl * 60_000).toISOString();
  db.prepare(
    `INSERT INTO pair_codes (code, kind, child_id, device_id, expires_at)
     VALUES (?, ?, ?, ?, ?)`,
  ).run(code, input.kind, input.childId ?? null, input.deviceId ?? null, expires);
  return db.prepare("SELECT * FROM pair_codes WHERE code = ?").get(code) as PairCode;
}

export function getPairCode(db: DB, code: string): PairCode | undefined {
  return db.prepare("SELECT * FROM pair_codes WHERE code = ?").get(code.trim().toUpperCase()) as PairCode | undefined;
}

export function markPairUsed(db: DB, code: string): void {
  db.prepare("UPDATE pair_codes SET used_at = datetime('now') WHERE code = ?").run(code);
}

export function isPairUsable(pair: PairCode | undefined): boolean {
  if (!pair) return false;
  if (pair.used_at) return false;
  return new Date(pair.expires_at).getTime() > Date.now();
}

export function purgePairCodes(db: DB): void {
  db.prepare("DELETE FROM pair_codes WHERE expires_at < datetime('now', '-1 day')").run();
}

/** Полезная нагрузка QR: familyos://pair?u=<url>&c=<code> */
export function pairPayload(publicUrl: string, code: string): string {
  return `familyos://pair?u=${encodeURIComponent(publicUrl)}&c=${code}`;
}

/** Разбор отсканированного QR. Понимает и сырой URL сервера + код. */
export function parsePairPayload(raw: string): { url?: string; code?: string } {
  const text = raw.trim();
  if (!text) return {};
  const query = text.includes("?") ? text.substring(text.indexOf("?") + 1) : "";
  const params = new URLSearchParams(query.replace(/^\/+/, ""));
  const code = params.get("c") ?? params.get("code") ?? "";
  const url = params.get("u") ?? params.get("url") ?? "";
  if (code) return { url: url || undefined, code: code.toUpperCase() };
  // Просто код из 8 символов
  const plain = text.toUpperCase().replace(/[^A-Z0-9]/g, "");
  if (plain.length === 8) return { code: plain };
  return {};
}

export function randomToken(prefix: string): string {
  return `${prefix}_${randomBytes(24).toString("base64url")}`;
}
