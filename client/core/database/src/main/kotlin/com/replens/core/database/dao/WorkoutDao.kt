package com.replens.core.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Transaction
import com.replens.core.database.entity.RepEntity
import com.replens.core.database.entity.SetEntity
import com.replens.core.database.entity.WorkoutEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDao {

    @Insert
    suspend fun insert(workout: WorkoutEntity): Long

    @Insert
    suspend fun insert(set: SetEntity): Long

    @Insert
    suspend fun insert(reps: List<RepEntity>)

    /**
     * The workout a starting set might belong to. The caller decides whether it is
     * recent enough — the cutoff is a product rule, not a schema one.
     */
    @Query("SELECT * FROM workouts ORDER BY endedAt DESC LIMIT 1")
    suspend fun mostRecentWorkout(): WorkoutEntity?

    @Query("UPDATE workouts SET endedAt = :endedAt, updatedAt = :updatedAt, isDirty = 1 WHERE id = :id")
    suspend fun touchWorkout(id: Long, endedAt: Long, updatedAt: Long)

    @Query("SELECT * FROM workouts WHERE id = :id")
    suspend fun workout(id: Long): WorkoutEntity?

    @Query("SELECT * FROM workouts ORDER BY startedAt DESC")
    fun workouts(): Flow<List<WorkoutEntity>>

    @Query("SELECT * FROM sets WHERE workoutId = :workoutId ORDER BY startedAt")
    suspend fun setsFor(workoutId: Long): List<SetEntity>

    /**
     * Every rep in a workout, so reading one costs two queries rather than one per
     * set. Ordered by set first, so the caller can group without sorting.
     */
    @Query(
        """
        SELECT * FROM reps
        WHERE setId IN (SELECT id FROM sets WHERE workoutId = :workoutId)
        ORDER BY setId, rep_index
        """,
    )
    suspend fun repsForWorkout(workoutId: Long): List<RepEntity>

    /**
     * Nothing in the app deletes yet; this is the only lever the cascade test can
     * pull, and `onDelete = CASCADE` is a schema guarantee that is free to keep
     * and needs a migration to add. Kept deliberately rather than left unverified.
     *
     * It is probably **not** what deletion will eventually call: a hard delete on
     * a synced row comes back on the next pull, so that will want a tombstone.
     */
    @Query("DELETE FROM workouts WHERE id = :id")
    suspend fun deleteWorkout(id: Long)

    /**
     * The set that opens a workout. [set] is a lambda because the workout's id
     * does not exist until it is inserted.
     *
     * One transaction, like [recordSet]: a workout with no sets, or a set without
     * its reps, must never be reachable — the counts on [SetEntity] are
     * denormalized, so a partial write is a row that lies.
     */
    @Transaction
    suspend fun recordFirstSet(
        workout: WorkoutEntity,
        set: (workoutId: Long) -> SetEntity,
        reps: (setId: Long) -> List<RepEntity>,
    ): Long = insertSetAndReps(set(insert(workout)), reps)

    /**
     * A set joining a workout that is already open, moving that workout's end to
     * this set's. Read off [set] rather than passed separately, so the workout
     * cannot end at a different moment than the set that ended it.
     *
     * Whether a set opens a workout or joins one is the caller's decision — the
     * gap that decides it is a product rule and this is a schema.
     */
    @Transaction
    suspend fun recordSet(set: SetEntity, reps: (setId: Long) -> List<RepEntity>): Long {
        touchWorkout(set.workoutId, endedAt = set.endedAt, updatedAt = set.updatedAt)
        return insertSetAndReps(set, reps)
    }

    private suspend fun insertSetAndReps(
        set: SetEntity,
        reps: (setId: Long) -> List<RepEntity>,
    ): Long {
        val setId = insert(set)
        insert(reps(setId))
        return setId
    }
}
