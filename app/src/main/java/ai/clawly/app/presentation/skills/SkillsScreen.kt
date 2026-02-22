package ai.clawly.app.presentation.skills

import ai.clawly.app.domain.model.Skill
import ai.clawly.app.domain.model.SkillFilter
import ai.clawly.app.ui.theme.ClawlyColors
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillsScreen(
    onBackClick: () -> Unit,
    onNavigateToChat: (String) -> Unit = {},
    viewModel: SkillsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Compute available skills (reactive to state changes)
    val availableSkills = remember(uiState.skills) {
        uiState.skills.filter { skill ->
            skill.missingRequirements?.hasMissingBins != true
        }
    }

    // Compute filtered skills
    val filteredSkills = remember(availableSkills, uiState.selectedFilter, uiState.searchText) {
        var result = availableSkills
        result = when (uiState.selectedFilter) {
            SkillFilter.All -> result
            SkillFilter.Active -> result.filter { it.isEnabled }
            SkillFilter.Inactive -> result.filter { !it.isEnabled }
        }
        if (uiState.searchText.isNotEmpty()) {
            result = result.filter { skill ->
                skill.name.contains(uiState.searchText, ignoreCase = true) ||
                        skill.description.contains(uiState.searchText, ignoreCase = true)
            }
        }
        result
    }

    // Compute counts for filter chips
    val countForFilter: (SkillFilter) -> Int = remember(availableSkills) {
        { filter ->
            when (filter) {
                SkillFilter.All -> availableSkills.size
                SkillFilter.Active -> availableSkills.count { it.isEnabled }
                SkillFilter.Inactive -> availableSkills.count { !it.isEnabled }
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.fetchSkills()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Skills",
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
                    IconButton(onClick = { viewModel.showCreateSkillSheet() }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Skill",
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Search bar
            SkillsSearchBar(
                searchText = uiState.searchText,
                onSearchTextChange = { viewModel.setSearchText(it) }
            )

            // Filter chips
            SkillsFilterChips(
                selectedFilter = uiState.selectedFilter,
                onFilterSelected = { viewModel.setFilter(it) },
                countForFilter = countForFilter
            )

            // Skills list
            @OptIn(ExperimentalMaterial3Api::class)
            PullToRefreshBox(
                isRefreshing = uiState.isLoading,
                onRefresh = { viewModel.fetchSkills() },
                modifier = Modifier.fillMaxSize()
            ) {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Loading state
                    if (uiState.isLoading) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = ClawlyColors.accentPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Loading server skills...",
                                    fontSize = 13.sp,
                                    color = ClawlyColors.secondaryText
                                )
                            }
                        }
                    }

                    // Error state
                    uiState.error?.let { error ->
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(ClawlyColors.surface)
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = ClawlyColors.warning,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = error,
                                    fontSize = 13.sp,
                                    color = ClawlyColors.secondaryText,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 2
                                )
                                TextButton(onClick = { viewModel.fetchSkills() }) {
                                    Text(
                                        text = "Retry",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = ClawlyColors.accentPrimary
                                    )
                                }
                            }
                        }
                    }

                    // Skills
                    items(filteredSkills, key = { it.id }) { skill ->
                        SkillCard(
                            skill = skill,
                            onToggle = { viewModel.toggleSkill(skill.id) },
                            onConfigure = if (
                                skill.missingRequirements?.hasConfigurableEnv == true &&
                                skill.missingRequirements?.hasMissingBins != true
                            ) {
                                { viewModel.showSkillConfig(skill) }
                            } else null
                        )
                    }
                }
            }
        }
    }

    // Create Skill Sheet
    if (uiState.showCreateSkillSheet) {
        CreateSkillSheet(
            onDismiss = { viewModel.hideCreateSkillSheet() },
            onSendMessage = { message ->
                viewModel.hideCreateSkillSheet()
                onNavigateToChat(message)
            }
        )
    }

    // Skill Config Sheet
    uiState.skillToConfig?.let { skill ->
        SkillConfigSheet(
            skill = skill,
            isConfiguring = uiState.isConfiguringSkill,
            configurationError = uiState.configurationError,
            onDismiss = { viewModel.hideSkillConfig() },
            onSave = { envName, value ->
                viewModel.configureSkillEnv(skill, envName, value) { success ->
                    if (success) {
                        viewModel.hideSkillConfig()
                    }
                }
            }
        )
    }
}

