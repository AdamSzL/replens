# RepLens

AI-powered personal trainer for Android. Uses the phone camera and on-device ML
(Google ML Kit Pose Detection) to analyze exercise form in real time: live pose
tracking with a skeletal overlay, form correction via TTS + visual feedback, and
automatic rep counting that only counts technically correct reps. Workout history is
stored locally (Room) and synced to a backend for stats and leaderboards.
*"Every rep, seen."*

**Goals (reframed 2026-08-06):** **fun to build, and shipped to Google Play as a
complete product.** Enjoying the work and getting a real release out (Play
Console, signing, privacy policy, crash reporting) is the payoff; portfolio value
and traction are welcome side effects, not drivers.

**This is explicitly not a race to a minimum release.** v1 should feel like a
finished app someone would enjoy using — polished UX, more than a camera with
lines drawn on it: workout history, stats, sync and leaderboard, a few exercises,
guidance that makes the camera setup painless. There is no deadline, so quality
and experimentation win over speed. Corollary: **the backend is built by hand
(Kotlin + Spring Boot) and is part of v1** — Firebase/BaaS was considered and
rejected; building and running it is part of both the fun and the "complete
product" bar.

## Repository layout

```
replens/
├── client/   # Android app — independent Gradle build, open in Android Studio
└── server/   # Spring Boot API — independent Gradle build, open in IntelliJ
```

- **Two independent Gradle builds; there is no root Gradle project.** Never add a
  root `settings.gradle.kts`. Always open `client/` or `server/` directly in the IDE.
- **DTOs are duplicated between client and server, not shared.** Deliberate v1
  decision: mobile clients can't be force-updated, so the server must stay
  backward-compatible anyway. API evolution discipline from day 1: default values,
  `@SerialName`, tolerant reading, versioned endpoints. Revisit at ~20–30 DTOs
  (upgrade path: OpenAPI spec as the shared artifact).
- Root `.gitignore` is minimal by design: OS/IDE junk + secret safety nets, patterns
  unanchored so they apply recursively. Each scaffold has its own authoritative
  `.gitignore`.

## Identifiers

- Android `applicationId`: `com.replens.app` — **permanent on Google Play after
  first publish; never `com.example.*`**
- Server: group `com.replens`, artifact `server`
- Domain: `replens.app` — **owned** (bought 2026-08-05, Cloudflare Registrar,
  auto-renew on, expires 2027-08-05). Parked until Milestone 4; future API host
  `api.replens.app` (note: `.app` TLD is HSTS-preloaded — HTTPS mandatory).

## Client tech stack

- Kotlin, Jetpack Compose (pure, no Views), Jetpack Navigation 3
- Hilt (DI), Room (local history), kotlinx.serialization
- **HTTP client undecided — Retrofit vs Ktor client, settle when `:core:network`
  is built (Milestone 4).** Retrofit was the original pick, but the
  `safeApiCall`-style wrapper this project wants (see *Result and errors*) is
  native in Ktor and needs a custom `CallAdapter` to match in Retrofit. The author
  has shipped the Ktor version before.
- CameraX (`ImageAnalysis` pipeline) + Google ML Kit Pose Detection
- Multi-module Gradle architecture: `app/` (thin shell: DI graph, nav host),
  `core/*` (`:core:model`, `:core:data`, `:core:network`, `:core:database`,
  `:core:designsystem`, …), `feature/*` (`:feature:workout`, `:feature:history`,
  `:feature:stats`, `:feature:leaderboard`, …)

### Client toolchain notes (non-obvious)

- Versions live in `client/gradle/libs.versions.toml`. minSdk 26, Java/Kotlin
  target 21 (Gradle daemon JVM is pinned to 21 in
  `client/gradle/gradle-daemon-jvm.properties`).
- **AGP 9 built-in Kotlin:** the app module must NOT apply
  `org.jetbrains.kotlin.android` (KGP refuses with AGP 9). The Kotlin compiler
  version is upgraded by declaring `alias(libs.plugins.kotlin.android) apply false`
  in the root `client/build.gradle.kts` — classpath conflict resolution raises
  AGP's bundled KGP to the catalog version. Built-in Kotlin aligns `jvmTarget`
  with `compileOptions` automatically; do not add a `kotlin {}` / `kotlinOptions`
  block for it.
- The Compose BOM is intentionally declared twice in `dependencies` (for
  `implementation` and `androidTestImplementation`) — the IDE's "declared multiple
  times" inspection is a false positive; leave both.
- Release builds run R8 with resource shrinking; when adding libraries (ML Kit,
  Retrofit), verify `assembleRelease` still passes and add keep rules if needed.
- **Convention plugins can only apply plugins whose implementation is already on
  the buildscript classpath.** `pluginManager.apply("com.google.devtools.ksp")`
  resolves nothing by itself — hence every third-party plugin is declared
  `alias(...) apply false` in the root `client/build.gradle.kts`, which is also
  where its version is pinned. build-logic's own `compileOnly(...)` deps do not
  help: they exist to compile the convention plugin's source, not to put anything
  on the consumer's classpath. Verified by removing the declarations — the build
  fails with `Plugin with id 'com.google.devtools.ksp' not found`.
- **Hilt in Compose: use `androidx.hilt:hilt-lifecycle-viewmodel-compose`, not
  `hilt-navigation-compose`.** Both expose `hiltViewModel()`, but the latter
  depends on `androidx.navigation:navigation-compose` — Navigation *2* — which
  this project does not use. `hiltViewModel()` itself is navigation-agnostic
  (it resolves the factory from `LocalViewModelStoreOwner`). Under Navigation 3
  it additionally needs `rememberViewModelStoreNavEntryDecorator()` in
  `NavDisplay(entryDecorators = …)`, or every destination shares the Activity's
  `ViewModelStore` — a silent scoping bug, not a compile error.

### Architecture decisions (client)

- **Pure-Kotlin core:** domain models, smoothing, angle math, and the rep state
  machine live in `replens.jvm.library` modules (no Android imports — enforced by
  compilation). Android-bound code (CameraX/ML Kit, Compose, Room, TTS) wraps
  around them. ML Kit types must not leak past `:core:pose` — it maps them into
  our own landmark/pose data classes at the boundary.
