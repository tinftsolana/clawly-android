 Clawly AI - Complete API & Managed Hosting Specification                                             │
│                                                                                                      │
│ Table of Contents                                                                                    │
│                                                                                                      │
│ 1. #1-architecture-overview                                                                          │
│ 2. #2-hosting-types                                                                                  │
│ 3. #3-data-models                                                                                    │
│ 4. #4-control-plane-api                                                                              │
│ 5. #5-gateway-websocket-protocol                                                                     │
│ 6. #6-ai-provider-integration                                                                        │
│ 7. #7-authentication-flow                                                                            │
│ 8. #8-skills-system                                                                                  │
│ 9. #9-error-handling                                                                                 │
│ 10. #10-key-files-reference                                                                          │
│                                                                                                      │
│ ---                                                                                                  │
│ 1. Architecture Overview                                                                             │
│                                                                                                      │
│ ┌─────────────────────────────────────────────────────────────────────────┐                          │
│ │                              iOS App                                     │                         │
│ ├─────────────────────────────────────────────────────────────────────────┤                          │
│ │  ┌─────────────────────┐  ┌─────────────────────┐  ┌─────────────────┐  │                          │
│ │  │   SettingsView      │  │   AuthProviderSheet │  │   ChatView      │  │                          │
│ │  │   (UI Layer)        │  │   (Hosting Setup)   │  │   (Chat UI)     │  │                          │
│ │  └──────────┬──────────┘  └──────────┬──────────┘  └────────┬────────┘  │                          │
│ │             │                        │                       │           │                         │
│ │  ┌──────────▼────────────────────────▼───────────────────────▼────────┐ │                          │
│ │  │                        ViewModels Layer                             │ │                         │
│ │  │  SettingsViewModel  │  ChatViewModel  │  SkillsViewModel           │ │                          │
│ │  └──────────┬────────────────────┬───────────────────┬────────────────┘ │                          │
│ │             │                    │                   │                   │                         │
│ │  ┌──────────▼────────────────────▼───────────────────▼────────────────┐ │                          │
│ │  │                         Services Layer                              │ │                         │
│ │  │  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────────┐ │ │                          │
│ │  │  │AuthProviderSvc  │  │ControlPlaneSvc  │  │   GatewayService    │ │ │                          │
│ │  │  │(State + Persist)│  │(REST API Client)│  │(WebSocket Client)   │ │ │                          │
│ │  │  └────────┬────────┘  └────────┬────────┘  └─────────┬───────────┘ │ │                          │
│ │  └───────────┼────────────────────┼─────────────────────┼─────────────┘ │                          │
│ └──────────────┼────────────────────┼─────────────────────┼───────────────┘                          │
│                │                    │                     │                                          │
│                ▼                    ▼                     ▼                                          │
│ ┌──────────────────────┐  ┌─────────────────────┐  ┌──────────────────────┐                          │
│ │     UserDefaults     │  │  Control Plane API  │  │  OpenClaw Gateway    │                          │
│ │     (Local State)    │  │  157.245.185.252    │  │  wss://...           │                          │
│ └──────────────────────┘  └─────────────────────┘  └──────────────────────┘                          │
│                                                                                                      │
│ ---                                                                                                  │
│ 2. Hosting Types                                                                                     │
│                                                                                                      │
│ 2.1 HostingType Enum                                                                                 │
│                                                                                                      │
│ enum HostingType: String, Codable, CaseIterable {                                                    │
│     case managed = "managed"                                                                         │
│     case selfHosted = "self_hosted"                                                                  │
│ }                                                                                                    │
│                                                                                                      │
│ ┌────────────┬─────────────────┬─────────────┬───────────────────────────────┐                       │
│ │    Type    │  Display Name   │    Icon     │          Description          │                       │
│ ├────────────┼─────────────────┼─────────────┼───────────────────────────────┤                       │
│ │ managed    │ Managed Hosting │ cloud.fill  │ We'll set up a server for you │                       │
│ ├────────────┼─────────────────┼─────────────┼───────────────────────────────┤                       │
│ │ selfHosted │ Self-Hosted     │ server.rack │ Connect to your own gateway   │                       │
│ └────────────┴─────────────────┴─────────────┴───────────────────────────────┘                       │
│                                                                                                      │
│ 2.2 Managed Hosting Flow                                                                             │
│                                                                                                      │
│ User selects "Managed Hosting"                                                                       │
│          │                                                                                           │
│          ▼                                                                                           │
│ ┌─────────────────────────────────────┐                                                              │
│ │  AuthProviderService                │                                                              │
│ │  .createManagedInstance()           │                                                              │
│ └────────────────┬────────────────────┘                                                              │
│                  │                                                                                   │
│          ┌───────▼───────┐                                                                           │
│          │ ensureUserId()│ ◄─── Uses DeviceIdentityService.shared.deviceId                           │
│          └───────┬───────┘      (same ID as RevenueCat)                                              │
│                  │                                                                                   │
│          ┌───────▼───────┐                                                                           │
│          │getUserTenants()│ ◄─── Check for existing instances                                        │
│          └───────┬───────┘                                                                           │
│                  │                                                                                   │
│         ┌────────┴────────┐                                                                          │
│         ▼                 ▼                                                                          │
│    Has Instance?     No Instance                                                                     │
│         │                 │                                                                          │
│         │         ┌───────▼───────┐                                                                  │
│         │         │createInstance()│                                                                 │
│         │         └───────┬───────┘                                                                  │
│         │                 │                                                                          │
│         └────────┬────────┘                                                                          │
│                  ▼                                                                                   │
│ ┌─────────────────────────────────────┐                                                              │
│ │  Start Polling (5 second interval) │                                                               │
│ │  Until status != isInProgress       │                                                              │
│ └────────────────┬────────────────────┘                                                              │
│                  │                                                                                   │
│                  ▼                                                                                   │
│          ┌───────────────┐                                                                           │
│          │ Status: ready │                                                                           │
│          └───────┬───────┘                                                                           │
│                  │                                                                                   │
│          ┌───────▼───────┐                                                                           │
│          │Update Gateway │                                                                           │
│          │Config + Reconnect│                                                                        │
│          └───────────────┘                                                                           │
│                                                                                                      │
│ 2.3 Self-Hosted Flow                                                                                 │
│                                                                                                      │
│ User selects "Self-Hosted"                                                                           │
│          │                                                                                           │
│          ▼                                                                                           │
│ ┌─────────────────────────────────────┐                                                              │
│ │  User enters:                       │                                                              │
│ │  - Gateway URL (wss://...)          │                                                              │
│ │  - Auth Token                       │                                                              │
│ └────────────────┬────────────────────┘                                                              │
│                  │                                                                                   │
│          ┌───────▼───────┐                                                                           │
│          │ setProvider() │                                                                           │
│          └───────┬───────┘                                                                           │
│                  │                                                                                   │
│          ┌───────▼───────┐                                                                           │
│          │Update Gateway │                                                                           │
│          │Config + Reconnect│                                                                        │
│          └───────────────┘                                                                           │
│                                                                                                      │
│ ---                                                                                                  │
│ 3. Data Models                                                                                       │
│                                                                                                      │
│ 3.1 AuthProviderConfig                                                                               │
│                                                                                                      │
│ struct AuthProviderConfig: Codable, Equatable {                                                      │
│     var hostingType: HostingType?                                                                    │
│                                                                                                      │
│     // For self-hosted                                                                               │
│     var wssUrl: String?                                                                              │
│     var wssToken: String?                                                                            │
│                                                                                                      │
│     // For managed hosting                                                                           │
│     var managedInstance: ManagedInstanceInfo?                                                        │
│                                                                                                      │
│     // Computed properties                                                                           │
│     var isConfigured: Bool              // Ready to use                                              │
│     var isProvisioning: Bool            // Status is in progress                                     │
│     var effectiveGatewayUrl: String?    // Works for both types                                      │
│     var effectiveGatewayToken: String?  // Works for both types                                      │
│ }                                                                                                    │
│                                                                                                      │
│ 3.2 ManagedInstanceInfo                                                                              │
│                                                                                                      │
│ struct ManagedInstanceInfo: Codable, Equatable {                                                     │
│     var tenantId: String                                                                             │
│     var status: ManagedInstanceStatus                                                                │
│     var lastError: String?                                                                           │
│     var gatewayUrl: String?                                                                          │
│     var gatewayToken: String?                                                                        │
│     var createdAt: Date?                                                                             │
│     var updatedAt: Date?                                                                             │
│                                                                                                      │
│     var isReady: Bool { status == .ready && gatewayUrl != nil }                                      │
│ }                                                                                                    │
│                                                                                                      │
│ 3.3 ManagedInstanceStatus                                                                            │
│                                                                                                      │
│ enum ManagedInstanceStatus: String, Codable {                                                        │
│     case queued         // Waiting in provisioning queue                                             │
│     case provisioning   // Cloud server being created (Fly.io)                                       │
│     case installing     // Gateway software being installed                                          │
│     case ready          // Fully operational                                                         │
│     case failed         // Provisioning failed                                                       │
│     case suspended      // Instance suspended                                                        │
│                                                                                                      │
│     var isInProgress: Bool {                                                                         │
│         switch self {                                                                                │
│         case .queued, .provisioning, .installing: return true                                        │
│         case .ready, .failed, .suspended: return false                                               │
│         }                                                                                            │
│     }                                                                                                │
│ }                                                                                                    │
│                                                                                                      │
│ 3.4 AIProviderType (for connecting AI to managed instance)                                           │
│                                                                                                      │
│ enum AIProviderType: String, CaseIterable {                                                          │
│     case openaiOAuth = "openai_oauth"                                                                │
│     case anthropic = "anthropic"                                                                     │
│     case openaiApiKey = "openai_api_key"                                                             │
│ }                                                                                                    │
│                                                                                                      │
│ Provider: openaiOAuth                                                                                │
│ Display Name: Login with OpenAI                                                                      │
│ Auth Method: OAuth WebView                                                                           │
│ Key Format: N/A                                                                                      │
│ Icon: person.badge.key                                                                               │
│ ────────────────────────────────────────                                                             │
│ Provider: openaiApiKey                                                                               │
│ Display Name: ChatGPT API Key                                                                        │
│ Auth Method: Manual entry                                                                            │
│ Key Format: sk-... (min 20 chars)                                                                    │
│ Icon: key.fill                                                                                       │
│ ────────────────────────────────────────                                                             │
│ Provider: anthropic                                                                                  │
│ Display Name: Claude API Key                                                                         │
│ Auth Method: Manual entry                                                                            │
│ Key Format: sk-ant-... (min 20 chars)                                                                │
│ Icon: brain.head.profile                                                                             │
│                                                                                                      │
│ 3.5 GatewayConfig (Local Storage)                                                                    │
│                                                                                                      │
│ struct GatewayConfig {                                                                               │
│     static let defaultURL = "wss://167.172.219.64"                                                   │
│     static let defaultToken = "ba861d953af9137b46e6d8c2de3965aaba9608857559249c21d8b401076bddab"     │
│     static let defaultSessionKey = "agent:main:main"                                                 │
│                                                                                                      │
│     static var savedURL: String          // UserDefaults: "gateway_url"                              │
│     static var savedToken: String        // UserDefaults: "gateway_token"                            │
│     static var savedSessionKey: String   // UserDefaults: "gateway_session_key"                      │
│     static var savedDeviceToken: String? // UserDefaults: "gateway_device_token"                     │
│                                                                                                      │
│     // Debug mode uses defaults if no explicit value set                                             │
│     static var useDebugDefaults: Bool    // DEBUG only                                               │
│ }                                                                                                    │
│                                                                                                      │
│ ---                                                                                                  │
│ 4. Control Plane API                                                                                 │
│                                                                                                      │
│ 4.1 Configuration                                                                                    │
│                                                                                                      │
│ // Base URL                                                                                          │
│ let baseURL = "http://157.245.185.252:3003"                                                          │
│                                                                                                      │
│ // Session Configuration                                                                             │
│ let timeoutIntervalForRequest = 30    // seconds                                                     │
│ let timeoutIntervalForResource = 60   // seconds                                                     │
│                                                                                                      │
│ // Date Decoding                                                                                     │
│ decoder.dateDecodingStrategy = .iso8601                                                              │
│                                                                                                      │
│ 4.2 Required Headers                                                                                 │
│                                                                                                      │
│ ┌────────────────┬────────────────────────┬──────────────────────────────────────┐                   │
│ │     Header     │        Required        │             Description              │                   │
│ ├────────────────┼────────────────────────┼──────────────────────────────────────┤                   │
│ │ X-User-Id      │ Yes (for instance ops) │ Device ID from DeviceIdentityService │                   │
│ ├────────────────┼────────────────────────┼──────────────────────────────────────┤                   │
│ │ Content-Type   │ Yes (POST/PATCH)       │ application/json                     │                   │
│ ├────────────────┼────────────────────────┼──────────────────────────────────────┤                   │
│ │ X-Bypass-Token │ No (DEBUG only)        │ Skips subscription check             │                   │
│ └────────────────┴────────────────────────┴──────────────────────────────────────┘                   │
│                                                                                                      │
│ 4.3 User Management Endpoints                                                                        │
│                                                                                                      │
│ Create User                                                                                          │
│                                                                                                      │
│ POST /users                                                                                          │
│ Content-Type: application/json                                                                       │
│                                                                                                      │
│ Body: {}                                                                                             │
│                                                                                                      │
│ Response 201:                                                                                        │
│ {                                                                                                    │
│   "userId": "string"                                                                                 │
│ }                                                                                                    │
│                                                                                                      │
│ Get User                                                                                             │
│                                                                                                      │
│ GET /users/{userId}                                                                                  │
│                                                                                                      │
│ Response 200:                                                                                        │
│ {                                                                                                    │
│   "userId": "string",                                                                                │
│   "createdAt": "2024-01-15T10:30:00Z",                                                               │
│   "updatedAt": "2024-01-15T10:30:00Z"                                                                │
│ }                                                                                                    │
│                                                                                                      │
│ Get User's Tenants/Instances                                                                         │
│                                                                                                      │
│ GET /users/{userId}/tenants                                                                          │
│ X-User-Id: {userId}                                                                                  │
│                                                                                                      │
│ Response 200:                                                                                        │
│ [                                                                                                    │
│   {                                                                                                  │
│     "tenantId": "uuid",                                                                              │
│     "status": "ready",                                                                               │
│     "lastError": null,                                                                               │
│     "instance": {                                                                                    │
│       "provider": "fly",                                                                             │
│       "serverId": 123,                                                                               │
│       "serverName": "clawly-xyz",                                                                    │
│       "vmIp": "1.2.3.4",                                                                             │
│       "sshUser": "root",                                                                             │
│       "gatewayPort": 3000,                                                                           │
│       "gatewayUrl": "wss://clawly-xyz.fly.dev",                                                      │
│       "gatewayToken": "token-string",                                                                │
│       "flyAppName": "clawly-xyz",                                                                    │
│       "flyMachineId": "machine-id",                                                                  │
│       "flyVolumeId": "volume-id"                                                                     │
│     },                                                                                               │
│     "createdAt": "2024-01-15T10:30:00Z",                                                             │
│     "updatedAt": "2024-01-15T10:35:00Z"                                                              │
│   }                                                                                                  │
│ ]                                                                                                    │
│                                                                                                      │
│ 4.4 Instance Management Endpoints                                                                    │
│                                                                                                      │
│ Create Instance                                                                                      │
│                                                                                                      │
│ POST /instances                                                                                      │
│ Content-Type: application/json                                                                       │
│ X-User-Id: {userId}                                                                                  │
│ X-Bypass-Token: {token}  # Optional, DEBUG only                                                      │
│                                                                                                      │
│ Body: {}                                                                                             │
│                                                                                                      │
│ Response 201:                                                                                        │
│ {                                                                                                    │
│   "tenantId": "uuid",                                                                                │
│   "status": "queued"                                                                                 │
│ }                                                                                                    │
│                                                                                                      │
│ Errors:                                                                                              │
│ - 403 "subscription_required" - No active Pro subscription                                           │
│ - 400 "missing_x_user_id" - X-User-Id header required                                                │
│                                                                                                      │
│ Get Instance Status                                                                                  │
│                                                                                                      │
│ GET /instances/{tenantId}                                                                            │
│ X-User-Id: {userId}                                                                                  │
│ X-Bypass-Token: {token}  # Optional, DEBUG only                                                      │
│                                                                                                      │
│ Response 200:                                                                                        │
│ {                                                                                                    │
│   "tenantId": "uuid",                                                                                │
│   "status": "queued|provisioning|installing|ready|failed|suspended",                                 │
│   "lastError": "string or null",                                                                     │
│   "instance": {                                                                                      │
│     "provider": "fly",                                                                               │
│     "serverId": 123,                                                                                 │
│     "serverName": "clawly-xyz",                                                                      │
│     "vmIp": "1.2.3.4",                                                                               │
│     "sshUser": "root",                                                                               │
│     "gatewayPort": 3000,                                                                             │
│     "gatewayUrl": "wss://clawly-xyz.fly.dev",                                                        │
│     "gatewayToken": "token-string",                                                                  │
│     "flyAppName": "clawly-xyz",                                                                      │
│     "flyMachineId": "machine-id",                                                                    │
│     "flyVolumeId": "volume-id"                                                                       │
│   },                                                                                                 │
│   "createdAt": "2024-01-15T10:30:00Z",                                                               │
│   "updatedAt": "2024-01-15T10:35:00Z"                                                                │
│ }                                                                                                    │
│                                                                                                      │
│ Errors:                                                                                              │
│ - 404 "not_found" - Instance not found                                                               │
│                                                                                                      │
│ Refresh Instance (Force Status Check)                                                                │
│                                                                                                      │
│ POST /instances/{tenantId}/refresh                                                                   │
│ Content-Type: application/json                                                                       │
│ X-User-Id: {userId}                                                                                  │
│ X-Bypass-Token: {token}  # Optional, DEBUG only                                                      │
│                                                                                                      │
│ Body: {}                                                                                             │
│                                                                                                      │
│ Response 200: (same as GET /instances/{tenantId})                                                    │
│                                                                                                      │
│ Delete Instance                                                                                      │
│                                                                                                      │
│ DELETE /instances/{tenantId}                                                                         │
│ X-User-Id: {userId}                                                                                  │
│                                                                                                      │
│ Response 200 or 204                                                                                  │
│                                                                                                      │
│ 4.5 Provider Authentication Endpoints                                                                │
│                                                                                                      │
│ Start OpenAI OAuth                                                                                   │
│                                                                                                      │
│ POST /instances/{tenantId}/auth/openai/start                                                         │
│ Content-Type: application/json                                                                       │
│                                                                                                      │
│ Body: {}                                                                                             │
│                                                                                                      │
│ Response 200:                                                                                        │
│ {                                                                                                    │
│   "authUrl": "https://auth.openai.com/authorize?client_id=...&redirect_uri=..."                      │
│ }                                                                                                    │
│                                                                                                      │
│ Complete OpenAI OAuth                                                                                │
│                                                                                                      │
│ POST /instances/{tenantId}/auth/openai/callback                                                      │
│ Content-Type: application/json                                                                       │
│                                                                                                      │
│ Body:                                                                                                │
│ {                                                                                                    │
│   "callbackUrl": "http://localhost/callback?code=AUTH_CODE&state=STATE"                              │
│ }                                                                                                    │
│                                                                                                      │
│ Response 200                                                                                         │
│                                                                                                      │
│ Errors:                                                                                              │
│ - 400 "oauth_state_not_found_or_expired" - OAuth session expired                                     │
│ - 400 "oauth_state_tenant_mismatch" - OAuth state doesn't match tenant                               │
│                                                                                                      │
│ Set OpenAI API Key (Direct)                                                                          │
│                                                                                                      │
│ POST /instances/{tenantId}/auth/openai/key                                                           │
│ Content-Type: application/json                                                                       │
│                                                                                                      │
│ Body:                                                                                                │
│ {                                                                                                    │
│   "apiKey": "sk-..."                                                                                 │
│ }                                                                                                    │
│                                                                                                      │
│ Response 200                                                                                         │
│                                                                                                      │
│ Set Anthropic API Key                                                                                │
│                                                                                                      │
│ POST /instances/{tenantId}/auth/anthropic/key                                                        │
│ Content-Type: application/json                                                                       │
│                                                                                                      │
│ Body:                                                                                                │
│ {                                                                                                    │
│   "apiKey": "sk-ant-api03-..."                                                                       │
│ }                                                                                                    │
│                                                                                                      │
│ Response 200                                                                                         │
│                                                                                                      │
│ Errors:                                                                                              │
│ - 400 "invalid_anthropic_api_key" - Bad API key format                                               │
│                                                                                                      │
│ Set Anthropic Setup Token                                                                            │
│                                                                                                      │
│ POST /instances/{tenantId}/auth/anthropic/setup-token                                                │
│ Content-Type: application/json                                                                       │
│                                                                                                      │
│ Body:                                                                                                │
│ {                                                                                                    │
│   "setupToken": "setup-token-string"                                                                 │
│ }                                                                                                    │
│                                                                                                      │
│ Response 200                                                                                         │
│                                                                                                      │
│ Errors:                                                                                              │
│ - 400 "invalid_anthropic_setup_token" - Bad setup token format                                       │
│                                                                                                      │
│ 4.6 API Response Models                                                                              │
│                                                                                                      │
│ // Instance creation response                                                                        │
│ struct CreateTenantResponse: Codable {                                                               │
│     let tenantId: String                                                                             │
│     let status: String                                                                               │
│ }                                                                                                    │
│                                                                                                      │
│ // Instance status response                                                                          │
│ struct GetTenantResponse: Codable {                                                                  │
│     let tenantId: String                                                                             │
│     let status: String                                                                               │
│     let lastError: String?                                                                           │
│     let instance: TenantInstanceResponse?                                                            │
│     let createdAt: String                                                                            │
│     let updatedAt: String                                                                            │
│ }                                                                                                    │
│                                                                                                      │
│ // Instance details                                                                                  │
│ struct TenantInstanceResponse: Codable {                                                             │
│     let provider: String?                                                                            │
│     let serverId: Int?                                                                               │
│     let serverName: String?                                                                          │
│     let vmIp: String?                                                                                │
│     let sshUser: String?                                                                             │
│     let gatewayPort: Int?                                                                            │
│     let gatewayUrl: String?                                                                          │
│     let gatewayToken: String?                                                                        │
│     let flyAppName: String?                                                                          │
│     let flyMachineId: String?                                                                        │
│     let flyVolumeId: String?                                                                         │
│ }                                                                                                    │
│                                                                                                      │
│ // OAuth start response                                                                              │
│ struct OpenAIOAuthStartResponse: Codable {                                                           │
│     let authUrl: String                                                                              │
│ }                                                                                                    │
│                                                                                                      │
│ // Error response                                                                                    │
│ struct ControlPlaneError: Codable {                                                                  │
│     let error: String                                                                                │
│ }                                                                                                    │
│                                                                                                      │
│ ---                                                                                                  │
│ 5. Gateway WebSocket Protocol                                                                        │
│                                                                                                      │
│ 5.1 Connection Configuration                                                                         │
│                                                                                                      │
│ // URL Building                                                                                      │
│ - Accepts: wss://host, ws://host, https://host, http://host                                          │
│ - Auto-converts http:// to ws://, https:// to wss://                                                 │
│ - Strips legacy Socket.IO paths (/socket.io/)                                                        │
│ - Default: wss://167.172.219.64                                                                      │
│                                                                                                      │
│ // Origin Header (Required)                                                                          │
│ - Format: https://host[:port] or http://host[:port]                                                  │
│ - Non-default ports are included                                                                     │
│ - Gateway enforces Origin allow-list                                                                 │
│                                                                                                      │
│ // Timeouts                                                                                          │
│ - Request timeout: 10 seconds                                                                        │
│ - Connection timeout: 30 seconds                                                                     │
│ - Ping interval: 30 seconds                                                                          │
│                                                                                                      │
│ // Reconnection                                                                                      │
│ - Max attempts: 5                                                                                    │
│ - Base delay: 3 seconds (exponential backoff)                                                        │
│                                                                                                      │
│ 5.2 Message Wire Format                                                                              │
│                                                                                                      │
│ All messages are JSON over WebSocket:                                                                │
│                                                                                                      │
│ enum GatewayWireType: String {                                                                       │
│     case req    // Request (client -> server)                                                        │
│     case res    // Response (server -> client)                                                       │
│     case event  // Event (server -> client)                                                          │
│ }                                                                                                    │
│                                                                                                      │
│ Request Message                                                                                      │
│                                                                                                      │
│ {                                                                                                    │
│   "type": "req",                                                                                     │
│   "id": "uuid-string",                                                                               │
│   "method": "method.name",                                                                           │
│   "params": {                                                                                        │
│     "key1": "value1",                                                                                │
│     "key2": "value2"                                                                                 │
│   }                                                                                                  │
│ }                                                                                                    │
│                                                                                                      │
│ Response Message                                                                                     │
│                                                                                                      │
│ {                                                                                                    │
│   "type": "res",                                                                                     │
│   "id": "uuid-string",                                                                               │
│   "ok": true,                                                                                        │
│   "payload": {                                                                                       │
│     "resultKey": "resultValue"                                                                       │
│   }                                                                                                  │
│ }                                                                                                    │
│                                                                                                      │
│ Error Response                                                                                       │
│                                                                                                      │
│ {                                                                                                    │
│   "type": "res",                                                                                     │
│   "id": "uuid-string",                                                                               │
│   "ok": false,                                                                                       │
│   "error": {                                                                                         │
│     "code": "ERROR_CODE",                                                                            │
│     "message": "Error description",                                                                  │
│     "details": {                                                                                     │
│       "requestId": "pairing-request-id"                                                              │
│     }                                                                                                │
│   }                                                                                                  │
│ }                                                                                                    │
│                                                                                                      │
│ Event Message                                                                                        │
│                                                                                                      │
│ {                                                                                                    │
│   "type": "event",                                                                                   │
│   "event": "event.name",                                                                             │
│   "payload": {                                                                                       │
│     "eventData": "value"                                                                             │
│   }                                                                                                  │
│ }                                                                                                    │
│                                                                                                      │
│ 5.3 Connection Handshake                                                                             │
│                                                                                                      │
│ 1. Client initiates WebSocket connection with Origin header                                          │
│          │                                                                                           │
│          ▼                                                                                           │
│ 2. Server sends challenge event                                                                      │
│    {                                                                                                 │
│      "type": "event",                                                                                │
│      "event": "connect.challenge",                                                                   │
│      "payload": { "nonce": "challenge-string" }                                                      │
│    }                                                                                                 │
│          │                                                                                           │
│          ▼                                                                                           │
│ 3. Client sends connect request                                                                      │
│    {                                                                                                 │
│      "type": "req",                                                                                  │
│      "id": "uuid",                                                                                   │
│      "method": "connect",                                                                            │
│      "params": {                                                                                     │
│        "minProtocol": 3,                                                                             │
│        "maxProtocol": 3,                                                                             │
│        "client": {                                                                                   │
│          "id": "openclaw-ios",                                                                       │
│          "version": "1.0.0",                                                                         │
│          "platform": "ios",                                                                          │
│          "mode": "webchat",                                                                          │
│          "instanceId": "clawdbot"                                                                    │
│        },                                                                                            │
│        "role": "operator",                                                                           │
│        "scopes": ["operator.admin", "operator.approvals", "operator.pairing"],                       │
│        "caps": [],                                                                                   │
│        "userAgent": "clawdbot-ios",                                                                  │
│        "locale": "en-US",                                                                            │
│        "auth": { "token": "gateway-or-device-token" },                                               │
│        "device": { ... }  // Optional signed device auth                                             │
│      }                                                                                               │
│    }                                                                                                 │
│          │                                                                                           │
│          ▼                                                                                           │
│ 4. Server responds with success + optional device token                                              │
│    {                                                                                                 │
│      "type": "res",                                                                                  │
│      "id": "uuid",                                                                                   │
│      "ok": true,                                                                                     │
│      "payload": {                                                                                    │
│        "auth": {                                                                                     │
│          "deviceToken": "new-device-token"                                                           │
│        }                                                                                             │
│      }                                                                                               │
│    }                                                                                                 │
│                                                                                                      │
│ 5.4 Device Identity & Signing (Curve25519)                                                           │
│                                                                                                      │
│ // Identity Structure                                                                                │
│ struct DeviceIdentity {                                                                              │
│     let privateSeed: Data      // 32-byte Curve25519 private seed                                    │
│     let publicKey: Data        // Derived public key                                                 │
│     let deviceId: String       // SHA256 hex of public key                                           │
│ }                                                                                                    │
│                                                                                                      │
│ // Stored in UserDefaults: "gateway_device_private_seed"                                             │
│ // Device token stored in: "gateway_device_token"                                                    │
│                                                                                                      │
│ Signature Creation                                                                                   │
│                                                                                                      │
│ // Signature string format (v1 - no nonce)                                                           │
│ let signString = [                                                                                   │
│     "v1",                                                                                            │
│     deviceId,                                                                                        │
│     clientId,         // "openclaw-ios"                                                              │
│     clientMode,       // "webchat"                                                                   │
│     role,             // "operator"                                                                  │
│     scopes.joined(separator: ","),                                                                   │
│     String(signedAtMs),                                                                              │
│     tokenString                                                                                      │
│ ].joined(separator: "|")                                                                             │
│                                                                                                      │
│ // Signature string format (v2 - with nonce)                                                         │
│ let signString = [                                                                                   │
│     "v2",                                                                                            │
│     deviceId,                                                                                        │
│     clientId,                                                                                        │
│     clientMode,                                                                                      │
│     role,                                                                                            │
│     scopes.joined(separator: ","),                                                                   │
│     String(signedAtMs),                                                                              │
│     tokenString,                                                                                     │
│     nonce           // From connect.challenge                                                        │
│ ].joined(separator: "|")                                                                             │
│                                                                                                      │
│ // Sign with Curve25519                                                                              │
│ let signature = privateKey.signature(for: Data(signString.utf8))                                     │
│                                                                                                      │
│ // Device payload                                                                                    │
│ {                                                                                                    │
│   "id": "device-id-sha256-hex",                                                                      │
│   "publicKey": "base64url-encoded-public-key",                                                       │
│   "signature": "base64url-encoded-signature",                                                        │
│   "signedAt": 1697234567890,                                                                         │
│   "nonce": "challenge-nonce"  // v2 only                                                             │
│ }                                                                                                    │
│                                                                                                      │
│ 5.5 Authentication Modes                                                                             │
│                                                                                                      │
│ enum ConnectAuthMode {                                                                               │
│     case tokenOnly      // Default - send auth.token                                                 │
│     case signedDevice   // Add device signature (if required)                                        │
│ }                                                                                                    │
│                                                                                                      │
│ enum TokenSource {                                                                                   │
│     case deviceToken    // Cached from previous pairing                                              │
│     case gatewayToken   // Configured gateway token                                                  │
│     case none                                                                                        │
│ }                                                                                                    │
│                                                                                                      │
│ // Token preference order:                                                                           │
│ // 1. Device token (if cached from pairing)                                                          │
│ // 2. Gateway token (fallback)                                                                       │
│                                                                                                      │
│ 5.6 Chat Methods                                                                                     │
│                                                                                                      │
│ chat.send                                                                                            │
│                                                                                                      │
│ {                                                                                                    │
│   "method": "chat.send",                                                                             │
│   "params": {                                                                                        │
│     "sessionKey": "agent:main:main",                                                                 │
│     "message": "Hello, how are you?",                                                                │
│     "deliver": false,                                                                                │
│     "idempotencyKey": "uuid",                                                                        │
│     "skills": [                                                                                      │
│       {                                                                                              │
│         "id": "skill-id",                                                                            │
│         "name": "web-search",                                                                        │
│         "content": "# Web Search\nSearch the web for information..."                                 │
│       }                                                                                              │
│     ],                                                                                               │
│     "attachments": [                                                                                 │
│       {                                                                                              │
│         "type": "file",                                                                              │
│         "mimeType": "image/jpeg",                                                                    │
│         "fileName": "photo.jpg",                                                                     │
│         "content": "base64-encoded-data"                                                             │
│       }                                                                                              │
│     ]                                                                                                │
│   }                                                                                                  │
│ }                                                                                                    │
│                                                                                                      │
│ Response: { "ok": true }                                                                             │
│                                                                                                      │
│ chat.history                                                                                         │
│                                                                                                      │
│ {                                                                                                    │
│   "method": "chat.history",                                                                          │
│   "params": {                                                                                        │
│     "sessionKey": "agent:main:main"                                                                  │
│   }                                                                                                  │
│ }                                                                                                    │
│                                                                                                      │
│ Response:                                                                                            │
│ {                                                                                                    │
│   "messages": [                                                                                      │
│     {                                                                                                │
│       "id": "msg-123",                                                                               │
│       "role": "user",                                                                                │
│       "content": "Hello",                                                                            │
│       "timestamp": 1697234567000                                                                     │
│     },                                                                                               │
│     {                                                                                                │
│       "id": "msg-124",                                                                               │
│       "role": "assistant",                                                                           │
│       "content": "Hi there!",                                                                        │
│       "timestamp": 1697234568000                                                                     │
│     }                                                                                                │
│   ]                                                                                                  │
│ }                                                                                                    │
│                                                                                                      │
│ chat.abort                                                                                           │
│                                                                                                      │
│ {                                                                                                    │
│   "method": "chat.abort",                                                                            │
│   "params": {                                                                                        │
│     "sessionKey": "agent:main:main"                                                                  │
│   }                                                                                                  │
│ }                                                                                                    │
│                                                                                                      │
│ Response: { "ok": true }                                                                             │
│                                                                                                      │
│ 5.7 Chat Events                                                                                      │
│                                                                                                      │
│ {                                                                                                    │
│   "type": "event",                                                                                   │
│   "event": "chat",                                                                                   │
│   "payload": {                                                                                       │
│     "state": "streaming|partial|final",                                                              │
│     "message": {                                                                                     │
│       "role": "assistant",                                                                           │
│       "content": "Hello! How can I...",                                                              │
│       "id": "msg-id",                                                                                │
│       "timestamp": 1697234567000                                                                     │
│     }                                                                                                │
│   }                                                                                                  │
│ }                                                                                                    │
│                                                                                                      │
│ ┌───────────┬───────────────────────────────────┐                                                    │
│ │   State   │            Description            │                                                    │
│ ├───────────┼───────────────────────────────────┤                                                    │
│ │ streaming │ Partial response being received   │                                                    │
│ ├───────────┼───────────────────────────────────┤                                                    │
│ │ partial   │ Partial response being received   │                                                    │
│ ├───────────┼───────────────────────────────────┤                                                    │
│ │ final     │ Complete response, streaming done │                                                    │
│ └───────────┴───────────────────────────────────┘                                                    │
│                                                                                                      │
│ 5.8 Streaming State                                                                                  │
│                                                                                                      │
│ enum StreamingState: Equatable {                                                                     │
│     case idle                              // No active streaming                                    │
│     case streaming(partialContent: String) // Receiving partial response                             │
│     case complete                          // Response complete                                      │
│ }                                                                                                    │
│                                                                                                      │
│ ---                                                                                                  │
│ 6. AI Provider Integration                                                                           │
│                                                                                                      │
│ 6.1 OpenAI OAuth Flow                                                                                │
│                                                                                                      │
│ 1. User taps "Login with OpenAI"                                                                     │
│          │                                                                                           │
│          ▼                                                                                           │
│ 2. App calls: POST /instances/{tenantId}/auth/openai/start                                           │
│          │                                                                                           │
│          ▼                                                                                           │
│ 3. Server returns authUrl                                                                            │
│          │                                                                                           │
│          ▼                                                                                           │
│ 4. App presents WKWebView (ephemeral session) with authUrl                                           │
│          │                                                                                           │
│          ▼                                                                                           │
│ 5. User completes OpenAI login in WebView                                                            │
│          │                                                                                           │
│          ▼                                                                                           │
│ 6. OpenAI redirects to localhost callback URL                                                        │
│          │                                                                                           │
│          ▼                                                                                           │
│ 7. App intercepts localhost navigation (WKNavigationDelegate)                                        │
│          │                                                                                           │
│          ▼                                                                                           │
│ 8. App calls: POST /instances/{tenantId}/auth/openai/callback                                        │
│    Body: { "callbackUrl": "http://localhost/callback?code=...&state=..." }                           │
│          │                                                                                           │
│          ▼                                                                                           │
│ 9. Server exchanges code for token, stores on instance                                               │
│          │                                                                                           │
│          ▼                                                                                           │
│ 10. App saves provider selection: UserDefaults["selected_ai_provider"] = "openai_oauth"              │
│                                                                                                      │
│ 6.2 API Key Flow (OpenAI or Anthropic)                                                               │
│                                                                                                      │
│ 1. User enters API key in text field                                                                 │
│          │                                                                                           │
│          ▼                                                                                           │
│ 2. App validates key format:                                                                         │
│    - OpenAI: starts with "sk-", min 20 chars                                                         │
│    - Anthropic: starts with "sk-ant-", min 20 chars                                                  │
│          │                                                                                           │
│          ▼                                                                                           │
│ 3. App calls appropriate endpoint:                                                                   │
│    - OpenAI: POST /instances/{tenantId}/auth/openai/key                                              │
│    - Anthropic: POST /instances/{tenantId}/auth/anthropic/key                                        │
│          │                                                                                           │
│          ▼                                                                                           │
│ 4. Server validates and stores key on instance                                                       │
│          │                                                                                           │
│          ▼                                                                                           │
│ 5. App saves provider selection: UserDefaults["selected_ai_provider"] = "openai_api_key" |           │
│ "anthropic"                                                                                          │
│                                                                                                      │
│ ---                                                                                                  │
│ 7. Authentication Flow                                                                               │
│                                                                                                      │
│ 7.1 AuthProviderService State Management                                                             │
│                                                                                                      │
│ final class AuthProviderService {                                                                    │
│     static let shared = AuthProviderService()                                                        │
│                                                                                                      │
│     // State Publishers                                                                              │
│     var currentConfig: AuthProviderConfig                                                            │
│     var configPublisher: AnyPublisher<AuthProviderConfig, Never>                                     │
│     var isSyncing: Bool                                                                              │
│     var isSyncingPublisher: AnyPublisher<Bool, Never>                                                │
│     var currentUserId: String  // DeviceIdentityService.shared.deviceId                              │
│                                                                                                      │
│     // UserDefaults Keys                                                                             │
│     enum Keys {                                                                                      │
│         static let hostingType = "auth_hosting_type"                                                 │
│         static let tenantId = "auth_managed_tenant_id"                                               │
│     }                                                                                                │
│ }                                                                                                    │
│                                                                                                      │
│ 7.2 Polling Configuration                                                                            │
│                                                                                                      │
│ // Polling interval: 5 seconds                                                                       │
│ pollingTimer = Timer.scheduledTimer(withTimeInterval: 5.0, repeats: true) { ... }                    │
│                                                                                                      │
│ // Polling stops when:                                                                               │
│ // - status.isInProgress == false (ready, failed, or suspended)                                      │
│                                                                                                      │
│ // Polling triggers:                                                                                 │
│ // - On createManagedInstance() if status is in progress                                             │
│ // - On app startup if saved instance is still provisioning                                          │
│                                                                                                      │
│ 7.3 Startup Sync                                                                                     │
│                                                                                                      │
│ // On AuthProviderService init:                                                                      │
│ if config.hostingType == .managed && config.managedInstance?.tenantId != nil {                       │
│     if config.isProvisioning {                                                                       │
│         // Still provisioning - start polling                                                        │
│         startPolling(tenantId: tenantId)                                                             │
│     } else {                                                                                         │
│         // Ready or failed - fetch latest status once                                                │
│         syncManagedInstanceOnStartup()                                                               │
│     }                                                                                                │
│ }                                                                                                    │
│                                                                                                      │
│ // If instance not found (404):                                                                      │
│ // - Clear local config                                                                              │
│ // - Call logout()                                                                                   │
│                                                                                                      │
│ ---                                                                                                  │
│ 8. Skills System                                                                                     │
│                                                                                                      │
│ 8.1 Skills Status                                                                                    │
│                                                                                                      │
│ {                                                                                                    │
│   "method": "skills.status",                                                                         │
│   "params": {                                                                                        │
│     "agentId": "optional-agent-id"                                                                   │
│   }                                                                                                  │
│ }                                                                                                    │
│                                                                                                      │
│ Response:                                                                                            │
│ {                                                                                                    │
│   "workspaceDir": "/path/to/workspace",                                                              │
│   "managedSkillsDir": "/path/to/skills",                                                             │
│   "skills": [                                                                                        │
│     {                                                                                                │
│       "name": "web-search",                                                                          │
│       "description": "Search the web",                                                               │
│       "source": "bundled",                                                                           │
│       "bundled": true,                                                                               │
│       "filePath": "/path/to/skill.md",                                                               │
│       "baseDir": "/path/to/dir",                                                                     │
│       "skillKey": "web-search",                                                                      │
│       "primaryEnv": "SERPER_API_KEY",                                                                │
│       "emoji": "🔍",                                                                                 │
│       "homepage": "https://serper.dev",                                                              │
│       "always": false,                                                                               │
│       "disabled": false,                                                                             │
│       "blockedByAllowlist": false,                                                                   │
│       "eligible": true,                                                                              │
│       "requirements": {                                                                              │
│         "bins": [],                                                                                  │
│         "anyBins": ["curl", "wget"],                                                                 │
│         "env": ["SERPER_API_KEY"],                                                                   │
│         "config": [],                                                                                │
│         "os": []                                                                                     │
│       },                                                                                             │
│       "missing": {                                                                                   │
│         "bins": [],                                                                                  │
│         "anyBins": [],                                                                               │
│         "env": ["SERPER_API_KEY"],                                                                   │
│         "config": [],                                                                                │
│         "os": []                                                                                     │
│       },                                                                                             │
│       "configChecks": [],                                                                            │
│       "install": [                                                                                   │
│         {                                                                                            │
│           "id": "homebrew",                                                                          │
│           "kind": "homebrew",                                                                        │
│           "label": "Install with Homebrew",                                                          │
│           "bins": ["curl"]                                                                           │
│         }                                                                                            │
│       ]                                                                                              │
│     }                                                                                                │
│   ]                                                                                                  │
│ }                                                                                                    │
│                                                                                                      │
│ 8.2 Skills Update                                                                                    │
│                                                                                                      │
│ {                                                                                                    │
│   "method": "skills.update",                                                                         │
│   "params": {                                                                                        │
│     "skillKey": "web-search",                                                                        │
│     "enabled": true,                                                                                 │
│     "apiKey": "optional-api-key",                                                                    │
│     "env": {                                                                                         │
│       "SERPER_API_KEY": "value"                                                                      │
│     }                                                                                                │
│   }                                                                                                  │
│ }                                                                                                    │
│                                                                                                      │
│ 8.3 Skills Install                                                                                   │
│                                                                                                      │
│ {                                                                                                    │
│   "method": "skills.install",                                                                        │
│   "params": {                                                                                        │
│     "name": "web-search",                                                                            │
│     "installId": "homebrew",                                                                         │
│     "timeoutMs": 60000                                                                               │
│   }                                                                                                  │
│ }                                                                                                    │
│                                                                                                      │
│ Response:                                                                                            │
│ {                                                                                                    │
│   "ok": true,                                                                                        │
│   "message": "Successfully installed"                                                                │
│ }                                                                                                    │
│                                                                                                      │
│ 8.4 Config Methods                                                                                   │
│                                                                                                      │
│ // Get config                                                                                        │
│ {                                                                                                    │
│   "method": "config.get",                                                                            │
│   "params": {}                                                                                       │
│ }                                                                                                    │
│                                                                                                      │
│ Response:                                                                                            │
│ {                                                                                                    │
│   "hash": "config-hash-string",                                                                      │
│   "config": { ... }                                                                                  │
│ }                                                                                                    │
│                                                                                                      │
│ // Patch config (for skill env/apiKey)                                                               │
│ {                                                                                                    │
│   "method": "config.patch",                                                                          │
│   "params": {                                                                                        │
│     "baseHash": "current-config-hash",                                                               │
│     "raw": "{\"skills\":{\"entries\":{\"web-search\":{\"env\":{\"SERPER_API_KEY\":\"value\"}}}}}"    │
│   }                                                                                                  │
│ }                                                                                                    │
│                                                                                                      │
│ ---                                                                                                  │
│ 9. Error Handling                                                                                    │
│                                                                                                      │
│ 9.1 Control Plane API Errors                                                                         │
│                                                                                                      │
│ enum ControlPlaneAPIError: LocalizedError, Equatable {                                               │
│     case invalidURL                                                                                  │
│     case invalidResponse                                                                             │
│     case notFound                      // 404 - Instance not found                                   │
│     case missingUserId                 // 400 - "missing_x_user_id"                                  │
│     case userNotFound                  // 404 - "user_not_found"                                     │
│     case subscriptionRequired          // 403 - "subscription_required"                              │
│     case forbidden                     // 403 - "forbidden"                                          │
│     case instanceNotFly                // 400 - "instance_not_fly"                                   │
│     case invalidAnthropicApiKey        // 400 - "invalid_anthropic_api_key"                          │
│     case invalidAnthropicSetupToken    // 400 - "invalid_anthropic_setup_token"                      │
│     case oauthStateExpired             // 400 - "oauth_state_not_found_or_expired"                   │
│     case oauthStateMismatch            // 400 - "oauth_state_tenant_mismatch"                        │
│     case serverError(statusCode: Int, message: String)                                               │
│     case unknown                                                                                     │
│ }                                                                                                    │
│                                                                                                      │
│ 9.2 Gateway Errors                                                                                   │
│                                                                                                      │
│ enum GatewayError: LocalizedError {                                                                  │
│     case notConnected                                                                                │
│     case encodingFailed                                                                              │
│     case requestFailed(String)                                                                       │
│     case notPaired(requestId: String?)  // NOT_PAIRED - needs Control UI approval                    │
│     case deviceIdentityRequired         // Gateway requires signed device auth                       │
│ }                                                                                                    │
│                                                                                                      │
│ 9.3 Connection Status                                                                                │
│                                                                                                      │
│ enum ConnectionStatus: Equatable {                                                                   │
│     case online           // Connected and authenticated                                             │
│     case offline          // Not connected                                                           │
│     case connecting       // Connection in progress                                                  │
│     case error(String)    // Connection error with message                                           │
│ }                                                                                                    │
│                                                                                                      │
│ ---                                                                                                  │
│ 10. Key Files Reference                                                                              │
│                                                                                                      │
│ Component: Settings UI                                                                               │
│ File Path: clawdbot/SettingsView.swift                                                               │
│ ────────────────────────────────────────                                                             │
│ Component: Auth Provider Service                                                                     │
│ File Path: clawdbot/Data/Services/AuthProviderService.swift                                          │
│ ────────────────────────────────────────                                                             │
│ Component: Control Plane Service                                                                     │
│ File Path: clawdbot/Data/Services/ControlPlaneService.swift                                          │
│ ────────────────────────────────────────                                                             │
│ Component: Gateway Service                                                                           │
│ File Path: clawdbot/Data/Services/GatewayService.swift                                               │
│ ────────────────────────────────────────                                                             │
│ Component: Auth Models                                                                               │
│ File Path: clawdbot/Domain/Models/AuthProvider.swift                                                 │
│ ────────────────────────────────────────                                                             │
│ Component: Device Identity                                                                           │
│ File Path: clawdbot/Data/Services/DeviceIdentityService.swift                                        │
│ ────────────────────────────────────────                                                             │
│ Component: OAuth WebView                                                                             │
│ File Path: clawdbot/Data/Services/ControlPlaneService.swift (OAuthWebViewController)                 │
│ ────────────────────────────────────────                                                             │
│ Component: Theme/Colors                                                                              │
│ File Path: clawdbot/Theme.swift                                                                      │
│                                                                                                      │
│ ---                                                                                                  │
│ Summary                                                                                              │
│                                                                                                      │
│ Managed Hosting Complete Flow                                                                        │
│                                                                                                      │
│ 1. User selects "Managed Hosting" in AuthProviderSheet                                               │
│ 2. AuthProviderService.createManagedInstance() called                                                │
│ 3. Uses DeviceIdentityService.deviceId as userId (same as RevenueCat)                                │
│ 4. Checks for existing tenants via GET /users/{userId}/tenants                                       │
│ 5. Creates new instance via POST /instances if none exist                                            │
│ 6. Starts polling every 5 seconds until status != isInProgress                                       │
│ 7. When ready: updates GatewayConfig with URL/token, reconnects gateway                              │
│ 8. User connects AI provider (OpenAI OAuth, OpenAI Key, or Anthropic Key)                            │
│ 9. Provider credentials stored server-side on the instance                                           │
│ 10. User can now chat via WebSocket gateway                                                          │
│                                                                                                      │
│ Self-Hosted Complete Flow                                                                            │
│                                                                                                      │
│ 1. User selects "Self-Hosted" in AuthProviderSheet                                                   │
│ 2. User enters gateway URL and auth token                                                            │
│ 3. AuthProviderService.setProvider() saves config                                                    │
│ 4. GatewayConfig updated with URL/token                                                              │
│ 5. GatewayService.reconnect() establishes WebSocket connection                                       │
│ 6. WebSocket handshake: connect.challenge -> connect request                                         │
│ 7. User can now chat via their own gateway  
