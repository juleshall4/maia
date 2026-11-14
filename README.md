<div align="center">

### uniread

**Books from calibre on your Unihertz Titan 2 Elite.**

</div>

uniread is a personal Android e-reader built with [Ink](https://github.com/No-Scrolling/ink). It follows unime's layout: Home, Library and Settings tabs at the bottom.

## Status

Books open in the reader: Literata pages laid out to the reading settings, with the book title and time left in the chapter at the bottom. Volume keys, arrows, Space and Shift+Space turn pages, as do sideways swipes on the keyboard. Taps on the left third (back) and the rest of the page (forward), and sideways swipes on the screen, turn pages too unless Page turns is set to Keys only. Back closes the book and saves the place. Chapters on a book's page jump straight to that chapter.

Turning off **Keyboard light** in Settings keeps the keyboard light off while uniread is open. The Titan then keeps it off in other apps too, until the screen next turns on.

The calibre wireless connection is not built yet. Until it is, copy EPUBs into the app's books folder over USB. Open the app once first so it creates the folder itself; a folder made by adb is not readable by the app.

```sh
adb push book.epub /sdcard/Android/data/dev.uniread/files/books/
```

The library matches the folder each time the app starts.

## Plan

- **Calibre:** connect with calibre's wireless device connection (Connect/share, then Start wireless device connection). Edit metadata in calibre, then choose Send to device. This needs a small Kotlin layer, since Ink has no raw sockets.

Designs: Paper file [Titan e-reader](https://app.paper.design/file/01M7DXY8CCYZYSH7FP63Z2QJQ3).

## Development

Built against the local Ink checkout at `~/Developer/ink` (`feat/themes` branch).

```sh
bun install
ink check
bun test
ink export / --size 1080x1200 --cutout 505,20,70,80 --setup design/screenshots/setup.ts
```

## Credits

- [Ink](https://github.com/No-Scrolling/ink): the app framework (MIT)
- [Inter](https://github.com/rsms/inter): typeface (SIL Open Font Licence)
- [Literata](https://github.com/googlefonts/literata): reading typeface (SIL Open Font Licence)
- [Remix Icon](https://github.com/Remix-Design/RemixIcon): tab icons
