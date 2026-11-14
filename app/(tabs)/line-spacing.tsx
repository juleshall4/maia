import { ChoicePage } from "../../lib/choice";
import { lineSpacings } from "../../lib/settings";

export default function Choice() {
  return <ChoicePage title="Line spacing" setting="lineSpacing" options={lineSpacings} />;
}
