import { ChoicePage } from "../../lib/choice";
import { marginSizes } from "../../lib/settings";

export default function Choice() {
  return <ChoicePage title="Margins" setting="margins" options={marginSizes} />;
}