- **One exercise module, one package per exercise — not a module per exercise.**
  `:core:posemath` stays domain-free (it would be identical in an app about
  physiotherapy, and compilation enforces that); `:core:exercise` owns thresholds
  and exercise names. Within it, per-exercise packages (`…exercise.squat`) with a
  small shared vocabulary (`Rep`, `RepPhase`, `RepUpdate`) at the root. A module
  per exercise was considered and rejected 2026-08-06: with a 2–3 exercise scope
  guard it is four Gradle projects doing one project's work, there is no build
  time to save on tiny pure-Kotlin modules, and the only boundary it would enforce
  (exercises not referencing each other) is already given by packages. Nested
  projects (`:core:exercise:squat`) are also an unusual Gradle shape when the
  parent has sources of its own. **Whether `Rep`/`RepPhase` genuinely generalise
  is unproven** — one exercise is not evidence. Exercise #2 decides both that and
  whether `SquatRepCounter` becomes a parameterized counter; splitting into modules
  later is a directory move, because the packages already match.
- **Feature-owned navigation (no api/impl split).** Features never navigate to
  each other and never depend on each other. Each feature owns its `NavKey` route
  types and exposes one `EntryProviderScope<NavKey>.<feature>Entries(...)`
  extension; `:app` calls those explicitly and owns the back stack. api/impl
  modules are deliberate overkill for a solo ~4-feature app — revisit only if
  build times hurt or features need to embed each other's UI. Full rules in
  *Feature architecture* below.
- **Every feature screen follows the same quintet: `Screen` + `ViewModel` +
  `State` + `Action` + `Event`.** The ViewModel exposes `state: StateFlow<State>`
  and one-shot `events: Flow<Event>` (channel-backed), and takes user intent
  through `onAction(action)`. Events are for things that must fire exactly once —
  navigation, and TTS cues especially (a cue must not replay on recomposition or
  rotation). Implement it as a per-feature convention, **not** a generic
  `BaseViewModel<S, A, E>`; inheritance-based MVI frameworks are where this
  pattern goes to die. Adopt alongside Hilt + Navigation 3, while `:feature:workout`
  is still the only feature. Full rules in *Feature architecture* below.
- Convention plugins live in `client/build-logic/` (included build):
  `replens.android.application`, `replens.android.library`,
  `replens.android.compose` (additive: compose flag + BOM + tooling — modules
  without UI must not apply it), `replens.jvm.library` (pure Kotlin, no AGP —
  must set kotlinc's jvmTarget explicitly since there's no built-in-Kotlin
  alignment), `replens.hilt` (additive: KSP + Hilt Gradle plugin +
  `hilt-android` + `ksp(hilt-compiler)`; Android modules only — the Hilt Gradle
  plugin requires AGP). AGP 9 plugin-code gotchas: `CommonExtension` has no generic type
  parameters anymore, and DSL blocks are property access in plugin code
  (`defaultConfig.minSdk = 26`), except `compileSdk { version = release(37) }`.

## Server tech stack

- Kotlin, Spring Boot, Gradle Kotlin DSL, JDK 21
- Spring Web, Spring Data JPA, PostgreSQL, Validation
- Keep v1 thin: auth, workout sync, stats, leaderboard. No social/streaming.

## Architecture

Module map (client) — each module's `build.gradle.kts` is convention plugins +
namespace + dependencies, nothing else:

```
:app                MainActivity: camera permission gate -> NavDisplay. Owns the
                    back stack + every cross-feature edge. Nothing else.
:feature:workout    WorkoutRoot (internal) / WorkoutScreen (private, pure render),
                    WorkoutViewModel (owns the session), WorkoutState, PoseOverlay,
                    navigation/WorkoutEntries.kt (routes + entry provider).
:core:pose          PoseCameraDataSource: CameraX + ML Kit behind Flow<PoseFrame>,
                    plus surfaceRequests: StateFlow<SurfaceRequest?>. PoseMapper is
                    internal — the ML Kit boundary.
:core:designsystem  RepLensTheme + the app's Compose gateway (see below).
:core:ui            Compose utilities that carry no design opinion: ObserveAsEvents,
                    UiText. Distinct from :core:designsystem on purpose.
:core:model         Landmark, LandmarkType, BodyPose, PoseFrame. Pure Kotlin.
:core:posemath      Point + joint angles, torso size, normalized distances and
                    line deviation; OneEuroFilter + PoseSmoother. Pure Kotlin,
                    domain-free (no thresholds, no exercise names).
:core:exercise      Exercise knowledge and thresholds. Pure Kotlin.
                      …exercise/       Rep, RepPhase, RepUpdate — shared vocabulary
                      …exercise.squat/ SquatSignals, SquatRepCounter, SquatRepConfig
```

Layering inside the workout feature: the composable renders and hands over the
`LifecycleOwner` (CameraX binds the camera to UI visibility — that handoff looks
like a layering violation but is the intended pattern); the ViewModel owns the
session and derives UI state; the data source owns hardware and ML.

Dependency rules:

- **`api` only for types in a module's public signatures**, `implementation`
  otherwise. Watch for deps that "work" only via someone else's transitive graph
  (`:core:pose` needs `api(coroutines)` because `Flow`/`StateFlow` are in its
  signatures).
- **`:core:designsystem` is the Compose gateway.** It `api`s foundation,
  material3, runtime, ui and ui-graphics, so UI modules get the toolkit from the
  design system they already depend on for theming. The compose convention plugin
  deliberately supplies only the compiler plugin, `buildFeatures.compose`, the BOM
  (Compose catalog entries are version-less on purpose — the BOM is the single
  version source) and preview annotations/renderer. Plugin = capability, design
  system = toolkit; neither is sufficient alone.
- Version catalog, plugin aliases and project accessors (`projects.core.pose`,
  enabled via `TYPESAFE_PROJECT_ACCESSORS`) are all type-safe — no string
  dependency notation.

### Threading and the 30 fps path

Assessed 2026-08-06. The instinct "move heavy math off the main thread" does not
apply here, and acting on it would cost more than it saves.

**The math is free.** One Euro over a full pose is 33 landmarks × 2 axes ≈ 1,000
float operations; the rep machine adds a handful of `atan2` calls. That is ~1 µs
against a 33 ms frame budget — about 0.01%. Dispatching it to
`Dispatchers.Default` would add a context switch and thread-hop latency to save
nothing measurable.

