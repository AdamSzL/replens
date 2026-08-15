package com.replens.core.text

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes

/**
 * Text a ViewModel or mapper **chooses between**, carried unresolved so that the
 * choice survives a locale change — a `String` resolved in a ViewModel is frozen
 * in whatever locale was active when it was made.
 *
 * Text that is always the same resource does not belong here, or in state at all;
 * the composable can name it directly. Server-provided and user-entered content
 * is a plain `String` for the same reason: nothing is being chosen.
 *
 * There are two resolvers on purpose, and they live in different modules. This
 * one takes a [Context] and is what speech uses — the TTS engine negotiates its
 * own locale and may not get the app's, so the spoken text has to be resolved
 * against the locale the voice actually got, or a Polish phrase comes out of an
 * English voice. The `@Composable` one is in `:core:ui`, which is the whole
 * reason this type does not live there: a module with no Compose could otherwise
 * call it, compile clean, and throw `NoSuchMethodError` at runtime.
 *
 * **No `@Immutable`**, because that would mean a Compose dependency here and put
 * the two resolvers back in one module. It costs nothing: under strong skipping
 * the annotation only selects identity comparison over `equals`, and `equals` is
 * what this type gets right on purpose — see the `List`-not-`Array` rule below.
 * If skipping ever measurably matters, the Compose stability configuration file
 * covers it without a dependency.
 */
sealed interface UiText {

    fun asString(context: Context): String = when (this) {
        is Raw -> value
        is Resource -> context.getString(id, *args.toTypedArray())
        is Plural -> context.resources.getQuantityString(id, quantity, *args.toTypedArray())
    }

    data class Raw(val value: String) : UiText

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

