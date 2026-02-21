# clawly Studio Architecture

This document describes the architecture patterns, coding conventions, and structure used in clawly Studio.

## Overview

The app follows **Clean Architecture** with **MVVM** pattern for the presentation layer:

```
┌─────────────────────────────────────────────────────────────┐
│                    PRESENTATION LAYER                        │
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────┐     │
│  │   Screen    │───▶│  ViewModel  │───▶│    State    │     │
│  │ (Composable)│◀───│   (Hilt)    │◀───│ (data class)│     │
│  └─────────────┘    └─────────────┘    └─────────────┘     │
│                            │                                 │
│                            ▼                                 │
├─────────────────────────────────────────────────────────────┤
│                      DOMAIN LAYER                            │
│  ┌─────────────────────────────────────────────────────┐    │
│  │                     UseCases                         │    │
│  │  (Business logic, data transformation, validation)   │    │
│  └─────────────────────────────────────────────────────┘    │
│                            │                                 │
│                            ▼                                 │
├─────────────────────────────────────────────────────────────┤
│                       DATA LAYER                             │
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────┐     │
│  │ Repository  │───▶│   Models    │    │   Sources   │     │
│  │ (Interface) │    │(Data/Domain)│    │(API/Local)  │     │
│  └─────────────┘    └─────────────┘    └─────────────┘     │
└─────────────────────────────────────────────────────────────┘
```

---

## Project Structure

```
app/src/main/java/io/breakout/hackathon/app/
├── compose/                    # UI Components & Design System
│   ├── theme/                  # Colors, Typography, Theme, Space
│   ├── components/             # Reusable composables
│   ├── ripple/                 # Ripple effects
│   └── utils/                  # Compose utilities (click modifiers)
│
├── data/                       # Data Layer
│   ├── model/                  # Data models (API responses, DTOs)
│   ├── repository/             # Repository implementations
│   └── source/                 # Data sources (remote, local)
│
├── domain/                     # Domain Layer (Business Logic)
│   ├── model/                  # Domain models
│   ├── repository/             # Repository interfaces
│   └── usecase/                # Use cases
│
├── presentation/               # Presentation Layer
│   └── <feature>/              # Feature package
│       ├── <Feature>Screen.kt  # Composable screen
│       ├── <Feature>ViewModel.kt
│       └── <Feature>State.kt   # UI State
│
├── navigation/                 # Navigation setup
│   ├── Routes.kt               # Type-safe routes
│   └── NavHost.kt              # NavHost configuration
│
├── di/                         # Dependency Injection (Hilt)
│   ├── AppModule.kt
│   ├── NetworkModule.kt
│   ├── RepositoryModule.kt
│   └── DataStoreModule.kt
│
├── auth/                       # Authentication
├── billing/                    # In-app purchases
├── analytics/                  # Analytics tracking
├── notifications/              # Push notifications
│
├── BreakoutAppHilt.kt          # Application class
└── MainActivity.kt             # Main activity
```

---

## ViewModel Pattern

### State Class

Create a dedicated state data class for each ViewModel:

```kotlin
// <Feature>State.kt
data class FeatureState(
    // Loading states
    val isLoading: Boolean = true,
    val isError: Boolean = false,
    val isContent: Boolean = false,
    val errorMessage: String? = null,

    // Feature-specific data
    val items: List<Item> = emptyList(),
    val selectedItem: Item? = null,

    // UI flags
    val isRefreshing: Boolean = false,
    val isDialogVisible: Boolean = false
)
```

**State Guidelines:**
- Use `isLoading`, `isError`, `isContent` flags for screen states
- Keep error messages in `errorMessage: String?`
- Use default values for all properties
- Keep state immutable (data class)

### ViewModel Class

```kotlin
// <Feature>ViewModel.kt
@HiltViewModel
class FeatureViewModel @Inject constructor(
    private val getItemsUseCase: GetItemsUseCase,
    private val deleteItemUseCase: DeleteItemUseCase
) : ViewModel() {

    // State
    private val _state = MutableStateFlow(FeatureState())
    val state: StateFlow<FeatureState> = _state.asStateFlow()

    // Events (one-time actions like navigation)
    private val _events = Channel<FeatureEvent>()
    val events = _events.receiveAsFlow()

    // Error handler
    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        _state.update { it.copy(
            isLoading = false,
            isError = true,
            isContent = false,
            errorMessage = throwable.message
        )}
    }

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch(coroutineExceptionHandler) {
            _state.update { it.copy(isLoading = true, isError = false) }

            getItemsUseCase().collect { items ->
                _state.update { it.copy(
                    isLoading = false,
                    isContent = true,
                    items = items
                )}
            }
        }
    }

    fun onItemClick(item: Item) {
        viewModelScope.launch {
            _events.send(FeatureEvent.NavigateToDetail(item.id))
        }
    }

    fun deleteItem(id: String) {
        viewModelScope.launch(coroutineExceptionHandler) {
            deleteItemUseCase(id)
        }
    }
}

// Events for navigation and one-time actions
sealed interface FeatureEvent {
    data class NavigateToDetail(val id: String) : FeatureEvent
    data object ShowSuccessToast : FeatureEvent
}
```

