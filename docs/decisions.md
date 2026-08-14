# Decisions

Why RepLens is built the way it is — the arguments, the measurements, and the
options that were rejected.

**CLAUDE.md holds the conclusions**, each one carrying what it ruled out, because
that is what stops a decision being re-litigated by accident. This file holds the
reasoning behind them: read it when you want to *reopen* a decision, not when you
want to follow one.

Entries are dated by when the call was made. A dated argument that later turned
out wrong is corrected in place and says so — the point is to be able to trust
what is here, not to keep a diary.

---

## The schema

*Decided 2026-08-14, before any code was written.*

### `ExerciseSet`, not `WorkoutSet` or `RepSet`

`WorkoutSet` parses as "the set belonging to the workout", which read as *the whole
workout* to a fresh pair of eyes — exactly the ambiguity that had already made
`session` mean two things in the ViewModel. `ExerciseSet` names the row by the
column that actually discriminates it (`exercise`), and unlike `RepSet` it cannot
be falsified by a future exercise that is not rep-based (a plank, a carry).

Domain names avoid bare `Set` because it shadows `kotlin.collections.Set`. Table
names are under no such constraint, so the schema keeps the clean plurals and only
the Kotlin type pays the extra word.

### `Rep.index` — removed, then reverted the same day

The column is redundant *live* (`SquatRepCounter.repCount` is the same number,
which is why the ViewModel passes `repCount` to `CueEngine` separately) and
redundant *in a list* (position says it). It earns its place at the persistence
boundary, and only there:

- Re-deriving it on write makes **"the list is complete and in order" a silent
  invariant of the mapper** rather than data.
