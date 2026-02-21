# clawly Studio Coding Conventions

## File Organization Rules

### 1. Models - One Model Per File

Each data class/model should be in its own file:

```
data/model/
├── GenerateRequest.kt        # Single request model
├── GenerateResponse.kt       # Single response model
├── Template.kt               # Single model
├── TemplatePreview.kt        # Single model
├── Attachment.kt             # Attachment models (related can be grouped)
├── UserModels.kt             # User-related models (UserStats, UsageInfo)
├── CreatorModels.kt          # Creator-related models
└── PackModels.kt             # Pack-related models
```

**Rules:**
- One primary model per file
- Closely related models can share a file (e.g., Request + Response for same endpoint)
- Enums can be in separate files or with their related model
- File name should match the primary class name

### 2. Composables - Separate Files for Reusable Components

```
presentation/<feature>/
├── <Feature>Screen.kt        # Main screen composable
├── <Feature>ViewModel.kt     # ViewModel
├── <Feature>State.kt         # State data class + Events
└── components/               # Feature-specific components
    ├── <Feature>TopBar.kt
    ├── <Feature>Content.kt
    └── <Feature>Item.kt

compose/                       # Shared/reusable components
├── clawlyImage.kt
├── LoadingIndicator.kt
├── ErrorScreen.kt
├── TagChip.kt
└── TemplateCard.kt
```

**Rules:**
- Main screen stays in `<Feature>Screen.kt`
- Extract components > 50 lines to separate files
- Reusable components go to `compose/` directory
- Feature-specific components go to `presentation/<feature>/components/`

### 3. State Classes

Each feature should have a dedicated state file:

```kotlin
// <Feature>State.kt

// Main state class
data class FeatureState(
    val isLoading: Boolean = false,
    val isError: Boolean = false,
    val errorMessage: String? = null,
    // ... feature-specific fields
)

// Events sealed interface (if needed)
sealed interface FeatureEvent {
    data class NavigateToDetail(val id: String) : FeatureEvent
    data object ShowSuccess : FeatureEvent
}

// Supporting data classes used only in this state
data class FeatureItem(
    val id: String,
    val name: String
)
```

### 4. ViewModel Pattern

```kotlin
// <Feature>ViewModel.kt
@HiltViewModel
class FeatureViewModel @Inject constructor(
    private val repository: FeatureRepository
) : ViewModel() {

    private val _state = MutableStateFlow(FeatureState())
    val state: StateFlow<FeatureState> = _state.asStateFlow()

    // Events channel for one-time actions
    private val _events = Channel<FeatureEvent>()
    val events = _events.receiveAsFlow()

    // Public functions for UI actions
    fun onAction(action: String) { ... }
}
```

---

## Naming Conventions

### Files

| Type | Pattern | Example |
|------|---------|---------|
| Screen | `<Feature>Screen.kt` | `ChatScreen.kt` |
| ViewModel | `<Feature>ViewModel.kt` | `ChatViewModel.kt` |
| State | `<Feature>State.kt` | `ChatState.kt` |
| Component | `<Name>.kt` | `MessageBubble.kt` |
| Model | `<Name>.kt` | `GenerateRequest.kt` |
| Repository | `<Entity>Repository.kt` | `TemplateRepository.kt` |

### Classes & Functions

| Type | Pattern | Example |
|------|---------|---------|
| Screen | `<Feature>Screen` | `ChatScreen` |
| ViewModel | `<Feature>ViewModel` | `ChatViewModel` |
| State | `<Feature>State` | `ChatState` |
| Event | `<Feature>Event` | `ChatEvent` |
| Composable | PascalCase | `MessageBubble` |
| Private composable | `<Feature><Element>` | `ChatTopBar` |

---

## Code Style

### Composables

```kotlin
// Public composables - documented
/**
 * Brief description of what this composable does.
 */
@Composable
fun FeatureScreen(
    param1: Type,
    param2: Type,
    viewModel: FeatureViewModel = hiltViewModel()
) {
    // Implementation
}

// Private composables - no documentation needed
@Composable
private fun FeatureContent(
    state: FeatureState,
    onAction: (Action) -> Unit
) {
    // Implementation
}
```

### State Updates

```kotlin
// Prefer update with copy
_state.update { it.copy(isLoading = true) }

// Not this
_state.value = _state.value.copy(isLoading = true)
```

### Logging

```kotlin
private const val TAG = "FeatureViewModel"

Log.d(TAG, "action: description")
Log.e(TAG, "error: description", exception)
```

---

## Import Organization

Order imports as follows:
1. Android imports
2. AndroidX imports
3. Third-party libraries (Compose, Ktor, Coil, etc.)
4. Project imports (`io.breakout.hackathon.app.*`)

---

## What NOT to Do

1. **Don't put multiple unrelated models in one file**
   - Bad: `ApiModels.kt` with 20+ models
   - Good: Separate files per model or related group

2. **Don't put all composables in the Screen file**
   - Bad: 1000+ line `ChatScreen.kt`
   - Good: Extract to `components/` directory

3. **Don't use hardcoded colors**
   - Bad: `Color(0xFFE07A5F)`
   - Good: `ClawlyTheme.Colors.action` or `Colors.clawly.primary`

4. **Don't duplicate code**
   - Extract common components to `compose/`
   - Extract common logic to utilities

---

## Refactoring Checklist

When refactoring existing code:

- [ ] Split large model files into individual files
- [ ] Extract screen components > 50 lines
- [ ] Move reusable components to `compose/`
- [ ] Ensure consistent naming
- [ ] Add logging with TAG constant
- [ ] Remove hardcoded values
- [ ] Update imports
