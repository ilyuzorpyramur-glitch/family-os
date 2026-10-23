import { createHash, randomBytes } from "node:crypto";
import type { DB } from "../db.js";
import { localDateISO } from "../lib/time.js";

export type ChildRow = { id: string; name: string; birth_date: string | null; created_at: string };

export type PolicyRow = {
  child_id: string;
  screen_limit_min: number;
  bedtime_start: string;
  bedtime_end: string;
  web_filter: number;
  filter_level: string;
  allow_install: number;
  extra_minutes: number;
  extra_date: string | null;
  updated_at: string;
};

export type DeviceRow = {
  id: string;
  child_id: string;
  name: string;
  platform: string;
  token_hash: string;
  push_token: string | null;
  status: string;
  last_seen_at: string | null;
  created_at: string;
};

export type GuardEventRow = {
  id: string;
  child_id: string;
  device_id: string | null;
  kind: string;
  payload: string | null;
  occurred_at: string;
  created_at: string;
};

export type ExtensionRequestRow = {
  id: string;
  child_id: string;
  device_id: string | null;
  minutes: number;
  reason: string | null;
  cost_coins: number;
  status: string;
  decided_by: string | null;
  decided_at: string | null;
  created_at: string;
};

export function createChild(db: DB, name: string, birthDate?: string | null): ChildRow {
  const id = `child_${crypto.randomUUID()}`;
  db.prepare("INSERT INTO children (id, name, birth_date) VALUES (?, ?, ?)").run(id, name, birthDate ?? null);
  ensurePolicy(db, id);
  return db.prepare("SELECT * FROM children WHERE id = ?").get(id) as ChildRow;
}

export function getChild(db: DB, id: string): ChildRow | undefined {
  return db.prepare("SELECT * FROM children WHERE id = ?").get(id) as ChildRow | undefined;
}

export function listChildren(db: DB): ChildRow[] {
  return db.prepare("SELECT * FROM children ORDER BY created_at").all() as ChildRow[];
}

export function ensurePolicy(db: DB, childId: string): PolicyRow {
  db.prepare("INSERT OR IGNORE INTO guard_policies (child_id) VALUES (?)").run(childId);
  return db.prepare("SELECT * FROM guard_policies WHERE child_id = ?").get(childId) as PolicyRow;
}

export type PolicyPatch = {
  screenLimitMin?: number;
  bedtimeStart?: string;
  bedtimeEnd?: string;
  webFilter?: boolean;
  filterLevel?: string;
  allowInstall?: boolean;
};

export function updatePolicy(db: DB, childId: string, patch: PolicyPatch): PolicyRow {
  const current = ensurePolicy(db, childId);
  db.prepare(
    `UPDATE guard_policies SET screen_limit_min = ?, bedtime_start = ?, bedtime_end = ?, web_filter = ?,
       filter_level = ?, allow_install = ?, updated_at = datetime('now') WHERE child_id = ?`,
  ).run(
    patch.screenLimitMin ?? current.screen_limit_min,
    patch.bedtimeStart ?? current.bedtime_start,
    patch.bedtimeEnd ?? current.bedtime_end,
    patch.webFilter === undefined ? current.web_filter : patch.webFilter ? 1 : 0,
    patch.filterLevel ?? current.filter_level,
    patch.allowInstall === undefined ? current.allow_install : patch.allowInstall ? 1 : 0,
    childId,
  );
  return db.prepare("SELECT * FROM guard_policies WHERE child_id = ?").get(childId) as PolicyRow;
}

export function effectiveLimit(policy: PolicyRow, date = localDateISO()): number {
  const extra = policy.extra_date === date ? policy.extra_minutes : 0;
  return policy.screen_limit_min + extra;
}

function hashToken(token: string): string {
  return createHash("sha256").update(token).digest("hex");
}

export function createDevice(
  db: DB,
  childId: string,
  name: string,
  platform = "android",
): { device: DeviceRow; token: string } {
  const token = `dev_${randomBytes(24).toString("base64url")}`;
  const id = `dev_${crypto.randomUUID()}`;
  db.prepare("INSERT INTO devices (id, child_id, name, platform, token_hash) VALUES (?, ?, ?, ?, ?)").run(
    id,
    childId,
    name,
    platform,
    hashToken(token),
  );
  return { device: db.prepare("SELECT * FROM devices WHERE id = ?").get(id) as DeviceRow, token };
}

export function listDevices(db: DB, childId: string): DeviceRow[] {
  return db.prepare("SELECT * FROM devices WHERE child_id = ? ORDER BY created_at").all(childId) as DeviceRow[];
}

export function findDeviceByToken(db: DB, token: string): DeviceRow | undefined {
  return db.prepare("SELECT * FROM devices WHERE token_hash = ?").get(hashToken(token)) as DeviceRow | undefined;
}

export function touchDevice(db: DB, deviceId: string): void {
  db.prepare("UPDATE devices SET last_seen_at = datetime('now') WHERE id = ?").run(deviceId);
}

