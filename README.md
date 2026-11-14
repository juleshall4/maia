<div align="center">

### uniread

**Books from calibre on your Unihertz Titan 2 Elite.**

</div>

uniread is a personal Android e-reader built with [Ink](https://github.com/No-Scrolling/ink). It follows unime's layout: Home, Library and Settings tabs at the bottom.

## Status

This is the app layout: Home, Library, a book's chapters, Settings and the Calibre page. Settings are saved. The calibre wireless connection and the reader are not built yet, so the library stays empty.

## Plan

- **Calibre:** connect with calibre's wireless device connection (Connect/share, then Start wireless device connection). Edit metadata in calibre, then choose Send to device. This needs a small Kotlin layer, since Ink has no raw sockets.
- **Reader:** books open full screen in a native reader. The Titan's keys turn pages, and Back returns to the list.

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
- [Remix Icon](https://github.com/Remix-Design/RemixIcon): tab icons
