import { test } from "node:test";
import assert from "node:assert/strict";
import { buildApp } from "../app.js";
import type { Config } from "../config.js";
import { openDb } from "../db.js";

function testConfig(): Config {
  return {
    host: "127.0.0.1",
    port: 0,
    dbFile: ":memory:",
    dataDir: "/tmp/familyos-test",
    secret: "***",
    adminToken: "admin-test-token",
    agentToken: "agent-test-token",
    zenmoneyToken: null,
    baseCurrency: "PLN",
    publicUrl: "http://localhost:3001",
  };
}

function auth(value: string): Record<string, string> {
  return { authorization: ["Bearer", value].join(" ") };
}

const ADMIN = auth("admin-test-token");
const AGENT = auth("agent-test-token");

test("Quest: задание, выполнение, одобрение, XP, уровень и монеты", async () => {
  const db = openDb(":memory:");
  const app = await buildApp({ config: testConfig(), db });

  const childRes = await app.inject({
    method: "POST",
    url: "/api/guard/children",
    headers: ADMIN,
    payload: { name: "Алиса" },
  });
  const childId = (childRes.json() as { data: { id: string } }).data.id;

  const deviceRes = await app.inject({
    method: "POST",
    url: `/api/guard/children/${childId}/devices`,
    headers: ADMIN,
    payload: { name: "Pixel" },
  });
  const deviceKey = (deviceRes.json() as any).data.token;
  const DEVICE = auth(deviceKey);

  const questRes = await app.inject({
    method: "POST",
    url: `/api/guard/children/${childId}/quests`,
    headers: ADMIN,
    payload: { title: "Прочитать главу", xp: 50, coins: 10, stat: "intellect" },
  });
  assert.equal(questRes.statusCode, 200);
  const questId = (questRes.json() as { data: { id: string } }).data.id;

  const list = await app.inject({ method: "GET", url: "/api/device/quests", headers: DEVICE });
  const listBody = list.json() as {
    data: { active: unknown[]; profile: { level: number; coins: number } };
  };
  assert.equal(listBody.data.active.length, 1);
  assert.equal(listBody.data.profile.level, 1);
  assert.equal(listBody.data.profile.coins, 0);

  const complete = await app.inject({
    method: "POST",
    url: `/api/device/quests/${questId}/complete`,
    headers: DEVICE,
  });
  assert.equal(complete.statusCode, 200);

  const decide = await app.inject({
    method: "POST",
    url: `/api/guard/quests/${questId}/decide`,
    headers: ADMIN,
    payload: { approve: true },
  });
  assert.equal(decide.statusCode, 200);
  const decided = decide.json() as { data: { profile: { xp: number; level: number; coins: number; intellect: number } } };
  assert.equal(decided.data.profile.xp, 50);
  assert.equal(decided.data.profile.level, 2);
  assert.equal(decided.data.profile.coins, 10);
  assert.equal(decided.data.profile.intellect, 5);

  await app.close();
});

test("Shop: покупка уходит родителю и списывает монеты после одобрения", async () => {
  const db = openDb(":memory:");
  const app = await buildApp({ config: testConfig(), db });

  const childRes = await app.inject({
    method: "POST",
    url: "/api/guard/children",
    headers: ADMIN,
    payload: { name: "Миша" },
  });
  const childId = (childRes.json() as { data: { id: string } }).data.id;

  const deviceRes = await app.inject({
    method: "POST",
    url: `/api/guard/children/${childId}/devices`,
    headers: ADMIN,
    payload: { name: "Телефон" },
  });
  const deviceKey = (deviceRes.json() as any).data.token;
  const DEVICE = auth(deviceKey);

  // Заработать 10 монет
  const questRes = await app.inject({
    method: "POST",
    url: `/api/guard/children/${childId}/quests`,
    headers: AGENT,
    payload: { title: "Убрать комнату", xp: 20, coins: 10 },
  });
  const questId = (questRes.json() as { data: { id: string } }).data.id;
  await app.inject({ method: "POST", url: `/api/device/quests/${questId}/complete`, headers: DEVICE });
  await app.inject({ method: "POST", url: `/api/guard/quests/${questId}/decide`, headers: ADMIN, payload: { approve: true } });

  const cheap = await app.inject({
    method: "POST",
    url: "/api/guard/shop/items",
    headers: ADMIN,
    payload: { title: "Мороженое", priceCoins: 5 },
  });
  const cheapId = (cheap.json() as { data: { id: string } }).data.id;

  const expensive = await app.inject({
    method: "POST",
    url: "/api/guard/shop/items",
    headers: ADMIN,
    payload: { title: "Кино", priceCoins: 100 },
  });
  const expensiveId = (expensive.json() as { data: { id: string } }).data.id;

  const tooMuch = await app.inject({
    method: "POST",
    url: `/api/device/shop/${expensiveId}/buy`,
    headers: DEVICE,
  });
  assert.equal(tooMuch.statusCode, 400);

  const buy = await app.inject({
    method: "POST",
    url: `/api/device/shop/${cheapId}/buy`,
    headers: DEVICE,
  });
  assert.equal(buy.statusCode, 200);
  const purchaseId = (buy.json() as { data: { id: string } }).data.id;

  const duplicate = await app.inject({
    method: "POST",
    url: `/api/device/shop/${cheapId}/buy`,
    headers: DEVICE,
  });
  assert.equal(duplicate.statusCode, 400);

  const approve = await app.inject({
    method: "POST",
    url: `/api/guard/shop/purchases/${purchaseId}/decide`,
    headers: ADMIN,
    payload: { approve: true },
  });
  assert.equal(approve.statusCode, 200);
  const approved = approve.json() as { data: { profile: { coins: number } } };
  assert.equal(approved.data.profile.coins, 5);

  await app.close();
});
