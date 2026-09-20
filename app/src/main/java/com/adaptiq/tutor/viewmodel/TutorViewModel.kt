package com.adaptiq.tutor.viewmodel

import android.app.Application
import android.speech.tts.TextToSpeech
import java.util.Locale
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.adaptiq.tutor.agent.AdaptiveDiagnosticManager
import com.adaptiq.tutor.agent.KnowledgeGapTracker
import com.adaptiq.tutor.agent.PersonaEngine
import com.adaptiq.tutor.data.AdaptIQDatabase
import com.adaptiq.tutor.data.LearnerProfile
import com.adaptiq.tutor.data.UserPreferences
import com.adaptiq.tutor.engine.MnnBridge
import com.adaptiq.tutor.engine.ModelLoadException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * TutorViewModel orchestrates the interaction between the UI, inference engine,
 * persona system, and knowledge tracking.
 *
 * Implements MVI-style unidirectional data flow using [StateFlow].
 */
class TutorViewModel(private val application: Application) : AndroidViewModel(application) {

    // ─── Dependencies ────────────────────────────────────────────
    private val mnnBridge = MnnBridge()
    private val userPreferences = UserPreferences(application)
    private val database = AdaptIQDatabase.getInstance(application)
    private val diagnosticManager = AdaptiveDiagnosticManager()
    private val knowledgeGapTracker = KnowledgeGapTracker(database.knowledgeGapDao())
    private var tts: TextToSpeech? = null

    // ─── State ───────────────────────────────────────────────────
    private val _uiState = MutableStateFlow<TutorUiState>(TutorUiState.InitializingModel)
    val uiState: StateFlow<TutorUiState> = _uiState.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private var currentProfile: LearnerProfile? = null
    private var generationJob: Job? = null

    private val _availableModels = MutableStateFlow<List<AvailableModel>>(emptyList())
    val availableModels: StateFlow<List<AvailableModel>> = _availableModels.asStateFlow()

