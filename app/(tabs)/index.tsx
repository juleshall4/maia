import { Row, Screen, Section, Text, useAction, useSnapshot } from "ink";
import { library } from "../../lib/library";
import { chapterProgress, continueReading, readingState, recents } from "../../lib/library-model";
import { read } from "../../lib/reader";
import { openBook } from "../../lib/routes";

export default function Home() {
  const snapshot = useSnapshot(library);
  const resume = useAction(read);
  if (snapshot.status !== "ready") return <Screen title="maia" />;
  const next = continueReading(snapshot.data);
  const others = recents(snapshot.data);
  return (
    <Screen title="maia">
      {next && (
        <Section title="Continue">
          <Row
            title={next.title}
            titleMaxLines={2}
            subtitle={chapterProgress(next)}
            onPress={() => resume.run(next)}
          />
          {resume.status === "error" && <Text>{resume.error.message}</Text>}
        </Section>
      )}
      {others.length > 0 && (
        <Section title="Recents">
          {others.map((book) => (
            <Row
              key={book.id}
              title={book.title}
              titleMaxLines={2}
              subtitle={`${book.authors.join(", ")} · ${readingState(book)}`}
              onPress={() => openBook(book.id)}
            />
          ))}
        </Section>
      )}
    </Screen>
  );
}
