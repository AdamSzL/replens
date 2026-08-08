# :core:pose

CameraX + ML Kit behind `Flow<PoseFrame>`. `PoseMapper` is internal — ML Kit types
must not escape this module.

It also holds `FixtureGenerator`, which is **a tool, not a test**.

## Fixture generation

The rep counter is tuned against CSV fixtures generated from real recordings. The
expensive, device-dependent half runs here, once; the cheap, deterministic half
runs on every `./gradlew test` in `:core:exercise`.

```
recorded .mp4 ──ffmpeg──► frames ──ML Kit──► CSV ──committed──► JVM tests
   (host)                 (host)    (device)                   (everywhere)
```

`FixtureGenerator` asserts nothing. It lives in `androidTest` only because it needs
a device with ML Kit on it, and in this module because `toBodyPose` is internal
here. CI never runs it — instrumented tests need a connected device, so the
separation is automatic.

### Running it

```bash
# 1. Extract every frame, scaled to the analysis stream's long edge.
./tools/extract-fixture-frames.sh ~/replens-recordings /tmp/fixture-frames

# 2. Build and install the test APK.
./gradlew :core:pose:assembleDebugAndroidTest
adb install -r core/pose/build/outputs/apk/androidTest/debug/pose-debug-androidTest.apk

# 3. Push the frames, then run.
adb push /tmp/fixture-frames/. /sdcard/Android/data/com.replens.core.pose.test/files/
adb shell am instrument -w -e class com.replens.core.pose.FixtureGenerator \
    com.replens.core.pose.test/androidx.test.runner.AndroidJUnitRunner

# 4. Collect the CSVs.
adb pull /sdcard/Android/data/com.replens.core.pose.test/files/ /tmp/fixtures-out
cp /tmp/fixtures-out/*.csv core/exercise/src/test/resources/fixtures/
```

Roughly 3,300 frames takes about 15 minutes. Reinstall after every rebuild — a
stale APK fails silently by doing nothing.

### Why it looks like this

Five things that each cost an hour, none of which announce themselves:

**Frames are decoded on the host, not the device.** `MediaMetadataRetriever` is a
*thumbnail* API: on HEVC phone footage it returns only near-keyframe frames —
about 4 fps out of 30 — whichever addressing mode you use. `MediaExtractor` +
`MediaCodec` would work but is ~100 lines of decoder plumbing for a tool.

**`-fps_mode passthrough` is mandatory.** Some clips declare a 120 fps container
rate while holding 30 fps of real frames. ffmpeg's default duplicates each frame
four times to fill it, quadrupling the rows and stretching the timeline — and the
result looks entirely plausible.

**Container frame counts lie.** One clip reported 283 frames where ffmpeg decoded
257. Timestamps come from the frame index at a rate `extract-fixture-frames.sh`
verifies with ffprobe, never from metadata.

**Frames are flat, not one directory per clip.** `adb push` creates directories
owned by `shell` with mode `drwxrws---`. The app can list its own `files/` and read
pushed *files*, but cannot traverse into pushed *directories*. Hence
`clip__00001.jpg`.

**`listFiles()` returns empty on this storage — `list()` works.** The filtering
overloads stat each entry, and stat is what fails.

And it is driven by `am instrument` rather than `connectedAndroidTest` because
Gradle uninstalls the test APK afterwards, which deletes the pushed frames with it.

### Recording new clips

Front camera, 1×, FHD, 30 fps, portrait, stabilisation locked, video boost off.
Same spot and lighting for every clip in a batch — differences between clips must
come from what you did, not where you stood. Leave a beat of stillness at each end
so the trims are unambiguous, and stand *fully* upright between reps: a rep only
closes at `standingEnter` (168°), so staying slightly bent breaks the count for
reasons unrelated to depth.

Name the file for what you performed — `squats_5_deep`, `squats_3_paused` — because
that number becomes the assertion in `SquatFixtureTest`. If the counter disagrees,
the counter is what's wrong.

Clips live in `~/replens-recordings/`, never in the repo.
