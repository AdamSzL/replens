# RepLens

AI-powered personal trainer for Android. Phone camera + on-device ML (Google ML
Kit Pose Detection) analyzing exercise form in real time: live pose tracking with
a skeletal overlay, form correction via TTS, and automatic rep counting. History
in Room, synced to a hand-built backend for stats and leaderboards.
*"Every rep, seen."*

**Goals: fun to build, and shipped to Google Play as a complete product.**
Enjoying the work and getting a real release out is the payoff; portfolio value is
a side effect, not a driver. **Explicitly not a race to a minimum release** — v1
should feel finished: history, stats, sync, leaderboard, a few exercises, setup
guidance that works. No deadline, so quality and experimentation beat speed.
Corollary: **the backend is hand-built (Kotlin + Ktor) and is part of v1** —
Firebase/BaaS was considered and rejected; building it is part of the fun.

## Repository layout

```
replens/
├── client/   # Android app — independent Gradle build, open in Android Studio
└── server/   # Ktor API — independent Gradle build, open in IntelliJ
```

- **Two independent Gradle builds; no root Gradle project.** Never add a root
  `settings.gradle.kts`. Open `client/` or `server/` directly.
- **DTOs are duplicated between client and server, not shared.** Mobile clients
  can't be force-updated, so the server must stay backward-compatible anyway. API
  evolution discipline from day 1: default values, `@SerialName`, tolerant
  reading, versioned endpoints. Revisit at ~20–30 DTOs (upgrade path: OpenAPI).

**Reconsidered 2026-08-11 and kept.** A root `settings.gradle.kts` with a
`:shared` module is the standard full-stack Kotlin advice, and it is right for a
web client — which is redeployed *with* its server. Mobile inverts that premise:

- **A shared DTO makes the compiler lie about compatibility.** Renaming a field,
  tightening `String?` to `String`, or adding a required one compiles clean and
  passes every test, then breaks every installed app. Duplication is what forces
  the version skew to be *felt*: changing the server's copy means walking to the
  client's and deciding what an old client does with it. Discipline inside a
  shared module is discipline nothing checks.
- **Enums are DTOs.** The server adds `PULL_UP` and an old client's `enumValueOf`
  throws. The client needs a tolerant fallback arm regardless — a client-side
  decision the server must not own.
- **The client is the fragile build.** AGP 9's built-in Kotlin, modules that must
  not apply `kotlin.android`, a Kotlin version raised only via classpath conflict
  resolution, convention plugins limited to what is already on the buildscript
  classpath (see *Toolchain gotchas*). Merging puts server plugin resolution
  through the most delicate machinery here; today a broken server build cannot
  stop the app shipping. Also lost: the server in IntelliJ rather than Android
  Studio, and deploy pipelines that don't check out the other half.

What would actually be shared is thin. Validation rules are a regex and two ints,
and the server must re-validate anyway since it cannot trust a client. The one
genuinely valuable share — exercise thresholds — isn't needed, because the server
aggregates and writes summaries and does no pose math. **If duplication starts to
hurt, the answer is OpenAPI, not `:shared`:** generated client DTOs are sharing
*with* a version boundary, since regeneration is deliberate and the skew appears
in a diff. `includeBuild` of a shared module is the middle option, and it fixes
the Gradle-fragility objection while fixing none of the compatibility one.

## Identifiers

- Android `applicationId`: `com.replens.app` — **permanent on Play after first
  publish; never `com.example.*`**
- Server: group `com.replens`, artifact `server`
- Domain `replens.app` — owned (Cloudflare, expires 2027-08-05). Future API host
  `api.replens.app`; `.app` is HSTS-preloaded, so HTTPS is mandatory.

## Server tech stack

**Ktor, not Spring Boot — decided 2026-08-11, before a line was written.** Spring's
value is amortizing complexity across a team and many modules; this API is ~12–15
endpoints written by one person, which is exactly the regime where the amortization
never happens and only the tax is paid: ~2–4 s startup against a few hundred ms
(cold-start latency on anything that scales to zero), roughly 3–4x the idle memory,
`allOpen`/`noArg` compiler plugins to make JPA entities work, and Jackson as the
well-trodden path — which would put a *different* serializer on each side of the
wire for no reason. Ktor is also the more hand-built of the two, which is the
stated reason this backend exists at all.

- Ktor + kotlinx.serialization, Exposed, Flyway, Postgres, HikariCP. Exposed's DSL
  is about as short as JPA repositories at this schema size and the SQL stays
  visible.
- **No Hilt — it is Android-only.** DI is constructor wiring in
  `Application.module()`; Koin only if that stops scaling.
- **Spring Security is the one real loss, and it is not close** — nothing in Ktor
  matches it. Priced before choosing: verifying a Google ID token means fetching
  the JWKS and checking the signature plus `iss`/`aud`/`exp`, which is the `jwt {}`
  block and ~40 lines against stable, well-documented behavior. An evening, not a
  wall — and the payoff is understanding our own auth.

## Client tech stack