**ML Kit inference is already off-main.** `detector.process()` returns a `Task`
immediately and runs inference on ML Kit's own threads; only the success callback
lands on main. `flowOn(Dispatchers.Main)` in `PoseCameraDataSource` is not a
performance mistake either — `bindToLifecycle` must be called on main.

What actually costs, in order:

1. **Compose recomposition at 30 fps — the big one.** **Read pose state inside
   the `Canvas` draw lambda, not in the composable body**, so a new frame skips
   composition and layout and re-runs only the draw phase:
   ```kotlin
   Canvas(modifier) {
       val frame = frameState.value   // deferred read: draw phase only
       …
   }
   ```
   This matters more than every other item here combined, and it is easy to lose
   accidentally by hoisting the read "for readability".
   **Not done yet:** `PoseOverlay` currently takes `PoseFrame` as a parameter, so
   every frame recomposes it and its caller. Fix when wiring step 4.
2. **Allocation churn.** 33 `Landmark`s + a map + a `PoseFrame` per frame, and
   `PoseSmoother` allocates a second set — ~2,000 objects/second. Survivable, but
   it is the profiler metric to watch, not CPU time. (Also why `PoseFrame` is not
   mapped to a UiModel — see *Model layers*.)
3. **Delayed `imageProxy.close()`.** A janky main thread closes late, and with
   `STRATEGY_KEEP_ONLY_LATEST` that silently drops frames rather than failing
   loudly.

Planned, not yet done:

- **Step 4:** give `ImageAnalysis` its own single-thread executor instead of
  `getMainExecutor`, and pass the same executor to `addOnSuccessListener`. Not
  because the work is heavy — to decouple the camera pipeline from Compose's
  frame timing. `bindToLifecycle` stays on main.
- **When Room/network arrive:** inject dispatchers via Hilt qualifiers rather
  than hardcoding `Dispatchers.IO`. Mainly for testability — ViewModel tests want
  a `TestDispatcher`, and that is painful to retrofit once `Dispatchers.IO` is
  inlined in twenty call sites.

## Feature architecture

Settled 2026-08-06, ported from a previous app of the author's and adapted. This
is the shape every feature module follows; deviate only with a recorded reason.

### Package layout inside a feature module

```
data/         network calls, data sources, Room/DataStore, repository impls
di/           <Feature>Module.kt — Hilt @Module/@InstallIn for this feature
domain/       repository interfaces; UseCases only if they earn their keep
model/        models used only inside this feature (rare — most live in :core:model)
navigation/   route NavKeys + the EntryProviderScope extension (the entry point)
ui/           the five files below
ui/model/     UiModels
ui/mapper/    domain -> UiModel mappers
ui/components/  smaller composables, each with its own @Preview
```

`domain/` holds the repository *interface*, `data/` the implementation, bound in
`di/`. That split is worth it for repositories specifically — they are the thing
faked in every ViewModel test, and good coverage is an explicit project goal. Do
**not** extend it to data sources or mappers; a one-caller interface is ceremony.
Expect `domain/` to be mostly empty per feature: the real business logic (rep
state machine, form rules, smoothing) already lives in pure-Kotlin core modules,
which is the value UseCases would otherwise provide.

The repository is the bridge to the ViewModel and speaks **only domain models**,
wrapped in `Result`.

### The five files

`<Feature>Action.kt`, `<Feature>Event.kt`, `<Feature>State.kt`,
`<Feature>Screen.kt`, `<Feature>ViewModel.kt` — all in `ui/`.

- **Actions are past tense** — `StartClicked`, `PermissionGranted`. They are facts
  that already happened. No `On` prefix (`StartClicked`, not `OnStartClick`), so
  non-click actions read consistently.
- **Events are imperative** — `NavigateToSummary`. They are requests for something
  that has not happened yet.
- `<Feature>Screen.kt` holds exactly two composables:
  - `<Feature>Root` (**internal**) — collects state with
    `collectAsStateWithLifecycle`, hosts `ObserveAsEvents`, and maps events onto
    the navigation callbacks passed in from `navigation/`. Its `when (action)` may
    handle an action directly instead of forwarding it, to skip a pointless
    ViewModel round-trip for pure-navigation actions.
  - `<Feature>Screen` (**private**) — takes `state` + `onAction`, renders, nothing
    else. Always has a `@Preview` in the same file.
- Soft cap ~300 lines on the Screen file; anything bigger moves to
  `ui/components/`, each piece with its own preview. (Those previews are also the
  future surface for Compose Preview Screenshot Testing.)

### State modelling

Default: `<Feature>State` is a **data class** holding screen-level flags
(`isRefreshing`, …) plus one field `val content: <Feature>Content` — a sealed
interface with `Loading` / `Error` / `Loaded` arms. Drop to a bare sealed-interface
root only when the screen genuinely has nothing outside content; converting a
sealed root into a data-class root later rewrites every `when` at every call site,
and screens reliably grow flags.

`Loaded`, not `Success` — these are states, not completed operations.

Not every screen needs this. The workout screen has no loading phase.

### Model layers

DTO -> domain -> UI, with a fourth for Room entities and a fifth for navigation
arguments. Domain models stay free of `@Serializable`; routes are separate
`NavKey` types with round-trip mappers. Mappers to UiModels live in `ui/mapper/`.

**Documented exception: `PoseFrame` stays a domain model all the way into
`WorkoutState`.** The camera path is 30 fps × 33 landmarks; mapping it to a
UiModel would allocate ~1000 objects/second alongside ML inference, for a
`Canvas` that needs exactly the domain numbers. The general rule is: introduce a
UiModel when the UI needs *formatted or derived* data, or when the domain model
carries fields the UI must not see. A raw geometry stream is neither.

### UiText

All user-facing text that the ViewModel or a mapper **chooses between** is a
`UiText` (in `:core:ui`), resolved at render time — never a `String` resolved in
the ViewModel, or a locale change won't re-render. Text that is always the same
resource does not belong in state at all; the composable calls `stringResource`
directly. Plain `String` only for server-provided or user-entered content.

```kotlin
@Immutable
sealed interface UiText {
    data class Raw(val value: String) : UiText
    data class Resource(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText
    data class Plural(
        @PluralsRes val id: Int,
        val quantity: Int,
        val args: List<Any> = listOf(quantity),
    ) : UiText

    companion object {
        fun resource(@StringRes id: Int, vararg args: Any) = Resource(id, args.asList())
        fun plural(@PluralsRes id: Int, quantity: Int, vararg args: Any) =
            Plural(id, quantity, if (args.isEmpty()) listOf(quantity) else args.asList())
    }
}
```

