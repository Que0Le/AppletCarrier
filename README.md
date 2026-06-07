This is a Kotlin Multiplatform project targeting Desktop (JVM).

* [/shared](./shared/src) is for code that will be shared across your Compose Multiplatform applications.
  It contains several subfolders:
    - [commonMain](./shared/src/commonMain/kotlin) is for code that’s common for all targets.
    - Other folders are for Kotlin code that will be compiled for only the platform indicated in the folder name.
      For example, if you want to use Apple’s CoreCrypto for the iOS part of your Kotlin app,
      the [iosMain](./shared/src/iosMain/kotlin) folder would be the right place for such calls.
      Similarly, if you want to edit the Desktop (JVM) specific part, the [jvmMain](./shared/src/jvmMain/kotlin)
      folder is the appropriate location.

### Running the apps

For day-to-day development (not a packaged binary), run via the **`:desktopApp:run` Gradle
task** — it's the app's run configuration and shows up in IntelliJ's run widget and in the
Gradle tool window (**desktopApp → Tasks → application → run**). These work the same on
Windows, macOS, and Linux:

- Desktop app:
    - Hot reload: `./gradlew :desktopApp:hotRun --auto`
    - Standard run: `./gradlew :desktopApp:run`

> **macOS / Linux: `./gradlew` Permission denied?** If you see
> `Cannot run program "./gradlew" … error: 13 (Permission denied)`, the wrapper script is
> missing its executable bit (it was committed on Windows). Fix it once:
> ```bash
> chmod +x gradlew
> ```
> The repo records the bit in git (`100755`), so a fresh `git pull` / checkout already has
> it — this only bites older checkouts. Windows uses `gradlew.bat` and is unaffected.

### Running tests

Use the run button in your IDE's editor gutter, or run tests using Gradle tasks:

- Desktop tests: `./gradlew :shared:jvmTest`

### Build and distribution

