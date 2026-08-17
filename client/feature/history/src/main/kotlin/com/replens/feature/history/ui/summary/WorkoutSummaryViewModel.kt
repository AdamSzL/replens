package com.replens.feature.history.ui.summary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.replens.core.data.WorkoutRepository
import com.replens.feature.history.ui.summary.mapper.toSummaryState
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
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

    private val eventChannel = Channel<WorkoutSummaryEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            val workout = repository.workout(workoutId)
            state.value = workout?.toSummaryState(clock.now()) ?: WorkoutSummaryState.NotFound
        }
    }

    fun onAction(action: WorkoutSummaryAction) {
        when (action) {
            WorkoutSummaryAction.BackClicked -> navigateBack()
        }
    }

    private fun navigateBack() {
        viewModelScope.launch {
            eventChannel.send(WorkoutSummaryEvent.NavigateBack)
        }
    }
}
