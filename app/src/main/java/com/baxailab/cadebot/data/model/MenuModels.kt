package com.baxailab.cadebot.data.model

import kotlinx.serialization.Serializable

@Serializable
data class MenuResponse(
    val categories: List<Category>,
    val items: List<MenuItem>
)

@Serializable
data class Category(
    val id: String,
    val name: String,
    val iconEmoji: String
)

@Serializable
data class MenuItem(
    val menuItemId: String,
    val name: String,
    val category: String,
    val price: Int,
    val description: String,
    val tags: List<String>,
    val imageRes: String,
    val attributes: ItemAttributes,
    val available: Boolean
)

@Serializable
data class ItemAttributes(
    val caffeine: Boolean,
    val temperatureOptions: List<String>,
    val defaultTemperature: String = "",
    val sweetnessOptions: List<String> = emptyList(),
    val defaultSweetness: String = "",
    val iceOptions: List<String> = emptyList(),
    val defaultIce: String = "",
    val sizeOptions: List<String> = emptyList(),
    val defaultSize: String = "",
    val toppings: List<String> = emptyList()
)
