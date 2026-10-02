package com.example.assistant.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.assistant.MahiPersonality
import com.example.assistant.MahiVibe
import com.example.assistant.MahiViewModel
import com.example.assistant.SessionState
import com.example.ui.theme.DarkBg
import com.example.ui.theme.DarkCardBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.ListeningGreen
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonPink
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.SpeakingPurple
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MahiScreen(
    viewModel: MahiViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var showInfoSheet by remember { mutableStateOf(false) }
    var permissionDeniedDialog by remember { mutableStateOf(false) }

    // Audio permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.toggleSession()
        } else {
            permissionDeniedDialog = true
        }
    }

    // Handle tool-invoked URL launches safely
    LaunchedEffect(uiState.pendingUrlToOpen) {
        uiState.pendingUrlToOpen?.let { url ->
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(context, "Could not open link: $url", Toast.LENGTH_SHORT).show()
            } finally {
                viewModel.clearPendingUrl()
            }
        }
    }

    // Handle errors via snackbar
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissError()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = DarkBg,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            DarkBg,
                            Color(0xFF0F111E),
                            DarkBg
                        )
                    )
                )
                .windowInsetsPadding(WindowInsets.systemBars)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top App Bar
                TopBar(
                    sessionState = uiState.sessionState,
                    isKeyConfigured = uiState.isKeyConfigured,
                    isDemoMode = uiState.isDemoMode,
                    activeVibe = uiState.activeVibe,
                    onInfoClick = { showInfoSheet = true }
                )

                // Personality Vibe Selector Bar
                VibeSelectorRow(
                    activeVibe = uiState.activeVibe,
                    onSelectVibe = { vibe -> viewModel.selectVibe(vibe) }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Central Interactive Voice Arena
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    MahiOrb(
                        sessionState = uiState.sessionState,
                        inputAmplitude = uiState.inputAmplitude,
                        outputAmplitude = uiState.outputAmplitude,
                        onOrbClick = {
                            val hasPermission = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED

                            if (hasPermission) {
                                viewModel.toggleSession()
                            } else {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Status subtitle text
                    Text(
                        text = when (uiState.sessionState) {
                            SessionState.DISCONNECTED -> "Tap orb to wake Mahi"
                            SessionState.CONNECTING -> "Connecting neural live session..."
                            SessionState.LISTENING -> "Mahi is listening... say anything"
                            SessionState.SPEAKING -> "Mahi speaking • tap to interrupt"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = when (uiState.sessionState) {
                            SessionState.SPEAKING -> NeonPink
                            SessionState.LISTENING -> NeonCyan
                            SessionState.CONNECTING -> NeonViolet
                            SessionState.DISCONNECTED -> TextSecondary
                        },
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Real-time audio waveform visualizer
                    WaveformVisualizer(
                        amplitude = if (uiState.sessionState == SessionState.SPEAKING) uiState.outputAmplitude else uiState.inputAmplitude,
                        sessionState = uiState.sessionState
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Live Subtitle Transcript Card & Tool Events
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Tool Call Feedback banner
                    AnimatedVisibility(
                        visible = uiState.lastToolEvent != null,
                        enter = fadeIn() + slideInVertically(),
                        exit = fadeOut() + slideOutVertically()
                    ) {
                        uiState.lastToolEvent?.let { event ->
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFF132238)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp)
                                    .border(1.dp, NeonCyan.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.OpenInBrowser,
                                        contentDescription = null,
                                        tint = NeonCyan,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Action: ${event.summary}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = NeonCyan,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    // Transcript Glass Card
                    if (uiState.transcript.isNotEmpty()) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = DarkSurface.copy(alpha = 0.85f)
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, DarkCardBorder, RoundedCornerShape(16.dp))
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .background(NeonPink, CircleShape)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "MAHI",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = NeonPink,
                                            letterSpacing = 1.sp
                                        )
                                    }

                                    if (uiState.sessionState == SessionState.SPEAKING) {
                                        TextButton(
                                            onClick = { viewModel.interrupt() },
                                            modifier = Modifier.height(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Stop,
                                                contentDescription = "Interrupt",
                                                tint = TextSecondary,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                "Interrupt",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = TextSecondary
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = "\"${uiState.transcript}\"",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextPrimary,
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    // Quick Voice Suggestion Chips
                    QuickPromptsRow(
                        prompts = MahiPersonality.QUICK_PROMPTS,
                        onPromptClick = { prompt ->
                            viewModel.onQuickPrompt(prompt)
                        }
                    )
                }
            }
        }
    }

    // Permission Denied Dialog
    if (permissionDeniedDialog) {
        AlertDialog(
            onDismissRequest = { permissionDeniedDialog = false },
            title = { Text("Microphone Access Required") },
            text = {
                Text("Mahi is a voice-to-voice AI assistant. Please allow microphone access to talk directly with Mahi.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        permissionDeniedDialog = false
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonPink)
                ) {
                    Text("Grant Permission")
                }
            },
            dismissButton = {
                TextButton(onClick = { permissionDeniedDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    // Info and Settings Sheet
    if (showInfoSheet) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { showInfoSheet = false },
            sheetState = sheetState,
            containerColor = DarkSurface
        ) {
            InfoAndSettingsSheetContent(
                uiState = uiState,
                onSelectVoice = { voice -> viewModel.selectVoice(voice) },
                onDismiss = { showInfoSheet = false }
            )
        }
    }
}

@Composable
private fun TopBar(
    sessionState: SessionState,
    isKeyConfigured: Boolean,
    isDemoMode: Boolean,
    activeVibe: MahiVibe,
    onInfoClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "MAHI",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(NeonPink.copy(alpha = 0.2f))
                        .border(1.dp, NeonPink.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "LIVE",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = NeonPink,
                        fontSize = 9.sp
                    )
                }
            }
            Text(
                text = "Voice-to-Voice AI Companion",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                fontSize = 11.sp
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            // Live Status Pill
            val statusColor = when (sessionState) {
                SessionState.SPEAKING -> SpeakingPurple
                SessionState.LISTENING -> ListeningGreen
                SessionState.CONNECTING -> NeonViolet
                SessionState.DISCONNECTED -> if (isDemoMode) NeonAmber else Color(0xFF64748B)
            }

            val statusText = when (sessionState) {
                SessionState.SPEAKING -> "Speaking"
                SessionState.LISTENING -> "Active Live"
                SessionState.CONNECTING -> "Connecting..."
                SessionState.DISCONNECTED -> if (isDemoMode) "Voice Demo" else "Standby"
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(statusColor.copy(alpha = 0.15f))
                    .border(1.dp, statusColor.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(statusColor, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = statusColor
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = onInfoClick,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(DarkSurfaceVariant)
                    .testTag("info_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Assistant Info & Settings",
                    tint = TextPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun VibeSelectorRow(
    activeVibe: MahiVibe,
    onSelectVibe: (MahiVibe) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "PERSONA VIBE",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = TextTertiary,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(start = 2.dp, bottom = 6.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MahiVibe.values().forEach { vibe ->
                val isSelected = vibe == activeVibe
                val borderColor = if (isSelected) NeonPink else DarkCardBorder
                val bgColor = if (isSelected) NeonPink.copy(alpha = 0.15f) else DarkSurface

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(bgColor)
                        .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                        .clickable { onSelectVibe(vibe) }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .testTag("vibe_${vibe.id}")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = vibe.emoji, fontSize = 14.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = vibe.title,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) NeonPink else TextSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickPromptsRow(
    prompts: List<String>,
    onPromptClick: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "QUICK TOPICS",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = TextTertiary,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(start = 2.dp, bottom = 6.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            prompts.forEach { prompt ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(DarkSurfaceVariant)
                        .border(1.dp, DarkCardBorder, RoundedCornerShape(20.dp))
                        .clickable { onPromptClick(prompt) }
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                        .testTag("prompt_chip_${prompt.take(6)}")
                ) {
                    Text(
                        text = prompt,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextPrimary,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoAndSettingsSheetContent(
    uiState: com.example.assistant.MahiUiState,
    onSelectVoice: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Mahi AI Assistant",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "Witty, Sassy Voice Companion",
                    style = MaterialTheme.typography.bodySmall,
                    color = NeonPink
                )
            }

            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Live Model Architecture Info
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Gemini Live API Architecture",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = NeonCyan
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "• Model: gemini-2.5-flash-native-audio-preview-12-2025\n" +
                            "• Mode: Pure Audio-to-Audio bidirectional streaming\n" +
                            "• Audio Input: PCM 16-bit @ 16 kHz Mono\n" +
                            "• Audio Output: PCM 16-bit @ 24 kHz Mono\n" +
                            "• Tools: openWebsite, changeVibe",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    lineHeight = 18.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // API Key Status
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = if (uiState.isKeyConfigured) ListeningGreen else NeonAmber,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (uiState.isKeyConfigured) "Gemini API Key Connected" else "Secrets Panel Configuration",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (uiState.isKeyConfigured) {
                        "Your GEMINI_API_KEY is active in BuildConfig. Live bidirectional audio streaming is enabled."
                    } else {
                        "To use real-time live streaming, add GEMINI_API_KEY in the AI Studio Secrets panel. Meanwhile, the interactive Voice Demo engine is fully functional!"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Voice Selector
        Text(
            text = "GEMINI LIVE VOICE",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = TextTertiary,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(8.dp))

        val voices = listOf(
            "Aoede" to "Aoede (Confident & Expressive)",
            "Kore" to "Kore (Bright & Sassy)",
            "Fenrir" to "Fenrir (Deeper Tone)",
            "Puck" to "Puck (Playful & Energetic)"
        )

        voices.forEach { (voiceId, voiceLabel) ->
            val isSelected = uiState.selectedVoice == voiceId
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) NeonPink.copy(alpha = 0.15f) else DarkSurfaceVariant
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .border(
                        1.dp,
                        if (isSelected) NeonPink else DarkCardBorder,
                        RoundedCornerShape(10.dp)
                    )
                    .clickable { onSelectVoice(voiceId) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = voiceLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) NeonPink else TextPrimary
                    )
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(NeonPink, CircleShape)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
