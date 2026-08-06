"""
Kiem thu luong thanh toan VietQR/Sepay.

Chay:
    .venv/bin/python -m pytest test_payment_flow.py -v

Test chay tren ban COPY tam cua demo_cafe.db nen khong dung vao DB that,
va dung TestClient nen khong can bat server truoc.
"""

from __future__ import annotations

import importlib
import json
import shutil
import sqlite3
import sys
from datetime import datetime, timedelta, timezone
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

BASE_DIR = Path(__file__).resolve().parent
REAL_DB = BASE_DIR / "demo_cafe.db"

API_KEY = "test-sepay-key-123"
AUTH = {"Authorization": f"Apikey {API_KEY}"}


@pytest.fixture()
def client(tmp_path, monkeypatch):
    """Nap lai payment_server voi DB tam va cau hinh test."""
    db = tmp_path / "test_cafe.db"
    shutil.copy(REAL_DB, db)

    monkeypatch.setenv("DB_PATH", str(db))
    monkeypatch.setenv("BANK_ID", "970407")
    monkeypatch.setenv("BANK_SHORT_NAME", "TESTBANK")
    monkeypatch.setenv("ACCOUNT_NO", "2090056868")
    monkeypatch.setenv("ACCOUNT_NAME", "TEST ACCOUNT")
    monkeypatch.setenv("SEPAY_API_KEY", API_KEY)
    monkeypatch.setenv("ALLOW_MOCK_PAY", "1")

    sys.modules.pop("payment_server", None)
    mod = importlib.import_module("payment_server")

    with TestClient(mod.app) as c:
        c.db_path = db          # tien tay cho test doc DB truc tiep
        c.module = mod
        yield c

    sys.modules.pop("payment_server", None)


def make_order(client, *, table="T01", items=None):
    body = {
        "tableId": table,
        "items": items or [{"itemCode": "VR_COMBO_A", "quantity": 1}],
    }
    r = client.post("/api/orders/create", json=body)
    assert r.status_code == 200, r.text
    return r.json()


def webhook_payload(content, amount, **over):
    payload = {
        "id": 92704,
        "gateway": "Techcombank",
        "transactionDate": "2026-08-04 14:02:37",
        "accountNumber": "2090056868",
        "content": content,
        "transferType": "in",
        "transferAmount": amount,
        "referenceCode": "FT2608040001",
    }
    payload.update(over)
    return payload


# =============================================================================
#  TAO DON & TINH GIA
# =============================================================================

def test_health(client):
    r = client.get("/health")
    assert r.status_code == 200
    body = r.json()
    assert body["status"] == "ok"
    assert body["webhookAuthConfigured"] is True


def test_gia_lay_tu_database(client):
    """Combo A trong DB gia 85.000d."""
    order = make_order(client)
    assert order["totalAmount"] == 85000
    assert order["status"] == "PENDING"
    assert order["items"][0]["name"] == "Combo Sáng Sớm"


def test_gia_client_gui_len_bi_bo_qua(client):
    """Lo hong nghiem trong nhat cua plan goc: neu tin gia tu client thi khach
    sua request la mua Combo B 110k voi gia 1k."""
    r = client.post("/api/orders/create", json={
        "tableId": "T01",
        "items": [{
            "itemCode": "VR_COMBO_B",
            "quantity": 1,
            "price": 1000,          # gia gia mao
            "unitPrice": 1000,
            "totalAmount": 1000,
        }],
    })
    assert r.status_code == 200
    assert r.json()["totalAmount"] == 110000       # gia that trong DB, khong phai 1000


def test_phu_thu_topping_khop_voi_app(client):
    """CartItem.unitPrice trong OrderModels.kt: menuItem.price + toppings.size * 5000."""
    order = make_order(client, items=[{
        "itemCode": "VR_LATTE_M",                  # 55.000d
        "quantity": 2,
        "options": {"toppings": ["extra_shot", "oat_milk"]},   # +10.000d
    }])
    assert order["items"][0]["unitPrice"] == 65000
    assert order["totalAmount"] == 130000


