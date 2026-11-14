import { Row, Screen, Section, Stack, Text, useAction, useRouteParams, useSnapshot } from "ink";
import { library } from "../../lib/library";
import { chapterRows } from "../../lib/library-model";
import { read } from "../../lib/reader";
import { decodeBookParams } from "../../lib/routes";

export default function Book() {
  const { id } = useRouteParams(decodeBookParams);
  const snapshot = useSnapshot(library);
  const open = useAction(read);
  const book = snapshot.status === "ready" ? snapshot.data.books[id] : undefined;
  if (!book) return <Screen />;
  const count = book.chapters.length;
  return (
    <Screen title={book.title}>
      {open.status === "error" && <Text>{open.error.message}</Text>}
      {/* Books without a table of contents still need a way in. */}
      {count === 0 && <Row title="Read" onPress={() => open.run(book)} />}
      {count > 0 && (
        <Section title={`${count} ${count === 1 ? "chapter" : "chapters"}`}>
          {chapterRows(book).map(({ number, index, title }) => {
            // Read chapters are muted; the current one is all white; the rest have muted numbers.
            // The current chapter carries on where reading stopped; the others start at the top.
            const current = index === book.chapter;
            return (
              <Stack
                key={number}
                axis="horizontal"
                gap={18}
                onPress={() =>
                  open.run(book, current ? { kind: "saved" } : { kind: "chapter", index })
                }
              >
                <Text width={56} tabularNumbers tone={current ? "primary" : "muted"}>
                  {String(number)}
                </Text>
                <Text maxLines={2} tone={index < book.chapter ? "muted" : "primary"}>
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
