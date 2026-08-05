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
- Domain (planned): `replens.app`, future API host `api.replens.app`

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

## Server tech stack

- Kotlin, Spring Boot, Gradle Kotlin DSL, JDK 21
- Spring Web, Spring Data JPA, PostgreSQL, Validation
- Keep v1 thin: auth, workout sync, stats, leaderboard. No social/streaming.

## Architecture

*(to be expanded as modules take shape)*

## Current status (2026-08-05)

- Client scaffold committed and building: AGP 9.3.1, Kotlin 2.4.10, Gradle 9.6.1,
  SDK 37, Java 21, R8 + resource shrinking on for release. Server not started.
- **Milestone 1 (camera + skeleton overlay): DONE and validated on a real device.**
  The spike lives unstructured in `client/app/` by design (`MainActivity.kt`
  permission gate, `WorkoutScreen.kt` CameraX + ML Kit pipeline, `PoseOverlay.kt`
  Canvas skeleton) — do not "clean it up" in place; it gets extracted in the
  structural pass.
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
- **Next step: structural pass** — extract the spike into multi-module
  architecture (`build-logic` convention plugins, Hilt, Navigation 3,
  `:core:*` / `:feature:workout`) before writing Milestone 2 logic (smoothing +
  rep state machine as pure, unit-tested Kotlin).

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
