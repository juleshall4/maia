import { ChoicePage } from "../../lib/choice";
import { librarySortOptions } from "../../lib/settings";

export default function Sort() {
  return <ChoicePage title="Sort by" setting="librarySort" options={librarySortOptions} />;
}
