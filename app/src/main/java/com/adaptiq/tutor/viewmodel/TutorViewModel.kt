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
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import com.adaptiq.tutor.engine.ModelDownloader

/**
 * TutorViewModel orchestrates the interaction between the UI, inference engine,
 * persona system, and knowledge tracking.
 *
 * Implements MVI-style unidirectional data flow using [StateFlow].
 */
class TutorViewModel(private val application: Application) : AndroidViewModel(application) {

    // ─── Dependencies ──────────────────────────────────────────────────────────
    private val mnnBridge = MnnBridge()
    private val userPreferences = UserPreferences(application)
    private val database = AdaptIQDatabase.getInstance(application)
    private val diagnosticManager = AdaptiveDiagnosticManager()
    private val knowledgeGapTracker = KnowledgeGapTracker(database.knowledgeGapDao())
    private var tts: TextToSpeech? = null
    private val historyFile = File(application.filesDir, "chat_history.json")
    val modelDownloader = ModelDownloader(application)

    // ─── Predefined Downloadable Models ────────────────────────────────────────
    val downloadableModels = listOf(
        DownloadableModel("qwen3.5-2b", "Qwen 3.5 · 2B", "Fastest. Fits comfortably alongside the dev server.", "0x3/Qwen3.5-2B-MNN"),
        DownloadableModel("qwen3.5-4b", "Qwen 3.5 · 4B", "Better code, fewer malformed tool calls. Needs ~3 GB free.", "0x3/Qwen3.5-4B-MNN"),
        DownloadableModel("qwen3.5-9b", "Qwen 3.5 · 9B", "Excellent reasoning. Needs ~7 GB free.", "hb-dev/Qwen3.5-9B-MNN"),
        DownloadableModel("qwen3.5-27b", "Qwen 3.5 · 27B", "Desktop-class performance. Needs ~16 GB free.", "0x3/Qwen3.5-27B-MNN")
    )

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
        viewModelScope.launch {
            _messages.drop(1).collect {
                saveHistory()
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            loadHistory()
            scanForModels()
            try {
                // Load saved profile if available
                userPreferences.learnerProfile.first()?.let { profile ->
                    currentProfile = profile
                }

                // Load model from saved path only if the file actually exists
                val savedPath = userPreferences.modelPath.first()
                if (!savedPath.isNullOrBlank() && File(savedPath).exists() && File(savedPath).length() > 0) {
                    loadModel(savedPath)
                } else {
                    userPreferences.setModelPath("")
                    _uiState.value = TutorUiState.ModelNotFound
                }
            } catch (e: Exception) {
                _uiState.value = TutorUiState.ModelNotFound
            }
        }
    }

    // ─── Public Actions ──────────────────────────────────────────

    /**
     * Resets the model path and shows the model setup screen.
     */
    fun resetModelSelection() {
        viewModelScope.launch {
            userPreferences.setModelPath("")
            _uiState.value = TutorUiState.ModelNotFound
        }
    }

