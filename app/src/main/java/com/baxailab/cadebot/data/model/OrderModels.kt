package com.baxailab.cadebot.data.model

import java.util.UUID

// Price delta per size relative to base (M). Matches payment_server.py's pricing_rules seed data.
val SIZE_PRICE_DELTA = mapOf("S" to -5000, "M" to 0, "L" to 10000)

data class CartItem(
    val id: String = UUID.randomUUID().toString(),
    val menuItem: MenuItem,
    val quantity: Int,
    val selectedSize: String,
    val selectedSweetness: String,
    val selectedIce: String,
    val selectedTemperature: String,
    val selectedToppings: List<String>,
    val note: String = ""
) {
    val unitPrice: Int get() {
        val sizeDelta = SIZE_PRICE_DELTA[selectedSize] ?: 0
        val toppingPrice = selectedToppings.size * 5000
        return menuItem.price + sizeDelta + toppingPrice
    }
    val totalPrice: Int get() = unitPrice * quantity
}

data class Order(
    val orderId: String = UUID.randomUUID().toString(),
    val tableId: String,
    val items: List<CartItem>,
    val status: OrderStatus = OrderStatus.PENDING,
    val totalAmount: Int = items.sumOf { it.totalPrice },
    val createdAt: Long = System.currentTimeMillis()
)

enum class OrderStatus {
    PENDING, PAID, PREPARING, READY, DISPATCHED, DELIVERED, CANCELLED
}

data class TableInfo(
    val tableId: String,
    val tablePointId: String,
    val displayName: String,
    val zone: String
)

data class OrderCreateResult(
    val orderCode: String,
    val tableId: String,
    val totalAmount: Int,
    val status: String,
    val qrUrl: String,
    val transferContent: String,
    val expiresAt: String,
    val secondsRemaining: Int
)

data class OrderStatusResult(
    val orderCode: String,
    val status: String,
    val totalAmount: Int,
    val paidAmount: Int?,
    val paidAt: String?,
    val secondsRemaining: Int,
    val qrUrl: String
)
