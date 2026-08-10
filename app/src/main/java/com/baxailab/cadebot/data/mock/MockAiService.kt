package com.baxailab.cadebot.data.mock

import android.content.Context
import com.baxailab.cadebot.data.model.AiIntent
import com.baxailab.cadebot.data.model.AiMessage
import com.baxailab.cadebot.data.model.FaqItem
import com.baxailab.cadebot.data.model.FaqResponse
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MockAiService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val faqs: List<FaqItem> by lazy { loadFaqs() }

    private fun loadFaqs(): List<FaqItem> {
        val raw = context.assets.open("config/ai_knowledge/faq.json").bufferedReader().readText()
        return json.decodeFromString<FaqResponse>(raw).faqs
    }

    fun processQuery(userText: String): AiMessage {
        val lower = userText.lowercase()

        val matchedFaq = faqs.firstOrNull { faq ->
            faq.question.lowercase().split(" ").any { word ->
                word.length > 3 && lower.contains(word)
            }
        }

        return if (matchedFaq != null) {
            AiMessage(
                content = matchedFaq.answer,
                isUser = false,
                recommendedItems = matchedFaq.relatedItems
            )
        } else {
            detectIntentFallback(lower)
        }
    }

    private fun detectIntentFallback(lower: String): AiMessage {
        return when {
            lower.contains("caffeine") || lower.contains("cà phê") || lower.contains("ca phe") -> {
                val faq = faqs.find { it.id == "faq_002" }
                AiMessage(content = faq?.answer ?: FALLBACK_TEXT, isUser = false,
                    recommendedItems = faq?.relatedItems ?: emptyList())
            }
            lower.contains("ít ngọt") || lower.contains("không ngọt") || lower.contains("ít đường") -> {
                val faq = faqs.find { it.id == "faq_004" }
                AiMessage(content = faq?.answer ?: FALLBACK_TEXT, isUser = false,
                    recommendedItems = faq?.relatedItems ?: emptyList())
            }
            lower.contains("không cà phê") || lower.contains("không coffee") || lower.contains("no coffee") -> {
                val faq = faqs.find { it.id == "faq_003" }
                AiMessage(content = faq?.answer ?: FALLBACK_TEXT, isUser = false,
                    recommendedItems = faq?.relatedItems ?: emptyList())
            }
            lower.contains("combo") || lower.contains("ưu đãi") || lower.contains("khuyến mãi") -> {
                val faq = faqs.find { it.id == "faq_009" }
                AiMessage(content = faq?.answer ?: FALLBACK_TEXT, isUser = false,
                    recommendedItems = faq?.relatedItems ?: emptyList())
            }
            lower.contains("gọi nhân viên") || lower.contains("cần hỗ trợ") || lower.contains("nhân viên") -> {
                AiMessage(content = "Mình sẽ gọi nhân viên Viva đến hỗ trợ bạn ngay nhé!", isUser = false,
                    recommendedItems = emptyList())
            }
            lower.contains("giao") || lower.contains("robot") || lower.contains("bàn") -> {
                val faq = faqs.find { it.id == "faq_007" }
                AiMessage(content = faq?.answer ?: FALLBACK_TEXT, isUser = false)
            }
            lower.contains("nóng") || lower.contains("hot") -> {
                val faq = faqs.find { it.id == "faq_015" }
                AiMessage(content = faq?.answer ?: FALLBACK_TEXT, isUser = false,
                    recommendedItems = faq?.relatedItems ?: emptyList())
            }
            else -> AiMessage(content = FALLBACK_TEXT, isUser = false)
        }
    }

    fun getGreeting(): AiMessage = AiMessage(
        content = "Xin chào! Mình là Cadebot, trợ lý của Viva Reserve Coffee. Bạn muốn gọi món gì hôm nay, hay cần mình gợi ý theo khẩu vị?",
        isUser = false
    )

    companion object {
        const val FALLBACK_TEXT = "Mình chưa có thông tin chính xác về điều này, để nhân viên Viva hỗ trợ bạn nhé. Hoặc bạn có thể xem thực đơn để chọn món!"
    }
}
