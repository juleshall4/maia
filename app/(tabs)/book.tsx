import { Screen, Section, Stack, Text, useRouteParams, useSnapshot } from "ink";
import { library } from "../../lib/library";
import { chapterRows, readingState } from "../../lib/library-model";
import { decodeBookParams } from "../../lib/routes";

export default function Book() {
  const { id } = useRouteParams(decodeBookParams);
  const snapshot = useSnapshot(library);
  const book = snapshot.status === "ready" ? snapshot.data.books[id] : undefined;
  if (!book) return <Screen />;
  const count = book.chapters.length;
  return (
    <Screen title={book.title} subtitle={`${book.authors.join(", ")} · ${readingState(book)}`}>
      {count > 0 && (
        <Section title={`${count} ${count === 1 ? "chapter" : "chapters"}`}>
          {chapterRows(book).map(({ number, title }) => {
            // Read chapters are muted; the current one is all white; the rest have muted numbers.
            const current = number === book.chapter + 1;
            return (
              <Stack key={number} axis="horizontal" gap={18}>
                <Text width={56} tabularNumbers tone={current ? "primary" : "muted"}>
                  {String(number)}
                </Text>
                <Text maxLines={2} tone={number <= book.chapter ? "muted" : "primary"}>
                  {title}
                </Text>
              </Stack>
            );
          })}
        </Section>
      )}
    </Screen>
  );
}
