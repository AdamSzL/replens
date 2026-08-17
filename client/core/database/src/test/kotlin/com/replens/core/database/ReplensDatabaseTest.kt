package com.replens.core.database

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.replens.core.database.dao.WorkoutTotals
import com.replens.core.database.entity.RepEntity
import com.replens.core.database.entity.SetEntity
import com.replens.core.database.entity.WorkoutEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Runs on the JVM, not a device: Room 3 talks to a [BundledSQLiteDriver] that
 * ships its own SQLite, so there is no framework database to emulate and no
 * Robolectric. That is what keeps the schema in the fast test tier the rest of
 * this project lives in — and the in-memory builder takes no `Context`, which is
 * the whole difference from the Android overload.
 */
class ReplensDatabaseTest {

    private val db = Room.inMemoryDatabaseBuilder<ReplensDatabase>()
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.Unconfined)
        .build()

    private val dao = db.workoutDao()

    @After
    fun tearDown() = db.close()

    private fun workout(startedAt: Long = 0L, endedAt: Long = 0L) =
        WorkoutEntity(startedAt = startedAt, endedAt = endedAt, updatedAt = endedAt)

    private suspend fun insertWorkout(startedAt: Long = 0L, endedAt: Long = 0L) =
        dao.insert(workout(startedAt = startedAt, endedAt = endedAt))

    private fun set(workoutId: Long, repCount: Int = 3): SetEntity {
        return SetEntity(
            workoutId = workoutId,
            exercise = "SQUAT",
            startedAt = 0L,
            endedAt = 30_000L,
            repCount = repCount,
            repsAtDepth = 2,
            abandonedCount = 1,
            deepestAbandonedAngle = 128f,
            updatedAt = 30_000L,
        )
    }

    private fun rep(setId: Long, index: Int, deepestAngle: Float = 90f): RepEntity {
        return RepEntity(
            setId = setId,
            index = index,
            deepestAngle = deepestAngle,
            descentMillis = 900L,
            ascentMillis = 700L,
        )
    }

    @Test
    fun `a workout, its sets and their reps round-trip`() = runTest {
        val workoutId = dao.recordFirstSet(
            workout = workout(startedAt = 1_000L, endedAt = 31_000L),
            set = ::set,
            reps = { id -> List(3) { rep(id, index = it + 1, deepestAngle = 80f + it) } },
        )

        val sets = dao.setsFor(workoutId)
        assertEquals(1, sets.size)
        assertEquals("SQUAT", sets.single().exercise)
        assertEquals(128f, sets.single().deepestAbandonedAngle!!, 0.001f)

        val reps = dao.repsForWorkout(workoutId)
        assertEquals(listOf(1, 2, 3), reps.map(RepEntity::index))
        assertEquals(listOf(80f, 81f, 82f), reps.map(RepEntity::deepestAngle))
    }

    /**
     * Which of the two happens is the caller's decision — the gap that decides it
     * is a product rule — so both arms are pinned here rather than assumed.
     */
    @Test
    fun `each first set opens its own workout`() = runTest {
        dao.recordFirstSet(workout(startedAt = 1_000L, endedAt = 31_000L), ::set) { listOf(rep(it, 1)) }
        dao.recordFirstSet(workout(startedAt = 9_000L, endedAt = 39_000L), ::set) { listOf(rep(it, 1)) }

        assertEquals(2, dao.workouts().first().size)
    }

    @Test
    fun `a joining set extends its workout rather than opening one`() = runTest {
        val workoutId = insertWorkout(startedAt = 0L, endedAt = 10_000L)

        dao.recordSet(set(workoutId).copy(endedAt = 50_000L, updatedAt = 50_000L)) { listOf(rep(it, 1)) }

        val workouts = dao.workouts().first()
        assertEquals(1, workouts.size)
        assertEquals(50_000L, workouts.single().endedAt)
        assertEquals(1, dao.setsFor(workoutId).size)
    }

    /**
     * Reps are ordered by their own index rather than by insertion, because once
     * sync reassigns local ids the primary key stops being a reliable sequence.
     */
    @Test
    fun `reps come back in rep order, not insertion order`() = runTest {
        val workoutId = dao.recordFirstSet(workout(), ::set) {
            listOf(rep(it, index = 3), rep(it, index = 1), rep(it, index = 2))
        }

        assertEquals(listOf(1, 2, 3), dao.repsForWorkout(workoutId).map(RepEntity::index))
    }

    /** Two queries per workout instead of one per set is the whole point of it. */
    @Test
    fun `a workout's reps come back in one query, set by set`() = runTest {
        val workoutId = insertWorkout()
        dao.recordSet(set(workoutId)) { listOf(rep(it, 1), rep(it, 2)) }
        dao.recordSet(set(workoutId)) { listOf(rep(it, 1)) }

        val (first, second) = dao.setsFor(workoutId).map(SetEntity::id)
        val reps = dao.repsForWorkout(workoutId)
        assertEquals(listOf(first, first, second), reps.map(RepEntity::setId))
        assertEquals(listOf(1, 2, 1), reps.map(RepEntity::index))
    }

    /**
     * The cascade is what lets a workout be deleted without leaving orphan sets
     * and reps behind — nothing else enforces it.
     */
    @Test
    fun `deleting a workout takes its sets and reps with it`() = runTest {
        dao.recordFirstSet(workout(), ::set) { listOf(rep(it, index = 1)) }
        val workoutId = dao.mostRecentWorkout()!!.id

        dao.deleteWorkout(workoutId)

        assertTrue(dao.setsFor(workoutId).isEmpty())
        assertTrue(dao.repsForWorkout(workoutId).isEmpty())
    }

    /**
     * All three tables or none. A set whose reps are missing lies about itself,
     * since [SetEntity.repCount] is denormalized, and a workout with no sets is a
     * history row with nothing in it.
     */
    @Test
    fun `nothing is written when the reps fail`() = runTest {
        val result = runCatching {
            dao.recordFirstSet(workout(), ::set) { error("reps could not be built") }
        }

        assertTrue(result.isFailure)
        assertTrue(dao.workouts().first().isEmpty())
        assertNull(dao.mostRecentWorkout())
    }

    @Test
    fun `the workout list is newest first`() = runTest {
        insertWorkout(startedAt = 1_000L, endedAt = 2_000L)
        insertWorkout(startedAt = 9_000L, endedAt = 9_500L)
        insertWorkout(startedAt = 5_000L, endedAt = 6_000L)

        assertEquals(
            listOf(9_000L, 5_000L, 1_000L),
            dao.workouts().first().map(WorkoutTotals::startedAt),
        )
    }

    /** The whole reason the counts are denormalized onto sets: one join, no reps. */
    @Test
    fun `a workout totals up its sets`() = runTest {
        val workoutId = insertWorkout(startedAt = 1_000L, endedAt = 2_000L)
        dao.recordSet(set(workoutId, repCount = 5).copy(repsAtDepth = 4)) { listOf(rep(it, 1)) }
        dao.recordSet(set(workoutId, repCount = 3).copy(repsAtDepth = 1)) { listOf(rep(it, 1)) }

        val totals = dao.workouts().first().single()

        assertEquals(2, totals.setCount)
        assertEquals(8, totals.repCount)
        assertEquals(5, totals.repsAtDepth)
    }

    /**
     * Unreachable — [WorkoutDao.recordFirstSet] writes both in one transaction —
     * and pinned anyway, because the alternative join silently drops such a row
     * and would make a broken transaction invisible instead of merely wrong.
     */
    @Test
    fun `a workout with no sets reads as zeros rather than disappearing`() = runTest {
        insertWorkout(startedAt = 1_000L, endedAt = 2_000L)

        val totals = dao.workouts().first().single()

        assertEquals(0, totals.setCount)
        assertEquals(0, totals.repCount)
        assertEquals(0, totals.repsAtDepth)
    }

    /**
     * Room 3 replaced `InvalidationTracker.Observer` with Flow-based
     * invalidation, and a query that never re-emits looks exactly like a history
     * screen that quietly stops updating — a bug only findable by using the app.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `the workout list emits again when a workout lands`() = runTest {
        val sizes = mutableListOf<Int>()
        val collecting = backgroundScope.launch(Dispatchers.Unconfined) {
            dao.workouts().collect { sizes += it.size }
        }

        insertWorkout(startedAt = 1_000L, endedAt = 2_000L)
        advanceUntilIdle()
        insertWorkout(startedAt = 5_000L, endedAt = 6_000L)
        advanceUntilIdle()
        collecting.cancel()

        assertEquals(listOf(0, 1, 2), sizes)
    }

    /**
     * The row count does not move here — the *counts on it* do, which is what a
     * second set joining an open workout looks like on this screen.
     *
     * Inserted directly rather than through [WorkoutDao.recordSet], deliberately:
     * that also calls `touchWorkout`, so the write to `workouts` alone would
     * trigger the re-emission and the test would pass without `sets` being
     * observed at all. Bypassing it is the only way to pin the join's half.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `the workout list emits again when only a set is written`() = runTest {
        val workoutId = insertWorkout(startedAt = 1_000L, endedAt = 2_000L)
        val counts = mutableListOf<Int>()
        val collecting = backgroundScope.launch(Dispatchers.Unconfined) {
            dao.workouts().collect { counts += it.single().repCount }
        }

        dao.insert(set(workoutId, repCount = 4))
        advanceUntilIdle()
        collecting.cancel()

        assertEquals(listOf(0, 4), counts)
    }

    @Test
    fun `the most recent workout is the one that ended last`() = runTest {
        insertWorkout(startedAt = 0L, endedAt = 10_000L)
        insertWorkout(startedAt = 60_000L, endedAt = 90_000L)
        insertWorkout(startedAt = 30_000L, endedAt = 40_000L)

        assertEquals(90_000L, dao.mostRecentWorkout()!!.endedAt)
    }

    @Test
    fun `an empty database has no most recent workout`() = runTest {
        assertNull(dao.mostRecentWorkout())
    }

    /**
     * The gap rule reads `endedAt` to decide whether a starting set continues this
     * workout, so extending one has to move it — and mark it for sync.
     */
    @Test
    fun `touching a workout moves its end and marks it dirty`() = runTest {
        val id = dao.insert(
            WorkoutEntity(startedAt = 0L, endedAt = 10_000L, updatedAt = 10_000L, isDirty = false),
        )

        dao.touchWorkout(id, endedAt = 50_000L, updatedAt = 50_000L)

        val workout = dao.mostRecentWorkout()!!
        assertEquals(50_000L, workout.endedAt)
        assertTrue(workout.isDirty)
    }
}