The app is packaged with the Compose Desktop plugin (which wraps the JDK's `jpackage`).

> **`JAVA_HOME` (CLI only).** Packaging needs a JDK with `jpackage`. When running from
> IntelliJ the configured Gradle JVM is used automatically. From the command line on this
> machine, set it first:
> ```powershell
> $env:JAVA_HOME = "C:\Users\winde\.jdks\jbrsdk_jcef-21.0.11"
> ```

#### Standalone app image (recommended — no installer, no Java needed)

```
./gradlew :desktopApp:buildAppImage
```

`buildAppImage` is a convenience task (defined in `desktopApp/build.gradle.kts`) that wraps
`createDistributable`. In IntelliJ, run it from the Gradle tool window under
**desktopApp → Tasks → distribution → buildAppImage**.

`jpackage` builds **only for the OS it runs on**, so run this on each platform you want a
binary for. The output is a self-contained bundle with a bundled Java runtime (no JDK/JRE
needed on the target). All of it lands under:

```
desktopApp/build/compose/binaries/main/app/
```

**Where the binary is and how to run it from the file manager:**

| OS | Produced under `…/main/app/` | Run by double-clicking |
|----|------------------------------|------------------------|
| **Windows** | `AppletCarrier/` (folder) | `AppletCarrier/AppletCarrier.exe` in **Explorer** |
| **macOS** | `AppletCarrier.app` (bundle) | `AppletCarrier.app` in **Finder** (or drag to `/Applications`) |
| **Linux** | `AppletCarrier/` (folder) | `AppletCarrier/bin/AppletCarrier` (terminal or file manager) |

- **Windows** — the folder holds `AppletCarrier.exe` + `runtime/` + `app/`; keep them
  together (the exe finds its siblings). Zip the whole folder to share it.
- **macOS** — a single double-clickable `AppletCarrier.app`; the runtime is inside the
  bundle. Zip it with `ditto -c -k --keepParent AppletCarrier.app AppletCarrier.zip` to
  preserve bundle symlinks.
- **Linux** — the folder holds `bin/AppletCarrier` + `lib/`; launch `bin/AppletCarrier`.

Everything is under `build/`, so it's regenerated each build and removed by `./gradlew
clean` — copy the bundle elsewhere to keep or share it. (First-run prompts for *downloaded*
builds: see "Running a downloaded build" below.)

A smaller, ProGuard-minified variant:

```
./gradlew :desktopApp:createReleaseDistributable   # → build/compose/binaries/main-release/app/AppletCarrier/
```

#### Installers (single file, but require WiX)

`jpackage` builds Windows installers via the [WiX Toolset v3](https://wixtoolset.org/),
which must be installed and on `PATH`:

```
./gradlew :desktopApp:packageMsi   # → build/compose/binaries/main/msi/*.msi
./gradlew :desktopApp:packageExe   # → build/compose/binaries/main/exe/*.exe
```

#### Releases via GitHub Actions

Pushing a tag named **`release_<version>`** (e.g. `release_1.2.3`) triggers
`.github/workflows/release.yml`, which runs the tests and builds the app image on **both a
Windows and a macOS runner**, then publishes them as **GitHub Release assets**:

- `AppletCarrier-windows-<version>.zip` — the Windows app-image folder
- `AppletCarrier-macos-<version>.zip` — the macOS `.app` bundle (zipped with `ditto`)
- `AppletCarrier-<version>.dmg` — a macOS installer (best-effort; skipped if the DMG step fails)

The macOS runner is Apple Silicon (arm64). For Intel Mac builds, add a second job on
`macos-13`.

```bash
git tag release_1.2.3
git push origin release_1.2.3
```

Notes:
- Tags point at commits, not branches, so this works from **any branch** and does **not**
  run on ordinary pushes — only on `release_*` tags.
- The `<version>` after `release_` is injected into the build via `-PappVersion`, which the
  `generateVersionProperties` task writes to `version.properties` on the classpath.
  `AppVersion` reads it at startup and the top bar shows it (`dev build` for local builds).
  This is independent of jpackage's `packageVersion`.
- The published `.exe` is **unsigned** (expect a Windows SmartScreen prompt on download);
  code signing would need a certificate stored in repository secrets.

#### Running a downloaded build (unsigned-app prompts)

The builds are **unsigned**, so each OS warns on first launch of a *downloaded* build.
(A locally-*built* bundle usually opens directly — these prompts come from the
"downloaded from the internet" quarantine flag.)

**Windows (SmartScreen):** the downloaded zip is flagged with the "Mark of the Web" and
shows *"Windows protected your PC."* To avoid it:

1. Right-click the downloaded `AppletCarrier-windows-<version>.zip` → **Properties**.
2. Tick **Unblock** (bottom of the dialog) → **OK**.
3. **Then** extract the zip and run `AppletCarrier.exe`.

Unblocking before extracting strips the flag from every extracted file. (If you forget, you
can still click **More info → Run anyway** on the prompt.)

**macOS (Gatekeeper):** a downloaded `.app` shows *"AppletCarrier can't be opened because it
is from an unidentified developer."* Approve it once:

1. **Right-click** `AppletCarrier.app` → **Open** → **Open** in the dialog, **or**
2. strip the quarantine flag: `xattr -dr com.apple.quarantine AppletCarrier.app`

After the first approval it launches on a normal double-click.

Verify downloads with the published SHA-256 checksum if provided.

> Removing these prompts entirely requires code signing (Azure Trusted Signing on Windows,
> an Apple Developer ID + notarization on macOS) — planned for later, not yet configured.

#### Single standalone .exe

There is no `jpackage` option for a single self-contained `.exe` — a JVM app always needs
its runtime. To collapse the app-image folder into one file, run it through a bundler such
as **Enigma Virtual Box** (boxes `AppletCarrier.exe` + `runtime/` + `app/` into one ~120 MB
exe that runs without extracting). GraalVM native-image is not viable here (Compose
relies on Skiko/AWT).

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)…