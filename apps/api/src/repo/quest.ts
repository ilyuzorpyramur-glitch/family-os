import type { DB } from "../db.js";
import { localDateISO } from "../lib/time.js";

export type ProfileRow = {
  child_id: string;
  xp: number;
  coins: number;
  level: number;
  strength: number;
  intellect: number;
  creativity: number;
  kindness: number;
  discipline: number;
  streak_days: number;
  last_completed_date: string | null;
};

export type QuestRow = {
  id: string;
  child_id: string;
  title: string;
  description: string | null;
  kind: string;
  xp: number;
  coins: number;
  stat: string | null;
  due_at: string | null;
  status: string;
  created_by: string;
  created_at: string;
  completed_at: string | null;
  approved_at: string | null;
};

export type ShopItemRow = {
  id: string;
  title: string;
  description: string | null;
  price_coins: number;
  kind: string;
  value: string | null;
  stock: number | null;
  active: number;
};

export type PurchaseRow = {
  id: string;
  child_id: string;
  item_id: string;
  price_coins: number;
  status: string;
  decided_by: string | null;
  decided_at: string | null;
  created_at: string;
};

const STATS = ["strength", "intellect", "creativity", "kindness", "discipline"] as const;
export type StatName = (typeof STATS)[number];

export function isStat(value: string | null | undefined): value is StatName {
  return typeof value === "string" && (STATS as readonly string[]).includes(value);
}

export function levelFromXp(xp: number): number {
  return Math.floor(Math.sqrt(Math.max(0, xp) / 50)) + 1;
}

export function xpForLevel(level: number): number {
  const n = Math.max(1, level) - 1;
  return 50 * n * n;
}

export function ensureProfile(db: DB, childId: string): ProfileRow {
  db.prepare("INSERT OR IGNORE INTO character_profiles (child_id) VALUES (?)").run(childId);
  return db.prepare("SELECT * FROM character_profiles WHERE child_id = ?").get(childId) as ProfileRow;
}

export type ProfileView = ProfileRow & {
  level: number;
  xpIntoLevel: number;
  xpForNextLevel: number;
  nextLevelAt: number;
  progress: number;
};

export function profileView(db: DB, childId: string): ProfileView {
  const row = ensureProfile(db, childId);
  const level = levelFromXp(row.xp);
  const base = xpForLevel(level);
  const next = xpForLevel(level + 1);
  const span = Math.max(1, next - base);
  return {
    ...row,
    level,
    xpIntoLevel: row.xp - base,
    xpForNextLevel: next - row.xp,
    nextLevelAt: next,
    progress: Math.min(1, Math.max(0, (row.xp - base) / span)),
  };
}

export type QuestInput = {
  childId: string;
  title: string;
  description?: string | null;
  kind?: string;
  xp?: number;
  coins?: number;
  stat?: string | null;
  dueAt?: string | null;
  createdBy?: string;
};

