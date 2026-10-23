import type { DB } from "../db.js";

export type ProductRow = {
  id: string;
  title: string;
  barcode: string | null;
  unit: string;
  quantity: number;
  min_quantity: number;
  kcal: number | null;
  protein: number | null;
  fat: number | null;
  carbs: number | null;
  image_url: string | null;
  created_at: string;
};

export type ProductInput = {
  title: string;
  barcode?: string | null;
  unit?: string;
  quantity?: number;
  minQuantity?: number;
  kcal?: number | null;
  protein?: number | null;
  fat?: number | null;
  carbs?: number | null;
  imageUrl?: string | null;
};

export type RecipeRow = {
  id: string;
  title: string;
  description: string | null;
  servings: number;
  minutes: number;
  steps: string;
  created_by: string;
  created_at: string;
};

export type RecipeItemRow = {
  id: string;
  recipe_id: string;
  product_id: string | null;
  title: string;
  quantity: number;
  unit: string;
};

export type RecipeItemView = RecipeItemRow & {
  available: number;
  enough: boolean;
};

export type RecipeView = RecipeRow & {
  stepsList: string[];
  items: RecipeItemView[];
  canCook: boolean;
  missingCount: number;
};

export function listProducts(db: DB, query?: string): ProductRow[] {
  if (query?.trim()) {
    return db
      .prepare("SELECT * FROM products WHERE title LIKE ? ORDER BY title COLLATE NOCASE LIMIT 200")
      .all(`%${query.trim()}%`) as ProductRow[];
  }
  return db.prepare("SELECT * FROM products ORDER BY title COLLATE NOCASE LIMIT 500").all() as ProductRow[];
}

export function getProduct(db: DB, id: string): ProductRow | undefined {
  return db.prepare("SELECT * FROM products WHERE id = ?").get(id) as ProductRow | undefined;
}

export function findProductByBarcode(db: DB, barcode: string): ProductRow | undefined {
  return db.prepare("SELECT * FROM products WHERE barcode = ?").get(barcode) as ProductRow | undefined;
}

export function createProduct(db: DB, input: ProductInput): ProductRow {
  const id = `prod_${crypto.randomUUID()}`;
  db.prepare(
    `INSERT INTO products (id, title, barcode, unit, quantity, min_quantity, kcal, protein, fat, carbs, image_url)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
  ).run(
    id,
    input.title.trim(),
    input.barcode ?? null,
    input.unit ?? "шт",
    input.quantity ?? 0,
    input.minQuantity ?? 0,
    input.kcal ?? null,
    input.protein ?? null,
    input.fat ?? null,
    input.carbs ?? null,
    input.imageUrl ?? null,
  );
  return getProduct(db, id)!;
}

export function updateProduct(
  db: DB,
  id: string,
  patch: Partial<ProductInput> & { quantity?: number },
): ProductRow | undefined {
  const product = getProduct(db, id);
  if (!product) return undefined;
  db.prepare(
    `UPDATE products SET title = ?, unit = ?, quantity = ?, min_quantity = ?, kcal = ?, protein = ?, fat = ?, carbs = ?,
       barcode = ?, image_url = ? WHERE id = ?`,
  ).run(
    patch.title ?? product.title,
    patch.unit ?? product.unit,
    patch.quantity ?? product.quantity,
    patch.minQuantity ?? product.min_quantity,
    patch.kcal === undefined ? product.kcal : patch.kcal,
    patch.protein === undefined ? product.protein : patch.protein,
    patch.fat === undefined ? product.fat : patch.fat,
    patch.carbs === undefined ? product.carbs : patch.carbs,
    patch.barcode === undefined ? product.barcode : patch.barcode,
    patch.imageUrl === undefined ? product.image_url : patch.imageUrl,
    id,
  );
  return getProduct(db, id);
}

export function deleteProduct(db: DB, id: string): boolean {
  const result = db.prepare("DELETE FROM products WHERE id = ?").run(id);
  return result.changes > 0;
}

/** Поиск продукта в Open Food Facts по штрихкоду. */
export async function lookupBarcode(barcode: string): Promise<ProductInput | null> {
  const url = `https://world.openfoodfacts.org/api/v2/product/${encodeURIComponent(barcode)}.json?fields=product_name,product_name_ru,brands,nutriments,image_small_url,quantity`;
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 8000);
  try {
    const response = await fetch(url, {
      signal: controller.signal,
      headers: { "User-Agent": "FamilyOS/0.1 (self-hosted family app)" },
    });
    if (!response.ok) return null;
    const data = (await response.json()) as {
      status?: number;
      product?: {
        product_name?: string;
        product_name_ru?: string;
        brands?: string;
        quantity?: string;
        image_small_url?: string;
        nutriments?: Record<string, number>;
      };
    };
    if (!data.product) return null;
    const name = data.product.product_name_ru || data.product.product_name;
    if (!name) return null;
    const nutriments = data.product.nutriments ?? {};
    return {
      title: [data.product.brands, name].filter(Boolean).join(" · ").slice(0, 120),
      barcode,
      unit: "шт",
      quantity: 1,
      kcal: nutriments["energy-kcal_100g"] ?? null,
      protein: nutriments.proteins_100g ?? null,
      fat: nutriments.fat_100g ?? null,
      carbs: nutriments.carbohydrates_100g ?? null,
      imageUrl: data.product.image_small_url ?? null,
    };
  } catch {
    return null;
  } finally {
    clearTimeout(timer);
  }
}

