import type { DB } from "../db.js";
import { randomUUID } from "node:crypto";

export type MealRow = {
  id: string;
  child_id: string;
  date: string;
  title: string;
  kcal: number;
  grams: number | null;
  source: string;
  created_at: string;
};

export function localDate(date = new Date()): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

export function addMeal(
  db: DB,
  input: { childId: string; title: string; kcal: number; grams?: number | null; date?: string; source?: string },
): MealRow {
  const id = `meal_${randomUUID()}`;
  db.prepare(
    `INSERT INTO meals (id, child_id, date, title, kcal, grams, source) VALUES (?, ?, ?, ?, ?, ?, ?)`,
  ).run(
    id,
    input.childId,
    input.date ?? localDate(),
    input.title,
    Math.max(0, Math.round(input.kcal)),
    input.grams ?? null,
    input.source ?? "manual",
  );
  return db.prepare("SELECT * FROM meals WHERE id = ?").get(id) as MealRow;
}

export function deleteMeal(db: DB, id: string): boolean {
  return db.prepare("DELETE FROM meals WHERE id = ?").run(id).changes > 0;
}

export function listMeals(db: DB, childId: string, date?: string): MealRow[] {
  return db
    .prepare("SELECT * FROM meals WHERE child_id = ? AND date = ? ORDER BY created_at DESC")
    .all(childId, date ?? localDate()) as MealRow[];
}

export function nutritionDay(db: DB, childId: string, date?: string, target = 2000) {
  const day = date ?? localDate();
  const meals = listMeals(db, childId, day);
  const consumed = meals.reduce((sum, meal) => sum + meal.kcal, 0);
  const week = db
    .prepare(
      `SELECT date, SUM(kcal) AS kcal FROM meals WHERE child_id = ? AND date >= date(?, '-6 days') AND date <= ?
       GROUP BY date ORDER BY date`,
    )
    .all(childId, day, day) as { date: string; kcal: number }[];

  return {
    date: day,
    target,
    consumed: Math.round(consumed),
    left: Math.max(0, Math.round(target - consumed)),
    percent: target > 0 ? Math.min(100, Math.round((consumed / target) * 100)) : 0,
    meals,
    week,
  };
}