    /**
     * Loads the MNN model from the specified config path.
     */
    fun loadModel(configPath: String) {
        if (configPath.isBlank()) {
            _uiState.value = TutorUiState.ModelNotFound
            return
        }

        val configFile = File(configPath)
        if (!configFile.exists() || configFile.length() == 0L) {
            viewModelScope.launch {
                userPreferences.setModelPath("")
                _uiState.value = TutorUiState.ModelNotFound
            }
            return
        }

        viewModelScope.launch {
            _uiState.value = TutorUiState.InitializingModel
            try {
                val success = mnnBridge.loadModel(configPath)
                if (!success) {
                    throw ModelLoadException("Engine returned false when loading model from $configPath")
                }
                // Configure the LLM to prevent infinite loops and limit length
                mnnBridge.setConfig("{\"max_new_tokens\": 250, \"temperature\": 0.7, \"repetition_penalty\": 1.1}")
                userPreferences.setModelPath(configPath)
                scanForModels()

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

    private val _flashcards = MutableStateFlow<List<Flashcard>>(emptyList())
    val flashcards: StateFlow<List<Flashcard>> = _flashcards.asStateFlow()
    
    private val _quiz = MutableStateFlow<Quiz?>(null)
    val quiz: StateFlow<Quiz?> = _quiz.asStateFlow()
    
    private val _matchingGame = MutableStateFlow<MatchingGame?>(null)
    val matchingGame: StateFlow<MatchingGame?> = _matchingGame.asStateFlow()

    private val _currentFlashcardIndex = MutableStateFlow(0)
    val currentFlashcardIndex: StateFlow<Int> = _currentFlashcardIndex.asStateFlow()

    fun nextFlashcard() {
        if (_currentFlashcardIndex.value < _flashcards.value.size - 1) {
            _currentFlashcardIndex.value += 1
        }
    }

    fun generateFlashcards() {
        if (_uiState.value == TutorUiState.InitializingModel || _uiState.value == TutorUiState.GeneratingQuiz) return
        
        generationJob?.cancel()
        generationJob = viewModelScope.launch {
            _uiState.value = TutorUiState.GeneratingQuiz
            _flashcards.value = emptyList()
            _currentFlashcardIndex.value = 0
            
            // Build a prompt to ask for flashcards
            val context = _messages.value.takeLast(6).joinToString("\n") { "${it.role.name}: ${it.content}" }
            val prompt = "<|im_start|>system\nYou are a helpful tutor. Based on the recent conversation, generate 3 study flashcards.\n\nFormat EXACTLY like this (do NOT use markdown):\nQ: [question 1]\nA: [answer 1]\n---\nQ: [question 2]\nA: [answer 2]\n---\nQ: [question 3]\nA: [answer 3]<|im_end|>\n<|im_start|>user\nRecent conversation:\n${context}\n\nPlease generate flashcards.<|im_end|>\n<|im_start|>assistant\n"
            
            try {
                var fullText = ""
                mnnBridge.generateStream(prompt).collect { chunk ->
                    fullText = chunk
                }
                
                // Parse it manually
                val blocks = fullText.split("---")
                val cards = mutableListOf<Flashcard>()
                
                for (block in blocks) {
                    val qMatch = Regex("Q:\\s*(.+)").find(block)
                    val aMatch = Regex("A:\\s*(.+)").find(block)
                    
                    if (qMatch != null && aMatch != null) {
                        cards.add(Flashcard(qMatch.groupValues[1].trim(), aMatch.groupValues[1].trim()))
                    }
                }
                
                if (cards.isNotEmpty()) {
                    _flashcards.value = cards
                } else {
                    _flashcards.value = listOf(
                        Flashcard("What is Orbital Mechanics?", "The study of the motions of artificial satellites and space vehicles."),
                        Flashcard("What is Vis-Viva?", "An equation that models the velocity of any body in an elliptic orbit.")
                    )
                }
            } catch (e: Exception) {
                _flashcards.value = listOf(
                    Flashcard("Error generating flashcards", "Please try again later.")
                )
            } finally {
                _uiState.value = TutorUiState.Tutoring
            }
        }
    }
    
    fun generateQuiz() {
        if (_uiState.value == TutorUiState.InitializingModel || _uiState.value == TutorUiState.GeneratingQuiz) return
        
        generationJob?.cancel()
        generationJob = viewModelScope.launch {
            _uiState.value = TutorUiState.GeneratingQuiz
            _quiz.value = null
            
            val context = _messages.value.takeLast(6).joinToString("\n") { "${it.role.name}: ${it.content}" }
            val prompt = "<|im_start|>system\nYou are a helpful tutor. Based on the recent conversation, generate 1 multiple choice question.\n\nFormat EXACTLY like this (no markdown):\nQ: [question]\n1. [option 1]\n2. [option 2]\n3. [option 3]\n4. [option 4]\nA: [correct index 1-4]<|im_end|>\n<|im_start|>user\nRecent conversation:\n${context}\n\nPlease generate a question.<|im_end|>\n<|im_start|>assistant\n"
            
            try {
                var fullText = ""
                mnnBridge.generateStream(prompt).collect { chunk ->
                    fullText = chunk
                }
                
                val qMatch = Regex("Q:\\s*(.+)").find(fullText)
                val options = mutableListOf<String>()
                for (i in 1..4) {
                    val oMatch = Regex("$i\\.\\s*(.+)").find(fullText)
                    if (oMatch != null) options.add(oMatch.groupValues[1].trim())
                }
                val aMatch = Regex("A:\\s*([1-4])").find(fullText)
                
                if (qMatch != null && options.size == 4 && aMatch != null) {
                    val correctIdx = aMatch.groupValues[1].toInt() - 1
                    _quiz.value = Quiz(qMatch.groupValues[1].trim(), options, correctIdx)
                } else {
                    _quiz.value = Quiz("What is the capital of France?", listOf("Berlin", "Madrid", "Paris", "Rome"), 2)
                }
            } catch (e: Exception) {
                _quiz.value = Quiz("Error generating quiz", listOf("A", "B", "C", "D"), 0)
            } finally {
                _uiState.value = TutorUiState.Tutoring
            }
        }
    }
    
    fun generateMatchingGame() {
        if (_uiState.value == TutorUiState.InitializingModel || _uiState.value == TutorUiState.GeneratingQuiz) return
        
        generationJob?.cancel()
        generationJob = viewModelScope.launch {
            _uiState.value = TutorUiState.GeneratingQuiz
            _matchingGame.value = null
            
            val context = _messages.value.takeLast(6).joinToString("\n") { "${it.role.name}: ${it.content}" }
            val prompt = "<|im_start|>system\nYou are a helpful tutor. Based on the recent conversation, generate 4 pairs for a matching game.\n\nFormat EXACTLY like this (no markdown):\n1. [Term 1] | [Definition 1]\n2. [Term 2] | [Definition 2]\n3. [Term 3] | [Definition 3]\n4. [Term 4] | [Definition 4]<|im_end|>\n<|im_start|>user\nRecent conversation:\n${context}\n\nPlease generate matching pairs.<|im_end|>\n<|im_start|>assistant\n"
            
            try {
                var fullText = ""
                mnnBridge.generateStream(prompt).collect { chunk ->
                    fullText = chunk
                }
                
                val pairs = mutableListOf<MatchingPair>()
                for (i in 1..4) {
                    val pMatch = Regex("$i\\.\\s*(.+?)\\s*\\|\\s*(.+)").find(fullText)
                    if (pMatch != null) {
                        pairs.add(MatchingPair(pMatch.groupValues[1].trim(), pMatch.groupValues[2].trim()))
                    }
                }
                
                if (pairs.size >= 2) {
                    _matchingGame.value = MatchingGame(pairs)
                } else {
                    _matchingGame.value = MatchingGame(listOf(
                        MatchingPair("Apple", "A red fruit"),
                        MatchingPair("Dog", "Man's best friend"),
                        MatchingPair("Car", "A vehicle with four wheels")
                    ))
                }
            } catch (e: Exception) {
                _matchingGame.value = MatchingGame(listOf(
                    MatchingPair("Error", "Could not generate"),
                    MatchingPair("Try", "Again later")
                ))
            } finally {
                _uiState.value = TutorUiState.Tutoring
            }
        }
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

            val responseId = UUID.randomUUID().toString()

            // Guard: Check if a model is actually loaded in native memory
            if (!mnnBridge.isLoaded) {
                val notLoadedMessage = "⚠️ No AI model is currently loaded on your device.\n\nPlease tap the Settings ⚙️ icon in the top right corner to download and select an on-device Qwen 3.5 model."
                _messages.update { it + ChatMessage(id = responseId, role = MessageRole.ASSISTANT, content = notLoadedMessage) }
                _uiState.value = if (isDiagnostic) TutorUiState.DiagnosticOnboarding else TutorUiState.Tutoring
                return@launch
            }

            // Build the system prompt based on current mode
            val systemPrompt = if (isDiagnostic) {
                diagnosticManager.getNextDiagnosticPrompt() ?: ""
            } else {
                currentProfile?.let { PersonaEngine.buildSystemPrompt(it) } ?: ""
            }

            // Build ChatML format manually to bypass MNN's broken templating
            val rawPrompt = buildRawPrompt(systemPrompt)

            // Create a placeholder message for streaming
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
            } catch (e: Exception) {
                _messages.update { msgs ->
                    msgs.map {
                        if (it.id == responseId) it.copy(
                            content = it.content.ifEmpty { "I encountered an issue generating a response. Please check model status in Settings ⚙️." }
                        )
                        else it
                    }
                }
            } finally {
                // Ensure the assistant message is never left completely blank
                _messages.update { msgs ->
                    msgs.map {
                        if (it.id == responseId && it.content.isBlank()) {
                            it.copy(content = "I didn't receive any tokens from the model. Please check model status in Settings ⚙️ or try asking again.")
                        } else it
                    }
                }
                _uiState.value = if (isDiagnostic) TutorUiState.DiagnosticOnboarding
                                 else TutorUiState.Tutoring
            }
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
    
    private fun saveHistory() {
        try {
            val json = Json.encodeToString(_messages.value)
            historyFile.writeText(json)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private suspend fun loadHistory() {
        try {
            if (historyFile.exists()) {
                val json = historyFile.readText()
                val savedMessages = Json.decodeFromString<List<ChatMessage>>(json)
                if (savedMessages.isNotEmpty()) {
                    _messages.value = savedMessages
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
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

// ─── Practice Lab Models ────────────────────────────────────────────────────────
data class Flashcard(
    val front: String,
    val back: String
)

data class Quiz(
    val question: String,
    val options: List<String>,
    val correctIndex: Int
)

data class MatchingGame(
    val pairs: List<MatchingPair>
)

data class MatchingPair(
    val term: String,
    val definition: String
)

// ─── Available Model ──────────────────────────────────────────────────────────
data class AvailableModel(
    val name: String,
    val configPath: String,
    val size: String
)

data class DownloadableModel(
    val id: String,
    val name: String,
    val description: String,
    val repoId: String
)

// ─── Chat Message Model ──────────────────────────────────────────────────────
@Serializable
data class ChatMessage(
    val id: String,
    val role: MessageRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
enum class MessageRole {
    USER, ASSISTANT, SYSTEM
}
