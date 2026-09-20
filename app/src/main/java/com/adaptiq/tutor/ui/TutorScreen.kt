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
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
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
                            if (uiState != TutorUiState.ModelNotFound) {
                                IconButton(onClick = { viewModel.resetModelSelection() }) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = "Change AI Model",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            IconButton(onClick = { viewModel.stopAudio() }) {
                                Icon(
                                    imageVector = Icons.Default.Stop,
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
                    ModelSetupScreen(
                        viewModel = viewModel,
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
                        // Loading indicator
                        if (uiState == TutorUiState.InitializingModel) {
                            item { ModelLoadingCard() }
                        }

                        items(messages, key = { it.id }) { message ->
                            ChatBubble(
                                message = message,
                                onReadAloud = { viewModel.speakMessage(message.content) }
                            )
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

// ─── Model Setup Screen ──────────────────────────────────────────────
@Composable
private fun ModelSetupScreen(
    viewModel: TutorViewModel,
    modifier: Modifier = Modifier
) {
    val downloadState by viewModel.modelDownloader.downloadState.collectAsState()
    val availableModels by viewModel.availableModels.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Dark theme for model selection
    val darkBackground = Color(0xFF121212)
    val cardBackground = Color(0xFF1E1E1E)
    val blueButton = Color(0xFF4A90E2)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(darkBackground)
            .padding(24.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Model",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Runs entirely on this device. Download once; it works offline after that.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray
        )
        
        Spacer(modifier = Modifier.height(32.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(viewModel.downloadableModels) { model ->
                val progress = downloadState[model.id]
                val isDownloaded = viewModel.modelDownloader.isModelDownloaded(model.id)
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                        Text(
                            text = model.name,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = model.description,
                            fontSize = 12.sp,
                            color = Color.Gray,
                            lineHeight = 16.sp
                        )
                        
                        if (progress?.isDownloading == true) {
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = progress.progress,
                                modifier = Modifier.fillMaxWidth().height(4.dp),
                                color = blueButton,
                                trackColor = Color.DarkGray
                            )
                        } else if (progress?.error != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Error: ${progress.error}", color = Color.Red, fontSize = 12.sp)
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isDownloaded && progress?.isDownloading != true) {
                            IconButton(
                                onClick = { viewModel.modelDownloader.deleteModel(model.id) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = Color.Gray,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = {
                                if (isDownloaded) {
                                    val configPath = java.io.File(context.getExternalFilesDir("models"), "${model.id}/config.json").absolutePath
                                    viewModel.loadModel(configPath)
                                } else if (progress?.isDownloading != true) {
                                    coroutineScope.launch {
                                        viewModel.modelDownloader.downloadModel(model.id, model.repoId)
                                    }
                                }
                            },
                            enabled = progress?.isDownloading != true,
                            colors = ButtonDefaults.buttonColors(containerColor = blueButton),
                            shape = CircleShape,
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = if (isDownloaded) "Use" else if (progress?.isDownloading == true) "${(progress.progress * 100).toInt()}%" else "Use",
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
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
private fun ChatBubble(
    message: ChatMessage,
    onReadAloud: () -> Unit = {}
) {
    val isUser = message.role == MessageRole.USER
    val alignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            // Optional AI Avatar could go here
        }
        Column(horizontalAlignment = if (isUser) Alignment.End else Alignment.Start) {
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
            if (!isUser) {
                Row(
                    modifier = Modifier.padding(top = 4.dp, start = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onReadAloud,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Default.VolumeUp,
                            contentDescription = "Read Aloud",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Read it out",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
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
