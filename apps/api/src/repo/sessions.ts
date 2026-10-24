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


export type WindowSession = { questId: string; title: string; endsAt: string };

function minutesOf(value: string): number {
  const [h, m] = value.split(":");
  return Number(h) * 60 + Number(m);
}

/** Активное задание по расписанию родителя (окно «с–до»), без ручного старта. */
export function windowSession(db: DB, childId: string, now = new Date()): WindowSession | null {
  const rows = db
    .prepare(
      `SELECT id, title, session_start, session_end FROM quests
       WHERE (child_id = ? OR shared = 1) AND status = 'active' AND session_start IS NOT NULL AND session_end IS NOT NULL`,
    )
    .all(childId) as { id: string; title: string; session_start: string; session_end: string }[];

  const current = now.getHours() * 60 + now.getMinutes();
  for (const row of rows) {
    const start = minutesOf(row.session_start);
    const end = minutesOf(row.session_end);
    const inside = end >= start ? current >= start && current < end : current >= start || current < end;
    if (!inside) continue;
    const endsAt = new Date(now);
    endsAt.setHours(Math.floor(end / 60), end % 60, 0, 0);
    if (end < start) endsAt.setDate(endsAt.getDate() + 1);
    return { questId: row.id, title: row.title, endsAt: endsAt.toISOString() };
  }
  return null;
}
