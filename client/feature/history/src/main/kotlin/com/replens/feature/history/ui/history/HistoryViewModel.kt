package com.replens.feature.history.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.replens.core.data.WorkoutRepository
import com.replens.core.ui.EventChannel
import com.replens.feature.history.ui.history.mapper.toHistoryState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.ZoneId
import javax.inject.Inject
import kotlin.time.Clock

@HiltViewModel
internal class HistoryViewModel @Inject constructor(
    repository: WorkoutRepository,
    private val clock: Clock,
    private val zone: ZoneId,
) : ViewModel() {

    val state: StateFlow<HistoryState>
        field = MutableStateFlow<HistoryState>(HistoryState.Loading)

    private val eventChannel = EventChannel<HistoryEvent>(viewModelScope)
    val events = eventChannel.events

    init {
        viewModelScope.launch {
            repository.workouts().collect { overviews ->
                state.value = overviews.toHistoryState(
                    now = clock.now(),
                    zone = zone,
                )
            }
        }
    }

    fun onAction(action: HistoryAction) {
        when (action) {
            is HistoryAction.WorkoutClicked -> navigateToWorkout(action.workoutId)
        }
    }

    private fun navigateToWorkout(workoutId: Long) {
        eventChannel.send(HistoryEvent.NavigateToWorkout(workoutId))
    }
}
