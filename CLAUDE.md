# RepLens

AI-powered personal trainer for Android. Uses the phone camera and on-device ML
(Google ML Kit Pose Detection) to analyze exercise form in real time: live pose
tracking with a skeletal overlay, form correction via TTS + visual feedback, and
automatic rep counting that only counts technically correct reps. Workout history is
stored locally (Room) and synced to a backend for stats and leaderboards.
*"Every rep, seen."*

**Goals:** portfolio "wow" piece first, real Google Play release second. Traction is
a bonus — the shipping experience (Play Console, signing, privacy policy, crash
reporting) is itself the payoff.

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
- Hilt (DI), Retrofit + kotlinx.serialization, Room (local history)
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

### Architecture decisions (client)

- **Pure-Kotlin core:** domain models, smoothing, angle math, and the rep state
  machine live in `replens.jvm.library` modules (no Android imports — enforced by
  compilation). Android-bound code (CameraX/ML Kit, Compose, Room, TTS) wraps
  around them. ML Kit types must not leak past `:core:pose` — it maps them into
  our own landmark/pose data classes at the boundary.
- **Feature navigation = callback hoisting (no api/impl split).** Features never
  navigate to each other and never depend on each other. Each feature exposes its
  screen with navigation lambdas (e.g. `WorkoutScreen(onWorkoutFinished: (Id) -> Unit)`)
  and `:app`'s Navigation 3 host is the only place that maps callbacks to
  destinations. Decided 2026-08-05: api/impl modules are deliberate overkill for a
  solo ~4-feature app — revisit only if build times hurt or features need to embed
  each other's UI.
- **Every feature screen follows the same quartet: `Screen` + `ViewModel` +
  `UiState` + `Action`/`Event`.** The ViewModel exposes `state: StateFlow<UiState>`
  and one-shot `events: Flow<Event>` (channel-backed), and takes user intent
  through `onAction(action)`. Events are for things that must fire exactly once —
  navigation, and TTS cues especially (a cue must not replay on recomposition or
  rotation). Implement it as a per-feature convention, **not** a generic
  `BaseViewModel<S, A, E>`; inheritance-based MVI frameworks are where this
  pattern goes to die. Adopt alongside Hilt + Navigation 3, while `:feature:workout`
  is still the only feature.
- Convention plugins live in `client/build-logic/` (included build):
  `replens.android.application`, `replens.android.library`,
  `replens.android.compose` (additive: compose flag + BOM + tooling — modules
  without UI must not apply it), `replens.jvm.library` (pure Kotlin, no AGP —
  must set kotlinc's jvmTarget explicitly since there's no built-in-Kotlin
  alignment). Planned when first needed: `replens.hilt`. AGP 9 plugin-code gotchas: `CommonExtension` has no generic type
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
:app                MainActivity: camera permission gate -> WorkoutScreen. Nothing else.
:feature:workout    WorkoutScreen (public) / WorkoutContent (private, pure render),
                    WorkoutViewModel (owns the session), WorkoutUiState, PoseOverlay.
:core:pose          PoseCameraDataSource: CameraX + ML Kit behind Flow<PoseFrame>,
                    plus surfaceRequests: StateFlow<SurfaceRequest?>. PoseMapper is
                    internal — the ML Kit boundary.
:core:designsystem  RepLensTheme + the app's Compose gateway (see below).
:core:model         Landmark, LandmarkType, BodyPose, PoseFrame. Pure Kotlin.
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
threshold by the user's own proportions** (shoulder width / femur length in
pixels) so rules survive different body sizes and camera distances. Tune against
`~/replens-recordings/`.

ML Kit's own [pose classification guide](https://developers.google.com/ml-kit/vision/pose-detection/classifying-poses)
(k-NN over pairwise-joint-distance embeddings, a few hundred labelled images per
exercise) was evaluated and **not adopted as the primary approach**: it tells you
*which pose* you're in, not *what's wrong with it*, so form cues would need
labelled bad-form classes per fault per exercise, and its output isn't
explainable enough to speak. Two things taken from it anyway: **normalize poses
to constant torso size and vertical torso orientation** before computing
anything (that's the body-proportion normalization above, made concrete), and its
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

## Testing & CI (planned)

Good test coverage is an explicit goal of this project, not an afterthought —
there is no delivery deadline, so experimenting here is worth the time.

**Unit tests carry the weight, and the architecture is built for it.** The
interesting logic — angle math, One Euro smoothing, the rep state machine, form
rules — lives in `replens.jvm.library` modules with no Android dependencies, so
it runs on plain JUnit in milliseconds. Rep detection should be tested against
known-good sequences derived from `~/replens-recordings/` (e.g. "this frame
sequence contains exactly 5 reps").

**Compose Preview Screenshot Testing** (`com.android.compose.screenshot`) — to
try once there is UI worth pinning; best fit is `:core:designsystem` components
and `WorkoutContent` rendered against fixed `WorkoutUiState` values (it takes
plain state, which is exactly why it's previewable).

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
  MainActivity only. Builds green (debug + release); **not yet re-verified on a
  device after the extraction** — do that before building on top of it.
- **Next: Hilt** (`replens.hilt` convention plugin + KSP; `WorkoutViewModel`'s
  constructor already has the right shape — its `viewModelFactory` companion goes
  away), **then Navigation 3** (the nav host is where `WorkoutScreen`'s hoisted
  callbacks get wired), then Milestone 2 logic: smoothing (One Euro filter, needs
  `PoseFrame.timestampMillis`) and the rep state machine as pure, unit-tested
  Kotlin in `replens.jvm.library` modules.
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
