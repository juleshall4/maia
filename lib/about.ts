import { resource } from "ink";
import { callNative } from "ink/native";
import * as v from "valibot";

const aboutSchema = v.object({ version: v.string(), books: v.number(), bytes: v.number() });

/** The app's version and what the books folder holds. */
export const about = resource({
  key: () => ["about"],
  load: async () => v.parse(aboutSchema, JSON.parse(await callNative("app", "about", {}))),
});

/** "38 books · 214 MB" */
export function storageLabel(books: number, bytes: number) {
  const megabytes = bytes / 1_000_000;
  const size = megabytes < 10 ? megabytes.toFixed(1) : Math.round(megabytes).toString();
  return `${books} ${books === 1 ? "book" : "books"} · ${size} MB`;
}
