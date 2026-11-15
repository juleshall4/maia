import { expect, test } from "bun:test";
import { readFileSync } from "node:fs";
import { runInNewContext } from "node:vm";

const source = readFileSync(
  new URL("../android/kotlin/com/vandam/ink/ReaderPage.kt", import.meta.url),
  "utf8",
);
const script = source.split('internal const val READER_SCRIPT = """\n')[1].split('\n"""')[0];

async function openReader({ paginate = false, fraction = 0, end = false } = {}) {
  const positions: number[] = [];
  const layouts: { pages: number; words: number; chapters: { index: number; page: number }[] }[] =
    [];
  const events = new Map<string, () => void>();
  let ready = false;
  let pageCount = 5;
  const window = {
    location: { href: "https://book.maia/chapter.xhtml?g=7" },
    scrollX: 0,
    scrollTo(x: number) {
      this.scrollX = x;
    },
    addEventListener(event: string, callback: () => void) {
      events.set(event, callback);
    },
    MaiaReader: {
      config: () =>
        JSON.stringify({
          generation: 7,
          chapters: [
            { index: 2, anchor: "start" },
            { index: 3, anchor: "next" },
          ],
          paginate,
          fraction,
          end,
        }),
      layout(generation: number, pages: number, words: number, chapters: string) {
        expect(generation).toBe(7);
        layouts.push({ pages, words, chapters: JSON.parse(chapters) });
      },
      position(generation: number, page: number) {
        expect(generation).toBe(7);
        positions.push(page);
      },
      ready: () => {
        ready = true;
      },
    },
  };
  const document = {
    body: {
      get scrollWidth() {
        return pageCount * 100;
      },
      getBoundingClientRect: () => ({ width: 100 }),
      textContent: "word ".repeat(1250),
      style: {},
    },
    documentElement: { style: {}, getAttribute: () => "en" },
    fonts: { ready: Promise.resolve() },
    getElementById(id: string) {
      const page = id === "start" ? 0 : 3;
      return { getBoundingClientRect: () => ({ left: page * 100 - window.scrollX }) };
    },
    addEventListener() {},
  };
  const context = {
    window,
    document,
    URL,
    requestAnimationFrame: (callback: () => void) => callback(),
  };
  runInNewContext(script, context);
  events.get("load")?.();
  await Promise.resolve();
  return {
    layouts,
    positions,
    ready: () => ready,
    next: () => runInNewContext("window.maia.next()", context),
    resize: (pages: number) => {
      pageCount = pages;
      events.get("resize")?.();
    },
  };
}

test("the page reports measured totals and chapter anchors without resetting the count on turns", async () => {
  const reader = await openReader({ fraction: 0.4 });
  expect(reader.ready()).toBe(true);
  expect(reader.layouts).toEqual([
    {
      pages: 5,
      words: 1250,
      chapters: [
        { index: 2, page: 0 },
        { index: 3, page: 3 },
      ],
    },
  ]);
  expect(reader.positions).toEqual([2]);
  reader.next();
  expect(reader.positions).toEqual([2, 3]);
  expect(reader.layouts).toHaveLength(1);
});

test("background pagination reports layout without sending a reading position", async () => {
  const reader = await openReader({ paginate: true });
  expect(reader.ready()).toBe(true);
  expect(reader.layouts[0].pages).toBe(5);
  expect(reader.positions).toEqual([]);
  reader.resize(10);
  expect(reader.layouts.at(-1)?.pages).toBe(10);
  expect(reader.positions).toEqual([]);
});

test("reflow reports the new page count and keeps the relative reading position", async () => {
  const reader = await openReader({ fraction: 0.4 });
  reader.resize(10);
  expect(reader.layouts.at(-1)?.pages).toBe(10);
  expect(reader.positions.at(-1)).toBe(4);
});

test("opening at the end reports the last page", async () => {
  const reader = await openReader({ end: true });
  expect(reader.positions).toEqual([4]);
});