**ViewModel Guidelines:**
- Annotate with `@HiltViewModel`
- Use constructor injection with `@Inject constructor`
- Expose state via `StateFlow`
- Use `Channel` for one-time events
- Use `CoroutineExceptionHandler` for error handling
- Update state using `_state.update { it.copy(...) }`

---

## UseCase Pattern

```kotlin
// GetItemsUseCase.kt
class GetItemsUseCase @Inject constructor(
    private val repository: ItemRepository
) {
    operator fun invoke(): Flow<List<Item>> {
        return repository.getItems()
            .map { items -> items.sortedByDescending { it.createdAt } }
    }
}

// DeleteItemUseCase.kt
class DeleteItemUseCase @Inject constructor(
    private val repository: ItemRepository
) {
    suspend operator fun invoke(id: String) {
        repository.deleteItem(id)
    }
}
```

**UseCase Guidelines:**
- One class per use case
- Use `@Inject constructor`
- Use `operator fun invoke()` for single-action use cases
- Return `Flow` for observable data
- Use `suspend` for one-shot operations

---

## Repository Pattern

### Interface (Domain Layer)

```kotlin
// domain/repository/ItemRepository.kt
interface ItemRepository {
    fun getItems(): Flow<List<Item>>
    suspend fun getItemById(id: String): Item?
    suspend fun addItem(item: Item)
    suspend fun updateItem(item: Item)
    suspend fun deleteItem(id: String)
}
```

### Implementation (Data Layer)

```kotlin
// data/repository/ItemRepositoryImpl.kt
@Singleton
class ItemRepositoryImpl @Inject constructor(
    private val apiService: ApiService,
    private val localDataStore: DataStore<Preferences>
) : ItemRepository {

    override fun getItems(): Flow<List<Item>> {
        return flow {
            val items = apiService.getItems()
            emit(items)
        }
    }

    override suspend fun getItemById(id: String): Item? {
        return apiService.getItem(id)
    }

    override suspend fun addItem(item: Item) {
        apiService.createItem(item)
    }

    override suspend fun updateItem(item: Item) {
        apiService.updateItem(item.id, item)
    }

    override suspend fun deleteItem(id: String) {
        apiService.deleteItem(id)
    }
}
```

### API Repository (Ktor)

```kotlin
interface ApiRepository {
    suspend fun getUser(userId: String): UserResponse
    suspend fun createItem(request: CreateItemRequest): ItemResponse
    suspend fun deleteItem(itemId: String)
}

class ApiRepositoryImpl @Inject constructor(
    private val client: HttpClient,
    private val preferenceHelper: PreferenceHelper
) : ApiRepository {

    private fun addAuthHeader(
        block: HttpRequestBuilder.() -> Unit
    ): HttpRequestBuilder.() -> Unit = {
        val token = preferenceHelper.jwtToken
        if (token.isNotEmpty()) {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        block()
    }

    override suspend fun getUser(userId: String): UserResponse {
        return client.get("/api/user", addAuthHeader {
            contentType(ContentType.Application.Json)
            parameter("userId", userId)
        }).body()
    }

    override suspend fun createItem(request: CreateItemRequest): ItemResponse {
        return client.post("/api/items", addAuthHeader {
            contentType(ContentType.Application.Json)
            setBody(request)
        }).body()
    }

    override suspend fun deleteItem(itemId: String) {
        client.delete("/api/items/$itemId", addAuthHeader {
            contentType(ContentType.Application.Json)
        })
    }
}
```

---

## Screen Pattern

```kotlin
@Composable
fun FeatureScreen(
    onNavigateToDetail: (String) -> Unit,
    viewModel: FeatureViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Handle one-time events
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is FeatureEvent.NavigateToDetail -> onNavigateToDetail(event.id)
            }
        }
    }

    // Content based on state
    when {
        state.isLoading -> LoadingScreen()
        state.isError -> ErrorScreen(
            message = state.errorMessage,
            onRetry = { viewModel.loadData() }
        )
        state.isContent -> FeatureContent(
            state = state,
            onItemClick = viewModel::onItemClick,
            onDeleteItem = viewModel::deleteItem
        )
    }
}

@Composable
private fun FeatureContent(
    state: FeatureState,
    onItemClick: (Item) -> Unit,
    onDeleteItem: (String) -> Unit
) {
    // UI implementation
}
```

