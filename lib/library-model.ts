export type Book = {
  /** calibre's uuid for the book. */
  id: string;
  title: string;
  authors: readonly string[];
  series: string | null;
  seriesIndex: number | null;
  chapters: readonly string[];
  /** Index into `chapters` of the chapter being read. */
  chapter: number;
  /** How far through the book, from 0 to 1. */
  progress: number;
  addedAt: number;
  lastReadAt: number | null;
};

export type Library = { books: Readonly<Record<string, Book>> };

export const emptyLibrary: Library = { books: {} };

/** Books sent from calibre in the last week and not opened yet. */
const newFor = 7 * 24 * 60 * 60 * 1000;

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

export function newBooks(library: Library, now: number): Book[] {
  return Object.values(library.books)
    .filter((book) => book.lastReadAt === null && now - book.addedAt < newFor)
    .sort((a, b) => b.addedAt - a.addedAt);
}

/** Sorts by title, ignoring a leading "The", "A" or "An". */
export function allBooks(library: Library): Book[] {
  const key = (book: Book) => book.title.replace(/^(the|an?)\s+/i, "");
  return Object.values(library.books).sort((a, b) => key(a).localeCompare(key(b)));
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
  book.chapters.map((title, index) => ({ number: index + 1, title }));
