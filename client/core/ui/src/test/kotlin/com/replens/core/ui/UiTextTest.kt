package com.replens.core.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

private const val SOME_ID = 1
private const val OTHER_ID = 2

/**
 * Resolution needs a real `Context`, so these cover the part that can go wrong
 * silently: **equality**. Two structurally identical cues must compare equal, or
 * `MutableStateFlow` stops conflating them and every whole-state `assertEquals`
 * in a ViewModel test fails for no visible reason.
 */
class UiTextTest {

    @Test
    fun `resources with the same arguments are equal`() {
        assertEquals(
            UiText.Resource(SOME_ID, listOf(1, "a")),
            UiText.Resource(SOME_ID, listOf(1, "a")),
        )
    }

    /**
     * The reason `args` is a `List` and not an `Array`: array equality is identity
     * based, so this pair would differ despite being the same cue.
     */
    @Test
    fun `resources without arguments are equal`() {
        assertEquals(UiText.Resource(SOME_ID), UiText.Resource(SOME_ID))
    }

    @Test
    fun `resources differ by id and by arguments`() {
        assertNotEquals(UiText.Resource(SOME_ID), UiText.Resource(OTHER_ID))
        assertNotEquals(UiText.Resource(SOME_ID, listOf(1)), UiText.Resource(SOME_ID, listOf(2)))
    }

    @Test
    fun `the vararg factory matches the list constructor`() {
        assertEquals(UiText.Resource(SOME_ID, listOf(1, 2)), UiText.Resource(SOME_ID, 1, 2))
    }

    /**
     * `Resource(id, listOf(...))` must reach the constructor, not the vararg
     * factory with a single list argument — otherwise the list is silently nested
     * one deep and formatting prints `[1, 2]`.
     */
    @Test
    fun `a list argument is not wrapped again`() {
        assertEquals(listOf(1, 2), UiText.Resource(SOME_ID, listOf(1, 2)).args)
    }

    /**
     * The quantity picks the grammatical form and does not fill `%d`, so it has to
     * appear in the arguments as well or `getQuantityString` throws on any plural
     * whose text shows the number.
     */
    @Test
    fun `a plural defaults its argument to the quantity`() {
        assertEquals(listOf(3), UiText.Plural(SOME_ID, quantity = 3).args)
    }

    @Test
    fun `plurals with the same quantity are equal`() {
        assertEquals(UiText.Plural(SOME_ID, 3), UiText.Plural(SOME_ID, 3))
        assertNotEquals(UiText.Plural(SOME_ID, 3), UiText.Plural(SOME_ID, 4))
    }

    @Test
    fun `raw text compares by value`() {
        assertEquals(UiText.Raw("deeper"), UiText.Raw("deeper"))
        assertNotEquals(UiText.Raw("deeper"), UiText.Raw("shallower"))
    }
}
