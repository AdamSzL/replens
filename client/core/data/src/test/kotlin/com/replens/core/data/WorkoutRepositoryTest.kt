package com.replens.core.data

import com.replens.core.database.entity.SetEntity
import com.replens.core.database.entity.WorkoutEntity
import com.replens.core.model.Exercise
import com.replens.core.model.Rep
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class WorkoutRepositoryTest {

    private val dao = FakeWorkoutDao()
    private val repository = WorkoutRepositoryImpl(dao)

    private val noon = Instant.fromEpochMilliseconds(12 * 60 * 60 * 1000L)

    private fun rep(index: Int, deepestAngle: Float = 90f): Rep {
        return Rep(
            index = index,
            deepestAngle = deepestAngle,
            descent = 900.milliseconds,
            ascent = 700.milliseconds,
        )
    }

    private suspend fun record(
        startedAt: Instant,
        reps: List<Rep> = listOf(rep(1)),
        repsAtDepth: Int = 0,
        abandonedCount: Int = 0,
        deepestAbandonedAngle: Float? = null,
    ) = repository.recordSet(
        exercise = Exercise.SQUAT,
        startedAt = startedAt,
        endedAt = startedAt + 30.seconds,
        repsAtDepth = repsAtDepth,
        abandonedCount = abandonedCount,
        deepestAbandonedAngle = deepestAbandonedAngle,
        reps = reps,
    )

    @Test
    fun `the first set opens a workout`() = runTest {
        record(startedAt = noon)

        val workout = dao.workoutRows.single()
        assertEquals(noon.toEpochMilliseconds(), workout.startedAt)
        assertEquals((noon + 30.seconds).toEpochMilliseconds(), workout.endedAt)
    }

    /**
     * Three sets because two cannot tell the answers apart: the first workout and
     * the first set are both id 1, so a set id passes every assertion a workout id
     * would. Only once a join has pushed the set ids ahead does the difference
     * become visible.
     */
    @Test
    fun `the id is the workout's, whether the set opened one or joined one`() = runTest {
        val opened = record(startedAt = noon)
        val joined = record(startedAt = noon + 1.minutes)
        val reopened = record(startedAt = noon + 2.hours)

        assertEquals(opened, joined)
        assertEquals(listOf(opened, reopened), dao.workoutRows.map(WorkoutEntity::id))
        // The set ids ran ahead, so the line above is a result rather than a
        // coincidence of both counters agreeing.
        assertEquals(listOf(1L, 2L, 3L), dao.setRows.map(SetEntity::id))
    }

    @Test
    fun `a set within the gap joins the workout and moves its end`() = runTest {
        record(startedAt = noon)
        val second = noon + 30.seconds + (WORKOUT_GAP - 1.minutes)

        record(startedAt = second)

        val workout = dao.workoutRows.single()
        assertEquals(noon.toEpochMilliseconds(), workout.startedAt)
        assertEquals((second + 30.seconds).toEpochMilliseconds(), workout.endedAt)
        assertEquals(2, dao.setsFor(workout.id).size)
    }

    /**
     * The boundary is strict, so the rule reads as "under an hour" rather than
     * "an hour or less" — which is the phrasing everywhere else it is described.
     */
    @Test
    fun `a set exactly at the gap opens a new workout`() = runTest {
        record(startedAt = noon)

        record(startedAt = noon + 30.seconds + WORKOUT_GAP)

        assertEquals(2, dao.workoutRows.size)
    }

    @Test
    fun `a set after the gap leaves the earlier workout alone`() = runTest {
        record(startedAt = noon)
        val firstEnd = dao.workoutRows.single().endedAt

        record(startedAt = noon + 2.hours)

        assertEquals(2, dao.workoutRows.size)
        assertEquals(firstEnd, dao.workoutRows.first().endedAt)
    }

    /**
     * repCount is the one summary the repository derives, because reps are passed
     * anyway — they are rows. The others come in, since nothing here could
     * recompute them.
     */
    @Test
    fun `the stored set counts its reps and carries the rest`() = runTest {
        record(
            startedAt = noon,
            reps = listOf(rep(1), rep(2), rep(3)),
            repsAtDepth = 2,
            abandonedCount = 2,
            deepestAbandonedAngle = 128f,
        )

        val set = dao.setRows.single()
        assertEquals(3, set.repCount)
        assertEquals(2, set.repsAtDepth)
        assertEquals(2, set.abandonedCount)
        assertEquals(128f, set.deepestAbandonedAngle!!, 0.001f)
    }

    @Test
    fun `a set with no abandoned descents stores no angle`() = runTest {
        record(startedAt = noon)

        assertNull(dao.setRows.single().deepestAbandonedAngle)
    }

    @Test
    fun `a recorded workout reads back whole`() = runTest {
        val reps = listOf(rep(1, deepestAngle = 88f), rep(2, deepestAngle = 91f))
        record(startedAt = noon, reps = reps, repsAtDepth = 1)
        val id = dao.workoutRows.single().id

        val workout = repository.workout(id)!!

        assertEquals(noon, workout.startedAt)
        val set = workout.sets.single()
        assertEquals(Exercise.SQUAT, set.exercise)
        assertEquals(noon, set.startedAt)
        assertEquals(1, set.repsAtDepth)
        assertEquals(2, set.repCount)
        // Rep is a data class, so this covers index, angle and both durations.
        assertEquals(reps, set.reps)
    }

    @Test
    fun `an unknown workout id reads as null`() = runTest {
        assertNull(repository.workout(404L))
    }

    /**
     * A row written by a build that knew more exercises than this one. Dropping
     * the workout beats throwing on the screen that opens it.
     */
    @Test
    fun `a workout of nothing but unreadable sets reads as null`() = runTest {
        record(startedAt = noon)
        dao.setRows.replaceAll { it.copy(exercise = "PULL_UP") }

        assertNull(repository.workout(dao.workoutRows.single().id))
    }

    @Test
    fun `an unreadable set does not take its workout's other sets with it`() = runTest {
        record(startedAt = noon)
        record(startedAt = noon + 5.minutes)
        dao.setRows[0] = dao.setRows[0].copy(exercise = "PULL_UP")

        val workout = repository.workout(dao.workoutRows.single().id)!!

        assertEquals(1, workout.sets.size)
        assertEquals(dao.setRows[1].id, workout.sets.single().id)
    }

    @Test
    fun `reps come back attached to the set that produced them`() = runTest {
        record(startedAt = noon, reps = listOf(rep(1, 80f)))
        record(startedAt = noon + 5.minutes, reps = listOf(rep(1, 95f), rep(2, 97f)))

        val sets = repository.workout(dao.workoutRows.single().id)!!.sets

        assertEquals(listOf(80f), sets[0].reps.map(Rep::deepestAngle))
        assertEquals(listOf(95f, 97f), sets[1].reps.map(Rep::deepestAngle))
    }
}
