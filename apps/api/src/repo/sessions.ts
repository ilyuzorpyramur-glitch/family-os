import type { DB } from "../db.js";

export type SessionRow = {
  id: string;
  quest_id: string;
  child_id: string;
  started_at: string;
  ends_at: string;
  status: string;
};

export function startSession(db: DB, questId: string, childId: string, minutes: number): SessionRow {
  db.prepare("UPDATE quest_sessions SET status = 'finished' WHERE child_id = ? AND status = 'active'").run(childId);
  const id = `ses_${crypto.randomUUID()}`;
  const duration = Math.max(1, Math.min(600, Math.round(minutes)));
  db.prepare(
    `INSERT INTO quest_sessions (id, quest_id, child_id, ends_at) VALUES (?, ?, ?, datetime('now', ?))`,
  ).run(id, questId, childId, `+${duration} minutes`);
  return db.prepare("SELECT * FROM quest_sessions WHERE id = ?").get(id) as SessionRow;
}

export function activeSession(db: DB, childId: string): SessionRow | undefined {
  db.prepare("UPDATE quest_sessions SET status = 'finished' WHERE status = 'active' AND ends_at <= datetime('now')").run();
  return db
    .prepare(
      "SELECT * FROM quest_sessions WHERE child_id = ? AND status = 'active' ORDER BY ends_at DESC LIMIT 1",
    )
    .get(childId) as SessionRow | undefined;
}

export function finishSession(db: DB, id: string): void {
  db.prepare("UPDATE quest_sessions SET status = 'finished' WHERE id = ?").run(id);
}

export function finishSessionsForQuest(db: DB, questId: string): void {
  db.prepare("UPDATE quest_sessions SET status = 'finished' WHERE quest_id = ? AND status = 'active'").run(questId);
}

export type ShoppingItemRow = {
  id: string;
  quest_id: string;
  title: string;
  barcode: string | null;
  checked_at: string | null;
};

export function addShoppingItems(db: DB, questId: string, items: { title: string; barcode?: string | null }[]): void {
  for (const item of items) {
    db.prepare("INSERT INTO quest_shopping_items (id, quest_id, title, barcode) VALUES (?, ?, ?, ?)").run(
      `si_${crypto.randomUUID()}`,
      questId,
      item.title.trim(),
      item.barcode ?? null,
    );
  }
}

export function listShoppingItems(db: DB, questId: string): ShoppingItemRow[] {
  return db
    .prepare("SELECT * FROM quest_shopping_items WHERE quest_id = ? ORDER BY rowid")
    .all(questId) as ShoppingItemRow[];
}

export function checkShoppingItem(
  db: DB,
  questId: string,
  barcode: string,
): { item: ShoppingItemRow | null; remaining: number } {
  const items = listShoppingItems(db, questId);
  const match = items.find((item) => !item.checked_at && (item.barcode === barcode || item.title === barcode));
  if (match) {
    db.prepare("UPDATE quest_shopping_items SET checked_at = datetime('now') WHERE id = ?").run(match.id);
  }
  const remaining = listShoppingItems(db, questId).filter((item) => !item.checked_at).length;
  return { item: match ?? null, remaining };
}

export function shoppingProgress(db: DB, questId: string): { total: number; done: number; items: ShoppingItemRow[] } {
  const items = listShoppingItems(db, questId);
  return { total: items.length, done: items.filter((item) => item.checked_at).length, items };
}
