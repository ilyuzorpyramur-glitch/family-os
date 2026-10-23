const ZENMONEY_URL = "https://api.zenmoney.ru/v8/diff/";

export type ZenInstrument = { id: number; title: string; shortTitle: string; symbol?: string; rate?: number };
export type ZenAccount = {
  id: string;
  changed: number;
  title: string;
  type: string;
  instrument: number;
  balance?: number | null;
  inBalance: boolean;
  archive: boolean;
};
export type ZenTag = {
  id: string;
  changed: number;
  title: string;
  parent?: string | null;
  budgetIncome?: boolean;
  budgetOutcome?: boolean;
  archive?: boolean;
};
export type ZenTransaction = {
  id: string;
  changed: number;
  deleted?: boolean;
  user?: number;
  date: string;
  income: number;
  outcome: number;
  incomeAccount: string;
  outcomeAccount: string;
  incomeInstrument: number;
  outcomeInstrument: number;
  tag?: string[] | null;
  payee?: string | null;
  comment?: string | null;
};
export type ZenDeletion = { id: string; object: string; stamp: number; user: number };

export type ZenDiff = {
  currentClientTimestamp?: number;
  serverTimestamp: number;
  instrument?: ZenInstrument[];
  account?: ZenAccount[];
  tag?: ZenTag[];
  transaction?: ZenTransaction[];
  deletion?: ZenDeletion[];
};

export class ZenMoneyError extends Error {
  constructor(
    message: string,
    readonly status?: number,
  ) {
    super(message);
    this.name = "ZenMoneyError";
  }
}

export async function fetchZenDiff(token: string, serverTimestamp: number): Promise<ZenDiff> {
  let response: Response;
  try {
    response = await fetch(ZENMONEY_URL, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${token}`,
        "Content-Type": "application/json",
        "User-Agent": "FamilyOS/0.1 (+https://github.com/ilyuzorpyramur-glitch/family-os)",
      },
      body: JSON.stringify({
        currentClientTimestamp: Math.floor(Date.now() / 1000),
        serverTimestamp,
      }),
      signal: AbortSignal.timeout(25_000),
    });
  } catch (error) {
    throw new ZenMoneyError(`Не удалось связаться с Zen Money: ${(error as Error).message}`);
  }

  if (response.status === 401 || response.status === 403) {
    throw new ZenMoneyError("Zen Money отклонил токен (401/403). Проверьте токен на zerro.app/token", response.status);
  }
  if (!response.ok) {
    throw new ZenMoneyError(`Zen Money вернул HTTP ${response.status}`, response.status);
  }

  return (await response.json()) as ZenDiff;
}
