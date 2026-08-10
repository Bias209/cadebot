# Payment Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the Cadebot Android app's fake payment flow (`MockOrderService`, static QR placeholder) with a real integration against the already-written `payment_server.py` VietQR/Sepay backend.

**Architecture:** App creates an order via `POST /api/orders/create` (server computes the authoritative price — client never sends price), shows the server-returned QR image and polls `GET /api/orders/{code}/status` every 2s until `PAID`/`CANCELLED`, then navigates to the existing `OrderSuccessScreen` with the real server-issued order code. `payment_server.py` itself is not modified — it already has a passing test suite and is deployed as-is, run locally with a `cloudflared` tunnel exposing only the webhook path to Sepay.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt DI, OkHttp + `org.json` (matches the existing `CadebotApiService.kt` pattern — no Retrofit), JUnit4 + OkHttp MockWebServer for the new networking tests, kotlinx.coroutines (`StateFlow`, polling loop).

## Global Constraints

- Package name stays `com.baxailab.cadebot` — no manifest/package changes in this plan.
- `payment_server.py`, `schema.sql`, and `.env` in `knowledge_Base_cadebot/` are **not modified** — they are already correct and tested (`test_payment_flow.py` passes). `ALLOW_MOCK_PAY=0` in `.env` must stay `0` — do not flip it to test.
- No new runtime dependencies beyond `okhttp3:mockwebserver` (test-only, same version as the existing `okhttp` dependency) — follow the existing hand-rolled OkHttp + `org.json` style, not Retrofit.
- The app's traffic to `payment_server.py` stays plain HTTP over LAN (manifest already has `usesCleartextTraffic="true"`) — do not route the app itself through the public cloudflared tunnel.
- Design doc: `docs/superpowers/specs/2026-08-06-payment-integration-and-icon-fixes-design.md` — read it for full rationale if anything below is ambiguous.

---

### Task 1: Fix the `CartItem.unitPrice` size-surcharge bug

The customization screen (`DetailViewModel.DetailUiState.unitPrice`) already correctly adds a size surcharge (`SIZE_PRICE_DELTA`: S=-5000, M=0, L=+10000) on top of the topping price. Once an item is added to the cart, `CartItem.unitPrice` (in `OrderModels.kt`) recomputes price from scratch and forgets the size surcharge — only topping price is added. This is exactly the bug `payment_server.py`'s test suite documents server-side (`test_bug_goc_latte_L_them_oat_milk`): detail screen shows one total, cart/payment shows a smaller one. Fix: move `SIZE_PRICE_DELTA` into the data layer (`OrderModels.kt`) so both places share one constant, and make `CartItem.unitPrice` use it.

**Files:**
- Modify: `app/src/main/java/com/baxailab/cadebot/data/model/OrderModels.kt`
- Modify: `app/src/main/java/com/baxailab/cadebot/ui/detail/DetailViewModel.kt:14-15,29-34` (remove the duplicate constant, import the shared one)
- Test: `app/src/test/java/com/baxailab/cadebot/data/model/OrderModelsTest.kt` (new)

**Interfaces:**
- Produces: `com.baxailab.cadebot.data.model.SIZE_PRICE_DELTA: Map<String, Int>` (moved from `DetailViewModel.kt`, same values: `"S" to -5000, "M" to 0, "L" to 10000`), `CartItem.unitPrice: Int` (now includes size surcharge)

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/baxailab/cadebot/data/model/OrderModelsTest.kt`:

```kotlin
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.baxailab.cadebot.data.model.OrderModelsTest"`
Expected: FAIL — `unitPrice adds size surcharge for size L` fails with `expected:<70000> but was:<60000>` (size surcharge missing today).

- [ ] **Step 3: Fix `OrderModels.kt`**

In `app/src/main/java/com/baxailab/cadebot/data/model/OrderModels.kt`, add the shared constant and fix `unitPrice`:

```kotlin
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
```

(Leave the rest of the file — `Order`, `OrderStatus`, `TableInfo` — unchanged for this step; Task 2 adds more to this file.)

- [ ] **Step 4: Update `DetailViewModel.kt` to use the shared constant**

In `app/src/main/java/com/baxailab/cadebot/ui/detail/DetailViewModel.kt`, replace lines 14-15:

