import { Tabs } from "ink";
import shelfFilled from "../../assets/icons/book-shelf-fill.svg";
import shelf from "../../assets/icons/book-shelf-line.svg";
import homeFilled from "../../assets/icons/home-5-fill.svg";
import home from "../../assets/icons/home-5-line.svg";
import gearFilled from "../../assets/icons/settings-3-fill.svg";
import gear from "../../assets/icons/settings-3-line.svg";

export default function Layout() {
  return (
    <Tabs showOnPages>
      <Tabs.Screen name="index" icon={home} activeIcon={homeFilled} />
      <Tabs.Screen name="library" icon={shelf} activeIcon={shelfFilled} />
      <Tabs.Screen name="settings" icon={gear} activeIcon={gearFilled} />
    </Tabs>
  );
}
