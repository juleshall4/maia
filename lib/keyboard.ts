import { callNative } from "ink/native";

/** Turns the keyboard light on or off for as long as uniread is in front. */
export function setKeyboardLight(on: boolean) {
  return callNative("keyboard", "light", { on });
}
