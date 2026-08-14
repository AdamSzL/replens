package com.replens.core.database.entity

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

/**
 * One bout of reps — a "set" in gym terms, not a container of exercises. The
 * domain calls it `ExerciseSet`, since `Set` shadows `kotlin.collections.Set`.
 *
 * [repCount] and [repsAtDepth] are denormalized here on purpose. That does not
 * contradict the derive-don't-store rule the ViewModel follows: that rule exists
 * because a live counter can drift from its source, and **a finished set never
 * changes again**, so there is nothing to drift from. A history list that joins
 * and aggregates per row to print "12 reps" is the wrong trade.
 *
 * [deepestAbandonedAngle] is a **minimum** — 128 is shallower than 95, matching
 * `Rep.deepestAngle`. It is here because the per-user calibration trigger is
 * "reached DESCENDING and never BOTTOM", which is exactly an abandoned descent,
 * and answering that from history beats guessing from one bad session.
 */
@Entity(
    tableName = "sets",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutEntity::class,
            parentColumns = ["id"],
            childColumns = ["workoutId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("workoutId")],
)
data class SetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutId: Long,
    /** Stored as a name, read tolerantly: an unknown one must not throw. */
    val exercise: String,
    val startedAt: Long,
    val endedAt: Long,
    val repCount: Int,
    val repsAtDepth: Int,
    val abandonedCount: Int,
    val deepestAbandonedAngle: Float? = null,
    val serverId: String? = null,
    val updatedAt: Long,
    val isDirty: Boolean = true,
)
