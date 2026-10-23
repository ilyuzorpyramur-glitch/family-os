import { mkdirSync, writeFileSync } from "node:fs";
import { randomUUID } from "node:crypto";
import { join } from "node:path";

const MAX_BYTES = 3 * 1024 * 1024;

export function saveImage(dataDir: string, base64: string): string {
  const clean = base64.replace(/^data:image\/[a-zA-Z0-9.+-]+;base64,/, "").trim();
  if (!clean) throw new Error("Пустое изображение");
  const buffer = Buffer.from(clean, "base64");
  if (buffer.length === 0) throw new Error("Не удалось прочитать изображение");
  if (buffer.length > MAX_BYTES) throw new Error("Фото больше 3 МБ");

  const dir = join(dataDir, "uploads");
  mkdirSync(dir, { recursive: true });
  const name = `${randomUUID()}.jpg`;
  writeFileSync(join(dir, name), buffer, { mode: 0o600 });
  return name;
}

export function imageUrl(publicUrl: string, path: string | null | undefined): string | null {
  if (!path) return null;
  return `${publicUrl.replace(/\/$/, "")}/uploads/${path}`;
}
