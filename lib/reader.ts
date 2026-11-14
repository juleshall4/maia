import { callNative, onNativeMessage } from "ink/native";
import * as v from "valibot";
import { library } from "./library";
import { type Book, recordPosition } from "./library-model";
import { settings } from "./settings";

const eventSchema = v.object({
  event: v.picklist(["progress", "closed"]),
  spine: v.number(),
  fraction: v.number(),
  progress: v.number(),
});

/** Where to open a book: where it was left, or at a chapter from its contents. */
type From = { kind: "saved" } | { kind: "chapter"; index: number };

let reading: string | null = null;
let saving: Promise<void> = Promise.resolve();

export async function read(book: Book, from: From = { kind: "saved" }) {
  const chapter = from.kind === "chapter" ? book.chapters[from.index] : undefined;
  const position = chapter ? { spine: chapter.spine, fraction: 0 } : book.position;
  reading = book.id;
  await callNative("reader", "open", {
    file: book.file,
    title: book.title,
    spine: position.spine,
    fraction: position.fraction,
    anchor: chapter?.anchor,
    settings: await settings.get(),
  });
}

onNativeMessage("uniread.reader", (message) => {
  const event = v.safeParse(eventSchema, message);
  const id = reading;
  if (!event.success || id === null) return;
  const { spine, fraction, progress } = event.output;
  if (event.output.event === "closed") reading = null;
  saving = saving
    .then(() =>
      library.update((value) =>
        recordPosition(value, id, { spine, fraction }, progress, Date.now()),
      ),
    )
    .catch(() => {});
});