```kotlin
// Price delta per size relative to base (M)
val SIZE_PRICE_DELTA = mapOf("S" to -5000, "M" to 0, "L" to 10000)
```

with an import instead — add to the import block at the top of the file:

```kotlin
import com.baxailab.cadebot.data.model.SIZE_PRICE_DELTA
```

The `unitPrice` getter in `DetailUiState` (lines 29-34) stays exactly as-is; it already references `SIZE_PRICE_DELTA` by name, which now resolves to the imported one.

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.baxailab.cadebot.data.model.OrderModelsTest"`
Expected: PASS (3 tests).

- [ ] **Step 6: Verify the whole module still compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (confirms `DetailViewModel.kt`'s import fix didn't break anything).

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/baxailab/cadebot/data/model/OrderModels.kt \
        app/src/main/java/com/baxailab/cadebot/ui/detail/DetailViewModel.kt \
        app/src/test/java/com/baxailab/cadebot/data/model/OrderModelsTest.kt
git commit -m "fix: CartItem.unitPrice now includes the size surcharge"
```

---

### Task 2: `PaymentApiService` — real HTTP client for `payment_server.py`

**Files:**
- Modify: `gradle/libs.versions.toml` (add `mockwebserver` test library entry)
- Modify: `app/build.gradle.kts` (add `testImplementation(libs.mockwebserver)`)
- Modify: `app/src/main/java/com/baxailab/cadebot/data/model/OrderModels.kt` (add response models)
- Create: `app/src/main/java/com/baxailab/cadebot/data/remote/PaymentApiService.kt`
- Test: `app/src/test/java/com/baxailab/cadebot/data/remote/PaymentApiServiceTest.kt` (new)

**Interfaces:**
- Consumes: `CartItem` (from Task 1's `OrderModels.kt`) — `menuItem.menuItemId`, `quantity`, `selectedSize`, `selectedSweetness`, `selectedIce`, `selectedTemperature`, `selectedToppings`, `note`
- Produces: `OrderCreateResult(orderCode: String, tableId: String, totalAmount: Int, status: String, qrUrl: String, transferContent: String, expiresAt: String, secondsRemaining: Int)`, `OrderStatusResult(orderCode: String, status: String, totalAmount: Int, paidAmount: Int?, paidAt: String?, secondsRemaining: Int, qrUrl: String)`, `class PaymentApiService(baseUrl: String) { suspend fun createOrder(tableId: String, items: List<CartItem>): Result<OrderCreateResult>; suspend fun getOrderStatus(orderCode: String): Result<OrderStatusResult> }`

- [ ] **Step 1: Add the MockWebServer test dependency**

In `gradle/libs.versions.toml`, under `[libraries]` (near the existing `okhttp` entry), add:

```toml
mockwebserver = { group = "com.squareup.okhttp3", name = "mockwebserver", version.ref = "okhttp" }
```

In `app/build.gradle.kts`, in the `dependencies { }` block, add next to the other `testImplementation` lines:

```kotlin
testImplementation(libs.mockwebserver)
```

- [ ] **Step 2: Write the failing tests**

Create `app/src/test/java/com/baxailab/cadebot/data/remote/PaymentApiServiceTest.kt`:

```kotlin
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
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.baxailab.cadebot.data.remote.PaymentApiServiceTest"`
Expected: FAIL to compile — `PaymentApiService`, `OrderCreateResult`, `OrderStatusResult` don't exist yet.

- [ ] **Step 4: Add the response models to `OrderModels.kt`**

Append to `app/src/main/java/com/baxailab/cadebot/data/model/OrderModels.kt`:

```kotlin
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
```

- [ ] **Step 5: Create `PaymentApiService.kt`**

Create `app/src/main/java/com/baxailab/cadebot/data/remote/PaymentApiService.kt`:

```kotlin
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
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.baxailab.cadebot.data.remote.PaymentApiServiceTest"`
Expected: PASS (5 tests).

- [ ] **Step 7: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts \
        app/src/main/java/com/baxailab/cadebot/data/model/OrderModels.kt \
        app/src/main/java/com/baxailab/cadebot/data/remote/PaymentApiService.kt \
        app/src/test/java/com/baxailab/cadebot/data/remote/PaymentApiServiceTest.kt
