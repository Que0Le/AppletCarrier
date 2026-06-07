# applet_carrier — Agent Build Instructions

Instructions for AI coding agents working on this project. Follow these exactly.
They encode decisions already made with the project owner; do not re-litigate them
without being asked.

---

## 1. What this project is

`applet_carrier` is an **applet host shell** — a single Compose Multiplatform desktop
app that acts as a reusable carrier for small, self-contained utility programs
("applets"). The shell owns all common boilerplate so each applet only implements
its own logic and UI.

The shell provides:
- **Lifecycle**: startup, shutdown, per-applet state persistence, config load/save.
- **Settings**: a shared preferences window, with per-applet settings pages hanging off it.
- **Input utilities**: common dialogs (confirm, text input, …). These are examples, not an exhaustive set.

It targets **boring/utility apps** — no full-screen, no high frame rate, no
sophisticated graphics. The whole point is to avoid rebuilding the same
boilerplate for every small tool. Think "an app with an applet ecosystem."

**Stage constraint:** No true plugin system. Applets are **normal compiled-in
Kotlin classes** registered in the main program. Keep the design simple but
**extensible** — future work will add runtime plugin loading, per-applet
start/stop buttons, and notifications/badges.

---

## 2. Tech stack (confirmed, from the generated project — do not change)

- **Compose Multiplatform** `1.11.1`
- **Kotlin** `2.4.0`
- **Material3** `1.11.0-alpha07`
- **Coroutines** `1.11.0`
- **Gradle** `9.1.0` (wrapper), Kotlin DSL, version catalog at `gradle/libs.versions.toml`
- Modules:
  - `:shared` — `commonMain` + `jvmMain`, holds **all** UI and logic. Package `com.example.applet_carrier`.
  - `:desktopApp` — JVM entry point only. Main class `com.example.applet_carrier.MainKt` (`desktopApp/src/main/kotlin/.../main.kt`).
- **Target platforms: Windows, macOS, Linux** (JVM desktop). Build/run via the `desktopApp`
  module; `compose.desktop.currentOs` resolves the right native libs per OS. Platform
  applets that shell out branch on `Os` (e.g. Find-process: `netstat` on Windows, `lsof`/
  `ps` on Unix). Packaging (jpackage) only produces output for the OS it runs on.

Put new shared code under
`shared/src/commonMain/kotlin/com/example/applet_carrier/`. The platform is JVM
desktop, so it is acceptable to use JVM APIs (e.g. `java.io.File`, `java.awt`)
from `jvmMain` where needed; keep pure UI/logic in `commonMain`.

When adding dependencies, add them to `gradle/libs.versions.toml` and reference
via `libs.*` — match the existing catalog style. Do not hardcode versions in
`build.gradle.kts`.

---

## 3. Application UI layout

The main window looks like a **modern code editor / JetBrains IDE**:

```
┌─────────────────────────────────────────────────────────┐
│  Top bar:  [ app title ............... ] [ ⚙ Prefs ]    │  ← global toolbar
├──────────┬──────────────────────────────────────────────┤
│ Sidebar  │                                              │
│ (left)   │            Applet viewport (right)           │
│          │                                              │
│ • Hello  │        The active applet's UI fills          │
│ ▸ List   │        this entire area.                     │
│   …      │                                              │
│ scroll   │                                              │
└──────────┴──────────────────────────────────────────────┘
```

- **Top bar** spans the full width above both panels. For now it holds the app
  title (left) and a **Preferences button** (right). Nothing else yet.
- **Left sidebar**: a scrollable, vertically-stacked list of selectable applets.
  Behaves like a **vertical tab bar**. Each row shows **name + icon** only.
  Selecting a row makes that applet active.
- **Right viewport**: shows the **currently selected applet's UI, and only that**.
  An applet has full authority over this area — and **only** this area. It never
  touches the top bar, sidebar, or preferences chrome.

### Preferences = a separate **window** (not a modal dialog)

Opened by the top-bar button. Same two-panel pattern as the main window:

- **Left**: a **tree**. Two fixed top-level nodes:
  - `General` — built-in host settings.
  - `Applet Settings` — each applet that provides a prefs page appears as a **child** node.
- **Right**: the settings form for the selected tree node.

An applet with no prefs page does not appear under `Applet Settings`.

---

## 4. Applet model & "browser-tab" behavior

This is the core mechanic. Get it right.

### Contract

Every applet is a class implementing a common contract:

- **Metadata**: stable `id` (String), `displayName` (String), `icon`.
- **Lifecycle hooks** (all optional except UI):
  - `onInit(context)` — called once when the applet is **started** (first selection,
    or restart after stop). Restore persisted state here.
  - `onSuspend()` — user navigated away; applet stays alive in the background.
  - `onResume()` — user navigated back.
  - `onShutdown()` — applet is being stopped or the app is closing. **Persist state here.**
- **`@Composable Ui()`** — the applet's entire UI, rendered into the right viewport.
- **`@Composable PrefsUi()`** (optional) — settings form; `null`/absent ⇒ no prefs node.
- **`AppletContext`** injected at init: gives scoped `config`, scoped `state`, and a
  `dialogs` handle. "Scoped" = automatically tied to this applet's `id`.