def test_bug_goc_latte_L_them_oat_milk(client):
    """Bug đã gặp: màn chi tiết hiện 70.000đ nhưng giỏ hàng + thanh toán ra
    60.000đ, vì CartItem.unitPrice bỏ quên phụ thu size.
    55.000 (giá size M) + 10.000 (size L) + 5.000 (oat milk) = 70.000."""
    order = make_order(client, items=[{
        "itemCode": "VR_LATTE_M",
        "quantity": 1,
        "options": {"size": "L", "toppings": ["oat_milk"]},
    }])
    assert order["totalAmount"] == 70000
    assert order["items"][0]["unitPrice"] == 70000


@pytest.mark.parametrize("size,expected", [
    ("S", 50000),    # 55.000 - 5.000
    ("M", 55000),    # giá gốc trong menu_items
    ("L", 65000),    # 55.000 + 10.000
    ("", 55000),     # không chọn size -> tính như size M
    ("l", 65000),    # chữ thường vẫn phải khớp
])
def test_phu_thu_theo_size(client, size, expected):
    order = make_order(client, items=[
        {"itemCode": "VR_LATTE_M", "quantity": 1, "options": {"size": size}}])
    assert order["totalAmount"] == expected


def test_size_va_topping_cong_don(client):
    """Size L + 3 topping, gọi 2 phần."""
    order = make_order(client, items=[{
        "itemCode": "VR_MATCHA_LATTE",     # 60.000
        "quantity": 2,
        "options": {"size": "L", "toppings": ["pearl", "jelly", "oat_milk"]},
    }])
    assert order["items"][0]["unitPrice"] == 85000     # 60.000 + 10.000 + 15.000
    assert order["totalAmount"] == 170000


def test_endpoint_pricing_khop_bang_trong_db(client):
    body = client.get("/api/pricing").json()
    assert body["sizeSurcharge"] == {"S": -5000, "M": 0, "L": 10000}
    assert body["toppingPrice"] == 5000


def test_sua_gia_trong_db_la_co_hieu_luc_ngay(client):
    """Lý do chọn lưu bảng giá trong DB: nhân viên sửa xong là dùng được luôn,
    không phải build lại app hay khởi động lại server."""
    with sqlite3.connect(client.db_path) as c:
        c.execute("UPDATE pricing_rules SET surcharge = 20000 "
                  "WHERE rule_type = 'SIZE' AND rule_key = 'L'")

    assert client.get("/api/pricing").json()["sizeSurcharge"]["L"] == 20000
    order = make_order(client, items=[
        {"itemCode": "VR_LATTE_M", "quantity": 1, "options": {"size": "L"}}])
    assert order["totalAmount"] == 75000       # 55.000 + 20.000


def test_nhieu_dong_cong_don(client):
    order = make_order(client, items=[
        {"itemCode": "VR_AMERICANO", "quantity": 2},     # 45.000 x 2
        {"itemCode": "VR_CROISSANT", "quantity": 1},     # 35.000
    ])
    assert order["totalAmount"] == 125000


def test_mon_khong_ton_tai(client):
    r = client.post("/api/orders/create", json={
        "tableId": "T01",
        "items": [{"itemCode": "VR_KHONG_CO_THAT", "quantity": 1}],
    })
    assert r.status_code == 400
    assert "khong ton tai" in r.json()["detail"].lower()


def test_mon_tam_het(client):
    with sqlite3.connect(client.db_path) as c:
        c.execute("UPDATE menu_items SET available = 0 WHERE item_code = 'VR_LATTE_M'")
    r = client.post("/api/orders/create", json={
        "tableId": "T01",
        "items": [{"itemCode": "VR_LATTE_M", "quantity": 1}],
    })
    assert r.status_code == 400
    assert "tam het" in r.json()["detail"].lower()


