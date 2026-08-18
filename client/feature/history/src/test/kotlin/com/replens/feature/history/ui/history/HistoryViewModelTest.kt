package com.replens.feature.history.ui.history

import com.replens.core.model.WorkoutOverview
import com.replens.core.testing.FakeClock
import com.replens.core.testing.FakeWorkoutRepository
import com.replens.core.testing.MainDispatcherRule
import com.replens.feature.history.ui.history.model.WorkoutRowUiModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.ZoneId
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

private val zone = ZoneId.of("Europe/Warsaw")
private val now = Instant.parse("2026-08-17T08:00:00Z")

class HistoryViewModelTest {

    @get:Rule
    internal val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeWorkoutRepository()

    /**
     * Lazily, because JUnit constructs the test instance before applying rules
     * and `init` touches `viewModelScope` — built as a plain field it would bind
     * a Main dispatcher that never runs anything, and every test would fail with
     * a loading screen rather than an error.
     */
    private val viewModel by lazy {
        HistoryViewModel(
            repository = repository,
            clock = FakeClock(now),
            zone = zone,
        )
    }

    private fun overview(id: Long, startedAt: Instant): WorkoutOverview {
        return WorkoutOverview(
            id = id,
            startedAt = startedAt,
            endedAt = startedAt + 20.minutes,
            setCount = 3,
            repCount = 30,
            repsAtDepth = 18,
        )
    }

    /** The screen has something to draw before the first query comes back. */
    @Test
    fun `the list is loading until the query answers`() = runTest {
        assertEquals(HistoryState.Loading, viewModel.state.value)
    }

    /**
     * The distinction the [HistoryState.Empty] arm exists for: this is an answer,
     * and it must not look like the absence of one.
     */
    @Test
    fun `no workouts is a different answer from no answer`() = runTest {
        viewModel.state.value

        repository.overviews.emit(emptyList())

        assertEquals(HistoryState.Empty, viewModel.state.value)
    }

    @Test
    fun `workouts arrive as rows in the order the repository gave them`() = runTest {
        repository.overviews.emit(
            listOf(
                overview(id = 3, startedAt = now - 1.hours),
                overview(id = 2, startedAt = now - 2.hours),
                overview(id = 1, startedAt = now - 3.hours),
            ),
        )

        val content = viewModel.state.value as HistoryState.Content

        assertEquals(listOf(3L, 2L, 1L), content.workouts.map(WorkoutRowUiModel::id))
    }

    /**
     * The whole point of the screen reading a `Flow`: a set finished on the
     * camera screen lands on a list that is already on screen, with nothing
     * asking for a refresh.
     */
    @Test
    fun `a workout recorded later reaches a list already on screen`() = runTest {
        repository.overviews.emit(listOf(overview(id = 1, startedAt = now - 3.hours)))
        assertEquals(1, (viewModel.state.value as HistoryState.Content).workouts.size)

        repository.overviews.emit(
            listOf(
                overview(id = 2, startedAt = now - 1.hours),
                overview(id = 1, startedAt = now - 3.hours),
            ),
        )

        val content = viewModel.state.value as HistoryState.Content
        assertEquals(listOf(2L, 1L), content.workouts.map(WorkoutRowUiModel::id))
    }

    @Test
    fun `tapping a workout navigates to it`() = runTest {
        viewModel.onAction(HistoryAction.WorkoutClicked(workoutId = 7))

        assertEquals(HistoryEvent.NavigateToWorkout(7), viewModel.events.first())
    }
}
