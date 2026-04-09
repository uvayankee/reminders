package com.uvayankee.medreminder

import android.app.Application
import androidx.room.Room
import com.uvayankee.medreminder.db.AppDatabase
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.dsl.module

import com.uvayankee.medreminder.alarm.AlarmRepository
import com.uvayankee.medreminder.alarm.AlarmScheduler
import com.uvayankee.medreminder.alarm.DoseLogObserver
import com.uvayankee.medreminder.alarm.PrescriptionRepository
import com.uvayankee.medreminder.presentation.MainViewModel
import com.uvayankee.medreminder.presentation.AddEditViewModel
import com.uvayankee.medreminder.domain.dose.TakeDoseUseCase
import com.uvayankee.medreminder.domain.dose.SnoozeDoseUseCase
import com.uvayankee.medreminder.domain.dose.SkipDoseUseCase
import com.uvayankee.medreminder.domain.prescription.SavePrescriptionUseCase
import com.uvayankee.medreminder.domain.prescription.DeletePrescriptionUseCase
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.android.ext.android.get

val appModule = module {
    single {
        Room.databaseBuilder(
            androidContext(),
            AppDatabase::class.java,
            "med_reminder_db"
        ).fallbackToDestructiveMigration().build()
    }
    
    single { get<AppDatabase>().alarmDao() }
    
    single { AlarmScheduler(androidContext()) }
    single { AlarmRepository(get(), get()) }
    single { PrescriptionRepository(get(), get()) }
    single { DoseLogObserver(get(), get()) }

    // UseCases
    factory { TakeDoseUseCase(get()) }
    factory { SnoozeDoseUseCase(get()) }
    factory { SkipDoseUseCase(get()) }
    factory { SavePrescriptionUseCase(get(), get()) }
    factory { DeletePrescriptionUseCase(get(), get()) }

    viewModel { MainViewModel(get(), get(), get(), get(), get(), get()) }
    viewModel { AddEditViewModel(get(), get(), get()) }
}

class MedReminderApp : Application() {
    override fun onCreate() {
        super.onCreate()
        
        startKoin {
            androidContext(this@MedReminderApp)
            modules(appModule)
        }

        // Start reactive alarm observation
        val observer: DoseLogObserver = get()
        observer.startObserving()
    }
}
