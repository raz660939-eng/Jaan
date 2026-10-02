package com.example.assistant

import android.app.Application
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

data class MahiUiState(
    val sessionState: SessionState = SessionState.DISCONNECTED,
    val activeVibe: MahiVibe = MahiVibe.SASSY_FLIRTY,
    val selectedVoice: String = MahiPersonality.DEFAULT_VOICE,
    val inputAmplitude: Float = 0f,
    val outputAmplitude: Float = 0f,
    val transcript: String = "",
    val lastToolEvent: ToolExecutionEvent? = null,
    val errorMessage: String? = null,
    val isKeyConfigured: Boolean = false,
    val isDemoMode: Boolean = false,
    val pendingUrlToOpen: String? = null
)

class MahiViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "MahiViewModel"
    }

    val audioStreamer = AudioStreamer(application.applicationContext)

    private val _uiState = MutableStateFlow(MahiUiState())
    val uiState: StateFlow<MahiUiState> = _uiState.asStateFlow()

    private var textToSpeech: TextToSpeech? = null
    private var isTtsReady = false
    private var demoSimulationJob: Job? = null

    val liveSession = MahiLiveSession(
        audioStreamer = audioStreamer,
        onToolExecute = { toolCall -> handleToolExecution(toolCall) }
    )

    init {
        checkApiKeyConfiguration()
        initTextToSpeech(application)
        observeAudioAmplitudes()
        observeLiveSession()
    }

    private fun checkApiKeyConfiguration() {
        val key = BuildConfig.GEMINI_API_KEY
        val isValid = key.isNotBlank() && key != "MY_GEMINI_API_KEY"
        _uiState.update { it.copy(isKeyConfigured = isValid) }
    }

    private fun initTextToSpeech(context: Application) {
        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech?.language = Locale.US
                // Configure playful, confident female cadence
                textToSpeech?.setPitch(1.22f)
                textToSpeech?.setSpeechRate(1.04f)
                isTtsReady = true

                textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _uiState.update { it.copy(sessionState = SessionState.SPEAKING) }
                        startSimulatedAmplitude()
                    }

                    override fun onDone(utteranceId: String?) {
                        stopSimulatedAmplitude()
                        _uiState.update { it.copy(sessionState = SessionState.LISTENING) }
                    }

                    override fun onError(utteranceId: String?) {
                        stopSimulatedAmplitude()
                        _uiState.update { it.copy(sessionState = SessionState.LISTENING) }
                    }
                })
            } else {
                Log.w(TAG, "TTS Initialization failed: $status")
            }
        }
    }

    private fun observeAudioAmplitudes() {
        viewModelScope.launch {
            audioStreamer.inputAmplitude.collect { amp ->
                if (_uiState.value.sessionState == SessionState.LISTENING) {
                    _uiState.update { it.copy(inputAmplitude = amp) }
                }
            }
        }
        viewModelScope.launch {
            audioStreamer.outputAmplitude.collect { amp ->
                if (_uiState.value.sessionState == SessionState.SPEAKING) {
                    _uiState.update { it.copy(outputAmplitude = amp) }
                }
            }
        }
    }

    private fun observeLiveSession() {
        viewModelScope.launch {
            liveSession.sessionState.collect { state ->
                _uiState.update { it.copy(sessionState = state) }
            }
        }
        viewModelScope.launch {
            liveSession.currentTranscript.collect { text ->
                _uiState.update { it.copy(transcript = text) }
            }
        }
        viewModelScope.launch {
            liveSession.lastToolEvent.collect { event ->
                _uiState.update { it.copy(lastToolEvent = event) }
            }
        }
        viewModelScope.launch {
            liveSession.errorMessage.collect { err ->
                if (err != null) {
                    _uiState.update { it.copy(errorMessage = err) }
                }
            }
        }
    }

    fun toggleSession() {
        if (_uiState.value.sessionState != SessionState.DISCONNECTED) {
            disconnect()
        } else {
            connect()
        }
    }

    fun connect() {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isNotBlank() && apiKey != "MY_GEMINI_API_KEY") {
            _uiState.update { it.copy(isDemoMode = false, errorMessage = null) }
            liveSession.setVibe(_uiState.value.activeVibe)
            liveSession.setVoice(_uiState.value.selectedVoice)
            liveSession.connect(apiKey)
        } else {
            // Smart Demo Mode: activates full interactive voice assistant immediately
            _uiState.update {
                it.copy(
                    isDemoMode = true,
                    sessionState = SessionState.LISTENING,
                    errorMessage = null
                )
            }
            audioStreamer.startRecording()
            // Mahi greets the user with her signature sassy voice
            speakWittyPhrase("Hey darling! Mahi is ready. What kind of trouble are we getting into today?")
        }
    }

    fun disconnect() {
        liveSession.disconnect()
        audioStreamer.stopRecording()
        audioStreamer.interrupt()
        textToSpeech?.stop()
        stopSimulatedAmplitude()
        _uiState.update {
            it.copy(
                sessionState = SessionState.DISCONNECTED,
                inputAmplitude = 0f,
                outputAmplitude = 0f
            )
        }
    }

    fun interrupt() {
        audioStreamer.interrupt()
        textToSpeech?.stop()
        stopSimulatedAmplitude()
        liveSession.interrupt()
        _uiState.update {
            it.copy(
                sessionState = SessionState.LISTENING,
                outputAmplitude = 0f
            )
        }
    }

    fun selectVibe(vibe: MahiVibe) {
        _uiState.update { it.copy(activeVibe = vibe) }
        liveSession.setVibe(vibe)
        if (_uiState.value.sessionState != SessionState.DISCONNECTED) {
            val remark = when (vibe) {
                MahiVibe.SASSY_FLIRTY -> "Oh, you want extra sass? Challenge accepted, cutie."
                MahiVibe.SWEET_BESTIE -> "Sweet bestie mode unlocked! Spill the tea, I'm all ears."
                MahiVibe.CHEEKY_QUEEN -> "Queen mode activated. Keep up, darling!"
                MahiVibe.MIDNIGHT_TALK -> "Late night mode. Let's keep it between us."
            }
            speakWittyPhrase(remark)
        }
    }

    fun selectVoice(voice: String) {
        _uiState.update { it.copy(selectedVoice = voice) }
        liveSession.setVoice(voice)
    }

    fun onQuickPrompt(promptText: String) {
        if (_uiState.value.sessionState == SessionState.DISCONNECTED) {
            connect()
        }
        interrupt()
        val response = MahiPersonality.getRandomSassyReply(promptText)

        // Check for tools in prompt
        if (promptText.contains("YouTube", ignoreCase = true)) {
            handleToolExecution(ToolCall("demo_1", "openWebsite", mapOf("url" to "https://m.youtube.com")))
        }

        speakWittyPhrase(response)
    }

    private fun speakWittyPhrase(text: String) {
        _uiState.update { it.copy(transcript = text, sessionState = SessionState.SPEAKING) }
        if (isTtsReady) {
            textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "mahi_voice_${System.currentTimeMillis()}")
        } else {
            // Visual simulation fallback
            startSimulatedAmplitude()
            viewModelScope.launch {
                delay(3000)
                stopSimulatedAmplitude()
                _uiState.update { it.copy(sessionState = SessionState.LISTENING) }
            }
        }
    }

    private fun startSimulatedAmplitude() {
        demoSimulationJob?.cancel()
        demoSimulationJob = viewModelScope.launch(Dispatchers.Default) {
            while (isActive && _uiState.value.sessionState == SessionState.SPEAKING) {
                val wave = (0.35f + (Math.sin(System.currentTimeMillis() / 90.0) * 0.35f).toFloat() + (Math.random() * 0.3f).toFloat()).coerceIn(0.1f, 1.0f)
                _uiState.update { it.copy(outputAmplitude = wave) }
                delay(40)
            }
        }
    }

    private fun stopSimulatedAmplitude() {
        demoSimulationJob?.cancel()
        demoSimulationJob = null
        _uiState.update { it.copy(outputAmplitude = 0f) }
    }

    private fun handleToolExecution(toolCall: ToolCall): String {
        return when (toolCall.name) {
            "openWebsite" -> {
                val url = toolCall.args["url"] ?: "https://www.google.com"
                val normalizedUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    "https://$url"
                } else {
                    url
                }
                _uiState.update { it.copy(pendingUrlToOpen = normalizedUrl) }
                "Opened website in browser: $normalizedUrl"
            }
            "changeVibe" -> {
                val moodId = toolCall.args["mood"] ?: "sassy_flirty"
                val matched = MahiVibe.values().firstOrNull { it.id.equals(moodId, ignoreCase = true) } ?: MahiVibe.SASSY_FLIRTY
                selectVibe(matched)
                "Mahi's vibe set to ${matched.title}"
            }
            else -> {
                "Executed action: ${toolCall.name}"
            }
        }
    }

    fun clearPendingUrl() {
        _uiState.update { it.copy(pendingUrlToOpen = null) }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
        liveSession.clearError()
    }

    override fun onCleared() {
        super.onCleared()
        audioStreamer.release()
        liveSession.disconnect()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
    }
}
