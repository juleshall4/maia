import { Button, Field, Screen, Section, Toggle, useSnapshot } from "ink";
import { about, storageLabel } from "../../lib/about";
import {
  fonts,
  labelOf,
  lineSpacings,
  marginSizes,
  pageTurnsLabel,
  settings,
  textSizes,
} from "../../lib/settings";

export default function Settings() {
  const snapshot = useSnapshot(settings);
  const info = useSnapshot(about());
  if (snapshot.status !== "ready") return <Screen title="Settings" />;
  const current = snapshot.data;
  return (
    <Screen title="Settings">
      <Section title="Calibre">
        <Field label="Wireless" href="/calibre">
          Not connected
        </Field>
      </Section>
      <Section title="Reading">
        <Field label="Font" href="/font">
          {labelOf(fonts, current.font)}
        </Field>
        <Field label="Text size" href="/text-size">
          {labelOf(textSizes, current.textSize)}
        </Field>
        <Field label="Line spacing" href="/line-spacing">
          {labelOf(lineSpacings, current.lineSpacing)}
        </Field>
        <Field label="Margins" href="/margins">
          {labelOf(marginSizes, current.margins)}
        </Field>
        <Field label="Page turns" href="/page-turns">
          {pageTurnsLabel(current.pageTurns)}
        </Field>
      </Section>
      <Section title="Keyboard">
        <Toggle
          label="Backlight when reading"
          value={current.keyboardLight}
          onChange={(keyboardLight) =>
            void settings.update((value) => ({ ...value, keyboardLight }))
          }
        />
      </Section>
      <Section title="About">
        <Field label="Storage">
          {info.status === "ready" ? storageLabel(info.data.books, info.data.bytes) : ""}
        </Field>
        <Field label="Version">{info.status === "ready" ? info.data.version : ""}</Field>
        <Button href="/credits">Credits</Button>
      </Section>
    </Screen>
  );
}