def test_ban_khong_hop_le(client):
    r = client.post("/api/orders/create", json={
        "tableId": "T99",
        "items": [{"itemCode": "VR_LATTE_M", "quantity": 1}],
    })
    assert r.status_code == 422


@pytest.mark.parametrize("table", ["T01", "T05", "BAR", "t01", " bar "])
def test_ban_hop_le(client, table):
    r = client.post("/api/orders/create", json={
        "tableId": table,
        "items": [{"itemCode": "VR_LATTE_M", "quantity": 1}],
    })
    assert r.status_code == 200
    assert r.json()["tableId"] == table.strip().upper()


def test_gio_hang_rong_bi_tu_choi(client):
    r = client.post("/api/orders/create", json={"tableId": "T01", "items": []})
    assert r.status_code == 422


def test_so_luong_am_bi_tu_choi(client):
    r = client.post("/api/orders/create", json={
        "tableId": "T01",
        "items": [{"itemCode": "VR_LATTE_M", "quantity": -5}],
    })
    assert r.status_code == 422


# =============================================================================
#  MA QR
# =============================================================================

def test_qr_url_dung_dinh_dang(client):
    """QR do chinh SePay tao (qr.sepay.vn), khong phai img.vietqr.io - xem ly do
    trong build_qr_url()."""
    order = make_order(client)
    url = order["qrUrl"]
    assert url.startswith("https://qr.sepay.vn/img?acc=2090056868&bank=TESTBANK")
    assert "amount=85000" in url
    assert f"CADEBOT%20{order['orderCode']}" in url


def test_noi_dung_ck_khong_vuot_gioi_han_napas(client):
    """Truong 'Purpose of Transaction' chuan NAPAS toi da 25 ky tu.
    Plan goc dung ORD<epoch 10 chu so> = 21 ky tu, sat tran."""
    order = make_order(client)
    assert order["transferContent"] == f"CADEBOT {order['orderCode']}"
    assert len(order["transferContent"]) <= 25
    assert len(order["transferContent"]) == 19


def test_ma_don_khong_trung_nhau(client):
    codes = {make_order(client)["orderCode"] for _ in range(30)}
    assert len(codes) == 30


# =============================================================================
#  WEBHOOK - XAC THUC
# =============================================================================

def test_webhook_thieu_header_bi_tu_choi(client):
    """Lo hong so 1 cua plan goc: webhook khong xac thuc thi ai cung
    curl mot phat la danh dau don da thanh toan."""
    order = make_order(client)
    r = client.post("/api/payment/webhook",
                    json=webhook_payload(f"CADEBOT {order['orderCode']}", 85000))
    assert r.status_code == 401

    st = client.get(f"/api/orders/{order['orderCode']}/status").json()
    assert st["status"] == "PENDING"      # van chua thanh toan


def test_webhook_sai_key_bi_tu_choi(client):
    order = make_order(client)
    r = client.post("/api/payment/webhook",
                    json=webhook_payload(f"CADEBOT {order['orderCode']}", 85000),
                    headers={"Authorization": "Apikey sai-be-bet"})
    assert r.status_code == 401


def test_webhook_tu_choi_het_khi_chua_cau_hinh_key(tmp_path, monkeypatch):
    """Fail closed: chua dat SEPAY_API_KEY thi khong nhan webhook nao."""
    db = tmp_path / "t.db"
    shutil.copy(REAL_DB, db)
    monkeypatch.setenv("DB_PATH", str(db))
    monkeypatch.setenv("SEPAY_API_KEY", "")
    sys.modules.pop("payment_server", None)
    mod = importlib.import_module("payment_server")

    with TestClient(mod.app) as c:
        r = c.post("/api/payment/webhook",
                   json=webhook_payload("CADEBOT ORD00000000", 85000),
                   headers={"Authorization": "Apikey "})
        assert r.status_code == 401
    sys.modules.pop("payment_server", None)


