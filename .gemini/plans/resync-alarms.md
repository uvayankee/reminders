# Resync Alarms Implementation Plan

## Objective
Add a "Resync Alarms" options menu button to the main screen. This will serve as an "admin button" to clean up and regenerate all future alarms, resolving the corrupted schedules caused by a previous timezone bug.

## Key Files & Context
*   `app/src/main/java/com/uvayankee/medreminder/db/AlarmDao.kt`: Needs a query to delete all future-facing doses.
*   `app/src/main/java/com/uvayankee/medreminder/alarm/AlarmRepository.kt`: Needs a method to orchestrate the global deletion and regeneration.
*   `app/src/main/java/com/uvayankee/medreminder/presentation/MainViewModel.kt`: Needs to expose the resync action to the UI.
*   `app/src/main/java/com/uvayankee/medreminder/MainActivity.kt`: Needs to implement the Options Menu.
*   `app/src/main/res/menu/main_menu.xml`: New file to define the menu item.

## Implementation Steps

1.  **Database Level (`AlarmDao.kt`)**
    *   Add a new method:
        ```kotlin
        @Query("DELETE FROM dose_log WHERE status = 'PENDING' OR status = 'SNOOZED'")
        suspend fun clearAllPendingAndSnoozedDoses()
        ```

2.  **Repository Level (`AlarmRepository.kt`)**
    *   Add a new method `resyncAllAlarms()`:
        ```kotlin
        suspend fun resyncAllAlarms() {
            Log.i("AlarmRepository", "Resyncing all alarms globally")
            alarmDao.clearAllPendingAndSnoozedDoses()
            val prescriptions = alarmDao.getActivePrescriptions()
            prescriptions.forEach {
                ensureFutureDosesScheduled(it.id, force = true)
            }
            refreshNotifications()
        }
        ```

3.  **ViewModel Level (`MainViewModel.kt`)**
    *   Add a function to launch the repository action:
        ```kotlin
        fun resyncAlarms() {
            viewModelScope.launch {
                alarmRepository.resyncAllAlarms()
            }
        }
        ```

4.  **UI Resources (`res/menu/main_menu.xml`)**
    *   Create the menu file with a single item:
        ```xml
        <?xml version="1.0" encoding="utf-8"?>
        <menu xmlns:android="http://schemas.android.com/apk/res/android"
              xmlns:app="http://schemas.android.com/apk/res-auto">
            <item
                android:id="@+id/action_resync"
                android:title="Resync Alarms"
                app:showAsAction="never" />
        </menu>
        ```

5.  **UI Activity (`MainActivity.kt`)**
    *   Override `onCreateOptionsMenu` to inflate the menu.
    *   Override `onOptionsItemSelected` to handle the action, call `viewModel.resyncAlarms()`, and show a Toast message ("Alarms resynchronized").

## Verification
*   Compile and run the app.
*   Click the 3-dot menu and select "Resync Alarms".
*   Verify that duplicate/corrupted doses on the Schedule tab are replaced with a single correct timeline.
