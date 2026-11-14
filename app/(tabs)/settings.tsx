import { Field, Screen, Section, useSnapshot } from "ink";
import {
  fonts,
  labelOf,
  lineSpacings,
  marginSizes,
  pageTurnModes,
  settings,
  textSizes,
} from "../../lib/settings";

export default function Settings() {
  const snapshot = useSnapshot(settings);
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
          {labelOf(pageTurnModes, current.pageTurns)}
        </Field>
      </Section>
    </Screen>
  );
}
