import { describe, expect, test } from "bun:test";
import {
  allBooks,
  type Book,
  byline,
  chapterProgress,
  continueReading,
  type Library,
  newBooks,
  readingState,
  recents,
  recordPosition,
  syncScanned,
} from "../lib/library-model";

const day = 24 * 60 * 60 * 1000;
const now = 100 * day;

const book = (id: string, change: Partial<Book> = {}): Book => ({
  id,
  title: id,
  authors: ["Herman Melville"],
  series: null,
  seriesIndex: null,
  file: `/books/${id}.epub`,
  chapters: [],
  chapter: 0,
  position: { spine: 0, fraction: 0 },
  progress: 0,
  addedAt: now - 30 * day,
  lastReadAt: null,
  ...change,
});

const chapters = (...titles: string[]) => titles.map((title, index) => ({ title, spine: index }));

const library = (...books: Book[]): Library => ({
  books: Object.fromEntries(books.map((entry) => [entry.id, entry])),
});

describe("home", () => {
  test("continue is the book read last, and recents are the other opened books newest first", () => {
    const books = library(
      book("old", { lastReadAt: 1, progress: 0.2 }),
      book("latest", { lastReadAt: 3, progress: 0.5 }),
      book("middle", { lastReadAt: 2, progress: 1 }),
      book("unopened"),
    );
    expect(continueReading(books)?.id).toBe("latest");
    expect(recents(books).map((entry) => entry.id)).toEqual(["middle", "old"]);
  });

  test("a finished book is not continued but stays in recents", () => {
    const books = library(
      book("done", { lastReadAt: 3, progress: 1 }),
      book("old", { lastReadAt: 1 }),
    );
    expect(continueReading(books)).toBeNull();
    expect(recents(books).map((entry) => entry.id)).toEqual(["done", "old"]);
  });
});

describe("library", () => {
  test("new books are unopened books from the last week, newest first", () => {
    const books = library(
      book("week-old", { addedAt: now - 8 * day }),
      book("yesterday", { addedAt: now - day }),
      book("today", { addedAt: now - 1000 }),
      book("opened", { addedAt: now - 1000, lastReadAt: now }),
    );
    expect(newBooks(books, now).map((entry) => entry.id)).toEqual(["today", "yesterday"]);
  });

  test("books sort by title without a leading article", () => {
    const books = library(
      book("1", { title: "The Dispossessed" }),
      book("2", { title: "Piranesi" }),
      book("3", { title: "A Wizard of Earthsea" }),
      book("4", { title: "Moby-Dick" }),
    );
    expect(allBooks(books).map((entry) => entry.title)).toEqual([
      "The Dispossessed",
      "Moby-Dick",
      "Piranesi",
      "A Wizard of Earthsea",
    ]);
  });
});

describe("labels", () => {
  test("byline joins authors and series", () => {
    expect(
      byline(book("1", { authors: ["Ursula K. Le Guin"], series: "Hainish", seriesIndex: 5 })),
    ).toBe("Ursula K. Le Guin · Hainish 5");
    expect(byline(book("2"))).toBe("Herman Melville");
  });

  test("progress reads as a chapter, a percentage or finished", () => {
    expect(chapterProgress(book("1", { chapters: chapters("a", "b", "c"), chapter: 1 }))).toBe(
      "Chapter 2 of 3",
    );
    expect(chapterProgress(book("2", { progress: 0.649 }))).toBe("64%");
    expect(readingState(book("3", { progress: 1 }))).toBe("Finished");
  });
});

describe("reading", () => {
  test("syncing the books folder adds new books, keeps reading state and drops missing files", () => {
    const before = library(
      book("kept", {
        title: "Old title",
        position: { spine: 4, fraction: 0.5 },
        progress: 0.3,
        lastReadAt: 5,
      }),
      book("deleted"),
    );
    const scanned = [
      { ...book("kept", { title: "New title", chapters: chapters("a", "b", "c", "d") }) },
      { ...book("added") },
    ].map(({ id, title, authors, series, seriesIndex, file, chapters }) => ({
      id,
      title,
      authors,
      series,
      seriesIndex,
      file,
      chapters,
    }));
    const after = syncScanned(before, scanned, now);
    expect(Object.keys(after.books).sort()).toEqual(["added", "kept"]);
    expect(after.books.kept).toMatchObject({
      title: "New title",
      progress: 0.3,
      lastReadAt: 5,
      chapter: 3,
    });
    expect(after.books.added).toMatchObject({ addedAt: now, lastReadAt: null, progress: 0 });
  });

  test("saving a position finds the chapter it is in", () => {
    const contents = [
      { title: "Part One", spine: 2 },
      { title: "Chapter One", spine: 3 },
      { title: "Chapter Two", spine: 6 },
    ];
    const saved = recordPosition(
      library(book("pilgrim", { chapters: contents })),
      "pilgrim",
      { spine: 5, fraction: 0.4 },
      0.1,
      now,
    );
    expect(saved.books.pilgrim).toMatchObject({
      chapter: 1,
      position: { spine: 5, fraction: 0.4 },
      progress: 0.1,
      lastReadAt: now,
    });
  });
});
