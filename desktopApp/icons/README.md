# App icons

Drop the generated icon files here. The build wires them in automatically (each is optional
and guarded by `exists()`, so a missing file never breaks the build — that platform just
falls back to the default icon).

Start from one **square master PNG, 1024×1024, transparent background**, then export the
per-platform formats below.

## Files to provide

| File | Platform | Format | Resolution(s) | Used for |
|------|----------|--------|---------------|----------|
| `desktopApp/icons/icon.ico` | Windows | ICO (multi-size) | 16, 32, 48, 256 px inside one .ico | `.exe` + MSI/EXE installer |
| `desktopApp/icons/icon.icns` | macOS | ICNS | 16…1024 px incl. @2x (from 1024 master) | `.app` + DMG |
| `desktopApp/icons/icon.png` | Linux | PNG | 512×512 (256 ok) | `.deb` / `.rpm` + desktop entry |
| `desktopApp/src/main/resources/icon.png` | all | PNG | 256×256 or 512×512 | running window (taskbar / title bar) |

The two PNGs (`icons/icon.png` and `resources/icon.png`) can be the **same image** — just
copy it to both locations. They serve different purposes (Linux packaging vs. the live
window icon), and only `resources/icon.png` is read at runtime.

## Generating the formats from the 1024 master

- **Windows `.ico`** (ImageMagick):
  ```
  magick icon-1024.png -define icon:auto-resize=256,48,32,16 icon.ico
  ```
- **macOS `.icns`** (on a Mac): build an `icon.iconset` folder with the standard sizes
  (`icon_16x16.png`, `icon_16x16@2x.png`, … `icon_512x512@2x.png`) then:
  ```
  iconutil -c icns icon.iconset -o icon.icns
  ```
- **Linux / window PNG**: just downscale the master to 512×512 (and/or 256×256).

## Design notes

- Keep it a centered square glyph with transparent padding; avoid text (illegible at 16 px).
- Test legibility at 16×16 and 32×32 — that's the size most users actually see.