@Composable
private fun SkillsSearchBar(
    searchText: String,
    onSearchTextChange: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(ClawlyColors.surface)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = ClawlyColors.secondaryText,
            modifier = Modifier.size(16.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        BasicTextField(
            value = searchText,
            onValueChange = onSearchTextChange,
            modifier = Modifier.weight(1f),
            textStyle = TextStyle(
                fontSize = 15.sp,
                color = ClawlyColors.textPrimary
            ),
            cursorBrush = SolidColor(ClawlyColors.accentPrimary),
            decorationBox = { innerTextField ->
                if (searchText.isEmpty()) {
                    Text(
                        text = "Search skills...",
                        fontSize = 15.sp,
                        color = ClawlyColors.secondaryText
                    )
                }
                innerTextField()
            }
        )

        if (searchText.isNotEmpty()) {
            IconButton(
                onClick = { onSearchTextChange("") },
                modifier = Modifier.size(20.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Clear",
                    tint = ClawlyColors.secondaryText,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun SkillsFilterChips(
    selectedFilter: SkillFilter,
    onFilterSelected: (SkillFilter) -> Unit,
    countForFilter: (SkillFilter) -> Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SkillFilter.entries.forEach { filter ->
            val isSelected = selectedFilter == filter
            val backgroundColor by animateColorAsState(
                targetValue = if (isSelected) ClawlyColors.accentPrimary else ClawlyColors.surface,
                label = "filterBg"
            )
            val textColor by animateColorAsState(
                targetValue = if (isSelected) Color.White else ClawlyColors.textPrimary,
                label = "filterText"
            )

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(backgroundColor)
                    .clickable { onFilterSelected(filter) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = filter.displayName,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = textColor
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${countForFilter(filter)}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isSelected) Color.White.copy(alpha = 0.8f) else ClawlyColors.textMuted
                )
            }
        }
    }
}

@Composable
private fun SkillCard(
    skill: Skill,
    onToggle: () -> Unit,
    onConfigure: (() -> Unit)?
) {
    val hasMissingRequirements = skill.missingRequirements?.hasMissing == true
    val iconColor = Color(0xFF4CAF50) // Green for server skills

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ClawlyColors.surface)
            .border(
                width = 1.dp,
                color = when {
                    hasMissingRequirements -> ClawlyColors.warning.copy(alpha = 0.3f)
                    skill.isEnabled -> ClawlyColors.accentPrimary.copy(alpha = 0.3f)
                    else -> ClawlyColors.surfaceBorder
                },
                shape = RoundedCornerShape(12.dp)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(iconColor.copy(alpha = if (hasMissingRequirements) 0.08f else 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = getIconForSkill(skill.icon),
                    contentDescription = null,
                    tint = if (hasMissingRequirements) iconColor.copy(alpha = 0.5f) else iconColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Info
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = skill.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (hasMissingRequirements)
                            ClawlyColors.textPrimary.copy(alpha = 0.6f)
                        else ClawlyColors.textPrimary
                    )

                    if (hasMissingRequirements) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Missing requirements",
                            tint = ClawlyColors.warning,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = skill.description,
                    fontSize = 13.sp,
                    color = ClawlyColors.secondaryText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Toggle
            Switch(
                checked = skill.isEnabled,
                onCheckedChange = { onToggle() },
                enabled = !hasMissingRequirements,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = ClawlyColors.accentPrimary,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = ClawlyColors.surfaceBorder,
                    disabledCheckedThumbColor = Color.White.copy(alpha = 0.5f),
                    disabledCheckedTrackColor = ClawlyColors.accentPrimary.copy(alpha = 0.5f),
                    disabledUncheckedThumbColor = Color.White.copy(alpha = 0.5f),
                    disabledUncheckedTrackColor = ClawlyColors.surfaceBorder.copy(alpha = 0.5f)
                ),
                modifier = Modifier.scale(0.9f)
            )
        }

        // Missing requirements banner
        skill.missingRequirements?.let { missing ->
            if (missing.hasMissing) {
                val needsServerInstall = missing.hasMissingBins

                HorizontalDivider(color = ClawlyColors.surfaceBorder)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (needsServerInstall) Icons.Default.Warning else Icons.Default.Build,
                        contentDescription = null,
                        tint = if (needsServerInstall) ClawlyColors.error else ClawlyColors.warning,
                        modifier = Modifier.size(11.dp)
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    Text(
                        text = if (needsServerInstall) "Requires server setup" else missing.summary,
                        fontSize = 11.sp,
                        color = if (needsServerInstall)
                            ClawlyColors.error.copy(alpha = 0.8f)
                        else ClawlyColors.warning,
                        maxLines = 2,
                        modifier = Modifier.weight(1f)
                    )

                    // Configure button
                    if (missing.hasConfigurableEnv && !needsServerInstall && onConfigure != null) {
                        TextButton(
                            onClick = onConfigure,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text(
                                text = "Configure",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = ClawlyColors.accentPrimary
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateSkillSheet(
    onDismiss: () -> Unit,
    onSendMessage: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    val canCreate = name.isNotBlank() && description.isNotBlank()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = ClawlyColors.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 8.dp)
                .navigationBarsPadding()
                .imePadding()
        ) {
            Text(
                text = "Create Skill",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = ClawlyColors.textPrimary
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Info banner
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(ClawlyColors.warning.copy(alpha = 0.1f))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = ClawlyColors.warning,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Describe what your skill should do and the agent will create it for you.",
                    fontSize = 13.sp,
                    color = ClawlyColors.secondaryText
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Name input
            Text(
                text = "Skill Name",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = ClawlyColors.secondaryText
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text("e.g. Weather Lookup") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ClawlyColors.accentPrimary,
                    unfocusedBorderColor = ClawlyColors.surfaceBorder,
                    focusedTextColor = ClawlyColors.textPrimary,
                    unfocusedTextColor = ClawlyColors.textPrimary,
                    cursorColor = ClawlyColors.accentPrimary
                ),
                shape = RoundedCornerShape(8.dp),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Description input
            Text(
                text = "What can this skill do?",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = ClawlyColors.secondaryText
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                placeholder = { Text("Explain what the skill should do...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ClawlyColors.accentPrimary,
                    unfocusedBorderColor = ClawlyColors.surfaceBorder,
                    focusedTextColor = ClawlyColors.textPrimary,
                    unfocusedTextColor = ClawlyColors.textPrimary,
                    cursorColor = ClawlyColors.accentPrimary
                ),
                shape = RoundedCornerShape(8.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Create button
            Button(
                onClick = {
                    val message = "I want to create a new skill ${name.trim()}. ${description.trim()}"
                    onSendMessage(message)
                },
                enabled = canCreate,
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
                    text = "Create Skill",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SkillConfigSheet(
    skill: Skill,
    isConfiguring: Boolean,
    configurationError: String?,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    val missingEnvVars = skill.missingRequirements?.env ?: emptyList()
    val envValues = remember { mutableStateMapOf<String, String>() }
    var showSuccess by remember { mutableStateOf(false) }

    val canSave = envValues.values.any { it.isNotBlank() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = ClawlyColors.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            // Skill header
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF4CAF50).copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = getIconForSkill(skill.icon),
                        contentDescription = null,
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = skill.name,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ClawlyColors.textPrimary
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = skill.description,
                    fontSize = 14.sp,
                    color = ClawlyColors.secondaryText,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Env var inputs
            if (missingEnvVars.isNotEmpty()) {
                Text(
                    text = "API Keys & Environment Variables",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = ClawlyColors.secondaryText
                )

                Spacer(modifier = Modifier.height(16.dp))

                missingEnvVars.forEach { envName ->
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = envName,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = ClawlyColors.textPrimary
                            )

                            if (skill.primaryEnv == envName) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFF4CAF50).copy(alpha = 0.15f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "Primary",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFF4CAF50)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        OutlinedTextField(
                            value = envValues[envName] ?: "",
                            onValueChange = { envValues[envName] = it },
                            placeholder = { Text("Enter value...") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ClawlyColors.accentPrimary,
                                unfocusedBorderColor = ClawlyColors.surfaceBorder,
                                focusedTextColor = ClawlyColors.textPrimary,
                                unfocusedTextColor = ClawlyColors.textPrimary,
                                cursorColor = ClawlyColors.accentPrimary
                            ),
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true
                            // Note: For production, use visualTransformation for password masking
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }

            // Error message
            configurationError?.let { error ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(ClawlyColors.error.copy(alpha = 0.1f))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = ClawlyColors.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = error,
                        fontSize = 13.sp,
                        color = ClawlyColors.error
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Success message
            if (showSuccess) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF4CAF50).copy(alpha = 0.1f))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Configuration saved! Skill should now be available.",
                        fontSize = 13.sp,
                        color = Color(0xFF4CAF50)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Save button
            Button(
                onClick = {
                    val firstValue = envValues.entries.firstOrNull { it.value.isNotBlank() }
                    firstValue?.let { onSave(it.key, it.value) }
                },
                enabled = canSave && !isConfiguring,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ClawlyColors.accentPrimary,
                    disabledContainerColor = ClawlyColors.accentPrimary.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isConfiguring) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = if (isConfiguring) "Saving..." else "Save Configuration",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

private fun getIconForSkill(iconName: String): ImageVector = when (iconName) {
    "magnifyingglass" -> Icons.Default.Search
    "folder" -> Icons.Default.Favorite
    "globe" -> Icons.Default.Place
    "bubble.left" -> Icons.Default.Email
    "envelope" -> Icons.Default.Email
    "calendar" -> Icons.Default.DateRange
    "clock" -> Icons.Default.DateRange
    "wrench" -> Icons.Default.Build
    "paintpalette" -> Icons.Default.Create
    "chart.bar" -> Icons.Default.Star
    "lock" -> Icons.Default.Lock
    "photo" -> Icons.Default.Star
    "music.note" -> Icons.Default.Star
    "iphone" -> Icons.Default.Phone
    "laptopcomputer" -> Icons.Default.Home
    "cpu" -> Icons.Default.Settings
    "terminal" -> Icons.Default.Create
    "pencil" -> Icons.Default.Edit
    else -> Icons.Default.Star
}

private fun Modifier.scale(scale: Float) = this.graphicsLayer(
    scaleX = scale,
    scaleY = scale
)
