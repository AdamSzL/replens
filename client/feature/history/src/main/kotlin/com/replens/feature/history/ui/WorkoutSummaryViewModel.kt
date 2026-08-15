package com.replens.feature.history.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.replens.core.data.WorkoutRepository
import com.replens.feature.history.ui.mapper.toSummaryState
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.time.Clock

@HiltViewModel(assistedFactory = WorkoutSummaryViewModel.Factory::class)
internal class WorkoutSummaryViewModel @AssistedInject constructor(
    @Assisted private val workoutId: Long,
    private val repository: WorkoutRepository,
    private val clock: Clock,
) : ViewModel() {

    @AssistedFactory
    internal interface Factory {
        fun create(workoutId: Long): WorkoutSummaryViewModel
    }

    val state: StateFlow<WorkoutSummaryState>
        field = MutableStateFlow<WorkoutSummaryState>(WorkoutSummaryState.Loading)

    init {
        viewModelScope.launch {
            val workout = repository.workout(workoutId)
            state.value = workout?.toSummaryState(clock.now()) ?: WorkoutSummaryState.NotFound
        }
    }
}
