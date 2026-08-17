package com.replens.feature.history.ui.history.model

import androidx.compose.runtime.Immutable
import java.time.DayOfWeek
import java.time.LocalDate

@Immutable
internal sealed interface WorkoutDay {
    data object Today : WorkoutDay
    data object Yesterday : WorkoutDay
    data class Weekday(val day: DayOfWeek) : WorkoutDay
    data class Earlier(val date: LocalDate) : WorkoutDay
}
