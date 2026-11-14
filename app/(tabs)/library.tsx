import { Row, Screen, Section, useSnapshot } from "ink";
import { library } from "../../lib/library";
import { allBooks, byline, newBooks } from "../../lib/library-model";
import { openBook } from "../../lib/routes";

export default function Library() {
  const snapshot = useSnapshot(library);
  if (snapshot.status !== "ready") return <Screen title="Library" />;
  const fresh = newBooks(snapshot.data, Date.now());
  const books = allBooks(snapshot.data);
  return (
    <Screen title="Library">
      {fresh.length > 0 && (
        <Section title="New from calibre">
          {fresh.map((book) => (
            <Row
              key={book.id}
              title={book.title}
              titleMaxLines={2}
              subtitle={byline(book)}
              onPress={() => openBook(book.id)}
            />
          ))}
        </Section>
      )}
      {books.length > 0 && (
        <Section title={`${books.length} ${books.length === 1 ? "book" : "books"}`}>
          {books.map((book) => (
            <Row
              key={book.id}
              title={book.title}
              titleMaxLines={2}
              subtitle={byline(book)}
              onPress={() => openBook(book.id)}
            />
          ))}
        </Section>
      )}
    </Screen>
  );
}
