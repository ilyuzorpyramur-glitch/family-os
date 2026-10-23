import type { DB } from "../db.js";

export type CategoryRow = {
  id: string;
  external_id: string | null;
  title: string;
  parent_external_id: string | null;
  kind: "expense" | "income";
  changed: number;
};

export function seedCategories(db: DB): void {
  db.prepare(
    `INSERT OR IGNORE INTO categories (id, external_id, title, kind) VALUES ('sys:uncategorized', NULL, 'Без категории', 'expense')`,
  ).run();
  db.prepare(
    `INSERT OR IGNORE INTO categories (id, external_id, title, kind) VALUES ('sys:uncategorized-income', NULL, 'Без категории', 'income')`,
  ).run();
}

export function upsertCategory(
  db: DB,
  input: { externalId: string; title: string; parentExternalId?: string | null; kind: "expense" | "income"; changed?: number },
): CategoryRow {
  const existing = db
    .prepare("SELECT * FROM categories WHERE external_id = ?")
    .get(input.externalId) as CategoryRow | undefined;

  if (existing) {
    if ((input.changed ?? 0) < existing.changed) return existing;
    db.prepare(
      `UPDATE categories SET title = ?, parent_external_id = ?, kind = ?, changed = ?, updated_at = datetime('now') WHERE id = ?`,
    ).run(input.title, input.parentExternalId ?? null, input.kind, input.changed ?? existing.changed, existing.id);
    return db.prepare("SELECT * FROM categories WHERE id = ?").get(existing.id) as CategoryRow;
  }

  const id = `cat_${crypto.randomUUID()}`;
  db.prepare(
    `INSERT INTO categories (id, external_id, title, parent_external_id, kind, changed) VALUES (?, ?, ?, ?, ?, ?)`,
  ).run(id, input.externalId, input.title, input.parentExternalId ?? null, input.kind, input.changed ?? 0);
  return db.prepare("SELECT * FROM categories WHERE id = ?").get(id) as CategoryRow;
}

export function listCategories(db: DB): CategoryRow[] {
  return db.prepare("SELECT * FROM categories ORDER BY kind, title").all() as CategoryRow[];
}

export function categoryIdByExternal(db: DB, externalId: string): string | null {
  const row = db.prepare("SELECT id FROM categories WHERE external_id = ?").get(externalId) as { id: string } | undefined;
  return row?.id ?? null;
}