git commit -m "feat: add PaymentApiService for payment_server.py integration"
```

---

### Task 3: Build config — `PAYMENT_API_URL`

**Files:**
- Modify: `local.properties`
- Modify: `app/build.gradle.kts:16-32` (`defaultConfig` block)

**Interfaces:**
- Produces: `BuildConfig.PAYMENT_API_URL: String`

- [ ] **Step 1: Confirm this machine's LAN IP**

Run: `ip route get 1.1.1.1`
Expected: a line like `1.1.1.1 via 172.31.100.1 dev wlp0s20f3 src 172.31.100.185` — note the `src` IP. Use this IP below (it was `172.31.100.185` at design time; re-confirm since it can change between WiFi sessions).

- [ ] **Step 2: Add the property to `local.properties`**

Add a line to `local.properties`:

```properties
payment.api.url=http://172.31.100.185:8080
```

(substitute the IP confirmed in Step 1)

- [ ] **Step 3: Add the build config field**

In `app/build.gradle.kts`, inside `defaultConfig { }`, next to the existing `buildConfigField("String", "CADEBOT_API_URL", ...)` line, add:

```kotlin
buildConfigField("String", "PAYMENT_API_URL", "\"${localProps.getProperty("payment.api.url", "http://localhost:8080")}\"")
```

- [ ] **Step 4: Verify it compiles and the value is wired through**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

Run: `grep -A1 "PAYMENT_API_URL" app/build/generated/source/buildConfig/debug/com/baxailab/cadebot/BuildConfig.java`
Expected: shows `public static final String PAYMENT_API_URL = "http://172.31.100.185:8080";` (or whatever IP was set).

- [ ] **Step 5: Commit**

```bash
git add local.properties app/build.gradle.kts
git commit -m "config: add PAYMENT_API_URL build config field"
```

Note: `local.properties` is typically gitignored (machine-specific SDK path already lives there) — check `git status` after `git add`; if it's ignored, skip it in the commit and just leave the local file in place.

---

### Task 4: DI wiring — swap `MockOrderService` for `PaymentApiService`

**Files:**
- Modify: `app/src/main/java/com/baxailab/cadebot/di/AppModule.kt`
- Delete: `app/src/main/java/com/baxailab/cadebot/data/mock/MockOrderService.kt`

**Interfaces:**
- Consumes: `PaymentApiService(baseUrl: String)` (Task 2), `BuildConfig.PAYMENT_API_URL` (Task 3)
- Produces: a Hilt-injectable singleton `PaymentApiService` (available to `CartViewModel` in Task 5)

- [ ] **Step 1: Update `AppModule.kt`**

Replace the whole file `app/src/main/java/com/baxailab/cadebot/di/AppModule.kt`:

```kotlin
package com.baxailab.cadebot.di

import android.content.Context
import com.baxailab.cadebot.BuildConfig
import com.baxailab.cadebot.data.mock.MockMenuService
import com.baxailab.cadebot.data.remote.CadebotApiService
import com.baxailab.cadebot.data.remote.PaymentApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideMenuService(@ApplicationContext ctx: Context) = MockMenuService(ctx)

    @Provides
    @Singleton
    fun provideCadebotApiService(): CadebotApiService =
        CadebotApiService(BuildConfig.CADEBOT_API_URL)

    @Provides
    @Singleton
    fun providePaymentApiService(): PaymentApiService =
        PaymentApiService(BuildConfig.PAYMENT_API_URL)
}
```

- [ ] **Step 2: Delete the now-unused mock service**

Run: `rm app/src/main/java/com/baxailab/cadebot/data/mock/MockOrderService.kt`

(Confirmed via prior grep that only `AppModule.kt` and `CartViewModel.kt` reference it — Task 5 removes the `CartViewModel.kt` reference.)

- [ ] **Step 3: Verify — this will fail to compile until Task 5, that's expected**

Run: `./gradlew :app:compileDebugKotlin`
Expected: FAILS with `CartViewModel.kt` still referencing `MockOrderService` — this is expected at this point; Task 5 fixes it. Do not be alarmed; proceed to Task 5 before the final compile check.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/baxailab/cadebot/di/AppModule.kt
git rm app/src/main/java/com/baxailab/cadebot/data/mock/MockOrderService.kt
git commit -m "refactor: wire PaymentApiService into DI, drop MockOrderService"
```

---

### Task 5: `CartViewModel` — real checkout + status polling