- Kotlin, Jetpack Compose (pure, no Views), Jetpack Navigation 3
- Hilt (DI), Room (local history), kotlinx.serialization
- CameraX (`ImageAnalysis`) + ML Kit Pose Detection
- **Ktor Client — settled 2026-08-11** (was "Retrofit vs Ktor, decide when
  `:core:network` is built"). The margin is small and worth stating honestly: both
  sit on OkHttp, so connection pooling, HTTP/2 and the cache are identical either
  way, and Retrofit's annotated interface is the nicer thing to read. Three points
  decided it:
  - **Token refresh.** Ktor's `Auth` plugin
    (`bearer { loadTokens; refreshTokens }`) retries the original request after a
    401 and serializes concurrent refreshes. Retrofit means an OkHttp
    `Authenticator` written by hand, where five requests 401-ing at once all firing
    a refresh is a classic thing to ship broken. With optional login plus
    background sync that is a Tuesday, not an edge case.
  - **Error bodies.** Per-endpoint sealed errors (see *Result and errors*) need the
    failure payload. Retrofit hands back `errorBody(): ResponseBody?` to
    deserialize by hand on a path separate from the success one; Ktor's `body<T>()`
    doesn't care about the status.
  - **One HTTP library across both halves**, now that the server is Ktor — one
    serialization setup and one mental model for the person writing a route and its
    caller in the same sitting.

  The `safeApiCall`-style wrapper is real but smaller than it sounds: ~30 lines in
  Ktor against a `CallAdapter.Factory` or `Response<T>` at every call site. **This
  would not have been worth a migration** — greenfield is what makes it free.

### Toolchain gotchas (non-obvious)

- Versions in `client/gradle/libs.versions.toml`. minSdk 26, Java/Kotlin 21
  (daemon JVM pinned in `gradle-daemon-jvm.properties`).
- **AGP 9 built-in Kotlin:** modules must NOT apply `org.jetbrains.kotlin.android`
  (KGP refuses). The Kotlin version is raised by declaring
  `alias(libs.plugins.kotlin.android) apply false` in the root build file —
  classpath conflict resolution lifts AGP's bundled KGP. Built-in Kotlin aligns
  `jvmTarget` with `compileOptions`; don't add a `kotlin {}` block for it.
- **Convention plugins can only apply plugins already on the buildscript
  classpath.** Hence every third-party plugin is declared `apply false` in the
  root build file, which is also where its version is pinned. build-logic's
  `compileOnly(...)` deps don't help — they only compile the plugin's source.
  (Verified: removing them fails with `Plugin with id '…' not found`.)
- **Hilt in Compose: use `androidx.hilt:hilt-lifecycle-viewmodel-compose`, not
  `hilt-navigation-compose`** — the latter drags in Navigation *2*. Under Nav 3,
  `hiltViewModel()` additionally needs `rememberViewModelStoreNavEntryDecorator()`
  in `NavDisplay(entryDecorators = …)`, or every destination shares the Activity's
  `ViewModelStore` — a silent scoping bug, not a compile error.
- **Compose stays `implementation` in `:core:ui`, and the composable overload is a
  landmine because of it.** Catalog Compose entries are version-less (the BOM
  supplies versions), so `api`-ing one exports a dependency that non-UI consumers
  cannot resolve — `:core:audio` has no BOM and failed with
  `Could not find androidx.compose.runtime:runtime:`. `implementation` is correct
  here because `UiText` has **no Compose types in any signature**; `@Immutable` and
  `@Composable` are annotations, and unresolvable annotations are silently skipped
  when reading a class file. The day a Compose *type* enters a signature, `api`
  becomes mandatory and non-UI modules can no longer depend on it.
  The cost, **verified 2026-08-09**: a module without the Compose plugin can call
  `UiText.asString()` (the `@Composable` one) and it **compiles with no error or
  warning**, then throws
  `NoSuchMethodError: UiTextKt.asString(UiText)` at runtime. The Compose plugin
  rewrites the JVM signature to `asString(UiText, Composer, int)`, but Kotlin
  resolves calls from `@Metadata`, which still describes the *source* signature —
  and the `@Composable` annotation that would give it away is exactly the thing
  being skipped. A Java caller would be safe: `javac` reads the class file, not the
  metadata. **From a non-Compose module, use the `Context` overload.**
- The Compose BOM is declared twice on purpose (`implementation` +
  `androidTestImplementation`); the IDE's duplicate warning is a false positive.
- Release runs R8 + resource shrinking; verify `assembleRelease` when adding
  libraries.
- AGP 9 plugin code: `CommonExtension` has no type parameters, and DSL blocks are
  property access (`defaultConfig.minSdk = 26`), except
  `compileSdk { version = release(37) }`.

## Architecture

Module map — each `build.gradle.kts` is convention plugins + namespace + deps:

```
:app                MainActivity: camera permission gate -> WorkoutRoot. Later:
                    NavDisplay, back stack, every cross-feature edge.
:feature:workout    …workout.ui/            the five files (no Event yet), plus
                                            CueAnnouncer — the speech gate
                    …workout.ui.mapper/     SessionCue: SetupCheck/SessionState
                                            -> UiText, drawn and spoken from one
                                            source
                    …workout.ui.model/      SpokenCue
                    …workout.ui.components/ PoseOverlay, RepCounter,
                                            SessionControls, ZoomControl
                    22 tests.
:core:pose          PoseCameraDataSource: CameraX + ML Kit behind Flow<PoseFrame>
                    + surfaceRequests. PoseMapper is internal — the ML Kit boundary.
:core:audio         Speaker + TtsSpeaker: one engine, locale negotiation, audio
                    focus. Silence is a legal outcome, never an error.
:core:ui            UiText and its two resolvers. 8 tests. ObserveAsEvents lands
                    when something is actually one-shot.
:core:designsystem  RepLensTheme + the app's Compose gateway (below).
                      …component.button/ Primary, OverlayPrimary,
                                         OverlaySecondary, OverlayIcon
:core:model         Landmark, LandmarkType, BodyPose, PoseFrame. Pure Kotlin.
:core:posemath      Point, joint angles, torso size, normalized distances, line
                    deviation; OneEuroFilter + PoseSmoother. Pure Kotlin,
                    domain-free (no thresholds, no exercise names). 55 tests.
:core:exercise      Exercise knowledge and thresholds. Pure Kotlin. 76 tests.
                      …exercise/       Rep, RepPhase, RepUpdate, Framing,
                                       SetupCheck, SessionState, SetSession
                      …exercise.squat/ SquatSignals, SquatRepCounter, SquatRepConfig
```

Planned, not built: `:core:data`, `:core:network`, `:core:database`,
`:feature:{history,stats,leaderboard}`.

### Key decisions

- **Pure-Kotlin core:** domain models, smoothing, angle math, rep state machine
  live in `replens.jvm.library` modules — no Android imports, enforced by
  compilation. ML Kit types must not leak past `:core:pose`.
- **One exercise module, one package per exercise** (`…exercise.squat`), with a
  small shared vocabulary at the root. A module per exercise was rejected: with a
  2–3 exercise scope guard it's four Gradle projects doing one project's work, and
  packages already enforce the only boundary that matters. **Whether
  `Rep`/`RepPhase` generalize is unproven** — exercise #2 decides that, and
  whether `SquatRepCounter` becomes a parameterized counter. Splitting later is a
  directory move.
- **Feature-owned navigation, no api/impl split.** Features never depend on each
  other. Each owns its `NavKey` routes and exposes one
  `EntryProviderScope<NavKey>.<feature>Entries(...)`; `:app` calls those and owns
  the back stack. Details in *Navigation*.
- **Every feature screen: `Screen` + `ViewModel` + `State` + `Action` + `Event`.**
  A per-feature convention, **not** a generic `BaseViewModel<S, A, E>` —
  inheritance-based MVI is where this pattern dies. Details in *Feature
  architecture*.
- Convention plugins in `client/build-logic/` (included build):
  `replens.android.application`, `replens.android.library`,
  `replens.android.compose` (additive: compose flag + BOM + tooling; non-UI
  modules must not apply it), `replens.jvm.library` (pure Kotlin, no AGP — must
  set kotlinc's jvmTarget explicitly), `replens.hilt` (additive: KSP + Hilt
  plugin + `hilt-android` + `ksp(hilt-compiler)`; Android modules only).

### Dependency rules

- **`api` only for types in a module's public signatures**, `implementation`
  otherwise. Watch for deps that work only via someone else's transitive graph.
- **`:core:designsystem` is the Compose gateway** — it `api`s foundation,
  material3, runtime, ui, ui-graphics, so UI modules get the toolkit from the
  design system they already depend on. The compose convention plugin supplies
  only the compiler plugin, `buildFeatures.compose`, the BOM (catalog entries are
  version-less on purpose) and preview tooling. Plugin = capability, design system
  = toolkit; neither is sufficient alone.
- Version catalog, plugin aliases, and project accessors are all type-safe — no
  string dependency notation.

### Design system — custom vocabulary, not Material's

**Built 2026-08-08.** Color and typography have RepLens names and RepLens values;
M3 remains a component library we wrap. Note what was **not** rejected: Now in
Android is itself fully custom — its palette and all 15 type slots are its own.
The only axis was whose semantic vocabulary to adopt, so this is not "Material bad."

Two arguments decided it:

- **The component → role mapping is library-internal and versioned.** `Card` reads
  `surfaceContainerLow`; it used to read `surfaceVariant`. Under Material's
  vocabulary a `compose-material3` bump can restyle the app with no code change.
  Colors stated at our own wrappers can't be touched by an upgrade.
- **M3's five button variants encode Material's emphasis hierarchy, not ours.**
  Every choice between `FilledTonalButton` and `ElevatedButton` is a developer
  answering a Material question, and inconsistent answers are how a solo-built app
  drifts. `PrimaryButton` makes the right choice the only choice.

What exists:

- **Two tiers.** `Palette` is `internal`, holds only raw values (`Slate6`,
  `Cyan11`), and is never referenced by UI. `RepLensColors` maps those onto names
  that mean something. Light/dark works because the semantics remap, not because
  there are two palettes.
- **Radix Colors** — `slate` + `cyan`, values copied from the repo. Its numbered
  steps have defined jobs (9 = solid brand, 11 = low-contrast text, 12 =
  high-contrast), so contrast is inherited rather than eyeballed. **Keep Radix's
  numbering**: the number is a foreign key into their docs, and gaps just mean we
  have not needed those steps yet.
- **`accent` is cyan, deliberately not green/amber/red** — those three are spoken
  for by form feedback, and a brand color that collides with "your knees are
  caving" makes the one moment color carries meaning meaningless.
- **`accent` inverts with the theme; overlay colors never do.** A fill has to
  separate from what is behind it, so a *fixed* accent against a background that
  flips can only be right in one mode — cyan 9 measured 2.93:1 on the light page,
  under the 3:1 a component boundary needs. It is now **Radix step 11 in both
  themes**: dark fill with a light label on white (4.65:1), light fill with a dark
  label on dark (9.96:1). Step 9 left the palette entirely — it is Radix's "pure
  brand" step, kept near-identical across modes on purpose, which is exactly wrong
  for this job. `accentText` went with it, since step 11 already *is* the text step.
- **Overlay colors are theme-independent.** Anything drawn on the camera feed is
  competing with an unknown room, not with our background. And no fixed color is
  legible over arbitrary video — cyan on a mid-gray wall measures 2.09:1 — so the
  skeleton is drawn with a dark outline behind it and carries its own contrast.
  The rule that makes this coherent: **a camera feed under a scrim is a dark
  surface, permanently**, so overlay colors are the dark-theme values frozen —
  which is why `LightColors` reaching into `Palette.Dark` is correct rather than a
  slip. `Palette.Dark`/`Light` name the *surface* a color sits on, Radix's own
  meaning, not the app's theme.
- **`overlayScrim` tints, `overlaySurface` covers — a scrim is not a fill.** Two
  scrims stack (50% over 50% is 75%), so a scrim-filled button on a scrimmed card
  rendered *darker* than the card, and only looked like a button because the room
  behind it happened to be bright. Panels over the feed are opaque; nothing can
  reliably contrast against a translucent surface, because its rendered color is
  partly the room. Buttons on the feed are opaque too, and primary and secondary
  separate **by hue, not lightness** — every dark secondary measures 1.2–1.4:1
  against that panel.
- **`on` pairing kept in our names** (`accent`/`onAccent`). The one genuinely
  valuable part of M3's color system. Giving it up makes **contrast our job**:
  every pair was measured once, and the worst is 4.65:1.
- **No `MaterialTheme` at all.** `RepLensTheme` provides two `CompositionLocal`s
  and nothing else. Un-wrapped M3 components fall back to Material's own defaults
  and render visibly wrong, which is how we find them — the absence *is* the leak
  detector, so don't add a floor "to be safe". `IconButton` was the last leak —
  it took its colors from `IconButtonDefaults`, hence `OverlayIconButton`.
- **Typography named for the job, not the size** — `display`, `title`, `body`,
  `label`. `Title28` becomes a lie the moment the size changes or an accessibility
  setting scales it. Four sizes, two weights. `display` sets `tnum` so the rep
  counter doesn't reflow as it passes 9.
- **Montserrat, subset by `tools/subset-fonts.sh`** — Google Fonts ships 325 KB per
  weight for a character set we don't render; Latin + Polish is 101 KB. The
  variable font is 672 KB against 649 KB for two statics, so it only pays from
  three weights up.
- **Wrap every M3 component before first use.** Nia's `DesignSystemDetector` lint
  rule is the eventual enforcement, once there are enough wrappers to police.

**No spacing, sizing or radius tokens — plain `.dp` at call sites.** Color varies
by theme and type size varies by the user's font scale, so both earn a token layer;
spacing varies by nothing, so a token is just a second name for a number. Two
failure modes seen first-hand and rejected:

- **Semantic scales** (`Spacing.md`, `IconSize.large`) give no guidance — is a
  card's inner padding `md` or `lg`? Two people answer differently and the same gap
  ends up under two names, which is worse than raw numbers because the
  inconsistency is no longer visible.
- **Rescaling wrappers** (`.figmaDp`) are worse than a naming problem: `dp` is a
  platform guarantee that 48dp is a 48dp touch target, and a wrapper that rescales
  it makes accessibility minimums silently approximate.

Keep the **rule** instead — only 4/8/12/16/24/32 — enforceable by a detekt rule if
it ever drifts. A `private val` for a value repeated within one file is fine; that
is naming a local constant, not building a token layer.

**Build the structure, populate on demand.** Tokens designed against imaginary
screens are guesses.

Written by a solo dev with no designer, which shapes the tactics: **borrow a
palette** rather than invent one, and hold to few values. Amateur UI comes from too
many values, not the wrong ones. The visual load here is unusually low anyway — the
workout screen is invisible during a set, and history/stats/summary are lists and
numbers.

### Icons

**No `material-icons-*` dependency.** Compose no longer bundles `Icons`, and the
extended artifact is bloat for the handful we need. Icons are vector XML
downloaded from [Material Symbols](https://fonts.google.com/icons) into
`:core:designsystem` — filled variants, exported as SVG. The export color is
irrelevant (pick black): `Icon(tint = …)` replaces it, and we always pass one
since nothing provides `LocalContentColor`. What matters is that icons stay
**single-color**, or the tint flattens them.

Feature modules must not import a foreign `R`, so the design system exposes them
by name:

```kotlin
object RepLensIcons {
    @DrawableRes val CameraSwitch = R.drawable.ic_camera_switch
}
```

`@DrawableRes Int` rather than `ImageVector`: every icon has the same source, so
there is nothing to normalize, and an id also works where a `@Composable` getter
cannot — notification small icons, shortcuts, `RemoteViews`. Call sites use
`painterResource`, which covers bitmaps too if one ever appears.

**No `resourcePrefix` yet, deliberately.** Resource merging is flat across
modules, so same-named resources silently override — but `RepLensIcons` means
every id has exactly one reference, making a later rename trivial. Turn the
parked `resourcePrefix` on when a collision-prone generic name appears
(`ic_close`, `ic_settings`), not before.

### Haptics

**The rule: only where the phone is in your hand *and* the buzz says something
the screen does not.** That rules out every in-set event by construction — the
phone is propped 2–3 m away, so a rep haptic is felt by the floor. Haptics here
are a setup-phase polish, not a feedback channel; audio is the feedback channel.

What fires today, all via `LocalHapticFeedback.current` at the call site rather
than baked into the design system components — the same wrapper serves Start
(should buzz) and a future settings button (should not), so the component cannot
know:

| where | type | why |
|---|---|---|
| zoom stop | `SegmentTick` | your finger covers the pill you tapped |
| Start set / Go again | `Confirm` | the one press whose result you stop watching |
| camera flip | `Confirm` | the stock camera does it; not matching reads as unfinished |

Deliberately silent: **Cancel, Finish, Done.** Each changes the screen while you
are looking at it. The asymmetry is itself the signal — a buzz means "you
committed to something you are about to stop watching", and if everything buzzed
it would only mean "you touched glass".

**`Reject` is not for Cancel.** It signals *the app* refusing *the user* — invalid
input, a disabled control, a swipe that snapped back. Cancel is the user rejecting
and the app complying, so `Reject` would report failure at the moment of success.
The near-miss worth knowing: pressing Start while too close genuinely is a refusal,
but it is a "not yet" rather than a failure, and `Confirm` already fired on that
same press.

The catalogue, so the next choice is a lookup rather than a re-derivation:

| type | for |
|---|---|
| `Confirm` | an action completed successfully |
| `Reject` | the app refused or the action failed |
| `SegmentTick` | moving between a few discrete choices — zoom stops, slider notches |
| `SegmentFrequentTick` | moving between *many* — minutes on a dial, percentages |
| `ToggleOn` / `ToggleOff` | a switch changing state (front/back is **not** a toggle) |
| `LongPress` | a long press that fired its action |
| `ContextClick` | a context click |
| `GestureEnd` | a gesture finished |
| `GestureThresholdActivate` | a drag crossed the point where releasing would act — pull-to-refresh |
| `TextHandleMove` | a selection handle moved |
| `KeyboardTap` / `VirtualKey` | soft-keyboard and on-screen key presses |

Likely next candidates when the features land: **`ToggleOn`/`ToggleOff` for the
settings switches** — voice cues, the skeleton overlay, whatever else earns a
switch — which is the one place those two are literally correct rather than
stretched; `GestureThresholdActivate` for a swipe-to-delete in history; and
`SegmentTick` for scrubbing the replay of a rep.

**M3's `Switch` does not fire them itself**, so the wrapper has to — there is no
double-buzz to avoid.

### The 30 fps path

**The math is free** — One Euro over a pose is ~1,000 float ops, ~1 µs against a
33 ms budget. Don't move it to `Dispatchers.Default`; the context switch costs
more than it saves. **ML Kit already infers off-main**, and `flowOn(Main)` in
`PoseCameraDataSource` is required by `bindToLifecycle`, not a mistake.

What actually costs:

1. **Compose recomposition.** **Frame-rate data gets its own stream** —
   `poseFrame` is a separate `StateFlow` from `state`, because Compose subscribes
   per `State` object, so sharing one would invalidate the rep counter 30×/s.
   `PoseOverlay` takes `() -> PoseFrame?` and reads it inside the `Canvas` draw
   lambda; `WorkoutRoot` collects it **without `by`** so the lambda captures a
   stable `State` box. **Do not hoist that read** — it looks like a tidy-up and
   silently costs both the deferral and the lambda memoization.
   *Measured 2026-08-07:* 24 recompositions per session (4 phase transitions per
   rep), `PoseOverlay` skipped 24/24. Draw-phase invalidation is invisible in the
   Layout Inspector — the `Canvas` still redraws every frame, which is the point.
2. **Allocation churn** — ~2,000 objects/second (landmarks + map + frame, twice,
   because `PoseSmoother` rebuilds). The profiler metric to watch, not CPU.
3. **Delayed `imageProxy.close()`** — silently drops frames under
   `KEEP_ONLY_LATEST`. `ImageAnalysis` runs on its own single-thread executor to
   keep the pipeline off Compose's frame timing.

Stability of `PoseFrame`/`BodyPose` (a `Map`, hence unstable) is **moot** — under
strong skipping it only selects `equals` vs identity comparison, and the frame
never crosses a composable boundary as a parameter. If it ever matters, use the
Compose stability configuration file rather than annotating `:core:model`, which
must stay Compose-free.

**When Room/network arrive:** inject dispatchers via Hilt qualifiers rather than
hardcoding `Dispatchers.IO` — retrofitting a `TestDispatcher` afterwards is
painful.

## Feature architecture

The shape every feature module follows; deviate only with a recorded reason.

```
data/         network, data sources, Room/DataStore, repository impls
di/           <Feature>Module.kt
domain/       repository interfaces; UseCases only if they earn their keep
model/        feature-only models (rare)
navigation/   route NavKeys + the EntryProviderScope extension
ui/           the five files
ui/model/     UiModels          ui/mapper/  domain -> UiModel
ui/components/  smaller composables, each with its own @Preview
```

`domain/` holds the repository *interface*, `data/` the impl, bound in `di/`.
Worth it for repositories specifically — they're what every ViewModel test fakes.
Do **not** extend it to data sources or mappers. Expect `domain/` to stay mostly
empty: the real logic already lives in pure-Kotlin core modules. The repository
speaks only domain models, wrapped in `Result`.

### The five files

`<Feature>{Action,Event,State,Screen,ViewModel}.kt`, all in `ui/`.

- **Actions are past tense** — `StartClicked`, `PermissionGranted`. No `On`
  prefix, so non-click actions read consistently.
- **Events are imperative** — `NavigateToSummary`.
- `<Feature>Screen.kt` holds exactly two composables: `<Feature>Root` (internal —
  collects state, hosts `ObserveAsEvents`, maps events to navigation callbacks;
  its `when` may handle an action directly to skip a pointless ViewModel
  round-trip) and `<Feature>Screen` (private — `state` + `onAction`, renders,
  always has a `@Preview`).
- Soft cap ~300 lines; overflow goes to `ui/components/`, each with a preview
  (also the future surface for screenshot testing).

### State modeling

Default: a **data class** with screen-level flags plus one
`val content: <Feature>Content` — a sealed interface with `Loading` / `Error` /
`Loaded`. Drop to a bare sealed root only when there is genuinely nothing outside
content; converting back later rewrites every `when` at every call site, and
screens reliably grow flags. `Loaded`, not `Success` — these are states, not
completed operations. Not every screen needs it — the workout screen has no
loading *phase*, though it does have not-yet-known *values* (below).

**Nullable when it's an observation we haven't made; a plain default when it's a
choice we made.** A default that stands in for an unasked question is a lie, and
it hides the difference between "haven't heard back" and "heard back, nothing
there". The workout screen has one of each:

- `zoomRatio = 1f` — a choice, and valid on every camera (`min ≤ 1 ≤ max` always
  holds). Never unknown, so never nullable.
- `cameraFacing: CameraFacing? = null` — also a choice, but **constrained by
  hardware**. Until the constraint is known no valid choice exists, so it waits.
- `repCount = 0` — a real value, not a placeholder.

The trap this avoids: `zoomStops = emptyList()` meant both "the camera hasn't
reported" and "this camera has nothing to offer". It worked only because the
`size > 1` render check happened to collapse them — a coincidence, not a design.
Group discovered values into one nullable object rather than giving each its own
sentinel convention.

### Model layers

DTO -> domain -> UI, plus Room entities and navigation arguments. Domain models
stay free of `@Serializable`; routes are separate `NavKey` types with round-trip
mappers.

**Introduce a UiModel when the UI needs formatted or derived data, or when the
domain model carries fields the UI must not see.** A raw geometry stream is
neither — `PoseFrame` reaches the `Canvas` as a domain model, because mapping
30 fps × 33 landmarks would allocate ~1,000 objects/second for numbers the
overlay needs verbatim.

**A 1:1 mapping is the model telling you it is already at the right altitude**,
not an invitation to add a layer. `SessionState` is the case: its arms *are* the
screen's modes, so a UiModel would be a rename. **Stability is the weakest reason
to wrap** — it produces a type whose only job is to carry `@Immutable`, and here
it would not even remove the mechanism, since `CameraOptions` and `ZoomRange`
still need the stability configuration file.
TTS was predicted to be the trigger that earned one, and **half of that prediction
was right** (2026-08-10). `SetupCheck → stringResource` did have to leave the
composable the moment the same line was both drawn and spoken: `stringResource`
needs a composition, and the speaker resolves against the locale its *voice*
negotiated, which is not necessarily the app's. So `UiText` and a mapper, yes.

But **not** `Waiting(message: UiText)`. `SessionState` lives in `:core:exercise`,
which is pure Kotlin, and `UiText` is Android — so a UiModel there would mean
mirroring five arms to change one, and it would still be a rename. The mapper is
an extension on the domain type instead (`SetupCheck.message`,
`SessionState.spokenCue`), which both the composable and the ViewModel call, so
the drawn and spoken lines come from one source and cannot drift.
**The lesson generalizes: needing formatted text is not the same as needing a
UiModel.** A pure function to `UiText` is the smaller answer whenever the state
type is already at the right altitude.

### UiText

All user-facing text the ViewModel or a mapper **chooses between** is a `UiText`
(`:core:ui`), resolved at render time — a `String` resolved in the ViewModel won't
re-render on a locale change. Text that's always the same resource doesn't belong
in state at all. Plain `String` only for server-provided or user-entered content.

Arms: `Raw(String)`, `Resource(@StringRes id, args: List<Any>)`,
`Plural(@PluralsRes id, quantity, args)`. Two `asString()` extensions — a
`@Composable @ReadOnlyComposable` one and one taking a `Context`.

**Args must be a `List`, not an `Array`, and the arms must be data classes.**
`Array` equality is identity-based and `emptyArray()` allocates per construction,
so two structurally identical cues never compare equal — breaking
`MutableStateFlow` conflation and whole-state `assertEquals` in tests.
`@Immutable` doesn't fix that (it governs stability, not equality); both are
needed. Keep `vararg` on companion factories so call sites stay ergonomic.

`Plural.quantity` selects the form and does **not** fill `%d` — default the args
to `listOf(quantity)` or `getQuantityString` throws. Polish (one/few/many/other)
is why this matters and English-only testing won't catch it.

The `Context` overload is what TTS calls, so one `UiText` drives both the on-screen
cue and the spoken line, and the form-rule engine stays unit-testable.

**`UiText` equality became load-bearing once speech existed** (2026-08-10), which
raises the stakes on the `List`-not-`Array` rule above. `CueAnnouncer` decides
whether to speak by comparing a cue against the last one, so equality is not a
rendering optimization there — it is the difference between a countdown you can
hear and one spoken once. Two corollaries: an `Array` in `args` would make the app
repeat every line at frame rate, and **two states that must both be heard need
distinct `UiText`**, which is why the rep callout has its own string resource
despite rendering the same text as the count-in.

### Result and errors

```kotlin
sealed interface Result<out D, out E : AppError> {
    data class Success<out D>(val data: D) : Result<D, Nothing>
    data class Failure<out E : AppError>(val error: E) : Result<Nothing, E>
}
typealias EmptyResult<E> = Result<Unit, E>
```

`Success`/`Failure` with an `AppError` marker — naming the failure arm `Error`
shadows the marker and collides with `kotlin.Error` (a `Throwable`).

**Shared + specific errors.** One `NetworkError` enum every call can produce
(`NoInternet`, `Serialization`, `Timeout`, `Unauthorized`, `TooManyRequests`,
`Server`, `Unknown`), plus a per-endpoint sealed type **only where the UI branches
differently** — login yes, workout sync no. Modeling per-call errors as a sealed
interface with a `Network(NetworkError)` arm also removes the `wrapCommon`
parameter the previous app's `safeApiCall` needed.

### Events

Channel-backed, **`Channel.BUFFERED`, not RENDEZVOUS** — with RENDEZVOUS a
backgrounded screen parks the sending coroutine and stalls whatever follows the
send. Always `send` from `viewModelScope`; never `trySend` (silently drops).

Collected via `ObserveAsEvents` (`:core:ui`): `repeatOnLifecycle(STARTED)` +
`withContext(Dispatchers.Main.immediate)`. The `immediate` matters — without it an
event can land after cancellation has begun, i.e. dropped exactly when the user
backgrounds the app mid-navigation. Wrap the callback in `rememberUpdatedState`,
or a lambda capturing changing state goes stale.

### Navigation (Navigation 3)

Each feature owns its routes and entry provider:

```kotlin
@Serializable data object WorkoutRoute : NavKey

fun EntryProviderScope<NavKey>.workoutEntries(
    navigator: Navigator,
    navigateToHistory: () -> Unit,       // cross-feature: :app decides
) {
    entry<WorkoutRoute> {
        WorkoutRoot(navigateToSummary = { navigator.goTo(WorkoutSummaryRoute(it)) })
    }
}
```

- **No shared routes module.** A feature can only name its own routes, so handing
  it a `Navigator` is safe — the compiler enforces that cross-feature edges are
  hoisted lambdas wired in `:app`.
- **Google's multibinding recipe (`@IntoSet EntryProviderInstaller`) was rejected.**
  Hilt constructs the installer, so cross-feature lambdas can't be passed — which
  pushes you to a shared routes module or the api/impl split. Its payoff is
  unavailable anyway: a bottom bar means `:app` names all top-level routes
  regardless. Reversible in ~10 lines per feature if dynamic features ever appear.
- **Back stack persistence gotcha:** an `@ActivityRetainedScoped Navigator` holding
  `mutableStateListOf` survives rotation but **not process death**. `NavKey`s are
  `@Serializable` from day one so it's fixable (`SavedStateHandle`, or a
  `rememberNavBackStack`-backed list) — do it before release.
- Wrap navigation clicks in `dropUnlessResumed { }`.

### Explicit backing fields

```kotlin
val state: StateFlow<WorkoutState>
    field = MutableStateFlow(WorkoutState())
```

Verified on Kotlin 2.4.10: no flag, no experimental warning, and encapsulation
confirmed empirically (an external `vm.state.value = …` fails to compile).
`state.update { }` resolves fine inside the class — the name is
`MutableStateFlow` there and `StateFlow` only to the outside. The one real
difference from `_state`: `asStateFlow()` returned a distinct read-only wrapper,
whereas this is the same object seen through a narrower declared type, so an
external `as MutableStateFlow<…>` would succeed at runtime. Encapsulation is
compile-time, not runtime.

**`update { }`, always — there is no hot-path exception.** Read-modify-write is
not atomic; it only survives today because `viewModelScope` is main-confined, and
CLAUDE.md plans to inject dispatchers later.

`onFrame` used to guard the write behind a hand-written "did anything change?"
check. **Removed 2026-08-13, because it was protecting against a cost that isn't
there.** `update { }` is a CAS loop, and `StateFlow.compareAndSet` returns without
emitting when the new value `equals` the old — conflation is by contract, using
`Any.equals`. So a redundant `copy` costs **one short-lived object and nothing
else**: no emission, no collector wakeup, no recomposition. At ~28 skipped frames
a second against the ~2,000 objects/second this path already allocates, the guard
was saving ~1.4% of the churn.

What it cost was worse: the guard listed three fields while the `copy` wrote four,
so it was a standing trap — add a state field, write it in `onFrame`, forget the
guard, and it silently never reaches the screen. No compile error and no test
catches that. **A field list that must be kept in sync by hand is not worth 1% of
an allocation budget.**

## Product decisions

**Voice feedback: platform TTS, on-device.** Non-obvious requirements, all of
which make or break the feel:

- **Transient ducking** audio focus with usage `ASSISTANCE_ACCESSIBILITY` — people
  exercise with music on; it should dip, not stop.
- `QUEUE_FLUSH`, never `QUEUE_ADD` — a cue four seconds late is worse than
  silence. One cue at a time, by priority, with a cooldown.
- Language availability isn't guaranteed: try device locale, fall back to English,
  keep every phrase in `strings.xml`.
- Keep one engine instance alive (init costs a few hundred ms).

**Built and validated on device 2026-08-10/11**; all four hold. What is spoken
today: setup problems (repeating), the count-in, "Go", every rep, and the set
summary. `SetupCheck.READY` is **drawn but never spoken** — it holds for
`settleFor`, half a second, and the count-in flushes whatever is still being
said, so it would only ever be heard cut off. The count-in starting is the
confirmation that line would have given.

- **Audio focus is per burst, not per utterance.** Abandoning it when each
  utterance ended let the music ramp back up in the gaps and duck again — the
  count-in pumped it four times in three seconds, which is more distracting than
  the ducking it undoes. Focus now outlives an utterance by `FOCUS_GRACE` (2 s),
  each completion replacing the previous release rather than stacking. Reps
  slower than that still let the music breathe between them, which is fine;
  a *stutter* is what grates, not a dip.
- **The channel's budget is about one short phrase per rep**, set by how fast
  people squat, not by anything in our code. "Eight" is ~0.5 s against a 2-4 s
  rep; "eight, go deeper" is ~1.5 s and most of the slack is gone. This is a
  second, independent route to the decision below that **richness belongs in the
  post-set summary** — no latency budget there.
- **When form cues land, they outrank the rep number**, because both fire at rep
  completion and `QUEUE_FLUSH` means one erases the other. Losing "eight" is
  self-correcting — the next rep says "nine" — and losing "knees out" is not.
- **Cue text is chosen by a shared mapper in the feature module**
  (`ui/mapper/SessionCue.kt`), not carried on the domain type. CLAUDE.md
  previously predicted `Waiting(message: UiText)`; that is wrong, because
  `:core:exercise` is pure Kotlin and `UiText` is Android. One mapper feeds both
  the composable and the speaker, so the drawn and spoken lines cannot drift.
- **`CueAnnouncer` turns a per-frame condition into tolerable speech.** The
  mapper describes what is true on every frame; the announcer speaks only what
  **differs from the last line**, with an optional repeat interval. Two
  consequences worth not re-deriving: cue *inequality* is the entire mechanism,
  so two states that must both be heard need distinct `UiText` (hence the rep
  callout having its own resource despite rendering the same text as the
  count-in); and **silence deliberately does not clear what was last said**, or a
  gate flickering near its threshold would restart the same instruction several
  times a second. `reset()` belongs to starting a set, where the press proves the
  user is listening.
- **Settings, when they land: switch by category, not by message** — setup
  guidance, rep counting, form cues, summary. Per-message granularity sounds
  respectful and is unusable. Needs DataStore, so it rides with the setup and
  settings work; and the categories cannot be designed before form cues exist.

**Form feedback: hand-written geometry rules, not an LLM.** Cues must fire within
~100 ms, and knee valgus is a measurement, not a judgement. Per-exercise rule =
detector + threshold + hysteresis + cooldown + priority. Squat signals: depth
(hip–knee–ankle angle), valgus (knee vs the hip→ankle line), forward lean
(shoulder→hip vs vertical), heel lift, left/right asymmetry.

**Normalize distance-based thresholds by the user's own proportions** (torso size
/ femur length) so rules survive different body sizes and camera distances.
**Angles need no normalization** — they're already scale- and translation-
invariant, which is why depth is the cheapest signal to get right and valgus is
not. **Normalization is translate + scale, NOT rotate.**

