import { ChoicePage } from "../../lib/choice";
import { pageTurnModes } from "../../lib/settings";

export default function Choice() {
  return <ChoicePage title="Page turns" setting="pageTurns" options={pageTurnModes} />;
}
