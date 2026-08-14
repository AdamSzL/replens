package com.replens.core.database.entity

import androidx.room3.Entity
import androidx.room3.PrimaryKey

/**
 * A training session: the sets you did before walking away.
 *
 * There is no "finish workout" event and there should not be — the common real
 * behavior is pressing nothing at all, so any rule that waits for a press gets
 * the usual case wrong. The boundary is inferred when a *set starts* instead, and
 * [endedAt] is simply its last set's end, which makes an abandoned workout already
 * correct rather than dangling.
 *
 * Times are epoch millis. The domain speaks `Instant`; the mapper is the only
 * place that knows which is which.
 */
@Entity(tableName = "workouts")
data class WorkoutEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val endedAt: Long,
    val serverId: String? = null,
    val updatedAt: Long,
    val isDirty: Boolean = true,
)