ML Kit's [pose classification guide](https://developers.google.com/ml-kit/vision/pose-detection/classifying-poses)
was evaluated and **not adopted**: it tells you *which* pose you're in, not what's
wrong with it, so cues would need labeled bad-form classes per fault, and its
output isn't explainable enough to speak. Taken from it anyway: normalize to
constant torso size, and its entry/exit threshold rep counting (the same
hysteresis our state machine needs). **Not** taken: its rotation to vertical torso
orientation — right for tilt-invariant embeddings, wrong for us, because forward
lean *is* torso angle against vertical, so rotating erases the signal we measure.
Camera tilt is handled by the setup check, not by rotating it away. Reconsider
classification only if hand-tuned thresholds prove brittle around exercise #3.

**An LLM belongs in the post-workout summary, not the live loop** — aggregate
deterministic metrics into natural language server-side ("depth on 8/12 reps;
knees caved on the last three — likely fatigue"). No latency constraint, no
hallucination risk in the safety-critical path.

**Login is optional; never gate the core loop.** Guest by default (Room history
needs no identity). Signing in unlocks sync, multi-device and the leaderboard —
which needs a display name anyway, so it's an honest incentive. Decide the
**anonymous→account migration path before writing sync**. Play requires in-app
*and* web account deletion if account creation exists.

## What makes it a product

Real-time form correction alone is a *feature*. The product is: **your phone
counts your reps, logs your workouts, scores your form, and shows you the rep you
got wrong.** Lead with rep counting and auto-logging — that's the hook that
doesn't decay. Form checking makes someone *choose* RepLens; auto-logging makes
them *keep* it.

Three risks to design against:

1. **Setup friction is the biggest threat** — clear space, prop phone, check
   framing, walk back: a ~60 s tax before a 3 min workout. Evidence: the first
   three test recordings were unusable because of phone placement, made by the
   most motivated user this app will ever have. Needs audio-guided framing (you
   can't read the screen from 3 m), a "you're good" chime, remembered setups, and
   ultra-wide for small rooms — not just a pre-rep check.
2. **The screen is invisible during a set.** **Audio is the real UX; the skeleton
   is marketing** (and a great first-30-seconds moment). Spend effort accordingly.
3. **Cue novelty decays** — after ~3 sessions the user has learned "go deeper,
   knees out" and the app has nothing new to say.

Answers: **a form score that trends** ("average depth 88%, up from 81%"),
**structured sets** with rest timers so a session is something you *complete*, a
**post-workout summary** as the closing moment, and **replaying the worst rep as a
skeleton animation** — store the landmark stream, not video: kilobytes, no privacy
problem, impossible for a mirror to do.

Completeness is **not** more exercises (scope guard: 2–3). It's the app *around*
the camera: onboarding, setup guidance, history worth browsing, stats worth
opening, sensible empty states, cues that feel like a coach.

## Squat specifics

### Convention trap — read before touching any threshold

Literature reports **knee flexion** (0° = straight leg); `angleDegrees(hip, knee,
ankle)` returns the **interior angle** (180° = straight leg).
`interior = 180 − flexion`. They coincide at 90° — the one value everyone quotes
for parallel — so the mistake is invisible exactly where you'd check it.

| depth | flexion (literature) | **interior (ours)** |
|---|---|---|
| standing | 0° | 180° |
| mini squat | 40–50° | 130–140° |
| **parallel** (hip crease level with top of patella) | ~90° | ~90° |
| below parallel (IPF) | ~120° | ~60° |
| deep / ATG | 110–130° | 70–50° |

### Counting and depth-scoring are separate questions

One threshold forces a bad trade: at true parallel most casual reps don't count
(app looks broken); lenient and quarter-squats count. So `bottomEnter` only means
"this was a rep attempt" and is deliberately forgiving, while depth *quality* is
graded from `Rep.deepestAngle` and never affects the count. Ten shallow reps read
as "10 reps, depth 42%", not "0 reps".

```
standingExit    160°   descent under way
bottomEnter     115°   counts as a rep — deliberately lenient
bottomExit      125°   rising out of the bottom
standingEnter   168°   back to standing; the rep is counted here
goodDepthAngle   95°   scoring only, never affects counting (~parallel)
```

Research-informed starting points that **turned out to need no tuning** — all
eight fixtures pass unchanged (2026-08-08). Hysteresis bands (8° and 10°) must
stay wider than post-smoothing noise, including the ~10 frames after a descent
while the One Euro cutoff winds down.

The fixtures also validate the boundary from both sides: the descending sweep
counts its 113° rep and rejects its 128° one, and ten deliberately marginal reps
at 124–141° produce zero rather than an occasional phantom.

**A fixed threshold is still wrong for somebody.** The author's real reps land at
38–98°, clearing 115° by 20–80°, so it is very forgiving *for him*. Someone whose
comfortable deepest is 130° — knee issues, poor ankle dorsiflexion, age — gets
zero reps and an app that is simply broken. Recorded evidence that human ground
truth is itself ambiguous here: the `tiny` clip (140–145°, intended as non-reps)
overlaps `borderline` (124–141°, intended as reps), so no fixed value satisfies
both and raising it only trades a consistent "no" for an inconsistent "sometimes".

**The fix is per-user calibration, and the shape matters:**

- Derive from range, not a constant: `bottomEnter ≈ personalDeepest + 25°`
  reproduces 115° for this author, and 155° for someone who stops at 130°. **Cap
  it** — calibrating on knee twitches must not teach the app that knee twitches
  are reps, or coaching becomes impossible.
- **Reactive, not upfront.** A mandatory calibration flow is exactly the setup
  friction that is this product's biggest risk — a tax on everyone to help a
  minority. The failure is detectable instead: the state machine reaches
  `DESCENDING` and never `BOTTOM`. Offer calibration only to the people it fails.
- **Count relatively, coach absolutely.** If the score adapts too, nobody is ever
  told their depth is objectively shallow and the coaching quietly disappears.
  "12 reps" against your range; "depth 62% of parallel" against the standard.

Needs somewhere to store a baseline, so it lands with history (milestone 3).

### Two layers

`BodyPose -> SquatSignals` (per-leg knee angles gated on `inFrameLikelihood`
across all three joints, averaged when both survive, `null` when neither) then
`Float? -> SquatRepCounter`. The state machine never sees a pose, so a test is a
readable list of angles rather than eight hand-built skeletons. `SquatSignals`
doubles as the CSV fixture row format. Average rather than min/max: a large
left/right disagreement is far more often measurement error than real asymmetry,
and averaging cancels error where min/max amplify it.

### Reference frames — which rules need true vertical

| signal | measured against | needs true vertical? |
|---|---|---|
| depth | interior knee angle | no |
| knee valgus | the hip→ankle line | no |
| left/right asymmetry | the other leg | no |
| **forward lean** | **vertical** | **yes** |
| **heel lift** | **the y-axis** | **yes** |

Three of five are reference-free, including everything rep counting depends on.
Forward lean can't be expressed without gravity, so those two rules must be gated
on the setup check rather than trusted unconditionally. What breaks a vertical
reference is camera **roll**, which a propped phone rarely has; the Milestone 1
tilt was **pitch**, which distorts perspective and degrades *every* measurement.
Interior angles win for the simpler reason that they need no reference frame at
all.

Depth uses the interior knee angle, **not** femur inclination from vertical, even
though the latter states the parallel standard directly. Compute femur
inclination into `SquatSignals` anyway for scoring; never let it decide the count.

*If roll ever bites:* during `STANDING` the shoulder→hip segment should be
vertical, so whatever it reads is the camera roll — subtract it for the session.
Conflates posture with tilt, but better than assuming image-vertical is true
vertical, and the state machine says exactly when the user is standing.

### Form-fault thresholds need later research

Much of the common advice is folklore sports science has walked back ("knees must
not pass the toes" is normal for many people; forward lean is heavily
proportion-dependent). Encoding folklore means confidently nagging people about
non-faults, which is worse than silence. Every threshold will be tuned against
**one body** (the author's).

### Smoothing behaviors to remember

`minCutoff = 1`, `beta = 0.5` are the One Euro paper's defaults, **untuned**. Tune
`minCutoff` first with `beta = 0` until rest is clean, then raise `beta` until
fast reps stop lagging. Two behaviors seen in simulation: **noise inflates the
resting cutoff** (jitter looks like movement and `beta` multiplies it), and **the
cutoff decays slowly after motion stops** — for ~10 frames after a descent there
is less smoothing than at rest, landing exactly at the bottom where depth is read.

`PoseSmoother` filters in **units of frame width, not pixels**, so `beta` means the
same thing at any analysis resolution; both axes divide by width so smoothing
stays isotropic. A gap over 500 ms resets, so stepping out of frame and back
doesn't drag the skeleton across the screen.

**Kalman was evaluated and rejected.** A constant-velocity model assumes motion
continues, but a rep is a sequence of direction reversals — so it's wrong exactly
where the counter cares. Simulated: ~10° overshoot past the bottom, mean error
3.6° vs 0.85° for One Euro. Structural, not mistuning (lower `Q` smooths but
overshoots more; higher `Q` converges on the raw signal), and a steady-state CV
filter is an alpha-beta filter that doesn't adapt to speed at all. Where Kalman
*would* win: **missing measurements** — it coasts while a landmark is occluded,
which One Euro cannot do, and side-view footage occludes far-side limbs by
definition. If `inFrameLikelihood` gating leaves gaps, try holding the last value
with decaying confidence first.

## Testing & CI

Good coverage is an explicit goal. **Unit tests carry the weight** — the
interesting logic lives in `replens.jvm.library` modules and runs on plain JUnit
in milliseconds.

**Test the derived signal, not the landmarks.** A fixture of 900 frames × 33
landmarks is unreadable, so nobody can tell whether a failure is real. A fixture
is a time series of `timestamp, kneeAngle, …` — a few hundred rows you can plot
and see five valleys in.

Three layers: **synthetic sequences** for "is the logic correct?" (clean rep, half
rep, pause at the bottom, jitter on the threshold — each encodes one decided
rule); **real recordings** for "does it survive noise?" (one assertion per file:
*this contains exactly 5 reps*); and **a way to see it** for "why is it wrong?"
(dump the angle curve with transitions marked).

**Python MediaPipe was rejected** — fixtures exist mostly to *tune thresholds*, and
thresholds tuned against a different model don't transfer. (Harmless if fixtures
were only for logic regression.)

**Fixtures are generated by `:core:pose`'s `FixtureGenerator`** — a tool, not a
test, run by hand against recorded clips. Frames are decoded on the host by
`tools/extract-fixture-frames.sh` rather than on the device, because Android's
`MediaMetadataRetriever` is a thumbnail API that returns ~4 fps out of 30 on this
footage. Model and frame size match production deliberately: a CSV from another
configuration describes an app we don't ship. **Runbook and the five non-obvious
traps: `client/core/pose/README.md`.**

**Fixtures are raw, unsmoothed angles** — production runs `PoseSmoother` first.
That gap turned out not to matter for counting (all eight fixtures pass), which is
itself the finding: the landmark signal is clean enough that smoothing is not
load-bearing for threshold crossings. It will still matter for depth *scoring*,
where `deepestAngle` is a single sample rather than a crossing.

**Why fixtures matter: you cannot do the same squat twice.** Tuning by rebuild →
deploy → do five squats → squint is minutes per iteration and the input changes
every time. Against a fixture it's a 50 ms test run.

**Widen the CSV when they are next regenerated** — the current columns are
`SquatSignals` only, so nothing about *framing* is testable against real footage:
no torso fraction, no landmark confidence, no frame size. Add `torsoFraction`
plus per-leg minimum likelihood and an in-bounds flag, and `squats_4_walk_in_out`
becomes a `SetupCheck` regression fixture — it already contains the walk out and
the walk back.

Store **inputs, not the verdict**. A `setupCheck=TOO_CLOSE` column would assert
the generator against itself; thresholds have to stay parameters the test
supplies. That still leaves the bounds-and-confidence *extraction* untested
against real data, exactly as `depthAngle` is trusted rather than re-derived
today — the fixtures test decisions, not extraction.

**Not worth a regeneration on its own.** The decision logic already has synthetic
tests, and the thresholds were measured more cheaply by logging `torsoFraction`
through one live session. What only footage can show is the **sequence** — where
the verdict flips across a continuous take, and whether it chatters near
`SetupCheck.MAX_TORSO_FRACTION`. The trigger is that threshold misfiring on
somebody, or any other reason to regenerate.

**Rep counting is objectively testable; form quality is not.** You know you did 5
reps; whether rep 3 was "too shallow" depends on whose standard. Unit-test
counting hard; treat form rules as tuned-by-eye.

**Compose Preview Screenshot Testing** — to try once there's UI worth pinning
(`:core:designsystem` components, the workout screen against fixed states). Still
alpha; host-side, so CI-friendly unlike instrumented tests. Gotchas: renaming a
`@PreviewTest` orphans its reference image, it's memory-hungry, and reference PNGs
are committed. If it works out, enable it in a convention plugin.

**GitHub Actions** — `assembleDebug`, `assembleRelease` (catches R8 breakage),
unit tests, and screenshot validation once it exists. Cache the Gradle
distribution and build cache.

## Current status (2026-08-11)

- **Milestone 1 (camera + overlay): done**, validated on device.
- **Milestone 2 steps 1–4: done**, validated on device — 8 reps performed, 8
  counted, clean phase transitions, no phantom reps from walking to/from the
  phone. Hilt is wired throughout.
- **Zoom stops and front/back flip: done**, validated on device 2026-08-08
  (`squats_camera_flip.mp4`): 7 performed, 7 counted — 4 on the back camera, 3 on
  the front. Across the flip the zoom range updated 0.5 → 0.9 and the selection
  reset to 1x, the rep count survived, mirroring was correct on both lenses, and
  carrying the phone produced no phantom reps. 89 unit tests.
- The flip **rebinds the same use cases in place** rather than restarting the
  flow, so the ML Kit detector is never rebuilt and its model never reloads. A
  lens change is CameraX's business alone.
- **What that does not prove: the thresholds.** Every rep in both clips was deep
  and clean, nowhere near the 115° boundary, and the smoothing constants are still
  the paper's defaults. Shallow reps are what will expose them.
- **Camera configuration: settled** (below) — 640x480, 4:3, accurate model.
- **Fixtures: done** (2026-08-08). Eight clips recorded, generated and committed;
  `SquatFixtureTest` asserts one rep count per file and **all pass with the
  thresholds unchanged** — deep 5, parallel 5, tiny 0, paused 3, double-dip 1,
  walk-in-out 4, borderline 0, and a descending sweep counting 5 at 51/73/87/97/113°.
  What that establishes is narrow: one body, one room, one camera. It says the
  numbers are not obviously wrong, not that they generalize.
- **Session start/stop: done** (2026-08-09). `Idle → Waiting → CountingIn →
  Active → Finished`, and only `Active` feeds the counter. **`Waiting` has no
  duration** — it ends when the setup check sees you in position, so the walk out
  to your spot can never eat a countdown that was only ever a guess at how long
  the walk takes. Leaving position mid-count returns to waiting.
- **Session machine extracted** (2026-08-09). `SetSession` in `:core:exercise` is
  frame-driven off the timestamps frames already carry, the way `SquatRepCounter`
  is — so it needs no coroutines, no Hilt and no fake, and the ViewModel is left
  pumping frames in and copying state out. 17 tests.
  Two bugs it made visible, both fixed and pinned: readiness was counted in
  *frames* (`READY_FRAMES = 14`, "~0.5 s at ~27 fps"), which is a full second at
  15 fps — it is a `Duration` now, asserted at three frame rates; and nothing
  noticed a gap in the stream, so a stall between two `READY` frames read as
  sustained readiness.
  **Frame-driven beats clock-driven for a reason worth keeping:** `viewModelScope`
  is not tied to UI visibility but the camera is, so a `delay` countdown kept
  running while the app was backgrounded and CameraX had unbound — reaching zero
  and starting a set on a stream delivering nothing.
- **Voice: done and validated on device 2026-08-10/11.** `:core:audio` and
  `:core:ui` both have their first caller. The app now speaks setup problems, the
  count-in, "Go", **every rep**, and the set summary. Details and the rules that
  came out of it are under *Voice feedback*. Verified by ear: ducking dips the
  music rather than stopping it; backgrounding the app silences cues instantly
  (only the in-flight utterance finishes, which is `TextToSpeech` being an OS
  service and is left alone); and at normal rep cadence nothing truncates.
  **A set is now startable and finishable by ear alone**, which was the point —
  the screen is unreadable from three meters.
- **Next:** form rules — the two with evidence already behind them, shallow rep
  (`Rep.isAtDepth`) and abandoned descent (`AbandonedDescent`). Both fire *after*
  a rep, so neither needs a new threshold or a latency budget. **Priority
  arbitration lands with them, not before**: an arbitrator over one cue is dead
  code, but a form cue and the rep number collide on the same frame by
  construction. Valgus, forward lean and heel lift stay parked — they need
  research and tuning against footage, and two of them need the vertical
  reference gated on the setup check. 166 unit tests.
- **Deferred until they have a job to do:** `WorkoutEvent` (nothing one-shot yet)
  and Navigation 3 (one screen, nothing to navigate to — it lands with
  the post-workout summary). The **set summary card is not that screen**: a card is
  the *set* boundary, where you are still three meters away and want a number and
  "go again"; the screen is the *workout* boundary, where you have picked the phone
  up. Keep both.
- **The rep counter counted garbage; the framing gate fixes half of it.**
  Observed 2026-08-08: picking the phone up counted **a rep off a face**. An
  earlier note claimed the `standingEnter` requirement prevented this; it does not.
  At 20 cm ML Kit does not report "no body" — it **hallucinates a full skeleton**
  with `inFrameLikelihood` above our 0.5 gate, and as the phone moves those
  invented joints sweep the angle through 168 → 115 → 168. One sweep is one rep.
  The state machine is behaving correctly on fictional input, so **no confidence
  threshold can catch this** — the model is confident.
  Apparent size can. `Framing` (`:core:exercise`) rejects a frame whose torso
  spans too much of the frame height, and a rejected frame yields a **null depth
  angle** rather than a special case — which is the "no reading" path
  `maxMissingFrames` already handles, so it needed no new state machine.
- **The model fits a skeleton to anything human-shaped, so every gate is
  geometric — because geometry is all there is to gate on.** Observed 2026-08-09
  (`microphone_squat.mp4`): a mic stand on a desk — a vertical pole with a splayed
  base — was detected as a person and, after repeated attempts, got as far as
  `Active`. It counted nothing, because a stand cannot sweep 168 → 115 → 168.
  **There is no confidence to tune.** `inFrameLikelihood` answers "is this landmark
  inside the picture", not "is this a person" or even "is this a knee", and ML Kit
  exposes nothing at the `Pose` level — no detection score at all. Raising the
  threshold changes nothing; the model is already certain about the wrong question.
  What did make it hard was **dropout**: the detector's grip on a non-human
  flickers, so `Waiting` and the count-in kept restarting and it took hand-aimed
  framing to hold `READY` for the 3.5 s that `settleFor` + `countIn` require. That
  is an accident rather than a discriminator and should not be relied on — but it
  is `SetSession`'s two guards earning their keep, since a single lucky frame would
  otherwise have started a set.
  **Deliberately not fixed.** Proportion checks (shoulder width against torso
  height) would catch it and would also reject anyone filmed side-on, which is the
  framing this app recommends. "Reject anything too still" punishes the patient
  user waiting for a countdown. The realistic version — locking onto a chair while
  you walk to your spot — self-corrects, because you then walk into frame and ML
  Kit tracks the human.
- **One threshold could not do both jobs, so there are two checks.** The framing
  gate above stays lenient because rejecting a real frame mid-set silently drops a
  rep. That leaves the opposite failure, observed 2026-08-09: leaning over the
  phone at ~1–1.5 m reads a torso fraction of 0.29–0.34 — *under* the framing
  threshold — so a set counted in while the skeleton below the knees was a tangle.
  `SetupCheck` (`:core:exercise`) answers "may a set start?" and takes the
  opposite bet: refusing costs a message, starting on invented legs costs a whole
  set measured off nothing. It is tighter on size (0.32) and additionally demands
  **one genuinely observed leg** — hip, knee and ankle above the confidence gate
  *and inside the image bounds*, because ML Kit reports coordinates past the edge,
  which is what feet cut off by the bottom of the frame look like numerically.
  That bounds test is a fact rather than a tuned number, and it is the arm that
  actually catches leaning in.
  **No `TOO_FAR` arm**: a real failure mode, but no too-far case has been recorded
  and a guessed lower bound would block real users — the exact mistake
  `MAX_TORSO_FRACTION` was widened from 0.30 to 0.40 to avoid.
  **`SetupCheck.MAX_TORSO_FRACTION = 0.32` turns out to be almost unreachable**,
  and it is the leg check that decides essentially every case. Verified on device
  2026-08-11, and it inverts an earlier note here calling 0.32 "the number most
  likely to be wrong in the annoying direction". The geometry: the torso is ~29%
  of a person's height, so a body that just fits head to toe already reads ~0.29
  — *under* 0.32. By the time your ankles are inside the frame the size arm has
  therefore already passed, and when you are closer than that your ankles are out
  of frame, which the leg arm catches anyway. The two arms are not independent
  tests; they are nearly the same geometric condition, and the stricter one needs
  no threshold at all.
  Good news, since the arm doing the work is a fact rather than a tuned guess. It
  is **not dead code**: a phone propped low and angled up gives feet in frame
  *and* a large torso, and then 0.32 is the backstop that fires.
  Also retired: the worry that deep reps push feet out of frame and so the check
  passes a setup that breaks mid-set. **Feet do not move during a squat** — the
  hips and head come down, which is why torso fraction falls rather than rises.
  A `SetupCheck` that passes while standing therefore holds for the whole rep.
- **Known issue from the validation footage:** arms held forward occlude the legs
  at the bottom of a rep, which front-on framing makes worst — leg landmarks
  start being inferred, and that will corrupt heel-lift and shin rules. This is
  confidence degrading *within* a rep, so `SetupCheck` cannot see it: the check
  runs while you are standing, and nothing gates on landmark quality during
  `Active`. The other half of this note — feet leaving the bottom edge — is
  retired above; feet do not move during a squat, and marginal framing is now
  refused before the set starts. The back camera at 0.5x remains the answer for
  small rooms.
- **`WorkoutViewModel` still has no tests**, but far less now depends on that.
  What remains in it is camera facing resolution, the resolved-once rule and the
  flip guard — real, but small. Covering them needs a fake, and
  `PoseCameraDataSource` is a concrete class: **extracting an interface is still
  worth doing** (a data source in name, this feature's whole outside world in
  role, which is what "what every ViewModel test fakes" means) but it is no longer
  the prerequisite for testing the interesting logic.
- **The completed `Rep`s are collected now** (2026-08-09), in a `reps` list the
  ViewModel clears on `startSet`, so `deepestAngle` and the descent/ascent timings
  survive the set. `repsAtDepth` is the first thing built on them and is spoken in
  the set summary. This was the prerequisite for "depth 88%, up from 81%"; what is
  still missing is somewhere to *persist* them, which arrives with history.
  `repsAtDepth` is recomputed from the list inside the same `update` that moves
  `repCount`, rather than incremented: the guard above it fires on every frame
  that completes a rep, so it cannot go stale, and a derived value cannot drift
  the way a counter maintained in three places can.
- **`:feature:workout` exposes exactly `WorkoutRoot(modifier)`**; the ViewModel,
  state and actions are `internal`, so `:core:pose` and `:core:exercise` are
  `implementation`. Root itself becomes internal when `navigation/` exists — a
  one-keyword change. The `RepPhase` text is a debug affordance; removing the
  `Text` alone won't cut recompositions, since `phase` living in `WorkoutState` is
  what drives them. The real cleanup is dropping it from state once cues are
  events.
- Findings from Milestone 1 footage still worth honoring: 45° is the best angle
  (depth + both knees resolved); pure side view infers far-side limbs, so use
  near-side joints only; bad framing makes leg landmarks hallucinate. **The
  "inference warms the phone" note is withdrawn** — it came from one early session
  where the phone was also charging, and four measurement runs on 2026-08-08 did
  not reproduce it.
- Test footage lives outside the repo in `~/replens-recordings/`.
- Parked for the library convention plugin when they earn their keep:
  `resourcePrefix`, default `testInstrumentationRunner`, `animationsDisabled`,
  `disableUnnecessaryAndroidTests`.

### Next up: camera selection vs capabilities

Two groups, two natures. **Selection** is what the camera is doing, chosen by the
user. **Capabilities** are what it could do, discovered from the device. Conflating
them is why `cameraFacing` currently defaults to `FRONT` without knowing a front
lens exists — on a device without one, `requireLensFacing` throws and the app
**crashes at startup**, not on the flip. The manifest already declares
`android.hardware.camera.any` (deliberately not `camera.front`, since the
recommended setup is a back-camera position), so the manifest and the code
disagree today.

`:core:pose` publishes one nullable object instead of a growing row of flows:

```kotlin
val surfaceRequests: StateFlow<SurfaceRequest?>
val options: StateFlow<CameraOptions?>          // null: nothing bound yet

/** What the user can choose right now. */
data class CameraOptions(
    val facings: Set<CameraFacing>,   // device-level, from provider.availableCameraInfos
    val zoomRange: ZoomRange? = null, // lens-level: null until bound, changes on flip
)
```

The inner null is load-bearing, not sloppiness: facings must be published *before*
the first bind for the handshake to work, and the zoom range only exists *after*
it. The nesting states the real discovery order — device capabilities first, lens
capabilities second — and a non-null `zoomRange` would deadlock.

`WorkoutState` holds only the selection — `cameraFacing: CameraFacing?`,
`zoomRatio: Float = 1f` — plus the options it renders from. The "prefer front"
rule is a **product constant, so it lives in code**, not in state:
`if (FRONT in options.facings) FRONT else BACK`.

The ordering is a handshake, not a bootstrap problem: `poseFrames` resolves the
provider and publishes `options.facings` **before** it waits on a facing, so the
ViewModel can resolve and reply. Nothing binds until the lens is known to exist,
which removes the crash by construction rather than by catching.

Rejected on the way, worth not re-deriving:

- **A `preferredFacing` / `activeFacing` split.** Only needed if we bind before
  knowing what exists — then the request can name an absent lens and `mirrored`
  reads the wrong one. Resolving first collapses both into one always-valid value.
- **A `CameraFilter` expressing "prefer front".** It works — `CameraFilter`'s
  contract is explicit that *"the CameraInfo that has lower index in the list has
  higher priority"* and `bindToLifecycle` returns a `Camera` whose
  `cameraInfo.lensFacing` reports what was chosen. But with a resolved facing
  `requireLensFacing` can't throw, so it carries nothing. Keep it in mind as
  belt-and-braces only.
- **`activeZoomRatio` vs requested.** Same shape as facing, two orders of
  magnitude lower stakes: `1f` is valid everywhere and every other value comes
  from the device's own reported range, so divergence is a pill highlighted for
  ~200 ms after a flip. `zoomState` already carries the current ratio, so it is
  one field whenever we want it. **Trigger to add it: continuous or pinch zoom**,
  where the camera ramps toward a target and the UI must follow the real value.

### Camera configuration — settled 2026-08-08

**`ImageAnalysis` is pinned to 640x480, 4:3, accurate model, `STREAM_MODE`.** The
resolution is set explicitly even though CameraX's default matches it: fixtures
and every threshold tuned against them assume this exact frame, so a future
default must not move it silently. 4:3 is not negotiable — the preview is 4:3 and
`PoseOverlay` maps analysis coordinates onto it, so a different aspect would frame
a different scene and misplace the skeleton.

Measured on the Pixel 10 Pro XL, back camera, standing still:

| | fps | inference | raw angle noise |
|---|---|---|---|
| **640x480** | 26–29 | 33–40 ms | **0.2–1.0°** |
| 1280x960 | 12–22 | 45–85 ms | not measured — moot |

1280x960 costs ~10 fps to fix jitter that isn't there: 0.2–1.0° against hysteresis
bands of 8° and 10°. Rejected. Going *lower* than 640x480 would buy almost nothing
either — the landmark model's input is a fixed ~256px crop, so source resolution
only moves the detector pass and buffer transfer, which is why quadrupling the
pixels cost 20 ms rather than 4x.

**We are inference-bound, not resolution-bound**, and `fps ≈ 1000/inference` holds
in every sample. `KEEP_ONLY_LATEST` won't deliver the next frame until we
`close()`, and we can't close until detection finishes because ML Kit still holds
the `mediaImage`. So there is no pipelining and the frame rate *is* the inference
rate. Every future "can we afford X" question here is a question about the model.

`~27 fps is fine` — `PoseSmoother` and `SquatRepCounter` are timestamp-driven, not
frame-count-driven. The lever if a weaker device can't sustain ~15 fps is the
**base model**, per device, not a resolution drop for everyone.

**0.5x costs nothing measurable.** 0.5x read sd 0.2–0.5, 1x read 0.4–1.3 — the
difference is within stance variation. So the wide framing is free, and the
feet-in-frame problem can be solved with it. Note this contradicts ML Kit's
guidance that the subject should be ≥256px (at 0.5x the body is ~210px tall) —
the measurement wins, but that line is the first place to look if depth proves
noisy.

Zoom ranges: front `0.8958334`–10.0, back `0.5`–10.0. The front minimum is crop
geometry, not a lens; the back's 0.5 is a real optical ultra-wide, so `2x` is
probably a genuine telephoto there and a quality-costing digital crop on the front.

Dead ends, so they are not re-run: `setPreferredHardwareConfigs(CPU_GPU, CPU)`
changed nothing (22–25 fps against 26–29 — within variance), and the Pixel 3XL
figures in Google's table are not comparable, because our number is an end-to-end
round trip converted to a rate while a benchmark can pipeline.

**Deferred to the fixtures:** noise at squat depth. Standing straight is the easy
case, and a human cannot hold 115° still for long enough to measure it well. The
fixture CSVs give the angle time series for real reps, so noise at any angle falls
out for free — and the same footage through both models is the controlled
comparison a live camera can never provide.

**The fixture generator must replicate this config** — downscale to 640x480, use
the accurate model. Feeding ML Kit full-resolution video frames would produce CSVs
describing a pipeline we don't ship, and every threshold tuned against them would
be optimistic.

### Framing — measured 2026-08-08

`torsoFraction` = hip-to-shoulder over frame height. One continuous take on the
Pixel 10 Pro XL — phone in hand, walk out, squat, walk back:

| | torso fraction |
|---|---|
| phone in hand at ~20 cm | 0.75–0.81 |
| walking, either direction | 0.25–0.55, sparse |
| **standing and squatting at 2–3 m** (231 frames) | **0.177–0.239, mean 0.193** |

Strongly bimodal, which is what makes a single threshold viable at all.

`MAX_TORSO_FRACTION = 0.40` sits in the corridor rather than just above the
measured maximum, because **the measured maximum is one room**. The torso is
~29% of a person's height, so a body filling the frame head to toe — legitimate
tight framing in a small room — reads ~0.29. Rejecting that user means zero reps
and an app that looks broken; accepting a few frames of someone walking costs
nothing, because the counter needs a full 168 → 115 → 168 sweep.

**The fraction falls during a rep**, never rises: the spine is rigid, so leaning
forward only foreshortens its projection (0.177 against a 0.193 mean). The gate
cannot misfire mid-squat.

## Roadmap

1. **Camera + skeleton overlay** — done.
2. **The squat** — angles, smoothing, rep state machine and the whole voice
   channel done; **form heuristics are what remains**.
3. **Local persistence & app shell** — Room history, Nav 3 flows, stats screen.
4. **Backend & sync** — Ktor API (auth or device-ID first), leaderboard.
5. **Second/third exercise + Play release** — push-ups, bicep curls; privacy
   policy (camera!), data-safety form, signing, crash reporting.

Backlog: remembering the camera choice and zoom across launches (needs DataStore,
which the setup/settings work will bring anyway); per-category cue switches (same
DataStore); a `PoseCameraDataSource` interface so the ViewModel's camera logic can
be faked; widening the fixture CSVs so framing is testable against real footage;
`TtsSpeaker` has no `shutdown()` and holds its engine for the life of the process,
which is defensible for a `@Singleton` but is a choice rather than an oversight.

Scope guard: **2–3 exercises max, done well.** Form heuristics are the hard part,
not ML Kit — landmarks jitter (smoothing + hysteresis are non-negotiable) and
degrade with bad lighting, clothing and angles, so UX must guide phone placement.

## Conventions

- Conventional commits, matching existing history: `chore:`, `docs:`,
  `fix(client):`, …
- The author makes all commits; propose the message, never run `git commit`.
- **American spelling everywhere, comments included** — `color`, not `colour`;
  `gray`, not `grey`. The APIs are spelled that way, so anything else means the
  same word appears twice in one file.
- **Two or more named arguments: one per line**, with a trailing comma —
  `Foo(a = 1, b = 2)` becomes four lines. Applies to function and composable
  calls; modifier arguments (`.padding(horizontal = …, vertical = …)`) and
  annotations stay inline.
- **Comments are for what the code can't say.** A comment that restates the
  signature (`/** What the user did. */`, `/** Zoom stops. */`) is noise —
  delete it. Worth writing: why a number is that number, why an ordering or a
  thread matters, and what silently breaks if someone "tidies" the code. If the
  answer is in the names, say nothing.
