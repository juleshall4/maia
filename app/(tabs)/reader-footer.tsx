import { ChoicePage } from "../../lib/choice";
import { readerFooterOptions } from "../../lib/settings";

export default function Choice() {
  return (
    <ChoicePage title="Bottom-right display" setting="readerFooter" options={readerFooterOptions} />
  );
}
