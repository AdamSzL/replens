# Status

Where RepLens actually is, what has been validated on a device, and what is next.
**This is the file that changes every session** — CLAUDE.md holds the rules, which
should not churn just because a milestone landed.

Last updated 2026-08-17.

## Milestones

- **Milestone 1 (camera + overlay): done**, validated on device.
- **Milestone 2 steps 1–4: done**, validated on device — 8 reps performed, 8
  counted, clean phase transitions, no phantom reps from walking to/from the
  phone. Hilt is wired throughout.
- **Zoom stops and front/back flip: done**, validated on device 2026-08-08
  (`squats_camera_flip.mp4`): 7 performed, 7 counted — 4 on the back camera, 3 on
  the front. Across the flip the zoom range updated 0.5 → 0.9 and the selection
  reset to 1x, the rep count survived, mirroring was correct on both lenses, and
  carrying the phone produced no phantom reps. 89 unit tests.
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
- **The first two form cues: done** (2026-08-13), **not yet heard on device.**
  `FormFault.SHALLOW_REP` and `ABANDONED_DESCENT`, both decided *after* a rep off
  a `RepUpdate` the counter already produced, so neither needed a new threshold or
  a latency budget. The shallow arm lives in the gap between `bottomEnterAngle`
  (115°, what counts) and `goodDepthAngle` (95°, what is parallel) — grading it
  against the counting threshold instead would make the fault unreachable.
  Arbitration turned out to be one elvis plus a hold rather than a priority
  system; see *Voice feedback*. Valgus, forward lean and heel lift stay parked —
  they need research and tuning against footage, and two of them need the vertical
  reference gated on the setup check.
- **Milestone 3, persistence: validated on device 2026-08-14.** `:core:database`
  (Room 3), `:core:data` (repository, mappers, the 60-minute gap rule) and the
  `:feature:workout` wiring all exist, and a set now survives being read back out
  of the file. Confirmed against real sets: sets attach to one workout under the
  gap rule with `endedAt` and `isDirty` tracking each write, `repCount` matches
  the rep rows, `repsAtDepth` matches a recomputation, `rep_index` is 1-based per
  set, foreign keys are clean, and the timestamps are real epoch millis rather
  than a frame clock leaking through. The shallow arm fired on real reps for the
  first time — two at 101.7° and 104.4° counted and graded as not-at-depth.
  **The case worth having tested is the second process:** a set recorded after a
  force-stop and relaunch attached to a workout written by the *previous* process,
  10 minutes earlier, so the gap rule reads committed data rather than only its
  own. Cancel and a Finish with no movement each wrote nothing, as designed.
  What was not built at the time: any screen that reads it back — `workout(id)`
  had no caller, and `workouts()` did not exist because its shape was the history
  screen's question to answer. Both landed since, with the summary and the list.
  **`BundledSQLiteDriver` means Android Studio's Database Inspector may show
  nothing** — it hooks framework SQLite connections, and this driver compiles its
  own. Pull the file instead (`adb exec-out run-as com.replens.app cat
  databases/replens.db`), taking `-wal` and `-shm` too or recent writes are
  missing. It also leaves a `replens.db.lck` beside them, which a wipe must delete.
- **Navigation 3 wired 2026-08-15** on stable 1.1.6, on branch
  `feat/workout-summary`. One destination so far, so behavior is unchanged; what
  it buys is that `:feature:workout` is now reachable only through `WorkoutRoute`
  and `workoutEntries()`, with `WorkoutRoot` internal. No `Navigator` object —
  `rememberNavBackStack` already survives process death, so the persistence
  gotcha CLAUDE.md had filed as "fix before release" never got built.
- **The workout summary screen: done and used on device 2026-08-16.** The first
  screen that reads persistence back, and the first one built entirely from the
  five-file convention — `:feature:history` now owns `WorkoutSummaryRoute`, and
  `:app` wires `workoutEntries(navigateToSummary = …)` to it. It shows session
  totals, a **depth chart plotting every rep against the parallel threshold**, and
  a chronological set list with rest drawn as the gap between sets.
  Three modules came out of building it, each for a reason worth keeping.
  **`:core:text`** because `UiText` sitting in `:core:ui` made the
  `@Composable`-overload landmine a matter of discipline; splitting the two
  resolvers across modules makes it impossible instead. **`:core:testing`**
  because `FakeWorkoutRepository`, `FakeClock` and `MainDispatcherRule` were
  about to be copied into a second feature. **`:feature:history`** because the
  summary is a workout-boundary screen, not a workout-screen mode.
  Three things settled by measurement rather than taste: the chart's axis, after
  the guessed floor of 75° turned out to be wrong (the author's reps land at
  50–73°, so the axis fits and snaps to 5° now); `heading` at 20sp, added when
  the top bar was found rendering a screen title at caption size; and `UiText` in
  `compose-stability.conf`, which the compiler reported `runtime` and which was
  dragging three UiModels down with it.
  **Known and deliberate:** the totals and the chart are squat-shaped in one
  respect only — the y-axis. See the backlog.
