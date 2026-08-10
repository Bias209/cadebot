package com.baxailab.cadebot.data.model

import kotlinx.serialization.Serializable

@Serializable
data class FaqResponse(
    val faqs: List<FaqItem>
)

@Serializable
data class FaqItem(
    val id: String,
    val question: String,
    val answer: String,
    val relatedItems: List<String>,
    val tags: List<String>
)

data class AiMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val content: String,
    val isUser: Boolean,
    val spokenText: String = "",
    val recommendedItems: List<String> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)

enum class AiIntent {
    MENU_QA, RECOMMENDATION, ADD_TO_CART_DRAFT, PROMOTION_QA, CALL_STAFF, FALLBACK
}
