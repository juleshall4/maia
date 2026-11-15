import { beforeEach, describe, expect, test } from "bun:test";
import { settings } from "../lib/settings";

declare const __inkReceive: (message: string) => void;

type Entry = { revision: number; version: number; value: string };
let entry: Entry | null = null;
let nextId = 0;

// Exercise the real store and migrations with an in-memory native storage boundary.
Object.assign(globalThis, {
  __inkNextId: () => ++nextId,
  __inkPost: (source: string) => {
    const { id, module, operation, payload } = JSON.parse(source);
    if (module !== "store" || payload.key !== "maia.settings") {
      throw new Error("Unexpected native request");
    }
    let result: Entry | null | { committed: boolean };
    if (operation === "get") {
      result = entry;
    } else if (operation === "write" || operation === "replace") {
      const committed = operation === "replace" || payload.revision === (entry?.revision ?? 0);
      if (committed) {
        entry = {
          revision: (entry?.revision ?? 0) + 1,
          version: payload.version,
          value: payload.value,
        };
      }
      result = { committed };
    } else {
      throw new Error("Unexpected storage operation");
    }
    __inkReceive(JSON.stringify({ type: "result", id, value: JSON.stringify(result) }));
  },
});

beforeEach(() => {
  entry = null;
});

const saved = {
  font: "inter",
  textSize: "large",
  lineSpacing: "loose",
  margins: "wide",
  connectAutomatically: false,
  sendProgress: false,
  shelvesFromTags: true,
};

describe("reader footer settings", () => {
  test("new installations keep time left as the default", async () => {
    expect((await settings.get()).readerFooter).toBe("time-left");
  });

  test.each([
    [1, { ...saved, pageTurns: "keys" }, { keys: true, taps: false, swipes: true }, true],
    [
      2,
      { ...saved, pageTurns: "keys-and-taps", keyboardLight: false },
      { keys: true, taps: true, swipes: true },
      false,
    ],
    [
      3,
      { ...saved, pageTurns: { keys: false, taps: true, swipes: false }, keyboardLight: false },
      { keys: false, taps: true, swipes: false },
      false,
    ],
  ])(
    "version %i gains the default without losing saved preferences",
    async (version, value, pageTurns, keyboardLight) => {
      entry = { revision: 1, version, value: JSON.stringify(value) };

      const current = await settings.get();

      expect(current).toEqual({
        ...saved,
        pageTurns,
        keyboardLight,
        readerFooter: "time-left",
        librarySort: "recently-added",
        hyphenation: true,
      });
      expect(entry.version).toBe(5);
      expect(JSON.parse(entry.value)).toEqual(current);
    },
  );

  test.each([
    "chapter",
    "pages-left",
    "page-count",
    "book-page-count",
    "percentage",
    "none",
  ] as const)("%s persists across a fresh storage read", async (readerFooter) => {
    await settings.update((current) => ({ ...current, readerFooter }));

    expect((await settings.get()).readerFooter).toBe(readerFooter);
  });
});

describe("sorting and hyphenation settings", () => {
  test("upgrading keeps every saved preference and adds the new defaults", async () => {
    const previous = {
      ...saved,
      pageTurns: { keys: false, taps: true, swipes: false },
      keyboardLight: false,
      readerFooter: "book-page-count",
    };
    entry = { revision: 1, version: 4, value: JSON.stringify(previous) };

    expect(await settings.get()).toEqual({
      ...previous,
      librarySort: "recently-added",
      hyphenation: true,
    });
    expect(entry.version).toBe(5);
  });

  test.each(["recently-added", "title", "author"] as const)(
    "%s and disabled hyphenation survive a fresh storage read",
    async (librarySort) => {
      await settings.update((current) => ({ ...current, librarySort, hyphenation: false }));

      const current = await settings.get();
      expect(current.librarySort).toBe(librarySort);
      expect(current.hyphenation).toBe(false);

      await settings.update((current) => ({ ...current, hyphenation: true }));
      expect((await settings.get()).hyphenation).toBe(true);
      expect((await settings.get()).librarySort).toBe(librarySort);
    },
  );
});