# =============================================================================
#  WEBHOOK - XU LY
# =============================================================================

def test_webhook_hop_le_chuyen_sang_paid(client):
    order = make_order(client)
    r = client.post("/api/payment/webhook", headers=AUTH,
                    json=webhook_payload(f"CADEBOT {order['orderCode']}", 85000))
    assert r.status_code == 200
    assert r.json()["status"] == "PAID"

    st = client.get(f"/api/orders/{order['orderCode']}/status").json()
    assert st["status"] == "PAID"
    assert st["paidAt"] is not None
    assert st["paidAmount"] == 85000

    with sqlite3.connect(client.db_path) as c:
        trans = c.execute(
            "SELECT payment_trans_id FROM orders WHERE order_code = ?",
            (order["orderCode"],)).fetchone()[0]
    assert trans == "FT2608040001"


def test_webhook_gui_lai_khong_ghi_de(client):
    """Sepay retry webhook la chuyen binh thuong - lan 2 khong duoc doi paid_at."""
    order = make_order(client)
    payload = webhook_payload(f"CADEBOT {order['orderCode']}", 85000)

    client.post("/api/payment/webhook", headers=AUTH, json=payload)
    lan1 = client.get(f"/api/orders/{order['orderCode']}/status").json()["paidAt"]

    r2 = client.post("/api/payment/webhook", headers=AUTH,
                     json={**payload, "referenceCode": "FT_KHAC", "id": 99999})
    assert r2.status_code == 200
    assert r2.json()["duplicate"] is True

    lan2 = client.get(f"/api/orders/{order['orderCode']}/status").json()["paidAt"]
    assert lan1 == lan2

    with sqlite3.connect(client.db_path) as c:
        trans = c.execute("SELECT payment_trans_id FROM orders WHERE order_code = ?",
                          (order["orderCode"],)).fetchone()[0]
    assert trans == "FT2608040001"      # giu ma giao dich dau tien


def test_webhook_chuyen_thieu_tien_khong_thanh_cong(client):
    """Chuyen 50k cho don 85k thi khong duoc tinh la da tra."""
    order = make_order(client)
    r = client.post("/api/payment/webhook", headers=AUTH,
                    json=webhook_payload(f"CADEBOT {order['orderCode']}", 50000))
    assert r.status_code == 200
    assert r.json()["underpaid"] is True

    st = client.get(f"/api/orders/{order['orderCode']}/status").json()
    assert st["status"] == "PENDING"
    assert st["paidAt"] is None
    assert st["paidAmount"] == 50000       # van ghi lai de doi soat


def test_webhook_chuyen_du_tien_van_ok(client):
    order = make_order(client)
    r = client.post("/api/payment/webhook", headers=AUTH,
                    json=webhook_payload(f"CADEBOT {order['orderCode']}", 100000))
    assert r.json()["status"] == "PAID"


def test_webhook_bo_qua_tien_chuyen_di(client):
    order = make_order(client)
    r = client.post("/api/payment/webhook", headers=AUTH,
                    json=webhook_payload(f"CADEBOT {order['orderCode']}", 85000,
                                         transferType="out"))
    assert r.status_code == 200
    assert "ignored" in r.json()
    st = client.get(f"/api/orders/{order['orderCode']}/status").json()
    assert st["status"] == "PENDING"


def test_webhook_giao_dich_khong_lien_quan_tra_200(client):
    """Tra loi se khien Sepay retry vo han voi moi giao dich khong phai cua quan."""
    r = client.post("/api/payment/webhook", headers=AUTH,
                    json=webhook_payload("Tra tien dien thang 8", 500000))
    assert r.status_code == 200
    assert "ignored" in r.json()


