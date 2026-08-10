package com.baxailab.cadebot.ui.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baxailab.cadebot.data.mock.MockMenuService
import com.baxailab.cadebot.data.model.AiMessage
import com.baxailab.cadebot.data.model.MenuItem
import com.baxailab.cadebot.data.remote.CadebotApiService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AiUiState(
    val messages: List<AiMessage> = emptyList(),
    val isTyping: Boolean = false,
    val inputText: String = "",
    val recommendedItems: List<MenuItem> = emptyList(),
    val isListening: Boolean = false,
    val isTranscribing: Boolean = false
)

@HiltViewModel
class AiViewModel @Inject constructor(
    private val cadebotApiService: CadebotApiService,
    private val menuService: MockMenuService
) : ViewModel() {

    private val _uiState = MutableStateFlow(AiUiState())
    val uiState: StateFlow<AiUiState> = _uiState.asStateFlow()

    private val allItems by lazy { menuService.getMenu().items }

    init {
        _uiState.value = _uiState.value.copy(
            messages = listOf(
                AiMessage(
                    content = "Xin chào! Mình là Cadebot, trợ lý AI của Viva Reserve Coffee. Bạn muốn gọi món gì hôm nay, hay cần mình gợi ý theo khẩu vị?",
                    isUser = false
                )
            )
        )
    }

    fun onInputChange(text: String) {
        _uiState.value = _uiState.value.copy(inputText = text)
    }

    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        if (text.isEmpty() || _uiState.value.isTyping) return

        val userMsg = AiMessage(content = text, isUser = true)
        val updatedMessages = _uiState.value.messages + userMsg
        _uiState.value = _uiState.value.copy(
            messages = updatedMessages,
            inputText = "",
            isTyping = true
        )

        viewModelScope.launch {
            val response = cadebotApiService.processQuery(text, updatedMessages)
            val recommended = allItems.filter { it.menuItemId in response.recommendedItems }
            _uiState.value = _uiState.value.copy(
                messages = _uiState.value.messages + response,
                isTyping = false,
                recommendedItems = recommended
            )
        }
    }

    fun sendQuickQuery(query: String) {
        _uiState.value = _uiState.value.copy(inputText = query)
        sendMessage()
    }

    fun setListening(listening: Boolean) {
        _uiState.value = _uiState.value.copy(isListening = listening)
    }

    fun setTranscribing(transcribing: Boolean) {
        _uiState.value = _uiState.value.copy(isTranscribing = transcribing)
    }

    fun onSpeechResult(text: String) {
        _uiState.value = _uiState.value.copy(
            inputText = text,
            isListening = false,
            isTranscribing = false
        )
        // Nói xong là gửi luôn, khách không phải bấm Send nữa.
        sendMessage()
    }
}
