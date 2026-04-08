package com.uvayankee.medreminder.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uvayankee.medreminder.alarm.AlarmRepository
import com.uvayankee.medreminder.alarm.PrescriptionRepository
import com.uvayankee.medreminder.db.DoseLog
import com.uvayankee.medreminder.db.Prescription
import com.uvayankee.medreminder.domain.dose.SkipDoseUseCase
import com.uvayankee.medreminder.domain.dose.SnoozeDoseUseCase
import com.uvayankee.medreminder.domain.dose.TakeDoseUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar

sealed class MainUiState {
    data object Loading : MainUiState()
    data class Schedule(val doses: List<DoseLog>, val selectedDate: Calendar) : MainUiState()
    data class Medications(val prescriptions: List<Prescription>) : MainUiState()
}

class MainViewModel(
    private val alarmRepository: AlarmRepository,
    private val prescriptionRepository: PrescriptionRepository,
    private val takeDoseUseCase: TakeDoseUseCase,
    private val snoozeDoseUseCase: SnoozeDoseUseCase,
    private val skipDoseUseCase: SkipDoseUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<MainUiState>(MainUiState.Loading)
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var currentDataJob: Job? = null
    private var selectedDate = Calendar.getInstance()
    private var currentTab = 0 // 0: Schedule, 1: Medications

    init {
        viewModelScope.launch {
            alarmRepository.scheduleInitialAlarms()
            loadData()
        }
    }

    fun onTabSelected(position: Int) {
        currentTab = position
        loadData()
    }

    fun onDateChanged(year: Int, month: Int, dayOfMonth: Int) {
        selectedDate.set(year, month, dayOfMonth)
        if (currentTab == 0) {
            loadData()
        }
    }

    private fun loadData() {
        currentDataJob?.cancel()
        currentDataJob = viewModelScope.launch {
            if (currentTab == 0) {
                val cal = Calendar.getInstance().apply {
                    timeInMillis = selectedDate.timeInMillis
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val start = cal.timeInMillis
                cal.add(Calendar.DAY_OF_YEAR, 1)
                val end = cal.timeInMillis

                prescriptionRepository.getDosesForDay(start, end).collectLatest { doses ->
                    _uiState.update { MainUiState.Schedule(doses, selectedDate) }
                }
            } else {
                prescriptionRepository.getAllPrescriptions().collectLatest { prescriptions ->
                    _uiState.update { MainUiState.Medications(prescriptions) }
                }
            }
        }
    }

    fun takeDose(doseId: Long) {
        viewModelScope.launch {
            takeDoseUseCase(longArrayOf(doseId))
        }
    }

    fun takeDoses(doseIds: LongArray) {
        viewModelScope.launch {
            takeDoseUseCase(doseIds)
        }
    }

    fun snoozeDoses(doseIds: LongArray, minutes: Int) {
        viewModelScope.launch {
            snoozeDoseUseCase(doseIds, minutes)
        }
    }

    fun skipDoses(doseIds: LongArray) {
        viewModelScope.launch {
            skipDoseUseCase(doseIds)
        }
    }

    fun deletePrescription(prescription: Prescription) {
        viewModelScope.launch {
            prescriptionRepository.deletePrescription(prescription)
        }
    }
}