@pytest.mark.parametrize("mau_noi_dung", [
    "CADEBOT {code}",                    # dung chuan
    "CADEBOT{code}",                     # bank bo dau cach
    "cadebot {code}",                    # bank viet thuong
    "CADEBOT-{code}",                    # bank thay dau cach bang gach ngang
    "TL DD:CADEBOT {code} FT123",        # bank them tien to / hau to
    "  CADEBOT   {code}  ",              # thua khoang trang
])
def test_webhook_khop_du_bank_bien_dang_noi_dung(client, mau_noi_dung):
    """Moi ngan hang xu ly noi dung chuyen khoan mot kieu."""
    order = make_order(client)
    content = mau_noi_dung.format(code=order["orderCode"])
    r = client.post("/api/payment/webhook", headers=AUTH,
                    json=webhook_payload(content, 85000))
    assert r.status_code == 200, content
    assert r.json().get("status") == "PAID", f"khong khop noi dung: {content!r}"


def test_webhook_dung_truong_description_khi_content_rong(client):
    order = make_order(client)
    r = client.post("/api/payment/webhook", headers=AUTH,
                    json=webhook_payload("", 85000,
                                         description=f"CADEBOT {order['orderCode']}"))
    assert r.json()["status"] == "PAID"


# =============================================================================
#  TRANG THAI & HET HAN
# =============================================================================

def test_status_don_khong_ton_tai(client):
    assert client.get("/api/orders/ORDKHONGCO/status").status_code == 404


def test_don_qua_han_tu_chuyen_cancelled(client):
    order = make_order(client)
    qua_han = (datetime.now(timezone.utc) - timedelta(minutes=1)).isoformat()
    with sqlite3.connect(client.db_path) as c:
        c.execute("UPDATE orders SET expires_at = ? WHERE order_code = ?",
                  (qua_han, order["orderCode"]))

    st = client.get(f"/api/orders/{order['orderCode']}/status").json()
    assert st["status"] == "CANCELLED"
    assert st["secondsRemaining"] == 0

    with sqlite3.connect(client.db_path) as c:      # da ghi xuong DB, khong chi tinh tam
        assert c.execute("SELECT status FROM orders WHERE order_code = ?",
                         (order["orderCode"],)).fetchone()[0] == "CANCELLED"


def test_tien_vao_sau_khi_het_han_van_ghi_paid(client):
    """Tien da vao tai khoan that roi thi phai ghi nhan, de nhan vien xu ly."""
    order = make_order(client)
    qua_han = (datetime.now(timezone.utc) - timedelta(minutes=1)).isoformat()
    with sqlite3.connect(client.db_path) as c:
        c.execute("UPDATE orders SET expires_at = ? WHERE order_code = ?",
                  (qua_han, order["orderCode"]))

    r = client.post("/api/payment/webhook", headers=AUTH,
                    json=webhook_payload(f"CADEBOT {order['orderCode']}", 85000))
    assert r.json()["status"] == "PAID"


def test_dem_nguoc_giam_dan(client):
    order = make_order(client)
    assert order["secondsRemaining"] == 600
    st = client.get(f"/api/orders/{order['orderCode']}/status").json()
    assert 0 < st["secondsRemaining"] <= 600


def test_chi_tiet_don_tra_day_du_mon(client):
    order = make_order(client, items=[{
        "itemCode": "VR_LATTE_M", "quantity": 2,
        "options": {"size": "L", "sweetness": "30%", "toppings": ["pearl"]},
    }])
    detail = client.get(f"/api/orders/{order['orderCode']}").json()
    assert len(detail["items"]) == 1
    it = detail["items"][0]
    assert it["quantity"] == 2
    assert it["unitPrice"] == 70000       # 55.000 + 10.000 (size L) + 5.000 (topping)
    assert it["options"]["size"] == "L"
    assert it["options"]["toppings"] == ["pearl"]


# =============================================================================
#  MOCK PAY
# =============================================================================

