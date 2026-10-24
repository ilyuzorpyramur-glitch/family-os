import type { FastifyInstance, FastifyRequest } from "fastify";
import { z } from "zod";
import { bearerActor, isAdminCookie } from "../auth.js";
import { findDeviceByToken } from "../repo/guard.js";
import {
  cookRecipe,
  createProduct,
  createRecipe,
  deductProduct,
  deleteProduct,
  findProductByBarcode,
  getProduct,
  listProducts,
  listRecipes,
  lookupBarcode,
  updateProduct,
} from "../repo/pantry.js";
import type { Ctx } from "./api.js";

function deviceFromRequest(request: FastifyRequest, ctx: Ctx) {
  const header = request.headers.authorization ?? "";
  if (!header.startsWith("Bearer ")) return null;
  const key = header.substring(7).trim();
  if (!key) return null;
  return findDeviceByToken(ctx.db, key);
}

export function registerPantryApi(app: FastifyInstance, ctx: Ctx): void {
  const { config, db } = ctx;

  const parentGuard = async (request: FastifyRequest, reply: any) => {
    if (!bearerActor(request, config) && !isAdminCookie(request, config)) {
      return reply.code(401).send({ ok: false, error: { code: "UNAUTHORIZED", message: "Нужен токен родителя" } });
    }
  };

  const deviceGuard = async (request: FastifyRequest, reply: any) => {
    const device = deviceFromRequest(request, ctx);
    if (!device) {
      return reply.code(401).send({ ok: false, error: { code: "UNAUTHORIZED", message: "Неверный токен устройства" } });
    }
    (request as FastifyRequest & { device?: typeof device }).device = device;
  };

  const productBody = z.object({
    title: z.string().min(1).max(120),
    barcode: z.string().max(64).nullable().optional(),
    unit: z.string().max(20).optional(),
    quantity: z.number().min(0).max(100000).optional(),
    minQuantity: z.number().min(0).max(100000).optional(),
    kcal: z.number().min(0).max(10000).nullable().optional(),
    protein: z.number().min(0).max(1000).nullable().optional(),
    fat: z.number().min(0).max(1000).nullable().optional(),
    carbs: z.number().min(0).max(1000).nullable().optional(),
    imageUrl: z.string().max(500).nullable().optional(),
  });

  app.get("/api/products", { preHandler: parentGuard }, async (request) => {
    const q = request.query as { q?: string };
    return { ok: true, data: listProducts(db, q.q) };
  });

  app.post("/api/products", { preHandler: parentGuard }, async (request, reply) => {
    const body = productBody.safeParse(request.body);
    if (!body.success) {
      return reply.code(400).send({ ok: false, error: { code: "VALIDATION", message: body.error.issues[0]?.message } });
    }
    return { ok: true, data: createProduct(db, body.data) };
  });

  app.patch("/api/products/:id", { preHandler: parentGuard }, async (request, reply) => {
    const { id } = request.params as { id: string };
    const body = productBody.partial().safeParse(request.body);
    if (!body.success) {
      return reply.code(400).send({ ok: false, error: { code: "VALIDATION", message: body.error.issues[0]?.message } });
    }
    const product = updateProduct(db, id, body.data);
    if (!product) return reply.code(404).send({ ok: false, error: { code: "NOT_FOUND", message: "Продукт не найден" } });
    return { ok: true, data: product };
  });

  app.post("/api/products/:id/consume", { preHandler: parentGuard }, async (request, reply) => {
    const { id } = request.params as { id: string };
    const body = z.object({ amount: z.number().min(0.01).max(100000) }).safeParse(request.body);
    if (!body.success) {
      return reply.code(400).send({ ok: false, error: { code: "VALIDATION", message: "Нужно количество" } });
    }
    const product = deductProduct(db, id, body.data.amount);
    if (!product) return reply.code(404).send({ ok: false, error: { code: "NOT_FOUND", message: "Продукт не найден" } });
    return { ok: true, data: product };
  });

  app.delete("/api/products/:id", { preHandler: parentGuard }, async (request) => {
    const { id } = request.params as { id: string };
    return { ok: true, data: { deleted: deleteProduct(db, id) } };
  });

  /** Поиск по штрихкоду: сначала свой склад, потом Open Food Facts. */
  app.get("/api/products/barcode/:code", { preHandler: parentGuard }, async (request) => {
    const { code } = request.params as { code: string };
    const local = findProductByBarcode(db, code);
    if (local) return { ok: true, data: { source: "local", product: local } };
    const remote = await lookupBarcode(code);
    return { ok: true, data: { source: remote ? "openfoodfacts" : "none", product: remote, barcode: code } };
  });

  /** Сканирование: создаёт продукт или увеличивает количество. */
  app.post("/api/products/scan", { preHandler: parentGuard }, async (request, reply) => {
    const body = z
      .object({ barcode: z.string().min(4).max(64), quantity: z.number().min(0.01).max(1000).default(1) })
      .safeParse(request.body);
    if (!body.success) {
      return reply.code(400).send({ ok: false, error: { code: "VALIDATION", message: "Нужен штрихкод" } });
    }
    const existing = findProductByBarcode(db, body.data.barcode);
    if (existing) {
      const updated = updateProduct(db, existing.id, { quantity: existing.quantity + body.data.quantity });
      return { ok: true, data: { created: false, product: updated } };
    }
    const remote = await lookupBarcode(body.data.barcode);
    const created = createProduct(db, {
      title: remote?.title ?? `Товар ${body.data.barcode}`,
      barcode: body.data.barcode,
      unit: remote?.unit ?? "шт",
      quantity: body.data.quantity,
      kcal: remote?.kcal ?? null,
      protein: remote?.protein ?? null,
      fat: remote?.fat ?? null,
      carbs: remote?.carbs ?? null,
      imageUrl: remote?.imageUrl ?? null,
    });
    return { ok: true, data: { created: true, product: created, nutrition: Boolean(remote) } };
  });

  app.get("/api/recipes", { preHandler: parentGuard }, async () => ({ ok: true, data: listRecipes(db) }));

  app.post("/api/recipes", { preHandler: parentGuard }, async (request, reply) => {
    const body = z
      .object({
        title: z.string().min(1).max(120),
        description: z.string().max(500).optional(),
        servings: z.number().int().min(1).max(20).optional(),
        minutes: z.number().int().min(1).max(600).optional(),
        steps: z.array(z.string().min(1).max(600)).min(1).max(30),
        items: z
          .array(
            z.object({
              productId: z.string().nullable().optional(),
              title: z.string().min(1).max(120),
              quantity: z.number().min(0.01).max(10000),
              unit: z.string().max(20).default("шт"),
            }),
          )
          .min(1)
          .max(40),
      })
      .safeParse(request.body);
    if (!body.success) {
      return reply.code(400).send({ ok: false, error: { code: "VALIDATION", message: body.error.issues[0]?.message } });
    }
    const recipe = createRecipe(db, { ...body.data, createdBy: "parent" });
    return { ok: true, data: recipe };
  });

  app.post("/api/recipes/:id/cook", { preHandler: parentGuard }, async (request, reply) => {
    const { id } = request.params as { id: string };
    const body = z.object({ servings: z.number().min(0.25).max(10).optional() }).safeParse(request.body ?? {});
    try {
      return { ok: true, data: cookRecipe(db, id, body.success ? body.data.servings ?? 1 : 1) };
    } catch (error) {
      return reply.code(400).send({ ok: false, error: { code: "COOK_FAILED", message: (error as Error).message } });
    }
  });

  // ---- детское приложение ----

  app.get("/api/device/recipes", { preHandler: deviceGuard }, async () => ({ ok: true, data: listRecipes(db) }));

  app.get("/api/device/products", { preHandler: deviceGuard }, async () => ({ ok: true, data: listProducts(db) }));

  app.post("/api/device/recipes/:id/cook", { preHandler: deviceGuard }, async (request, reply) => {
    const { id } = request.params as { id: string };
    const device = (request as FastifyRequest & { device: NonNullable<ReturnType<typeof deviceFromRequest>> }).device;
    const body = z.object({ servings: z.number().min(0.25).max(10).optional() }).safeParse(request.body ?? {});
    try {
      const result = cookRecipe(db, id, body.success ? body.data.servings ?? 1 : 1);
      db.prepare(
        "INSERT INTO guard_events (id, child_id, device_id, type, payload) VALUES (?, ?, ?, ?, ?)",
      ).run(
        `ev_${crypto.randomUUID()}`,
        device.child_id,
        device.id,
        "recipe_cooked",
        JSON.stringify({ recipeId: id }),
      );
      return { ok: true, data: result };
    } catch (error) {
      return reply.code(400).send({ ok: false, error: { code: "COOK_FAILED", message: (error as Error).message } });
    }
  });

  app.post("/api/device/products/scan", { preHandler: deviceGuard }, async (request, reply) => {
    const body = z
      .object({ barcode: z.string().min(4).max(64), quantity: z.number().min(0.01).max(1000).default(1) })
      .safeParse(request.body);
    if (!body.success) {
      return reply.code(400).send({ ok: false, error: { code: "VALIDATION", message: "Нужен штрихкод" } });
    }
    const existing = findProductByBarcode(db, body.data.barcode);
    if (existing) {
      const updated = updateProduct(db, existing.id, { quantity: existing.quantity + body.data.quantity });
      return { ok: true, data: { created: false, product: updated } };
    }
    const remote = await lookupBarcode(body.data.barcode);
    const created = createProduct(db, {
      title: remote?.title ?? `Товар ${body.data.barcode}`,
      barcode: body.data.barcode,
      quantity: body.data.quantity,
      kcal: remote?.kcal ?? null,
      protein: remote?.protein ?? null,
      fat: remote?.fat ?? null,
      carbs: remote?.carbs ?? null,
      imageUrl: remote?.imageUrl ?? null,
    });
    return { ok: true, data: { created: true, product: created, nutrition: Boolean(remote) } };
  });

  app.get("/api/device/products/:id", { preHandler: deviceGuard }, async (request, reply) => {
    const { id } = request.params as { id: string };
    const product = getProduct(db, id);
    if (!product) return reply.code(404).send({ ok: false, error: { code: "NOT_FOUND", message: "Не найдено" } });
    return { ok: true, data: product };
  });
}
