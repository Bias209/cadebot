# Cadebot: VietQR payment integration + Cruzr icon fixes — design

Date: 2026-08-06
Status: approved by user, ready for implementation plan

## 1. Context

The Cadebot Android app (`com.baxailab.cadebot`) currently fakes the whole
order/payment flow: `CartViewModel.checkout()` calls `MockOrderService`,
which just delays 1.5s and always marks the order `PAID`. `PaymentScreen`
shows a static "QR Demo" placeholder box, not a real QR code.

A complete, already-written payment backend exists in
`knowledge_Base_cadebot/` (a folder inside this project) but has never been
deployed or wired to the app:

- `payment_server.py` — FastAPI service, SQLite (`demo_cafe.db`), 4
  endpoints (`/api/orders/create`, `/api/payment/webhook`,
  `/api/orders/{code}/status`, `/api/payment/mock-pay/{code}`), plus
  `/api/pricing` and `/health`.
- `schema.sql` — `orders` / `order_items` / `pricing_rules` tables, plus
  the existing `menu_items` knowledge-base table.
- `.env` — **already filled with real credentials**: OCB bank via Sepay
  (VA account `SEPNLDV35383`), real `SEPAY_API_KEY`, `ALLOW_MOCK_PAY=0`
  (real bank transfers only — confirmed with user, not to be changed).
- `test_payment_flow.py` — full pytest suite already passing against this
  server (pricing math, webhook auth, idempotency, expiry, etc).
- `.venv` — already has `fastapi`/`uvicorn` installed and importable.

Menu item codes (`VR_LATTE_M`, `VR_COMBO_A`, ...) and table IDs (`T01`-`T05`,
`BAR`) in the Android app's `assets/config/menu.json` /
`table_mapping.json` already match `schema.sql` exactly — this backend was
built hand-in-hand with this app. No ID-mapping work needed.

Separately, the user reported broken (missing-glyph) icons on the Cruzr
robot for the "Đá Xay" and "Bánh Ngọt" menu categories. A full-app emoji
audit (below) found 2 more likely-broken spots.

## 2. Scope

Two independent pieces of work, approved together, implemented in the same
session:

1. **Payment integration** — wire the Android client to the real
   `payment_server.py`, run it locally, expose the webhook publicly.
2. **Icon fixes** — replace Unicode-new emoji (missing on Android 5.1.1)
   with Material vector icons or older/safe emoji.

Explicitly out of scope: changes to `payment_server.py` itself (already
correct/tested), changes to the `/chat` and `/stt` backend
(`duybao.tdbao-brian.work`, different codebase, not in this repo), moving
order statuses beyond `PAID` (`PREPARING`/`DISPATCHED`/etc. — no endpoint
exists for these yet, staff-side tooling not built).

## 3. Payment integration

### 3.1 Architecture

```
CartScreen "Thanh toán QR"
     │
     ▼
CartViewModel.checkout()
     │  POST /api/orders/create  (tableId + items; NO price sent — server is authoritative)
     ▼
payment_server.py  (run locally, port 8080, existing .venv + .env)
     │  → { orderCode, totalAmount, qrUrl, transferContent, expiresAt, secondsRemaining, items[] }
     ▼
PaymentScreen: real QR (Coil AsyncImage) + live countdown
     │  poll GET /api/orders/{code}/status every 2s
     ▼
Customer scans QR with any bank app → real transfer
     │
     ▼
Sepay → POST /api/payment/webhook  (needs a PUBLIC url — LAN alone unreachable by Sepay's servers)
     │  server matches transfer content, marks PAID
     ▼
App polling observes status == PAID → auto-navigate to OrderSuccessScreen (real orderCode)
```

Two separate network paths, deliberately not unified:

- **App ↔ payment_server**: LAN only, plain HTTP,
  `http://172.31.100.185:8080` (this machine's current WiFi IP; confirm at
  deploy time with `ip route get 1.1.1.1`). `usesCleartextTraffic="true"`
  is already set in the manifest — no manifest change needed. Kept off the
  public tunnel deliberately, since Android 5.1's outdated TLS/CA store
  makes HTTPS a known risk on Cruzr (per prior porting notes) — LAN HTTP
  sidesteps that entirely for the app's own traffic.
- **Sepay ↔ payment_server webhook**: needs a public HTTPS URL, since
  Sepay's servers cannot reach a private LAN address. `cloudflared tunnel
  --url http://localhost:8080` will be started for this; the resulting
  URL must be registered by the user in the Sepay dashboard as the webhook
  target (only they can log in to do this — not automatable here).
  TLS here is Sepay-to-server, not Android-to-server, so it isn't subject
  to the Android 5.1 TLS-compatibility risk.

