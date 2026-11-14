import { navigate } from "ink";
import * as v from "valibot";

export const decodeBookParams = v.parser(v.object({ id: v.string() }));

export function openBook(id: string) {
  navigate({ path: "/book", params: { id } });
}