**Files:**
- Modify: `app/src/main/java/com/baxailab/cadebot/ui/cart/CartViewModel.kt` (full rewrite of the file)

**Interfaces:**
- Consumes: `PaymentApiService.createOrder(tableId, items): Result<OrderCreateResult>`, `PaymentApiService.getOrderStatus(orderCode): Result<OrderStatusResult>` (Task 2)
- Produces: `enum class PaymentStatus { IDLE, CREATING, AWAITING_PAYMENT, PAID, EXPIRED, ERROR }`, `CartUiState(items, tables, selectedTableId, paymentStatus, orderCode: String, qrUrl: String, transferContent: String, secondsRemaining: Int, errorMessage: String)` with computed `totalAmount: Int` and `isEmpty: Boolean` — consumed by `PaymentScreen`/`NavGraph` in Task 6-7

No unit test for this task: it's a coroutine/`StateFlow` state machine wired directly to a concrete `PaymentApiService` (matching this codebase's existing pattern — `AiViewModel` also has no unit tests and is wired directly to `CadebotApiService`, not through an interface). Introducing a fakeable interface here solely for this one ViewModel would be inconsistent with the rest of the app's architecture. Verification is compile-check now, and real end-to-end device testing in Task 8.

- [ ] **Step 1: Replace `CartViewModel.kt`**

Replace the whole file `app/src/main/java/com/baxailab/cadebot/ui/cart/CartViewModel.kt`:

```kotlin
package com.baxailab.cadebot.ui.cart

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baxailab.cadebot.data.mock.MockMenuService
import com.baxailab.cadebot.data.model.CartItem
import com.baxailab.cadebot.data.model.TableInfo
import com.baxailab.cadebot.data.remote.PaymentApiService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class PaymentStatus { IDLE, CREATING, AWAITING_PAYMENT, PAID, EXPIRED, ERROR }

data class CartUiState(
    val items: List<CartItem> = emptyList(),
    val tables: List<TableInfo> = emptyList(),
    val selectedTableId: String = "",
    val paymentStatus: PaymentStatus = PaymentStatus.IDLE,
    val orderCode: String = "",
    val qrUrl: String = "",
    val transferContent: String = "",
    val secondsRemaining: Int = 0,
    val errorMessage: String = ""
) {
    val totalAmount: Int get() = items.sumOf { it.totalPrice }
    val isEmpty: Boolean get() = items.isEmpty()
}

@HiltViewModel
class CartViewModel @Inject constructor(
    private val paymentApiService: PaymentApiService,
    private val menuService: MockMenuService
) : ViewModel() {

    private val _uiState = MutableStateFlow(CartUiState())
    val uiState: StateFlow<CartUiState> = _uiState.asStateFlow()

    private var pollingJob: Job? = null

    init {
        val tables = menuService.getTables()
        _uiState.value = _uiState.value.copy(
            tables = tables,
            selectedTableId = tables.firstOrNull()?.tableId ?: ""
        )
    }

    fun addItem(item: CartItem) {
        _uiState.value = _uiState.value.copy(items = _uiState.value.items + item)
    }

    fun removeItem(itemId: String) {
        _uiState.value = _uiState.value.copy(items = _uiState.value.items.filter { it.id != itemId })
    }

    fun selectTable(tableId: String) {
        _uiState.value = _uiState.value.copy(selectedTableId = tableId)
    }

    fun checkout() {
        pollingJob?.cancel()
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(paymentStatus = PaymentStatus.CREATING, errorMessage = "")
            val result = paymentApiService.createOrder(_uiState.value.selectedTableId, _uiState.value.items)
            result.fold(
                onSuccess = { order ->
                    _uiState.value = _uiState.value.copy(
                        paymentStatus = PaymentStatus.AWAITING_PAYMENT,
                        orderCode = order.orderCode,
                        qrUrl = order.qrUrl,
                        transferContent = order.transferContent,
                        secondsRemaining = order.secondsRemaining
                    )
                    startPolling(order.orderCode)
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        paymentStatus = PaymentStatus.ERROR,
                        errorMessage = e.message ?: "Không kết nối được server thanh toán"
                    )
                }
            )
        }
    }

    private fun startPolling(orderCode: String) {
        pollingJob = viewModelScope.launch {
            var consecutiveFailures = 0
            while (isActive) {
                delay(2000)
                val result = paymentApiService.getOrderStatus(orderCode)
                result.fold(
                    onSuccess = { status ->
                        consecutiveFailures = 0
                        when (status.status) {
                            "PAID" -> {
                                _uiState.value = _uiState.value.copy(
                                    paymentStatus = PaymentStatus.PAID,
                                    secondsRemaining = status.secondsRemaining
                                )
                                return@launch
                            }
                            "CANCELLED" -> {
                                _uiState.value = _uiState.value.copy(paymentStatus = PaymentStatus.EXPIRED)
                                return@launch
                            }
                            else -> {
                                _uiState.value = _uiState.value.copy(secondsRemaining = status.secondsRemaining)
                            }
                        }
                    },
                    onFailure = {
                        consecutiveFailures++
                        if (consecutiveFailures >= 3) {
                            _uiState.value = _uiState.value.copy(
                                paymentStatus = PaymentStatus.ERROR,
                                errorMessage = "Mất kết nối tới server thanh toán"
                            )
                            return@launch
                        }
                    }
                )
            }
        }
    }

    fun clearCart() {
        pollingJob?.cancel()
        _uiState.value = CartUiState(
            tables = _uiState.value.tables,
            selectedTableId = _uiState.value.selectedTableId
        )
    }

    override fun onCleared() {
        pollingJob?.cancel()
        super.onCleared()
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: FAILS — `NavGraph.kt`, `PaymentScreen.kt`, `OrderSuccessScreen.kt` call sites still reference the old `CartUiState`/`PaymentScreen` shape. This is expected; Tasks 6-7 fix the remaining call sites. Do not be alarmed.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/baxailab/cadebot/ui/cart/CartViewModel.kt
git commit -m "feat: CartViewModel creates real orders and polls payment status"
```

