package com.replens.core.database

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
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

    private suspend fun insertWorkout(startedAt: Long = 0L, endedAt: Long = 0L) =
        dao.insert(WorkoutEntity(startedAt = startedAt, endedAt = endedAt, updatedAt = endedAt))

    private fun set(workoutId: Long, repCount: Int = 3) = SetEntity(
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

    private fun rep(setId: Long, index: Int, deepestAngle: Float = 90f) = RepEntity(
        setId = setId,
        index = index,
        deepestAngle = deepestAngle,
        descentMillis = 900L,
        ascentMillis = 700L,
    )

    @Test
    fun `a workout, its sets and their reps round-trip`() = runTest {
        val workoutId = insertWorkout(startedAt = 1_000L, endedAt = 31_000L)
        val setId = dao.insertSetWithReps(set(workoutId)) { id ->
            List(3) { rep(id, index = it + 1, deepestAngle = 80f + it) }
        }

        val sets = dao.setsFor(workoutId)
        assertEquals(1, sets.size)
        assertEquals("SQUAT", sets.single().exercise)
        assertEquals(128f, sets.single().deepestAbandonedAngle!!, 0.001f)

        val reps = dao.repsFor(setId)
        assertEquals(listOf(1, 2, 3), reps.map(RepEntity::index))
        assertEquals(listOf(80f, 81f, 82f), reps.map(RepEntity::deepestAngle))
    }

    /**
     * Reps are ordered by their own index rather than by insertion, because once
     * sync reassigns local ids the primary key stops being a reliable sequence.
     */
    @Test
    fun `reps come back in rep order, not insertion order`() = runTest {
        val workoutId = insertWorkout()
        val setId = dao.insertSetWithReps(set(workoutId)) { id ->
            listOf(rep(id, index = 3), rep(id, index = 1), rep(id, index = 2))
        }

        assertEquals(listOf(1, 2, 3), dao.repsFor(setId).map(RepEntity::index))
    }

    /**
     * The cascade is what lets a workout be deleted without leaving orphan sets
     * and reps behind — nothing else enforces it.
     */
    @Test
    fun `deleting a workout takes its sets and reps with it`() = runTest {
        val workoutId = insertWorkout()
        val setId = dao.insertSetWithReps(set(workoutId)) { id -> listOf(rep(id, index = 1)) }

        db.workoutDao().deleteWorkout(workoutId)

        assertTrue(dao.setsFor(workoutId).isEmpty())
        assertTrue(dao.repsFor(setId).isEmpty())
    }

    /**
     * The denormalized counts on a set are only true if its reps landed with it,
     * so a half-written set would be a row that lies about itself.
     */
    @Test
    fun `a set is not written when its reps fail`() = runTest {
        val workoutId = insertWorkout()

        val result = runCatching {
            dao.insertSetWithReps(set(workoutId)) { error("reps could not be built") }
        }

        assertTrue(result.isFailure)
        assertTrue(dao.setsFor(workoutId).isEmpty())
    }

    @Test
    fun `the workout list is newest first`() = runTest {
        insertWorkout(startedAt = 1_000L, endedAt = 2_000L)
        insertWorkout(startedAt = 9_000L, endedAt = 9_500L)
        insertWorkout(startedAt = 5_000L, endedAt = 6_000L)

        assertEquals(
            listOf(9_000L, 5_000L, 1_000L),
            dao.workouts().first().map(WorkoutEntity::startedAt),
        )
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
