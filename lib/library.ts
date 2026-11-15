import { createStore } from "@ink/store";
import * as v from "valibot";
import { emptyLibrary, type Library } from "./library-model";

export const chapterSchema = v.object({
  title: v.string(),
  spine: v.number(),
  anchor: v.optional(v.string()),
});

const bookSchema = v.object({
  id: v.string(),
  title: v.string(),
  authors: v.array(v.string()),
  series: v.nullable(v.string()),
  seriesIndex: v.nullable(v.number()),
  file: v.string(),
  chapters: v.array(chapterSchema),
  chapter: v.number(),
  position: v.object({ spine: v.number(), fraction: v.number() }),
  progress: v.number(),
  addedAt: v.number(),
  lastReadAt: v.nullable(v.number()),
});

export const library = createStore<Library>({
  key: "maia.library",
  version: 2,
  initial: emptyLibrary,
  // Version 1 had no files to read; books come back from the books folder on the next scan.
  migrate: () => emptyLibrary,
  decode: v.parser(v.object({ books: v.record(v.string(), bookSchema) })),
});
