# Refactor Standard Operating Procedure (SOP)

## The "Surgical Parallelism" Protocol

1. **Baseline:** Identify the procedural logic and write a failing behavioral test (Robolectric/BDD) that captures its current output/system impact.
2. **Domain Draft:** Create a new UseCase (Pure Kotlin) that encapsulates the business logic.
3. **Shadow Mode:** Implement the UseCase alongside the existing Repository method. Use unit tests to verify that for identical inputs, both produce identical state changes.
4. **Switch-Over:** Update the UI/ViewModel to call the UseCase. The UseCase now triggers the DB update, and the system (via a reactive observer) triggers the side effect (e.g., scheduling the alarm).
5. **Verification:** Run the original behavioral test. It must pass without modification.
6. **Clean-up:** Remove the old procedural code from Repositories and Activities.
