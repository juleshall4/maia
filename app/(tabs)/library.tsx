import { navigate, Row, Screen, Section, useSnapshot } from "ink";
import sort from "../../assets/icons/sort-desc.svg";
import { library } from "../../lib/library";
import { allBooks, byline } from "../../lib/library-model";
import { openBook } from "../../lib/routes";
import { settings } from "../../lib/settings";

export default function Library() {
  const snapshot = useSnapshot(library);
  const preferences = useSnapshot(settings);
  const books =
    snapshot.status === "ready" && preferences.status === "ready"
      ? allBooks(snapshot.data, preferences.data.librarySort)
      : [];
  return (
    <Screen title="Library" rightAction={{ icon: sort, onPress: () => navigate("/sort") }}>
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
