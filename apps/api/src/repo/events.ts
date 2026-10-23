import type { DB } from "../db.js";
import { localDateISO } from "../lib/time.js";

export type EventRow = {
  id: string;
  child_id: string;
  title: string;
  geofence_id: string | null;
  date: string;
  start_time: string;
  duration_min: number;
  status: string;
  note: string | null;
  description: string | null;
  lat: number | null;
  lng: number | null;
  created_by: string;
  approved_by: string | null;
  approved_at: string | null;
  created_at: string;
};

export type EventInput = {
  childId: string;
  title: string;
  geofenceId?: string | null;
  date: string;
  startTime: string;
  durationMin?: number;
  note?: string | null;
  description?: string | null;
  lat?: number | null;
  lng?: number | null;
  createdBy?: string;
};

export function createEvent(db: DB, input: EventInput): EventRow {
  const id = `evt_${crypto.randomUUID()}`;
  db.prepare(
    `INSERT INTO events (id, child_id, title, geofence_id, date, start_time, duration_min, note, description, lat, lng, created_by)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
  ).run(
    id,
    input.childId,
    input.title.trim(),
    input.geofenceId ?? null,
    input.date,
    input.startTime,
    Math.max(15, Math.min(720, Math.round(input.durationMin ?? 60))),
    input.note ?? null,
    input.description ?? null,
    input.lat ?? null,
    input.lng ?? null,
    input.createdBy ?? "child",
  );
  return getEvent(db, id)!;
}

export function getEvent(db: DB, id: string): EventRow | undefined {
  return db.prepare("SELECT * FROM events WHERE id = ?").get(id) as EventRow | undefined;
}

export function listEvents(
  db: DB,
  opts: { childId?: string; status?: string; from?: string; to?: string; limit?: number } = {},
): EventRow[] {
  const where: string[] = [];
  const params: unknown[] = [];
  if (opts.childId) {
    where.push("child_id = ?");
    params.push(opts.childId);
  }
  if (opts.status) {
    where.push("status = ?");
    params.push(opts.status);
  }
  if (opts.from) {
    where.push("date >= ?");
    params.push(opts.from);
  }
  if (opts.to) {
    where.push("date <= ?");
    params.push(opts.to);
  }
  params.push(Math.min(Math.max(opts.limit ?? 200, 1), 500));
  return db
    .prepare(
      `SELECT * FROM events ${where.length ? "WHERE " + where.join(" AND ") : ""}
       ORDER BY date, start_time LIMIT ?`,
    )
    .all(...params) as EventRow[];
}

export function decideEvent(
  db: DB,
  id: string,
  approve: boolean,
  decidedBy = "parent",
  patch: { startTime?: string; durationMin?: number; title?: string } = {},
): EventRow {
  const event = getEvent(db, id);
  if (!event) throw new Error("Событие не найдено");
  if (event.status !== "pending") throw new Error("Событие уже обработано");

  db.prepare(
    `UPDATE events SET status = ?, approved_by = ?, approved_at = datetime('now'),
       start_time = ?, duration_min = ?, title = ? WHERE id = ?`,
  ).run(
    approve ? "approved" : "rejected",
    decidedBy,
    patch.startTime ?? event.start_time,
    patch.durationMin ?? event.duration_min,
    patch.title ?? event.title,
    id,
  );
  return getEvent(db, id)!;
}

function toMinutes(value: string): number | null {
  const [h, m] = value.split(":");
  const hours = Number(h);
  const minutes = Number(m);
  if (!Number.isFinite(hours) || !Number.isFinite(minutes)) return null;
  return hours * 60 + minutes;
}

/** Есть ли одобренное событие, дающее доступ к геозоне в это время. */
export function eventAllowsGeofence(db: DB, childId: string, geofenceId: string, date = localDateISO(), now = new Date()): boolean {
  const events = db
    .prepare("SELECT * FROM events WHERE child_id = ? AND geofence_id = ? AND date = ? AND status = 'approved'")
    .all(childId, geofenceId, date) as EventRow[];
  const current = now.getHours() * 60 + now.getMinutes();
  return events.some((event) => {
    const start = toMinutes(event.start_time);
    if (start == null) return false;
    return current >= start && current < start + event.duration_min;
  });
}

export type CalendarDay = {
  date: string;
  questsDone: number;
  questsPlanned: number;
  events: { id: string; title: string; startTime: string; durationMin: number; status: string; place: string | null }[];
};

export function calendarForMonth(db: DB, childId: string, month: string): CalendarDay[] {
  const from = `${month}-01`;
  const to = `${month}-31`;

  const quests = db
    .prepare(
      `SELECT status, due_at, approved_at, completed_at FROM quests
       WHERE (child_id = ? OR shared = 1) AND (due_at >= ? AND due_at <= ? OR approved_at >= ? AND approved_at <= ?)`,
    )
    .all(childId, from, to, from, to) as {
    status: string;
    due_at: string | null;
    approved_at: string | null;
    completed_at: string | null;
  }[];

  const events = listEvents(db, { childId, from, to }).map((event) => ({
    id: event.id,
    title: event.title,
    startTime: event.start_time,
    durationMin: event.duration_min,
    status: event.status,
    place: event.geofence_id,
    date: event.date,
  }));

  const days = new Map<string, CalendarDay>();
  const ensure = (date: string) => {
    if (!days.has(date)) days.set(date, { date, questsDone: 0, questsPlanned: 0, events: [] });
    return days.get(date)!;
  };

  for (const quest of quests) {
    const planned = quest.due_at?.slice(0, 10);
    if (planned && planned >= from && planned <= to) ensure(planned).questsPlanned += 1;
    const done = (quest.status === "approved" ? quest.approved_at : quest.completed_at)?.slice(0, 10);
    if (done && done >= from && done <= to) ensure(done).questsDone += 1;
  }

  for (const event of events) {
    ensure(event.date).events.push({
      id: event.id,
      title: event.title,
      startTime: event.startTime,
      durationMin: event.durationMin,
      status: event.status,
      place: event.place,
    });
  }

  return [...days.values()].sort((a, b) => a.date.localeCompare(b.date));
}

export function monthBounds(month: string): { from: string; to: string } {
  return { from: `${month}-01`, to: `${month}-31` };
}
