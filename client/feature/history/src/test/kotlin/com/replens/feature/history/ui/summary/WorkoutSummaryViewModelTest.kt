package com.replens.feature.history.ui.summary

import com.replens.core.testing.FakeClock
import com.replens.core.testing.FakeWorkoutRepository
import com.replens.core.testing.MainDispatcherRule
import com.replens.feature.history.ui.summary.mapper.EPOCH
import com.replens.feature.history.ui.summary.mapper.set
import com.replens.feature.history.ui.summary.mapper.workout
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class WorkoutSummaryViewModelTest {

    @get:Rule
    internal val mainDispatcherRule = MainDispatcherRule()

    private val workout = workout(
        set(startedAt = EPOCH, angles = listOf(90f, 100f)),
        set(startedAt = EPOCH + 5.minutes, angles = listOf(80f)),
    )
    private val clock = FakeClock(EPOCH + 10.minutes)

    private fun viewModel(
        id: Long = workout.id,
        repository: FakeWorkoutRepository = FakeWorkoutRepository(listOf(workout)),
    ): WorkoutSummaryViewModel {
        return WorkoutSummaryViewModel(
            workoutId = id,
            repository = repository,
            clock = clock,
        )
    }

    @Test
    fun `a workout is read and mapped`() = runTest {
        val state = viewModel().state.value

        val content = state as WorkoutSummaryState.Content
        assertEquals(3, content.totals.repCount)
        assertEquals(2, content.sets.size)
        val series = content.depthChart!!.sets.map { it.angles }
        assertEquals(listOf(listOf(90f, 100f), listOf(80f)), series)
    }

    @Test
    fun `an id with no workout behind it is not found`() = runTest {
        val state = viewModel(id = 404).state.value

        assertEquals(WorkoutSummaryState.NotFound, state)
    }

    /** The screen has something to draw before the read comes back. */
    @Test
    fun `state is Loading until the read lands`() = runTest {
        val repository = FakeWorkoutRepository(listOf(workout))
        repository.readInFlight = CompletableDeferred()

        val viewModel = viewModel(repository = repository)
        assertEquals(WorkoutSummaryState.Loading, viewModel.state.value)

        repository.readInFlight?.complete(Unit)

        assertTrue(viewModel.state.value is WorkoutSummaryState.Content)
    }

    @Test
    fun `back asks to navigate back`() = runTest {
        val viewModel = viewModel()

        viewModel.onAction(WorkoutSummaryAction.BackClicked)

        assertEquals(WorkoutSummaryEvent.NavigateBack, viewModel.events.first())
    }

    /**
     * The gap rule decided against the clock at the moment of reading, not
     * against the workout's own timestamps — so the same workout is an open
     * session now and a closed one tomorrow.
     */
    @Test
    fun `whether the workout is still open is read from the clock`() = runTest {
        val soon = viewModel().state.value as WorkoutSummaryState.Content
        assertTrue(soon.totals.isOpen)

        clock.instant = EPOCH + 3.hours
        val later = viewModel().state.value as WorkoutSummaryState.Content

        assertFalse(later.totals.isOpen)
    }
}
