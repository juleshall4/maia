import { createStore } from "@ink/store";
import * as v from "valibot";
import { emptyLibrary, type Library } from "./library-model";

const bookSchema = v.object({
  id: v.string(),
  title: v.string(),
  authors: v.array(v.string()),
  series: v.nullable(v.string()),
  seriesIndex: v.nullable(v.number()),
  chapters: v.array(v.string()),
  chapter: v.number(),
  progress: v.number(),
  addedAt: v.number(),
  lastReadAt: v.nullable(v.number()),
});

export const library = createStore<Library>({
  key: "uniread.library",
  version: 1,
  initial: emptyLibrary,
  decode: v.parser(v.object({ books: v.record(v.string(), bookSchema) })),
});
