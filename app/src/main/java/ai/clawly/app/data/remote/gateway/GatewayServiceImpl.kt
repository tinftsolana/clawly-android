package ai.clawly.app.data.remote.gateway

import ai.clawly.app.BuildConfig
import ai.clawly.app.data.preferences.GatewayPreferences
import ai.clawly.app.domain.model.*
import ai.clawly.app.domain.repository.AttachmentPayload
import ai.clawly.app.domain.repository.ChatHistoryMessage
import ai.clawly.app.domain.repository.SkillPayload
import android.util.Log
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val TAG = "GatewayService"

@Singleton
class GatewayServiceImpl @Inject constructor(
    private val preferences: GatewayPreferences,
    private val deviceIdentityManager: DeviceIdentityManager,
    private val controlPlaneService: ai.clawly.app.data.remote.ControlPlaneService
) : GatewayService {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    })

    private val sslContext = SSLContext.getInstance("TLS").apply {
        init(null, trustAllCerts, SecureRandom())
    }

    private val client = HttpClient(OkHttp) {
        install(WebSockets) {
            pingInterval = -1L  // Disabled - we send pings manually
            maxFrameSize = Long.MAX_VALUE
        }
        install(DefaultRequest) {
            header("X-Platform", if (BuildConfig.IS_WEB3) "web3" else "web2")
        }
        engine {
            config {
                sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
                hostnameVerifier { _, _ -> true }
                // WebSocket connections are long-lived; extend timeouts to prevent premature kills
                connectTimeout(15, TimeUnit.SECONDS)
                readTimeout(0, TimeUnit.SECONDS)    // No read timeout (WebSocket idles between messages)
                writeTimeout(60, TimeUnit.SECONDS)
            }
        }
    }

    private var webSocketSession: DefaultClientWebSocketSession? = null
    private var receiveJob: Job? = null
    private var pingJob: Job? = null
    private var connectJob: Job? = null
    private var connectionScope: CoroutineScope? = null

    // Separate scope for reconnect scheduling (survives connectionScope cancellation)
    private val reconnectScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Mutex to prevent concurrent connect() calls
    private val connectMutex = Mutex()

    // Guard to prevent double-scheduling reconnects (receive error + connection error race)
    private val reconnectScheduled = AtomicBoolean(false)
    // Suppress automatic reconnect while we intentionally disconnect/reconnect manually.
    private val suppressAutoReconnect = AtomicBoolean(false)

    // State
    private val _connectionStatus = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Offline)
    override val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _incomingMessages = MutableSharedFlow<GatewayMessage>(replay = 0)
    override val incomingMessages: Flow<GatewayMessage> = _incomingMessages.asSharedFlow()

    private val _streamingState = MutableStateFlow<StreamingState>(StreamingState.Idle)
    override val streamingState: StateFlow<StreamingState> = _streamingState.asStateFlow()

    private val _pairingRequired = MutableSharedFlow<String>(extraBufferCapacity = 1)
    override val pairingRequired: Flow<String> = _pairingRequired.asSharedFlow()

    private val _pairingRequested = MutableSharedFlow<String>(extraBufferCapacity = 1)
    override val pairingRequested: Flow<String> = _pairingRequested.asSharedFlow()

    private data class PendingRequest(
        val resolve: (JsonObject?) -> Unit,
        val reject: (Exception) -> Unit
    )

    private val pendingRequests = ConcurrentHashMap<String, PendingRequest>()

    // Connect handshake state
    private var connectNonce: String? = null
    private var connectSent = false
    private var reconnectAttempts = 0
    private val reconnectDelayMs = 5000L  // Fixed 5s delay (matches iOS spec)
    private val connectChallengeWaitMs = 750L

    private enum class ConnectAuthMode { TokenOnly, SignedDevice }
    private enum class TokenSource { GatewayToken, DeviceToken, None }

    private var connectAuthMode = ConnectAuthMode.TokenOnly
    private var lastTokenSource = TokenSource.None
    private var didRetryAfterTokenMismatch = false

    override suspend fun connect() {
        // Mutex prevents concurrent connect() calls (init + onStart race, watchdog overlap, etc.)
        connectMutex.withLock {
            connectInternal()
        }
    }

    private suspend fun connectInternal() {
        val current = _connectionStatus.value
        Log.d(TAG, "connect() called, current status: $current")
        suppressAutoReconnect.set(false)

        // Check if managed instance is paused/suspended
        val hostingType = preferences.getHostingTypeSync()
        if (hostingType == "managed") {
            val managedStatus = preferences.getManagedStatusSync()
            if (managedStatus == "suspended") {
                Log.d(TAG, "Managed instance is paused/suspended, setting Paused status")
                _connectionStatus.value = ConnectionStatus.Paused
                return
            }
        }

        val gatewayUrl = preferences.getEffectiveGatewayUrl()
        val gatewayToken = preferences.getEffectiveGatewayToken()
        Log.d(TAG, "Effective gateway URL: '$gatewayUrl'")
        Log.d(TAG, "Effective gateway token: '${if (gatewayToken.isNotEmpty()) gatewayToken.take(8) + "..." else "empty"}'")

        if (gatewayUrl.isEmpty()) {
            Log.d(TAG, "No gateway URL configured, staying offline")
            _connectionStatus.value = ConnectionStatus.Offline
            return
        }

        if (current == ConnectionStatus.Online) {
            Log.d(TAG, "Already connected, skipping")
            return
        }

        if (current == ConnectionStatus.Connecting) {
            Log.d(TAG, "Already connecting, skipping")
            return
        }

        reconnectAttempts = 0
        reconnectScheduled.set(false)
        _connectionStatus.value = ConnectionStatus.Connecting

        doConnect(gatewayUrl)
    }

    /**
     * Core connection logic. Called by connectInternal() and by reconnect retries.
     */
    private suspend fun doConnect(gatewayUrl: String) {
        // Reset handshake state
        connectJob?.cancel()
        connectJob = null
        connectNonce = null
        connectSent = false
        // Auth mode depends on hosting type:
        // - Managed: start with SignedDevice (requires device field with signature)
        // - Self-hosted: start with TokenOnly (no device field)
        val hostingType = preferences.getHostingTypeSync()
        connectAuthMode = if (hostingType == "managed") ConnectAuthMode.SignedDevice else ConnectAuthMode.TokenOnly
        lastTokenSource = TokenSource.None
        didRetryAfterTokenMismatch = false
        pendingRequests.clear()

        val wsUrl = buildGatewayUrl(gatewayUrl) ?: run {
            _connectionStatus.value = ConnectionStatus.Error("Invalid gateway URL")
            return
        }

        connectionScope?.cancel()
        connectionScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        connectionScope?.launch {
            try {
                Log.d(TAG, "Connecting to $wsUrl...")
                val origin = buildOriginHeader(wsUrl)
                Log.d(TAG, "Using Origin: $origin")

                client.webSocket(wsUrl, request = {
                    header("Origin", origin)
                }) {
                    webSocketSession = this
                    Log.d(TAG, "WebSocket connected")

                    scheduleConnectIfNeeded()

                    receiveJob = launch {
                        try {
                            for (frame in incoming) {
                                when (frame) {
                                    is Frame.Text -> handleIncomingFrame(frame.readText())
                                    is Frame.Close -> {
                                        Log.d(TAG, "Received close frame")
                                        scheduleReconnect()
                                    }
                                    is Frame.Ping -> send(Frame.Pong(frame.data))
                                    is Frame.Pong -> {}
                                    else -> {}
                                }
                            }
                        } catch (e: Exception) {
                            val message = e.message ?: ""
                            if (message.contains("UnsupportedFrameType") ||
                                message.contains("PING", ignoreCase = true)) {
                                return@launch
                            }
                            Log.e(TAG, "Receive error: ${e.javaClass.simpleName}: ${e.message}")
                            scheduleReconnect()
                        }
                    }

                    receiveJob?.join()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Connection error: ${e.javaClass.simpleName}: ${e.message}")
                scheduleReconnect()
            }
        }
    }

    override suspend fun disconnect() {
        Log.d(TAG, "Disconnecting...")
        suppressAutoReconnect.set(true)
        // Cancel all pending reconnect jobs
        reconnectScope.coroutineContext[Job]?.children?.forEach { it.cancel() }
        cancelPendingConnect()
        pingJob?.cancel()
        receiveJob?.cancel()
        pendingRequests.clear()

        try {
            webSocketSession?.close(CloseReason(CloseReason.Codes.NORMAL, "Client disconnect"))
        } catch (e: Exception) {
            Log.w(TAG, "Error closing WebSocket", e)
        }

        webSocketSession = null
        connectionScope?.cancel()
        connectionScope = null

        _connectionStatus.value = ConnectionStatus.Offline
        reconnectAttempts = 0
        reconnectScheduled.set(false)
    }

    override suspend fun reconnect() {
        Log.d(TAG, "Manual reconnect requested")

        disconnect()
        delay(500)
        connect()
    }

    /**
     * Schedules a reconnect attempt via reconnectScope (survives connectionScope cancellation).
     * Uses fixed 5s delay with infinite retries (matches iOS spec).
     *
     * @param preserveErrorStatus if true and current status is Error, keep it visible in UI
     *        instead of switching to Connecting immediately.
     */
    private fun scheduleReconnect(preserveErrorStatus: Boolean = false) {
        if (suppressAutoReconnect.get()) {
            Log.d(TAG, "Auto-reconnect suppressed during manual disconnect/reconnect")
            return
        }
        if (_connectionStatus.value is ConnectionStatus.Paused) {
            Log.d(TAG, "Instance is paused, skipping reconnect")
            return
        }

        // Prevent double-scheduling (receive error + connection error can fire in quick succession)
        if (!reconnectScheduled.compareAndSet(false, true)) {
            Log.d(TAG, "Reconnect already scheduled, skipping")
            return
        }

        // Clean up current connection
        cancelPendingConnect()
        pingJob?.cancel()
        receiveJob?.cancel()
        pendingRequests.clear()
        webSocketSession = null
        connectionScope?.cancel()
        connectionScope = null

        reconnectAttempts++
        Log.d(TAG, "Scheduling reconnect in ${reconnectDelayMs}ms (attempt $reconnectAttempts)")

        if (!preserveErrorStatus || _connectionStatus.value !is ConnectionStatus.Error) {
            _connectionStatus.value = ConnectionStatus.Connecting
        }

        reconnectScope.launch {
            delay(reconnectDelayMs)
            reconnectScheduled.set(false)
            val gatewayUrl = preferences.getEffectiveGatewayUrl()
            if (gatewayUrl.isNotEmpty()) {
                doConnect(gatewayUrl)
            } else {
                _connectionStatus.value = ConnectionStatus.Offline
            }
        }
    }

    override suspend fun sendMessage(
        message: String,
        thinkingLevel: ThinkingLevel,
        skills: List<SkillPayload>,
        attachments: List<AttachmentPayload>
    ): Result<String> {
        Log.d(TAG, "sendMessage called: message='$message', attachments=${attachments.size}, connectionStatus=${_connectionStatus.value}")
        attachments.forEach { att ->
            Log.d(TAG, "  attachment: type=${att.type}, mime=${att.mimeType}, file=${att.fileName}, contentLen=${att.content.length}")
        }
        if (_connectionStatus.value != ConnectionStatus.Online) {
            Log.e(TAG, "sendMessage failed: not connected")
            return Result.failure(GatewayError.NotConnected)
        }

        _streamingState.value = StreamingState.Idle

        val sessionKey = preferences.getSessionKeySync()
        val idempotencyKey = UUID.randomUUID().toString()
        val params = buildJsonObject {
            put("sessionKey", sessionKey)
            put("message", message)
            put("deliver", false)
            put("idempotencyKey", idempotencyKey)

            if (attachments.isNotEmpty()) {
                putJsonArray("attachments") {
                    attachments.forEach { attachment ->
                        addJsonObject {
                            put("type", attachment.type)
                            put("mimeType", attachment.mimeType)
                            put("fileName", attachment.fileName)
                            put("content", attachment.content)
                        }
                    }
                }
            }
        }

        return try {
            val response = request("chat.send", params, timeoutMs = 60_000)
            // Server may return a different runId than the idempotencyKey we sent
            val serverRunId = response?.get("runId")?.jsonPrimitive?.contentOrNull
            val actualRunId = serverRunId ?: idempotencyKey
            Log.d(TAG, "chat.send resolved: idempotencyKey=$idempotencyKey, serverRunId=$serverRunId, using=$actualRunId")
            Result.success(actualRunId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun fetchHistory(sessionKey: String?): Result<List<ChatHistoryMessage>> {
        if (_connectionStatus.value != ConnectionStatus.Online) {
            return Result.failure(GatewayError.NotConnected)
        }

        val key = sessionKey ?: preferences.getSessionKeySync()
        val params = buildJsonObject {
            put("sessionKey", key)
        }

        return try {
            val response = request("chat.history", params)
            Log.d(TAG, "chat.history response: $response")
            val messagesArray = response?.get("messages")?.jsonArray
            Log.d(TAG, "chat.history messages array size: ${messagesArray?.size ?: 0}")

            val messages = messagesArray?.mapIndexedNotNull { index, msg ->
                val obj = msg.jsonObject
                val timestamp = obj["timestamp"]?.jsonPrimitive?.longOrNull
                    ?: obj["createdAt"]?.jsonPrimitive?.longOrNull
                val id = obj["id"]?.jsonPrimitive?.contentOrNull
                    ?: obj["_id"]?.jsonPrimitive?.contentOrNull
                    ?: "history-${timestamp ?: System.currentTimeMillis()}-$index"
                val role = obj["role"]?.jsonPrimitive?.contentOrNull ?: return@mapIndexedNotNull null
                val content = extractMessageText(obj) ?: ""

                Log.d(TAG, "Parsed history message: id=$id, role=$role, content=${content.take(50)}...")
                ChatHistoryMessage(id, role, content, timestamp)
            } ?: emptyList()

            Log.d(TAG, "chat.history parsed ${messages.size} messages")
            Result.success(messages)
        } catch (e: Exception) {
            Log.e(TAG, "chat.history error", e)
            Result.failure(e)
        }
    }

    override suspend fun abortChat(sessionKey: String?): Result<Unit> {
        if (_connectionStatus.value != ConnectionStatus.Online) {
            return Result.failure(GatewayError.NotConnected)
        }

        val key = sessionKey ?: preferences.getSessionKeySync()
        val params = buildJsonObject {
            put("sessionKey", key)
        }

        return try {
            request("chat.abort", params)
            _streamingState.value = StreamingState.Idle
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun fetchSkillsStatus(): Result<SkillsStatusResponse> {
        if (_connectionStatus.value != ConnectionStatus.Online) {
            return Result.failure(GatewayError.NotConnected)
        }

        return try {
            val response = request("skills.status", buildJsonObject {})
            val skillsArray = response?.get("skills")?.jsonArray ?: JsonArray(emptyList())

            val skills = skillsArray.mapNotNull { skillElement ->
                try {
                    val obj = skillElement.jsonObject
                    ServerSkill(
                        name = obj["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null,
                        description = obj["description"]?.jsonPrimitive?.contentOrNull,
                        skillKey = obj["skillKey"]?.jsonPrimitive?.contentOrNull
                            ?: obj["key"]?.jsonPrimitive?.contentOrNull
                            ?: return@mapNotNull null,
                        primaryEnv = obj["primaryEnv"]?.jsonPrimitive?.contentOrNull,
                        emoji = obj["emoji"]?.jsonPrimitive?.contentOrNull,
                        disabled = obj["disabled"]?.jsonPrimitive?.booleanOrNull ?: false,
                        eligible = obj["eligible"]?.jsonPrimitive?.booleanOrNull ?: true,
                        missing = obj["missing"]?.jsonObject?.let { missingObj ->
                            SkillRequirements(
                                bins = missingObj["bins"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList(),
                                anyBins = missingObj["anyBins"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList(),
                                env = missingObj["env"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList(),
                                config = missingObj["config"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList(),
                                os = missingObj["os"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
                            )
                        }
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse skill", e)
                    null
                }
            }

            Log.d(TAG, "Fetched ${skills.size} skills from gateway")
            Result.success(SkillsStatusResponse(skills))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch skills status", e)
            Result.failure(e)
        }
    }

    override suspend fun updateSkill(skillKey: String, enabled: Boolean): Result<Unit> {
        if (_connectionStatus.value != ConnectionStatus.Online) {
            return Result.failure(GatewayError.NotConnected)
        }

        return try {
            val params = buildJsonObject {
                put("skillKey", skillKey)
                put("enabled", enabled)
            }
            request("skills.update", params)
            Log.d(TAG, "Updated skill $skillKey: enabled=$enabled")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update skill $skillKey", e)
            Result.failure(e)
        }
    }

    override suspend fun getConfig(): Result<ConfigResponse> {
        if (_connectionStatus.value != ConnectionStatus.Online) {
            return Result.failure(GatewayError.NotConnected)
        }

        return try {
            val response = request("config.get", buildJsonObject {})
            val hash = response?.get("hash")?.jsonPrimitive?.contentOrNull ?: ""
            val config = response?.get("config")?.jsonObject

            Result.success(ConfigResponse(hash = hash, config = config))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get config", e)
            Result.failure(e)
        }
    }

    override suspend fun configureSkillEnv(skillKey: String, envName: String, value: String): Result<Unit> {
        if (_connectionStatus.value != ConnectionStatus.Online) {
            return Result.failure(GatewayError.NotConnected)
        }

        return try {
            val configResult = getConfig()
            val baseHash = configResult.getOrNull()?.hash
                ?: return Result.failure(GatewayError.RequestFailed("Failed to get config hash"))

            val patchConfig = buildJsonObject {
                putJsonObject("skills") {
                    putJsonObject("entries") {
                        putJsonObject(skillKey) {
                            putJsonObject("env") {
                                put(envName, value)
                            }
                        }
                    }
                }
            }

            // raw must be a JSON string, not an object
            val rawString = Json.encodeToString(JsonObject.serializer(), patchConfig)

            val params = buildJsonObject {
                put("baseHash", baseHash)
                put("raw", rawString)
            }

            request("config.patch", params)
            Log.d(TAG, "Configured $envName for skill $skillKey")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to configure skill env $skillKey.$envName", e)
            Result.failure(e)
        }
    }

    // Private methods

    private fun buildGatewayUrl(raw: String): String? {
        var s = raw.trim()
        if (s.isEmpty()) return null

        s = s.replace("http://", "ws://").replace("https://", "wss://")
        if (!s.startsWith("ws://") && !s.startsWith("wss://")) {
            s = "wss://$s"
        }

        if (s.contains("/socket.io/")) {
            val uri = java.net.URI(s)
            s = "${uri.scheme}://${uri.host}${if (uri.port > 0) ":${uri.port}" else ""}"
        }

        return s
    }

    private fun buildOriginHeader(wsUrl: String): String {
        val uri = java.net.URI(wsUrl)
        val scheme = if (uri.scheme == "ws") "http" else "https"
        val port = uri.port
        val defaultPort = if (scheme == "http") 80 else 443

        return if (port > 0 && port != defaultPort) {
            "$scheme://${uri.host}:$port"
        } else {
            "$scheme://${uri.host}"
        }
    }

    private suspend fun handleIncomingFrame(text: String) {
        Log.d(TAG, "Received: $text")

        try {
            val obj = json.parseToJsonElement(text).jsonObject
            val typeRaw = obj["type"]?.jsonPrimitive?.contentOrNull ?: return
            val type = GatewayWireType.fromString(typeRaw) ?: return

            when (type) {
                GatewayWireType.Event -> handleEvent(obj)
                GatewayWireType.Res -> handleResponse(obj)
                GatewayWireType.Req -> {}
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing frame", e)
        }
    }

    private suspend fun handleEvent(obj: JsonObject) {
        val event = obj["event"]?.jsonPrimitive?.contentOrNull ?: return
        val payload = obj["payload"]

        when (event) {
            "connect.challenge" -> {
                val dict = payload?.jsonObject
                val nonce = dict?.get("nonce")?.jsonPrimitive?.contentOrNull
                if (nonce != null) {
                    connectNonce = nonce
                    Log.d(TAG, "Received connect.challenge nonce: $nonce")
                    cancelPendingConnect()
                    connectionScope?.launch {
                        sendConnect()
                    }
                }
            }
            "chat" -> {
                val dict = payload?.jsonObject
                if (dict != null) {
                    handleChatEvent(dict)
                }
            }
            "tick" -> {
                // Server sends tick events to keep connection alive; acknowledge by staying connected
                Log.d(TAG, "Received tick")
            }
            "health" -> {
                Log.d(TAG, "Received health update")
            }
            "presence" -> {
                Log.d(TAG, "Received presence update")
            }
            "shutdown" -> {
                Log.w(TAG, "Received shutdown event, reconnecting immediately")
                // Use reconnectScope - NOT connectionScope (which gets cancelled during reconnect)
                reconnectScope.launch {
                    delay(500)  // Brief delay to let server finish shutdown
                    scheduleReconnect()
                }
            }
            "device.pair.requested", "node.pair.requested" -> {
                val requestId = payload
                    ?.jsonObject
                    ?.get("requestId")
                    ?.jsonPrimitive
                    ?.contentOrNull
                if (!requestId.isNullOrEmpty()) {
                    Log.d(TAG, "Pairing event requested, requestId=$requestId, event=$event")
                    _pairingRequested.tryEmit(requestId)
                }
            }
            else -> {
                try {
                    val content = when (payload) {
                        is JsonObject -> {
                            payload["text"]?.jsonPrimitive?.contentOrNull
                                ?: payload["message"]?.jsonPrimitive?.contentOrNull
                        }
                        is JsonPrimitive -> payload.contentOrNull
                        else -> null
                    }

                    if (!content.isNullOrEmpty()) {
                        _incomingMessages.emit(
                            GatewayMessage(event, GatewayPayload(content = content))
                        )
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Ignoring event '$event' with complex payload")
                }
            }
        }
    }

    private suspend fun handleChatEvent(payload: JsonObject) {
        // SessionKey filtering: ignore events for other sessions
        val eventSessionKey = payload["sessionKey"]?.jsonPrimitive?.contentOrNull?.trim()
        if (!eventSessionKey.isNullOrEmpty()) {
            val currentSessionKey = preferences.getSessionKeySync()
            if (eventSessionKey != currentSessionKey) {
                Log.d(TAG, "Ignoring chat event for session '$eventSessionKey' (current: '$currentSessionKey')")
                return
            }
        }

        val state = payload["state"]?.jsonPrimitive?.contentOrNull ?: return
        val runId = payload["runId"]?.jsonPrimitive?.contentOrNull
        val message = payload["message"]?.jsonObject ?: return
        val role = message["role"]?.jsonPrimitive?.contentOrNull?.lowercase() ?: return

        if (role != "assistant") return

        val stopReason = message["stopReason"]?.jsonPrimitive?.contentOrNull
        val errorMessage = message["errorMessage"]?.jsonPrimitive?.contentOrNull

        if (stopReason == "error" && errorMessage != null) {
            Log.e(TAG, "Chat error from server: $errorMessage (runId=$runId)")
            _streamingState.value = StreamingState.Complete
            _incomingMessages.emit(
                GatewayMessage("chat.error", GatewayPayload(content = "Error: $errorMessage"), runId = runId)
            )
            delay(100)
            _streamingState.value = StreamingState.Idle
            return
        }

        val content = extractMessageText(message) ?: ""

        when (state) {
            "streaming", "partial", "delta" -> {
                if (content.isNotEmpty()) {
                    _streamingState.value = StreamingState.Streaming(content, runId = runId)
                }
            }
            "final" -> {
                _streamingState.value = StreamingState.Complete
                if (content.isNotEmpty()) {
                    _incomingMessages.emit(
                        GatewayMessage("chat.final", GatewayPayload(content = content), runId = runId)
                    )
                }
                delay(100)
                _streamingState.value = StreamingState.Idle
            }
            "aborted" -> {
                Log.d(TAG, "Chat aborted (runId=$runId)")
                _streamingState.value = StreamingState.Complete
                _incomingMessages.emit(
                    GatewayMessage("chat.aborted", runId = runId)
                )
                delay(100)
                _streamingState.value = StreamingState.Idle
            }
        }
    }

    private fun extractMessageText(message: JsonObject): String? {
        val content = message["content"]

        return when (content) {
            is JsonPrimitive -> content.contentOrNull
            is JsonArray -> {
                val parts = content.mapNotNull { part ->
                    try {
                        val obj = part.jsonObject
                        if (obj["type"]?.jsonPrimitive?.contentOrNull == "text") {
                            obj["text"]?.jsonPrimitive?.contentOrNull
                        } else null
                    } catch (e: Exception) {
                        (part as? JsonPrimitive)?.contentOrNull
                    }
                }
                if (parts.isNotEmpty()) parts.joinToString("\n") else null
            }
            else -> {
                message["text"]?.jsonPrimitive?.contentOrNull
            }
        }
    }

    private fun handleResponse(obj: JsonObject) {
        val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: return
        val ok = obj["ok"]?.jsonPrimitive?.booleanOrNull ?: false
        val payload = obj["payload"]?.jsonObject

        val pending = pendingRequests.remove(id) ?: return

        if (ok) {
            pending.resolve(payload)
        } else {
            val error = obj["error"]?.jsonObject
            val code = error?.get("code")?.jsonPrimitive?.contentOrNull
            val message = error?.get("message")?.jsonPrimitive?.contentOrNull ?: "Request failed"

            when {
                code == "NOT_PAIRED" -> {
                    // Try both locations: error.requestId (per guide) and error.details.requestId (legacy)
                    val requestId = error?.get("requestId")?.jsonPrimitive?.contentOrNull
                        ?: error?.get("details")?.jsonObject?.get("requestId")?.jsonPrimitive?.contentOrNull
                    pending.reject(GatewayError.NotPaired(requestId))
                }
                message.lowercase().contains("device identity required") -> {
                    pending.reject(GatewayError.DeviceIdentityRequired)
                }
                else -> {
                    pending.reject(GatewayError.RequestFailed(message))
                }
            }
        }
    }

    private suspend fun sendConnect() {
        if (connectSent) return
        connectSent = true

        val scopes = listOf("operator.admin", "operator.approvals", "operator.pairing", "operator.write")
        val role = "operator"
        val clientId = "openclaw-android"  // Must match server's allowed client IDs
        val clientMode = "webchat"
        val signedAtMs = System.currentTimeMillis()

        val deviceToken = preferences.getDeviceTokenSync()
        val gatewayToken = preferences.getEffectiveGatewayToken().trim()
        val hostingType = preferences.getHostingTypeSync()
        val isManaged = hostingType == "managed"

        // Token priority differs by hosting type:
        // - Managed: deviceToken → gatewayToken (device token has priority)
        // - Self-hosted: gatewayToken → deviceToken (gateway token has priority)
        val token = if (isManaged) {
            when {
                deviceToken != null -> {
                    lastTokenSource = TokenSource.DeviceToken
                    deviceToken
                }
                gatewayToken.isNotEmpty() -> {
                    lastTokenSource = TokenSource.GatewayToken
                    gatewayToken
                }
                else -> {
                    lastTokenSource = TokenSource.None
                    ""
                }
            }
        } else {
            // Self-hosted: gateway token first
            when {
                gatewayToken.isNotEmpty() -> {
                    lastTokenSource = TokenSource.GatewayToken
                    gatewayToken
                }
                deviceToken != null -> {
                    lastTokenSource = TokenSource.DeviceToken
                    deviceToken
                }
                else -> {
                    lastTokenSource = TokenSource.None
                    ""
                }
            }
        }

        val nonce = connectNonce
        val includeDevice = connectAuthMode == ConnectAuthMode.SignedDevice

        // instanceId: for managed hosting use stable UUID (stored), for self-hosted use fixed string
        val instanceId = if (isManaged) {
            preferences.getOrCreateInstanceId()
        } else {
            "clawly"
        }

        val device = if (includeDevice) {
            deviceIdentityManager.createSignedDevice(
                clientId, clientMode, role, scopes, signedAtMs,
                token.ifEmpty { null }, nonce
            )
        } else null

        val params = buildJsonObject {
            put("minProtocol", 3)
            put("maxProtocol", 3)
            putJsonObject("client") {
                put("id", clientId)
                put("version", BuildConfig.VERSION_NAME)
                put("platform", "android")
                put("mode", clientMode)
                put("instanceId", instanceId)
            }
            put("role", role)
            putJsonArray("scopes") { scopes.forEach { add(it) } }
            putJsonArray("caps") {}
            put("userAgent", "clawly-android")
            put("locale", java.util.Locale.getDefault().toLanguageTag())

            if (device != null) {
                putJsonObject("device") {
                    device.forEach { (key, value) ->
                        when (value) {
                            is String -> put(key, value)
                            is Long -> put(key, value)
                            is Number -> put(key, value.toLong())
                        }
                    }
                }
            }

            if (token.isNotEmpty()) {
                putJsonObject("auth") {
                    put("token", token)
                }
            }
        }

        Log.d(TAG, "Sending connect request (authMode: ${connectAuthMode}, nonce: ${nonce ?: "<none>"}, hasToken: ${token.isNotEmpty()}, tokenPrefix: ${if (token.length > 8) token.take(8) + "..." else "empty"})")

        try {
            val response = request("connect", params, timeoutMs = 30_000)

            // deviceToken is directly in payload (per guide), fallback to auth.deviceToken for compatibility
            val newDeviceToken = response?.get("deviceToken")?.jsonPrimitive?.contentOrNull
                ?: response?.get("auth")?.jsonObject?.get("deviceToken")?.jsonPrimitive?.contentOrNull
            if (!newDeviceToken.isNullOrEmpty()) {
                preferences.setDeviceToken(newDeviceToken)
                Log.d(TAG, "Saved device token (prefix: ${newDeviceToken.take(8)}...)")
            }

            Log.d(TAG, "Connected!")
            _connectionStatus.value = ConnectionStatus.Online
            startPingTimer()
            reconnectAttempts = 0

        } catch (e: Exception) {
            when {
                e is GatewayError.RequestFailed &&
                        e.message.lowercase().contains("gateway token mismatch") &&
                        lastTokenSource == TokenSource.DeviceToken &&
                        !didRetryAfterTokenMismatch -> {
                    didRetryAfterTokenMismatch = true
                    preferences.setDeviceToken(null)
                    connectAuthMode = ConnectAuthMode.TokenOnly
                    connectSent = false
                    Log.d(TAG, "Device token mismatch; cleared, retrying with gateway token...")
                    delay(200)
                    sendConnect()
                }
                e is GatewayError.DeviceIdentityRequired &&
                        connectAuthMode == ConnectAuthMode.TokenOnly -> {
                    connectAuthMode = ConnectAuthMode.SignedDevice
                    connectSent = false
                    Log.d(TAG, "Device identity required; retrying with signed device...")
                    delay(200)
                    sendConnect()
                }
                e is GatewayError.NotPaired -> {
                    if (connectAuthMode == ConnectAuthMode.TokenOnly) {
                        Log.e(TAG, "Gateway rejected token (pairing required)")
                        _connectionStatus.value = ConnectionStatus.Error("Gateway rejected token (pairing required)")
                        return
                    }

                    val rid = e.requestId ?: "<unknown>"
                    Log.e(TAG, "Pairing required: requestId=$rid")
                    _connectionStatus.value = ConnectionStatus.Error(
                        "Pairing required. Approve in Control UI (Devices -> Pairing). requestId=$rid"
                    )
                    runCatching {
                        webSocketSession?.close(
                            CloseReason(CloseReason.Codes.NORMAL, "Awaiting pairing approval")
                        )
                    }
                    webSocketSession = null
                    _pairingRequired.tryEmit(e.requestId ?: "")
                    // Keep reconnecting every 5s until pairing is approved (matches iOS spec)
                    scheduleReconnect(preserveErrorStatus = true)
                }
                else -> {
                    Log.e(TAG, "Connect handshake failed: ${e.message}")
                    // Schedule reconnect (same as socket errors)
                    scheduleReconnect()
                }
            }
        }
    }

    private suspend fun request(method: String, params: JsonObject, timeoutMs: Long = 30000): JsonObject? {
        val session = webSocketSession ?: throw GatewayError.NotConnected

        val id = UUID.randomUUID().toString()
        val request = buildJsonObject {
            put("type", "req")
            put("id", id)
            put("method", method)
            put("params", params)
        }

        Log.d(TAG, "Sending request: $method (id: ${id.take(8)}...)")

        return withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { continuation ->
                pendingRequests[id] = PendingRequest(
                    resolve = { payload ->
                        Log.d(TAG, "Request $method resolved")
                        continuation.resume(payload)
                    },
                    reject = { error ->
                        Log.d(TAG, "Request $method rejected: ${error.message}")
                        continuation.resumeWithException(error)
                    }
                )

                connectionScope?.launch {
                    try {
                        val json = request.toString()
                        Log.d(TAG, "Sending $method frame: ${json.length} chars")
                        session.send(Frame.Text(json))
                        Log.d(TAG, "Frame sent successfully for $method")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to send frame for $method: ${e.message}")
                        pendingRequests.remove(id)
                        continuation.resumeWithException(e)
                    }
                }

                continuation.invokeOnCancellation {
                    pendingRequests.remove(id)
                }
            }
        } ?: run {
            pendingRequests.remove(id)
            Log.e(TAG, "Request $method timed out after ${timeoutMs}ms")
            throw GatewayError.RequestFailed("Request timed out")
        }
    }

    private fun startPingTimer() {
        pingJob?.cancel()
        pingJob = connectionScope?.launch {
            Log.d(TAG, "Starting ping timer (every 30s)")
            while (isActive) {
                delay(30_000L)
                try {
                    val session = webSocketSession ?: break
                    session.send(Frame.Ping(byteArrayOf()))
                    Log.d(TAG, "Ping sent")
                } catch (e: Exception) {
                    Log.e(TAG, "Ping failed: ${e.message}")
                    scheduleReconnect()
                    break
                }
            }
        }
    }

    private fun scheduleConnectIfNeeded() {
        if (connectSent) return
        connectJob?.cancel()
        connectJob = connectionScope?.launch {
            delay(connectChallengeWaitMs)
            if (!connectSent) {
                sendConnect()
            }
        }
        connectJob?.invokeOnCompletion { connectJob = null }
    }

    private fun cancelPendingConnect() {
        connectJob?.cancel()
        connectJob = null
    }

    override suspend fun approvePairingAndReconnect(requestId: String): Result<Unit> {
        val tenantId = preferences.getTenantIdSync()
            ?: return Result.failure(Exception("No tenant ID"))

        return controlPlaneService.approvePairing(tenantId, requestId)
            .onSuccess {
                Log.d(TAG, "Pairing approved, reconnecting...")
        
                reconnect()
            }
    }
}
