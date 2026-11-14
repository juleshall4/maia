import { ChoicePage } from "../../lib/choice";
import { fonts } from "../../lib/settings";

export default function Choice() {
  return <ChoicePage title="Font" setting="font" options={fonts} />;
}
