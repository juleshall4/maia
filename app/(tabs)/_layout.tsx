import { Tabs } from "ink";
import bookFilled from "../../assets/icons/book-2-fill.svg";
import book from "../../assets/icons/book-2-line.svg";
import homeFilled from "../../assets/icons/home-5-fill.svg";
import home from "../../assets/icons/home-5-line.svg";
import gearFilled from "../../assets/icons/settings-3-fill.svg";
import gear from "../../assets/icons/settings-3-line.svg";

export default function Layout() {
  return (
    <Tabs showOnPages>
      <Tabs.Screen name="index" icon={home} activeIcon={homeFilled} />
      <Tabs.Screen name="library" icon={book} activeIcon={bookFilled} />
      <Tabs.Screen name="settings" icon={gear} activeIcon={gearFilled} />
    </Tabs>
  );
}
