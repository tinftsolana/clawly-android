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

data class ApiKeyItem(
    val id: String,
    val name: String,
    val description: String,
    val value: String,
    val isSet: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiKeysScreen(
    onBackClick: () -> Unit
) {
    var showAddKeySheet by remember { mutableStateOf(false) }
    var keyToEdit by remember { mutableStateOf<ApiKeyItem?>(null) }

    // Mock API keys for demonstration
    val apiKeys = remember {
        mutableStateListOf(
            ApiKeyItem(
                id = "openai",
                name = "OpenAI API Key",
                description = "For ChatGPT and DALL-E",
                value = "",
                isSet = false
            ),
            ApiKeyItem(
                id = "anthropic",
                name = "Anthropic API Key",
                description = "For Claude models",
                value = "",
                isSet = false
            ),
            ApiKeyItem(
                id = "google",
                name = "Google AI API Key",
                description = "For Gemini models",
                value = "",
                isSet = false
            ),
            ApiKeyItem(
                id = "serpapi",
                name = "SerpAPI Key",
                description = "For web search functionality",
                value = "",
                isSet = false
            )
        )
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
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
                            text = "Secure Storage",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ClawlyColors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "API keys are encrypted and stored securely on your device. They are sent directly to the respective services when needed.",
                            fontSize = 12.sp,
                            color = ClawlyColors.secondaryText
                        )
                    }
                }
            }

            // API key cards
            items(apiKeys, key = { it.id }) { apiKey ->
                ApiKeyCard(
                    apiKey = apiKey,
                    onEdit = { keyToEdit = apiKey },
                    onDelete = {
                        val index = apiKeys.indexOfFirst { it.id == apiKey.id }
                        if (index != -1) {
                            apiKeys[index] = apiKeys[index].copy(value = "", isSet = false)
                        }
                    }
                )
            }
        }
    }

    // Edit key sheet
    keyToEdit?.let { key ->
        ApiKeyEditSheet(
            apiKey = key,
            onDismiss = { keyToEdit = null },
            onSave = { newValue ->
                val index = apiKeys.indexOfFirst { it.id == key.id }
                if (index != -1) {
                    apiKeys[index] = apiKeys[index].copy(
                        value = newValue,
                        isSet = newValue.isNotBlank()
                    )
                }
                keyToEdit = null
            }
        )
    }

    // Add key sheet
    if (showAddKeySheet) {
        AddApiKeySheet(
            onDismiss = { showAddKeySheet = false },
            onAdd = { name, value ->
                apiKeys.add(
                    ApiKeyItem(
                        id = name.lowercase().replace(" ", "_"),
                        name = name,
                        description = "Custom API key",
                        value = value,
                        isSet = value.isNotBlank()
                    )
                )
                showAddKeySheet = false
            }
        )
    }
}

@Composable
private fun ApiKeyCard(
    apiKey: ApiKeyItem,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ClawlyColors.surface)
            .border(
                width = 1.dp,
                color = if (apiKey.isSet) ClawlyColors.accentPrimary.copy(alpha = 0.3f)
                else ClawlyColors.surfaceBorder,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onEdit() }
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (apiKey.isSet) ClawlyColors.accentPrimary.copy(alpha = 0.15f)
                        else ClawlyColors.surfaceBorder.copy(alpha = 0.3f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = if (apiKey.isSet) ClawlyColors.accentPrimary else ClawlyColors.textMuted,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Info
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = apiKey.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = ClawlyColors.textPrimary
                    )

                    if (apiKey.isSet) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Configured",
                            tint = ClawlyColors.accentPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = apiKey.description,
                    fontSize = 13.sp,
                    color = ClawlyColors.secondaryText
                )

                if (apiKey.isSet) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022${apiKey.value.takeLast(4)}",
                        fontSize = 12.sp,
                        color = ClawlyColors.textMuted
                    )
                }
            }

            // Actions
            if (apiKey.isSet) {
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

            Icon(
                imageVector = Icons.Default.KeyboardArrowRight,
                contentDescription = null,
                tint = ClawlyColors.textMuted,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ApiKeyEditSheet(
    apiKey: ApiKeyItem,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var value by remember { mutableStateOf(apiKey.value) }
    var showValue by remember { mutableStateOf(false) }

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
                text = apiKey.name,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = ClawlyColors.textPrimary
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = apiKey.description,
                fontSize = 14.sp,
                color = ClawlyColors.secondaryText
            )

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text("API Key") },
                placeholder = { Text("sk-...") },
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
                onClick = { onSave(value) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ClawlyColors.accentPrimary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Save",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddApiKeySheet(
    onDismiss: () -> Unit,
    onAdd: (name: String, value: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }
    var showValue by remember { mutableStateOf(false) }

    val canAdd = name.isNotBlank() && value.isNotBlank()

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
                text = "Add Custom API Key",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = ClawlyColors.textPrimary
            )

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                placeholder = { Text("e.g. My Service") },
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

            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text("API Key") },
                placeholder = { Text("Enter API key...") },
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
                onClick = { onAdd(name, value) },
                enabled = canAdd,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ClawlyColors.accentPrimary,
                    disabledContainerColor = ClawlyColors.accentPrimary.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Add API Key",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
