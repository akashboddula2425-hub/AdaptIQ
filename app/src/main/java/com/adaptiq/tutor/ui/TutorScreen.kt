package com.adaptiq.tutor.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adaptiq.tutor.viewmodel.TutorUiState
import com.adaptiq.tutor.viewmodel.TutorViewModel
import com.adaptiq.tutor.viewmodel.ChatMessage
import com.adaptiq.tutor.viewmodel.MessageRole
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TutorScreen(
    viewModel: TutorViewModel,
    modifier: Modifier = Modifier,
    isEmbedded: Boolean = false
) {
    val uiState by viewModel.uiState.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val isSpeaking by viewModel.isSpeaking.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Box(modifier = modifier.fillMaxSize().padding(horizontal = 8.dp)) {
        if (!isEmbedded) {
            CosmicBackground(modifier = Modifier.fillMaxSize())
        }

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                if (!isEmbedded) {
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    text = "AdaptIQ",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp
                                )
                                AnimatedVisibility(visible = uiState != TutorUiState.Tutoring) {
                                    Text(
                                        text = uiState.displayName,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                        ),
                        actions = {
                            IconButton(onClick = { viewModel.stopAudio() }) {
                                Icon(
                                    imageVector = androidx.compose.material.icons.Icons.Default.Stop,
                                    contentDescription = "Stop Audio",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    )
                }
            },
            bottomBar = {
                val isInteractive = uiState is TutorUiState.Tutoring ||
                    uiState is TutorUiState.DiagnosticOnboarding ||
                    uiState is TutorUiState.GeneratingResponse ||
                    uiState is TutorUiState.Listening ||
                    uiState is TutorUiState.Idle
                if (isInteractive) {
                    ChatInputBar(
                        inputText = inputText,
                        onInputChange = { inputText = it },
                        uiState = uiState,
                        isSpeaking = isSpeaking,
                        onSend = {
                            if (inputText.isNotBlank()) {
                                viewModel.sendMessage(inputText.trim())
                                inputText = ""
                                coroutineScope.launch {
                                    if (messages.isNotEmpty()) {
                                        listState.animateScrollToItem(messages.size - 1)
                                    }
                                }
                            }
                        },
                        onStop = { 
                            if (uiState == TutorUiState.GeneratingResponse) viewModel.cancelGeneration()
                            else viewModel.stopAudio()
                        },
                        onMicToggle = { viewModel.toggleVoiceInput() }
                    )
                }
            }
        ) { innerPadding ->
            when (uiState) {
                is TutorUiState.ModelNotFound -> {
                    val availableModels by viewModel.availableModels.collectAsState()
                    ModelSetupScreen(
                        availableModels = availableModels,
                        onLoadModel = { path -> viewModel.loadModel(path) },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
                is TutorUiState.Error -> {
                    ErrorScreen(
                        message = (uiState as TutorUiState.Error).message,
                        onRetry = { viewModel.loadModel("") },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
                else -> {
                    // Messages list
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(vertical = 16.dp)
                    ) {
                        // Header Module (Matches "Solar Geometry" design)
                        item {
                            ModuleHeaderCard()
                        }
                        
                        // Loading indicator
                        if (uiState == TutorUiState.InitializingModel) {
                            item { ModelLoadingCard() }
                        }

                        items(messages, key = { it.id }) { message ->
                            ChatBubble(message = message)
                        }

                        // Typing indicator when generating
                        if (uiState == TutorUiState.GeneratingResponse) {
                            item { TypingIndicator() }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ModuleHeaderCard() {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = CircleShape,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.padding(6.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("ACTIVE MODULE", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Text("Solar Geometry & Orbit", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Surface(
                    color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.FlashOn, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("+10 XP", fontSize = 12.sp, color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Mastery Meter", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("76% Complete", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { 0.76f },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = MaterialTheme.colorScheme.secondary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}

// ─── Model Setup Screen ──────────────────────────────────────────────
@Composable
private fun ModelSetupScreen(
    availableModels: List<com.adaptiq.tutor.viewmodel.AvailableModel>,
    onLoadModel: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var manualPath by remember { mutableStateOf("") }
    var isManualEntry by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.FolderOpen,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Select AI Model",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Choose a model to load into the AI Engine",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Model List
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(availableModels) { model ->
                Card(
                    onClick = { onLoadModel(model.configPath) },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = model.name,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = model.configPath,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = model.size,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }
            
            item {
                TextButton(
                    onClick = { isManualEntry = !isManualEntry },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Text("Enter custom model path instead...")
                }
            }
            
            if (isManualEntry) {
                item {
                    OutlinedTextField(
                        value = manualPath,
                        onValueChange = { manualPath = it },
                        label = { Text("Custom config.json path") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { onLoadModel(manualPath.trim()) },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        enabled = manualPath.isNotBlank()
                    ) {
                        Text("Load Custom Model")
                    }
                }
            }
        }

        // Setup instructions
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "📋 How to add models",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Transfer MNN models (converted via llmexport) to:\n" +
                           "/sdcard/Android/data/com.adaptiq.tutor/files/models/\n" +
                           "or use adb to push to /data/local/tmp/adaptiq/models/",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

// ─── Error Screen ────────────────────────────────────────────────────
@Composable
private fun ErrorScreen(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "⚠️",
            fontSize = 48.sp
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Something Went Wrong",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        OutlinedButton(
            onClick = onRetry,
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Try Again")
        }
    }
}

// ─── Chat Input Bar ──────────────────────────────────────────────────
@Composable
private fun ChatInputBar(
    inputText: String,
    onInputChange: (String) -> Unit,
    uiState: TutorUiState,
    isSpeaking: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onMicToggle: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        tonalElevation = 3.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Mic toggle button
            IconButton(
                onClick = onMicToggle,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
            ) {
                val isListening = uiState == TutorUiState.Listening
                Icon(
                    imageVector = if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                    contentDescription = if (isListening) "Stop Listening" else "Start Listening",
                    tint = if (isListening) MaterialTheme.colorScheme.error
                           else MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Text input
            OutlinedTextField(
                value = inputText,
                onValueChange = onInputChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Ask me anything...") },
                shape = RoundedCornerShape(24.dp),
                maxLines = 4,
                enabled = uiState != TutorUiState.InitializingModel &&
                          uiState != TutorUiState.GeneratingResponse
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Send / Stop button
            val showStop = uiState == TutorUiState.GeneratingResponse || isSpeaking
            FilledIconButton(
                onClick = {
                    if (showStop) onStop()
                    else onSend()
                },
                modifier = Modifier.size(44.dp),
                enabled = uiState != TutorUiState.InitializingModel
            ) {
                Icon(
                    imageVector = if (showStop) Icons.Default.Stop else Icons.AutoMirrored.Filled.Send,
                    contentDescription = if (showStop) "Stop" else "Send"
                )
            }
        }
    }
}

// ─── Chat Bubble ─────────────────────────────────────────────────────
@Composable
private fun ChatBubble(message: ChatMessage) {
    val isUser = message.role == MessageRole.USER
    val alignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            // Optional AI Avatar could go here
        }
        Surface(
            color = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 0.dp,
                bottomEnd = if (isUser) 0.dp else 16.dp
            ),
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Text(
                text = message.content,
                modifier = Modifier.padding(16.dp),
                color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                fontSize = 16.sp
            )
        }
    }
}

// ─── Typing Indicator ────────────────────────────────────────────────
@Composable
private fun TypingIndicator() {
    Row(
        modifier = Modifier.padding(start = 8.dp, top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        repeat(3) { index ->
            val infiniteTransition = rememberInfiniteTransition(label = "dot_$index")
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = 900
                        0.3f at 0
                        1f at 300
                        0.3f at 600
                    },
                    initialStartOffset = StartOffset(index * 150)
                ),
                label = "alpha_$index"
            )
            Surface(
                modifier = Modifier.size(8.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = alpha)
            ) {}
        }
    }
}

// ─── Model Loading Card ──────────────────────────────────────────────
@Composable
private fun ModelLoadingCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                strokeWidth = 4.dp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Initializing AI Engine...",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Loading model into memory. This may take a moment.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
