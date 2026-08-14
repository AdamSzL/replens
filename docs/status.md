# Status

Where RepLens actually is, what has been validated on a device, and what is next.
**This is the file that changes every session** — CLAUDE.md holds the rules, which
should not churn just because a milestone landed.

Last updated 2026-08-14.

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
  What is not built: any screen that reads it back — `workout(id)` has no caller,
  and `workouts()` does not exist because its shape is the history screen's
  question to answer.
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
- **Next:** the rest of the workout summary screen — `recordSet` returning the
  workout id, `WorkoutEvent` + `ObserveAsEvents`, then the screen itself. Then
  `:feature:history`. 236 unit tests.

## Roadmap

1. **Camera + skeleton overlay** — done.
2. **The squat** — angles, smoothing, rep state machine, the whole voice channel
   and the first two form cues done; **the live geometric rules (valgus, forward
   lean, heel lift) are what remains, and they need footage before code**.
3. **Local persistence & app shell** — Room history and the Nav 3 wiring done; the
   summary, history and stats screens remain.
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
  the set summary. This was the prerequisite for "depth 88%, up from 81%"; what is
  still missing is somewhere to *persist* them, which arrives with history.
  `repsAtDepth` is recomputed from the list every frame rather than incremented,
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

- Remembering the camera choice and zoom across launches (needs DataStore, which
  the setup/settings work will bring anyway).
- Per-category cue switches (same DataStore).
- Widening the fixture CSVs so framing is testable against real footage.
- `TtsSpeaker` has no `shutdown()` and holds its engine for the life of the
  process — defensible for a `@Singleton`, but a choice rather than an oversight.
- `MainActivity`'s permission gate hardcodes its two strings and uses a raw M3
  `Button`; `CameraPermissionGate` also paints no background, so what shows behind
  it is `windowBackground` from `themes.xml` — light in both themes today. All of
  it lands with the real permission flow.
- Parked for the library convention plugin when they earn their keep:
  `resourcePrefix`, default `testInstrumentationRunner`, `animationsDisabled`,
  `disableUnnecessaryAndroidTests`.

## Reference

- Test footage lives outside the repo in `~/replens-recordings/`.
