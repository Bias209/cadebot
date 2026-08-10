package com.baxailab.cadebot.data.remote

import com.baxailab.cadebot.data.model.CartItem
import com.baxailab.cadebot.data.model.ItemAttributes
import com.baxailab.cadebot.data.model.MenuItem
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PaymentApiServiceTest {

    private lateinit var server: MockWebServer
    private lateinit var service: PaymentApiService

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        service = PaymentApiService(server.url("/").toString().removeSuffix("/"))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun sampleItems() = listOf(
        CartItem(
            menuItem = MenuItem(
                menuItemId = "VR_LATTE_M", name = "Viva Latte", category = "coffee",
                price = 55000, description = "", tags = emptyList(), imageRes = "img_latte",
                attributes = ItemAttributes(caffeine = true, temperatureOptions = listOf("iced")),
                available = true
            ),
            quantity = 1, selectedSize = "M", selectedSweetness = "50%",
            selectedIce = "normal_ice", selectedTemperature = "iced", selectedToppings = emptyList()
        )
    )

    @Test
    fun `createOrder parses a successful response`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "orderCode": "ORD7K3A9X", "tableId": "T01", "totalAmount": 55000,
                  "status": "PENDING", "qrUrl": "https://qr.sepay.vn/img?acc=123",
                  "transferContent": "CADEBOT ORD7K3A9X", "expiresAt": "2026-08-06T10:10:00+00:00",
                  "secondsRemaining": 600, "items": []
                }
                """.trimIndent()
            )
        )

        val result = service.createOrder("T01", sampleItems())

        assertTrue(result.isSuccess)
        val order = result.getOrThrow()
        assertEquals("ORD7K3A9X", order.orderCode)
        assertEquals(55000, order.totalAmount)
        assertEquals("PENDING", order.status)
        assertEquals("https://qr.sepay.vn/img?acc=123", order.qrUrl)
        assertEquals("CADEBOT ORD7K3A9X", order.transferContent)
        assertEquals(600, order.secondsRemaining)

        val request = server.takeRequest()
        assertEquals("/api/orders/create", request.path)
        assertTrue(request.body.readUtf8().contains("\"itemCode\":\"VR_LATTE_M\""))
    }

    @Test
    fun `createOrder returns failure on HTTP error`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"detail": "Mon khong ton tai"}"""))

        val result = service.createOrder("T01", sampleItems())

        assertTrue(result.isFailure)
    }

    @Test
    fun `getOrderStatus parses a pending order with null paid fields`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "orderCode": "ORD7K3A9X", "tableId": "T01", "status": "PENDING",
                  "totalAmount": 55000, "paidAmount": null, "paidAt": null,
                  "expiresAt": "2026-08-06T10:10:00+00:00", "secondsRemaining": 300,
                  "qrUrl": "https://qr.sepay.vn/img?acc=123"
                }
                """.trimIndent()
            )
        )

        val result = service.getOrderStatus("ORD7K3A9X")

        assertTrue(result.isSuccess)
        val status = result.getOrThrow()
        assertEquals("PENDING", status.status)
        assertNull(status.paidAmount)
        assertNull(status.paidAt)
        assertEquals(300, status.secondsRemaining)
    }

    @Test
    fun `getOrderStatus parses a paid order`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "orderCode": "ORD7K3A9X", "tableId": "T01", "status": "PAID",
                  "totalAmount": 55000, "paidAmount": 55000, "paidAt": "2026-08-06T10:05:00+00:00",
                  "expiresAt": "2026-08-06T10:10:00+00:00", "secondsRemaining": 0,
                  "qrUrl": "https://qr.sepay.vn/img?acc=123"
                }
                """.trimIndent()
            )
        )

        val result = service.getOrderStatus("ORD7K3A9X")

        assertTrue(result.isSuccess)
        val status = result.getOrThrow()
        assertEquals("PAID", status.status)
        assertEquals(55000, status.paidAmount)
        assertEquals("2026-08-06T10:05:00+00:00", status.paidAt)

        assertEquals("/api/orders/ORD7K3A9X/status", server.takeRequest().path)
    }

    @Test
    fun `getOrderStatus returns failure on 404`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"detail": "Khong tim thay don"}"""))

        val result = service.getOrderStatus("ORDKHONGCO")

        assertTrue(result.isFailure)
    }
}
