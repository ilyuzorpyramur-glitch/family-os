import { formatMinor } from "../lib/money.js";
import type { AccountRow } from "../repo/accounts.js";
import type { CurrencySummary, TxView } from "../repo/transactions.js";

export function escapeHtml(value: unknown): string {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");
}

const CSS = `
:root {
  color-scheme: dark;
  --bg:#0b0f14; --panel:#121922; --panel2:#0f151d; --line:#1f2a37;
  --text:#e6edf3; --muted:#8b9bb0; --accent:#34d399; --accent2:#60a5fa;
  --red:#f87171; --amber:#fbbf24;
}
* { box-sizing: border-box; }
body {
  margin:0; background:var(--bg); color:var(--text);
  font:15px/1.5 -apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,Inter,sans-serif;
}
a { color:var(--accent2); text-decoration:none; }
a:hover { text-decoration:underline; }
.wrap { max-width:1040px; margin:0 auto; padding:20px 16px 56px; }
header.top { display:flex; align-items:center; justify-content:space-between; gap:12px; padding:8px 0 20px; }
.brand { display:flex; align-items:center; gap:10px; font-weight:650; letter-spacing:.2px; }
.brand .dot { width:10px; height:10px; border-radius:50%; background:var(--accent); box-shadow:0 0 12px var(--accent); }
nav { display:flex; align-items:center; gap:14px; font-size:14px; color:var(--muted); }
main { display:flex; flex-direction:column; gap:18px; }
.grid { display:grid; gap:12px; grid-template-columns:repeat(auto-fit,minmax(190px,1fr)); }
.card { background:var(--panel); border:1px solid var(--line); border-radius:14px; padding:16px; }
.card h2, .card h3 { margin:0 0 10px; font-size:13px; font-weight:600; color:var(--muted); text-transform:uppercase; letter-spacing:.6px; }
.big { font-size:28px; font-weight:650; font-variant-numeric:tabular-nums; }
.sub { color:var(--muted); font-size:13px; }
.pos { color:var(--accent); } .neg { color:var(--red); }
.row { display:flex; align-items:center; justify-content:space-between; gap:12px; }
.section-head { display:flex; align-items:center; justify-content:space-between; margin-bottom:10px; }
.section-head h2 { margin:0; font-size:15px; color:var(--text); }
table { width:100%; border-collapse:collapse; font-size:14px; }
th { text-align:left; color:var(--muted); font-weight:500; font-size:12px; text-transform:uppercase; letter-spacing:.5px; padding:8px 6px; border-bottom:1px solid var(--line); }
td { padding:9px 6px; border-bottom:1px solid var(--line); vertical-align:top; }
tr:last-child td { border-bottom:none; }
.num { text-align:right; font-variant-numeric:tabular-nums; white-space:nowrap; }
.muted { color:var(--muted); }
.tag { display:inline-block; padding:1px 8px; border-radius:999px; background:var(--panel2); border:1px solid var(--line); font-size:12px; color:var(--muted); }
button, .btn { background:var(--accent); color:#06231a; border:0; border-radius:10px; padding:9px 14px; font-weight:600; font-size:14px; cursor:pointer; }
button.ghost { background:transparent; color:var(--text); border:1px solid var(--line); }
button:hover { filter:brightness(1.05); }
form.inline { display:inline; }
input[type=password], input[type=text], input[type=date] {
  width:100%; background:var(--panel2); border:1px solid var(--line); color:var(--text);
  border-radius:10px; padding:10px 12px; font-size:14px;
}
label { display:block; font-size:13px; color:var(--muted); margin:0 0 6px; }
.field { margin-bottom:12px; }
.banner { border:1px solid #3b2f14; background:#1c1708; color:#fde68a; padding:10px 14px; border-radius:12px; font-size:14px; }
.banner.err { border-color:#4c1d1d; background:#1f0d0d; color:#fca5a5; }
.banner.ok { border-color:#123b2a; background:#0b1f16; color:#6ee7b7; }
.login { max-width:380px; margin:10vh auto; }
code { background:var(--panel2); border:1px solid var(--line); padding:2px 6px; border-radius:6px; font-size:13px; }
.list { display:flex; flex-direction:column; gap:2px; }
.li { display:flex; justify-content:space-between; gap:12px; padding:10px 0; border-bottom:1px solid var(--line); }
.li:last-child { border-bottom:none; }
@media (max-width:640px) {
  .hide-sm { display:none; }
  .big { font-size:24px; }
  header.top { flex-wrap:wrap; }
}
`;

function layout(title: string, body: string, opts: { nav?: boolean } = {}): string {
  const nav = opts.nav
    ? `<nav>
        <a href="/app">Обзор</a>
        <a href="/app/settings">Настройки</a>
        <a href="/docs">MCP</a>
        <form class="inline" method="post" action="/logout"><button class="ghost" type="submit">Выйти</button></form>
      </nav>`
    : "";
  return `<!doctype html>
<html lang="ru">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>${escapeHtml(title)} · Family OS</title>
<style>${CSS}</style>
</head>
<body>
<div class="wrap">
  <header class="top">
    <div class="brand"><span class="dot"></span>Family OS</div>
    ${nav}
  </header>
  ${body}
</div>
</body>
</html>`;
}

