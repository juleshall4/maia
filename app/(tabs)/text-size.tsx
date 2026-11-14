import { ChoicePage } from "../../lib/choice";
import { textSizes } from "../../lib/settings";

export default function Choice() {
  return <ChoicePage title="Text size" setting="textSize" options={textSizes} />;
}
