package ai.clawly.app.presentation.apikeys

import ai.clawly.app.ui.theme.ClawlyColors
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiKeysScreen(
    onBackClick: () -> Unit,
    viewModel: ApiKeysViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showAddKeySheet by remember { mutableStateOf(false) }
    var keyToDelete by remember { mutableStateOf<ConfiguredApiKey?>(null) }

    LaunchedEffect(Unit) {
        viewModel.fetchConfig()
    }

    // Clear messages after showing
    LaunchedEffect(uiState.successMessage, uiState.error) {
        if (uiState.successMessage != null || uiState.error != null) {
            kotlinx.coroutines.delay(3000)
            viewModel.clearMessages()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "API Keys",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = ClawlyColors.accentPrimary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showAddKeySheet = true }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add API Key",
                            tint = ClawlyColors.accentPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ClawlyColors.background
                )
            )
        },
        containerColor = ClawlyColors.background
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = uiState.isLoading,
            onRefresh = { viewModel.fetchConfig() },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Info banner
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(ClawlyColors.accentPrimary.copy(alpha = 0.1f))
                            .padding(16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = ClawlyColors.accentPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Skill API Keys",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = ClawlyColors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Configure API keys for skills that require external services. Keys are stored securely on your gateway server.",
                                fontSize = 12.sp,
                                color = ClawlyColors.secondaryText
                            )
                        }
                    }
                }

                // Success message
                uiState.successMessage?.let { message ->
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(ClawlyColors.terminalGreen.copy(alpha = 0.15f))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = ClawlyColors.terminalGreen,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = message,
                                fontSize = 14.sp,
                                color = ClawlyColors.terminalGreen
                            )
                        }
                    }
                }

                // Error message
                uiState.error?.let { error ->
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(ClawlyColors.error.copy(alpha = 0.15f))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = ClawlyColors.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = error,
                                fontSize = 14.sp,
                                color = ClawlyColors.error,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { viewModel.fetchConfig() }) {
                                Text("Retry", color = ClawlyColors.accentPrimary)
                            }
                        }
                    }
                }

                // Empty state
                if (!uiState.isLoading && uiState.configuredKeys.isEmpty() && uiState.error == null) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = ClawlyColors.textMuted,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "No API Keys Configured",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = ClawlyColors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Add API keys for skills that need them",
                                fontSize = 14.sp,
                                color = ClawlyColors.secondaryText
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { showAddKeySheet = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ClawlyColors.accentPrimary
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Add API Key")
                            }
                        }
                    }
                }

                // Configured keys
                items(uiState.configuredKeys, key = { "${it.skillKey}-${it.envName}" }) { key ->
                    ConfiguredKeyCard(
                        apiKey = key,
                        onDelete = { keyToDelete = key }
                    )
                }
            }
        }
    }

    // Add key sheet
    if (showAddKeySheet) {
        AddApiKeySheet(
            isSaving = uiState.isSaving,
            onDismiss = { showAddKeySheet = false },
            onSave = { skillKey, envName, value ->
                viewModel.saveApiKey(skillKey, envName, value)
                showAddKeySheet = false
            }
        )
    }

    // Delete confirmation
    keyToDelete?.let { key ->
        AlertDialog(
            onDismissRequest = { keyToDelete = null },
            title = { Text("Delete API Key") },
            text = {
                Text("Are you sure you want to delete the API key for ${key.displayName}?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteApiKey(key.skillKey, key.envName)
                        keyToDelete = null
                    }
                ) {
                    Text("Delete", color = ClawlyColors.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { keyToDelete = null }) {
                    Text("Cancel")
                }
            },
            containerColor = ClawlyColors.surface
        )
    }
}

@Composable
private fun ConfiguredKeyCard(
    apiKey: ConfiguredApiKey,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ClawlyColors.surface)
            .border(
                width = 1.dp,
                color = ClawlyColors.accentPrimary.copy(alpha = 0.3f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(ClawlyColors.accentPrimary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = ClawlyColors.accentPrimary,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        // Info
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = apiKey.displayName,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = ClawlyColors.textPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Configured",
                    tint = ClawlyColors.terminalGreen,
                    modifier = Modifier.size(14.dp)
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${apiKey.skillKey} / ${apiKey.envName}",
                fontSize = 12.sp,
                color = ClawlyColors.textMuted
            )
        }

        // Delete button
        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete",
                tint = ClawlyColors.error,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddApiKeySheet(
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (skillKey: String, envName: String, value: String) -> Unit
) {
    var skillKey by remember { mutableStateOf("") }
    var envName by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }
    var showValue by remember { mutableStateOf(false) }

    val canSave = skillKey.isNotBlank() && envName.isNotBlank() && value.isNotBlank()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = ClawlyColors.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Text(
                text = "Add API Key",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = ClawlyColors.textPrimary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Configure an API key for a specific skill",
                fontSize = 14.sp,
                color = ClawlyColors.secondaryText
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Skill Key
            OutlinedTextField(
                value = skillKey,
                onValueChange = { skillKey = it },
                label = { Text("Skill Key") },
                placeholder = { Text("e.g. brave-search, weather") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ClawlyColors.accentPrimary,
                    unfocusedBorderColor = ClawlyColors.surfaceBorder,
                    focusedTextColor = ClawlyColors.textPrimary,
                    unfocusedTextColor = ClawlyColors.textPrimary,
                    focusedLabelColor = ClawlyColors.accentPrimary,
                    unfocusedLabelColor = ClawlyColors.secondaryText,
                    cursorColor = ClawlyColors.accentPrimary
                ),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Env Name
            OutlinedTextField(
                value = envName,
                onValueChange = { envName = it },
                label = { Text("Environment Variable") },
                placeholder = { Text("e.g. BRAVE_API_KEY") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ClawlyColors.accentPrimary,
                    unfocusedBorderColor = ClawlyColors.surfaceBorder,
                    focusedTextColor = ClawlyColors.textPrimary,
                    unfocusedTextColor = ClawlyColors.textPrimary,
                    focusedLabelColor = ClawlyColors.accentPrimary,
                    unfocusedLabelColor = ClawlyColors.secondaryText,
                    cursorColor = ClawlyColors.accentPrimary
                ),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            // API Key Value
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text("API Key") },
                placeholder = { Text("Enter your API key...") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (showValue) VisualTransformation.None
                else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showValue = !showValue }) {
                        Icon(
                            imageVector = if (showValue) Icons.Default.Close
                            else Icons.Default.Search,
                            contentDescription = if (showValue) "Hide" else "Show",
                            tint = ClawlyColors.secondaryText
                        )
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ClawlyColors.accentPrimary,
                    unfocusedBorderColor = ClawlyColors.surfaceBorder,
                    focusedTextColor = ClawlyColors.textPrimary,
                    unfocusedTextColor = ClawlyColors.textPrimary,
                    focusedLabelColor = ClawlyColors.accentPrimary,
                    unfocusedLabelColor = ClawlyColors.secondaryText,
                    cursorColor = ClawlyColors.accentPrimary
                ),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { onSave(skillKey.trim(), envName.trim(), value) },
                enabled = canSave && !isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ClawlyColors.accentPrimary,
                    disabledContainerColor = ClawlyColors.accentPrimary.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = if (isSaving) "Saving..." else "Save API Key",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