export function recordUsage(
  db: DB,
  childId: string,
  date: string,
  minutes: number,
  apps: { packageId: string; label?: string; minutes: number }[] = [],
): void {
  const run = db.transaction(() => {
    db.prepare(
      `INSERT INTO usage_daily (child_id, date, minutes, updated_at) VALUES (?, ?, ?, datetime('now'))
       ON CONFLICT(child_id, date) DO UPDATE SET minutes = excluded.minutes, updated_at = datetime('now')`,
    ).run(childId, date, Math.max(0, Math.round(minutes)));

    for (const app of apps) {
      db.prepare(
        `INSERT INTO usage_apps (child_id, date, package_id, label, minutes) VALUES (?, ?, ?, ?, ?)
         ON CONFLICT(child_id, date, package_id) DO UPDATE SET minutes = excluded.minutes, label = excluded.label`,
      ).run(childId, date, app.packageId, app.label ?? null, Math.max(0, Math.round(app.minutes)));
    }
  });
  run();
}

export function usageForDate(
  db: DB,
  childId: string,
  date = localDateISO(),
): { minutes: number; apps: { package_id: string; label: string | null; minutes: number }[] } {
  const daily = db.prepare("SELECT minutes FROM usage_daily WHERE child_id = ? AND date = ?").get(childId, date) as
    | { minutes: number }
    | undefined;
  const apps = db
    .prepare("SELECT package_id, label, minutes FROM usage_apps WHERE child_id = ? AND date = ? ORDER BY minutes DESC")
    .all(childId, date) as { package_id: string; label: string | null; minutes: number }[];
  return { minutes: daily?.minutes ?? 0, apps };
}

export function addEvent(
  db: DB,
  input: { childId: string; deviceId?: string | null; kind: string; payload?: unknown; occurredAt?: string },
): GuardEventRow {
  const id = `ev_${crypto.randomUUID()}`;
  db.prepare(
    "INSERT INTO guard_events (id, child_id, device_id, kind, payload, occurred_at) VALUES (?, ?, ?, ?, ?, ?)",
  ).run(
    id,
    input.childId,
    input.deviceId ?? null,
    input.kind,
    input.payload === undefined ? null : JSON.stringify(input.payload),
    input.occurredAt ?? new Date().toISOString(),
  );
  return db.prepare("SELECT * FROM guard_events WHERE id = ?").get(id) as GuardEventRow;
}

export function listEvents(db: DB, childId?: string, limit = 50): GuardEventRow[] {
  const safeLimit = Math.min(Math.max(limit, 1), 200);
  if (childId) {
    return db
      .prepare("SELECT * FROM guard_events WHERE child_id = ? ORDER BY occurred_at DESC LIMIT ?")
      .all(childId, safeLimit) as GuardEventRow[];
  }
  return db.prepare("SELECT * FROM guard_events ORDER BY occurred_at DESC LIMIT ?").all(safeLimit) as GuardEventRow[];
}

export function createExtensionRequest(
  db: DB,
  input: { childId: string; deviceId?: string | null; minutes: number; reason?: string | null; costCoins?: number },
): ExtensionRequestRow {
  const id = `req_${crypto.randomUUID()}`;
  db.prepare(
    "INSERT INTO extension_requests (id, child_id, device_id, minutes, reason, cost_coins) VALUES (?, ?, ?, ?, ?, ?)",
  ).run(
    id,
    input.childId,
    input.deviceId ?? null,
    Math.max(1, Math.min(240, Math.round(input.minutes))),
    input.reason ?? null,
    Math.max(0, Math.round(input.costCoins ?? 0)),
  );
  return db.prepare("SELECT * FROM extension_requests WHERE id = ?").get(id) as ExtensionRequestRow;
}

export function listExtensionRequests(db: DB, status?: string, limit = 50): ExtensionRequestRow[] {
  if (status) {
    return db
      .prepare("SELECT * FROM extension_requests WHERE status = ? ORDER BY created_at DESC LIMIT ?")
      .all(status, limit) as ExtensionRequestRow[];
  }
  return db.prepare("SELECT * FROM extension_requests ORDER BY created_at DESC LIMIT ?").all(limit) as ExtensionRequestRow[];
}

