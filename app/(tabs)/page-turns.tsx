import { Button, Screen, Stack, useSnapshot } from "ink";
import { type PageTurnWay, pageTurnWays, settings } from "../../lib/settings";

/** Turns one way of turning pages on or off; the last one left on stays on. */
const toggle = (way: PageTurnWay) =>
  void settings.update((current) => {
    const pageTurns = { ...current.pageTurns, [way]: !current.pageTurns[way] };
    return Object.values(pageTurns).some(Boolean) ? { ...current, pageTurns } : current;
  });

export default function PageTurns() {
  const snapshot = useSnapshot(settings);
  return (
    <Screen title="Page turns">
      {snapshot.status === "ready" && (
        <Stack gap={47}>
          {pageTurnWays.map((way) => (
            <Button
              key={way.value}
              selected={snapshot.data.pageTurns[way.value]}
              onPress={() => toggle(way.value)}
            >
              {way.label}
            </Button>
          ))}
        </Stack>
      )}
    </Screen>
  );
}