export function loginPage(error?: string): string {
  return layout(
    "Вход",
    `<main class="login">
      <div class="card">
        <h2>Вход в Family OS</h2>
        <p class="sub">Введите admin-токен. Он лежит в <code>data/admin-token.txt</code> на сервере
        или задан через <code>ADMIN_TOKEN</code>.</p>
        ${error ? `<div class="banner err">${escapeHtml(error)}</div>` : ""}
        <form method="post" action="/login">
          <div class="field" style="margin-top:12px">
            <label for="token">Токен</label>
            <input id="token" name="token" type="password" autocomplete="current-password" autofocus>
          </div>
          <button type="submit" style="width:100%">Войти</button>
        </form>
      </div>
    </main>`,
  );
}

export type DashboardData = {
  balances: { currency: string; balance: number }[];
  summary: CurrencySummary[];
  accounts: AccountRow[];
  transactions: TxView[];
  from: string;
  to: string;
  monthLabel: string;
  lastSyncAt: string | null;
  lastError: string | null;
  hasToken: boolean;
  flash: string | null;
};

function money(minor: number, currency: string): string {
  return escapeHtml(formatMinor(minor, currency));
}

function flashBlock(flash: string | null): string {
  if (!flash) return "";
  const messages: Record<string, string> = {
    sync: "Синхронизация завершена.",
    token: "Токен сохранён. Нажмите «Синхронизировать».",
    token_deleted: "Токен удалён.",
  };
  if (flash.startsWith("err:")) {
    return `<div class="banner err">${escapeHtml(flash.slice(4))}</div>`;
  }
  return `<div class="banner ok">${escapeHtml(messages[flash] ?? flash)}</div>`;
}

export function dashboardPage(data: DashboardData): string {
  const balanceCards = data.balances.length
    ? data.balances
        .map(
          (b) => `<div class="card">
            <h2>Баланс · ${escapeHtml(b.currency)}</h2>
            <div class="big ${b.balance < 0 ? "neg" : ""}">${money(b.balance, b.currency)}</div>
          </div>`,
        )
        .join("")
    : `<div class="card"><h2>Баланс</h2><div class="big">—</div><div class="sub">Подключите Zen Money в настройках</div></div>`;

  const summaryCards = data.summary
    .map(
      (s) => `<div class="card">
        <h2>${escapeHtml(data.monthLabel)} · ${escapeHtml(s.currency)}</h2>
        <div class="row"><span class="sub">Расход</span><span class="num neg">${money(s.expense, s.currency)}</span></div>
        <div class="row"><span class="sub">Доход</span><span class="num pos">${money(s.income, s.currency)}</span></div>
        <div class="row" style="margin-top:6px"><span class="sub">Итог</span><span class="num ${s.net < 0 ? "neg" : "pos"}">${money(s.net, s.currency)}</span></div>
      </div>`,
    )
    .join("");

  const accounts = data.accounts.length
    ? `<table>
        <thead><tr><th>Счёт</th><th class="hide-sm">Тип</th><th class="num">Баланс</th></tr></thead>
        <tbody>${data.accounts
          .map(
            (a) => `<tr>
              <td>${escapeHtml(a.title)}<div class="sub">${escapeHtml(a.currency)}</div></td>
              <td class="hide-sm muted">${escapeHtml(a.type)}</td>
              <td class="num ${a.balance_minor < 0 ? "neg" : ""}">${money(a.balance_minor, a.currency)}</td>
            </tr>`,
          )
          .join("")}</tbody>
      </table>`
    : `<p class="sub">Счетов пока нет. Подключите Zen Money.</p>`;

  const transactions = data.transactions.length
    ? `<table>
        <thead><tr><th>Дата</th><th>Операция</th><th class="hide-sm">Счёт</th><th class="num">Сумма</th></tr></thead>
        <tbody>${data.transactions
          .map((t) => {
            const title = t.payee || t.category_title || (t.kind === "transfer" ? "Перевод" : "Без описания");
            const meta = [t.category_title && t.payee ? t.category_title : null, t.comment].filter(Boolean).join(" · ");
            const amountClass = t.amount_minor < 0 ? "neg" : "pos";
            return `<tr>
              <td class="muted">${escapeHtml(t.occurred_at.slice(8, 10))}.${escapeHtml(t.occurred_at.slice(5, 7))}</td>
              <td>${escapeHtml(title)}${meta ? `<div class="sub">${escapeHtml(meta)}</div>` : ""}</td>
              <td class="hide-sm muted">${escapeHtml(t.account_title)}</td>
              <td class="num ${amountClass}">${money(t.amount_minor, t.currency)}</td>
            </tr>`;
          })
          .join("")}</tbody>
      </table>`
    : `<p class="sub">Операций за период нет.</p>`;

  return layout(
    "Обзор",
    `<main>
      ${flashBlock(data.flash)}
      ${!data.hasToken ? `<div class="banner">Zen Money ещё не подключён. <a href="/app/settings">Добавьте токен</a>, чтобы подтянуть счета и операции.</div>` : ""}
      ${data.lastError ? `<div class="banner err">Ошибка синхронизации: ${escapeHtml(data.lastError)}</div>` : ""}
      <div class="grid">${balanceCards}${summaryCards}</div>

      <section class="card">
        <div class="section-head">
          <h2>Синхронизация</h2>
          <form method="post" action="/app/sync"><button type="submit">Синхронизировать</button></form>
        </div>
        <div class="sub">Последняя синхронизация: ${data.lastSyncAt ? escapeHtml(data.lastSyncAt) + " UTC" : "никогда"}</div>
      </section>

      <section class="card">
        <div class="section-head"><h2>Счета</h2></div>
        ${accounts}
      </section>

      <section class="card">
        <div class="section-head"><h2>Последние операции</h2><span class="tag">${escapeHtml(data.from)} — ${escapeHtml(data.to)}</span></div>
        ${transactions}
      </section>
    </main>`,
    { nav: true },
  );
}