export function listRecipes(db: DB): RecipeView[] {
  const recipes = db.prepare("SELECT * FROM recipes ORDER BY title COLLATE NOCASE").all() as RecipeRow[];
  const items = db.prepare("SELECT * FROM recipe_items").all() as RecipeItemRow[];
  const products = new Map(listProducts(db).map((p) => [p.id, p]));

  return recipes.map((recipe) => {
    const own = items.filter((item) => item.recipe_id === recipe.id);
    const views: RecipeItemView[] = own.map((item) => {
      const product = item.product_id ? products.get(item.product_id) : undefined;
      const available = product?.quantity ?? 0;
      return { ...item, available, enough: available >= item.quantity };
    });
    return {
      ...recipe,
      stepsList: parseSteps(recipe.steps),
      items: views,
      canCook: views.every((item) => item.enough),
      missingCount: views.filter((item) => !item.enough).length,
    };
  });
}

export function getRecipe(db: DB, id: string): RecipeView | undefined {
  return listRecipes(db).find((recipe) => recipe.id === id);
}

export function parseSteps(raw: string): string[] {
  try {
    const parsed = JSON.parse(raw);
    if (Array.isArray(parsed)) return parsed.map((step) => String(step));
  } catch {
    // допускаем хранение шагов простым текстом
  }
  return raw
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean);
}

export function createRecipe(
  db: DB,
  input: {
    title: string;
    description?: string | null;
    servings?: number;
    minutes?: number;
    steps: string[];
    items: { productId?: string | null; title: string; quantity: number; unit: string }[];
    createdBy?: string;
  },
): RecipeRow {
  const id = `rec_${crypto.randomUUID()}`;
  const create = db.transaction(() => {
    db.prepare(
      `INSERT INTO recipes (id, title, description, servings, minutes, steps, created_by)
       VALUES (?, ?, ?, ?, ?, ?, ?)`,
    ).run(
      id,
      input.title.trim(),
      input.description ?? null,
      Math.max(1, Math.round(input.servings ?? 2)),
      Math.max(1, Math.round(input.minutes ?? 30)),
      JSON.stringify(input.steps.map((step) => step.trim()).filter(Boolean)),
      input.createdBy ?? "parent",
    );
    for (const item of input.items) {
      db.prepare(
        `INSERT INTO recipe_items (id, recipe_id, product_id, title, quantity, unit) VALUES (?, ?, ?, ?, ?, ?)`,
      ).run(
        `ri_${crypto.randomUUID()}`,
        id,
        item.productId ?? null,
        item.title.trim(),
        Math.max(0.01, item.quantity),
        item.unit || "шт",
      );
    }
  });
  create();
  return db.prepare("SELECT * FROM recipes WHERE id = ?").get(id) as RecipeRow;
}

/** Готовка: списывает продукты со склада, чего не хватает — сообщает. */
export function cookRecipe(
  db: DB,
  id: string,
  servingsFactor = 1,
): { cooked: boolean; used: { title: string; quantity: number; unit: string }[]; missing: string[] } {
  const recipe = getRecipe(db, id);
  if (!recipe) throw new Error("Рецепт не найден");

  const used: { title: string; quantity: number; unit: string }[] = [];
  const missing: string[] = [];

  const run = db.transaction(() => {
    for (const item of recipe.items) {
      const need = item.quantity * servingsFactor;
      if (!item.product_id) {
        missing.push(item.title);
        continue;
      }
      const product = getProduct(db, item.product_id);
      if (!product || product.quantity < need) {
        missing.push(`${item.title}: нужно ${round(need)} ${item.unit}, есть ${round(product?.quantity ?? 0)}`);
        continue;
      }
      db.prepare("UPDATE products SET quantity = quantity - ? WHERE id = ?").run(need, product.id);
      used.push({ title: product.title, quantity: round(need), unit: item.unit });
    }
  });
  run();

  return { cooked: missing.length === 0, used, missing };
}

export function deductProduct(db: DB, id: string, amount: number): ProductRow | undefined {
  const product = getProduct(db, id);
  if (!product) return undefined;
  db.prepare("UPDATE products SET quantity = MAX(0, quantity - ?) WHERE id = ?").run(amount, id);
  return getProduct(db, id);
}

function round(value: number): number {
  return Math.round(value * 100) / 100;
}