### Tab behavior requirements

Applets must behave like **browser tabs**:
- Independent of each other.
- Once started, they keep running in the background and stay ready for the user.
- UI state (e.g. **scroll position**, text field contents) must survive switching
  away and back **with zero serialization** — switch out, switch back, exactly where
  you left it.

### How to implement it

**Never destroy a started applet's composable when switching away — hide it.**

- The viewport keeps **all _started_ applets in the composition tree simultaneously**;
  only the active one is visible. Render inactive ones at zero size / clipped, so
  Compose's `remember { }` / `rememberScrollState()` state stays live (that state is
  tied to the composition node, not to visibility).
- Still call `onSuspend()` / `onResume()` on switch so applets can pause/resume
  timers, polling, etc.

### Started vs. stopped (memory model — confirmed)

- The registry tracks a **`started: Boolean`** per applet.
- The viewport **only composes started applets**. Not-yet-started applets are absent
  from the tree entirely.
- **Stopped = torn down**: removed from the composition (frees memory), state flushed
  to disk via `onShutdown()`. On next selection it is **restarted fresh from disk**
  (`onInit` re-reads persisted state).
- For now every registered applet is **hard-coded, always present in the sidebar, and
  cannot be removed**. On app launch, applets start and restore last state; on app
  shutdown, every started applet persists current state.
