import type { DB } from "../db.js";

export type SiteVisitRow = { id: string; child_id: string; domain: string; at: string };
export type BlockedSiteRow = { domain: string; reason: string | null; created_at: string; kind: string };

const ADULT_PATTERNS = [
  "porn",
  "xxx",
  "sex",
  "adult",
  "erotic",
  "onlyfans",
  "cam4",
  "chaturbate",
  "xvideos",
  "xnxx",
  "redtube",
  "youporn",
  "brazzers",
  "hentai",
];

/** Простая эвристика для взрослых домены; список расширяется родителем. */
export function looksAdult(domain: string): boolean {
  const value = domain.toLowerCase();
  return ADULT_PATTERNS.some((pattern) => value.includes(pattern));
}

export function recordVisits(db: DB, childId: string, domains: string[]): { saved: number; blocked: string[] } {
  const blocked = new Set(listBlocked(db).map((row) => row.domain));
  let saved = 0;
  const hits: string[] = [];
  for (const raw of domains.slice(0, 200)) {
    const domain = raw.trim().toLowerCase().replace(/^\.+|\.+$/g, "").slice(0, 200);
    if (domain.length < 3 || !domain.includes(".")) continue;
    db.prepare("INSERT INTO site_visits (id, child_id, domain) VALUES (?, ?, ?)").run(
      `visit_${crypto.randomUUID()}`,
      childId,
      domain,
    );
    saved += 1;
    if (blocked.has(domain) || looksAdult(domain)) hits.push(domain);
  }
  return { saved, blocked: hits };
}

export function listVisits(db: DB, opts: { childId?: string; limit?: number } = {}): (SiteVisitRow & { visits: number })[] {
  const params: unknown[] = [];
  let where = "";
  if (opts.childId) {
    where = "WHERE child_id = ?";
    params.push(opts.childId);
  }
  params.push(Math.min(Math.max(opts.limit ?? 100, 1), 500));
  return db
    .prepare(
      `SELECT domain, child_id, MAX(at) AS at, COUNT(*) AS visits, MIN(id) AS id
       FROM site_visits ${where} GROUP BY domain ORDER BY at DESC LIMIT ?`,
    )
    .all(...params) as (SiteVisitRow & { visits: number })[];
}

export function listBlocked(db: DB): BlockedSiteRow[] {
  return db.prepare("SELECT * FROM blocked_sites ORDER BY domain").all() as BlockedSiteRow[];
}

export function listRules(db: DB, kind: "allow" | "block"): BlockedSiteRow[] {
  return db.prepare("SELECT * FROM blocked_sites WHERE kind = ? ORDER BY domain").all(kind) as BlockedSiteRow[];
}

export function addRule(db: DB, domain: string, kind: "allow" | "block", reason?: string | null): BlockedSiteRow {
  const clean = domain.trim().toLowerCase().replace(/^https?:\/\//, "").replace(/\/.*$/, "").slice(0, 200);
  db.prepare("INSERT OR REPLACE INTO blocked_sites (domain, reason, kind) VALUES (?, ?, ?)").run(clean, reason ?? null, kind);
  return db.prepare("SELECT * FROM blocked_sites WHERE domain = ?").get(clean) as BlockedSiteRow;
}

export function addBlocked(db: DB, domain: string, reason?: string | null): BlockedSiteRow {
  const clean = domain.trim().toLowerCase().replace(/^https?:\/\//, "").replace(/\/.*$/, "").slice(0, 200);
  db.prepare("INSERT OR REPLACE INTO blocked_sites (domain, reason) VALUES (?, ?)").run(clean, reason ?? null);
  return db.prepare("SELECT * FROM blocked_sites WHERE domain = ?").get(clean) as BlockedSiteRow;
}

export function removeBlocked(db: DB, domain: string): void {
  db.prepare("DELETE FROM blocked_sites WHERE domain = ?").run(domain);
}

/** Полный список для устройства: ручные + эвристические шаблоны. */
export function blocklistForDevice(db: DB): { block: string[]; allow: string[]; patterns: string[] } {
  return {
    block: listRules(db, "block").map((row) => row.domain),
    allow: listRules(db, "allow").map((row) => row.domain),
    patterns: ADULT_PATTERNS,
  };
}