Two `asString()` extensions: a `@Composable @ReadOnlyComposable` one, and one
taking a `Context`.

Non-obvious, and the reason this differs from the common `vararg val args: Any`
version: **args must be a `List`, not an `Array`, and the arms must be data
classes.** `Array` equality is identity-based and `emptyArray()` allocates a fresh
instance per construction, so two structurally identical cues never compare equal
— which breaks `MutableStateFlow` conflation (the workout screen would emit 30×/s
for an unchanged cue) and makes whole-state `assertEquals` in ViewModel tests
fail. `@Immutable` does not fix this; it governs Compose stability, not equality —
both are needed, since `List<Any>` is itself unstable to the compiler. The
`vararg` lives on the companion factories so call sites stay ergonomic.

`Plural.quantity` selects the plural form and does **not** fill `%d` — hence the
`listOf(quantity)` default, otherwise `getQuantityString` throws at runtime.
Polish (one/few/many/other) is why this matters here and English-only testing
won't catch it.

The `Context` overload is what the TTS engine calls, so a single `UiText` value
drives both the on-screen cue and the spoken line, and the form-rule engine stays
unit-testable by emitting `UiText` rather than resolved strings.

### Result and errors

```kotlin
sealed interface Result<out D, out E : AppError> {
    data class Success<out D>(val data: D) : Result<D, Nothing>
    data class Failure<out E : AppError>(val error: E) : Result<Nothing, E>
}
typealias EmptyResult<E> = Result<Unit, E>
```

Arms are `Success`/`Failure` and the marker is `AppError` — naming the failure arm
`Error` shadows the marker interface (forcing fully-qualified references) and
collides with `kotlin.Error`, which is a `Throwable`.

**Error strategy: shared + specific.** One `NetworkError` enum every call can
produce (`NoInternet`, `Serialization`, `Timeout`, `Unauthorized`,
`TooManyRequests`, `Server`, `Unknown`), plus a per-endpoint sealed type **only
where the UI branches differently** — login (`InvalidCredentials`,
`EmailAlreadyTaken`) yes, workout sync no. Rule: add a specific error only when
the UI does something different because of it. Modelling per-call errors as a
sealed interface with a `Network(NetworkError)` arm also removes the
`wrapCommon: (CommonError) -> E` parameter that the previous app's `safeApiCall`
needed.

### Events

Channel-backed, **`Channel.BUFFERED`, not RENDEZVOUS**. With RENDEZVOUS `send`
suspends until the screen collects, so a backgrounded screen parks the sending
coroutine and stalls whatever follows the send. Always `send` from
`viewModelScope`; never `trySend` (silently drops).

Collected via `ObserveAsEvents` in `:core:ui` — `repeatOnLifecycle(STARTED)` +
`withContext(Dispatchers.Main.immediate)`. The `immediate` matters: without it the
collector resumes via a dispatch and an event can land after cancellation has
begun, i.e. dropped exactly when the user backgrounds the app mid-navigation.
Wrap the callback in `rememberUpdatedState` — `LaunchedEffect` keys on the flow,
so a lambda capturing changing state goes stale otherwise.

### Navigation (Navigation 3)

Each feature owns its routes and its entry provider:

```kotlin
// :feature:workout — navigation/WorkoutEntries.kt
@Serializable data object WorkoutRoute : NavKey
@Serializable data class WorkoutSummaryRoute(val workoutId: String) : NavKey

fun EntryProviderScope<NavKey>.workoutEntries(
    navigator: Navigator,
    navigateToHistory: () -> Unit,       // cross-feature: :app decides
) {
    entry<WorkoutRoute> {
        WorkoutRoot(navigateToSummary = { navigator.goTo(WorkoutSummaryRoute(it)) })
    }
    // …
}
```

- **No shared routes module.** A feature can only name its own routes, so handing
  it a `Navigator` is safe — the compiler, not discipline, enforces that internal
  edges stay internal and cross-feature edges are hoisted lambdas wired in `:app`.
- `:app` calls each `<feature>Entries(...)` explicitly inside `entryProvider { }`.
- **Google's multibinding recipe (`@IntoSet EntryProviderInstaller`) was evaluated
  and rejected.** Hilt must construct the installer, so cross-feature lambdas
  can't be passed — which pushes you to a shared routes module or the api/impl
  split the recipe uses. Its payoff (`:app` not knowing its features) is mostly
  unavailable anyway: a bottom bar means `:app` names all four top-level routes
  regardless. Reversible either way — wrapping each `<feature>Entries` call in an
  `@IntoSet @Provides` is ~10 lines per feature — so revisit if dynamic feature
  modules ever appear.
- **Back stack persistence gotcha:** the recipe's `@ActivityRetainedScoped
  Navigator` holds a plain `mutableStateListOf`, which survives rotation but
  **not process death** — the user returns to the start destination. `NavKey`s are
  `@Serializable` from day one so this is fixable (`SavedStateHandle` in the
  `Navigator`, or a `rememberNavBackStack`-backed list); do it before release.
- Wrap navigation clicks in `dropUnlessResumed { }` — prevents double-navigation
  when a fast double-tap lands while the screen is already leaving.

### Explicit backing fields

ViewModels use Kotlin's explicit backing fields instead of the `_state` /
`asStateFlow()` pair:

```kotlin
val state: StateFlow<WorkoutState>
    field = MutableStateFlow(WorkoutState())
