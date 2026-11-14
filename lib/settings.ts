import { createStore } from "@ink/store";
import * as v from "valibot";

export const fonts = [
  { value: "literata", label: "Literata" },
  { value: "inter", label: "Inter" },
] as const;
export const textSizes = [
  { value: "small", label: "Small" },
  { value: "medium", label: "Medium" },
  { value: "large", label: "Large" },
] as const;
export const lineSpacings = [
  { value: "tight", label: "Tight" },
  { value: "normal", label: "Normal" },
  { value: "loose", label: "Loose" },
] as const;
export const marginSizes = [
  { value: "narrow", label: "Narrow" },
  { value: "normal", label: "Normal" },
  { value: "wide", label: "Wide" },
] as const;
export const pageTurnModes = [
  { value: "keys-and-taps", label: "Keys and taps" },
  { value: "keys", label: "Keys only" },
] as const;

type Value<T extends readonly { value: string }[]> = T[number]["value"];

export type Settings = {
  font: Value<typeof fonts>;
  textSize: Value<typeof textSizes>;
  lineSpacing: Value<typeof lineSpacings>;
  margins: Value<typeof marginSizes>;
  pageTurns: Value<typeof pageTurnModes>;
  connectAutomatically: boolean;
  sendProgress: boolean;
  shelvesFromTags: boolean;
};

const values = <T extends readonly { value: string }[]>(options: T) =>
  // SAFETY: every options list above is a non-empty const tuple, so its values are a non-empty list of its value literals.
  v.picklist(options.map((option) => option.value) as [Value<T>, ...Value<T>[]]);

export const settings = createStore<Settings>({
  key: "uniread.settings",
  version: 1,
  initial: {
    font: "literata",
    textSize: "medium",
    lineSpacing: "normal",
    margins: "narrow",
    pageTurns: "keys-and-taps",
    connectAutomatically: true,
    sendProgress: true,
    shelvesFromTags: false,
  },
  decode: v.parser(
    v.object({
      font: values(fonts),
      textSize: values(textSizes),
      lineSpacing: values(lineSpacings),
      margins: values(marginSizes),
      pageTurns: values(pageTurnModes),
      connectAutomatically: v.boolean(),
      sendProgress: v.boolean(),
      shelvesFromTags: v.boolean(),
    }),
  ),
});

export const labelOf = (options: readonly { value: string; label: string }[], value: string) =>
  options.find((option) => option.value === value)?.label ?? "";
