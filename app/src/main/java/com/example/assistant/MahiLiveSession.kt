package com.example.assistant

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

enum class SessionState {
    DISCONNECTED,
    CONNECTING,
    LISTENING,
    SPEAKING
}

data class ToolCall(
    val id: String,
    val name: String,
    val args: Map<String, String>
)

data class ToolExecutionEvent(
    val id: String,
    val name: String,
    val summary: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Manages the real-time bidirectional WebSocket session with the Gemini Live API.
 * Uses @google/genai protocol: gemini-2.5-flash-native-audio-preview-12-2025.
 */
class MahiLiveSession(
    private val audioStreamer: AudioStreamer,
    private val onToolExecute: (ToolCall) -> String
) {
    companion object {
        private const val TAG = "MahiLiveSession"
        private const val LIVE_ENDPOINT =
            "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent"
        const val LIVE_MODEL = "models/gemini-2.5-flash-native-audio-preview-12-2025"
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private var webSocket: WebSocket? = null
    private var okHttpClient: OkHttpClient? = null

    private val _sessionState = MutableStateFlow(SessionState.DISCONNECTED)
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    private val _currentTranscript = MutableStateFlow("")
    val currentTranscript: StateFlow<String> = _currentTranscript.asStateFlow()

    private val _lastToolEvent = MutableStateFlow<ToolExecutionEvent?>(null)
    val lastToolEvent: StateFlow<ToolExecutionEvent?> = _lastToolEvent.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var activeVibe: MahiVibe = MahiVibe.SASSY_FLIRTY
    private var voiceName: String = MahiPersonality.DEFAULT_VOICE
    private var isSetupComplete = false

    init {
        // Wire audio streamer callbacks
        audioStreamer.onAudioChunkCaptured = { pcmChunk ->
            if (sessionState.value != SessionState.DISCONNECTED && isSetupComplete) {
                sendAudioChunk(pcmChunk)
            }
        }

        audioStreamer.onPlaybackFinished = {
            if (_sessionState.value == SessionState.SPEAKING) {
                _sessionState.value = SessionState.LISTENING
            }
        }
    }

    fun setVibe(vibe: MahiVibe) {
        activeVibe = vibe
        // If already connected, can notify live or will apply on next connect
    }

    fun setVoice(voice: String) {
        voiceName = voice
    }

    /**
     * Connect to the Gemini Live WebSocket using the provided API key.
     */
    fun connect(apiKey: String) {
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            _errorMessage.value = "Missing Gemini API Key. Configure it in AI Studio Secrets panel."
            return
        }

        if (_sessionState.value == SessionState.CONNECTING || _sessionState.value == SessionState.LISTENING) {
            return
        }

        _sessionState.value = SessionState.CONNECTING
        _errorMessage.value = null
        _currentTranscript.value = ""
        isSetupComplete = false

        okHttpClient = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS) // No timeout for long-lived WebSocket
            .writeTimeout(30, TimeUnit.SECONDS)
            .connectTimeout(30, TimeUnit.SECONDS)
            .build()

        val requestUrl = "$LIVE_ENDPOINT?key=$apiKey"
        val request = Request.Builder()
            .url(requestUrl)
            .build()

        webSocket = okHttpClient?.newWebSocket(request, createWebSocketListener())
    }

    private fun createWebSocketListener(): WebSocketListener {
        return object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "Gemini Live WebSocket opened. Sending setup payload...")
                sendSetupMessage()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleServerMessage(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                handleServerMessage(bytes.utf8())
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closing: $code / $reason")
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $code / $reason")
                handleDisconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure", t)
                scope.launch {
                    _errorMessage.value = "Connection error: ${t.message ?: "Network failure"}"
                    handleDisconnect()
                }
            }
        }
    }

    private fun sendSetupMessage() {
        try {
            val setupObj = JSONObject().apply {
                put("model", LIVE_MODEL)

                val generationConfig = JSONObject().apply {
                    put("responseModalities", JSONArray().put("AUDIO"))
                    val speechConfig = JSONObject().apply {
                        val voiceConfig = JSONObject().apply {
                            val prebuiltVoiceConfig = JSONObject().apply {
                                put("voiceName", voiceName)
                            }
                            put("prebuiltVoiceConfig", prebuiltVoiceConfig)
                        }
                        put("voiceConfig", voiceConfig)
                    }
                    put("speechConfig", speechConfig)
                }
                put("generationConfig", generationConfig)

                val systemInstruction = JSONObject().apply {
                    val partsArray = JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", MahiPersonality.buildSystemInstruction(activeVibe))
                        })
                    }
                    put("parts", partsArray)
                }
                put("systemInstruction", systemInstruction)

                // Tools definition
                val toolsArray = JSONArray().apply {
                    val functionDeclarations = JSONArray().apply {
                        // Tool 1: openWebsite
                        put(JSONObject().apply {
                            put("name", "openWebsite")
                            put("description", "Opens a website URL in the device browser or searches online.")
                            val params = JSONObject().apply {
                                put("type", "OBJECT")
                                val properties = JSONObject().apply {
                                    put("url", JSONObject().apply {
                                        put("type", "STRING")
                                        put("description", "The website URL to launch, e.g. https://youtube.com")
                                    })
                                }
                                put("properties", properties)
                                put("required", JSONArray().put("url"))
                            }
                            put("parameters", params)
                        })

                        // Tool 2: changeVibe
                        put(JSONObject().apply {
                            put("name", "changeVibe")
                            put("description", "Changes Mahi's personality mood or vibe style.")
                            val params = JSONObject().apply {
                                put("type", "OBJECT")
                                val properties = JSONObject().apply {
                                    put("mood", JSONObject().apply {
                                        put("type", "STRING")
                                        put("description", "Vibe id: sassy_flirty, sweet_bestie, cheeky_queen, midnight_talk")
                                    })
                                }
                                put("properties", properties)
                                put("required", JSONArray().put("mood"))
                            }
                            put("parameters", params)
                        })
                    }
                    put(JSONObject().apply {
                        put("functionDeclarations", functionDeclarations)
                    })
                }
                put("tools", toolsArray)
            }

            val payload = JSONObject().apply {
                put("setup", setupObj)
            }

            webSocket?.send(payload.toString())
            Log.d(TAG, "Sent setup payload")
            isSetupComplete = true
            _sessionState.value = SessionState.LISTENING
            audioStreamer.startRecording()
        } catch (e: Exception) {
            Log.e(TAG, "Error building setup payload", e)
            _errorMessage.value = "Failed to initiate session setup: ${e.message}"
            disconnect()
        }
    }

    private fun sendAudioChunk(pcmChunk: ByteArray) {
        try {
            val base64Data = Base64.encodeToString(pcmChunk, Base64.NO_WRAP)
            val chunkObj = JSONObject().apply {
                val realtimeInput = JSONObject().apply {
                    val mediaChunks = JSONArray().apply {
                        put(JSONObject().apply {
                            put("mimeType", "audio/pcm;rate=16000")
                            put("data", base64Data)
                        })
                    }
                    put("mediaChunks", mediaChunks)
                }
                put("realtimeInput", realtimeInput)
            }
            webSocket?.send(chunkObj.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error sending audio chunk", e)
        }
    }

    private fun handleServerMessage(jsonString: String) {
        try {
            val root = JSONObject(jsonString)

            // 1. Check for serverContent
            if (root.has("serverContent")) {
                val serverContent = root.getJSONObject("serverContent")

                // Interrupted event: user spoke during model response
                if (serverContent.optBoolean("interrupted", false)) {
                    Log.d(TAG, "Gemini Live interrupted by user speech")
                    audioStreamer.interrupt()
                    _sessionState.value = SessionState.LISTENING
                    return
                }

                if (serverContent.has("modelTurn")) {
                    val modelTurn = serverContent.getJSONObject("modelTurn")
                    val parts = modelTurn.optJSONArray("parts")
                    if (parts != null) {
                        for (i in 0 until parts.length()) {
                            val part = parts.getJSONObject(i)

                            // Audio playback
                            if (part.has("inlineData")) {
                                val inlineData = part.getJSONObject("inlineData")
                                val data = inlineData.optString("data")
                                if (data.isNotEmpty()) {
                                    val audioBytes = Base64.decode(data, Base64.DEFAULT)
                                    _sessionState.value = SessionState.SPEAKING
                                    audioStreamer.enqueueAudioChunk(audioBytes)
                                }
                            }

                            // Subtitles / transcript
                            if (part.has("text")) {
                                val text = part.getString("text")
                                _currentTranscript.value += text
                            }
                        }
                    }
                }

                if (serverContent.optBoolean("turnComplete", false)) {
                    // Turn finished; if audio has finished playing, will transition to LISTENING
                }
            }

            // 2. Check for toolCall
            if (root.has("toolCall")) {
                val toolCallObj = root.getJSONObject("toolCall")
                val functionCalls = toolCallObj.optJSONArray("functionCalls")
                if (functionCalls != null && functionCalls.length() > 0) {
                    handleFunctionCalls(functionCalls)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing server message", e)
        }
    }

    private fun handleFunctionCalls(functionCalls: JSONArray) {
        scope.launch {
            val responses = JSONArray()

            for (i in 0 until functionCalls.length()) {
                val fc = functionCalls.getJSONObject(i)
                val id = fc.getString("id")
                val name = fc.getString("name")
                val argsMap = mutableMapOf<String, String>()

                val argsObj = fc.optJSONObject("args")
                if (argsObj != null) {
                    val keys = argsObj.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        argsMap[key] = argsObj.optString(key)
                    }
                }

                val toolCall = ToolCall(id = id, name = name, args = argsMap)
                val summary = onToolExecute(toolCall)

                _lastToolEvent.value = ToolExecutionEvent(
                    id = id,
                    name = name,
                    summary = summary
                )

                // Build toolResponse
                val fnResponse = JSONObject().apply {
                    put("id", id)
                    val responseOutput = JSONObject().apply {
                        val outputObj = JSONObject().apply {
                            put("status", "success")
                            put("result", summary)
                        }
                        put("output", outputObj)
                    }
                    put("response", responseOutput)
                }
                responses.put(fnResponse)
            }

            // Send toolResponse back to Gemini Live
            val toolResponsePayload = JSONObject().apply {
                val toolResponse = JSONObject().apply {
                    put("functionResponses", responses)
                }
                put("toolResponse", toolResponse)
            }

            webSocket?.send(toolResponsePayload.toString())
            Log.d(TAG, "Sent toolResponse payload")
        }
    }

    fun interrupt() {
        audioStreamer.interrupt()
        _sessionState.value = SessionState.LISTENING
    }

    fun disconnect() {
        try {
            audioStreamer.stopRecording()
            audioStreamer.interrupt()
            webSocket?.close(1000, "User disconnected")
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting", e)
        } finally {
            handleDisconnect()
        }
    }

    private fun handleDisconnect() {
        webSocket = null
        isSetupComplete = false
        _sessionState.value = SessionState.DISCONNECTED
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun updateTranscript(text: String) {
        _currentTranscript.value = text
    }

    fun setDirectSpeakingState() {
        _sessionState.value = SessionState.SPEAKING
    }

    fun setDirectListeningState() {
        _sessionState.value = SessionState.LISTENING
    }
}
