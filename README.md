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

Use the run configurations provided by the run widget in your IDE's toolbar. You can also use these commands and
options:

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

Output:

```
desktopApp/build/compose/binaries/main/app/AppletCarrier/
├── AppletCarrier.exe   ← double-click to start
├── runtime/            ← bundled Java runtime (no JDK/JRE required on the target)
└── app/                ← compiled code + libraries
```

The folder is fully self-contained and portable — copy or **zip the whole `AppletCarrier`
folder** to share it. The three items must stay together (the `.exe` finds its sibling
`runtime/` and `app/`). Note: it lives under `build/`, so it is regenerated on each run and
removed by `./gradlew clean`.

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

#### Running a downloaded build (Windows SmartScreen)

The released `.exe` is **unsigned**, so Windows tags the downloaded zip with the "Mark of
the Web" and SmartScreen shows a *"Windows protected your PC"* prompt on first run. The
free way to avoid it:

1. Right-click the downloaded `AppletCarrier-<version>.zip` → **Properties**.
2. Tick **Unblock** (bottom of the dialog) → **OK**.
3. **Then** extract the zip and run `AppletCarrier.exe`.

Unblocking before extracting strips the Mark of the Web from every extracted file, so no
SmartScreen prompt appears. (If you forget, you can still click **More info → Run anyway**
on the prompt.) Verify the download with the published SHA-256 checksum if provided.

> Removing the prompt entirely requires code signing (e.g. Azure Trusted Signing) — planned
> for later, not yet configured.

#### Single standalone .exe

There is no `jpackage` option for a single self-contained `.exe` — a JVM app always needs
its runtime. To collapse the app-image folder into one file, run it through a bundler such
as **Enigma Virtual Box** (boxes `AppletCarrier.exe` + `runtime/` + `app/` into one ~120 MB
exe that runs without extracting). GraalVM native-image is not viable here (Compose
relies on Skiko/AWT).

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)…