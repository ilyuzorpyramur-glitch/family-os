import { test } from "node:test";
import assert from "node:assert/strict";
import { buildApp } from "../app.js";
import type { Config } from "../config.js";
import { openDb } from "../db.js";
import { upsertAccount } from "../repo/accounts.js";

function testConfig(overrides: Partial<Config> = {}): Config {
  return {
    host: "127.0.0.1",
    port: 0,
    dbFile: ":memory:",
    dataDir: "/tmp/familyos-test",
    secret: "test-secret",
    adminToken: "admin-test-token",
    agentToken: "agent-test-token",
    zenmoneyToken: null,
    baseCurrency: "PLN",
    publicUrl: "http://localhost:3001",
    ...overrides,
  };
}

test("API: защита токеном и создание операции", async () => {
  const db = openDb(":memory:");
  const account = upsertAccount(db, { title: "Кошелёк", currency: "PLN", balanceMinor: 10000 });
  const app = await buildApp({ config: testConfig(), db });

  const unauthorized = await app.inject({ method: "GET", url: "/api/accounts" });
  assert.equal(unauthorized.statusCode, 401);

  const health = await app.inject({ method: "GET", url: "/api/health" });
  assert.equal(health.statusCode, 200);

  const created = await app.inject({
    method: "POST",
    url: "/api/transactions",
    headers: { authorization: "Bearer admin-test-token" },
    payload: { amount: 12.5, accountId: account.id, kind: "expense", payee: "Кофе", date: "2026-10-23" },
  });
  assert.equal(created.statusCode, 200);
  const body = created.json() as { ok: boolean; data: { amount_minor: number } };
  assert.equal(body.ok, true);
  assert.equal(body.data.amount_minor, -1250);

  const summary = await app.inject({
    method: "GET",
    url: "/api/summary?from=2026-10-01&to=2026-10-31",
    headers: { authorization: "Bearer agent-test-token" },
  });
  assert.equal(summary.statusCode, 200);
  const summaryBody = summary.json() as { data: { byCurrency: { expense: number }[]; balances: { balance: number }[] } };
  assert.equal(summaryBody.data.byCurrency[0]?.expense, 12.5);
  assert.equal(summaryBody.data.balances[0]?.balance, 87.5);

  await app.close();
});

test("MCP: initialize, tools/list и tools/call", async () => {
  const db = openDb(":memory:");
  upsertAccount(db, { title: "Карта", currency: "PLN", balanceMinor: 5000 });
  const app = await buildApp({ config: testConfig(), db });

  const init = await app.inject({
    method: "POST",
    url: "/mcp",
    headers: { authorization: "Bearer agent-test-token", "content-type": "application/json" },
    payload: { jsonrpc: "2.0", id: 1, method: "initialize", params: { protocolVersion: "2025-06-18" } },
  });
  assert.equal(init.statusCode, 200);
  assert.equal((init.json() as { result: { serverInfo: { name: string } } }).result.serverInfo.name, "family-os");

  const list = await app.inject({
    method: "POST",
    url: "/mcp",
    headers: { authorization: "Bearer agent-test-token" },
    payload: { jsonrpc: "2.0", id: 2, method: "tools/list" },
  });
  const tools = (list.json() as { result: { tools: { name: string }[] } }).result.tools.map((t) => t.name);
  assert.ok(tools.includes("money_summary"));
  assert.ok(tools.includes("add_transaction"));

  const call = await app.inject({
    method: "POST",
    url: "/mcp",
    headers: { authorization: "Bearer agent-test-token" },
    payload: { jsonrpc: "2.0", id: 3, method: "tools/call", params: { name: "money_summary", arguments: {} } },
  });
  assert.equal(call.statusCode, 200);
  const callBody = call.json() as { result: { structuredContent: { balances: { currency: string }[] } } };
  assert.equal(callBody.result.structuredContent.balances[0]?.currency, "PLN");

  await app.close();
});
