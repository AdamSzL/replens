package com.replens.core.audio

import android.content.Context
import android.content.res.Configuration
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.replens.core.text.UiText
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

/**
 * Locales this app has strings for. **Must match the `values-*` folders**, and the
 * `localeFilters` that strip the rest from the APK — this is the list the spoken
 * locale is chosen from, and a locale named here without strings would be spoken
 * in a language we cannot write.
 */
private val SUPPORTED_LOCALES = arrayOf("en")

/**
 * How long ducking outlives an utterance.
 *
 * Cues arrive in bursts — the count-in speaks four in three seconds, and a set
 * will speak one per rep — and the gap between them is shorter than the cue, so
 * releasing focus as each one ends let the music ramp back up and duck again four
 * times in three seconds. Heard on device 2026-08-10: that pump is more
 * distracting than the ducking it undoes.
 *
 * The two seconds is a guess to be tuned by ear, like the setup repeat interval:
 * long enough to bridge a count-in tick and a quick rep, short enough that a rest
 * between sets gets the music back.
 */
private val FOCUS_GRACE = 2.seconds

private fun TextToSpeech.canSpeak(locale: Locale): Boolean =
    when (isLanguageAvailable(locale)) {
        TextToSpeech.LANG_AVAILABLE,
        TextToSpeech.LANG_COUNTRY_AVAILABLE,
        TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE,
        -> true

        else -> false
    }

/**
 * Scoped here rather than at the binding: the engine costs a few hundred
 * milliseconds to start and has to be alive before the first cue, not after it.
 */
@Singleton
internal class TtsSpeaker @Inject constructor(
    @ApplicationContext private val context: Context,
) : Speaker {

    private val audioManager = context.getSystemService(AudioManager::class.java)

    private val attributes = AudioAttributes.Builder()
        // Not USAGE_MEDIA: people train with music on, and accessibility usage is
        // what makes the system duck it rather than pause it.
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private val focusRequest =
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attributes)
            .build()

    private var engine: TextToSpeech? = null

    /**
     * Written on the caller's thread and read on the engine's, and only the newest
     * one may drop the ducking: QUEUE_FLUSH ends the previous utterance, whose
     * completion callback then arrives *while the new one is speaking*.
     */
    @Volatile
    private var speakingId: String? = null

    /** Atomic only because [Speaker] documents no threading contract. */
    private val utterances = AtomicInteger()

    private val handler = Handler(Looper.getMainLooper())

    /**
     * Which utterance the pending release belongs to. The post is scheduled from
     * the engine's thread and [speak] runs on the caller's, so a release can still
     * be in flight when the next cue starts; comparing against [speakingId] when it
     * finally runs is what stops that release from silencing the duck mid-sentence.
     */
    @Volatile
    private var releaseFor: String? = null

    private val releaseFocus = Runnable {
        if (releaseFor != speakingId) return@Runnable
        audioManager.abandonAudioFocusRequest(focusRequest)
    }

    /**
     * Configured for the locale the engine accepted, which is not necessarily the
     * device's: an Italian phone running an app that ships only English resolves
     * its strings to English, and asking for an Italian voice would read those
     * English words with Italian phonetics.
     *
     * **Null is the "say nothing" signal**, and it covers both reasons to stay
     * quiet: the engine has not initialised yet, and the engine initialised but has
     * no voice for any language this app has strings for. A separate readiness flag
     * would be true in the second case and wrong.
     */
    private var spokenContext: Context? = null

    init {
        // Eagerly, because init costs a few hundred milliseconds and a cue that
        // arrives first would simply be dropped.
        lateinit var tts: TextToSpeech
        // The callback is handed the engine rather than reading the field: it can
        // fire before the assignment below completes, and a miss there would leave
        // the speaker permanently and silently unprepared.
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) onEngineReady(tts)
        }
        tts.setAudioAttributes(attributes)
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) = onFinished(utteranceId)

            @Deprecated("Abstract in the base class; the two-argument form is called")
            override fun onError(utteranceId: String?) = onFinished(utteranceId)
            override fun onError(utteranceId: String?, errorCode: Int) = onFinished(utteranceId)
        })
        engine = tts
    }

    override fun speak(cue: UiText) {
        val tts = engine ?: return
        val resolved = spokenContext?.let { cue.asString(it) } ?: return

        val id = utterances.incrementAndGet().toString()
        speakingId = id
        // Requested on every cue; the system grants the same request again without
        // re-ducking, so a burst that already holds focus simply keeps it.
        audioManager.requestAudioFocus(focusRequest)

        // A synchronous failure queues nothing, so no completion callback will ever
        // arrive and the duck would last until the process died.
        if (tts.speak(resolved, TextToSpeech.QUEUE_FLUSH, null, id) == TextToSpeech.ERROR) {
            onFinished(id)
        }
    }

    private fun onFinished(utteranceId: String?) {
        if (utteranceId != speakingId) return
        releaseFor = utteranceId
        // Replaced rather than stacked: two pending releases would let the older one
        // fire on the newer one's schedule and drop the duck early.
        handler.removeCallbacks(releaseFocus)
        handler.postDelayed(releaseFocus, FOCUS_GRACE.inWholeMilliseconds)
    }

    private fun onEngineReady(tts: TextToSpeech) {
        val spoken = firstSpeakableLocale(tts) ?: return
        tts.language = spoken
        spokenContext = context.createConfigurationContext(
            Configuration(context.resources.configuration).apply { setLocale(spoken) }
        )
    }

    /**
     * The device's preference, narrowed to what this app has strings for, then to
     * what the engine can actually say. Null leaves [spokenContext] unset and the
     * app silent, which is the honest outcome when it has nothing it can pronounce.
     */
    private fun firstSpeakableLocale(tts: TextToSpeech): Locale? {
        val preferred = context.resources.configuration.locales
            .getFirstMatch(SUPPORTED_LOCALES)
            ?: Locale.ENGLISH
        return listOf(preferred, Locale.ENGLISH).firstOrNull(tts::canSpeak)
    }
}
