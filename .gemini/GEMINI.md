Project Conventions: Reminders app
1. Core Tech Stack
Language: Kotlin 1.9.22 (Current).

Min SDK: 26 (Android 8.0) | Target/Compile SDK: 34 (Android 14).

UI Framework: ViewBinding + XML (Transitioning/Planned for Jetpack Compose).

Concurrency: Kotlin Coroutines & Flow.

Dependency Injection: Koin (Replacing legacy Android constructs).

Persistence: Room Database (SQL-backed for medication logs).

Build System: Gradle Kotlin DSL (.kts) via wrapper 8.9, AGP 8.5.0.

2. Architecture: "Clean App" Principles
All business logic MUST be encapsulated in UseCases. All refactors MUST follow the SOPs and principles defined in our documentation.
Read and adhere to the following core documents before making architectural changes:
- `docs/architecture/principles.md`
- `docs/architecture/refactor-sop.md`

We follow a strict Layered Architecture to ensure the legacy logic is isolated from the modern UI.

Presentation Layer: Compose + ViewModel + StateFlow. Use Unidirectional Data Flow (UDF). ViewModels must use collectAsStateWithLifecycle().

Domain Layer: Pure Kotlin. Contains UseCases (e.g., ScheduleNextDoseUseCase). No Android framework imports allowed here.

Data Layer: Repositories, Room DAOs, and the legacy-to-modern mappers.

Package Structure: com.andrewmarshall.medreminder.[layer].[feature].

3. Medication Timer & System Rules (Android 16)
Alarming: Use AlarmManager.setExactAndAllowWhileIdle() for critical dose timings.

Permissions: Explicitly handle SCHEDULE_EXACT_ALARM (Android 12+) and POST_NOTIFICATIONS (Android 13+).

Edge-to-Edge: All screens must be edge-to-edge by default (Mandatory in API 36). Use WindowInsets for padding.

Notifications: Must use Notification Channels. For doses, use the IMPORTANCE_HIGH channel. The 'Insistent' flag (FLAG_INSISTENT) should NOT be implemented.

4. Coding Style & Patterns
Naming: * Composables: PascalCase.

Functions/Variables: camelCase.

Backing Properties: _privateState.

Error Handling: Use the Kotlin Result<T> type or a sealed UIState class (Loading, Success, Error). No silent failures.

Mappers: When porting from the Old_App_Decompiled folder, always create a Mapper class to translate legacy SharedPreferences keys into modern Data Classes.

5. AI Agent Coordination Protocol
Source Truth: Before writing code, the agent must check docs/requirements/ for the relevant GitHub Issue context.

Commits: Every PR/Commit must reference a requirement ID (e.g., feat: implement alarm trigger logic [REQ-001]).

Refactoring: Do not "dry" up code across layers. Prefer duplication over tight coupling between Domain and Data.

Documentation: Every public function in the Domain layer requires a KDoc summary.

6. Development Methodology (TDD & Tidy First)
Red-Green-Refactor Cycle: Always follow the Test-Driven Development (TDD) cycle. Write a failing test first, implement only the minimum code needed to pass, and refactor only once the tests are green.

Atomic Test-Driven Steps: Implement functionality in small, verifiable increments. Run all relevant tests after every change.

Structural vs. Behavioral Changes: Adhere to "Tidy First" principles by separating structural changes (refactoring, renaming, code movement) from behavioral changes (new features, bug fixes).

Structural Precedence: Perform structural "tidying" first to prepare the codebase for behavioral changes. Never mix both in a single commit.

Test Integrity: Never comment out tests. Use framework-specific annotations (e.g., @Disabled, @Ignore) to temporarily bypass tests if absolutely necessary.

7. Planning & Communication
Approval-First Workflow: Present a detailed, fine-grained implementation plan for user review and approval before starting work on any feature or fix.

Intentional Design: For complex tasks, use a phased approach (Design → Plan → Execute → Complete). Converge on an approved design before decomposing it into actionable phases.

Clarification & Confirmation: Proactively identify and resolve ambiguity. Explain the purpose and impact of critical shell commands that modify the system state before executing them.

Concise Communication: Maintain a professional, direct, and senior-engineer tone. Focus on high-signal output (intent and rationale) while minimizing conversational filler.

8. Code Quality & Contextual Integrity
Contextual Precedence: Rigorously analyze and adhere to existing workspace conventions, architectural patterns, and style (naming, formatting, typing).

Simplicity & Intent: Use the simplest solution that could possibly work. Express intent clearly through naming and structure.

Dependency Management: Make dependencies explicit and minimize state or side effects.

Ruthless De-duplication: Actively eliminate code duplication and consolidate logic into clean abstractions.

9. Workflow & Version Control
Isolation: Work in dedicated feature or fix branches.

Commit Discipline: Commit only when all tests pass and linter warnings are resolved. Commits should represent a single logical unit of work.

History Integrity: Maintain a clean, linear commit history. Propose descriptive commit messages based on git status and diff.

Validation Gating: A task is only complete once it has been verified through project-specific builds, lints, and automated tests.

10. System Integrity & Efficiency
Security First: Never log, print, or commit secrets, API keys, or sensitive credentials.

Tool Efficiency: Parallelize independent tool calls (searching, reading files) to optimize context usage. Prefer non-interactive commands.

Absolute Paths: Use absolute paths for all file operations to ensure reliability across different working directories.

Proactive Persistence: Fulfill the user's request thoroughly, including implied follow-ups like updating documentation or tests, and persist through obstacles by diagnosing and adjusting strategies.

11. Project Construction & Environment
Local SDK & Java Version: Kotlin 1.9.x crashes with a `25.0.2` CoreJrtFileSystem NumberFormatException when run against Java 25+. Always use `openjdk@21` via `jenv` (`export PATH="$HOME/.jenv/bin:$PATH" && eval "$(jenv init -)" && jenv local 21.0`) and set it locally via `sdk.dir` and `org.gradle.java.home` in `local.properties`.
Gradle/AGP Compatibility: To avoid XML v4 parsing bugs from modern `android-commandlinetools` and configuration-cache failures, use Gradle `8.9` alongside Android Gradle Plugin `8.5.0` with `org.gradle.configuration-cache=false`.
AndroidX Requirement: Must define `android.useAndroidX=true` globally in `gradle.properties`.
CI Pipeline: GitHub Actions `ubuntu-latest` relies on native `./gradlew assembleDebug`. Maintain `if-no-files-found: error` (or omit for default) to protect pipeline integrity.