```

**Verified 2026-08-06 on Kotlin 2.4.10:** compiles with no `-Xexplicit-backing-fields`
flag and emits no experimental warning on a `--rerun-tasks` build. Encapsulation
was confirmed empirically, not assumed — a probe doing `vm.state.value = …` from
outside the class fails with `'val' cannot be reassigned`, so callers really do
see `StateFlow`.

The cost to be aware of: the same name has different types depending on scope —
`MutableStateFlow` inside the class body, `StateFlow` outside. `state.update { }`
inside a ViewModel only type-checks because of the feature, and the old `_state`
naming made that mutability visible for free.

## Product decisions

**Voice feedback: platform TTS (`android.speech.tts.TextToSpeech`), on-device.**
Free, offline once voice data is installed, no dependency. Non-obvious
requirements, all of which make or break the feel:

- Request **transient ducking** audio focus with `AudioAttributes` usage
  `ASSISTANCE_ACCESSIBILITY` — users exercise with music on; it should dip, not stop.
- Speak with `QUEUE_FLUSH`, never `QUEUE_ADD` — a cue that lands four seconds late
  is worse than silence. One cue at a time, chosen by priority, with a cooldown.
- Language availability is not guaranteed (`LANG_MISSING_DATA` /
  `LANG_NOT_SUPPORTED`, engine varies by device): try device locale, fall back to
  English, keep every phrase in `strings.xml`.
- Keep one engine instance alive (init costs a few hundred ms).

**Form feedback: hand-written geometry rules, not an LLM.** Cues must fire within
~100 ms; on-device inference is far too slow and a fault like knee valgus is a
measurement, not a judgement. Per-exercise rule = detector + threshold +
hysteresis + cooldown + priority. Squat signals: depth (hip–knee–ankle angle),
valgus (knee-x vs the hip→ankle line), forward lean (shoulder→hip vs vertical),
heel lift (heel-y vs foot-index-y), left/right asymmetry. **Normalize every
*distance-based* threshold by the user's own proportions** (torso size / femur
length in pixels) so rules survive different body sizes and camera distances.
Tune against `~/replens-recordings/`.

Angles need no normalization — they are already invariant to scale and
translation, which is why depth (a joint angle) is the cheapest signal to get
right and valgus (a distance) is not.

**Normalization is translate + scale, NOT rotate** — see the ML Kit note below.

ML Kit's own [pose classification guide](https://developers.google.com/ml-kit/vision/pose-detection/classifying-poses)
(k-NN over pairwise-joint-distance embeddings, a few hundred labelled images per
exercise) was evaluated and **not adopted as the primary approach**: it tells you
*which pose* you're in, not *what's wrong with it*, so form cues would need
labelled bad-form classes per fault per exercise, and its output isn't
explainable enough to speak. Two things taken from it anyway: **normalize poses
to constant torso size** before computing anything (that's the body-proportion
normalization above, made concrete) — but **not** the guide's rotation to
vertical torso orientation: that is right for classification, where you want
tilt-invariant embeddings, and wrong for us, because forward lean *is* torso
angle against vertical, so rotating would erase the exact signal we measure, and
would erase real lean and camera tilt indiscriminately. Camera tilt is handled by
the setup check (upright phone at hip height), not by silently rotating it away.
Also taken from the guide: its
rep counting via separate entry/exit probability thresholds is the same
hysteresis our state machine needs. Reconsider classification only if hand-tuned
thresholds prove brittle around exercise #3 — collecting samples scales better
than hand-tuning a new state machine per exercise.

**An LLM belongs in the post-workout summary, not the live loop** — aggregate the
deterministic metrics into natural language server-side in Milestone 4 ("depth on
8/12 reps; knees caved on the last three — likely fatigue"). No latency
constraint, no hallucination risk in the safety-critical path.

**Login is optional; never gate the core loop.** Anonymous/guest by default (Room
history needs no identity at all). Signing in unlocks sync, multi-device and the
leaderboard — which needs a display name anyway, so it's an honest incentive.
Decide the **anonymous→account data-migration path before writing sync**
(Firebase anonymous auth linking to Google Sign-In preserves the UID; a custom
backend needs a device-ID → account linking endpoint). Play requires in-app *and*
web account deletion if account creation exists — budget it in Milestone 5.
Guest-first also matters for the portfolio goal: a recruiter will not sign up.

## What makes it a product (engagement design)

Real-time form correction alone is a *feature*. The product is: **your phone
counts your reps, logs your workouts, scores your form, and shows you the rep you
got wrong.** Same tech and same scope guard — better product. Lead with rep
counting and automatic logging ("I don't have to count, and it logs itself"):
that's the hook that doesn't decay. Form checking is what makes someone *choose*
RepLens; auto-logging is what makes them *keep* it.

Three risks to design against, not discover after launch:

1. **Setup friction is the biggest threat.** Clear the space, prop the phone,
   check framing, walk back — a ~60 s tax before a 3 min workout. Evidence: the
   first three test recordings were unusable because of phone placement, made by
   the most motivated user this app will ever have. The pre-rep setup check is
   necessary but not sufficient: also audio-guided framing (you can't read the
   screen from 3 m), a "you're good" chime, remembering a working setup, and
   ultra-wide 0.5x for small rooms.
2. **The screen is invisible during a set.** Nobody watches the overlay mid-squat,
   least of all side-on. **Audio is the real UX; the skeleton is marketing** (and
   a great first-30-seconds moment). Spend effort accordingly.
3. **Cue novelty decays.** After ~3 sessions the user has learned "go deeper,
   knees out" and the app has nothing new to say.

Features that answer those (and are also what "complete product" means here):

- **A form score that trends** — "average depth 88%, up from 81% last week".
  Converts one-off cues into something to chase; gives the stats screen a purpose.
- **Structured sets** — "3×10 squats" with rest timers, so a session is something
  you *complete*. Completion makes streaks meaningful.
- **Post-workout summary** (see the LLM note above) as the closing moment.
- **Replay the worst rep as a skeleton animation.** Store the landmark stream, not
  video — kilobytes, no privacy problem, and impossible for a mirror to do.
  "Here's rep 7, where your knees caved." This is what makes pose data valuable
  *after* the workout, which is where the app currently has nothing.

Completeness is **not** more exercises (scope guard: 2–3, done well). It's the app
*around* the camera: onboarding, setup guidance that works in a small room,
history worth browsing, stats worth opening, sensible empty/error states, and cues
that feel like a coach rather than a nagging timer.

## Milestone 2 plan (the squat)

Order, decided 2026-08-06. Each step is finishable and verifiable on its own.

1. **`:core:posemath`** — **DONE.** `Geometry.kt` (`Point`, `angleDegrees`,
   `distance`, `midpoint`, `angleFromVerticalDegrees`, `deviationFromLine` —
   signed, because valgus and varus are opposite faults) and
   `PoseMeasurements.kt` (`BodyPose` extensions taking `LandmarkType`s:
   `torsoSize`, `torsoLeanDegrees`, `distanceNormalized`,
   `deviationFromLineNormalized`). Domain-free: no thresholds, no exercise names,
   no state. 33 tests.
2. **One Euro filter** — **DONE.** `OneEuroFilter` (scalar, adaptive, keyed on
   `PoseFrame.timestampMillis` so it adapts to real frame intervals rather than
   assuming 30 fps) and `PoseSmoother` (one filter pair per landmark, wrapping a
   whole `PoseFrame`). 22 tests. Two decisions worth keeping:
   - `PoseSmoother` filters in **units of frame width, not pixels**, so `beta`
     means the same thing whatever analysis resolution the device picks. Both
     axes divide by width — dividing y by height instead would smooth vertical
     jitter differently from horizontal.
   - A gap longer than `resetAfterGapMillis` (500 ms) resets, so someone stepping
     out of frame and back is not dragged across the screen from their old spot.
   - `minCutoff = 1`, `beta = 0.5` are the **paper's starting values, untuned**.
     Tune `minCutoff` first with `beta = 0` until rest is clean, then raise `beta`
     until fast reps stop lagging.

   Two behaviours to remember when the rep counter misbehaves, both observed in
   simulation: **noise inflates the resting cutoff** (leftover jitter in the speed
   estimate looks like movement, and `beta` multiplies it — at ±2° of noise the
   resting cutoff wanders to 2–5 Hz rather than sitting at `minCutoff`), and **the
   cutoff decays slowly after motion stops** (the smoothed rate falls off
   exponentially, so for ~10 frames after the descent there is less smoothing than
   the resting case). The second lands exactly at the bottom of the squat, where
   depth is read — the state machine's hysteresis has to absorb it.

   **Kalman filtering was evaluated and rejected.** A constant-velocity Kalman
   filter assumes motion continues; a rep is a sequence of direction reversals, so
   the model is wrong precisely where the counter cares. Simulated on a synthetic
   squat with ±2° noise it overshoots ~10° past the bottom and ~8° past the top
   (mean abs error 3.6° vs 0.85° for One Euro), and a 10° error at the bottom is
   the difference between "good depth" and "not deep enough". This is structural,
   not mistuning: lowering `Q` smooths more but worsens the overshoot, raising it
   reduces overshoot but converges on the raw signal. Also, for fixed `dt`/`Q`/`R`
   the Kalman gain converges to a constant — a steady-state CV filter is an
   alpha-beta filter, so it does not adapt to speed at all, which is the one thing
   we want. (MediaPipe, same BlazePose lineage as ML Kit, also ships One Euro for
   landmark smoothing.)

   Where Kalman *would* win, if these ever bite: **missing measurements** — it
   coasts on prediction while a landmark is occluded, which One Euro cannot do at
   all, and side-view footage occludes far-side limbs by definition. Also sensor
   fusion (camera + IMU) and velocity estimates with uncertainty. If gating on
   `inFrameLikelihood` leaves gaps at step 3, try **holding the last value with
   decaying confidence** first — a full Kalman is a large jump for that problem.
3. **Rep state machine** — `STANDING → DESCENDING → BOTTOM → ASCENDING`, with
   separate entry/exit thresholds on the knee angle (the same hysteresis idea as
   ML Kit's classification rep counting). Design settled 2026-08-06 — see
   *Rep counting* below.
4. **Wire into `WorkoutViewModel`** — a live rep counter on device. First real
   use of the Hilt graph.
5. **Form rules + TTS** — this is where `UiText`, `:core:ui`, and the
   `Action`/`Event` files finally earn their place, because it is the first time
   the screen has something to say.

Non-obvious implementation notes for step 1:

- **Compute angles with `atan2(cross, dot)`, not `acos(dot / (|v1|·|v2|))`.**
  `acos` needs its argument clamped — floating-point error yields `1.0000001` on a
  straight leg and `acos` returns `NaN` — and it is numerically worst near 0° and
  180°, exactly where a standing leg sits, so landmark jitter becomes large angle
  swings. `atan2` has neither problem.
- **2D only (`x`, `y`).** ML Kit's `z` is relative depth with much lower
  reliability; mixing it in adds noise to a measurement that works without it.
- **Return `null`, not `NaN`, for degenerate input** (coincident landmarks, which
  ML Kit does occasionally emit). `NaN` propagates silently through everything
  downstream; `null` forces the caller to decide.
- Low `inFrameLikelihood` is **not** filtered here — that is the caller's call and
  varies per rule (side-view rules use near-side joints only).

### Rep counting (researched and settled 2026-08-06)

**Convention trap — read this before touching any threshold.** Literature reports
**knee flexion** (0° = straight leg); `angleDegrees(hip, knee, ankle)` returns the
**interior angle** (180° = straight leg). `interior = 180 − flexion`. They coincide
at 90°, which is the one value everyone quotes for parallel — so the mistake is
invisible exactly where you would check it, and wrong everywhere else.

| depth | flexion (literature) | **interior (ours)** |
|---|---|---|
| standing | 0° | 180° |
| mini squat | 40–50° | 130–140° |
| **parallel** (hip crease level with top of patella) | ~90° | ~90° |
| below parallel (IPF standard) | ~120° | ~60° |
| deep / ATG | 110–130° | 70–50° |

**Counting and depth-scoring are separate questions.** One threshold forces a bad
trade: set it at true parallel and casual users' reps do not count (app looks
broken); set it lenient and quarter-squats count. So `bottomEnter` only decides
"this was a rep attempt" and is deliberately forgiving, while depth *quality* is
graded from `Rep.deepestAngle` and never affects the count. Ten shallow reps
become "10 reps, depth 42%", not "0 reps".

```
standingExit    160°   descent under way
bottomEnter     115°   counts as a rep — deliberately lenient
bottomExit      125°   rising out of the bottom
standingEnter   168°   back to standing; the rep is counted here
goodDepthAngle   95°   scoring only, never affects counting (~parallel)
```

Starting points, not final — they live in a config data class so step 3½ can tune
them against fixtures. Hysteresis bands (8° and 10°) must stay wider than
post-smoothing noise, including the ~10 frames after a descent while the One Euro
cutoff winds down.

**Two layers.** `BodyPose -> SquatSignals` (per-leg knee angles gated on
`inFrameLikelihood` across all three joints, averaged when both survive, falling
back to whichever does, `null` when neither) then `Float? -> state machine`. The
state machine never sees a pose — a test is then a readable list of angles rather
than eight hand-built skeletons. `SquatSignals` doubles as the CSV fixture row
format. Average rather than min/max: a large left/right disagreement is far more
often measurement error than real asymmetry, and averaging cancels error where
min/max amplify it.

### Reference frames — which rules need true vertical

Borrowed (as an idea, not code) from
[learnopencv's squat trainer](https://learnopencv.com/ai-fitness-trainer-using-mediapipe/),
which measures every segment against vertical. We do not copy that, but the
underlying property is worth being deliberate about, because it decides which
rules survive a sloppy camera setup:

| signal | measured against | needs true vertical? |
|---|---|---|
| depth | interior knee angle | no |
| knee valgus | the hip→ankle line | no |
| left/right asymmetry | the other leg | no |
| **forward lean** | **vertical** | **yes** |
| **heel lift** | **the y-axis** | **yes** |

Three of five are reference-free, including everything rep counting depends on.
That is not luck — valgus is *defined* as the knee leaving the hip→ankle line, a
purely internal relationship. But forward lean cannot be expressed without
gravity, so those two rules inherit the fragility and must be gated on the setup
check rather than trusted unconditionally.

Precision worth keeping: what breaks a vertical reference is camera **roll**
(rotation about the viewing axis), which a propped phone rarely has. The tilt in
the Milestone 1 findings was **pitch** (phone low, angled up), which causes
perspective distortion and degrades *every* measurement, reference-free or not.
Interior angles are preferred for the simpler reason that they need no reference
frame at all — one assumption fewer.

Depth is measured by interior knee angle, **not** femur inclination from vertical,
even though the latter states the parallel standard directly (thigh horizontal =
90°). Compute it into `SquatSignals` anyway as the honest "below parallel" measure
for scoring; just never let it decide the count.

*Idea if roll ever bites:* during `STANDING` the shoulder→hip segment should be
vertical, so whatever angle it reads is the camera roll — subtract it for the rest
of the session. Self-calibrating, no UI, no sensors. Conflates posture with tilt,
so not exact, but better than assuming image-vertical is true vertical, and the
state machine already says exactly when the user is standing.

Form-fault thresholds need more care and **later** research: much of the common
advice is folklore that sports science has walked back ("knees must not pass the
toes" is normal and unavoidable for many people; forward lean is heavily
proportion-dependent — long femurs lean more without doing anything wrong).
Encoding folklore as rules means confidently nagging people about non-faults,
which is worse than staying quiet. Also remember every threshold will be tuned
against **one body** (the author's).

## Testing & CI (planned)

Good test coverage is an explicit goal of this project, not an afterthought —
there is no delivery deadline, so experimenting here is worth the time.

**Unit tests carry the weight, and the architecture is built for it.** The
interesting logic — angle math, One Euro smoothing, the rep state machine, form
rules — lives in `replens.jvm.library` modules with no Android dependencies, so
it runs on plain JUnit in milliseconds.

### Testing the pose pipeline (settled 2026-08-06)

**Test the derived signal, not the landmarks.** A fixture of 900 frames × 33
landmarks is unreadable, so nobody can tell whether a failure is real. The state
machine consumes a handful of scalars per frame, so a fixture is a time series of
`timestamp, kneeAngle, hipAngle, torsoAngle` — a few hundred rows, diffable,
plottable, and you can see five valleys in it by eye.

Three layers, each answering a different question:

1. **Synthetic sequences — "is the logic correct?"** Hand-written curves: a clean
   rep, a half rep that must not count, a pause at the bottom, jitter oscillating
   on the threshold, a very slow rep. Deterministic, and each encodes one decided
   rule. These catch regressions.
2. **Real recordings — "does it survive real noise?"** One assertion per file:
   *this recording contains exactly 5 reps.* Ground truth is free and unambiguous
   because the author filmed it.
3. **A way to see it — "why is it wrong?"** No assertion explains a bad count.
   Dump the angle curve with state transitions marked (CSV, or a live debug
   overlay showing knee angle + current state).

**Fixtures come from an `androidTest` that runs the real ML Kit pipeline** over
`~/replens-recordings/` and writes CSV; the CSV is committed and the fast JVM
tests read it. Run once, offline afterwards.

**Python MediaPipe was evaluated and rejected** for generating those fixtures.
It runs on the Mac and reads video directly, which ML Kit can't — but the fixtures
exist mostly to *tune thresholds*, and thresholds tuned against a different model's
numbers do not transfer to ML Kit. (Model drift would be harmless if fixtures were
only used for logic regression.)

**Why fixtures matter at all: you cannot do the same squat twice.** Tuning by
rebuild → deploy → perform five squats → squint is minutes per iteration, physically
tiring, and the input changes every time. Against a fixture it's a 50 ms test run
on identical input.

**Rep counting is objectively testable; form quality is not.** You know you did 5
reps; whether rep 3 was "too shallow" depends on whose standard. Unit-test counting
hard, and treat form rules as tuned-by-eye against footage rather than asserted.

**Compose Preview Screenshot Testing** (`com.android.compose.screenshot`) — to
try once there is UI worth pinning; best fit is `:core:designsystem` components
and the workout feature's pure-render composable driven by fixed state values (it
takes plain state, which is exactly why it's previewable).

- Status: **alpha** (`0.0.1-alpha15`+), APIs may change. Our toolchain already
  meets the requirements (AGP 9.3.1, Kotlin 2.4.10, JDK 21).
- Setup: `android.experimental.enableScreenshotTest=true` in `gradle.properties`
  *and* `experimentalProperties["android.experimental.enableScreenshotTest"] = true`
  per module; `screenshotTestImplementation` of `screenshot-validation-api` +
  `ui-tooling`; tests annotated `@PreviewTest` + `@Preview` in a `screenshotTest`
  source set. Tasks: `updateDebugScreenshotTest` (regenerate references),
  `validateDebugScreenshotTest` (verify).
- **Host-side — no emulator or device**, which is what makes it CI-friendly,
  unlike instrumented tests.
- Gotchas: renaming a `@PreviewTest` function orphans its reference image
  (regenerate); memory-hungry (`android.compose.screenshot.maxHeapSize=4g`);
  reference PNGs are committed, so watch repo size.
- If it works out, enabling it belongs in a convention plugin rather than
  per-module boilerplate.

**GitHub Actions** — verify build + tests on push/PR: `assembleDebug`,
`assembleRelease` (catches R8 breakage from new libraries), unit tests, and
`validateDebugScreenshotTest` once screenshot tests exist. Cache the Gradle
distribution and build cache; the daemon JVM is pinned to 21.

## Current status (2026-08-06)

- Client builds on AGP 9.3.1, Kotlin 2.4.10, Gradle 9.6.1, SDK 37, Java 21,
  R8 + resource shrinking on for release. Server not started.
- **Milestone 1 (camera + skeleton overlay): DONE and validated on a real device.**
- Findings from on-device squat footage (front, side, 45° both directions):
  - Overlay alignment and front-camera mirroring are correct (FILL_CENTER math).
  - Bottom-of-squat tracking holds in all views. 45° is the best angle (depth +
    knees, both legs resolved) and should be the recommended user setup.
  - Pure side view: far-side limbs are inferred — compute side-view metrics from
    near-side joints only, gate the rest by `inFrameLikelihood`.
  - Bad framing (phone low + tilted up, too close, feet out of frame) makes leg
    landmarks hallucinate → Milestone 2 must include a pre-rep setup check
    (nose + both ankles in frame with likelihood above threshold, else guide the
    user: upright phone, ~hip height, 2–3 m).
  - Sustained accurate-model inference warms the phone; acceptable. Levers if
    needed later: base `pose-detection` model, or throttle analysis to ~15 fps.
- Known-good squat test footage (ground truth for tuning smoothing / rep
  detection) lives outside the repo in `~/replens-recordings/` — keep it out of
  git; delete once the rep counter is tuned against it.
- **Structural pass: modules done** (see Architecture above). `build-logic`
  convention plugins in place and the spike is fully extracted — `:app` is
  MainActivity only. Builds green (debug + release), and **re-verified on a
  physical device after the extraction** (2026-08-06): skeleton still tracks.
- **Feature architecture settled** (see that section) — the conventions are
  written down but **not yet implemented**; `:feature:workout` still has
  `WorkoutUiState` + `WorkoutScreen`/`WorkoutContent`, not `WorkoutState` +
  `WorkoutRoot`/`WorkoutScreen`. Deliberate — see *Next* below.
- **Hilt: DONE.** `replens.hilt` convention plugin (KSP 2.3.11 + Hilt 2.60.1),
  `RepLensApplication` (`@HiltAndroidApp`), `@AndroidEntryPoint MainActivity`,
  `@HiltViewModel WorkoutViewModel`, `PoseCameraDataSource` constructor-injected,
  `viewModelFactory` companion gone. Debug + release both build.
  `PoseCameraDataSource` is **unscoped, not `@Singleton`** — its only retained
  state is `surfaceRequests`, which must not outlive the screen, and
  `ProcessCameraProvider` is already a process singleton.
- **Milestone 2 steps 1–3 DONE** — `:core:posemath` (geometry + One Euro
  smoothing, 55 tests) and `:core:exercise` (`SquatSignals`, `SquatRepCounter`,
  30 tests). All pure Kotlin, 85 tests total. **Nothing consumes any of it yet:**
  the camera path still runs raw, unsmoothed landmarks straight into
  `WorkoutUiState` and counts nothing. Wiring happens at step 4.
- **Next: Milestone 2 step 4** — wire `PoseSmoother` -> `squatSignals()` ->
  `SquatRepCounter` into `WorkoutViewModel` and show a live rep counter. Also the
  moment to do the two things already flagged: move `ImageAnalysis` off the main
  executor, and defer the `PoseOverlay` state read into the `Canvas` draw lambda.
- Deliberately deferred until they have a job to do: the `UiState` -> `State`
  rename and `Action`/`Event` files (the workout screen has no actions or events
  yet), `:core:ui` (`UiText`/`ObserveAsEvents` — no chosen text, no events yet),
  and Navigation 3 (one screen, nothing to navigate to; it lands with the
  post-workout summary at the end of Milestone 2).
- The camera is hardcoded to `DEFAULT_FRONT_CAMERA`, but the recommended setup is
  45° with the phone propped 2–3 m away — a **back**-camera position. Pull the
  camera-flip backlog item forward before testing step 4 on real squats.
- KSP no longer tracks the Kotlin version: the `2.2.21-2.0.5` scheme ended at
  `2.3.0`, which is plain semver (latest `2.3.11` as of 2026-08-06). There is no
  "find the KSP build matching Kotlin 2.4.10" step.
- Parked, to port into the library convention plugin when they earn their keep
  (NIA has them): module-path `resourcePrefix`, default
  `testInstrumentationRunner`, `animationsDisabled`,
  `disableUnnecessaryAndroidTests`. Also available if perf work needs it: Compose
  compiler metrics/reports and a stability-configuration file.

## Roadmap

1. **Camera + skeleton overlay** — the risky part first: CameraX preview with live
   ML Kit landmarks drawn on top. Deliverable: demo GIF for the README.
2. **One exercise done well: the squat** — joint angles + landmark smoothing
   (moving average / One Euro filter), rep state machine
   (`STANDING → DESCENDING → BOTTOM → ASCENDING`) with angle hysteresis, form
   heuristics (depth, knee tracking, back angle) + TTS feedback.
3. **Local persistence & app shell** — Room history, Navigation 3 flows, stats
   screen, design system module.
4. **Backend & sync** — Spring Boot API (auth or device-ID first), Retrofit sync,
   leaderboard.
5. **Second/third exercise + Play release** — push-ups, bicep curls; privacy policy
   (camera!), data-safety form, release signing, crash reporting.

Backlog (not scheduled): camera flip (front/back — overlay `mirrored` flag must
flip with it) and zoom control incl. 0.5x ultra-wide via
`cameraControl.setZoomRatio` (ultra-wide usually back-camera only; helps in small
rooms where the phone can't be placed far enough away).

Scope guard: **2–3 exercises max, done well.** Form-correction heuristics are the
hard part, not ML Kit — landmarks jitter (smoothing + hysteresis are
non-negotiable) and degrade with bad lighting/clothing/angles, so UX must guide
phone placement.

## Conventions

- Conventional commits, matching existing history: `chore:`, `docs:`, `fix(client):`, …
