import type { DB } from "../db.js";
import { localDateISO } from "../lib/time.js";
import { addEvent, getChild, listGeofences } from "./guard.js";
import { completeQuest, decideQuest, ensureProfile, getQuest, profileView, questPhotos, listQuests } from "./quest.js";

export function distanceMeters(lat1: number, lon1: number, lat2: number, lon2: number): number {
  const R = 6_371_000;
  const toRad = (value: number) => (value * Math.PI) / 180;
  const dLat = toRad(lat2 - lat1);
  const dLon = toRad(lon2 - lon1);
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLon / 2) ** 2;
  return 2 * R * Math.asin(Math.sqrt(a));
}

export type LocationResult = {
  inside: { id: string; label: string; inside: boolean }[];
  events: string[];
  fines: { geofenceId: string; amount: number }[];
  completedQuests: string[];
};

function minutesOfDay(value: string): number | null {
  const [h, m] = value.split(":");
  const hours = Number(h);
  const minutes = Number(m);
  if (!Number.isFinite(hours) || !Number.isFinite(minutes)) return null;
  return hours * 60 + minutes;
}

function inSchedule(start: string | null, end: string | null, now = new Date()): boolean {
  if (!start || !end) return true;
  const from = minutesOfDay(start);
  const to = minutesOfDay(end);
  if (from == null || to == null) return true;
  const current = now.getHours() * 60 + now.getMinutes();
  return from <= to ? current >= from && current < to : current >= from || current < to;
}

export function processLocation(
  db: DB,
  childId: string,
  deviceId: string,
  lat: number,
  lng: number,
  accuracy?: number | null,
): LocationResult {
  const now = new Date();
  db.prepare(
    "UPDATE devices SET last_lat = ?, last_lng = ?, last_location_at = datetime('now') WHERE id = ?",
  ).run(lat, lng, deviceId);
  db.prepare("INSERT INTO location_pings (id, child_id, lat, lng, accuracy) VALUES (?, ?, ?, ?, ?)").run(
    `ping_${crypto.randomUUID()}`,
    childId,
    lat,
    lng,
    accuracy ?? null,
  );

  const result: LocationResult = { inside: [], events: [], fines: [], completedQuests: [] };
  const fences = listGeofences(db, childId);

  for (const fence of fences) {
    const distance = distanceMeters(lat, lng, fence.lat, fence.lng);
    const inside = distance <= fence.radius_m;
    const previous = db
      .prepare("SELECT * FROM geofence_state WHERE child_id = ? AND geofence_id = ?")
      .get(childId, fence.id) as { inside: number; last_fine_at: string | null } | undefined;
    const wasInside = Boolean(previous?.inside);

    result.inside.push({ id: fence.id, label: fence.label, inside });

    if (inside !== wasInside) {
      addEvent(db, {
        childId,
        deviceId,
        kind: inside ? "geofence_enter" : "geofence_exit",
        payload: { geofenceId: fence.id, label: fence.label, distance: Math.round(distance) },
      });
      result.events.push(inside ? "geofence_enter" : "geofence_exit");
    }

    let lastFine = previous?.last_fine_at ?? null;
    if (inside && fence.auto_quest_id) {
      const quest = getQuest(db, fence.auto_quest_id);
      if (quest && quest.status === "active" && quest.child_id === childId) {
        completeQuest(db, quest.id, "geofence");
        try {
          decideQuest(db, quest.id, true, "geofence", "agent");
          result.completedQuests.push(quest.id);
        } catch {
          // если задание требует родителя — останется на проверке
        }
      }
    }

    if (!inside && fence.fine_coins > 0 && inSchedule(fence.schedule_start, fence.schedule_end, now)) {
      const cooldownMs = Math.max(1, fence.fine_every_min) * 60_000;
      const last = lastFine ? new Date(`${lastFine.replace(" ", "T")}Z`).getTime() : 0;
      if (now.getTime() - last >= cooldownMs) {
        const profile = ensureProfile(db, childId);
        const amount = Math.min(fence.fine_coins, profile.coins);
        db.prepare(
          "UPDATE character_profiles SET coins = MAX(0, coins - ?), updated_at = datetime('now') WHERE child_id = ?",
        ).run(fence.fine_coins, childId);
        db.prepare(
          `INSERT INTO geofence_state (child_id, geofence_id, inside, last_fine_at, updated_at)
           VALUES (?, ?, ?, datetime('now'), datetime('now'))
           ON CONFLICT(child_id, geofence_id) DO UPDATE SET inside = excluded.inside,
             last_fine_at = excluded.last_fine_at, updated_at = datetime('now')`,
        ).run(childId, fence.id, inside ? 1 : 0);
        lastFine = new Date().toISOString();
        if (amount > 0) {
          result.fines.push({ geofenceId: fence.id, amount });
          addEvent(db, {
            childId,
            deviceId,
            kind: "geofence_fine",
            payload: { geofenceId: fence.id, label: fence.label, amount },
          });
        }
      }
    }

    db.prepare(
      `INSERT INTO geofence_state (child_id, geofence_id, inside, last_fine_at, updated_at)
       VALUES (?, ?, ?, ?, datetime('now'))
       ON CONFLICT(child_id, geofence_id) DO UPDATE SET inside = excluded.inside,
         last_fine_at = COALESCE(excluded.last_fine_at, geofence_state.last_fine_at), updated_at = datetime('now')`,
    ).run(childId, fence.id, inside ? 1 : 0, lastFine);
  }

  return result;
}

