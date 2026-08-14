package com.replens.core.pose

import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.accurate.AccuratePoseDetectorOptions
import com.replens.core.exercise.squat.SquatSignals
import com.replens.core.exercise.squat.squatSignals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale

/**
 * Turns extracted clip frames into the CSV fixtures the fast JVM tests assert
 * against.
 *
 * **Not a test — a tool.** It asserts nothing. It lives in `androidTest` only
 * because it needs a device with ML Kit on it, and in `:core:pose` because
 * `toBodyPose` is internal to this module.
 *
 * It takes frames rather than video on purpose. Android's
 * `MediaMetadataRetriever` is a thumbnail API: on this footage it returns only
 * near-keyframe frames, about 4 fps out of 30, whichever addressing mode you use.
 * `MediaExtractor` + `MediaCodec` would work but is a hundred lines of decoder
 * plumbing for a tool, so `tools/extract-fixture-frames.sh` does it with ffmpeg
 * instead — which also makes the exact input to ML Kit inspectable.
 *
 * The model and the frame size match production deliberately. A CSV generated
 * from a different configuration describes an app we don't ship, and every
 * threshold tuned against it would be wrong with nothing to reveal it.
 *
 * ```
 * ./tools/extract-fixture-frames.sh ~/replens-recordings /tmp/fixture-frames
 * ./gradlew :core:pose:assembleDebugAndroidTest
 * adb install -r core/pose/build/outputs/apk/androidTest/debug/pose-debug-androidTest.apk
 * adb push /tmp/fixture-frames/. /sdcard/Android/data/com.replens.core.pose.test/files/
 * adb shell am instrument -w -e class com.replens.core.pose.FixtureGenerator \
 *     com.replens.core.pose.test/androidx.test.runner.AndroidJUnitRunner
 * adb pull /sdcard/Android/data/com.replens.core.pose.test/files/ <somewhere>
 * ```
 *
 * Driven by `am instrument` rather than `connectedAndroidTest` because Gradle
 * uninstalls the test APK afterwards, and that deletes the pushed frames with it.
 */
@RunWith(AndroidJUnit4::class)
class FixtureGenerator {

    @Test
    fun generateCsvFixtures() {
        val directory = InstrumentationRegistry.getInstrumentation()
            .targetContext.getExternalFilesDir(null)
        // Frames are flat and prefixed rather than one directory per clip: adb
        // creates pushed directories owned by shell with no access for others, so
        // the app can list its own files/ but cannot traverse into them. Discovered
        // via list() rather than listFiles() for the same reason — the filtering
        // overloads stat each entry and come back empty.
        val clips = directory?.list().orEmpty()
            .filter { it.endsWith(".jpg") }
            .groupBy { it.substringBefore(CLIP_SEPARATOR) }
            .toSortedMap()
        Log.i("FixtureGenerator", "dir=$directory clips=${clips.mapValues { it.value.size }}")
        // Skips rather than fails: nobody running connectedCheck without footage
        // should see a red build.
        assumeTrue("No frames in $directory — extract and push first", clips.isNotEmpty())
        clips.forEach { (clip, frames) -> generate(clip, frames.sorted(), directory!!) }
    }

    private fun generate(clip: String, frameNames: List<String>, directory: File) {
        val detector = PoseDetection.getClient(
            AccuratePoseDetectorOptions.Builder()
                .setDetectorMode(AccuratePoseDetectorOptions.STREAM_MODE)
                .build()
        )
        val frames = frameNames.map { File(directory, it) }
        try {
            File(directory, "$clip.csv").bufferedWriter().use { out ->
                out.appendLine(CSV_HEADER)
                frames.forEachIndexed { index, frame ->
                    val bitmap = BitmapFactory.decodeFile(frame.absolutePath) ?: return@forEachIndexed
                    val pose = Tasks.await(detector.process(InputImage.fromBitmap(bitmap, 0)))
                    bitmap.recycle()
                    // The extraction script rejects any clip not at this rate, so the
                    // index is a faithful clock. Container frame counts are not — one
                    // clip reported 283 frames where ffmpeg decoded 257.
                    val timestampMillis = index * 1_000L / FRAMES_PER_SECOND
                    out.appendLine(pose.toBodyPose().squatSignals(timestampMillis).toCsvRow(index))
                }
            }
        } finally {
            detector.close()
        }
    }
}

private const val CSV_HEADER =
    "frame,timestampMillis,depthAngle,leftKneeAngle,rightKneeAngle," +
        "femurInclination,torsoLeanDegrees"

/** Enforced by `tools/extract-fixture-frames.sh`, which refuses clips at other rates. */
private const val FRAMES_PER_SECOND = 30

/** `squats_5_deep__00001.jpg` — clip name, separator, frame number. */
private const val CLIP_SEPARATOR = "__"

private fun SquatSignals.toCsvRow(index: Int): String {
    return listOf(
        index.toString(),
        timestampMillis.toString(),
        depthAngle.csv(),
        leftKneeAngle.csv(),
        rightKneeAngle.csv(),
        femurInclination.csv(),
        torsoLeanDegrees.csv(),
    ).joinToString(",")
}

/**
 * Empty for null — "no confident reading", which a 0 would misrepresent.
 * [Locale.US] is not optional: a Polish locale writes 1,5 and silently splits the
 * value across two CSV columns.
 */
private fun Float?.csv(): String = this?.let { String.format(Locale.US, "%.3f", it) } ?: ""
