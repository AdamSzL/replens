package com.replens.core.database.di

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.replens.core.database.REPLENS_DATABASE_NAME
import com.replens.core.database.ReplensDatabase
import com.replens.core.database.dao.WorkoutDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object DatabaseModule {

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): ReplensDatabase =
        Room.databaseBuilder<ReplensDatabase>(context, REPLENS_DATABASE_NAME)
            .setDriver(BundledSQLiteDriver())
            .build()

    @Provides
    fun workoutDao(database: ReplensDatabase): WorkoutDao = database.workoutDao()
}