export type HomeData = {
  child: {
    id: string;
    name: string;
    nickname: string | null;
    school: string | null;
    grade: string | null;
    avatarUrl: string | null;
  };
  profile: ReturnType<typeof profileView>;
  today: { active: number; pending: number; approved: number };
  screenMinutes: number;
  geofences: { id: string; label: string; inside: boolean; fineCoins: number }[];
  lastLocation: { lat: number; lng: number; at: string | null } | null;
  tasks: { id: string; title: string; xp: number; coins: number; status: string }[];
  events: { kind: string; createdAt: string }[];
};

export function homeData(db: DB, childId: string, publicUrl: string): HomeData {
  const child = getChild(db, childId);
  const profile = profileView(db, childId);
  const today = localDateISO();

  const counts = db
    .prepare(
      `SELECT
         SUM(CASE WHEN status = 'active' THEN 1 ELSE 0 END) AS active,
         SUM(CASE WHEN status = 'completed' THEN 1 ELSE 0 END) AS pending,
         SUM(CASE WHEN status = 'approved' AND substr(approved_at, 1, 10) = ? THEN 1 ELSE 0 END) AS approved
       FROM quests WHERE child_id = ?`,
    )
    .get(today, childId) as { active: number | null; pending: number | null; approved: number | null };

  const usage = db.prepare("SELECT minutes FROM usage_daily WHERE child_id = ? AND date = ?").get(childId, today) as
    | { minutes: number }
    | undefined;

  const fences = listGeofences(db, childId).map((fence) => {
    const state = db
      .prepare("SELECT inside FROM geofence_state WHERE child_id = ? AND geofence_id = ?")
      .get(childId, fence.id) as { inside: number } | undefined;
    return {
      id: fence.id,
      label: fence.label,
      inside: Boolean(state?.inside),
      fineCoins: fence.fine_coins,
    };
  });

  const device = db
    .prepare("SELECT last_lat, last_lng, last_location_at FROM devices WHERE child_id = ? ORDER BY last_seen_at DESC LIMIT 1")
    .get(childId) as { last_lat: number | null; last_lng: number | null; last_location_at: string | null } | undefined;

  const tasks = listQuests(db, { childId, limit: 20 }).map((quest) => ({
    id: quest.id,
    title: quest.title,
    xp: quest.xp,
    coins: quest.coins,
    status: quest.status,
  }));

  const events = db
    .prepare("SELECT kind, created_at FROM guard_events WHERE child_id = ? ORDER BY created_at DESC LIMIT 5")
    .all(childId) as { kind: string; created_at: string }[];

  return {
    child: {
      id: childId,
      name: child?.name ?? "",
      nickname: (child as unknown as { nickname?: string })?.nickname ?? null,
      school: (child as unknown as { school?: string })?.school ?? null,
      grade: (child as unknown as { grade?: string })?.grade ?? null,
      avatarUrl: (child as unknown as { avatar_path?: string })?.avatar_path
        ? `${publicUrl.replace(/\/$/, "")}/uploads/${(child as unknown as { avatar_path: string }).avatar_path}`
        : null,
    },
    profile,
    today: {
      active: counts.active ?? 0,
      pending: counts.pending ?? 0,
      approved: counts.approved ?? 0,
    },
    screenMinutes: usage?.minutes ?? 0,
    geofences: fences,
    lastLocation: device?.last_location_at
      ? { lat: device.last_lat ?? 0, lng: device.last_lng ?? 0, at: device.last_location_at }
      : null,
    tasks,
    events: events.map((e) => ({ kind: e.kind, createdAt: e.created_at })),
  };
}
