# clawly Studio - Rules & Guidelines

Welcome to the clawly Studio codebase. This folder contains architectural guidelines, coding conventions, and session logs to help maintain consistency and enable easy session resumption.

---

## Quick Start for New Sessions

1. **Read this file first** - Get oriented with the project rules
2. **Check the latest session log** in `sessions/` - See what was done recently
3. **Review ARCHITECTURE.md** if working on new features
4. **Follow CODING_CONVENTIONS.md** for code style

---

## Folder Structure

```
rules/
├── README.md              # This file - Start here!
├── ARCHITECTURE.md        # App architecture, patterns, project structure
├── CODING_CONVENTIONS.md  # Code style, file organization, naming
└── sessions/              # Session logs for continuity
    └── <date>.md          # Daily work logs
```

---

## File Guide

### ARCHITECTURE.md
The comprehensive architecture guide covering:
- Clean Architecture + MVVM pattern overview
- Project folder structure
- ViewModel, UseCase, Repository patterns with examples
- Screen composable patterns
- Dependency injection setup
- Navigation with type-safe routes
- Theme usage (Colors, Typography, Spacing)
- File naming conventions

**When to read:** Starting a new feature, onboarding, architectural decisions.

### CODING_CONVENTIONS.md
Practical coding rules:
- File organization (one model per file, component extraction)
- State class patterns
- ViewModel patterns
- Naming conventions for files, classes, functions
- Import organization
- What NOT to do (anti-patterns)
- Refactoring checklist

**When to read:** Before writing code, during code review, refactoring.

### sessions/
Session logs documenting work done. Each file represents a work session:
- **Format:** `<date>.md` (e.g., `01febr.md`)
- **Contents:** Context, tasks planned, implementation details, completion status

**When to check:** Starting a new session to see recent changes and continue work.

---

## Session Continuity Protocol

To enable easy resumption of work across sessions:

### At the END of each session, update the session log with:
1. **What was completed** - List of tasks/features done
2. **Current state** - What's working, what's pending
3. **Next steps** - What should be done next
4. **Known issues** - Any bugs or problems discovered

### At the START of each session:
1. Read the latest session log in `sessions/`
2. Check git status for uncommitted changes
3. Continue from documented next steps

### Session Log Template

```markdown
# Session Plan: <Date>

## Context
Brief description of what we're working on.

## Tasks
- [ ] Task 1
- [ ] Task 2

## Implementation Notes
Details about approach, decisions made, files modified.

## Summary
- **Completed:** List what was done
- **Pending:** What's left
- **Next steps:** What to do next session
- **Issues:** Any problems found
```

---

## Key Architecture Highlights

### Tech Stack
- **Language:** Kotlin
- **UI:** Jetpack Compose
- **Architecture:** Clean Architecture + MVVM
- **DI:** Hilt
- **Navigation:** Type-safe Compose Navigation
- **Networking:** Ktor with Kotlin Serialization
- **Image Loading:** Coil (with GIF support)

### Project Structure
```
app/src/main/java/io/breakout/hackathon/app/
├── compose/           # Shared UI components & theme
│   ├── theme/         # Colors, Typography, Theme, Space
│   └── components/    # Reusable composables
├── data/              # Data layer (models, repository, API)
│   ├── model/         # Data models (one per file!)
│   ├── repository/    # Repository implementations
│   └── remote/        # API service
├── domain/            # Domain layer (business logic)
│   ├── model/         # Domain models
│   ├── repository/    # Repository interfaces
│   └── usecase/       # Use cases
├── presentation/      # Feature screens
│   └── <feature>/
│       ├── <Feature>Screen.kt
│       ├── <Feature>ViewModel.kt
│       ├── <Feature>State.kt
│       └── components/    # Feature-specific UI components
├── navigation/        # Navigation setup
├── di/                # Hilt modules
├── auth/              # Authentication
├── billing/           # In-app purchases
├── analytics/         # Analytics tracking
└── notifications/     # Push notifications
```

### Golden Rules
1. **One model per file** (or closely related group)
2. **Extract components > 50 lines** to separate files
3. **State in data class** with default values
4. **Events via Channel** for one-time actions
5. **Use theme colors** instead of hardcoded values
6. **Add logging** with TAG constant
7. **Document sessions** for continuity

---

## Common Workflows

### Adding a New Feature
1. Create `presentation/<feature>/` folder
2. Add `<Feature>Screen.kt`, `<Feature>ViewModel.kt`, `<Feature>State.kt`
3. Add models in `data/model/`
4. Add API endpoints in data layer
5. Add repository methods if needed
6. Add navigation route in `navigation/`
7. Register in NavHost

### Refactoring a Large File
1. Identify components to extract (> 50 lines)
2. Create `components/` subdirectory
3. Move each component to separate file
4. Update imports in main file
5. Verify build compiles

### Debugging
- Check Logcat with TAG filter
- Add `Log.d(TAG, "...")` statements
- Verify API responses in network logs

---

## Quick References

**Run build:**
```bash
./gradlew :app:compileDebugKotlin
```

**State update pattern:**
```kotlin
_state.update { it.copy(isLoading = true) }
```

**Collect state in Compose:**
```kotlin
val state by viewModel.state.collectAsStateWithLifecycle()
```

**Send one-time event:**
```kotlin
_events.send(FeatureEvent.NavigateBack)
```

---

## Theme Usage

### Colors (clawly Design System)
```kotlin
Colors.clawly.primary        // Pink #FF86DF
Colors.clawly.secondary      // Purple #9159EC
Colors.clawly.background     // Dark #17171F
Colors.clawly.backgroundDark // Darker #0A0019
Colors.clawly.textWhite      // White
Colors.clawly.textGrey       // Grey #8792A7
Colors.clawly.success        // Green #7ABC27
Colors.clawly.error          // Red #F01456

// Via theme
ClawlyTheme.Colors.action // Theme-aware action color
ClawlyTheme.Colors.label  // Primary text
```

### Typography (Quicksand Font)
```kotlin
ClawlyTheme.TypographyInter.header   // 32sp SemiBold
ClawlyTheme.TypographyInter.title1   // 24sp SemiBold
ClawlyTheme.TypographyInter.title2   // 20sp SemiBold
ClawlyTheme.TypographyInter.title3   // 18sp Medium
ClawlyTheme.TypographyInter.body1    // 16sp Medium
ClawlyTheme.TypographyInter.body2    // 14sp Normal
ClawlyTheme.TypographyInter.caption  // 12sp Normal
```

### Spacing
```kotlin
Space.space2XS  // 4.dp
Space.spaceXS   // 8.dp
Space.spaceS    // 12.dp
Space.spaceM    // 16.dp
Space.spaceL    // 20.dp
Space.spaceXL   // 24.dp
Space.space2XL  // 32.dp
```

---

## Need Help?

- Architecture questions: See `ARCHITECTURE.md`
- Code style questions: See `CODING_CONVENTIONS.md`
- What was done last: Check `sessions/` folder
