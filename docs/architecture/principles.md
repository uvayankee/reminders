# Architectural Principles

1. **Clean Architecture:** Strict separation between Presentation (Compose/ViewModel), Domain (UseCases), and Data (Repositories/DAOs).
2. **Unidirectional Data Flow (UDF):** ViewModels expose a single `StateFlow`. UI components are passive observers.
3. **Single Source of Truth (SSOT):** The Room database is the only master of data. No in-memory state should persist across app restarts or background transitions.
4. **Reactive System Design:** System services (AlarmManager, Notifications) must be "observers" of the database state, not targets of direct "side-effect" calls from the UI.