---

### Task 6: `PaymentScreen` — real QR, countdown, status-driven UI

**Files:**
- Modify: `app/src/main/java/com/baxailab/cadebot/ui/payment/PaymentScreen.kt` (full rewrite)

**Interfaces:**
- Consumes: `PaymentStatus` enum (Task 5), Coil's `AsyncImage` (already a dependency: `libs.coil.compose`)
- Produces: `PaymentScreen(totalAmount: Int, qrUrl: String, transferContent: String, secondsRemaining: Int, paymentStatus: PaymentStatus, errorMessage: String, onRetry: () -> Unit, onSuccess: () -> Unit)` — consumed by `NavGraph.kt` in Task 7

- [ ] **Step 1: Replace `PaymentScreen.kt`**

Replace the whole file `app/src/main/java/com/baxailab/cadebot/ui/payment/PaymentScreen.kt`:

```kotlin
package com.baxailab.cadebot.ui.payment

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.baxailab.cadebot.ui.cart.PaymentStatus
import com.baxailab.cadebot.ui.components.VivaPrimaryButton
import com.baxailab.cadebot.ui.theme.*
import kotlinx.coroutines.delay

private fun formatCountdown(seconds: Int): String {
    val s = seconds.coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

@Composable
fun PaymentScreen(
    totalAmount: Int,
    qrUrl: String,
    transferContent: String,
    secondsRemaining: Int,
    paymentStatus: PaymentStatus,
    errorMessage: String,
    onRetry: () -> Unit,
    onSuccess: () -> Unit
) {
    LaunchedEffect(paymentStatus) {
        if (paymentStatus == PaymentStatus.PAID) {
            delay(500)
            onSuccess()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VivaFoam),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(VivaEspresso, VivaCoffee)))
                .statusBarsPadding()
                .padding(vertical = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("Thanh toán", style = MaterialTheme.typography.headlineSmall, color = VivaOnDark)
        }

        Spacer(Modifier.height(32.dp))

        Text("Tổng thanh toán", style = MaterialTheme.typography.titleMedium, color = VivaGray)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "${String.format("%,d", totalAmount)}đ",
            style = MaterialTheme.typography.displayMedium,
            color = VivaEspresso
        )

        Spacer(Modifier.height(32.dp))

        when (paymentStatus) {
            PaymentStatus.CREATING, PaymentStatus.IDLE -> {
                CircularProgressIndicator(color = VivaEspresso, modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(12.dp))
                Text("Đang tạo đơn hàng...", style = MaterialTheme.typography.bodyMedium, color = VivaGray)
            }

            PaymentStatus.AWAITING_PAYMENT, PaymentStatus.PAID -> {
                Box(
                    modifier = Modifier
                        .size(220.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(VivaSurface),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = qrUrl,
                        contentDescription = "Mã QR thanh toán",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(12.dp)
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = transferContent,
                    style = MaterialTheme.typography.bodyMedium,
                    color = VivaCoffee,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Quét mã QR bằng app ngân hàng bất kỳ để thanh toán\nMã hết hạn sau ${formatCountdown(secondsRemaining)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = VivaGray,
                    textAlign = TextAlign.Center
                )
            }

            PaymentStatus.EXPIRED -> {
                Text(
                    text = "Mã QR đã hết hạn",
                    style = MaterialTheme.typography.titleMedium,
                    color = VivaError,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                VivaPrimaryButton(
                    text = "Tạo lại mã QR",
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
                )
            }

            PaymentStatus.ERROR -> {
                Text(
                    text = errorMessage.ifBlank { "Có lỗi xảy ra, vui lòng thử lại" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = VivaError,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                VivaPrimaryButton(
                    text = "Thử lại",
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
                )
            }
        }

        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(24.dp).navigationBarsPadding())
    }
}
```