export function createQuest(db: DB, input: QuestInput): QuestRow {
  const id = `q_${crypto.randomUUID()}`;
  db.prepare(
    `INSERT INTO quests (id, child_id, title, description, kind, xp, coins, stat, due_at, created_by)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
  ).run(
    id,
    input.childId,
    input.title.trim(),
    input.description?.trim() ?? null,
    input.kind ?? "once",
    Math.max(0, Math.min(1000, Math.round(input.xp ?? 10))),
    Math.max(0, Math.min(1000, Math.round(input.coins ?? 5))),
    input.stat ?? null,
    input.dueAt ?? null,
    input.createdBy ?? "parent",
  );
  addQuestEvent(db, input.childId, id, "created", { by: input.createdBy ?? "parent" });
  return getQuest(db, id)!;
}

export function getQuest(db: DB, id: string): QuestRow | undefined {
  return db.prepare("SELECT * FROM quests WHERE id = ?").get(id) as QuestRow | undefined;
}

export function listQuests(
  db: DB,
  opts: { childId?: string; status?: string; limit?: number } = {},
): QuestRow[] {
  const where: string[] = [];
  const params: unknown[] = [];
  if (opts.childId) {
    where.push("child_id = ?");
    params.push(opts.childId);
  }
  if (opts.status) {
    where.push("status = ?");
    params.push(opts.status);
  } else {
    where.push("status != 'archived'");
  }
  params.push(Math.min(Math.max(opts.limit ?? 100, 1), 300));
  return db
    .prepare(
      `SELECT * FROM quests ${where.length ? "WHERE " + where.join(" AND ") : ""}
       ORDER BY CASE status WHEN 'completed' THEN 0 WHEN 'active' THEN 1 ELSE 2 END, created_at DESC LIMIT ?`,
    )
    .all(...params) as QuestRow[];
}

export function completeQuest(db: DB, questId: string, by = "child"): QuestRow {
  const quest = getQuest(db, questId);
  if (!quest) throw new Error("Задание не найдено");
  if (quest.status !== "active") throw new Error("Задание уже не активно");

  const run = db.transaction(() => {
    db.prepare("UPDATE quests SET status = 'completed', completed_at = datetime('now') WHERE id = ?").run(questId);
    addQuestEvent(db, quest.child_id, questId, "completed", { by });
  });
  run();
  return getQuest(db, questId)!;
}

export function decideQuest(db: DB, questId: string, approve: boolean, decidedBy = "parent"): QuestRow {
  const quest = getQuest(db, questId);
  if (!quest) throw new Error("Задание не найдено");
  if (quest.status !== "completed") throw new Error("Задание не ждёт проверки");

  const today = localDateISO();
  const run = db.transaction(() => {
    if (!approve) {
      db.prepare("UPDATE quests SET status = 'rejected', approved_at = datetime('now') WHERE id = ?").run(questId);
      addQuestEvent(db, quest.child_id, questId, "rejected", { by: decidedBy });
      return;
    }

    const profile = ensureProfile(db, quest.child_id);
    const xp = profile.xp + quest.xp;
    const levelBefore = levelFromXp(profile.xp);
    const levelAfter = levelFromXp(xp);

    let streak = profile.streak_days;
    if (profile.last_completed_date === today) {
      // второй квест за день — стрик не меняется
    } else if (profile.last_completed_date === yesterdayISO(today)) {
      streak += 1;
    } else if (profile.last_completed_date === null || profile.streak_days === 0) {
      streak = 1;
    } else {
      streak = 1;
    }

    const stat = isStat(quest.stat) ? quest.stat : "discipline";
    db.prepare(
      `UPDATE character_profiles SET xp = ?, coins = coins + ?, level = ?, ${stat} = ${stat} + ?,
         streak_days = ?, last_completed_date = ?, updated_at = datetime('now')
       WHERE child_id = ?`,
    ).run(xp, quest.coins, levelAfter, Math.max(1, Math.round(quest.xp / 10)), streak, today, quest.child_id);

    db.prepare("UPDATE quests SET status = 'approved', approved_at = datetime('now') WHERE id = ?").run(questId);
    addQuestEvent(db, quest.child_id, questId, "approved", {
      by: decidedBy,
      xp: quest.xp,
      coins: quest.coins,
      stat,
      levelBefore,
      levelAfter,
    });
  });
  run();
  return getQuest(db, questId)!;
}

function yesterdayISO(today: string): string {
  const d = new Date(`${today}T12:00:00Z`);
  d.setUTCDate(d.getUTCDate() - 1);
  return d.toISOString().slice(0, 10);
}

export function addQuestEvent(db: DB, childId: string, questId: string | null, kind: string, payload?: unknown): void {
  db.prepare("INSERT INTO quest_events (id, child_id, quest_id, kind, payload) VALUES (?, ?, ?, ?, ?)").run(
    `qe_${crypto.randomUUID()}`,
    childId,
    questId,
    kind,
    payload === undefined ? null : JSON.stringify(payload),
  );
}

export function listShopItems(db: DB, activeOnly = true): ShopItemRow[] {
  const where = activeOnly ? "WHERE active = 1" : "";
  return db.prepare(`SELECT * FROM shop_items ${where} ORDER BY price_coins, title`).all() as ShopItemRow[];
}

export function createShopItem(
  db: DB,
  input: { title: string; description?: string | null; priceCoins: number; kind?: string; value?: string | null; stock?: number | null; createdBy?: string },
): ShopItemRow {
  const id = `item_${crypto.randomUUID()}`;
  db.prepare(
    `INSERT INTO shop_items (id, title, description, price_coins, kind, value, stock, created_by)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?)`,
  ).run(
    id,
    input.title.trim(),
    input.description?.trim() ?? null,
    Math.max(1, Math.round(input.priceCoins)),
    input.kind ?? "privilege",
    input.value ?? null,
    input.stock ?? null,
    input.createdBy ?? "parent",
  );
  return db.prepare("SELECT * FROM shop_items WHERE id = ?").get(id) as ShopItemRow;
}

export function buyItem(db: DB, childId: string, itemId: string): PurchaseRow {
  const item = db.prepare("SELECT * FROM shop_items WHERE id = ? AND active = 1").get(itemId) as ShopItemRow | undefined;
  if (!item) throw new Error("Товар не найден");
  if (item.stock !== null && item.stock <= 0) throw new Error("Товар закончился");

  const profile = ensureProfile(db, childId);
  if (profile.coins < item.price_coins) throw new Error("Недостаточно монет");

  const pending = db
    .prepare("SELECT id FROM shop_purchases WHERE child_id = ? AND item_id = ? AND status = 'pending'")
    .get(childId, itemId) as { id: string } | undefined;
  if (pending) throw new Error("Запрос на этот товар уже отправлен");

  const id = `buy_${crypto.randomUUID()}`;
  db.prepare("INSERT INTO shop_purchases (id, child_id, item_id, price_coins) VALUES (?, ?, ?, ?)").run(
    id,
    childId,
    itemId,
    item.price_coins,
  );
  addQuestEvent(db, childId, null, "purchase_requested", { itemId, title: item.title, price: item.price_coins });
  return getPurchase(db, id)!;
}

export function getPurchase(db: DB, id: string): PurchaseRow | undefined {
  return db.prepare("SELECT * FROM shop_purchases WHERE id = ?").get(id) as PurchaseRow | undefined;
}

export function listPurchases(db: DB, opts: { status?: string; childId?: string; limit?: number } = {}): (PurchaseRow & { title: string })[] {
  const where: string[] = [];
  const params: unknown[] = [];
  if (opts.status) {
    where.push("p.status = ?");
    params.push(opts.status);
  }
  if (opts.childId) {
    where.push("p.child_id = ?");
    params.push(opts.childId);
  }
  params.push(Math.min(Math.max(opts.limit ?? 100, 1), 300));
  return db
    .prepare(
      `SELECT p.*, s.title FROM shop_purchases p JOIN shop_items s ON s.id = p.item_id
       ${where.length ? "WHERE " + where.join(" AND ") : ""}
       ORDER BY p.created_at DESC LIMIT ?`,
    )
    .all(...params) as (PurchaseRow & { title: string })[];
}

export function decidePurchase(db: DB, id: string, approve: boolean, decidedBy = "parent"): PurchaseRow {
  const purchase = getPurchase(db, id);
  if (!purchase) throw new Error("Запрос не найден");
  if (purchase.status !== "pending") throw new Error("Запрос уже обработан");

  const run = db.transaction(() => {
    if (approve) {
      const profile = ensureProfile(db, purchase.child_id);
      if (profile.coins < purchase.price_coins) throw new Error("У ребёнка уже не хватает монет");
      db.prepare("UPDATE character_profiles SET coins = coins - ?, updated_at = datetime('now') WHERE child_id = ?").run(
        purchase.price_coins,
        purchase.child_id,
      );
      db.prepare("UPDATE shop_items SET stock = CASE WHEN stock IS NULL THEN NULL ELSE stock - 1 END WHERE id = ?").run(
        purchase.item_id,
      );
    }
    db.prepare("UPDATE shop_purchases SET status = ?, decided_by = ?, decided_at = datetime('now') WHERE id = ?").run(
      approve ? "approved" : "rejected",
      decidedBy,
      id,
    );
    addQuestEvent(db, purchase.child_id, null, approve ? "purchase_approved" : "purchase_rejected", {
      purchaseId: id,
      price: purchase.price_coins,
      by: decidedBy,
    });
  });
  run();
  return getPurchase(db, id)!;
}

export type QuestOverview = {
  date: string;
  children: {
    id: string;
    name: string;
    profile: ProfileView;
    active: QuestRow[];
    pending: QuestRow[];
    purchases: (PurchaseRow & { item_title: string })[];
  }[];
  shop: ShopItemRow[];
};

export function questOverview(db: DB): QuestOverview {
  const children = (db.prepare("SELECT id, name FROM children ORDER BY created_at").all() as { id: string; name: string }[]).map(
    (child) => ({
      id: child.id,
      name: child.name,
      profile: profileView(db, child.id),
      active: listQuests(db, { childId: child.id, status: "active", limit: 50 }),
      pending: listQuests(db, { childId: child.id, status: "completed", limit: 50 }),
      purchases: listPurchases(db, { childId: child.id, status: "pending" }).map((p) => ({
        ...p,
        item_title: p.title,
      })),
    }),
  );
  return { date: localDateISO(), children, shop: listShopItems(db, false) };
}
