/**
 * A table of contents entry; `spine` is the file it starts in, in reading order, and `anchor` the
 * element it starts at when several chapters share a file.
 */
export type Chapter = { title: string; spine: number; anchor?: string };

/** Where the reader is: a file in reading order and how far through it, from 0 to 1. */
export type Position = { spine: number; fraction: number };

export type Book = {
  /** calibre's uuid for the book. */
  id: string;
  title: string;
  authors: readonly string[];
  series: string | null;
  seriesIndex: number | null;
  /** The EPUB on the phone. */
  file: string;
  chapters: readonly Chapter[];
  /** Index into `chapters` of the chapter being read. */
  chapter: number;
  position: Position;
  /** How far through the book, from 0 to 1. */
  progress: number;
  addedAt: number;
  lastReadAt: number | null;
};

/** A book found in the books folder. */
export type ScannedBook = Pick<
  Book,
  "id" | "title" | "authors" | "series" | "seriesIndex" | "file" | "chapters"
>;

export type Library = { books: Readonly<Record<string, Book>> };

export type LibrarySort = "recently-added" | "title" | "author";

export const emptyLibrary: Library = { books: {} };

const isFinished = (book: Book) => book.progress >= 1;

const byLastRead = (a: Book, b: Book) => (b.lastReadAt ?? 0) - (a.lastReadAt ?? 0);

/** The book read most recently, unless it is finished. */
export function continueReading(library: Library): Book | null {
  const latest = Object.values(library.books)
    .filter((book) => book.lastReadAt !== null)
    .sort(byLastRead)[0];
  return latest && !isFinished(latest) ? latest : null;
}

/** Other opened books, newest first. */
export function recents(library: Library, limit = 5): Book[] {
  const current = continueReading(library);
  return Object.values(library.books)
    .filter((book) => book.lastReadAt !== null && book.id !== current?.id)
    .sort(byLastRead)
    .slice(0, limit);
}

/** Orders the library without changing the books or their reading state. */
export function allBooks(library: Library, order: LibrarySort = "title"): Book[] {
  const key = (book: Book) => book.title.replace(/^(the|an?)\s+/i, "");
  return Object.values(library.books).sort((a, b) => {
    const primary =
      order === "recently-added"
        ? b.addedAt - a.addedAt
        : order === "author"
          ? a.authors.join(", ").localeCompare(b.authors.join(", "))
          : 0;
    return primary || key(a).localeCompare(key(b)) || a.id.localeCompare(b.id);
  });
}

export function byline(book: Book) {
  const series =
    book.series === null
      ? null
      : book.seriesIndex === null
        ? book.series
        : `${book.series} ${book.seriesIndex}`;
  return [book.authors.join(", "), series].filter(Boolean).join(" · ");
}

export function readingState(book: Book) {
  return isFinished(book) ? "Finished" : `${Math.floor(book.progress * 100)}%`;
}

export function chapterProgress(book: Book) {
  return book.chapters.length > 0
    ? `Chapter ${book.chapter + 1} of ${book.chapters.length}`
    : readingState(book);
}

/** Chapters numbered from 1. */
export const chapterRows = (book: Book) =>
  book.chapters.map((chapter, index) => ({ number: index + 1, index, title: chapter.title }));

/** The last chapter that starts at or before a file in the reading order. */
export function chapterAt(chapters: readonly Chapter[], spine: number) {
  let found = 0;
  chapters.forEach((chapter, index) => {
    if (chapter.spine <= spine) found = index;
  });
  return found;
}

/**
 * Makes the library match the books folder: new files are added, known books get fresh metadata
 * and keep their reading state, and books whose files are gone are removed.
 */
export function syncScanned(
  library: Library,
  scanned: readonly ScannedBook[],
  now: number,
): Library {
  const books: Record<string, Book> = {};
  for (const found of scanned) {
    const known = library.books[found.id];
    const book: Book = known
      ? { ...known, ...found }
      : {
          ...found,
          chapter: 0,
          position: { spine: 0, fraction: 0 },
          progress: 0,
          addedAt: now,
          lastReadAt: null,
        };
    books[found.id] = { ...book, chapter: chapterAt(book.chapters, book.position.spine) };
  }
  return { books };
}

/** Saves where the reader stopped. */
export function recordPosition(
  library: Library,
  id: string,
  position: Position,
  progress: number,
  now: number,
): Library {
  const book = library.books[id];
  if (!book) return library;
  const chapter = chapterAt(book.chapters, position.spine);
  return {
    books: { ...library.books, [id]: { ...book, position, progress, chapter, lastReadAt: now } },
  };
}
