package com.uvayankee.medreminder.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uvayankee.medreminder.alarm.PrescriptionRepository
import com.uvayankee.medreminder.db.Prescription
import com.uvayankee.medreminder.db.TimeSchedule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar

data class AddEditUiState(
    val name: String = "",
    val startDate: Long = Calendar.getInstance().timeInMillis,
    val reminderTimes: List<Pair<Int, Float>> = emptyList(), // Time in minutes to Dosage
    val isSaved: Boolean = false,
    val isDeleted: Boolean = false,
    val isLoading: Boolean = false
)

class AddEditViewModel(
    private val repository: PrescriptionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddEditUiState())
    val uiState: StateFlow<AddEditUiState> = _uiState.asStateFlow()

    private var prescriptionId: Long = 0

    fun loadPrescription(id: Long) {
        if (id == 0L) return
        prescriptionId = id
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            val p = repository.getPrescriptionById(id)
            if (p != null) {
                val times = repository.getTimesForPrescription(id)
                _uiState.update { state ->
                    state.copy(
                        name = p.name,
                        startDate = p.startDate,
                        reminderTimes = times.map { it.reminderTimeMinutes to it.dosage },
                        isLoading = false
                    )
                }
            }
        }
    }

    fun onNameChanged(newName: String) {
        _uiState.update { it.copy(name = newName) }
    }

    fun onDateChanged(year: Int, month: Int, dayOfMonth: Int) {
        val cal = Calendar.getInstance().apply {
            set(year, month, dayOfMonth)
        }
        _uiState.update { it.copy(startDate = cal.timeInMillis) }
    }

    fun addTime(hour: Int, minute: Int) {
        val totalMinutes = hour * 60 + minute
        _uiState.update { state ->
            if (state.reminderTimes.none { it.first == totalMinutes }) {
                val newList = state.reminderTimes.toMutableList()
                newList.add(totalMinutes to 1.0f)
                newList.sortBy { it.first }
                state.copy(reminderTimes = newList)
            } else {
                state
            }
        }
    }

    fun updateDosage(position: Int, dosage: Float) {
        _uiState.update { state ->
            if (position < state.reminderTimes.size) {
                val newList = state.reminderTimes.toMutableList()
                newList[position] = newList[position].first to dosage
                state.copy(reminderTimes = newList)
            } else {
                state
            }
        }
    }

    fun removeTime(position: Int) {
        _uiState.update { state ->
            if (position < state.reminderTimes.size) {
                val newList = state.reminderTimes.toMutableList()
                newList.removeAt(position)
                state.copy(reminderTimes = newList)
            } else {
                state
            }
        }
    }

    fun savePrescription() {
        val state = _uiState.value
        if (state.name.isBlank() || state.reminderTimes.isEmpty()) return

        viewModelScope.launch {
            val p = Prescription(
                id = prescriptionId,
                name = state.name,
                startDate = state.startDate,
                endDate = state.startDate + 365L * 24 * 60 * 60 * 1000 // 1 year default
            )
            val times = state.reminderTimes.map {
                TimeSchedule(prescriptionId = prescriptionId, reminderTimeMinutes = it.first, dosage = it.second)
            }
            repository.savePrescription(p, times)
            _uiState.update { it.copy(isSaved = true) }
        }
    }

    fun deletePrescription() {
        if (prescriptionId == 0L) return
        viewModelScope.launch {
            val p = repository.getPrescriptionById(prescriptionId)
            if (p != null) {
                repository.deletePrescription(p)
                _uiState.update { it.copy(isDeleted = true) }
            }
        }
    }
}
