package com.baxailab.cadebot.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class OrderModelsTest {

    private fun sampleMenuItem(price: Int) = MenuItem(
        menuItemId = "VR_LATTE_M",
        name = "Viva Latte",
        category = "coffee",
        price = price,
        description = "",
        tags = emptyList(),
        imageRes = "img_latte",
        attributes = ItemAttributes(caffeine = true, temperatureOptions = listOf("hot", "iced")),
        available = true
    )

    @Test
    fun `unitPrice adds size surcharge for size L`() {
        val item = CartItem(
            menuItem = sampleMenuItem(price = 55000),
            quantity = 1,
            selectedSize = "L",
            selectedSweetness = "50%",
            selectedIce = "normal_ice",
            selectedTemperature = "iced",
            selectedToppings = listOf("oat_milk")
        )
        // 55.000 (base) + 10.000 (size L) + 5.000 (1 topping) = 70.000
        assertEquals(70000, item.unitPrice)
    }

    @Test
    fun `unitPrice subtracts surcharge for size S and ignores unknown size`() {
        val base = sampleMenuItem(price = 55000)
        val small = CartItem(
            menuItem = base, quantity = 1, selectedSize = "S", selectedSweetness = "",
            selectedIce = "", selectedTemperature = "", selectedToppings = emptyList()
        )
        assertEquals(50000, small.unitPrice)

        val unknown = CartItem(
            menuItem = base, quantity = 1, selectedSize = "XL", selectedSweetness = "",
            selectedIce = "", selectedTemperature = "", selectedToppings = emptyList()
        )
        assertEquals(55000, unknown.unitPrice)
    }

    @Test
    fun `totalPrice multiplies unitPrice by quantity`() {
        val item = CartItem(
            menuItem = sampleMenuItem(price = 55000), quantity = 2, selectedSize = "M",
            selectedSweetness = "", selectedIce = "", selectedTemperature = "",
            selectedToppings = listOf("pearl", "jelly")
        )
        // (55.000 + 0 + 10.000) x 2 = 130.000
        assertEquals(130000, item.totalPrice)
    }
}
