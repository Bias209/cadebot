package com.baxailab.cadebot.data.remote

import com.baxailab.cadebot.data.model.AiMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class CadebotApiService(
    private val baseUrl: String
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .callTimeout(330, TimeUnit.SECONDS)
        .build()

    suspend fun processQuery(message: String, history: List<AiMessage>): AiMessage =
        withContext(Dispatchers.IO) {
            runCatching {
                val historyArr = JSONArray()
                history.takeLast(10).forEach { msg ->
                    historyArr.put(
                        JSONObject()
                            .put("role", if (msg.isUser) "user" else "assistant")
                            .put("content", msg.content)
                    )
                }

                val bodyJson = JSONObject()
                    .put("message", message)
                    .put("history", historyArr)
                    .toString()
                    .toRequestBody("application/json".toMediaType())

                val request = Request.Builder()
                    .url("$baseUrl/chat")
                    .post(bodyJson)
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val raw = response.body?.string() ?: throw Exception("Empty response")
                    val wrapper = JSONObject(raw)
                    parseModelOutput(wrapper.getString("response"))
                } else {
                    AiMessage(
                        content = "Cadebot đang bận, thử lại sau bạn nhé! (${response.code})",
                        isUser = false
                    )
                }
            }.getOrElse {
                AiMessage(
                    content = "Không kết nối được server. Kiểm tra WiFi và đảm bảo server đang chạy nhé!",
                    isUser = false
                )
            }
        }

    private fun parseModelOutput(raw: String): AiMessage {
        return runCatching {
            val start = raw.indexOf("{")
            val end = raw.lastIndexOf("}")
            if (start < 0 || end < 0) return AiMessage(content = raw.trim(), isUser = false)

            val json = JSONObject(raw.substring(start, end + 1))
            val answerText = json.optString("answerText").takeIf { it.isNotBlank() } ?: raw.trim()
            val spokenText = json.optString("spokenText").takeIf { it.isNotBlank() } ?: answerText
            val recommendedArr = json.optJSONArray("recommendedItems")
            val recommendedIds = mutableListOf<String>()

            recommendedArr?.let { arr ->
                for (i in 0 until arr.length()) {
                    when (val item = arr[i]) {
                        is JSONObject -> item.optString("menuItemId").takeIf { it.isNotBlank() }
                            ?.let { recommendedIds.add(it) }
                        is String -> if (item.isNotBlank()) recommendedIds.add(item)
                    }
                }
            }

            AiMessage(content = answerText, isUser = false, spokenText = spokenText, recommendedItems = recommendedIds)
        }.getOrElse {
            AiMessage(content = raw.trim(), isUser = false)
        }
    }
}