export function decideExtensionRequest(
  db: DB,
  id: string,
  approve: boolean,
  decidedBy = "parent",
  minutesOverride?: number,
): ExtensionRequestRow {
  const request = db.prepare("SELECT * FROM extension_requests WHERE id = ?").get(id) as ExtensionRequestRow | undefined;
  if (!request) throw new Error("Запрос не найден");
  if (request.status !== "pending") throw new Error("Запрос уже обработан");

  const today = localDateISO();
  const run = db.transaction(() => {
    db.prepare("UPDATE extension_requests SET status = ?, decided_by = ?, decided_at = datetime('now') WHERE id = ?").run(
      approve ? "approved" : "denied",
      decidedBy,
      id,
    );
    if (approve) {
      const policy = ensurePolicy(db, request.child_id);
      const minutes = minutesOverride ?? request.minutes;
      const base = policy.extra_date === today ? policy.extra_minutes : 0;
      db.prepare("UPDATE guard_policies SET extra_minutes = ?, extra_date = ?, updated_at = datetime('now') WHERE child_id = ?").run(
        base + minutes,
        today,
        request.child_id,
      );
    } else if (request.cost_coins > 0) {
      db.prepare("UPDATE character_profiles SET coins = coins + ?, updated_at = datetime('now') WHERE child_id = ?").run(
        request.cost_coins,
        request.child_id,
      );
    }
    addEvent(db, {
      childId: request.child_id,
      deviceId: request.device_id,
      kind: approve ? "extension_approved" : "extension_denied",
      payload: { requestId: id, minutes: minutesOverride ?? request.minutes },
    });
  });
  run();
  return db.prepare("SELECT * FROM extension_requests WHERE id = ?").get(id) as ExtensionRequestRow;
}


export function currentBlockReason(
  policy: PolicyRow,
  usedMinutes: number,
  now: Date = new Date(),
  timeZone: string = process.env.TZ ?? "Europe/Warsaw",
): "limit" | "bedtime" | null {
  if (policy.screen_limit_min > 0 && usedMinutes >= policy.screen_limit_min) return "limit";

  const hhmm = new Intl.DateTimeFormat("en-GB", {
    timeZone,
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).format(now);
  const minutesNow = toMinutes(hhmm);
  const from = toMinutes(policy.bedtime_start);
  const to = toMinutes(policy.bedtime_end);
  if (minutesNow == null || from == null || to == null) return null;
  const bedtime = from <= to ? minutesNow >= from && minutesNow < to : minutesNow >= from || minutesNow < to;
  return bedtime ? "bedtime" : null;
}

function toMinutes(value: string): number | null {
  const [h, m] = value.split(":");
  const hours = Number(h);
  const minutes = Number(m);
  if (!Number.isFinite(hours) || !Number.isFinite(minutes)) return null;
  return hours * 60 + minutes;
}

export type GeofenceRow = {
  id: string;
  child_id: string;
  label: string;
  lat: number;
  lng: number;
  radius_m: number;
  notify_enter: number;
  notify_exit: number;
};

export function listGeofences(db: DB, childId?: string): GeofenceRow[] {
  if (childId) {
    return db.prepare("SELECT * FROM geofences WHERE child_id = ? ORDER BY created_at").all(childId) as GeofenceRow[];
  }
  return db.prepare("SELECT * FROM geofences ORDER BY created_at").all() as GeofenceRow[];
}

export function createGeofence(
  db: DB,
  input: { childId: string; label: string; lat: number; lng: number; radiusM?: number; notifyEnter?: boolean; notifyExit?: boolean },
): GeofenceRow {
  const id = `geo_${crypto.randomUUID()}`;
  db.prepare(
    `INSERT INTO geofences (id, child_id, label, lat, lng, radius_m, notify_enter, notify_exit)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?)`,
  ).run(
    id,
    input.childId,
    input.label.trim(),
    input.lat,
    input.lng,
    Math.max(30, Math.min(5000, Math.round(input.radiusM ?? 150))),
    input.notifyEnter === false ? 0 : 1,
    input.notifyExit === false ? 0 : 1,
  );
  return db.prepare("SELECT * FROM geofences WHERE id = ?").get(id) as GeofenceRow;
}

export function deleteGeofence(db: DB, id: string): boolean {
  return db.prepare("DELETE FROM geofences WHERE id = ?").run(id).changes > 0;
}

export type GuardOverview = {
  date: string;
  children: {
    id: string;
    name: string;
    policy: PolicyRow;
    limitMinutes: number;
    usedMinutes: number;
    remainingMinutes: number;
    devices: { id: string; name: string; lastSeenAt: string | null; status: string }[];
    pendingRequests: ExtensionRequestRow[];
    lastEvent: GuardEventRow | null;
    geofences: GeofenceRow[];
  }[];
};

export function guardOverview(db: DB): GuardOverview {
  const date = localDateISO();
  const children = listChildren(db).map((child) => {
    const policy = ensurePolicy(db, child.id);
    const usage = usageForDate(db, child.id, date);
    const limitMinutes = effectiveLimit(policy, date);
    return {
      id: child.id,
      name: child.name,
      policy,
      limitMinutes,
      usedMinutes: usage.minutes,
      remainingMinutes: Math.max(0, limitMinutes - usage.minutes),
      devices: listDevices(db, child.id).map((d) => ({
        id: d.id,
        name: d.name,
        lastSeenAt: d.last_seen_at,
        status: d.status,
      })),
      pendingRequests: listExtensionRequests(db, "pending").filter((r) => r.child_id === child.id),
      lastEvent: listEvents(db, child.id, 1)[0] ?? null,
      geofences: listGeofences(db, child.id),
    };
  });
  return { date, children };
}
