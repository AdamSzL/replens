package com.replens.core.data

import com.replens.core.data.mapper.toDomain
import com.replens.core.data.mapper.toEntity
import com.replens.core.database.dao.WorkoutDao
import com.replens.core.database.dao.WorkoutTotals
import com.replens.core.database.entity.RepEntity
import com.replens.core.database.entity.SetEntity
import com.replens.core.database.entity.WorkoutEntity
import com.replens.core.model.Exercise
import com.replens.core.model.Rep
import com.replens.core.model.Workout
import com.replens.core.model.WorkoutOverview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import kotlin.time.Instant

internal class WorkoutRepositoryImpl @Inject constructor(
    private val dao: WorkoutDao,
) : WorkoutRepository {

    override suspend fun recordSet(
        exercise: Exercise,
        startedAt: Instant,
        endedAt: Instant,
        repsAtDepth: Int,
        abandonedCount: Int,
        deepestAbandonedAngle: Float?,
        reps: List<Rep>,
    ): Long {
        // Read outside the transaction: one user doing one set at a time, so there
        // is no concurrent writer for the decision to race against.
        val inProgress = dao.mostRecentWorkout()?.takeIf {
            startedAt - Instant.fromEpochMilliseconds(it.endedAt) < WORKOUT_GAP
        }

        val set = { workoutId: Long ->
            SetEntity(
                workoutId = workoutId,
                exercise = exercise.name,
                startedAt = startedAt.toEpochMilliseconds(),
                endedAt = endedAt.toEpochMilliseconds(),
                repCount = reps.size,
                repsAtDepth = repsAtDepth,
                abandonedCount = abandonedCount,
                deepestAbandonedAngle = deepestAbandonedAngle,
                updatedAt = endedAt.toEpochMilliseconds(),
            )
        }
        val repRows = { setId: Long -> reps.map { it.toEntity(setId) } }

        if (inProgress != null) {
            dao.recordSet(set(inProgress.id), repRows)
            return inProgress.id
        }
        return dao.recordFirstSet(
            workout = WorkoutEntity(
                startedAt = startedAt.toEpochMilliseconds(),
                endedAt = endedAt.toEpochMilliseconds(),
                updatedAt = endedAt.toEpochMilliseconds(),
            ),
            set = set,
            reps = repRows,
        )
    }

    override fun workouts(): Flow<List<WorkoutOverview>> =
        dao.workouts().map { rows -> rows.map(WorkoutTotals::toDomain) }

    override suspend fun workout(id: Long): Workout? {
        val entity = dao.workout(id) ?: return null
        val repsBySet = dao.repsForWorkout(id).groupBy(RepEntity::setId)
        val sets = dao.setsFor(id).mapNotNull { it.toDomain(repsBySet[it.id].orEmpty()) }
        // A workout exists only because a set was recorded, so an empty one here
        // means every set in it named an exercise this build cannot read.
        return if (sets.isEmpty()) null else entity.toDomain(sets)
    }
}