**Screen Guidelines:**
- Use `hiltViewModel()` for ViewModel injection
- Collect state with `collectAsStateWithLifecycle()`
- Handle events in `LaunchedEffect`
- Pass callbacks as lambdas
- Split content into private composables

---

## Dependency Injection

### Module Structure

```kotlin
// AppModule.kt - Application-scoped dependencies
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideApplicationContext(application: Application): Context {
        return application.applicationContext
    }
}

// NetworkModule.kt - Network dependencies
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideHttpClient(): HttpClient {
        return HttpClient(Android) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
            install(Logging) { level = LogLevel.BODY }
        }
    }
}

// RepositoryModule.kt - Repository bindings
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindItemRepository(impl: ItemRepositoryImpl): ItemRepository
}
```

---

## Navigation

### Type-safe Routes

```kotlin
// Routes.kt
@Serializable data object HomeRoute
@Serializable data object SettingsRoute
@Serializable data class DetailRoute(val id: String, val title: String)
```

### NavHost Setup

```kotlin
@Composable
fun clawlyNavHost(navController: NavHostController) {
    NavHost(navController = navController, startDestination = HomeRoute) {
        composable<HomeRoute> {
            HomeScreen(
                onNavigateToDetail = { id, title ->
                    navController.navigate(DetailRoute(id, title))
                }
            )
        }
        composable<DetailRoute> { backStackEntry ->
            val route: DetailRoute = backStackEntry.toRoute()
            DetailScreen(
                id = route.id,
                title = route.title,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
```

---

## Theme Usage

### clawly Design System Colors

```kotlin
// Direct access to clawly colors
Colors.clawly.primary        // Pink #FF86DF - main action color
Colors.clawly.secondary      // Purple #9159EC
Colors.clawly.background     // Dark #17171F
Colors.clawly.backgroundDark // Darker #0A0019
Colors.clawly.textWhite      // White #FFFFFF
Colors.clawly.textGrey       // Grey #8792A7
Colors.clawly.success        // Green #7ABC27
Colors.clawly.error          // Red #F01456

// Theme-aware colors
ClawlyTheme.Colors.label          // Primary text
ClawlyTheme.Colors.labelSecondary // Secondary text
ClawlyTheme.Colors.labelTertiary  // Tertiary text
ClawlyTheme.Colors.base           // Background
ClawlyTheme.Colors.baseSecondary  // Card/Surface
ClawlyTheme.Colors.action         // Primary action color (clawly pink)
ClawlyTheme.Colors.brand          // Brand accent (clawly pink)
ClawlyTheme.Colors.success        // Success state
ClawlyTheme.Colors.error          // Error state
```

### Typography (Quicksand Font)

```kotlin
ClawlyTheme.TypographyInter.header   // 32sp SemiBold - Headers
ClawlyTheme.TypographyInter.title1   // 24sp SemiBold - Large titles
ClawlyTheme.TypographyInter.title2   // 20sp SemiBold - Medium titles
ClawlyTheme.TypographyInter.title3   // 18sp Medium - Small titles
ClawlyTheme.TypographyInter.body1    // 16sp Medium - Large body
ClawlyTheme.TypographyInter.body2    // 14sp Normal - Regular body
ClawlyTheme.TypographyInter.caption  // 12sp Normal - Captions
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

## File Naming Conventions

| Type | Pattern | Example |
|------|---------|---------|
| Screen | `<Feature>Screen.kt` | `HomeScreen.kt` |
| ViewModel | `<Feature>ViewModel.kt` | `HomeViewModel.kt` |
| State | `<Feature>State.kt` | `HomeState.kt` |
| UseCase | `<Action><Entity>UseCase.kt` | `GetTemplatesUseCase.kt` |
| Repository | `<Entity>Repository.kt` | `TemplateRepository.kt` |
| Repository Impl | `<Entity>RepositoryImpl.kt` | `TemplateRepositoryImpl.kt` |
| Model | `<Entity>.kt` | `Template.kt` |
| DI Module | `<Feature>Module.kt` | `NetworkModule.kt` |

---

## Best Practices

1. **State Management**
   - Keep UI state in data classes
   - Use `StateFlow` for reactive updates
   - Use `Channel` for one-time events

2. **Error Handling**
   - Use `CoroutineExceptionHandler` in ViewModels
   - Expose error messages in state
   - Provide retry mechanisms

3. **Separation of Concerns**
   - Screens only display state
   - ViewModels manage state and business logic
   - UseCases encapsulate single operations
   - Repositories abstract data sources

4. **Dependency Injection**
   - Use constructor injection
   - Bind interfaces to implementations
   - Scope singletons appropriately

5. **Testing**
   - ViewModels are testable with fake repositories
   - UseCases are testable in isolation
   - Screens can be previewed with mock state