export type SettingsData = {
  hasToken: boolean;
  tokenMasked: string | null;
  lastSyncAt: string | null;
  lastError: string | null;
  flash: string | null;
  agentTokenConfigured: boolean;
  publicUrl: string;
};

export function settingsPage(data: SettingsData): string {
  return layout(
    "Настройки",
    `<main>
      ${flashBlock(data.flash)}
      <section class="card">
        <div class="section-head"><h2>Zen Money</h2><span class="tag">${data.hasToken ? "подключён" : "не подключён"}</span></div>
        <p class="sub">Токен выдаётся на <a href="https://zerro.app/token" target="_blank" rel="noreferrer">zerro.app/token</a>
        и даёт доступ к данным Дзен-мани через официальный API. Хранится зашифрованным (AES-256-GCM).</p>
        <form method="post" action="/app/settings/token">
          <div class="field">
            <label for="token">${data.hasToken ? `Заменить токен (сейчас ${escapeHtml(data.tokenMasked ?? "")})` : "Токен Zen Money"}</label>
            <input id="token" name="token" type="password" placeholder="Вставьте токен" autocomplete="off">
          </div>
          <div class="row">
            <button type="submit">Сохранить</button>
            ${data.hasToken ? `<button class="ghost" type="submit" formaction="/app/settings/token/delete">Удалить токен</button>` : ""}
          </div>
        </form>
        <div class="sub" style="margin-top:12px">Последняя синхронизация: ${data.lastSyncAt ? escapeHtml(data.lastSyncAt) + " UTC" : "никогда"}</div>
        ${data.lastError ? `<div class="banner err" style="margin-top:10px">${escapeHtml(data.lastError)}</div>` : ""}
      </section>

      <section class="card">
        <div class="section-head"><h2>OpenClaw / MCP</h2></div>
        <p class="sub">MCP-сервер доступен по адресу <code>${escapeHtml(data.publicUrl)}/mcp</code>.
        Авторизация — <code>Authorization: Bearer &lt;AGENT_TOKEN&gt;</code>.</p>
        <p class="sub">Статус агентского токена: ${data.agentTokenConfigured ? "задан" : "не задан (используется admin-токен)"}.</p>
        <p class="sub">Инструменты: <code>accounts_list</code>, <code>money_summary</code>,
        <code>transactions_list</code>, <code>categories_list</code>, <code>add_transaction</code>, <code>sync_now</code>.</p>
      </section>

      <section class="card">
        <div class="section-head"><h2>Опасная зона</h2></div>
        <p class="sub">Данные лежат в SQLite на сервере. Бэкап — копия файла <code>data/familyos.db</code>
        (остановите контейнер перед копированием).</p>
      </section>
    </main>`,
    { nav: true },
  );
}

export function docsPage(publicUrl: string): string {
  return layout(
    "MCP",
    `<main>
      <section class="card">
        <h2>Подключение OpenClaw</h2>
        <p class="sub">Family OS отдаёт MCP по HTTP (Streamable HTTP, JSON-RPC 2.0).</p>
        <pre style="overflow:auto;background:var(--panel2);padding:12px;border-radius:10px;border:1px solid var(--line)"><code>{
  "mcpServers": {
    "family-os": {
      "url": "${escapeHtml(publicUrl)}/mcp",
      "headers": { "Authorization": "Bearer $AGENT_TOKEN" }
    }
  }
}</code></pre>
        <h3 style="margin-top:18px">Инструменты</h3>
        <div class="list">
          <div class="li"><span><code>accounts_list</code><div class="sub">Счета и балансы</div></span></div>
          <div class="li"><span><code>money_summary</code><div class="sub">Доходы/расходы за период по валютам</div></span></div>
          <div class="li"><span><code>transactions_list</code><div class="sub">Поиск и список операций</div></span></div>
          <div class="li"><span><code>categories_list</code><div class="sub">Категории</div></span></div>
          <div class="li"><span><code>add_transaction</code><div class="sub">Добавить расход/доход/перевод (с аудитом)</div></span></div>
          <div class="li"><span><code>sync_now</code><div class="sub">Синхронизация с Zen Money</div></span></div>
        </div>
      </section>
    </main>`,
    { nav: true },
  );
}