- **Future (design for it, don't build it yet):** a per-applet stop button, and
  starting an applet by selecting its sidebar badge — mirroring the started/stopped
  lifecycle already modeled here. No notifications/badges yet.

---

## 5. Persistence (confirmed)

- **Format: JSON.** Use another format only if there's a significant, justified benefit.
- **One file per applet per concern**, plus a global host file:
  - State:  `<applet-id>-state.json`
  - Config: `<applet-id>-config.json`
  - Host:   `host-config.json`
- **Location: `~/.applet_carrier`** — a dotfolder in the user's home directory
  (cross-platform: `%USERPROFILE%\.applet_carrier` on Windows, `~/.applet_carrier` on
  macOS/Linux), created lazily on first write. Kept behind a single helper
  (`AppDirStoreFactory`) so it's trivial to change.
- **Filenames are ASCII-normalized**: lowercase, spaces → hyphens, strip/replace any
  non-ASCII characters. E.g. applet display name "My Notes" ⇒ id/file stem `my-notes`.
- **State lifecycle**: start with defaults on first run (no welcome/setup dialog).
  Always attempt to **save current state on shutdown** and **restore last state on
  startup**. Scroll positions and similar UI state should be restored on restart
  (pass the restored value as the initial value to `rememberScrollState(initial = …)`).
- Each applet generates and owns its **own** state/config files via its scoped
  `AppletContext` — applets do not read each other's files.

---

## 6. Folder & class structure

Create under `shared/src/commonMain/kotlin/com/example/applet_carrier/`
(use `jvmMain` for JVM-specific bits like file IO):

```
com/example/applet_carrier/
├── core/
│   ├── AppletRegistry.kt      # holds all registered applets + started:Boolean state
│   ├── AppletHost.kt          # top-level state: which applet is active, start/stop
│   ├── LifecycleManager.kt    # routes onInit/onSuspend/onResume/onShutdown
│   ├── StateStore.kt          # generic JSON persistence, scoped per applet id
│   └── ConfigStore.kt         # typed config load/save (global + per-applet)
│
├── api/                       # the contract applets implement
│   ├── Applet.kt              # interface / abstract base (see §4)
│   ├── AppletContext.kt       # injected services: config, state, dialogs
│   └── AppletMetadata.kt      # id, displayName, icon
│
├── ui/
│   ├── theme/
│   │   ├── CarrierTheme.kt    # MaterialTheme wrapper w/ the design system in §7
│   │   ├── Colors.kt          # palette constants
│   │   └── Dimens.kt          # spacing, radius, sizes
│   ├── shell/
│   │   ├── ShellWindow.kt     # root window composition
│   │   ├── TopBar.kt          # global toolbar + prefs button
│   │   ├── AppletSidebar.kt   # left vertical tab bar (scrollable)
│   │   └── AppletViewport.kt  # right panel; composes all STARTED applets, shows active
│   ├── prefs/
│   │   ├── PrefsWindow.kt     # separate preferences window (two-panel)
│   │   ├── PrefsTree.kt       # left tree: General + Applet Settings(children)
│   │   ├── GeneralPrefsPage.kt
│   │   └── AppletPrefsPage.kt # delegates to applet.PrefsUi()
│   └── dialogs/
│       ├── DialogHost.kt      # overlay manager: show/dismiss
│       ├── ConfirmDialog.kt
│       └── TextInputDialog.kt
│
└── applets/                   # compiled-in example applets (see §8)
    ├── hello/
    │   └── HelloApplet.kt
    └── list/
        └── ListApplet.kt
```

Wire registration in `AppletRegistry` (hard-coded list for now). `main.kt` in
`:desktopApp` stays thin: create the host, open `ShellWindow`, route close → shutdown.

Note: the wizard left demo files (`App.kt`, `Greeting*.kt`, `Platform*.kt`) in
`:shared`. Replace/remove the demo `App()` usage when wiring the shell; keep or
delete the others as appropriate.

---

## 7. Visual design system — JetBrains-IDE-inspired dark theme

Reproduce the **principles, density, and atmosphere** of modern JetBrains IDEs —
**not a pixel clone**. The UI must feel: industrial-grade, precise, information-dense,
calm, highly functional, modular, **engineered rather than decorative**. Like a
professional workstation / developer tool, not a consumer SaaS landing page.

**Avoid:** glossy surfaces, oversized spacing, playful design, neumorphism, heavy
gradients, big shadows, colorful surfaces, large blur/glow, theatrical or spring
animation, pill shapes.

**Prioritize:** low visual noise, restrained contrast, precise alignment, subtle
depth, efficient use of space, excellent readability, modular panels.

### Color palette (dark, slate/graphite — never pure black)

| Role             | Value                  |
|------------------|------------------------|
| Background       | `#1E1F22`              |
| Surface          | `#2B2D30`              |
| Elevated surface | `#35373B`              |
| Border           | `rgba(255,255,255,0.06)` |
| Primary text     | `#DFE1E5`              |
| Muted text       | `#9DA1A8`              |
| Accent           | `#4B9FFF`              |
| Secondary accents| cyan / violet / magenta — **used sparingly** |

### Surfaces

Separate surfaces by **luminance**, not big shadows. Use subtle borders, soft inner
highlights, tiny ambient shadows. Reference treatment:

```
background: #2B2D30;
border: 1px solid rgba(255,255,255,0.05);
box-shadow:
  inset 0 1px 0 rgba(255,255,255,0.03),
  0 0 0 1px rgba(0,0,0,0.4);
```

No glowing panels, bright outlines, or dramatic elevation.

### Spacing & density

Compact but readable: tight vertical rhythm, consistent spacing, small controls,
compact lists. No oversized padding, no card-heavy layouts, no giant buttons.

### Typography

Technical, crisp, medium weight, compact line height, subtle hierarchy. Preferred
families: Inter, JetBrains Mono, SF Pro, Segoe UI (Segoe UI is the safe Windows
default). Sizing: sidebar 12–13px, tabs 13px, body/editor 13–14px, secondary labels 11–12px.

### Interaction & motion

Subtle, restrained feedback. Hover: `background: rgba(255,255,255,0.04)`. Selection:
thin blue accent (underline/indicator) + slight contrast increase. Elements stay
visually quiet until interacted with. Animations are functional and nearly invisible:
**120–180ms**, `linear` or `ease-out`, only for fades / panel & tab transitions /
hover / subtle sliding. No bouncing, elastic, spring, or decorative motion.

### Border radius

Restrained: **4px–6px**. No pills, no playful geometry.

### Components

- **Buttons**: compact, muted by default, stronger contrast on hover/focus.
- **Inputs**: dark surface, subtle border, understated focus glow.
- **Tabs (sidebar rows)**: low-contrast inactive, thin accent indicator when active.
- **Sidebars/panels**: minimal chrome, near-invisible separation, rely on spacing + luminance.

Implement these as constants/composables in `ui/theme/` and reuse everywhere; do not
scatter raw hex values across the codebase.

---

## 8. The two example applets to build

Both must run inside the shell, register in `AppletRegistry`, and follow the design
system in §7.

### Applet A — "Hello" (single screen, color-toggling button)

- A single Hello-World screen with one button.
- The button is **blue by default**; clicking it changes its color to **green**.
  (A toggle is fine — clicking again may revert, your call — but the core spec is
  blue → green on click.)
- Use the accent palette for blue; pick a restrained green consistent with the theme.
- Good minimal demo of an applet that holds simple in-memory + persisted state.

### Applet B — "List" (scrollable clickable items)

- Shows **5 clickable items** in a **scroll area** (compact list rows per §7).
- Clicking an item gives visible selected/active feedback (thin accent indicator).
- This applet is the **scroll-position test case**: scroll it, switch to Hello,
  switch back → the scroll position **must be preserved** (browser-tab behavior, §4).
  Also persist last scroll position / selection to its `-state.json` so it restores
  across app restarts.

---

## 9. Working agreements for agents

- **Verify, don't guess versions/APIs.** This stack (Compose MP 1.11, Kotlin 2.4,
  Material3 alpha) is newer than some training data. If unsure about an API name or
  coordinate, check the version catalog and existing generated sources first, and
  prefer compiling to confirm.
- **Build/run** through the `:desktopApp` module on Windows (`gradlew.bat`). Confirm
  it compiles before claiming completion; report real output if it fails.
- **Keep it simple but extensible.** Every "for now" decision above has a known future
  extension — leave clean seams (interfaces, single-source helpers) rather than
  hardcoding assumptions throughout.
- **Respect boundaries.** Applets own only the right viewport (and their own prefs
  page + their own files). Shell owns top bar, sidebar, prefs chrome, lifecycle, and IO.
- **Don't over-ask.** Decisions in this document are settled. Ask only about genuinely
  new ambiguities.