def test_mock_pay_hoat_dong_khi_bat(client):
    order = make_order(client)
    r = client.post(f"/api/payment/mock-pay/{order['orderCode']}")
    assert r.status_code == 200
    assert r.json()["status"] == "PAID"
    assert client.get(
        f"/api/orders/{order['orderCode']}/status").json()["status"] == "PAID"


def test_mock_pay_bi_chan_khi_tat(tmp_path, monkeypatch):
    """Khong co guard nay thi mock-pay chinh la lo hong an do free."""
    db = tmp_path / "t.db"
    shutil.copy(REAL_DB, db)
    monkeypatch.setenv("DB_PATH", str(db))
    monkeypatch.setenv("SEPAY_API_KEY", API_KEY)
    monkeypatch.setenv("ALLOW_MOCK_PAY", "0")
    sys.modules.pop("payment_server", None)
    mod = importlib.import_module("payment_server")

    with TestClient(mod.app) as c:
        order = make_order(c)
        r = c.post(f"/api/payment/mock-pay/{order['orderCode']}")
        assert r.status_code == 403
        assert c.get(f"/api/orders/{order['orderCode']}/status"
                     ).json()["status"] == "PENDING"
    sys.modules.pop("payment_server", None)


def test_mock_pay_don_khong_ton_tai(client):
    assert client.post("/api/payment/mock-pay/ORDKHONGCO").status_code == 404


# =============================================================================
#  TOAN VEN DU LIEU
# =============================================================================

def test_khong_dung_vao_bang_knowledge_base(client):
    """Bang don hang khong duoc anh huong 3 bang KB ma demo_db_to_dify.py doc."""
    make_order(client)
    with sqlite3.connect(client.db_path) as c:
        assert c.execute("SELECT COUNT(*) FROM menu_items").fetchone()[0] == 12
        assert c.execute("SELECT COUNT(*) FROM promotions").fetchone()[0] == 3
        assert c.execute("SELECT COUNT(*) FROM faqs").fetchone()[0] == 21


def test_status_khong_hop_le_bi_chan_boi_check(client):
    order = make_order(client)
    with sqlite3.connect(client.db_path) as c, pytest.raises(sqlite3.IntegrityError):
        c.execute("UPDATE orders SET status = 'BAY_BA' WHERE order_code = ?",
                  (order["orderCode"],))


def test_options_luu_dung_json(client):
    opts = {"size": "L", "sweetness": "0%", "ice": "less_ice",
            "temperature": "hot", "toppings": ["jelly"], "note": "ít đá giùm mình"}
    order = make_order(client, items=[
        {"itemCode": "VR_LATTE_M", "quantity": 1, "options": opts}])
    with sqlite3.connect(client.db_path) as c:
        raw = c.execute("SELECT options FROM order_items WHERE order_code = ?",
                        (order["orderCode"],)).fetchone()[0]
    saved = json.loads(raw)
    assert saved["note"] == "ít đá giùm mình"     # tieng Viet co dau khong bi hong
    assert saved["toppings"] == ["jelly"]


def test_schema_sql_dung_lai_duoc_db_tu_dau(tmp_path):
    """schema.sql phai chay duoc tren SQLite - ban cu viet dialect PostgreSQL
    nen khong chay duoc tren chinh demo_cafe.db."""
    rebuilt = tmp_path / "rebuild.db"
    sql = (BASE_DIR / "schema.sql").read_text(encoding="utf-8")
    c = sqlite3.connect(rebuilt)
    c.executescript(sql)

    tables = {r[0] for r in c.execute("SELECT name FROM sqlite_master WHERE type='table'")}
    assert {"menu_items", "promotions", "faqs", "orders", "order_items"} <= tables
    assert c.execute("SELECT COUNT(*) FROM menu_items").fetchone()[0] == 12
    assert c.execute("SELECT COUNT(*) FROM faqs").fetchone()[0] == 21
    assert c.execute(
        "SELECT attributes FROM menu_items WHERE item_code='VR_LATTE_M'").fetchone()[0]
    c.close()