Note: `RoundedCornerShape` is used but not imported in the snippet above for brevity — add `import androidx.compose.foundation.shape.RoundedCornerShape` to the import block (it was already imported in the old version of this file).

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: FAILS — `NavGraph.kt` still calls the old `PaymentScreen(totalAmount, orderId, isLoading, onConfirmPayment, onSuccess)` signature. Expected; Task 7 fixes it.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/baxailab/cadebot/ui/payment/PaymentScreen.kt
git commit -m "feat: PaymentScreen shows a real QR code and live countdown"
```

---

### Task 7: `NavGraph` wiring — trigger checkout, pass new params, use real order code

**Files:**
- Modify: `app/src/main/java/com/baxailab/cadebot/ui/navigation/NavGraph.kt:74-97`
- Modify: `app/src/main/java/com/baxailab/cadebot/ui/cart/CartScreen.kt` (checkout-bar loading check)

**Interfaces:**
- Consumes: `CartUiState` (Task 5), `PaymentScreen(...)` (Task 6)

- [ ] **Step 1: Update the `CART` and `PAYMENT` composables**

In `app/src/main/java/com/baxailab/cadebot/ui/navigation/NavGraph.kt`, replace lines 74-97 (the `composable(Routes.CART) { ... }` and `composable(Routes.PAYMENT) { ... }` blocks):

```kotlin
        composable(Routes.CART) {
            CartScreen(
                uiState = cartUiState,
                onBack = { navController.popBackStack() },
                onCheckout = {
                    cartViewModel.checkout()
                    navController.navigate(Routes.PAYMENT)
                },
                onRemoveItem = { id -> cartViewModel.removeItem(id) },
                onSelectTable = { tableId -> cartViewModel.selectTable(tableId) },
                onContinueShopping = { navController.navigate(Routes.MENU) }
            )
        }

        composable(Routes.PAYMENT) {
            PaymentScreen(
                totalAmount = cartUiState.totalAmount,
                qrUrl = cartUiState.qrUrl,
                transferContent = cartUiState.transferContent,
                secondsRemaining = cartUiState.secondsRemaining,
                paymentStatus = cartUiState.paymentStatus,
                errorMessage = cartUiState.errorMessage,
                onRetry = { cartViewModel.checkout() },
                onSuccess = {
                    navController.navigate(Routes.ORDER_SUCCESS) {
                        popUpTo(Routes.CART) { inclusive = true }
                    }
                }
            )
        }
```

- [ ] **Step 2: Update the `ORDER_SUCCESS` composable to use the real order code**

In the same file, in the `composable(Routes.ORDER_SUCCESS) { ... }` block, change:

```kotlin
                orderId = cartUiState.placedOrderId,
```

to:

```kotlin
                orderId = cartUiState.orderCode,
