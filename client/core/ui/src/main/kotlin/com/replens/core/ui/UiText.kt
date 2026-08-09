package com.replens.core.ui

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalContext

/**
 * Text a ViewModel or mapper **chooses between**, carried unresolved so that the
 * choice survives a locale change — a `String` resolved in a ViewModel is frozen
 * in whatever locale was active when it was made.
 *
 * Text that is always the same resource does not belong here, or in state at all;
 * the composable can name it directly. Server-provided and user-entered content
 * is a plain `String` for the same reason: nothing is being chosen.
 *
 * There are two resolvers on purpose. [asString] with a [Context] is what speech
 * uses — the TTS engine negotiates its own locale and may not get the app's, so
 * the spoken text has to be resolved against the locale the voice actually got,
 * or a Polish phrase comes out of an English voice.
 */
@Immutable
sealed interface UiText {

    @Immutable
    data class Raw(val value: String) : UiText

    @Immutable
    data class Resource(
        @StringRes val id: Int,
        val args: List<Any> = emptyList(),
    ) : UiText {
        companion object {
            operator fun invoke(@StringRes id: Int, vararg args: Any) = Resource(id, args.toList())
        }
    }

    /**
     * [quantity] selects the grammatical form and does **not** fill `%d` — hence
     * the default, without which `getQuantityString` throws on any plural whose
     * text contains a number. Polish has four forms (one/few/many/other), so
     * testing in English alone will not surface a mistake here.
     */
    @Immutable
    data class Plural(
        @PluralsRes val id: Int,
        val quantity: Int,
        val args: List<Any> = listOf(quantity),
    ) : UiText {
        companion object {
            operator fun invoke(@PluralsRes id: Int, quantity: Int, vararg args: Any) =
                Plural(id, quantity, args.toList())
        }
    }
}

fun UiText.asString(context: Context): String = when (this) {
    is UiText.Raw -> value
    is UiText.Resource -> context.getString(id, *args.toTypedArray())
    is UiText.Plural -> context.resources.getQuantityString(id, quantity, *args.toTypedArray())
}

@Composable
@ReadOnlyComposable
fun UiText.asString(): String = asString(LocalContext.current)