### 3.2 Files

| File | Change |
|---|---|
| `local.properties` | add `payment.api.url=http://172.31.100.185:8080` |
| `app/build.gradle.kts` | add `buildConfigField("String", "PAYMENT_API_URL", ...)` reading `payment.api.url`, default same pattern as `CADEBOT_API_URL` |
| `app/src/main/java/.../data/remote/PaymentApiService.kt` (new) | OkHttp + `org.json` (matches `CadebotApiService.kt` style — no Retrofit, no new dependency). Methods: `createOrder(tableId, items): OrderCreateResult`, `getOrderStatus(orderCode): OrderStatusResult`, `getPricing(): PricingRules`. `/api/payment/mock-pay` is intentionally **not** called by the app (it's a manual/staff test tool, and `ALLOW_MOCK_PAY=0` anyway). |
| `app/src/main/java/.../data/model/OrderModels.kt` | add response models: `OrderCreateResult` (orderCode, tableId, totalAmount, status, qrUrl, transferContent, createdAt, expiresAt, secondsRemaining, items), `OrderStatusResult` (orderCode, tableId, status, totalAmount, paidAmount, paidAt, expiresAt, secondsRemaining, qrUrl), `PricingRules` (sizeSurcharge map, toppingPrice). Fix `CartItem.unitPrice` to add the size surcharge (currently only adds topping price — this is the exact bug `test_payment_flow.py::test_bug_goc_latte_L_them_oat_milk` documents server-side); surcharge values come from a `PricingRules` fetched via `/api/pricing` at cart-open time, not hardcoded, so a DB price change doesn't need an app rebuild (matches the server's own stated design intent). |
| `app/src/main/java/.../ui/cart/CartViewModel.kt` | inject `PaymentApiService` instead of `MockOrderService`. `checkout()` calls `createOrder()`, stores `orderCode`/`qrUrl`/`transferContent`/`expiresAt` in `CartUiState`, does **not** mark paid. Add `pollPaymentStatus()`: loop `getOrderStatus()` every 2s while screen is showing payment, stop on `PAID`/`CANCELLED`/error. Fetch `/api/pricing` on init (or cart open) to correct cart totals. |
| `app/src/main/java/.../ui/cart/CartUiState` (in `CartViewModel.kt`) | add `orderCode`, `qrUrl`, `transferContent`, `secondsRemaining`, `paymentStatus` (enum: `Idle/Creating/AwaitingPayment/Paid/Expired/Error`), `errorMessage` |
| `app/src/main/java/.../ui/payment/PaymentScreen.kt` | replace the static "QR Demo" box with `AsyncImage(model = qrUrl)` (Coil, already a dependency); replace the fake "Xác nhận thanh toán (Demo)" button with a live countdown (from `secondsRemaining`) and status text; success is driven entirely by polling, no manual confirm; on expiry, show a message + a "Tạo lại mã QR" retry button that calls `checkout()` again; on network error, show `CadebotApiService`-style error text + retry |
| `app/src/main/java/.../ui/ordersuccess/OrderSuccessScreen.kt`, `ui/navigation/NavGraph.kt` | no structural change — `placedOrderId` already flows through; just now carries the server's real `orderCode` instead of a client UUID |
| `app/src/main/java/.../di/AppModule.kt` | add `providePaymentApiService()`; remove `provideOrderService()` |
| `app/src/main/java/.../data/mock/MockOrderService.kt` | delete — after the `CartViewModel` swap, nothing references it (confirmed via grep: only `CartViewModel.kt` and `AppModule.kt` use it today) |

### 3.3 Deployment steps (done by me at implementation time, not app code)

1. `knowledge_Base_cadebot/.venv/bin/uvicorn payment_server:app --host 0.0.0.0 --port 8080` (background)
2. `cloudflared tunnel --url http://localhost:8080` (background) → capture the public URL
3. Hand the user the public URL to register as the Sepay webhook target
4. Confirm this machine's LAN IP (`172.31.100.185` as of this writing, re-verify at run time) is what `PAYMENT_API_URL` points to, and that the test phone is on the same WiFi

### 3.4 Error handling

- Order creation network failure → `CartUiState.paymentStatus = Error`, show retry, matching `CadebotApiService`'s existing "Không kết nối được server..." tone
- Polling failure (transient) → keep polling, don't flip to Error on a single failed poll (avoid flapping the UI on a dropped WiFi packet); only surface Error after N consecutive failures
- Order expiry (`CANCELLED` from a lazy-expired `PENDING` order) → distinct "hết hạn" UI state with retry, not lumped into generic Error
- Table validation (`VALID_TABLES` on server) — already guaranteed to match since `table_mapping.json` is the source both sides use; a mismatch would surface as an HTTP 422 from `/api/orders/create`, shown via the generic Error path

