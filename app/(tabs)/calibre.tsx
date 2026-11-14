import { Field, Screen, Section, Toggle, useSnapshot } from "ink";
import { type Settings, settings } from "../../lib/settings";

type Switch = "connectAutomatically" | "sendProgress" | "shelvesFromTags";

const set = (key: Switch) => (value: boolean) =>
  void settings.update((current): Settings => ({ ...current, [key]: value }));

export default function Calibre() {
  const snapshot = useSnapshot(settings);
  return (
    <Screen
      title="Calibre"
      subtitle="In calibre, choose Connect/share, then Start wireless device connection."
    >
      <Section title="Connection">
        <Field label="Status">Not connected</Field>
      </Section>
      {snapshot.status === "ready" && (
        <Section title="Options">
          <Toggle
            label="Connect automatically"
            value={snapshot.data.connectAutomatically}
            onChange={set("connectAutomatically")}
          />
          <Toggle
            label="Send reading progress"
            value={snapshot.data.sendProgress}
            onChange={set("sendProgress")}
          />
          <Toggle
            label="Shelves from tags"
            value={snapshot.data.shelvesFromTags}
            onChange={set("shelvesFromTags")}
          />
        </Section>
      )}
    </Screen>
  );
}
