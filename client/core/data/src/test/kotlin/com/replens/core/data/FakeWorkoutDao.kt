package com.replens.core.data

import com.replens.core.database.dao.WorkoutDao
import com.replens.core.database.entity.RepEntity
import com.replens.core.database.entity.SetEntity
import com.replens.core.database.entity.WorkoutEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Implements only the queries and inserts; `recordFirstSet` and `recordSet` are
 * default methods on the interface, so the write paths under test here are the
 * real ones rather than a re-description of them.
 *
 * What it deliberately does not model is atomicity — [WorkoutDao]'s own tests own
 * that, against real SQLite. This fake exists so a test can ask *which* write
 * path the gap rule chose, which a database cannot be asked.
 */
internal class FakeWorkoutDao : WorkoutDao {

    val workoutRows = mutableListOf<WorkoutEntity>()
    val setRows = mutableListOf<SetEntity>()
    val repRows = mutableListOf<RepEntity>()

    override suspend fun insert(workout: WorkoutEntity): Long =
        (workoutRows.size + 1L).also { workoutRows += workout.copy(id = it) }

    override suspend fun insert(set: SetEntity): Long =
        (setRows.size + 1L).also { setRows += set.copy(id = it) }

    override suspend fun insert(reps: List<RepEntity>) {
        reps.forEach { repRows += it.copy(id = repRows.size + 1L) }
    }

    override suspend fun mostRecentWorkout(): WorkoutEntity? =
        workoutRows.maxByOrNull(WorkoutEntity::endedAt)

    override suspend fun touchWorkout(id: Long, endedAt: Long, updatedAt: Long) {
        workoutRows.replaceAll {
            if (it.id == id) {
                it.copy(endedAt = endedAt, updatedAt = updatedAt, isDirty = true)
            } else {
                it
            }
        }
    }

    override suspend fun workout(id: Long): WorkoutEntity? = workoutRows.find { it.id == id }

    override fun workouts(): Flow<List<WorkoutEntity>> =
        flowOf(workoutRows.sortedByDescending(WorkoutEntity::startedAt))

    override suspend fun setsFor(workoutId: Long): List<SetEntity> =
        setRows.filter { it.workoutId == workoutId }.sortedBy(SetEntity::startedAt)

    override suspend fun repsFor(setId: Long): List<RepEntity> =
        repRows.filter { it.setId == setId }.sortedBy(RepEntity::index)

    override suspend fun repsForWorkout(workoutId: Long): List<RepEntity> {
        val setIds = setRows.filter { it.workoutId == workoutId }.map(SetEntity::id).toSet()
        return repRows
            .filter { it.setId in setIds }
            .sortedWith(compareBy(RepEntity::setId, RepEntity::index))
    }

    override suspend fun deleteWorkout(id: Long) {
        val setIds = setRows.filter { it.workoutId == id }.map(SetEntity::id).toSet()
        repRows.removeAll { it.setId in setIds }
        setRows.removeAll { it.workoutId == id }
        workoutRows.removeAll { it.id == id }
    }
}
