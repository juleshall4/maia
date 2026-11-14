import { callNative } from "ink/native";
import * as v from "valibot";
import { chapterSchema, library } from "./library";
import { syncScanned } from "./library-model";

const scannedSchema = v.array(
  v.object({
    id: v.string(),
    title: v.string(),
    authors: v.array(v.string()),
    series: v.nullable(v.string()),
    seriesIndex: v.nullable(v.number()),
    file: v.string(),
    chapters: v.array(chapterSchema),
  }),
);

/** Reads the books folder on the phone and brings the library up to date with it. */
export async function syncBooks() {
  const scanned = v.parse(scannedSchema, JSON.parse(await callNative("books", "scan", {})));
  await library.update((value) => syncScanned(value, scanned, Date.now()));
}
