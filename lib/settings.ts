import { createStore } from "@ink/store";
import * as v from "valibot";
import type { LibrarySort } from "./library-model";

export const librarySortOptions = [
  { value: "recently-added", label: "Recently added" },
  { value: "title", label: "Title" },
  { value: "author", label: "Author" },
] as const satisfies readonly { value: LibrarySort; label: string }[];

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
export const readerFooterOptions = [
  { value: "time-left", label: "Time left in chapter" },
  { value: "pages-left", label: "Pages left in chapter" },
  { value: "chapter", label: "Chapter number" },
  { value: "page-count", label: "Chapter page count" },
  { value: "book-page-count", label: "Book page count" },
  { value: "percentage", label: "Book percentage" },
  { value: "none", label: "None" },
] as const;
/** The ways a page can turn; each can be on or off, but not all off. */
export const pageTurnWays = [
  { value: "keys", label: "Keys" },
  { value: "taps", label: "Taps" },
  { value: "swipes", label: "Swipes" },
] as const;

export type PageTurnWay = (typeof pageTurnWays)[number]["value"];
export type PageTurns = Record<PageTurnWay, boolean>;

/** "Keys, taps, swipes" */
export const pageTurnsLabel = (turns: PageTurns) => {
  const words = pageTurnWays.filter((way) => turns[way.value]).map((way) => way.value);
  const text = words.join(", ");
  return text.charAt(0).toUpperCase() + text.slice(1);
};

type Value<T extends readonly { value: string }[]> = T[number]["value"];

export type Settings = {
  librarySort: LibrarySort;
  hyphenation: boolean;
  font: Value<typeof fonts>;
  textSize: Value<typeof textSizes>;
  lineSpacing: Value<typeof lineSpacings>;
  margins: Value<typeof marginSizes>;
  readerFooter: Value<typeof readerFooterOptions>;
  pageTurns: PageTurns;
  connectAutomatically: boolean;
  sendProgress: boolean;
  shelvesFromTags: boolean;
  /** Off keeps the keyboard light off while a book is open. */
  keyboardLight: boolean;
};

const values = <T extends readonly { value: string }[]>(options: T) =>
  // SAFETY: every options list above is a non-empty const tuple, so its values are a non-empty list of its value literals.
  v.picklist(options.map((option) => option.value) as [Value<T>, ...Value<T>[]]);

/** Page turns before swipes had their own setting: keyboard swipes always turned pages, screen swipes went with taps. */
const oldPageTurns = v.picklist(["keys-and-taps", "keys"]);

/** The settings saved before the keyboard light setting. */
const version1 = v.object({
  font: values(fonts),
  textSize: values(textSizes),
  lineSpacing: values(lineSpacings),
  margins: values(marginSizes),
  pageTurns: oldPageTurns,
  connectAutomatically: v.boolean(),
  sendProgress: v.boolean(),
  shelvesFromTags: v.boolean(),
});

const version2 = v.object({ ...version1.entries, keyboardLight: v.boolean() });
const version3 = v.object({
  ...version2.entries,
  pageTurns: v.object({ keys: v.boolean(), taps: v.boolean(), swipes: v.boolean() }),
});

const version4 = v.object({ ...version3.entries, readerFooter: values(readerFooterOptions) });
const newDefaults = { librarySort: "recently-added", hyphenation: true } as const;

export const settings = createStore<Settings>({
  key: "maia.settings",
  version: 5,
  migrate: (old, oldVersion) => {
    if (oldVersion === 4) return { ...v.parse(version4, old), ...newDefaults };
    if (oldVersion === 3) {
      return { ...v.parse(version3, old), readerFooter: "time-left", ...newDefaults };
    }
    const saved =
      oldVersion === 1
        ? { ...v.parse(version1, old), keyboardLight: true }
        : oldVersion === 2
          ? v.parse(version2, old)
          : null;
    if (!saved) throw new Error("Unsupported settings version");
    return {
      ...saved,
      // Keyboard swipes always turned pages before; Keys only turned off taps and screen swipes.
      pageTurns: { keys: true, taps: saved.pageTurns !== "keys", swipes: true },
      readerFooter: "time-left",
      ...newDefaults,
    };
  },
  initial: {
    ...newDefaults,
    font: "literata",
    textSize: "medium",
    lineSpacing: "normal",
    margins: "narrow",
    readerFooter: "time-left",
    pageTurns: { keys: true, taps: true, swipes: true },
    connectAutomatically: true,
    sendProgress: true,
    shelvesFromTags: false,
    keyboardLight: true,
  },
  decode: v.parser(
    v.object({
      ...version4.entries,
      librarySort: values(librarySortOptions),
      hyphenation: v.boolean(),
    }),
  ),
});

export const labelOf = (options: readonly { value: string; label: string }[], value: string) =>
  options.find((option) => option.value === value)?.label ?? "";
