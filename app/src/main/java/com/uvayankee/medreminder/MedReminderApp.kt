package com.uvayankee.medreminder

import android.app.Application
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.dsl.module

val appModule = module {
    // We will define DI definitions here
}

class MedReminderApp : Application() {
    override fun onCreate() {
        super.onCreate()
        
        startKoin {
            androidContext(this@MedReminderApp)
            modules(appModule)
        }
    }
}
