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
    secret: "test-secret-value",
    adminToken: "admin-test-token",
    agentToken: "agent-test-token",
    zenmoneyToken: null,
    baseCurrency: "PLN",
    publicUrl: "http://localhost:3001",
  };
}

function auth(token: string): { authorization: string } {
  return { authorization: ["Bearer", token].join(" ") };
}

const ADMIN = auth("admin-test-token");
const AGENT = auth("agent-test-token");

test("Guard: ребёнок, политика, устройство, лимит и запрос времени", async () => {
  const db = openDb(":memory:");
  const app = await buildApp({ config: testConfig(), db });

  const childRes = await app.inject({
    method: "POST",
    url: "/api/guard/children",
    headers: ADMIN,
    payload: { name: "Алиса" },
  });
  assert.equal(childRes.statusCode, 200);
  const child = (childRes.json() as { data: { id: string } }).data;
  assert.ok(child.id);

  const policyRes = await app.inject({
    method: "PATCH",
    url: `/api/guard/children/${child.id}/policy`,
    headers: ADMIN,
    payload: { screenLimitMin: 60, bedtimeStart: "21:30" },
  });
  assert.equal(policyRes.statusCode, 200);
  assert.equal((policyRes.json() as { data: { screen_limit_min: number } }).data.screen_limit_min, 60);

  const deviceRes = await app.inject({
    method: "POST",
    url: `/api/guard/children/${child.id}/devices`,
    headers: ADMIN,
    payload: { name: "Pixel Алисы" },
  });
  assert.equal(deviceRes.statusCode, 200);
  const deviceBody = deviceRes.json() as { data: { device: { id: string }; token: string } };
  const deviceToken = deviceBody.data.token;
  assert.ok(deviceToken.startsWith("dev_"));
  const DEVICE = auth(deviceToken);

  const policyAsDevice = await app.inject({ method: "GET", url: "/api/device/policy", headers: DEVICE });
  assert.equal(policyAsDevice.statusCode, 200);
  const devicePolicy = policyAsDevice.json() as { data: { limitMinutes: number; child: { name: string } } };
  assert.equal(devicePolicy.data.limitMinutes, 60);
  assert.equal(devicePolicy.data.child.name, "Алиса");

  const usage = await app.inject({
    method: "POST",
    url: "/api/device/usage",
    headers: DEVICE,
    payload: { minutes: 45, apps: [{ packageId: "com.youtube", label: "YouTube", minutes: 30 }] },
  });
  assert.equal(usage.statusCode, 200);

  const overview = await app.inject({ method: "GET", url: "/api/guard/overview", headers: ADMIN });
  const overviewBody = overview.json() as {
    data: { children: { usedMinutes: number; remainingMinutes: number; limitMinutes: number }[] };
  };
  assert.equal(overviewBody.data.children[0]?.usedMinutes, 45);
  assert.equal(overviewBody.data.children[0]?.remainingMinutes, 15);

  const request = await app.inject({
    method: "POST",
    url: "/api/device/extension-request",
    headers: DEVICE,
    payload: { minutes: 20, reason: "Досмотреть урок" },
  });
  assert.equal(request.statusCode, 200);
  const requestId = (request.json() as { data: { id: string } }).data.id;

  const decide = await app.inject({
    method: "POST",
    url: `/api/guard/requests/${requestId}/decide`,
    headers: ADMIN,
    payload: { approve: true },
  });
  assert.equal(decide.statusCode, 200);

  const after = await app.inject({ method: "GET", url: "/api/guard/overview", headers: ADMIN });
  const afterBody = after.json() as {
    data: { children: { limitMinutes: number; remainingMinutes: number; pendingRequests: unknown[] }[] };
  };
  assert.equal(afterBody.data.children[0]?.limitMinutes, 80);
  assert.equal(afterBody.data.children[0]?.remainingMinutes, 35);
  assert.equal(afterBody.data.children[0]?.pendingRequests.length, 0);

  const denied = await app.inject({ method: "GET", url: "/api/device/policy", headers: auth("wrong") });
  assert.equal(denied.statusCode, 401);

  await app.close();
});

test("Guard MCP: overview и установка лимита", async () => {
  const db = openDb(":memory:");
  const app = await buildApp({ config: testConfig(), db });

  const created = await app.inject({
    method: "POST",
    url: "/api/guard/children",
    headers: ADMIN,
    payload: { name: "Миша" },
  });
  const childId = (created.json() as { data: { id: string } }).data.id;

  const setLimit = await app.inject({
    method: "POST",
    url: "/mcp",
    headers: AGENT,
    payload: {
      jsonrpc: "2.0",
      id: 1,
      method: "tools/call",
      params: { name: "guard_set_limit", arguments: { childId, screenLimitMin: 90 } },
    },
  });
  assert.equal(setLimit.statusCode, 200);
  const result = setLimit.json() as { result: { structuredContent: { effectiveLimit: number } } };
  assert.equal(result.result.structuredContent.effectiveLimit, 90);

  const overview = await app.inject({
    method: "POST",
    url: "/mcp",
    headers: AGENT,
    payload: { jsonrpc: "2.0", id: 2, method: "tools/call", params: { name: "guard_overview", arguments: {} } },
  });
  const overviewBody = overview.json() as {
    result: { structuredContent: { children: { name: string; limitMinutes: number }[] } };
  };
  assert.equal(overviewBody.result.structuredContent.children[0]?.name, "Миша");
  assert.equal(overviewBody.result.structuredContent.children[0]?.limitMinutes, 90);

  await app.close();
});
