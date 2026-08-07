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
Corollary: **the backend is hand-built (Kotlin + Spring Boot) and is part of v1**
— Firebase/BaaS was considered and rejected; building it is part of the fun.

## Repository layout

```
replens/
├── client/   # Android app — independent Gradle build, open in Android Studio
└── server/   # Spring Boot API — independent Gradle build, open in IntelliJ
```

- **Two independent Gradle builds; no root Gradle project.** Never add a root
  `settings.gradle.kts`. Open `client/` or `server/` directly.
- **DTOs are duplicated between client and server, not shared.** Mobile clients
  can't be force-updated, so the server must stay backward-compatible anyway. API
  evolution discipline from day 1: default values, `@SerialName`, tolerant
  reading, versioned endpoints. Revisit at ~20–30 DTOs (upgrade path: OpenAPI).

## Identifiers

- Android `applicationId`: `com.replens.app` — **permanent on Play after first
  publish; never `com.example.*`**
- Server: group `com.replens`, artifact `server`
- Domain `replens.app` — owned (Cloudflare, expires 2027-08-05). Future API host
  `api.replens.app`; `.app` is HSTS-preloaded, so HTTPS is mandatory.

## Client tech stack

- Kotlin, Jetpack Compose (pure, no Views), Jetpack Navigation 3
- Hilt (DI), Room (local history), kotlinx.serialization
- CameraX (`ImageAnalysis`) + ML Kit Pose Detection
- **HTTP client undecided — Retrofit vs Ktor, settle when `:core:network` is
  built.** The `safeApiCall`-style wrapper this project wants (see *Result and
  errors*) is native in Ktor and needs a custom `CallAdapter` in Retrofit.

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
:feature:workout    WorkoutRoot / WorkoutScreen (private, pure render),
                    WorkoutViewModel, WorkoutState, PoseOverlay, RepCounter.
:core:pose          PoseCameraDataSource: CameraX + ML Kit behind Flow<PoseFrame>
                    + surfaceRequests. PoseMapper is internal — the ML Kit boundary.
:core:designsystem  RepLensTheme + the app's Compose gateway (below).
:core:model         Landmark, LandmarkType, BodyPose, PoseFrame. Pure Kotlin.
:core:posemath      Point, joint angles, torso size, normalized distances, line
                    deviation; OneEuroFilter + PoseSmoother. Pure Kotlin,
                    domain-free (no thresholds, no exercise names). 55 tests.
:core:exercise      Exercise knowledge and thresholds. Pure Kotlin. 30 tests.
                      …exercise/       Rep, RepPhase, RepUpdate (shared vocabulary)
                      …exercise.squat/ SquatSignals, SquatRepCounter, SquatRepConfig
