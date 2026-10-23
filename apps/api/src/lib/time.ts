export function todayISO(): string {
  return new Date().toISOString().slice(0, 10);
}

export function monthStartISO(date = new Date()): string {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-01`;
}

export function isValidISODate(value: string): boolean {
  return /^\d{4}-\d{2}-\d{2}$/.test(value) && !Number.isNaN(Date.parse(value));
}

export function normalizeDate(value: string): string {
  return value.slice(0, 10);
}
