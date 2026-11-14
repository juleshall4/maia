import { library } from "../../lib/library";
import type { Book } from "../../lib/library-model";

const hour = 60 * 60 * 1000;

function sampleBooks(now: number) {
  const book = (
    id: string,
    title: string,
    authors: string[],
    change: Partial<Book> = {},
  ): Book => ({
    id,
    title,
    authors,
    series: null,
    seriesIndex: null,
    chapters: [],
    chapter: 0,
    progress: 0,
    addedAt: now - 300 * hour,
    lastReadAt: null,
    ...change,
  });

  const books = [
    book("moby-dick", "Moby-Dick", ["Herman Melville"], {
      chapters: [
        "Loomings",
        "The Carpet-Bag",
        "The Spouter-Inn",
        "The Counterpane",
        "Breakfast",
        "The Street",
        "The Chapel",
        "The Pulpit",
        "The Sermon",
        "A Bosom Friend",
      ],
      chapter: 6,
      progress: 0.06,
      lastReadAt: now - hour,
    }),
    book("piranesi", "Piranesi", ["Susanna Clarke"], {
      progress: 0.64,
      lastReadAt: now - 30 * hour,
    }),
    book("earthsea", "A Wizard of Earthsea", ["Ursula K. Le Guin"], {
      series: "Earthsea",
      seriesIndex: 1,
      progress: 1,
      lastReadAt: now - 90 * hour,
    }),
    book("dispossessed", "The Dispossessed", ["Ursula K. Le Guin"], {
      series: "Hainish",
      seriesIndex: 5,
    }),
    book("tomorrow", "Tomorrow, and Tomorrow, and Tomorrow", ["Gabrielle Zevin"], {
      addedAt: now - 60_000,
    }),
  ];
  return books;
}

// Ink exports use an in-memory store; this sample library never touches the installed app.
export default async function setup() {
  const books = sampleBooks(Date.now());
  await library.set({ books: Object.fromEntries(books.map((entry) => [entry.id, entry])) });
}
