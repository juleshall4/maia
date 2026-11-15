import { Field, Screen, Section } from "ink";

export default function Credits() {
  return (
    <Screen title="Credits">
      <Section title="Made with">
        <Field label="Ink">App framework</Field>
        <Field label="Literata">Reading type</Field>
        <Field label="Inter">Interface type</Field>
        <Field label="Remix Icon">Tab icons</Field>
      </Section>
    </Screen>
  );
}
