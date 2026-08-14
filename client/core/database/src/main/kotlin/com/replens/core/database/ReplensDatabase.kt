package com.replens.core.database

import androidx.room3.Database
import androidx.room3.RoomDatabase
import com.replens.core.database.dao.WorkoutDao
import com.replens.core.database.entity.RepEntity
import com.replens.core.database.entity.SetEntity
import com.replens.core.database.entity.WorkoutEntity

@Database(
    entities = [WorkoutEntity::class, SetEntity::class, RepEntity::class],
    version = 1,
    exportSchema = true,
)
internal abstract class ReplensDatabase : RoomDatabase() {
    abstract fun workoutDao(): WorkoutDao
}

internal const val REPLENS_DATABASE_NAME = "replens.db"
