package com.baxailab.cadebot.data.remote

import com.baxailab.cadebot.data.model.CartItem
import com.baxailab.cadebot.data.model.OrderCreateResult
import com.baxailab.cadebot.data.model.OrderStatusResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class PaymentApiException(message: String) : Exception(message)

class PaymentApiService(
    private val baseUrl: String
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun createOrder(tableId: String, items: List<CartItem>): Result<OrderCreateResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                val itemsArr = JSONArray()
                items.forEach { cartItem ->
                    val optionsJson = JSONObject()
                        .put("size", cartItem.selectedSize)
                        .put("sweetness", cartItem.selectedSweetness)
                        .put("ice", cartItem.selectedIce)
                        .put("temperature", cartItem.selectedTemperature)
                        .put("toppings", JSONArray(cartItem.selectedToppings))
                        .put("note", cartItem.note)
                    itemsArr.put(
                        JSONObject()
                            .put("itemCode", cartItem.menuItem.menuItemId)
                            .put("quantity", cartItem.quantity)
                            .put("options", optionsJson)
                    )
                }
                val bodyJson = JSONObject()
                    .put("tableId", tableId)
                    .put("items", itemsArr)
                    .toString()
                    .toRequestBody("application/json".toMediaType())

                val request = Request.Builder()
                    .url("$baseUrl/api/orders/create")
                    .post(bodyJson)
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    throw PaymentApiException("Tạo đơn thất bại (${response.code})")
                }
                val raw = response.body?.string() ?: throw PaymentApiException("Phản hồi trống")
                val json = JSONObject(raw)
                OrderCreateResult(
                    orderCode = json.getString("orderCode"),
                    tableId = json.getString("tableId"),
                    totalAmount = json.getInt("totalAmount"),
                    status = json.getString("status"),
                    qrUrl = json.getString("qrUrl"),
                    transferContent = json.getString("transferContent"),
                    expiresAt = json.getString("expiresAt"),
                    secondsRemaining = json.getInt("secondsRemaining")
                )
            }
        }

    suspend fun getOrderStatus(orderCode: String): Result<OrderStatusResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url("$baseUrl/api/orders/$orderCode/status")
                    .get()
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    throw PaymentApiException("Không lấy được trạng thái đơn (${response.code})")
                }
                val raw = response.body?.string() ?: throw PaymentApiException("Phản hồi trống")
                val json = JSONObject(raw)
                OrderStatusResult(
                    orderCode = json.getString("orderCode"),
                    status = json.getString("status"),
                    totalAmount = json.getInt("totalAmount"),
                    paidAmount = if (json.isNull("paidAmount")) null else json.getInt("paidAmount"),
                    paidAt = if (json.isNull("paidAt")) null else json.getString("paidAt"),
                    secondsRemaining = json.getInt("secondsRemaining"),
                    qrUrl = json.getString("qrUrl")
                )
            }
        }
}