### 3.5 Testing plan

- `payment_server.py`'s own `test_payment_flow.py` already covers the backend — run it once to confirm the deployed copy still passes (`.venv/bin/python -m pytest test_payment_flow.py -v`)
- Manual end-to-end on the Redmi Note 9S test device (already reserved for payment-feature testing per prior session): add item → cart shows size-corrected total → checkout → real QR renders → scan with a real bank app → real transfer → app auto-navigates to `OrderSuccessScreen` with the real `orderCode`
- Verify `curl http://172.31.100.185:8080/health` reachable from the phone's browser before testing in-app, to isolate network issues from app bugs
- Confirm expiry path by shortening `ORDER_TTL_MINUTES` temporarily during dev testing only (not committed to `.env`)

## 4. Icon fixes (Cruzr / API 22 emoji compatibility)

### 4.1 Root cause

Android 5.1.1 shipped Feb 2015. Its bundled emoji font only covers emoji
standardized by then (~Unicode 6.0-7.0, pre-"Emoji 1.0"). Emoji added in
later Unicode revisions render as a missing-glyph box ("tofu") on devices
that never get a font update — which describes Cruzr, an embedded/kiosk
device unlikely to receive Play Services emoji font updates.

### 4.2 Full-app audit results

Scanned every `.kt` file and `assets/**/*.json` for emoji characters,
checked each character's Unicode introduction date:

| Emoji | Codepoint | Unicode ver. (year) | Verdict |
|---|---|---|---|
| 🧊 ice cube | U+1F9CA | 11.0 (2018) | **broken** (user-confirmed) |
| 🥐 croissant | U+1F950 | 9.0 (2016) | **broken** (user-confirmed) |
| 🛒 shopping trolley | U+1F6D2 | 9.0 (2016) | likely broken |
| 🤖 robot face | U+1F916 | 8.0 (mid-2015) | likely broken (right at/after Cruzr's Android 5.1.1 ship date) |
| ☕🍵🎁🔥❄️♨️⭐✅🔔📍→ | various | ≤ 7.0 (≤2014) | safe, unchanged |

### 4.3 Fixes

**Standalone icon slots → Material vector icons** (already a dependency,
`libs.compose.material.icons.extended`; verified present in the resolved
jar: `AcUnit`, `BakeryDining`, `SmartToy`, `ShoppingCart`, `LocalCafe`,
`EmojiFoodBeverage`, `CardGiftcard`):

| Location | Old | New |
|---|---|---|
| `categoryEmoji()` — duplicated in `DetailScreen.kt` (public fn) and `MenuScreen.kt` (private fn), used as the category badge on item cards / detail header | returns `String` (☕🍵🧊🥐🎁), rendered via `Text()` | change return type to `ImageVector`, rendered via `Icon()`; all 5 branches unified for consistency (not just the 2 broken ones, since the function's return type can't cleanly mix `String`/`ImageVector` and all 5 already sit in the same visual slot): `LocalCafe` / `EmojiFoodBeverage` / `AcUnit` / `BakeryDining` / `CardGiftcard` |
| `CartScreen.kt` empty-cart big icon | 🛒 (`Text`) | `Icons.Default.ShoppingCart` (`Icon`) |
| `AiScreen.kt` (x2), `OrderSuccessScreen.kt` robot avatar | 🤖 (`Text`) | `Icons.Default.SmartToy` (`Icon`) |

**Inline text glyphs → swap character only** (emoji sits inline inside a
larger text string alongside a label; restructuring into icon+text pairs
here is disproportionate for what these are — small filter chips / option
tags):

| Location | Old | New | Why |
|---|---|---|---|
| `menu.json` `iconEmoji` for `ice_blended`, `pastry` (used in `MenuScreen.kt`'s category filter chips, `"${iconEmoji} ${name}"`) | 🧊, 🥐 | 🍧 (shaved ice), 🍰 (shortcake) | Both Unicode 6.0 (2010), safe; visually still on-theme |
| `DetailScreen.kt` `tempLabel()`, "iced" branch | 🧊 Lạnh | ❄️ Lạnh | Reuses the exact glyph the same function already uses one branch away for "cold" — proven safe, zero new characters introduced |

### 4.4 Testing plan

- Visual check on a normal phone (emulator or Redmi) first — icons should
  look reasonable, not just "not broken"
- Visual check on Cruzr itself once reachable — this is the only way to
  confirm the missing-glyph issue is actually gone, per the standing
  caveat that Compose rendering on API 22 is under-exercised and surprises
  can only be found on the robot
