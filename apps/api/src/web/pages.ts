import { formatMinor } from "../lib/money.js";
import type { AccountRow } from "../repo/accounts.js";
import type { CurrencySummary, TxView } from "../repo/transactions.js";
import type { GuardEventRow, GuardOverview } from "../repo/guard.js";
import type { QuestOverview } from "../repo/quest.js";

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
.bar { height:8px; border-radius:999px; background:var(--panel2); border:1px solid var(--line); overflow:hidden; margin:8px 0 4px; }
.bar > span { display:block; height:100%; background:var(--accent); }
.bar.warn > span { background:var(--amber); }
.bar.full > span { background:var(--red); }
.kv { display:flex; justify-content:space-between; gap:10px; font-size:13px; color:var(--muted); }
.split { display:grid; gap:14px; grid-template-columns:repeat(auto-fit,minmax(300px,1fr)); }
.mini-form { display:flex; gap:8px; align-items:end; flex-wrap:wrap; }
.mini-form .field { margin:0; flex:1 1 90px; }
.mini-form input { padding:7px 9px; }
.mini-form button { padding:7px 12px; }
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
        <a href="/app/guard">Guard</a>
        <a href="/app/quests">Задания</a>
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
    guard_child: "Ребёнок добавлен.",
    guard_policy: "Политика обновлена.",
    guard_approved: "Время разрешено.",
    guard_denied: "Запрос отклонён.",
    geofence_added: "Геозона добавлена.",
    geofence_deleted: "Геозона удалена.",
    quest_created: "Задание создано.",
    quest_approved: "Задание одобрено, награда начислена.",
    quest_denied: "Задание отклонено.",
    shop_item: "Товар добавлен в магазин.",
    purchase_approved: "Покупка одобрена.",
    purchase_denied: "Покупка отклонена.",
    proposal_priced: "Цена установлена, товар в магазине.",
    proposal_rejected: "Предложение отклонено.",
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
        <code>transactions_list</code>, <code>categories_list</code>, <code>add_transaction</code>, <code>sync_now</code>,
        <code>guard_overview</code>, <code>guard_events</code>, <code>guard_set_limit</code>, <code>guard_approve_request</code>,
        <code>quest_list</code>, <code>quest_create</code>, <code>quest_approve</code>,
        <code>shop_list</code>, <code>shop_add</code>, <code>purchase_decide</code>.</p>
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

