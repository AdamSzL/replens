package com.replens.core.database.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

/**
 * One rep. No sync columns: the set is the sync unit and reps travel with it,
 * which leaves one dirty flag to reason about rather than hundreds.
 *
 * [index] is 1-based — the number a user is told. `ORDER BY id` would order these
 * correctly today, since a set is written once in one transaction and never
 * changed, but sync reassigns local ids and the sequence has to survive that.
 *
 * Durations are millis; the domain holds `Duration`. Keeping the primitive here
 * is what lets the entity read as the schema it is, and is why this module needs
 * no column type converters at all.
 */
@Entity(
    tableName = "reps",
    foreignKeys = [
        ForeignKey(
            entity = SetEntity::class,
            parentColumns = ["id"],
            childColumns = ["setId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("setId")],
)
data class RepEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val setId: Long,
    @ColumnInfo(name = "rep_index") val index: Int,
    val deepestAngle: Float,
    val descentMillis: Long,
    val ascentMillis: Long,
)