```

Planned, not built: `:core:ui` (`UiText`, `ObserveAsEvents`), `:core:data`,
`:core:network`, `:core:database`, `:feature:{history,stats,leaderboard}`.

### Key decisions

- **Pure-Kotlin core:** domain models, smoothing, angle math, rep state machine
  live in `replens.jvm.library` modules — no Android imports, enforced by
  compilation. ML Kit types must not leak past `:core:pose`.
- **One exercise module, one package per exercise** (`…exercise.squat`), with a
  small shared vocabulary at the root. A module per exercise was rejected: with a
  2–3 exercise scope guard it's four Gradle projects doing one project's work, and
  packages already enforce the only boundary that matters. **Whether
  `Rep`/`RepPhase` generalise is unproven** — exercise #2 decides that, and
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

**Decided 2026-08-07, not yet built.** Colour and typography get RepLens names and
RepLens values; M3 stays as a *component library* whose theming we replace. Note
what was **not** rejected: Now in Android is itself fully custom — its palette and
all 15 type slots are its own. The only axis was whose semantic vocabulary to
adopt, so this is not "Material bad."

Two arguments decided it:

- **The component → role mapping is library-internal and versioned.** `Card` reads
  `surfaceContainerLow`; it used to read `surfaceVariant`. Under Material's
  vocabulary a `compose-material3` bump can restyle the app with no code change.
  Colours stated at our own wrappers can't be touched by an upgrade.
- **M3's five button variants encode Material's emphasis hierarchy, not ours.**
  Every choice between `FilledTonalButton` and `ElevatedButton` is a developer
  answering a Material question, and inconsistent answers are how a solo-built app
  drifts. `PrimaryButton` makes the right choice the only choice.

Structure:

- **Two tiers.** `internal` primitives (raw palette, never referenced by UI) →
  semantic tokens resolved per theme. Light/dark works because semantics remap,
  not because there are two palettes. Also the insurance policy: a palette that
  looks wrong is ~12 token edits, not a rewrite.
- **Keep the `on` pairing in our own names** (`accent`/`onAccent`). It is the one
  genuinely valuable thing in M3's colour system — a container never exists
  without a contrasting content colour. Giving it up means **contrast is our job**;
  check the pairs once at definition time.
- **Typography: our names, one grammar.** `labelLarge` vs `titleSmall` vs
  `displayMedium` is unmemorable and the 15-slot scale is 3× what we use. Don't mix
  schemes (`Title20` is role+size, `Text14SemiBold` is type+size+weight — pick
  one). Sizes in names are *base* sizes; accessibility settings scale `sp`.
- **`staticCompositionLocalOf`** for colours and typography, provided by
  `RepLensTheme` (static: themes change rarely, and it skips read tracking).
- **A thin, unloved `MaterialTheme` stays underneath**, referenced by no UI code,
  so anything un-wrapped doesn't render Material purple. Fill unassigned roles with
  magenta in debug to make leaks obvious on screen instead of arguable.
- **Dynamic color must go** — `RepLensTheme` still has `dynamicColor = true`, and
  wallpaper-derived colour is incompatible with a brand palette.
- **Wrap every M3 component before first use.** Nia's `DesignSystemDetector` lint
  rule is the eventual enforcement, once there are enough wrappers to police.
- Fonts live in `core/designsystem/src/main/res/font/`. Each weight is a file
  unless the family ships a variable font; four weights is ~0.5 MB of APK.

**Build the structure, populate on demand.** Tokens and wrappers designed against
imaginary screens are guesses; one screen exists today.

Written by a solo dev with no designer, which shapes the tactics: **borrow a
palette** (Radix Colors is built for this — numbered steps with defined meanings
and matched dark variants), keep a strict 4/8/12/16/24/32 spacing scale, and hold
to 4–5 type sizes and 2 weights. Amateur UI comes from too many values, not the
wrong ones. The visual load here is unusually low anyway — the workout screen is
invisible during a set, and history/stats/summary are lists and numbers.

### Icons

**No `material-icons-*` dependency.** Compose no longer bundles `Icons`, and the
extended artifact is bloat for the handful we need. Icons are vector XML
downloaded from [Material Symbols](https://fonts.google.com/icons) into
`:core:designsystem` — filled variants, exported as SVG. The export colour is
irrelevant (pick black): `Icon()` tints with `LocalContentColor` and replaces it.
What matters is that icons stay **single-colour**, or the tint flattens them.

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

### State modelling

Default: a **data class** with screen-level flags plus one
`val content: <Feature>Content` — a sealed interface with `Loading` / `Error` /
`Loaded`. Drop to a bare sealed root only when there is genuinely nothing outside
content; converting back later rewrites every `when` at every call site, and
screens reliably grow flags. `Loaded`, not `Success` — these are states, not
completed operations. Not every screen needs it: the workout screen has no
loading phase.

### Model layers

DTO -> domain -> UI, plus Room entities and navigation arguments. Domain models
stay free of `@Serializable`; routes are separate `NavKey` types with round-trip
mappers.

**Introduce a UiModel when the UI needs formatted or derived data, or when the
domain model carries fields the UI must not see.** A raw geometry stream is
neither — `PoseFrame` reaches the `Canvas` as a domain model, because mapping
30 fps × 33 landmarks would allocate ~1,000 objects/second for numbers the
overlay needs verbatim.

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
differently** — login yes, workout sync no. Modelling per-call errors as a sealed
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

**Prefer `update { }` over `value = value.copy(…)`.** Read-modify-write is not
atomic; it only survives today because `viewModelScope` is main-confined, and
CLAUDE.md plans to inject dispatchers later. The exception is a single-writer hot
path, where the point of reading first is to skip the copy — `onFrame` allocating
a `WorkoutState` 30×/s would cost more than the race it avoids.

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
wrong with it, so cues would need labelled bad-form classes per fault, and its
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

Research-informed starting points, **not tuned**. They live in a config data class
so fixtures can tune them. Hysteresis bands (8° and 10°) must stay wider than
post-smoothing noise, including the ~10 frames after a descent while the One Euro
cutoff winds down.

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

### Smoothing behaviours to remember

`minCutoff = 1`, `beta = 0.5` are the One Euro paper's defaults, **untuned**. Tune
`minCutoff` first with `beta = 0` until rest is clean, then raise `beta` until
fast reps stop lagging. Two behaviours seen in simulation: **noise inflates the
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

**Fixtures come from an `androidTest` running the real ML Kit pipeline** over
`~/replens-recordings/`, writing CSV that's committed for the fast JVM tests.
**Python MediaPipe was rejected** — fixtures exist mostly to *tune thresholds*, and
thresholds tuned against a different model don't transfer. (Harmless if fixtures
were only for logic regression.) Note `~/replens-recordings/squats.mp4` is a
*screen recording* with the overlay burned in — useless as fixture input.

**Why fixtures matter: you cannot do the same squat twice.** Tuning by rebuild →
deploy → do five squats → squint is minutes per iteration and the input changes
every time. Against a fixture it's a 50 ms test run.

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

## Current status (2026-08-07)

- **Milestone 1 (camera + overlay): done**, validated on device.
- **Milestone 2 steps 1–4: done**, validated on device — 8 reps performed, 8
  counted, clean phase transitions, no phantom reps from walking to/from the
  phone. 85 unit tests. Hilt is wired throughout.
- **What that does not prove: the thresholds.** Every rep in that clip was deep
  and clean, nowhere near the 115° boundary, and the smoothing constants are still
  the paper's defaults. Shallow reps are what will expose them.
- **Next:** camera flip, then camera configuration (below), then fixtures +
  threshold tuning, then step 5 (form rules + TTS — the first time `UiText`,
  `:core:ui` and `Event` earn their place). Rationale for that order: the camera
  is hardcoded to `DEFAULT_FRONT_CAMERA` but the recommended setup is 45° at
  2–3 m, a **back** camera position, and tuning form rules against badly framed
  footage bakes in bad numbers.
- **Deferred until they have a job to do:** `WorkoutEvent` (nothing one-shot yet),
  `:core:ui`, and Navigation 3 (one screen, nothing to navigate to — it lands with
  the post-workout summary).

### Open: camera configuration — settle before fixtures

We have never chosen an `ImageAnalysis` resolution or aspect ratio; CameraX
defaults are in force. That single decision moves field of view (framing),
pixels-on-body (landmark accuracy), buffer size (inference cost and heat), and
`sourceWidth`/`sourceHeight` (the overlay mapping). **It must land before
fixtures** — thresholds tuned against footage at one resolution don't transfer if
the resolution later changes.

Measured on the Pixel front camera (2026-08-07): `minZoomRatio = 0.8958334`,
`maxZoomRatio = 10.0`, while Google Camera offers 0.7–2.9 on the same lens. Two
readings follow. A max of 10 against their 2.9 shows **the OEM app curates its
range**, so their 0.7 is not evidence of what a third-party app can reach. And
0.8958 is not a lens ratio — a real ultra-wide reads ~0.5–0.6 — so it is crop
geometry, which is exactly what a different `ResolutionSelector` would move.

Also unresolved: **zoom above 1x on a single-lens camera is digital**, so it costs
landmark quality on the one screen where quality is the product. The `2x` stop may
deserve to go — decide once the flip lets us log both lenses, since a back
telephoto would be optical rather than a crop.
- **Known issues from the validation footage**, both UX rather than code: feet at
  or past the bottom edge during deep reps (leg landmarks start being inferred —
  will corrupt heel-lift and shin rules), and arms held forward occluding the legs
  at the bottom, which front-on framing makes worst.
- **`WorkoutRoot` is public and the `RepPhase` text is a debug affordance.** Root
  becomes internal when `navigation/` exists. Removing the phase `Text` alone
  won't cut recompositions — `phase` living in `WorkoutState` is what drives them;
  the real cleanup is dropping it from state once cues are events.
- Findings from Milestone 1 footage still worth honouring: 45° is the best angle
  (depth + both knees resolved); pure side view infers far-side limbs, so use
  near-side joints only; bad framing makes leg landmarks hallucinate. Sustained
  accurate-model inference warms the phone — acceptable; levers are the base
  model or throttling to ~15 fps.
- Test footage lives outside the repo in `~/replens-recordings/`.
- Parked for the library convention plugin when they earn their keep:
  `resourcePrefix`, default `testInstrumentationRunner`, `animationsDisabled`,
  `disableUnnecessaryAndroidTests`.

## Roadmap

1. **Camera + skeleton overlay** — done.
2. **The squat** — angles, smoothing, rep state machine, form heuristics + TTS.
3. **Local persistence & app shell** — Room history, Nav 3 flows, stats screen.
4. **Backend & sync** — Spring Boot API (auth or device-ID first), leaderboard.
5. **Second/third exercise + Play release** — push-ups, bicep curls; privacy
   policy (camera!), data-safety form, signing, crash reporting.

Backlog: camera flip (front/back — the overlay's `mirrored` flag must flip with
it) and zoom control including 0.5x ultra-wide via `cameraControl.setZoomRatio`
(usually back-camera only; helps in small rooms).

Scope guard: **2–3 exercises max, done well.** Form heuristics are the hard part,
not ML Kit — landmarks jitter (smoothing + hysteresis are non-negotiable) and
degrade with bad lighting, clothing and angles, so UX must guide phone placement.

## Conventions

- Conventional commits, matching existing history: `chore:`, `docs:`,
  `fix(client):`, …
- The author makes all commits; propose the message, never run `git commit`.
- **Comments are for what the code can't say.** A comment that restates the
  signature (`/** What the user did. */`, `/** Zoom stops. */`) is noise —
  delete it. Worth writing: why a number is that number, why an ordering or a
  thread matters, and what silently breaks if someone "tidies" the code. If the
  answer is in the names, say nothing.