export function docsPage(publicUrl: string): string {  return layout(
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

function minutesLabel(minutes: number): string {
  const h = Math.floor(minutes / 60);
  const m = minutes % 60;
  return h > 0 ? `${h} ч ${m} мин` : `${m} мин`;
}

export function guardPage(
  data: GuardOverview,
  events: GuardEventRow[],
  flash: string | null,
): string {
  const cards = data.children.length
    ? data.children
        .map((child) => {
          const pct = child.limitMinutes > 0 ? Math.min(100, Math.round((child.usedMinutes / child.limitMinutes) * 100)) : 100;
          const barClass = pct >= 100 ? "bar full" : pct >= 80 ? "bar warn" : "bar";
          const requests = child.pendingRequests.length
            ? child.pendingRequests
                .map(
                  (r) => `<div class="li">
                    <span>+${r.minutes} мин${r.reason ? `<div class="sub">${escapeHtml(r.reason)}</div>` : ""}</span>
                    <span>
                      <form class="inline" method="post" action="/app/guard/requests/${escapeHtml(r.id)}/decide">
                        <input type="hidden" name="approve" value="yes"><button type="submit">Разрешить</button>
                      </form>
                      <form class="inline" method="post" action="/app/guard/requests/${escapeHtml(r.id)}/decide">
                        <input type="hidden" name="approve" value="no"><button class="ghost" type="submit">Отклонить</button>
                      </form>
                    </span>
                  </div>`,
                )
                .join("")
            : `<div class="sub">Нет активных запросов.</div>`;

          const devices = child.devices.length
            ? child.devices
                .map(
                  (d) => `<div class="li"><span>${escapeHtml(d.name)}</span><span class="sub">${
                    d.lastSeenAt ? "на связи " + escapeHtml(d.lastSeenAt) + " UTC" : "ещё не подключалось"
                  }</span></div>`,
                )
                .join("")
            : `<div class="sub">Устройство не привязано.</div>`;

          return `<section class="card">
            <div class="section-head">
              <h2>${escapeHtml(child.name)}</h2>
              <span class="tag">${minutesLabel(child.usedMinutes)} из ${minutesLabel(child.limitMinutes)}</span>
            </div>
            <div class="${barClass}"><span style="width:${pct}%"></span></div>
            <div class="kv"><span>Осталось сегодня: ${minutesLabel(child.remainingMinutes)}</span><span>${pct}%</span></div>

            <form method="post" action="/app/guard/children/${escapeHtml(child.id)}/policy" style="margin-top:14px">
              <div class="mini-form">
                <div class="field"><label>Лимит, мин</label><input type="number" name="screenLimitMin" min="0" max="1440" value="${child.policy.screen_limit_min}"></div>
                <div class="field"><label>Отбой</label><input type="text" name="bedtimeStart" pattern="\\d{2}:\\d{2}" value="${escapeHtml(child.policy.bedtime_start)}"></div>
                <div class="field"><label>Подъём</label><input type="text" name="bedtimeEnd" pattern="\\d{2}:\\d{2}" value="${escapeHtml(child.policy.bedtime_end)}"></div>
                <button type="submit">Сохранить</button>
              </div>
            </form>

            <h3 style="margin-top:16px">Запросы времени</h3>
            ${requests}

            <h3 style="margin-top:16px">Устройства</h3>
            ${devices}
            <form class="mini-form" method="post" action="/app/guard/children/${escapeHtml(child.id)}/devices" style="margin-top:10px">
              <div class="field"><label>Название телефона</label><input name="name" placeholder="Телефон Алисы" required></div>
              <button type="submit">Привязать</button>
            </form>

            <h3 style="margin-top:16px">Геозоны</h3>
            ${(child.geofences ?? []).length
              ? (child.geofences ?? [])
                  .map(
                    (g) => `<div class="li">
                      <span>${escapeHtml(g.label)}<div class="sub">${g.lat.toFixed(5)}, ${g.lng.toFixed(5)} · ${g.radius_m} м</div></span>
                      <form class="inline" method="post" action="/app/guard/geofences/${escapeHtml(g.id)}/delete">
                        <button class="ghost" type="submit">Удалить</button>
                      </form>
                    </div>`,
                  )
                  .join("")
              : `<div class="sub">Геозон нет.</div>`}
            <form class="mini-form" method="post" action="/app/guard/children/${escapeHtml(child.id)}/geofences" style="margin-top:10px">
              <div class="field"><label>Название</label><input name="label" placeholder="Школа" required></div>
              <div class="field"><label>Широта</label><input name="lat" placeholder="52.2297" required></div>
              <div class="field"><label>Долгота</label><input name="lng" placeholder="21.0122" required></div>
              <div class="field"><label>Радиус, м</label><input name="radiusM" value="150" min="30" max="5000"></div>
              <button type="submit">Добавить</button>
            </form>
          </section>`;
        })
        .join("")
    : `<section class="card"><h2>Дети</h2><p class="sub">Пока никого нет. Добавьте ребёнка, чтобы настроить лимиты.</p></section>`;

  const eventRows = events.length
    ? events
        .map(
          (e) => `<tr>
            <td class="muted">${escapeHtml(e.occurred_at.slice(0, 16).replace("T", " "))}</td>
            <td>${escapeHtml(e.kind)}</td>
            <td class="muted hide-sm">${escapeHtml(e.payload ?? "")}</td>
          </tr>`,
        )
        .join("")
    : "";

  return layout(
    "Guard",
    `<main>
      ${flashBlock(flash)}
      <div class="banner">
        Серверная часть Guard готова: политики, лимиты, запросы времени и события устройств.
        Android-приложение (Device Owner, UsageStats, фильтр) — следующий шаг. Протокол устройства:
        <code>docs/GUARD-DEVICE-API.md</code>.
      </div>
      <div class="split">${cards}</div>

      <section class="card">
        <div class="section-head"><h2>Добавить ребёнка</h2></div>
        <form class="mini-form" method="post" action="/app/guard/children">
          <div class="field"><label>Имя</label><input name="name" placeholder="Алиса" required></div>
          <button type="submit">Добавить</button>
        </form>
      </section>

      <section class="card">
        <div class="section-head"><h2>События</h2><span class="tag">${escapeHtml(data.date)}</span></div>
        ${
          events.length
            ? `<table><thead><tr><th>Время</th><th>Тип</th><th class="hide-sm">Данные</th></tr></thead><tbody>${eventRows}</tbody></table>`
            : `<p class="sub">Событий пока нет.</p>`
        }
      </section>
    </main>`,
    { nav: true },
  );
}

export function deviceTokenPage(childName: string, deviceName: string, token: string): string {
  return layout(
    "Устройство привязано",
    `<main>
      <section class="card">
        <h2>Токен устройства</h2>
        <p class="sub">${escapeHtml(childName)} · ${escapeHtml(deviceName)}</p>
        <p>Вставьте этот токен в приложение Family OS Guard на телефоне. Он показывается один раз —
        сервер хранит только хеш.</p>
        <pre style="overflow:auto;background:var(--panel2);padding:12px;border-radius:10px;border:1px solid var(--line)"><code>${escapeHtml(token)}</code></pre>
        <p class="sub">Дальше: включите Device Owner через QR/ADB, выдайте доступ к Usage Access и уведомлениям.</p>
        <p><a class="btn" href="/app/guard" style="display:inline-block">Вернуться в Guard</a></p>
      </section>
    </main>`,
    { nav: true },
  );
}

const STAT_LABELS: Record<string, string> = {
  strength: "Сила",
  intellect: "Интеллект",
  creativity: "Творчество",
  kindness: "Доброта",
  discipline: "Дисциплина",
};

export function questsPage(data: QuestOverview, flash: string | null, publicUrl = ""): string {
  const childCards = data.children.length
    ? data.children
        .map((child) => {
          const p = child.profile;
          const pct = Math.round((p.progress ?? 0) * 100);
          const stats = Object.entries(STAT_LABELS)
            .map(([key, label]) => {
              const value = (p as unknown as Record<string, number>)[key] ?? 0;
              return `<span class="tag">${label} ${value}</span>`;
            })
            .join(" ");

          const active = child.active.length
            ? child.active
                .map(
                  (q) => `<div class="li">
                    <span>${escapeHtml(q.title)}
                      <div class="sub">${q.xp} XP · ${q.coins} монет${q.stat ? " · " + escapeHtml(STAT_LABELS[q.stat] ?? q.stat) : ""}</div>
                    </span>
                    <span class="tag">активно</span>
                  </div>`,
                )
                .join("")
            : `<div class="sub">Нет активных заданий.</div>`;

          const pending = child.pending.length
            ? child.pending
                .map(
                  (q) => `<div class="li">
                    <span>${escapeHtml(q.title)}
                      <div class="sub">+${q.xp} XP · +${q.coins} монет · ${
                        q.approval === "agent" ? "подтверждает OpenClaw" : "подтверждает родитель"
                      }${q.photos_required > 0 ? ` · фото ${q.photos_required}` : ""}</div>
                      ${(q.photos ?? []).length
                        ? `<div class="sub">${(q.photos ?? [])
                            .map(
                              (ph) =>
                                `<a href="${escapeHtml(publicUrl)}/uploads/${escapeHtml(ph.path)}" target="_blank" rel="noreferrer">фото</a>`,
                            )
                            .join(" · ")}</div>`
                        : ""}
                    </span>
                    <span>
                      <form class="inline" method="post" action="/app/quests/${escapeHtml(q.id)}/decide">
                        <input type="hidden" name="approve" value="yes"><button type="submit">Одобрить</button>
                      </form>
                      <form class="inline" method="post" action="/app/quests/${escapeHtml(q.id)}/decide">
                        <input type="hidden" name="approve" value="no"><button class="ghost" type="submit">Отклонить</button>
                      </form>
                    </span>
                  </div>`,
                )
                .join("")
            : `<div class="sub">Нет заданий на проверке.</div>`;

          const purchases = child.purchases.length
            ? child.purchases
                .map(
                  (buy) => `<div class="li">
                    <span>${escapeHtml(buy.item_title)}
                      <div class="sub">${buy.price_coins} монет</div>
                    </span>
                    <span>
                      <form class="inline" method="post" action="/app/quests/purchases/${escapeHtml(buy.id)}/decide">
                        <input type="hidden" name="approve" value="yes"><button type="submit">Выдать</button>
                      </form>
                      <form class="inline" method="post" action="/app/quests/purchases/${escapeHtml(buy.id)}/decide">
                        <input type="hidden" name="approve" value="no"><button class="ghost" type="submit">Отклонить</button>
                      </form>
                    </span>
                  </div>`,
                )
                .join("")
            : `<div class="sub">Нет запросов на покупку.</div>`;

          return `<section class="card">
            <div class="section-head">
              <h2>${escapeHtml(child.name)}</h2>
              <span class="tag">Уровень ${p.level} · ${p.coins} монет</span>
            </div>
            <div class="bar"><span style="width:${pct}%"></span></div>
            <div class="kv"><span>${p.xp} XP</span><span>до ${p.level + 1} уровня: ${p.xpForNextLevel} XP</span></div>
            <div style="margin-top:10px">${stats}</div>

            <h3 style="margin-top:16px">Активные задания</h3>
            ${active}

            <h3 style="margin-top:16px">На проверке</h3>
            ${pending}

            <h3 style="margin-top:16px">Покупки</h3>
            ${purchases}

            <h3 style="margin-top:16px">Новое задание</h3>
            <form method="post" action="/app/quests/children/${escapeHtml(child.id)}/create">
              <div class="field"><label>Что сделать</label><input name="title" placeholder="Помыть посуду" required></div>
              <div class="mini-form">
                <div class="field"><label>XP</label><input type="number" name="xp" value="10" min="0" max="1000"></div>
                <div class="field"><label>Монеты</label><input type="number" name="coins" value="5" min="0" max="1000"></div>
                <div class="field"><label>Характеристика</label>
                  <select name="stat" style="width:100%;background:var(--panel2);border:1px solid var(--line);color:var(--text);border-radius:10px;padding:9px">
                    <option value="discipline">Дисциплина</option>
                    <option value="intellect">Интеллект</option>
                    <option value="strength">Сила</option>
                    <option value="creativity">Творчество</option>
                    <option value="kindness">Доброта</option>
                  </select>
                </div>
                <div class="field"><label>Подтверждение</label>
                  <select name="approval" style="width:100%;background:var(--panel2);border:1px solid var(--line);color:var(--text);border-radius:10px;padding:9px">
                    <option value="parent">Родитель</option>
                    <option value="agent">OpenClaw</option>
                  </select>
                </div>
                <div class="field"><label>Фото, шт.</label><input type="number" name="photosRequired" value="0" min="0" max="10"></div>
                <button type="submit">Создать</button>
              </div>
            </form>
          </section>`;
        })
        .join("")
    : `<section class="card"><h2>Дети</h2><p class="sub">Сначала добавьте ребёнка в Guard.</p></section>`;

  const shopItems = data.shop.length
    ? data.shop
        .map(
          (item) => `<div class="li">
            <span>${escapeHtml(item.title)}${item.description ? `<div class="sub">${escapeHtml(item.description)}</div>` : ""}</span>
            <span class="tag">${item.price_coins} монет${item.stock === null ? "" : " · остаток " + item.stock}</span>
          </div>`,
        )
        .join("")
    : `<p class="sub">Магазин пуст.</p>`;

  return layout(
    "Задания",
    `<main>
      ${flashBlock(flash)}
      <div class="banner">Задания может создавать родитель в панели или OpenClaw через MCP.
      Ребёнок отмечает выполнение на телефоне, награда начисляется после подтверждения.</div>
      <div class="split">${childCards}</div>

      <section class="card">
        <div class="section-head"><h2>Магазин наград</h2></div>
        ${shopItems}
        <h3 style="margin-top:16px">Предложения детей</h3>
        ${
          data.proposals.length
            ? data.proposals
                .map(
                  (proposal) => `<div class="li">
                    <span>${escapeHtml(proposal.title)}
                      <div class="sub">${escapeHtml(proposal.description ?? "")}
                      ${proposal.photo_path ? ` · <a href="${escapeHtml(publicUrl)}/uploads/${escapeHtml(proposal.photo_path)}" target="_blank" rel="noreferrer">фото</a>` : ""}</div>
                    </span>
                    <span>
                      <form class="inline" method="post" action="/app/quests/proposals/${escapeHtml(proposal.id)}/price">
                        <input type="number" name="priceCoins" min="1" placeholder="цена" style="width:80px;padding:6px">
                        <button type="submit">Цена</button>
                      </form>
                      <form class="inline" method="post" action="/app/quests/proposals/${escapeHtml(proposal.id)}/reject">
                        <button class="ghost" type="submit">Отклонить</button>
                      </form>
                    </span>
                  </div>`,
                )
                .join("")
            : `<p class="sub">Нет новых предложений.</p>`
        }

        <h3 style="margin-top:16px">Добавить товар</h3>
        <form method="post" action="/app/quests/shop/items">
          <div class="field"><label>Название</label><input name="title" placeholder="Поход в кино" required></div>
          <div class="mini-form">
            <div class="field"><label>Цена, монет</label><input type="number" name="priceCoins" value="20" min="1"></div>
            <div class="field"><label>Тип</label>
              <select name="kind" style="width:100%;background:var(--panel2);border:1px solid var(--line);color:var(--text);border-radius:10px;padding:9px">
                <option value="privilege">Привилегия</option>
                <option value="screen_time">Экранное время</option>
                <option value="money">Деньги</option>
                <option value="custom">Другое</option>
              </select>
            </div>
            <div class="field"><label>Остаток (пусто = бесконечно)</label><input type="number" name="stock" min="0" placeholder="—"></div>
            <button type="submit">Добавить</button>
          </div>
        </form>
      </section>
    </main>`,
    { nav: true },
  );
}
