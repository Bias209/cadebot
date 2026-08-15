# Cadebot

Ứng dụng gọi món + trợ lý AI cho quán cà phê, chạy trên điện thoại Android và trên robot phục vụ **Cruzr** (Android 5.1.1 / API 22). Repo gồm 2 phần: app Android (`app/`) và các thành phần backend/dữ liệu (`knowledge_Base_cadebot/`).

## Repo layout

```
app/                         Android app (Kotlin, Jetpack Compose)
knowledge_Base_cadebot/       Backend: payment server, knowledge base cho AI, dữ liệu demo
docs/                         (tài liệu bổ sung, nếu có)
```

## Frontend — app Android (`app/`)

**Package:** `com.baxailab.cadebot` · **Stack:** Kotlin, Jetpack Compose, Hilt (DI), Navigation Compose, OkHttp, Coroutines, Coil, kotlinx.serialization.

### Các màn hình chính (`ui/`)

| Màn hình | Chức năng |
|---|---|
| `home/` | Màn hình chào, điều hướng vào Gọi món / Hỏi Cadebot / Gọi nhân viên |
| `menu/` | Danh sách menu theo danh mục |
| `detail/` | Tuỳ chọn món: size, nhiệt độ (nóng/lạnh), độ ngọt, đá, topping, ghi chú — tính giá tự động |
| `cart/` | Giỏ hàng, chọn bàn, tổng tiền, checkout |
| `payment/` | Hiển thị QR VietQR, đếm ngược thời gian, poll trạng thái thanh toán |
| `ordersuccess/` | Xác nhận đặt hàng thành công |
| `ai/` | Chat với Cadebot (text + giọng nói), gợi ý món để thêm vào giỏ |
| `callstaff/` | Gọi nhân viên hỗ trợ |

### Luồng gọi món → thanh toán

`Menu → Detail (tuỳ chọn món) → Cart (chọn bàn) → Payment (quét QR) → Order Success`. `CartViewModel` được scope theo cả nav graph (`NavGraph.kt`) nên giỏ hàng giữ nguyên khi chuyển màn hình.

### Trợ lý AI bằng giọng nói

Icon mic trong `AiScreen.kt` → ghi âm bằng `MediaRecorder` (`GroqSttService.kt`) → gửi lên `{CADEBOT_API_URL}/stt` để chuyển thành text → tự động gửi câu hỏi tới `{CADEBOT_API_URL}/chat` (`CadebotApiService.kt`) → hiển thị câu trả lời + món được gợi ý (nếu có).

### Thanh toán VietQR

`PaymentApiService.kt` gọi backend `payment_server.py`:
1. `POST /api/orders/create` — tạo đơn, trả về mã QR VietQR + nội dung chuyển khoản.
2. Khách quét QR bằng app ngân hàng bất kỳ, chuyển khoản với nội dung `CADEBOT <mã đơn>`.
3. App poll `GET /api/orders/{code}/status` mỗi 2 giây để phát hiện đơn đã `PAID`.

### Cấu hình cần thiết — `local.properties`

```properties
sdk.dir=<đường dẫn Android SDK>
groq.api.key=<API key Groq cho STT>
cadebot.api.url=https://duybao.tdbao-brian.work   # mặc định nếu không set
payment.api.url=http://localhost:8080              # trỏ tới payment_server.py khi chạy local
```

File này không được commit (đã có trong `.gitignore`) — mỗi máy dev tự tạo riêng.

### Build

```bash
./gradlew assembleDebug     # ra file tại app/build/outputs/apk/debug/app-debug.apk
```

**Lưu ý về bản build cho Cruzr:**
- `minSdk`/`targetSdk` = 22 để tương thích Android 5.1.1 trên Cruzr (không phải lỗi cấu hình).
- Signing debug/release đều dùng chung `~/.android/debug.keystore` để `adb install -r` nâng cấp đè lên bản đã cài trên Cruzr mà không cần gỡ cài đặt trước.
- Mỗi lần build để đưa lên Cruzr, tăng `versionCode` trong `app/build.gradle.kts`.

## Backend (`knowledge_Base_cadebot/`)

- **`payment_server.py`** — FastAPI server xử lý tạo đơn, tạo QR VietQR qua Sepay, nhận webhook thanh toán, lưu đơn vào SQLite (`demo_cafe.db`). Chạy bằng `uvicorn payment_server:app --host 0.0.0.0 --port 8080`, cấu hình qua file `.env` cùng thư mục.
- **`cadebot_dify_bridge.py`, `ollama_docker_bridge.py`** — cầu nối giữa Dify (quản lý knowledge base/RAG) và model trả lời (Qwen2.5-7B-Instruct).
- **`demo_db_to_dify.py`** — đồng bộ dữ liệu menu/khuyến mãi từ DB demo lên knowledge base của Dify.
- **`01_*.md` → `05_*.md`** — nội dung knowledge base gốc (thương hiệu, menu, không gian, liên hệ, FAQ) dùng để build RAG.
- **`schema.sql`**, **`demo_cafe.db`** — schema và dữ liệu demo (menu, đơn hàng).
- **`cadebot-plan.md`, `implementation_plan.md`** — kế hoạch triển khai chi tiết (Dify + RAG, giai đoạn từng bước).

## Đã hoàn thành gần đây

- Port app từ chỉ chạy điện thoại (minSdk 26) sang chạy được cả trên Cruzr (minSdk 22), giữ nguyên voice pipeline.
- Tích hợp thanh toán thật qua VietQR/Sepay (`payment_server.py` ↔ `PaymentApiService.kt`): tạo đơn, hiển thị QR, poll trạng thái.
- Thay icon emoji (không hiện được trên Android 5.1) bằng Material Icons.
- Dọn UI màn hình Detail: bỏ hiện số tiền +/- trên chip Size (giá vẫn tự cộng ở `SIZE_PRICE_DELTA`, chỉ ẩn khỏi UI) và bỏ ghi chú thừa "(0% = không đá)" ở phần chọn đá.
