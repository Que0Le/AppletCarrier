# Versioning

How the app version is set, stored, and displayed.

## Two separate version concepts

| Concept | Value source | Where it shows |
|---|---|---|
| **App version** (displayed) | `-PappVersion` Gradle property → `version.properties` | The top bar (`v1.2.3`, or `dev build`) |
| **jpackage `packageVersion`** | Hardcoded literal in `desktopApp/build.gradle.kts` | Installer / native package metadata |

These are intentionally independent. `packageVersion` is currently fixed at `1.0.0`; the
app version is what you bump per release.

## App version — source of truth

The app version comes from the **`appVersion` Gradle property**:

- Local builds default to **`dev`** (shown as "dev build").
- The release workflow derives it from the `release_*` tag (see below) and passes
  `-PappVersion=<version>`.

Set it manually for a local build:

```bash
./gradlew :desktopApp:createDistributable -PappVersion=1.2.3
```

## How it flows through the build

1. **Generated** — the `generateVersionProperties` task in `desktopApp/build.gradle.kts`
   writes `version=<x>` to:
   ```
   desktopApp/build/generated/version/version.properties
   ```
2. **Bundled onto the classpath** — that directory is registered as a resource source, so
   `processResources` copies it into:
   ```
   desktopApp/build/resources/main/version.properties      (classpath: /version.properties)
   ```
3. **Read at runtime** — `AppVersion`
   (`shared/src/jvmMain/kotlin/com/example/applet_carrier/AppVersion.kt`) loads
   `/version.properties` from the classpath once at startup, falling back to `dev` if absent.
4. **Displayed** — threaded `CarrierApplication → ShellRoot → TopBar` and shown next to the
   title.

Both `build/` locations are regenerated on every build and removed by `./gradlew clean` —
nothing is committed to source control.

## Where the version lives in a release

In the distributed app image, `version.properties` is **not a loose file** — it is compiled
into the application jar:

```
AppletCarrier/
├── AppletCarrier.exe
├── runtime/
└── app/
    └── desktopApp-<hash>.jar      ← contains /version.properties (version=<x>)
```

The `<hash>` is jpackage's content hash (e.g. `desktopApp-acef2d3d…​.jar`). To inspect it:

```bash
jar tf AppletCarrier/app/desktopApp-<hash>.jar | grep version.properties
unzip -p AppletCarrier/app/desktopApp-<hash>.jar version.properties
```

You cannot change the released version by editing a text file in the folder — it requires a
rebuild (or repacking the jar).

## Releasing a version

Pushing a tag named **`release_<version>`** triggers `.github/workflows/release.yml`, which:

- extracts `<version>` (everything after `release_`),
- builds with `-PappVersion=<version>` (so the displayed version matches the tag),
- zips the app image and publishes it as a GitHub Release asset `AppletCarrier-<version>.zip`.

```bash
git tag release_1.2.3
git push origin release_1.2.3
```

See the README "Build and distribution" section for the full release flow.

## Changing jpackage's packageVersion (optional)

`packageVersion` in `desktopApp/build.gradle.kts` is fixed at `1.0.0`. To tie it to the tag
too, read the same `appVersion` property there — note jpackage requires a numeric
`MAJOR[.MINOR[.PATCH]]` format on Windows, so non-numeric tags (e.g. `release_beta`) would
fail packaging.