- **Reviewed, and five fixes landed 2026-08-16/17.** Three passes over the summary
  branch — Copilot CLI on the PR, a `DepthChart.kt` deep dive, and an ultrareview —
  produced 16 findings between them; five were real.
  The one actual defect: the depth chart **drops sets that reached no rep, then
  numbered the survivors**, so a workout whose middle set was all abandoned
  descents drew `1, 2` under a list reading Set 1, Set 2, Set 3 — and TalkBack said
  the same wrong number. `DepthChartSetUiModel` carries the pre-filter position
  now. The set *count* left the description entirely rather than being corrected to
  2 or 3: the totals card is announced first and already carries it, which the
  mapper's own KDoc had already said it must not repeat. That also deleted a plural.
  The rest were smaller: the recorded workout id travels in a `Deferred` instead of
  a field a new set could clear; two lines were assembled with `+ " · " +` and are
  single format resources now (the rule is in CLAUDE.md); one KDoc named the
  counting threshold where the code uses the scoring one.
  **Two rejections worth not re-deriving, because a future pass will raise them
  again.** A `LazyColumn` for the set list would *crash* — `LoadedWorkout` is a
  `Column` with `verticalScroll`, and a nested lazy list throws on infinite height
  constraints. And the summary screen has **no per-frame draw path**, so allocation
  findings about its `Canvas` are false: `verticalScroll` translates a layer rather
  than re-running draw lambdas, and `rememberTextMeasurer` caches internally. The
  30 fps budget in CLAUDE.md is the pose pipeline, not this screen.
- **The history list: done 2026-08-17** (PR #20, nine commits, 56 files). The
  summary is now reachable from somewhere other than finishing a set — an overlay
  button on the camera screen, shown only while the session is `Idle`.
  `WorkoutDao.workouts()` is one grouped query returning `WorkoutTotals`, and the
  row says *when* rather than *what date*: Today / Yesterday / a weekday for the
  last week / a date / a date with its year. Both screens moved into a package
  each (`ui/history/`, `ui/summary/`) with `ui/common/` for the duration
  formatter. 304 unit tests.
- **Reviewed by three agents, four fixes landed 2026-08-17.** Nine findings: the
  year missing from older dates (real, and the only behavior bug), `today`
  recomputed per row, every DAO `ORDER BY` made total, and `Loaded` renamed to
  `Content`. Three were rejected on inspection — the depth chart already filters
  empty sets, `FakeWorkoutRepository`'s `replay = 1` is deliberate so `Loading` is
  testable, and memoizing `is24HourFormat` would have frozen a setting nothing
  invalidates (see CLAUDE.md). One was deferred to #21.
- **Next:** unstarted. The nearest candidates are the app shell (#17 — bottom
  nav, the Home naming question, a top bar) and the live geometric form rules,
  which need footage before code.

## Roadmap

1. **Camera + skeleton overlay** — done.
2. **The squat** — angles, smoothing, rep state machine, the whole voice channel
   and the first two form cues done; **the live geometric rules (valgus, forward
   lean, heel lift) are what remains, and they need footage before code**.
3. **Local persistence & app shell** — Room history, the Nav 3 wiring, the workout
   summary and the history list done; the shell itself (#17) and stats remain.
4. **Backend & sync** — Ktor API (auth or device-ID first), leaderboard.
5. **Second/third exercise + Play release** — push-ups, bicep curls; privacy
   policy (camera!), data-safety form, signing, crash reporting.

Scope guard, which is a rule rather than a plan and so also lives in CLAUDE.md:
**2–3 exercises max, done well.**

## Already true, and easy to forget

- The flip **rebinds the same use cases in place** rather than restarting the
  flow, so the ML Kit detector is never rebuilt and its model never reloads. A
  lens change is CameraX's business alone.
- **The completed `Rep`s are collected now** (2026-08-09), in a `reps` list the
  ViewModel clears on `startSet`, so `deepestAngle` and the descent/ascent timings
  survive the set. `repsAtDepth` is the first thing built on them and is spoken in
  the set summary. This was the prerequisite for "depth 88%, up from 81%", and
  since 2026-08-14 they are persisted as rows — which is what the summary's depth
  chart reads. `repsAtDepth` is recomputed from the list every frame rather than incremented,
  so the list is the only thing that can be wrong — a derived value cannot drift
  the way a counter maintained in three places can.
- **`:feature:workout` exposes exactly `WorkoutRoute` and `workoutEntries()`**;
  `WorkoutRoot`, the ViewModel, state and actions are all `internal`, so
  `:core:pose` and `:core:exercise` are `implementation`. The `RepPhase` text is a
  debug affordance; removing the
  `Text` alone won't cut recompositions, since `phase` living in `WorkoutState` is
  what drives them. The real cleanup is dropping it from state once cues are
  events.

## Backlog

**Moved to [GitHub issues](https://github.com/AdamSzL/replens/issues).** It was a
duplicate of them and the issues were the fuller copy, so keeping both meant
maintaining the worse one. This file keeps what *happened*; issues keep what is
next.

## Reference

- Test footage lives outside the repo in `~/replens-recordings/`.
