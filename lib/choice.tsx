import { Screen, SettingsChoices, useSnapshot } from "ink";
import { type Settings, settings } from "./settings";

type Choice = { [K in keyof Settings]: Settings[K] extends string ? K : never }[keyof Settings];

/** Saves a reading setting as soon as an option is chosen. */
export function ChoicePage<K extends Choice>({
  title,
  setting,
  options,
}: {
  title: string;
  setting: K;
  options: readonly { value: Settings[K]; label: string }[];
}) {
  const snapshot = useSnapshot(settings);
  return (
    <Screen title={title}>
      {snapshot.status === "ready" && (
        <SettingsChoices
          value={snapshot.data[setting]}
          options={options}
          onChange={(value) =>
            void settings.update((current) => ({ ...current, [setting]: value }))
          }
        />
      )}
    </Screen>
  );
}