    init {
        tts = TextToSpeech(application) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) { _isSpeaking.value = true }
                    override fun onDone(utteranceId: String?) { _isSpeaking.value = false }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) { _isSpeaking.value = false }
                })
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            scanForModels()
            try {
                // Load saved profile if available
                userPreferences.learnerProfile.first()?.let { profile ->
                    currentProfile = profile
                }

                // Load model from saved path
                userPreferences.modelPath.first()?.let { path ->
                    loadModel(path)
                } ?: run {
                    _uiState.value = TutorUiState.ModelNotFound
                }
            } catch (e: Exception) {
                _uiState.value = TutorUiState.ModelNotFound
            }
        }
    }

    // ─── Public Actions ──────────────────────────────────────────

    /**
     * Loads the MNN model from the specified config path.
     */
    fun loadModel(configPath: String) {
        if (configPath.isBlank()) {
            _uiState.value = TutorUiState.ModelNotFound
            return
        }

        viewModelScope.launch {
            _uiState.value = TutorUiState.InitializingModel
            try {
                mnnBridge.loadModel(configPath)
                // Configure the LLM to prevent infinite loops and limit length
                mnnBridge.setConfig("{\"max_new_tokens\": 150, \"temperature\": 0.7, \"repetition_penalty\": 1.1}")
                userPreferences.setModelPath(configPath)

                // Check if onboarding is needed
                val isOnboarded = userPreferences.isOnboardingComplete.first()
                _uiState.value = if (isOnboarded && currentProfile != null) {
                    addSystemMessage("Welcome back! What would you like to learn today?")
                    TutorUiState.Tutoring
                } else {
                    diagnosticManager.reset()
                    addSystemMessage("Hi there! 👋 I'm AdaptIQ, your personal AI tutor. Let's get to know each other! What's your name?")
                    TutorUiState.DiagnosticOnboarding
                }
            } catch (e: ModelLoadException) {
                _uiState.value = TutorUiState.Error(
                    "Failed to load model from:\n$configPath\n\n" +
                    "Make sure the model files exist at this path on your device."
                )
            } catch (e: Exception) {
                _uiState.value = TutorUiState.Error(
                    e.message ?: "Unknown error loading model"
                )
            }
        }
    }

    /**
     * Sends a user message and triggers AI response generation.
     */
    fun sendMessage(text: String) {
        val userMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            role = MessageRole.USER,
            content = text
        )
        _messages.update { it + userMessage }

        when (_uiState.value) {
            is TutorUiState.DiagnosticOnboarding -> handleDiagnosticResponse(text)
            is TutorUiState.Tutoring, is TutorUiState.Idle -> generateResponse(text)
            else -> { /* Ignore messages in non-interactive states */ }
        }
    }

    /**
     * Toggles voice input mode.
     */
    fun toggleVoiceInput() {
        if (_uiState.value == TutorUiState.Listening) {
            _uiState.value = TutorUiState.Tutoring
        } else {
            _uiState.value = TutorUiState.Listening
        }
    }

    /**
     * Cancels the current generation and stops any speaking audio.
     */
    fun cancelGeneration() {
        generationJob?.cancel()
        mnnBridge.cancelGeneration()
        tts?.stop()
        _uiState.value = TutorUiState.Tutoring
    }

    /**
     * Stops the text-to-speech audio immediately.
     */
    fun stopAudio() {
        tts?.stop()
        _isSpeaking.value = false
    }

    // ─── Private Logic ───────────────────────────────────────────
    
    private fun scanForModels() {
        val models = mutableListOf<AvailableModel>()
        
        // Scan standard paths
        val pathsToScan = listOf(
            java.io.File("/data/local/tmp/adaptiq/models"),
            application.getExternalFilesDir("models")
        )
        
        pathsToScan.forEach { baseDir ->
            if (baseDir != null && baseDir.exists() && baseDir.isDirectory) {
                // If the baseDir contains config.json, it's a model
                val config = java.io.File(baseDir, "config.json")
                if (config.exists()) {
                    models.add(AvailableModel(
                        name = baseDir.name.replace("_", " ").replace("-", " ").capitalize(),
                        configPath = config.absolutePath,
                        size = formatSize(getFolderSize(baseDir))
                    ))
                }
                // Also check subdirectories (one level deep)
                baseDir.listFiles()?.forEach { subDir ->
                    if (subDir.isDirectory) {
                        val subConfig = java.io.File(subDir, "config.json")
                        if (subConfig.exists()) {
                            models.add(AvailableModel(
                                name = subDir.name.replace("_", " ").replace("-", " ").capitalize(),
                                configPath = subConfig.absolutePath,
                                size = formatSize(getFolderSize(subDir))
                            ))
                        }
                    }
                }
            }
        }
        
        // Add a default entry for manual path entry if list is empty
        if (models.isEmpty()) {
            models.add(AvailableModel("Qwen 2.5 (Manual Path)", "/data/local/tmp/adaptiq/models/config.json", "Unknown"))
        }
        
        _availableModels.value = models.distinctBy { it.configPath }
    }
    
    private fun getFolderSize(folder: java.io.File): Long {
        var length: Long = 0
        folder.listFiles()?.forEach { file ->
            if (file.isFile) length += file.length()
        }
        return length
    }
    
    private fun formatSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
        return java.text.DecimalFormat("#,##0.#").format(size / Math.pow(1024.0, digitGroups.toDouble())) + " " + units[digitGroups]
    }

    private fun handleDiagnosticResponse(text: String) {
        diagnosticManager.analyzeResponse(text)

        val diagnosticState = diagnosticManager.state.value
        if (diagnosticState.isComplete) {
            // Build profile from diagnostics
            val profile = diagnosticManager.buildProfile(
                name = _messages.value
                    .firstOrNull { it.role == MessageRole.USER }
                    ?.content
                    ?.split(" ")?.firstOrNull()
                    ?: "Student"
            )
            currentProfile = profile

            viewModelScope.launch {
                userPreferences.saveLearnerProfile(profile)
                userPreferences.setOnboardingComplete(true)
            }

            val persona = PersonaEngine.getPersona(profile.gradeLevel)
            addSystemMessage(
                "Great! I've set up your learning profile. " +
                "I'll be your ${persona.displayName}. " +
                "What subject would you like to explore? 🚀"
            )
            _uiState.value = TutorUiState.Tutoring
        } else {
            // Continue diagnostic conversation
            generateResponse(text, isDiagnostic = true)
        }
    }

    private fun generateResponse(userText: String, isDiagnostic: Boolean = false) {
        generationJob?.cancel()
        generationJob = viewModelScope.launch {
            _uiState.value = TutorUiState.GeneratingResponse

            // Build the system prompt based on current mode
            val systemPrompt = if (isDiagnostic) {
                diagnosticManager.getNextDiagnosticPrompt() ?: ""
            } else {
                currentProfile?.let { PersonaEngine.buildSystemPrompt(it) } ?: ""
            }

            // Build ChatML format manually to bypass MNN's broken templating
            val rawPrompt = buildRawPrompt(systemPrompt)

            // Create a placeholder message for streaming
            val responseId = UUID.randomUUID().toString()
            val responseMessage = ChatMessage(
                id = responseId,
                role = MessageRole.ASSISTANT,
                content = ""
            )
            _messages.update { it + responseMessage }

            // Stream tokens from the native engine using the raw string API
            try {
                mnnBridge.generateStream(rawPrompt)
                    .flowOn(Dispatchers.IO)
                    .collect { fullTextSoFar ->
                        // STOP TOKEN interception for various model formats
                        var cleanText = fullTextSoFar
                        val shouldStop = fullTextSoFar.contains("<|im_end|>") || 
                                         fullTextSoFar.contains("<|endoftext|>") ||
                                         fullTextSoFar.contains("###")
                        
                        if (shouldStop) {
                            cleanText = fullTextSoFar.substringBefore("<|im_end|>")
                                .substringBefore("<|endoftext|>")
                                .substringBefore("###")
                        }

                        _messages.update { msgs ->
                            msgs.map {
                                if (it.id == responseId) it.copy(content = cleanText.trim())
                                else it
                            }
                        }
                        
                        if (shouldStop) {
                            throw kotlinx.coroutines.CancellationException("Stop token reached")
                        }
                    }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Expected when hitting stop tokens or user cancels
                throw e
            } catch (e: Exception) {
                _messages.update { msgs ->
                    msgs.map {
                        if (it.id == responseId) it.copy(
                            content = it.content.ifEmpty { "I encountered an issue. Please try again." }
                        )
                        else it
                    }
                }
            }

            _uiState.value = if (isDiagnostic) TutorUiState.DiagnosticOnboarding
                             else TutorUiState.Tutoring
        }
    }

    /**
     * Speaks the provided text using Text-to-Speech.
     */
    fun speakMessage(text: String) {
        if (text.isNotBlank()) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
        }
    }

    /**
     * Builds a raw ChatML string from the conversation history.
     */
    private fun buildRawPrompt(systemPrompt: String): String {
        val sb = java.lang.StringBuilder()

        if (systemPrompt.isNotBlank()) {
            sb.append("<|im_start|>system\n").append(systemPrompt).append("<|im_end|>\n")
        }

        // Recent conversation history (last 10 turns)
        val recentMessages = _messages.value.takeLast(10)
        for (msg in recentMessages) {
            val role = when (msg.role) {
                MessageRole.USER -> "user"
                MessageRole.ASSISTANT -> "assistant"
                MessageRole.SYSTEM -> "system"
            }
            if (msg.content.isNotBlank()) {
                sb.append("<|im_start|>").append(role).append("\n").append(msg.content).append("<|im_end|>\n")
            }
        }
        
        sb.append("<|im_start|>assistant\n")
        return sb.toString()
    }

    private fun addSystemMessage(content: String) {
        val message = ChatMessage(
            id = UUID.randomUUID().toString(),
            role = MessageRole.ASSISTANT,
            content = content
        )
        _messages.update { it + message }
    }

    override fun onCleared() {
        super.onCleared()
        mnnBridge.release()
    }
}

// ─── UI State ────────────────────────────────────────────────────────
sealed class TutorUiState(val displayName: String) {
    data object InitializingModel : TutorUiState("Loading AI Engine...")
    data object ModelNotFound : TutorUiState("Model Not Found")
    data object DiagnosticOnboarding : TutorUiState("Getting to Know You")
    data object Tutoring : TutorUiState("Ready")
    data object GeneratingResponse : TutorUiState("Thinking...")
    data object GeneratingQuiz : TutorUiState("Creating Quiz...")
    data object Listening : TutorUiState("Listening...")
    data object Idle : TutorUiState("Idle")
    data class Error(val message: String) : TutorUiState("Error")
}

// ─── Available Model ─────────────────────────────────────────────────
data class AvailableModel(
    val name: String,
    val configPath: String,
    val size: String
)

// ─── Chat Message Model ──────────────────────────────────────────────
data class ChatMessage(
    val id: String,
    val role: MessageRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

enum class MessageRole {
    USER, ASSISTANT, SYSTEM
}
