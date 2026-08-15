# Cadebot — Payment Backend

Backend xử lý thanh toán cho app Cadebot (branch Android app: [`frontend`](../../tree/frontend)). Tạo đơn, sinh QR **VietQR**, nhận webhook xác nhận chuyển khoản từ **Sepay**, và cho app poll trạng thái đơn.

## Luồng thanh toán

```
App          POST /api/orders/create           -> server trả về qrUrl (ảnh VietQR động) + mã đơn
Khách        quét QR bằng app ngân hàng bất kỳ, chuyển khoản nội dung "CADEBOT <mã đơn>"
Sepay        POST /api/payment/webhook          -> server xác thực rồi chuyển đơn sang PAID
App          GET  /api/orders/{code}/status     (poll mỗi 2s) -> thấy PAID thì chuyển màn hình
```

Server luôn tự tính lại tổng tiền ở `/api/orders/create` (không tin giá app gửi lên) — `/api/pricing` chỉ để app hiển thị phụ thu size/topping trước khi tạo đơn.

## File chính

- **`payment_server.py`** — FastAPI app, toàn bộ logic tạo đơn/QR/webhook/polling, đọc-viết SQLite (`demo_cafe.db`, tạo tự động theo `schema.sql`).
- **`schema.sql`** — schema SQLite (đơn hàng, món, phụ thu size/topping).
- **`test_payment_flow.py`** — test pytest cho luồng tạo đơn → mock-pay → status.
- **`.env.example`** — mẫu cấu hình, copy thành `.env` (không commit `.env` thật).
- **`payment_controller.py`** — code Node.js/Express tham khảo từ một dự án khác (models `vehicleModel`, `voucherModel`...), **không được server FastAPI gọi tới** — giữ lại để đối chiếu ý tưởng QR/Sepay, không phải phần đang chạy thật.

## Chạy local

```bash
python3 -m venv .venv
.venv/bin/pip install -r requirements.txt
cp .env.example .env      # rồi điền BANK_ID, ACCOUNT_NO, SEPAY_API_KEY thật
.venv/bin/uvicorn payment_server:app --host 0.0.0.0 --port 8080
```

App Android trỏ tới server này qua `payment.api.url` trong `local.properties` (xem README branch `frontend`).

## Cấu hình quan trọng (`.env`)

| Biến | Ý nghĩa |
|---|---|
| `BANK_ID`, `ACCOUNT_NO`, `ACCOUNT_NAME` | Tài khoản nhận tiền, dùng để sinh QR VietQR |
| `SEPAY_API_KEY` | Sepay gửi kèm header `Authorization: Apikey <key>` khi gọi webhook — để trống thì server **từ chối mọi webhook** (fail closed) |
| `ALLOW_MOCK_PAY` | `1` = bật `/api/payment/mock-pay/{order_code}` để test không cần chuyển tiền thật. **Phải để `0` khi chạy thật ở quán**, nếu không ai cũng tự đánh dấu đơn là đã trả được |

## API chính

| Endpoint | Mô tả |
|---|---|
| `GET /health` | Trạng thái server, số đơn đang chờ, có bật mock-pay/webhook auth không |
| `GET /api/pricing` | Bảng phụ thu size/topping hiện tại |
| `POST /api/orders/create` | Tạo đơn từ giỏ hàng, trả `qrUrl`, `transferContent`, `secondsRemaining` |
| `GET /api/orders/{order_code}/status` | App poll để biết đơn đã `PAID` chưa |
| `GET /api/orders/{order_code}` | Chi tiết đơn |
| `POST /api/payment/webhook` | Sepay gọi khi có giao dịch vào tài khoản, xác thực bằng `SEPAY_API_KEY` |
| `POST /api/payment/mock-pay/{order_code}` | Test-only, cần `ALLOW_MOCK_PAY=1` |

## Test

```bash
.venv/bin/pytest test_payment_flow.py
```
