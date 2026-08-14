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

**This file is the rules: what to do, what to avoid, and what has already been
ruled out.** Two companions carry what it used to:

- [`docs/status.md`](docs/status.md) — milestones, device-validation results, the
  roadmap, the backlog. The file that changes every session; keeping it here made
  these rules churn for no reason.
- [`docs/decisions.md`](docs/decisions.md) — the arguments, measurements and
  rejected options behind the rules below. Read it to *reopen* a decision, not to
  follow one.

Every conclusion here carries what it ruled out, so nothing needs re-deriving from
the record. **When you add a decision, put the rule here and the argument there** —
that split is the only thing keeping this file readable.

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

**A root `settings.gradle.kts` with a `:shared` module was reconsidered and
rejected** — it is right for a web client, which redeploys *with* its server, and
wrong for mobile, which cannot be force-updated. Two consequences that are coding
rules rather than history: a shared DTO makes the compiler lie about
compatibility, and **enums are DTOs** — the server adding `PULL_UP` must not throw
in an old client, so every enum read from the wire or the database needs a
tolerant fallback arm. [The full trade](docs/decisions.md#no-shared-module).

## Identifiers

- Android `applicationId`: `com.replens.app` — **permanent on Play after first
  publish; never `com.example.*`**
- Server: group `com.replens`, artifact `server`
- Domain `replens.app` — owned (Cloudflare, expires 2027-08-05). Future API host
  `api.replens.app`; `.app` is HSTS-preloaded, so HTTPS is mandatory.

## Server tech stack

Ktor + kotlinx.serialization, Exposed, Flyway, Postgres, HikariCP. **Spring Boot
was rejected** before a line was written: its value is amortizing complexity
across a team and many modules, and ~12–15 endpoints written by one person is
exactly the regime where only the tax is paid. [Why](docs/decisions.md#ktor-not-spring-boot).

- **No Hilt — it is Android-only.** DI is constructor wiring in
  `Application.module()`; Koin only if that stops scaling.
- **Auth is a `jwt {}` block**, not a framework. Verifying a Google ID token means
  fetching the JWKS and checking the signature plus `iss`/`aud`/`exp` — roughly 40
  lines against stable, well-documented behavior. Losing Spring Security is the
  one real cost of the choice above, and it was priced before choosing.

## Client tech stack

- Kotlin, Jetpack Compose (pure, no Views), Jetpack Navigation 3
- Hilt (DI), Room (local history), kotlinx.serialization
- CameraX (`ImageAnalysis`) + ML Kit Pose Detection
- **Ktor Client**, not Retrofit. The margin was small and both sit on OkHttp, so
  this is settled rather than obvious. Two things to actually *use* when
  `:core:network` is built: Ktor's `Auth` plugin
  (`bearer { loadTokens; refreshTokens }`) for token refresh, because it retries
  the original request after a 401 and **serializes concurrent refreshes** — five
  requests 401-ing at once is a classic thing to ship broken; and `body<T>()` for
  error payloads, which does not care about the status, unlike Retrofit's separate
  `errorBody()` path. Budget ~30 lines for a `safeApiCall`-style wrapper.
  [Why](docs/decisions.md#ktor-client-not-retrofit).

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
                                            CueEngine (what to say), CueAnnouncer
                                            (whether to say it again) and
                                            CameraSelection (which lens to open
                                            on, which zoom stops to offer)
                    …workout.ui.mapper/     SessionCue + FormCue: domain state ->
                                            UiText, drawn and spoken from one source
                    …workout.ui.model/      SpokenCue
                    …workout.ui.components/ PoseOverlay, RepCounter,
                                            SessionControls, ZoomControl
                    63 tests.
:core:pose          PoseCameraDataSource: CameraX + ML Kit behind Flow<PoseFrame>
                    + surfaceRequests. PoseMapper is internal — the ML Kit boundary.
:core:audio         Speaker + TtsSpeaker: one engine, locale negotiation, audio
                    focus. Silence is a legal outcome, never an error.
:core:ui            UiText and its two resolvers. 8 tests. ObserveAsEvents lands
                    when something is actually one-shot.
:core:designsystem  RepLensTheme + the app's Compose gateway (below).
                      …component.button/ Primary, OverlayPrimary,
                                         OverlaySecondary, OverlayIcon
:core:model         Landmark, LandmarkType, BodyPose, PoseFrame; Rep, RepPhase,
                    RepUpdate, AbandonedDescent; Exercise, ExerciseSet, Workout.
                    Pure Kotlin, no dependencies at all.
:core:posemath      Point, joint angles, torso size, normalized distances, line
                    deviation; OneEuroFilter + PoseSmoother. Pure Kotlin,
                    domain-free (no thresholds, no exercise names). 55 tests.
:core:exercise      Exercise knowledge and thresholds. Pure Kotlin. 87 tests.
                      …exercise/       Framing, FormFault, SetupCheck,
                                       SessionState, SetSession
                      …exercise.squat/ SquatSignals, SquatRepCounter, SquatRepConfig
:core:database      Room 3: entities, WorkoutDao, ReplensDatabase (internal).
                    Tested on the JVM against BundledSQLiteDriver. 12 tests.
                      …dao/  …di/  …entity/
:core:data          WorkoutRepository + impl, the entity mappers, WORKOUT_GAP.
                    The only module speaking both vocabularies. 11 tests.
                      …mapper/  …di/
```

Planned, not built: `:core:network`, `:feature:{history,stats,leaderboard}`.

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
- **Deferred until they have a job to do:** `WorkoutEvent` (nothing one-shot yet)
  and Navigation 3 (one screen, nothing to navigate to — it lands with
  the post-workout summary). The **set summary card is not that screen**: a card is
  the *set* boundary, where you are still three meters away and want a number and
  "go again"; the screen is the *workout* boundary, where you have picked the phone
  up. Keep both.
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

Color and typography have RepLens names and RepLens values; M3 remains a component
library we wrap. **Material's semantic vocabulary was rejected**, not Material: a
`compose-material3` bump can restyle an app that names `surfaceContainerLow`, and
M3's five button variants encode Material's emphasis hierarchy rather than ours.
[The argument and every contrast measurement](docs/decisions.md#design-system).

- **Two tiers.** `Palette` is `internal`, holds only raw values (`Slate6`,
  `Cyan11`) and is **never referenced by UI**. `RepLensColors` maps those onto
  names that mean something. Theming is a remap, not a second palette.
- **Radix Colors** — `slate` + `cyan`. **Keep Radix's numbering**: the number is a
  foreign key into their docs (9 = solid brand, 11 = low-contrast text, 12 =
  high-contrast), so contrast is inherited rather than eyeballed. Gaps just mean
  we have not needed those steps yet.
- **`accent` is cyan, deliberately not green/amber/red** — those three are spoken
  for by form feedback, and a brand color colliding with "your knees are caving"
  makes the one moment color carries meaning meaningless.
- **`accent` inverts with the theme; overlay colors never do.** Anything drawn on
  the camera feed competes with an unknown room, not with our background.
- **`Palette.Dark`/`Light` name the surface a color sits on, not the app's theme**
  — Radix's own meaning. So `LightColors` reaching into `Palette.Dark` for overlay
  colors is correct rather than a slip: a camera feed under a scrim is a dark
  surface permanently.
- **`overlayScrim` tints, `overlaySurface` covers — a scrim is not a fill.** Two
  scrims stack, so panels and buttons over the feed are **opaque**; primary and
  secondary separate **by hue, not lightness**.
- **`on` pairing kept in our names** (`accent`/`onAccent`) — the one genuinely
  valuable part of M3's color system. It makes contrast our job: every pair was
  measured once, and the worst is 4.65:1.
- **No `MaterialTheme` at all.** `RepLensTheme` provides two `CompositionLocal`s
  and nothing else. Un-wrapped M3 components fall back to Material's defaults and
  render visibly wrong, which is how we find them — **the absence *is* the leak
  detector, so do not add a floor "to be safe".**
- **Wrap every M3 component before first use.** `IconButton` was the last leak; it
  read `IconButtonDefaults`, hence `OverlayIconButton`.
- **Typography named for the job, not the size** — `display`, `title`, `body`,
  `label`. `Title28` becomes a lie the moment the size changes or an accessibility
  setting scales it. Four sizes, two weights; `display` sets `tnum` so the rep
  counter does not reflow as it passes 9.
- **Montserrat, subset by `tools/subset-fonts.sh`** to Latin + Polish.

**No spacing, sizing or radius tokens — plain `.dp` at call sites.** Color varies
by theme and type size varies by the user's font scale, so both earn a token
layer; spacing varies by nothing. Semantic scales (`Spacing.md`) and rescaling
wrappers (`.figmaDp`) were both rejected — the second is worse than a naming
problem, since `dp` is a platform guarantee that 48dp is a 48dp touch target.
Keep the **rule** instead: only **4/8/12/16/24/32**. A `private val` for a value
repeated within one file is naming a local constant, not building a token layer.

**Build the structure, populate on demand.** Tokens designed against imaginary
screens are guesses.
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

## Local data model

Three tables. **Table names do not have to match class names**, so the schema
keeps the clean plurals and the Kotlin type pays one extra word — `ExerciseSet`,
because `Set` shadows `kotlin.collections.Set`. Not `WorkoutSet` (reads as *the
whole workout*) and not `RepSet` (falsified by a future exercise that is not
rep-based). [Full argument](docs/decisions.md#the-schema).

```
workouts        id, startedAt, endedAt          ← Workout
                serverId?, updatedAt, isDirty

sets            id, workoutId (FK, indexed, cascade)   ← ExerciseSet / SetEntity
                exercise                        ← String, tolerant read
                startedAt, endedAt
                repCount, repsAtDepth
                abandonedCount, deepestAbandonedAngle?
                serverId?, updatedAt, isDirty

reps            id, setId (FK, indexed, cascade)       ← Rep / RepEntity
                index, deepestAngle
                descentMillis, ascentMillis
```

- **`index` stays on `Rep`, 1-based** — the number a user is told. Removing it was
  tried and reverted: re-deriving on write makes "the list is complete and in
  order" a silent invariant of the mapper, and the round trip stops being lossless.
- **A workout has no "finish" trigger, and must not get one.** The boundary is
  inferred when a set *starts*: attach to the most recent workout if its last set
  ended under **60 minutes** ago, else open a new one. Any rule that waits for a
  press gets the common case — pressing nothing at all — wrong. An explicit Finish
  stays available later as a purely additive flag.
- **Reps are rows, not a blob** — ~6,000 rows a year, queryable, migratable.
- **`repCount` / `repsAtDepth` are denormalized on purpose.** This does not
  contradict derive-don't-store: that rule guards a *live* counter against
  drifting, and **a completed set is immutable**.
- **Abandoned descents are two columns on the set**, not a table and not dropped —
  they are what per-user calibration will read. `deepestAbandonedAngle` is a
  **minimum**: 128° is shallower than 95°.
- **The landmark stream is deliberately not stored.** Rejected on measured size
  (~300 MB/year, not the "kilobytes" an earlier note claimed) and on having no
  reader to validate a format against. `reps` has a stable id, so a `rep_frames`
  table is additive. [Numbers, and the in-memory stepping stone](docs/decisions.md#the-landmark-stream).

### Time types: domain speaks in time, entities speak in the column's primitive

| | domain | entity | column |
|---|---|---|---|
| rep timings | `Duration` | `Long` | INTEGER millis |
| set/workout times | `Instant` | `Long` | INTEGER epoch millis |

**Both are `kotlin.time`** — `Instant` and `Clock` are stdlib and stable on Kotlin
2.4.10 (verified: no opt-in, no warning). `java.time` and `kotlinx-datetime` were
both rejected: `Instant - Instant` yields a `kotlin.time.Duration` directly, which
is exactly where the gap rule lives, and `java.time` would put two `Duration` types
in one module. [Comparison](docs/decisions.md#time-types).

**`Duration` over `Long`** because this project has the scar: `READY_FRAMES = 14`,
*"~0.5 s at ~27 fps"*, was a full second at 15 fps.

**The conversion lives in the mapper, not a Room converter.** The entity *is* the
schema: `descentMillis: Long` says "INTEGER, milliseconds" to anyone who opens the
file. Same instinct as duplicating DTOs — make the boundary felt.

**`descent`/`ascent` split at the deepest frame, never at a threshold crossing.**
Splitting at `bottomEnter` measured a fixed angular window down and everything else
up, so a deeper rep reported a longer `ascent` at identical tempo — depth leaking
into the one metric whose job is tempo. **Known weakness, accepted:** a pause at
the bottom puts the split at a random point inside the pause, because exactly one
frame is lowest. A band-based three-phase version is designed but **not built** —
no reader yet, and until release the schema's whole population is one phone.
[Measurements](docs/decisions.md#rep-timings).

**Nothing reads `descent`/`ascent` yet and they are kept anyway** — 96 KB a year,
produced by a subtraction the counter already makes, and **unrecoverable after the
fact**. That is the same "no reader yet" argument the landmark stream *lost*, four
orders of magnitude cheaper, which is why it comes out the other way.

### Room 3, not Room 2

`androidx.room3:room3-runtime`. Coroutine-first is mandatory (every DAO function is
`suspend` or `Flow`; no Executor support), KSP-only codegen, and `@TypeConverter`
became `@ColumnTypeConverter` — which we need none of. The honest cost is that
search results are Room 2 with different API shapes (`withWriteTransaction`, not
`runInTransaction`; `useReaderConnection`, not `query`). The Gradle plugin is
`androidx.room3` and must be `apply false` in the root build file like every other
third-party plugin. **Commit the schema directory** so migrations are reviewable.

**`:core:database` exports coroutines, not Room.** `room3-runtime` is
`implementation` because the only Room type in a public supertype —
`ReplensDatabase : RoomDatabase` — is **`internal`**, and Room's annotations are
skipped rather than resolved when a class file is read (same mechanism as
`@Composable` in `:core:ui`). `kotlinx-coroutines-core` is `api` because `Flow` is
in DAO signatures, and it was arriving transitively through room-runtime — the
"works only via someone else's transitive graph" trap. `-core`, not `-android`:
nothing here wants a Main dispatcher. `:core:data` declared the same `api` with no
coroutine type in any signature; removed, and it returns when `workouts()` reaches
the repository.

**A DAO is the typed interface to a schema, not application logic**, so a query
with no production caller is not automatically speculative — a table you can insert
into but never list is an incomplete interface. The rule: **keep an unused query
when it is an obvious operation on the table, delete it when it is a narrower
spelling of one that already exists.** `repsFor(setId)` went (a subset of
`repsForWorkout`); `deleteWorkout` stayed and is genuinely test-only, because it is
the only lever the cascade test can pull and `onDelete = CASCADE` needs a migration
to add.

### Where these live

`Workout`, `ExerciseSet`, `Exercise` and `Rep` live in **`:core:model`**.

**The module's charter is the admission test: pure data, no thresholds, no
behavior, no dependencies.** `Landmark` and `Workout` pass; `SetupCheck` fails
(carries `MAX_TORSO_FRACTION`) and `SetSession` fails (behavior). Stated as a rule,
the module cannot drift into meaning "misc types". **Not split** into pose models
and workout models — the halves already meet in two modules, so they would be
declared together nearly everywhere.

**`Workout` and `ExerciseSet` are read models, so `id` is always real.** No
`id: Long = 0` — that is Room's `autoGenerate` sentinel leaking into a type that
must not know Room exists. Instead **recording a set does not go through these
types**: `recordSet`'s parameters are exactly `ExerciseSet`'s fields **minus `id`**,
and that one difference is the whole reason it is a parameter list.

**The repository derives what it is handed the source for, and takes what it is
not.** `repCount` from `reps` (rows, passed anyway); `abandonedCount` and
`deepestAbandonedAngle` passed, because abandoned descents are not rows. The
asymmetry sits exactly where the schema is lossy. `repsAtDepth` is passed for a
different reason: grading depth needs `SquatRepConfig`, the feature's knowledge.

**Primary keys: autoincrement is a hold, not a default.** The question that settles
it — *does the sync API accept client-supplied ids?* — is answered when sync is
**designed**, alongside the anonymous→account path. Yes means client-generated
UUIDs and `serverId` disappears; no means autoincrement was right all along. Not
now, because until release the schema's whole population is one phone, so switching
stays a wipe rather than a migration. [The full trade](docs/decisions.md#primary-keys).

**Measured, because it decides a tempting shortcut:** Room's `autoGenerate` treats
**0** — and only 0 — as not-set. An entity inserted with `id = -1` stores `-1` as
the actual key, and the *second* such insert fails. A `-1` sentinel would not fail
a compile or the first set of a session; it would fail the second.

`:core:database` owns entities, DAOs and the database; `:core:data` owns the
repository (interface *and* impl) and the mappers. The repository is shared rather
than per-feature because `:feature:workout` writes it while `:feature:history` and
`:feature:stats` read it — the one case the per-feature `domain/` rule does not
cover.

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
- **A form cue does not outrank the rep number, it replaces it** — and the
  difference is the whole of the arbitration (built 2026-08-13). Both fire at rep
  completion, so `QUEUE_FLUSH` means one erases the other; the trap is that they
  are offered at *different rates*. The fault lands on one frame, the number stays
  true on every frame until the next rep. A cue that merely won on the frame it
  fired would be followed by "eight" ~33 ms later and cut off mid-word — worse
  than either alone. So a correction stands in for the number **for as long as
  that rep is the current one**, keyed on the rep count. Losing "eight" is
  self-correcting; losing "go deeper" is not.
- **The cooldown on corrections prevents silence, not noise.** Second-order
  consequence of the hold: a held correction suppresses the number of the rep it
  belongs to, so a uniformly shallow set would say "go deeper" once and then go
  quiet for the rest of the set. `CORRECTION_COOLDOWN` (10 s, a guess to be tuned
  by ear) lets the correction step aside so the numbers flow again — which is also
  what tells the user those reps still counted.
- **Cue text is chosen by a shared mapper in the feature module**
  (`ui/mapper/SessionCue.kt`), not carried on the domain type. CLAUDE.md
  previously predicted `Waiting(message: UiText)`; that is wrong, because
  `:core:exercise` is pure Kotlin and `UiText` is Android. One mapper feeds both
  the composable and the speaker, so the drawn and spoken lines cannot drift.
- **Two classes, two questions — `CueEngine` decides *what is worth saying*,
  `CueAnnouncer` decides *whether the listener has already heard it*.** The engine
  owns the announcer, so the ViewModel hands over what the frame produced and
  speaks whatever comes back. The dividing rule: **anything that changes which cue
  is chosen belongs to the engine; anything that changes whether the chosen one is
  repeated belongs to the announcer.** That is why the cooldown is the engine's —
  its job is to let the rep number *win* — while `SpokenCue.repeatAfter` is the
  announcer's, and `SpokenCue` is exactly the message between them.
  Only the engine gets rewritten per exercise, and even then in two expressions:
  fault detection (`squatFormFault`) and wording (`mapper/FormCue.kt`). The
  session cues, the arbitration and the cooldown read the same for any exercise,
  so **exercise #2 should be parameters, not a second engine** — not
  parameterized today, because a strategy interface with one implementation is
  dead code.
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

- **Two things to judge by ear, both deliberate rather than oversights:** a
  shallow rep 8 means you hear "go deeper" and never "eight", and **consecutive
  identical faults are silent** — the held cue never changes, so the announcer has
  nothing new to say. Weakest for repeated abandoned descents, where the count
  does not move either. Fixable in two lines now that the engine owns the
  announcer (`announcer.reset()` on a deliberate re-emission), but that is a guess
  until it has annoyed somebody.

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
graded from `Rep.deepestAngle` and **never affects the count**. Ten shallow reps
read as "10 reps, depth 42%", not "0 reps".

```
standingExit    160°   descent under way
bottomEnter     115°   counts as a rep — deliberately lenient
bottomExit      125°   rising out of the bottom
standingEnter   168°   back to standing; the rep is counted here
goodDepthAngle   95°   scoring only, never affects counting (~parallel)
```

Research-informed starting points that **turned out to need no tuning** — all
eight fixtures pass unchanged. Hysteresis bands (8° and 10°) must stay wider than
post-smoothing noise, including the ~10 frames after a descent while the One Euro
cutoff winds down.

**A fixed threshold is still wrong for somebody**, and the fixtures prove human
ground truth is itself ambiguous here. The fix is **per-user calibration**, whose
shape is already decided: derive from range (`bottomEnter ≈ personalDeepest + 25°`)
with a **cap**, offer it **reactively** to the people the counter fails rather than
as an upfront flow, and **count relatively but coach absolutely** — "12 reps"
against your range, "depth 62% of parallel" against the standard. It needs
somewhere to store a baseline, so it lands with history.
[Evidence and the fixture overlap](docs/decisions.md#squat-thresholds).

### Two layers

`BodyPose -> SquatSignals` (per-leg knee angles gated on `inFrameLikelihood`
across all three joints, **averaged** when both survive — a large left/right
disagreement is far more often measurement error than real asymmetry, and
averaging cancels error where min/max amplify it) then `Float? -> SquatRepCounter`.
The state machine never sees a pose, so a test is a readable list of angles rather
than hand-built skeletons. `SquatSignals` doubles as the CSV fixture row format.

`PoseFrame.squatDepthAngle` composes the two and applies the framing gate, so
*"a badly framed frame reads as no reading rather than a bad one"* is stated once,
in core, with both thresholds as arguments a test supplies.

**`femurInclination` and `torsoLeanDegrees` have no production consumer** — they
exist to reach the fixtures, so depth scoring and forward lean can be tuned
against footage later. An "unused field" cleanup there costs a regeneration of all
eight clips.

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
reference is camera **roll**, which a propped phone rarely has.

Depth uses the interior knee angle, **not** femur inclination from vertical, even
though the latter states the parallel standard directly. Compute femur inclination
into `SquatSignals` anyway for scoring; never let it decide the count.

*If roll ever bites:* during `STANDING` the shoulder→hip segment should be
vertical, so whatever it reads is the camera roll — subtract it for the session.

### Form-fault thresholds need later research

Much of the common advice is folklore sports science has walked back ("knees must
not pass the toes" is normal for many people; forward lean is heavily
proportion-dependent). Encoding folklore means confidently nagging people about
non-faults, which is worse than silence. Every threshold will be tuned against
**one body** (the author's).

### Smoothing

`minCutoff = 1`, `beta = 0.5` are the One Euro paper's defaults, **untuned**. Tune
`minCutoff` first with `beta = 0` until rest is clean, then raise `beta` until fast
reps stop lagging. Two behaviors seen in simulation: **noise inflates the resting
cutoff**, and **the cutoff decays slowly after motion stops** — for ~10 frames
after a descent there is less smoothing than at rest, landing exactly at the
bottom where depth is read.

`PoseSmoother` filters in **units of frame width, not pixels**, so `beta` means the
same thing at any analysis resolution; both axes divide by width so smoothing stays
isotropic. A gap over 500 ms resets, so stepping out of frame and back doesn't drag
the skeleton across the screen.

**Kalman was evaluated and rejected** — a constant-velocity model assumes motion
continues, but a rep is a sequence of direction reversals, so it is wrong exactly
where the counter cares (~10° overshoot past the bottom). Structural, not
mistuning. Where it *would* win is missing measurements; if `inFrameLikelihood`
gating leaves gaps, try holding the last value with decaying confidence first.
[Simulation numbers](docs/decisions.md#smoothing-kalman-rejected).

### What the detector actually does

**ML Kit fits a skeleton to anything human-shaped, and is confident about it — so
every gate here is geometric, because geometry is all there is to gate on.** At
20 cm it does not report "no body", it **hallucinates a full skeleton** whose
`inFrameLikelihood` clears any usable threshold, and carrying the phone sweeps
those invented joints through 168 → 115 → 168 — one clean rep off a face. A mic
stand has been tracked as a person. **There is no confidence to tune**:
`inFrameLikelihood` answers "is this landmark inside the picture", and ML Kit
exposes no detection score at the `Pose` level at all.
[The observations](docs/decisions.md#what-ml-kit-does).

**Two geometric checks, taking opposite bets — that is the design, not
duplication.**

| | `Framing` | `SetupCheck` |
|---|---|---|
| asks | is this frame fiction? | may a set *start*? |
| bet | lenient — rejecting a real frame drops a rep | strict — starting on invented legs costs a whole set |
| torso fraction | 0.40 | 0.32 |
| also demands | — | one genuinely observed leg |

- A frame `Framing` rejects yields a **null depth angle**, not a special case —
  the "no reading" path `maxMissingFrames` already handles, so it needed no new
  state. The gate lives in `PoseFrame.squatDepthAngle`, not the ViewModel: it is a
  rule, not wiring.
- **The arm that actually works is the bounds test**, not a threshold: hip, knee
  and ankle must be above the confidence gate *and inside the image*, because ML
  Kit reports coordinates past the edge — which is what feet cut off by the bottom
  of the frame look like numerically. `MAX_TORSO_FRACTION = 0.32` turns out to be
  nearly unreachable and is a backstop for a phone propped low and angled up.
- **No `TOO_FAR` arm.** A real failure mode, but no too-far case has been recorded
  and a guessed lower bound would block real users.
- **Feet do not move during a squat** — hips and head come down, so torso fraction
  *falls* rather than rises. A `SetupCheck` that passes while standing therefore
  holds for the whole rep, and form cues can safely be gated on it.

**An abandoned descent is a two-frame trigger**: one frame below `standingExit`
then one above `standingEnter`, with no duration and no minimum depth. Walking
clears that trivially, so the walk back to the phone can fire "all the way down"
at the moment the user is finished. **Measured before acting, and the measurement
said don't** — it does not fire in normal use, and the data is filterable at read
time, unlike the tempo split.
[Numbers](docs/decisions.md#abandoned-descents).

**Known limitation:** arms held forward occlude the legs at the bottom of a rep,
worst front-on, so leg landmarks start being inferred — which will corrupt
heel-lift and shin rules. This is confidence degrading *within* a rep, so
`SetupCheck` cannot see it. The back camera at 0.5x remains the answer for small
rooms.

**From Milestone 1 footage:** 45° is the best angle (depth plus both knees
resolved); pure side view infers far-side limbs, so use near-side joints only.

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

- **`WorkoutViewModel` is tested end to end now** (2026-08-14, 20 tests across
  `WorkoutViewModelTest` and `WorkoutCameraTest`), against a fake
  `PoseCameraDataSource` — the interface extracted the same day. It was
  deferred for a long time on the grounds that it bought "the last 5%", and that
  was right until persistence arrived: the write path is the first thing here that
  can **lose or duplicate a user's data**, which is worth more than the routing
  around it. The double-write guard was mutation-checked (removing it fails
  exactly one test).
  The lesson still generalizes: **when a ViewModel is hard to test, look for the
  decision hiding in it before reaching for the fake.** Cue arbitration went to
  `CueEngine`, the framing gate to `PoseFrame.squatDepthAngle`, the lens preference
  to `Set<CameraFacing>.preferred` — each a pure function or a frame-driven object,
  testable with no ceremony, each leaving the ViewModel reading as routing.
  Two things the tests needed that are worth not re-deriving. **The ViewModel must
  be built lazily**: JUnit constructs the test instance *before* applying rules, and
  the `init` block touches `viewModelScope`, which binds whatever `Dispatchers.Main`
  is at that moment — as a plain field it silently gets a scope that never runs
  anything, and every test fails with an empty list rather than an error. And
  **driving it needs real landmark geometry** (`SquatFrames.kt`): a set only reaches
  `Active` after 3.5 s of `READY` frames, and a rep only counts after a smoothed
  168 → 115 → 168 sweep, so the fixture builds a body at a given interior knee angle
  rather than stubbing the angle.

## Camera selection vs capabilities — built 2026-08-08

Two groups, two natures. **Selection** is what the camera is doing, chosen by the
user. **Capabilities** are what it could do, discovered from the device. Conflating
them is why `cameraFacing` used to default to `FRONT` without knowing a front lens
exists — on a device without one, `requireLensFacing` throws and the app
**crashed at startup**, not on the flip. The manifest declares
`android.hardware.camera.any` and deliberately **not** `camera.front`, which would
filter the app off Play for devices without a selfie camera; it has to run on a
back lens alone. (That is a statement about what the app must *tolerate*, not a
preference — **front-first is the right default**, confirmed in use: it is the
only lens where you can check your own framing. The back camera's 0.5x ultra-wide
is the escape hatch for a room you cannot back far enough away in.)

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
`Set<CameraFacing>.preferred` in `:feature:workout`.

**It lives in the feature module and not next to `CameraFacing`**, and the name is
the reason: *preferred by whom?* `:core:pose` knows which lenses exist and has no
standing to have a favorite — everything else in `CameraFacing.kt` is a fact about
the enum or a translation to CameraX's constants. Selection is the feature's.
The empty set returning null is the load-bearing case: nothing is selected, so
`facings` never emits and nothing binds, where a fallback would hand CameraX a
lens the device does not have. It moves the day a second consumer appears — a
settings screen with a default-camera preference — and even then it lands with
that preference, not in `:core:pose`.

**`ZoomRange.stops` was the same rule, missed** (moved 2026-08-14, found by a
codebase pass). Which ratios to *offer* — the widest lens, 1x, and 2x only if the
camera reaches it — is a judgement about framing a whole body, not a fact the
lens reports, so it sat in `:core:pose` for exactly the reason `preferred` does
not. `ZoomRange` itself stays: min and max are what the device says. The two now
share one file, `CameraSelection.kt`, whose header states the rule once instead
of each property re-deriving it.
**The generalization: `:core:pose` may answer "what can this device do?" and
never "which of it do we want?"** Anything shaped like the second belongs to
whoever is doing the wanting.

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

## Camera configuration — settled 2026-08-08

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

## Framing — measured 2026-08-08

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

## Scope guard

**2–3 exercises max, done well.** Form heuristics are the hard part, not ML Kit —
landmarks jitter (smoothing + hysteresis are non-negotiable) and degrade with bad
lighting, clothing and angles, so UX must guide phone placement.
Completeness is **not** more exercises. It is the app *around* the camera:
onboarding, setup guidance, history worth browsing, stats worth opening, sensible
empty states, cues that feel like a coach.

Milestones, progress and the backlog live in `docs/status.md`.

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
- **No expression body when the body carries a multi-line argument list.** Write
  a block body with `return` and an explicit return type instead — `= Rep(`
  followed by four named arguments and a `)` puts the declaration, the `=` and
  the arguments at three different indent levels, and hides the return type
  behind inference. `= when (this) { … }` and `= runTest { … }` are unaffected:
  their body is already a block, so `{ return when … }` would only add a line
  and an indent level. Swept across the codebase 2026-08-14; the split was 13
  argument-list bodies rewritten, 8 `when` bodies left alone.
- **Comments are for what the code can't say.** A comment that restates the
  signature (`/** What the user did. */`, `/** Zoom stops. */`) is noise —
  delete it. Worth writing: why a number is that number, why an ordering or a
  thread matters, and what silently breaks if someone "tidies" the code. If the
  answer is in the names, say nothing.
