import type { DB } from "../db.js";

/** Стартовый набор счетов и категорий, чтобы «свои деньги» работали без Zen Money. */
export function seedDefaultMoney(db: DB, currency = "RUB"): void {
  const accounts = db.prepare("SELECT COUNT(*) AS n FROM accounts").get() as { n: number };
  if (accounts.n === 0) {
    db.prepare(
      `INSERT INTO accounts (id, title, type, currency, balance_minor, in_balance, archived, source)
       VALUES (?, ?, 'cash', ?, 0, 1, 0, 'local')`,
    ).run(`acc_${crypto.randomUUID()}`, "Наличные", currency);
    db.prepare(
      `INSERT INTO accounts (id, title, type, currency, balance_minor, in_balance, archived, source)
       VALUES (?, ?, 'ccard', ?, 0, 1, 0, 'local')`,
    ).run(`acc_${crypto.randomUUID()}`, "Карта", currency);
  }

  const defaults: { id: string; title: string; kind: "expense" | "income"; icon: string }[] = [
    { id: "cat:food", title: "Продукты", kind: "expense", icon: "🛒" },
    { id: "cat:transport", title: "Транспорт", kind: "expense", icon: "🚌" },
    { id: "cat:home", title: "Жильё", kind: "expense", icon: "🏠" },
    { id: "cat:health", title: "Здоровье", kind: "expense", icon: "💊" },
    { id: "cat:fun", title: "Развлечения", kind: "expense", icon: "🎬" },
    { id: "cat:kids", title: "Дети", kind: "expense", icon: "🧸" },
    { id: "cat:other", title: "Прочее", kind: "expense", icon: "📦" },
    { id: "cat:salary", title: "Зарплата", kind: "income", icon: "💰" },
    { id: "cat:gift", title: "Подарки", kind: "income", icon: "🎁" },
  ];
  for (const category of defaults) {
    db.prepare(
      `INSERT OR IGNORE INTO categories (id, external_id, title, kind) VALUES (?, ?, ?, ?)`,
    ).run(category.id, category.id, category.title, category.kind);
  }
  db.prepare(
    `INSERT OR IGNORE INTO categories (id, external_id, title, kind) VALUES ('sys:uncategorized', NULL, 'Без категории', 'expense')`,
  ).run();
  db.prepare(
    `INSERT OR IGNORE INTO categories (id, external_id, title, kind) VALUES ('sys:uncategorized-income', NULL, 'Без категории', 'income')`,
  ).run();
}
