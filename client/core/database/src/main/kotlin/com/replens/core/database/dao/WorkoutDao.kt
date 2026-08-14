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

    @Query("SELECT * FROM workouts ORDER BY startedAt DESC")
    fun workouts(): Flow<List<WorkoutEntity>>

    @Query("SELECT * FROM sets WHERE workoutId = :workoutId ORDER BY startedAt")
    suspend fun setsFor(workoutId: Long): List<SetEntity>

    @Query("SELECT * FROM reps WHERE setId = :setId ORDER BY rep_index")
    suspend fun repsFor(setId: Long): List<RepEntity>

    @Query("DELETE FROM workouts WHERE id = :id")
    suspend fun deleteWorkout(id: Long)

    /**
     * One transaction, so a set never exists without its reps — the counts on
     * [SetEntity] are denormalized and a partial write would make them lies.
     */
    @Transaction
    suspend fun insertSetWithReps(set: SetEntity, reps: (Long) -> List<RepEntity>): Long {
        val setId = insert(set)
        insert(reps(setId))
        return setId
    }
}