- The entity → domain → entity round trip stops being lossless.
- Every reader wanting to *name* a rep pays an off-by-one —
  `reps.withIndex().minBy { it.value.deepestAngle }` then `+ 1`, at a user-facing
  call site, for a feature that is actually planned ("show me the rep you got
  wrong").

The argument for removing it was that `Rep` should carry measurements only, with
the fixtures passing `index = 1` as noise offered as evidence. **That evidence does
not hold** — the same fixtures pass `descent = 500.milliseconds` as noise too, in
tests that only care about the angle. `ORDER BY id` would also have been sound,
since a completed set is written once in one transaction and never changed, so the
column was never load-bearing for *ordering* either. The round trip is what decides
it.

### Why the 60-minute gap, and no Finish button

The common real behavior at the end of a workout is **pressing nothing at all** —
you finish your last set, pick the phone up, close the app. Any rule that depends
on a press therefore gets the usual case wrong.

Inferring the boundary when a set *starts* has three properties a close event does
not: an abandoned workout is already correct (`endedAt` is just its last set's),
killing the app mid-workout is safe, and pressing Done then changing your mind five
minutes later still reads as one workout where an explicit close would have
produced two.

An explicit **Finish workout** stays available later as a purely additive flag that
makes the gap rule skip that workout. Add it only with evidence that forcing a
boundary is wanted.

---

## The landmark stream

*Rejected 2026-08-14. Measured, not guessed.*

33 landmarks × 4 floats = 528 bytes/frame, ~81 frames for a 3 s rep ≈ **42 KB per
rep**, ~500 KB per set, **~300 MB per year**. An earlier version of CLAUDE.md
called this "kilobytes", which was optimistic by three orders of magnitude.
Quantizing x/y to 16-bit and dropping z and likelihood still leaves ~80 MB/year.

Rejected for now on two grounds, of which the second is the stronger: you would be
designing a serialization format with **no reader to validate it against**, and
retention — when do old streams get deleted? — is a whole design problem of its
own.

`reps` has a stable id, so a `rep_frames` table is additive whenever the feature
arrives.

**The planned first version is in-memory only**: replay the reps of the workout you
just did, on the summary screen, nothing persisted. That is a genuine stepping
stone rather than a dead end, because the compaction it requires — one `FloatArray`
per rep, ~21 KB, instead of retaining ~130k live `Landmark` objects and turning
cheap young-gen churn into retained old-gen pressure — **is** the serialization
format.

What it does not do is discharge the risk the roadmap assigns to replay: it is
listed as an answer to **cue novelty decay**, and a replay that vanishes on app
close cannot be part of "history worth browsing".

---

## Rep timings

*`Rep` reshaped 2026-08-14; the split point corrected the same day after the first
device data.*

### Durations, not timestamps

`Rep` used to carry `startedAtMillis`, `bottomAtMillis` and `completedAtMillis`.
They existed **only to be subtracted from each other** — the durations were already
there as derived properties, and nothing outside the counter and its test fixtures
read the raw values.

Storing durations means the frame clock never escapes `SquatRepCounter`, so a
loaded `Rep` and a live `Rep` cannot mean different things by the same field name.
The ambiguity stops existing rather than getting documented.

What it gives up, neither wanted today: *when* within a set a rep happened
(ordering is `index`), and per-gap rest between reps — aggregate rest is still free,
being set duration minus the sum of rep durations. Both are additive if they ever
matter.

### Keeping columns nothing reads

The justification is cost asymmetry rather than a planned feature: 16 bytes a rep,
~96 KB a year, produced by a subtraction the counter already makes for
`minRepDurationMillis`, and **unrecoverable after the fact** — add the columns in
six months and every earlier rep is a permanent hole.

That is the same "we have no reader yet" argument the landmark stream lost, four
orders of magnitude cheaper (96 KB against 300 MB a year), which is why it comes
out the other way. Two columns also yield three metrics: rep duration, time under
tension (their sum), and rest (set duration minus that sum). And history and stats
showing only angles would be a thin product — tempo is a real lifting metric, and
*"your descent slowed 40% over the last three reps"* is something a mirror cannot
tell you, which is on-brand for the cue-decay problem.

If they are still unread when history and stats ship, that is evidence to drop them
— deleting a column nothing reads is a far easier call than adding one you wish you
had.

### The split point, corrected by the first real data

The original split was at threshold crossings: `descent` from `standingExit` (160°)
to `bottomEnter` (115°), `ascent` from there to `standingEnter` (168°). That made
`descent` a fixed 45° window — a genuine speed measure — while `ascent` absorbed
everything else, including the bottom half of the descent and the turnaround.

So its length tracked **depth**, not tempo. The first seven real reps measured
ascent/descent at **3.7–4.9x at ~60°** against **1.8–1.9x at ~102°**, at the same
cadence. Depth leaking into the one metric whose job is tempo would have made
*"your descent slowed 40%"* report reps that merely went deeper.

Splitting at the minimum-angle frame is threshold-independent, keeps the sum (and
therefore rep duration, time under tension and rest) identical, and cost three lines
— `SquatRepCounter` already tracked the minimum, only its timestamp was thrown away.
`bottomAtMillis` disappeared with it.

Confirmed on device the same day: two continuous reps in one set **20° apart in
depth** (39.8° and 60.2°) split 608/608 and 509/541 — ratios of 1.00 and 1.06,
where the old rule would have charged the deeper one a much longer ascent for
distance alone.

**Two residual biases, both accepted.** The rep still *starts* at the 160° crossing
rather than at first movement, so a fast descent starts measuring ~8° late; and it
ends at 168°, so the window is lopsided by the hysteresis band. Both apply equally
to every rep, so comparisons between reps hold — comparisons against anything
external do not.

### The pause problem, and why it is not fixed

During a hold at the bottom the angle wobbles ±0.5–1°, so exactly one frame is
lowest and it sits at a **random position inside the pause**. First-vs-last minimum
does not help: once the signal is real there is only one minimum frame.

Measured 2026-08-14: three reps done continuously split at ratios 1.21/1.33/1.37
(13% spread), while three the lifter paused on split at 0.76/1.09/0.55 (**98%**).
Well-defined for a continuous rep, arbitrary for a paused one.

The robust version treats the bottom as a **band** rather than a point — frames
within ~3° of the minimum, `descent` ending at the first and `ascent` starting
after the last — which makes the pause a third duration, keeps the sum equal to the
rep, and is a real metric in its own right (paused squats are a technique; an
involuntary pause means you are grinding).

**Deliberately not built**, on the primary-key argument: until release the whole
population of this schema is one phone, so this stays a wipe rather than a
migration, and designing a three-phase tempo metric with no reader is the mistake
the landmark stream lost on.

It is also **not a device question** — a lifter cannot feel where 160° is, so no
amount of careful squatting validates it. The tool is a fixture: one recorded clip
with a deliberate pause gives the angle trace, and where the minimum lands is then
something to read off a plot. Another reason to widen the CSVs.

**Why the split itself could not wait for a reader**, when the band version can:
the turnaround timestamp is not stored, so data written under the old split cannot
be recomposed into the new one. Exactly the argument that kept the columns in the
first place, applied to their definition.

---

## Time types

*Decided 2026-08-14.*

`kotlin.time.Instant` and `kotlin.time.Clock` moved into the stdlib and are stable
on Kotlin 2.4.10 — verified directly: no opt-in, no warning. This supersedes an
earlier note recommending `java.time` on the grounds that minSdk 26 needs no
desugaring; that was answering "java.time or kotlinx-datetime", and the stdlib is a
third option that beats both.

The deciding case is the workout gap rule, since `Instant - Instant` is a
`kotlin.time.Duration` directly:

```kotlin
now - lastEndedAt < WORKOUT_GAP                            // kotlin.time
Duration.between(a, b).toKotlinDuration() < WORKOUT_GAP    // java.time
```

`Rep` already carries `kotlin.time.Duration`, so `java.time.Instant` would put **two
`Duration` types in one module** and charge a conversion at exactly the place the
rule lives.

Smaller wins: `kotlin.time.Clock` is an interface with `Clock.System`, so faking
`now()` in a test needs no `Clock.fixed(instant, zone)`; and there is no minSdk
caveat left to remember.

---

## Room 3, not Room 2

*Decided 2026-08-14, six weeks after `androidx.room3:room3-runtime` 3.0.1 went
stable.*

Not adventurism — four reasons, none of which is novelty:

- **Coroutine-first is mandatory** in Room 3: every DAO function is `suspend` or
  returns `Flow`, there is no Executor support, and `InvalidationTracker` is
  Flow-based. There is not an `Executor` anywhere in this codebase.
- **KSP required, Kotlin-only codegen.** KSP is already on the classpath for Hilt;
  there is no KAPT to avoid.
- **The main migration pain does not apply.** `@TypeConverter` became
  `@ColumnTypeConverter` — and because conversion lives in the mapper, we need no
  converters at all.
- **Greenfield makes it free**, exactly as with Ktor Client. New Maven group, so
  there is nothing to migrate and being wrong costs a rename.

The honest cost: search results and snippets are Room 2 with different imports and
API shapes (`withWriteTransaction`, not `runInTransaction`; `useReaderConnection`,
not `query`).

### Why Now in Android's `core:database` looks different

It `api`s `core:model` because it maps *inside* the database module
(`TopicEntity.asExternalModel()`). Our mappers live in `:core:data` instead,
because our domain models sit alongside exercise *knowledge* — depending on that
here would drag squat math onto the database module's classpath to convert three
longs.

And its Room setup is a convention plugin applied to exactly one module, because
build-logic is part of what that repo demonstrates. **The rule here: a convention
plugin earns its keep at the second call site.** The trigger would be
`:core:database` splitting.

---

## Primary keys

*Parked 2026-08-14. The decision point is named rather than settled.*

**The question that settles it: *does the sync API accept client-supplied ids?***
Answer it when sync is **designed**, before it is written, alongside the
anonymous→account path.

- **Yes** → switch to client-generated UUIDs (`kotlin.uuid.Uuid`, now in the
  stdlib — verify its stability the way `kotlin.time.Instant` was). `serverId`
  disappears, push becomes an idempotent upsert, and claiming a guest's history is
  the server attaching a `userId` to rows that already have global identity.
  Locally it also removes the `id`-shaped awkwardness: `recordSet` stops needing a
  return value because the caller already knows the id, `WorkoutDao`'s `set`/`reps`
  callbacks collapse into plain parameters (they exist *only* because each id is
  unknown until the insert above returns), and write and read models become the
  same type.
- **No** → keep `serverId` and the mapping, and autoincrement was right all along.

**Why not now:** the app ships at milestone 5, *after* the backend at milestone 4,
so until release the entire population of this schema is one phone. Switching keys
stays a wipe rather than a data migration right up to the moment it ships. Adopting
UUIDs today would mean committing to a sync design that does not exist yet.

`isDirty` and `updatedAt` stay either way — they are about what to push and how to
resolve conflicts, not about identity. Nothing orders by `id` already (reps by
`rep_index`, sets and workouts by their `Instant`s), which was decided for sync
reasons and happens to keep this door open.

Collisions are not a consideration: 122 random bits, and a colliding insert would
be rejected by the primary key rather than corrupt anything. The real risk is a
non-cryptographic random source.

---

## No `:shared` module

*Reconsidered 2026-08-11 and kept.*

A root `settings.gradle.kts` with a
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

---

## Ktor, not Spring Boot

*Decided 2026-08-11, before a line was written.*

Spring's value is amortizing complexity across a team and many modules; this API
is ~12–15 endpoints written by one person, which is exactly the regime where the
amortization
never happens and only the tax is paid: ~2–4 s startup against a few hundred ms
(cold-start latency on anything that scales to zero), roughly 3–4x the idle memory,
`allOpen`/`noArg` compiler plugins to make JPA entities work, and Jackson as the
well-trodden path — which would put a *different* serializer on each side of the
wire for no reason. Ktor is also the more hand-built of the two, which is the
stated reason this backend exists at all.

Exposed's DSL is about as short as JPA repositories at this schema size, and the
SQL stays visible.

**Spring Security is the one real loss, and it is not close** — nothing in Ktor
matches it. It was priced before choosing rather than discovered afterwards:
verifying a Google ID token means fetching the JWKS and checking the signature
plus `iss`/`aud`/`exp`, which is the `jwt {}` block and ~40 lines against stable,
well-documented behavior. An evening, not a wall — and the payoff is understanding
our own auth.

---

## Ktor Client, not Retrofit

*Settled 2026-08-11. Previously parked as "decide when `:core:network` is built".*

The margin is small and worth stating honestly: both sit on OkHttp, so connection
pooling, HTTP/2 and the cache are identical either way, and Retrofit's annotated
interface is the nicer thing to read. Three points decided it:

- **Token refresh.** Ktor's `Auth` plugin
  (`bearer { loadTokens; refreshTokens }`) retries the original request after a
  401 and serializes concurrent refreshes. Retrofit means an OkHttp
  `Authenticator` written by hand, where five requests 401-ing at once all firing
  a refresh is a classic thing to ship broken. With optional login plus background
  sync that is a Tuesday, not an edge case.
- **Error bodies.** Per-endpoint sealed errors (see *Result and errors*) need the
  failure payload. Retrofit hands back `errorBody(): ResponseBody?` to deserialize
  by hand on a path separate from the success one; Ktor's `body<T>()` doesn't care
  about the status.
- **One HTTP library across both halves**, now that the server is Ktor — one
  serialization setup and one mental model for the person writing a route and its
  caller in the same sitting.

The `safeApiCall`-style wrapper is real but smaller than it sounds: ~30 lines in
Ktor against a `CallAdapter.Factory` or `Response<T>` at every call site. **This
would not have been worth a migration** — greenfield is what makes it free.

---

## Design system

*Built 2026-08-08. Written by a solo dev with no designer, which shapes the
tactics: **borrow a palette** rather than invent one, and hold to few values.
Amateur UI comes from too many values, not the wrong ones.*

Color and typography have RepLens names and RepLens values;
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

The visual load here is unusually low anyway — the workout screen is invisible
during a set, and history/stats/summary are lists and numbers.

---

## Squat thresholds

*Set 2026-08-08 from the literature; validated against eight fixtures without
tuning.*

The fixtures validate the boundary from both sides: the descending sweep counts
its 113° rep and rejects its 128° one, and ten deliberately marginal reps at
124–141° produce zero rather than an occasional phantom.

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

---

## Smoothing: Kalman rejected

*Simulated 2026-08-07.*

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

---

## What ML Kit does

*Observed on device 2026-08-08 and 2026-08-09.*

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
  `maxMissingFrames` already handles, so it needed no new state machine. The gate
  is applied in `PoseFrame.squatDepthAngle`, not in the ViewModel: it is a rule,
  not wiring, and the test that matters asserts that a too-close frame has a
  perfectly good knee angle and is refused anyway — which is the shape of the bug,
  since a hallucinated skeleton reads fine too.
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

---

## Framing vs SetupCheck

*Both thresholds measured 2026-08-08; the 0.32 analysis corrected on device
2026-08-11.*

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

---

## Abandoned descents

*Observed and measured 2026-08-14.*

- **An abandoned descent is a two-frame trigger, and walking clears it.** One
  frame below `standingExit` (160°) then one above `standingEnter` (168°) — no
  duration, no minimum depth, nothing like the full sweep plus
  `minRepDurationMillis` a counted rep must earn. So the walk back to the phone
  can fire "all the way down" at the moment the user is finished, observed
  2026-08-14 at 159.2°, which is 0.8° past the threshold and not a squat attempt
  by any reading.
  **Measured before acting, and the measurement said don't:** across five sets,
  the three normal ones scored 1, 0 and 0 abandoned descents while two sets of
  deliberately walking about scored 9 and 4. It does not fire in normal use.
  What decides it is that **this data is filterable at read time** — calibration
  asks "how deep did the last 20 attempts get", and `deepestAbandonedAngle < 150`
  is a `WHERE` clause applied whenever that feature is designed, against rows that
  kept everything. Exactly the opposite of the tempo split above, where the
  turnaround timestamp is genuinely unrecoverable and inaction lost it forever.
  Same shape of question, opposite answer, for a reason worth reusing.
  A 150° report gate would not have been enough anyway: the walking sets reached
  145.9°, inside it. The escalation if it ever annoys is **gating form cues on
  `SetupCheck`**, which costs no new threshold at all — and is safe mid-rep, since
  torso fraction falls during a squat.
- Findings from Milestone 1 footage still worth honoring: 45° is the best angle

---

## Sign-out is an unanswered question

With no `userId` locally, the database is "this device's history" and signing in
claims it — so if user A signs out and B signs in, B inherits A's sets. That is a
milestone-4 problem, named here so the answer is a decision rather than a
migration.
