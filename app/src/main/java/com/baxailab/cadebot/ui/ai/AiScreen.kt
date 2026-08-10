package com.baxailab.cadebot.ui.ai

import android.Manifest
import android.content.pm.PackageManager
import android.speech.tts.TextToSpeech
import java.util.Locale
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicNone
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.baxailab.cadebot.BuildConfig
import com.baxailab.cadebot.data.model.AiMessage
import com.baxailab.cadebot.data.model.MenuItem
import com.baxailab.cadebot.ui.components.PriceText
import com.baxailab.cadebot.ui.theme.*
import kotlinx.coroutines.launch

private val quickQueries = listOf(
    "Món nào không có cà phê?",
    "Gợi ý món ít ngọt",
    "Có combo ưu đãi không?",
    "Latte có caffeine không?",
    "Món nào uống nóng ngon?"
)

@Composable
fun AiScreen(
    onBack: () -> Unit,
    onAddToCart: (String) -> Unit,
    viewModel: AiViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val sttService = remember { GroqSttService(context) }
    DisposableEffect(sttService) {
        onDispose { sttService.release() }
    }

    // TTS — đọc câu trả lời của Cadebot
    val tts = remember {
        TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                // no-op, language set below
            }
        }
    }
    LaunchedEffect(Unit) {
        tts.language = Locale("vi", "VN")
    }
    DisposableEffect(tts) {
        onDispose {
            tts.stop()
            tts.shutdown()
        }
    }
    val lastMessage = uiState.messages.lastOrNull()
    LaunchedEffect(lastMessage?.id) {
        lastMessage?.takeIf { !it.isUser }?.let { msg ->
            tts.speak(msg.content, TextToSpeech.QUEUE_FLUSH, null, msg.id)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            sttService.startRecording()
            viewModel.setListening(true)
        }
    }

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    val micTint by animateColorAsState(
        targetValue = if (uiState.isListening) Color.Red else VivaGray,
        label = "mic_color"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VivaFoam)
    ) {
        // Top bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(VivaEspresso, VivaCoffee)))
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại", tint = VivaOnDark)
            }
            Row(
                modifier = Modifier.align(Alignment.Center),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Default.SmartToy, contentDescription = null, tint = VivaOnDark, modifier = Modifier.size(20.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Hỏi Cadebot", style = MaterialTheme.typography.headlineSmall, color = VivaOnDark)
                    Text("Trợ lý AI Viva Reserve", style = MaterialTheme.typography.labelSmall, color = VivaLatte)
                }
            }
        }

        // Quick query chips
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .background(VivaCream)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(quickQueries) { query ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(VivaEspresso)
                        .clickable { viewModel.sendQuickQuery(query) }
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    Text(query, style = MaterialTheme.typography.labelSmall, color = VivaOnDark)
                }
            }
        }

        // Messages
        LazyColumn(
            modifier = Modifier.weight(1f),
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(uiState.messages) { message ->
                MessageBubble(message = message)
            }
            if (uiState.isTyping) {
                item { TypingIndicator() }
            }
        }

        // Recommended items
        if (uiState.recommendedItems.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(VivaCream)
                    .padding(12.dp)
            ) {
                Text("Gợi ý cho bạn:", style = MaterialTheme.typography.labelMedium, color = VivaCoffee)
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(uiState.recommendedItems) { item ->
                        RecommendedItemChip(item = item, onAdd = { onAddToCart(item.menuItemId) })
                    }
                }
            }
        }

        // Input bar
        Surface(shadowElevation = 8.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = uiState.inputText,
                    onValueChange = viewModel::onInputChange,
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            when {
                                uiState.isListening -> "Đang ghi âm... nhấn lại để dừng"
                                uiState.isTranscribing -> "Đang nhận diện giọng nói..."
                                else -> "Hỏi Cadebot về menu..."
                            },
                            color = VivaGray
                        )
                    },
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VivaEspresso,
                        unfocusedBorderColor = when {
                            uiState.isListening -> Color.Red
                            uiState.isTranscribing -> VivaCaramel
                            else -> VivaLatte
                        }
                    ),
                    singleLine = true,
                    enabled = !uiState.isListening && !uiState.isTranscribing
                )

                if (uiState.isTranscribing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(40.dp),
                        color = VivaEspresso,
                        strokeWidth = 3.dp
                    )
                } else {
                    IconButton(
                        onClick = {
                            if (uiState.isListening) {
                                sttService.stopRecording()
                                viewModel.setListening(false)
                                viewModel.setTranscribing(true)
                                scope.launch {
                                    val text = sttService.transcribe(BuildConfig.CADEBOT_API_URL)
                                    if (!text.isNullOrBlank()) viewModel.onSpeechResult(text)
                                    else viewModel.setTranscribing(false)
                                }
                            } else if (ContextCompat.checkSelfPermission(
                                    context, Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                            ) {
                                sttService.startRecording()
                                viewModel.setListening(true)
                            } else {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = if (uiState.isListening) Icons.Default.Mic else Icons.Default.MicNone,
                            contentDescription = if (uiState.isListening) "Dừng ghi âm" else "Nói để nhập",
                            tint = micTint
                        )
                    }
                }

                IconButton(
                    onClick = viewModel::sendMessage,
                    enabled = !uiState.isListening && !uiState.isTranscribing,
                    modifier = Modifier
                        .size(48.dp)
                        .background(VivaEspresso, CircleShape)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Gửi", tint = VivaOnDark)
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: AiMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!message.isUser) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(VivaCoffee, VivaCaramel))),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.SmartToy,
                    contentDescription = null,
                    tint = VivaOnDark,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
        }

        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = if (message.isUser) 20.dp else 4.dp,
                        topEnd = if (message.isUser) 4.dp else 20.dp,
                        bottomStart = 20.dp, bottomEnd = 20.dp
                    )
                )
                .background(if (message.isUser) VivaEspresso else VivaSurface)
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Text(
                text = message.content,
                style = MaterialTheme.typography.bodyMedium,
                color = if (message.isUser) VivaOnDark else VivaEspresso
            )
        }
    }
}

@Composable
private fun TypingIndicator() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(VivaCoffee, VivaCaramel))),
            contentAlignment = Alignment.Center
        ) {
            Text("🤖", style = MaterialTheme.typography.bodyMedium)
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(VivaSurface)
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Text("Cadebot đang trả lời...", style = MaterialTheme.typography.bodyMedium, color = VivaGray)
        }
    }
}

@Composable
private fun RecommendedItemChip(item: MenuItem, onAdd: () -> Unit) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = VivaSurface),
        elevation = CardDefaults.cardElevation(2.dp),
        modifier = Modifier.width(160.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleMedium,
                color = VivaEspresso,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            PriceText(amount = item.price, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(VivaEspresso)
                    .clickable(onClick = onAdd)
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("+ Thêm vào giỏ", style = MaterialTheme.typography.labelSmall, color = VivaOnDark)
            }
        }
    }
}
