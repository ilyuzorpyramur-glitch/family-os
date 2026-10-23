/** Деньги храним целыми числами в минимальных единицах (копейки/гроши). */

export function toMinor(value: number | string): number {
  const n = typeof value === "string" ? Number(value) : value;
  if (!Number.isFinite(n)) throw new Error(`Некорректная сумма: ${value}`);
  return Math.round(n * 100);
}

export function fromMinor(minor: number): number {
  return Math.round(minor) / 100;
}

export function formatMinor(minor: number, currency: string, locale = "ru-RU"): string {
  return new Intl.NumberFormat(locale, {
    style: "currency",
    currency,
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(fromMinor(minor));
}

export function absMinor(minor: number): number {
  return Math.abs(minor);
}
