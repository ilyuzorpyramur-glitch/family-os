import { randomUUID } from "node:crypto";
import type { DB } from "../db.js";

export type PaymentRow = {
  id: string;
  child_id: string;
  amount: number;
  purpose: string | null;
  qr_payload: string;
  merchant: string | null;
  status: string;
  note: string | null;
  decided_at: string | null;
  created_at: string;
};

export type ParsedQr = {
  /** Это похоже на платёжный QR СБП */
  isPayment: boolean;
  /** Сумма в рублях, если она есть в коде */
  amount: number | null;
  /** Идентификатор банка-получателя, если есть */
  bank: string | null;
  /** Текст-назначение, если он есть в коде */
  purpose: string | null;
  scheme: "sbp" | "emv" | "unknown";
};

/**
 * Платёжные QR СБП бывают двух видов:
 *  1) ссылка вида https://qr.nspk.ru/AS1A...?type=01&bank=...&sum=12000&cur=RUB
 *  2) EMV-строка, начинающаяся с 000201
 * Сумма в ссылке приходит в копейках.
 */
export function parsePaymentQr(raw: string): ParsedQr {
  const text = (raw ?? "").trim();
  if (!text) return { isPayment: false, amount: null, bank: null, purpose: null, scheme: "unknown" };

  if (text.startsWith("000201") || text.includes("ST00012")) {
    return { isPayment: true, amount: null, bank: null, purpose: null, scheme: "emv" };
  }

  const isSbp = /qr\.nspk\.ru/i.test(text) || /^sbp:/i.test(text) || /nspk/i.test(text);
  if (!isSbp) return { isPayment: false, amount: null, bank: null, purpose: null, scheme: "unknown" };

  const query = text.includes("?") ? text.substring(text.indexOf("?") + 1) : "";
  const params = new URLSearchParams(query.split("&").filter((part) => part.includes("=")).join("&"));
  const sumRaw = params.get("sum") ?? params.get("amount") ?? "";
  const digits = sumRaw.replace(/[^\d.]/g, "");
  let amount: number | null = null;
  if (digits) {
    const value = Number(digits);
    if (Number.isFinite(value) && value > 0) {
      amount = sumRaw.includes(".") ? value : value / 100;
    }
  }
  const purpose = params.get("purpose") ?? params.get("name") ?? params.get("comment") ?? null;
  return {
    isPayment: true,
    amount: amount === null ? null : Math.round(amount * 100) / 100,
    bank: params.get("bank"),
    purpose: purpose && purpose.trim() ? purpose.trim() : null,
    scheme: "sbp",
  };
}

export type PayLimits = { perRequest: number; daily: number };

export function childPayLimits(db: DB, childId: string): PayLimits {
  const row = db.prepare("SELECT pay_limit, pay_daily_limit FROM children WHERE id = ?").get(childId) as
    | { pay_limit: number; pay_daily_limit: number }
    | undefined;
  return { perRequest: row?.pay_limit ?? 1500, daily: row?.pay_daily_limit ?? 3000 };
}

export function spentToday(db: DB, childId: string): number {
  const row = db
    .prepare(
      `SELECT COALESCE(SUM(amount), 0) AS total FROM payment_requests
       WHERE child_id = ? AND date(created_at) = date('now') AND status IN ('pending', 'paid')`,
    )
    .get(childId) as { total: number } | undefined;
  return Math.round((row?.total ?? 0) * 100) / 100;
}

export type CreatePaymentResult =
  | { ok: true; payment: PaymentRow }
  | { ok: false; code: string; message: string };

export function createPaymentRequest(
  db: DB,
  input: { childId: string; amount: number; purpose?: string | null; qrPayload: string; merchant?: string | null },
): CreatePaymentResult {
  const limits = childPayLimits(db, input.childId);
  const amount = Math.round(input.amount * 100) / 100;

  if (!Number.isFinite(amount) || amount <= 0) {
    return { ok: false, code: "VALIDATION", message: "Сумма должна быть больше нуля" };
  }
  if (amount > limits.perRequest) {
    return {
      ok: false,
      code: "OVER_LIMIT",
      message: `Один запрос — не больше ${limits.perRequest} ₽`,
    };
  }
  const spent = spentToday(db, input.childId);
  if (spent + amount > limits.daily) {
    return {
      ok: false,
      code: "DAILY_LIMIT",
      message: `Дневной лимит ${limits.daily} ₽. Уже запрошено ${spent} ₽`,
    };
  }

  const id = `pay_${randomUUID()}`;
  db.prepare(
    `INSERT INTO payment_requests (id, child_id, amount, purpose, qr_payload, merchant)
     VALUES (?, ?, ?, ?, ?, ?)`,
  ).run(id, input.childId, amount, input.purpose ?? null, input.qrPayload, input.merchant ?? null);
  return { ok: true, payment: getPayment(db, id)! };
}

export function getPayment(db: DB, id: string): PaymentRow | undefined {
  return db.prepare("SELECT * FROM payment_requests WHERE id = ?").get(id) as PaymentRow | undefined;
}

export function listPayments(
  db: DB,
  opts: { childId?: string; status?: string; limit?: number } = {},
): (PaymentRow & { child_name: string })[] {
  const clauses: string[] = [];
  const params: unknown[] = [];
  if (opts.childId) {
    clauses.push("p.child_id = ?");
    params.push(opts.childId);
  }
  if (opts.status) {
    clauses.push("p.status = ?");
    params.push(opts.status);
  }
  const where = clauses.length ? "WHERE " + clauses.join(" AND ") : "";
  return db
    .prepare(
      `SELECT p.*, c.name AS child_name FROM payment_requests p
       JOIN children c ON c.id = p.child_id
       ${where} ORDER BY p.created_at DESC LIMIT ?`,
    )
    .all(...params, opts.limit ?? 50) as (PaymentRow & { child_name: string })[];
}

export function decidePayment(
  db: DB,
  id: string,
  status: "paid" | "declined",
  note?: string | null,
): PaymentRow | undefined {
  db.prepare(
    `UPDATE payment_requests SET status = ?, note = ?, decided_at = datetime('now') WHERE id = ? AND status = 'pending'`,
  ).run(status, note ?? null, id);
  return getPayment(db, id);
}

/** Ссылка для банковского приложения: сам QR отдаём как есть. */
export function payUrl(payment: PaymentRow): string {
  return payment.qr_payload;
}