```

- [ ] **Step 3: Fix `CartScreen.kt`'s checkout-bar loading check**

`CartScreen.kt`'s checkout bar currently checks `uiState.isPaymentLoading`, a property that existed on the old `CartUiState` but was not carried over in Task 5's rewrite (the new state uses `paymentStatus` instead). In `app/src/main/java/com/baxailab/cadebot/ui/cart/CartScreen.kt`, in the "Checkout bar" section, change:

```kotlin
                    if (uiState.isPaymentLoading) {
```

to:

```kotlin
                    if (uiState.paymentStatus == PaymentStatus.CREATING) {
```

No new import needed — `PaymentStatus` is declared in `CartViewModel.kt`, in the same `com.baxailab.cadebot.ui.cart` package as `CartScreen.kt`.

- [ ] **Step 4: Verify the whole app compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. This is the first fully-green compile since Task 4 — Tasks 4 through 7 are one coherent change across files; if this step fails, check every call site touched in Tasks 4-7 for a mismatched name before moving on.

- [ ] **Step 5: Run the full unit test suite**

Run: `./gradlew testDebugUnitTest`
Expected: PASS — all tests from Task 1 and Task 2, plus the pre-existing `ExampleUnitTest`.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/baxailab/cadebot/ui/navigation/NavGraph.kt \
        app/src/main/java/com/baxailab/cadebot/ui/cart/CartScreen.kt
git commit -m "feat: wire real checkout flow through NavGraph"
```

---

### Task 8: Deploy the backend and run the end-to-end flow

Not a code task — this runs the already-written, already-tested backend and verifies the whole chain works with real money, per the user's explicit choice to test with real bank transfers rather than mock-pay.

**Files:** none (operational steps only)

- [ ] **Step 1: Run the backend's own test suite**

```bash
cd /home/bias29/Downloads/Cadebot_UI/knowledge_Base_cadebot
.venv/bin/python -m pytest test_payment_flow.py -v
```

Expected: all tests pass (they did at design time — this just confirms nothing about this machine's environment broke them).

- [ ] **Step 2: Start `payment_server.py`**

```bash
cd /home/bias29/Downloads/Cadebot_UI/knowledge_Base_cadebot
.venv/bin/uvicorn payment_server:app --host 0.0.0.0 --port 8080
```

Run this in the background (or a separate terminal) — it needs to stay up for the rest of this task. Verify: `curl http://localhost:8080/health` returns `{"status":"ok", ...}`.

- [ ] **Step 3: Start the cloudflared tunnel for the webhook**

```bash
cloudflared tunnel --url http://localhost:8080
```

Run in the background. Capture the printed `https://<random>.trycloudflare.com` URL.

- [ ] **Step 4: Hand the tunnel URL to the user**

Tell the user: register `https://<random>.trycloudflare.com/api/payment/webhook` as the webhook endpoint in their Sepay dashboard. This step requires their login — cannot be automated here.

- [ ] **Step 5: Confirm the app's `PAYMENT_API_URL` LAN IP is still correct**

Run: `ip route get 1.1.1.1` again — if the `src` IP differs from what was written into `local.properties` in Task 3, update `local.properties` and re-run `./gradlew :app:compileDebugKotlin` before proceeding.

- [ ] **Step 6: Build and install on the Redmi Note 9S test device**

```bash
cd /home/bias29/Downloads/Cadebot_UI
./gradlew assembleDebug
adb -s 7b71edb install -r app/build/outputs/apk/debug/app-debug.apk
```

(Device serial `7b71edb`, per prior session notes — this device is specifically reserved for payment-feature testing.)

- [ ] **Step 7: Verify LAN reachability from the phone**

On the phone's browser, visit `http://172.31.100.185:8080/health` (substitute the confirmed IP) — expect the same JSON `/health` shows on the server machine. If this fails, the phone and server aren't on the same WiFi network or a firewall is blocking port 8080 — fix that before testing in-app.

- [ ] **Step 8: Manual end-to-end test**

On the phone: add an item to the cart with size L and at least one topping (to exercise Task 1's fix) → open cart, confirm the displayed total matches what the detail screen showed → tap "Thanh toán QR" → confirm a real QR image renders (not a placeholder) and the countdown ticks down → scan the QR with a real banking app and complete a real transfer for the exact displayed amount → within a few seconds of the transfer completing, confirm the app automatically navigates to `OrderSuccessScreen` showing the real `orderCode` (an `ORD...` code, not a UUID).

- [ ] **Step 9: Report results to the user**

Summarize: did the QR render, did the total match, did the webhook fire and mark the order PAID, did the app auto-navigate. If anything failed, capture `adb logcat` output around the failure and the `payment_server.py` terminal output for the same window.
