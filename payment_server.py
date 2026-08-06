"""
Cadebot Payment Server - Thanh toan VietQR qua Sepay.

Chay:
    .venv/bin/uvicorn payment_server:app --host 0.0.0.0 --port 8080

Luong:
    App -> POST /api/orders/create      -> tra ve qrUrl (anh VietQR dong)
    Khach quet QR bang app ngan hang bat ky, chuyen tien voi noi dung "CADEBOT <ma don>"
    Sepay -> POST /api/payment/webhook  -> doi trang thai don sang PAID
    App   -> GET  /api/orders/{code}/status (polling 2s) -> thay PAID thi chuyen man hinh

Cau hinh doc tu file .env cung thu muc (xem .env.example).
"""

from __future__ import annotations

import hmac
import json
import logging
import os
import re
import secrets
import sqlite3
from contextlib import asynccontextmanager, contextmanager
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Any, Iterator
from urllib.parse import quote

from fastapi import Body, FastAPI, Header, HTTPException
from pydantic import BaseModel, Field, field_validator

BASE_DIR = Path(__file__).resolve().parent

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
)
log = logging.getLogger("payment")


# =============================================================================
#  CAU HINH
# =============================================================================

def _load_dotenv(path: Path) -> None:
    """Nap .env vao os.environ. Tu viet de khoi them dependency python-dotenv."""
    if not path.exists():
        return
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, _, val = line.partition("=")
        os.environ.setdefault(key.strip(), val.strip().strip("'\""))


_load_dotenv(BASE_DIR / ".env")

DB_PATH = Path(os.environ.get("DB_PATH") or BASE_DIR / "demo_cafe.db")
BANK_ID = os.environ.get("BANK_ID", "")
BANK_SHORT_NAME = os.environ.get("BANK_SHORT_NAME", "")  # ten ngan hang cho qr.sepay.vn, vd "OCB"
ACCOUNT_NO = os.environ.get("ACCOUNT_NO", "")
ACCOUNT_NAME = os.environ.get("ACCOUNT_NAME", "")
SEPAY_API_KEY = os.environ.get("SEPAY_API_KEY", "")
ALLOW_MOCK_PAY = os.environ.get("ALLOW_MOCK_PAY", "1") == "1"

ORDER_TTL_MINUTES = int(os.environ.get("ORDER_TTL_MINUTES", "10"))
TOPPING_PRICE = int(os.environ.get("TOPPING_PRICE", "5000"))

# Lay tu assets/config/table_mapping.json cua app Android.
# DB khong co bang tables nen chot cung o day.
VALID_TABLES = {"T01", "T02", "T03", "T04", "T05", "BAR"}

# Tien to trong noi dung chuyen khoan. Chuan NAPAS gioi han truong nay 25 ky tu,
# nen ma don phai ngan: "CADEBOT " (8) + "ORD" + 8 = 19 ky tu.
TRANSFER_PREFIX = "CADEBOT"
ADDINFO_MAX_LEN = 25
ORDER_CODE_RE = re.compile(rf"{TRANSFER_PREFIX}(ORD[0-9A-Z]{{8}})")

STATUS_PENDING = "PENDING"
STATUS_PAID = "PAID"
STATUS_CANCELLED = "CANCELLED"


# =============================================================================
#  DB LAYER
#  Toan bo SQL nam trong khoi nay. Endpoint khong viet SQL truc tiep.
#  Doi sang PostgreSQL sau nay chi can sua dung khoi nay.
# =============================================================================

_SCHEMA = """
CREATE TABLE IF NOT EXISTS orders (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    order_code       TEXT    UNIQUE NOT NULL,
    table_id         TEXT    NOT NULL,
    total_amount     INTEGER NOT NULL,
    status           TEXT    NOT NULL DEFAULT 'PENDING',
    payment_method   TEXT    NOT NULL DEFAULT 'VIETQR',
    payment_trans_id TEXT,
    paid_amount      INTEGER,
    qr_url           TEXT,
    created_at       TEXT    NOT NULL,
    expires_at       TEXT    NOT NULL,
    paid_at          TEXT,
    CHECK (status IN ('PENDING','PAID','PREPARING','READY',
                      'DISPATCHED','DELIVERED','CANCELLED'))
);
CREATE INDEX IF NOT EXISTS idx_orders_status  ON orders(status);
CREATE INDEX IF NOT EXISTS idx_orders_created ON orders(created_at);

CREATE TABLE IF NOT EXISTS order_items (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    order_code TEXT    NOT NULL REFERENCES orders(order_code) ON DELETE CASCADE,
    item_code  TEXT    NOT NULL,
    item_name  TEXT    NOT NULL,
    quantity   INTEGER NOT NULL,
    unit_price INTEGER NOT NULL,
    line_total INTEGER NOT NULL,
    options    TEXT,
    created_at TEXT    NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_order_items_code ON order_items(order_code);

CREATE TABLE IF NOT EXISTS pricing_rules (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    rule_type  TEXT    NOT NULL,
    rule_key   TEXT    NOT NULL,
    surcharge  INTEGER NOT NULL,
    note       TEXT,
    UNIQUE (rule_type, rule_key),
    CHECK (rule_type IN ('SIZE','TOPPING'))
);

INSERT OR IGNORE INTO pricing_rules (rule_type, rule_key, surcharge, note) VALUES
('SIZE',    'S',       -5000, 'Size nho, re hon size M 5.000d'),
('SIZE',    'M',           0, 'Size mac dinh - gia goc trong menu_items'),
('SIZE',    'L',       10000, 'Size lon, phu thu 10.000d'),
('TOPPING', 'default',  5000, 'Dong gia moi topping');
"""


@contextmanager
def _conn() -> Iterator[sqlite3.Connection]:
    """Mo ket noi ngan han cho moi request. Endpoint la sync def nen chay trong
    threadpool -> moi thread mot connection rieng, khong dung check_same_thread."""
    conn = sqlite3.connect(DB_PATH, timeout=10)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA foreign_keys = ON")
    try:
        yield conn
        conn.commit()
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()


def db_init() -> None:
    with _conn() as c:
        c.execute("PRAGMA journal_mode = WAL")
        c.executescript(_SCHEMA)


def db_get_menu_item(c: sqlite3.Connection, item_code: str) -> sqlite3.Row | None:
    return c.execute(
        "SELECT item_code, name, price, available FROM menu_items WHERE item_code = ?",
        (item_code,),
    ).fetchone()


def db_get_pricing(c: sqlite3.Connection) -> dict[str, Any]:
    """Doc bang gia phu thu. Nhan vien sua trong DB la co hieu luc ngay,
    khong can build lai app - app goi GET /api/pricing de dong bo."""
    rows = c.execute("SELECT rule_type, rule_key, surcharge FROM pricing_rules").fetchall()
    size = {r["rule_key"].upper(): r["surcharge"] for r in rows if r["rule_type"] == "SIZE"}
    topping = next(
        (r["surcharge"] for r in rows
         if r["rule_type"] == "TOPPING" and r["rule_key"] == "default"),
        TOPPING_PRICE,
    )
    return {"sizeSurcharge": size, "toppingPrice": topping}


def db_order_code_exists(c: sqlite3.Connection, code: str) -> bool:
    return c.execute(
        "SELECT 1 FROM orders WHERE order_code = ?", (code,)
    ).fetchone() is not None


def db_insert_order(
    c: sqlite3.Connection,
    *,
    order_code: str,
    table_id: str,
    total_amount: int,
    qr_url: str,
    created_at: str,
    expires_at: str,
    items: list[dict[str, Any]],
) -> None:
    c.execute(
        """INSERT INTO orders (order_code, table_id, total_amount, status,
                               payment_method, qr_url, created_at, expires_at)
           VALUES (?, ?, ?, 'PENDING', 'VIETQR', ?, ?, ?)""",
        (order_code, table_id, total_amount, qr_url, created_at, expires_at),
    )
    c.executemany(
        """INSERT INTO order_items (order_code, item_code, item_name, quantity,
                                    unit_price, line_total, options, created_at)
           VALUES (?, ?, ?, ?, ?, ?, ?, ?)""",
        [
            (order_code, it["itemCode"], it["name"], it["quantity"],
             it["unitPrice"], it["lineTotal"], json.dumps(it["options"],
             ensure_ascii=False), created_at)
            for it in items
        ],
    )


def db_get_order(c: sqlite3.Connection, order_code: str) -> sqlite3.Row | None:
    return c.execute(
        "SELECT * FROM orders WHERE order_code = ?", (order_code,)
    ).fetchone()


def db_get_order_items(c: sqlite3.Connection, order_code: str) -> list[sqlite3.Row]:
    return c.execute(
        """SELECT item_code, item_name, quantity, unit_price, line_total, options
           FROM order_items WHERE order_code = ? ORDER BY id""",
        (order_code,),
    ).fetchall()


def db_mark_paid(
    c: sqlite3.Connection, order_code: str, trans_id: str | None, paid_amount: int
) -> None:
    """Chi cap nhat khi don con PENDING -> webhook ban lai khong ghi de paid_at."""
    c.execute(
        """UPDATE orders
              SET status = 'PAID', paid_at = ?, payment_trans_id = ?, paid_amount = ?
            WHERE order_code = ? AND status = 'PENDING'""",
        (_now_iso(), trans_id, paid_amount, order_code),
    )


def db_record_underpaid(c: sqlite3.Connection, order_code: str, amount: int) -> None:
    c.execute(
        "UPDATE orders SET paid_amount = ? WHERE order_code = ?", (amount, order_code)
    )


def db_cancel_expired(c: sqlite3.Connection, order_code: str) -> None:
    c.execute(
        "UPDATE orders SET status = 'CANCELLED' WHERE order_code = ? AND status = 'PENDING'",
        (order_code,),
    )


def db_find_code_in_text(c: sqlite3.Connection, normalised: str) -> str | None:
    """Du phong khi regex truot: quet cac don chua thanh toan xem ma nao nam
    trong noi dung chuyen khoan. Chi soi don PENDING nen tap rat nho."""
    for row in c.execute(
        "SELECT order_code FROM orders WHERE status = 'PENDING' ORDER BY id DESC LIMIT 200"
    ):
        if row["order_code"] in normalised:
            return row["order_code"]
    return None


def db_count_pending(c: sqlite3.Connection) -> int:
    return c.execute("SELECT COUNT(*) FROM orders WHERE status = 'PENDING'").fetchone()[0]


# =============================================================================
#  TIEN ICH
# =============================================================================

_B36 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ"


def _now() -> datetime:
    return datetime.now(timezone.utc)


def _now_iso() -> str:
    return _now().isoformat()


def _parse_iso(value: str) -> datetime:
    dt = datetime.fromisoformat(value)
    return dt if dt.tzinfo else dt.replace(tzinfo=timezone.utc)


def _base36(n: int, width: int) -> str:
    out = ""
    while n:
        n, r = divmod(n, 36)
        out = _B36[r] + out
    return out.rjust(width, "0")[-width:]


def generate_order_code() -> str:
    """ORD + 6 ky tu base36 tu epoch giay + 2 ky tu ngau nhien = 11 ky tu.
    "CADEBOT " + 11 = 19 ky tu, nam duoi gioi han 25 cua NAPAS."""
    stamp = _base36(int(_now().timestamp()), 6)
    salt = "".join(secrets.choice(_B36) for _ in range(2))
    return f"ORD{stamp}{salt}"


def build_transfer_content(order_code: str) -> str:
    return f"{TRANSFER_PREFIX} {order_code}"


def build_qr_url(order_code: str, amount: int) -> str:
    add_info = build_transfer_content(order_code)
    if len(add_info) > ADDINFO_MAX_LEN:
        raise RuntimeError(
            f"Noi dung chuyen khoan {len(add_info)} ky tu, vuot gioi han "
            f"{ADDINFO_MAX_LEN} cua NAPAS: {add_info!r}"
        )
    # Dung QR do chinh SePay tao (qr.sepay.vn) thay vi img.vietqr.io.
    # Ly do: thuc te da kiem chung - giao dich qua QR ben thu ba (dung chuan
    # VietQR/NAPAS) khong duoc SePay phat hien tren tai khoan OCB da ket noi,
    # trong khi QR do chinh SePay tao thi phat hien webhook trong <1 giay.
    url = (
        f"https://qr.sepay.vn/img"
        f"?acc={ACCOUNT_NO}&bank={quote(BANK_SHORT_NAME)}"
        f"&amount={amount}&des={quote(add_info)}"
    )
    return url


def normalise_transfer_content(content: str) -> str:
    """Bo het ky tu khong phai chu/so roi viet hoa.

    Cac ngan hang xu ly noi dung chuyen khoan rat khac nhau: co ben giu nguyen,
    co ben bo dau cach, co ben them tien to kieu "TL DD:". Chuan hoa truoc khi
    do de khoi truot."""
    return re.sub(r"[^0-9A-Za-z]", "", content or "").upper()


def extract_order_code(content: str) -> str | None:
    match = ORDER_CODE_RE.search(normalise_transfer_content(content))
    return match.group(1) if match else None


def seconds_remaining(expires_at: str) -> int:
    return max(0, int((_parse_iso(expires_at) - _now()).total_seconds()))


# =============================================================================
#  MODEL
# =============================================================================

class ItemOptions(BaseModel):
    size: str = ""
    sweetness: str = ""
    ice: str = ""
    temperature: str = ""
    toppings: list[str] = Field(default_factory=list)
    note: str = ""


class OrderItemIn(BaseModel):
    """Chu y: KHONG co truong price. Gia luon tra tu bang menu_items o server,
    neu tin gia client gui len thi khach sua request la mua 110k voi gia 1k."""
    itemCode: str
    quantity: int = Field(gt=0, le=50)
    options: ItemOptions = Field(default_factory=ItemOptions)


class CreateOrderIn(BaseModel):
    tableId: str
    items: list[OrderItemIn] = Field(min_length=1, max_length=50)

    @field_validator("tableId")
    @classmethod
    def _check_table(cls, v: str) -> str:
        v = v.strip().upper()
        if v not in VALID_TABLES:
            raise ValueError(f"tableId khong hop le: {v}. Cho phep: {sorted(VALID_TABLES)}")
        return v


# =============================================================================
#  APP
# =============================================================================

@asynccontextmanager
async def lifespan(_: FastAPI):
    db_init()
    log.info("DB: %s", DB_PATH)
    log.info("Ngan hang: %s - %s (%s)", BANK_ID, ACCOUNT_NO, ACCOUNT_NAME or "chua dat ten")
    if not SEPAY_API_KEY:
        log.warning("SEPAY_API_KEY trong -> webhook se tu choi MOI request (fail closed)")
    if ALLOW_MOCK_PAY:
        log.warning("ALLOW_MOCK_PAY=1 -> endpoint mock-pay dang bat. TAT truoc khi dung that!")
    yield


app = FastAPI(title="Cadebot Payment Server", version="1.0.0", lifespan=lifespan)


@app.get("/health")
def health() -> dict[str, Any]:
    with _conn() as c:
        pending = db_count_pending(c)
    return {
        "status": "ok",
        "db": str(DB_PATH),
        "pendingOrders": pending,
        "mockPayEnabled": ALLOW_MOCK_PAY,
        "webhookAuthConfigured": bool(SEPAY_API_KEY),
    }


@app.get("/api/pricing")
def pricing() -> dict[str, Any]:
    """Bang gia phu thu size/topping cho app hien thi truoc khi tao don.

    Server van la nguon chot: tong tien cua don luon tinh lai o
    /api/orders/create, endpoint nay chi de app khoi hien sai so cho khach."""
    with _conn() as c:
        return db_get_pricing(c)


@app.post("/api/orders/create")
def create_order(payload: CreateOrderIn) -> dict[str, Any]:
    with _conn() as c:
        pricing = db_get_pricing(c)
        size_rules: dict[str, int] = pricing["sizeSurcharge"]
        topping_price: int = pricing["toppingPrice"]

        items: list[dict[str, Any]] = []
        total = 0

        for line in payload.items:
            row = db_get_menu_item(c, line.itemCode)
            if row is None:
                raise HTTPException(400, f"Mon khong ton tai: {line.itemCode}")
            if not row["available"]:
                raise HTTPException(400, f"Mon dang tam het: {row['name']}")

            # Gia trong menu_items la gia size M. Cong thuc phai khop
            # CartItem.unitPrice trong OrderModels.kt cua app.
            size_delta = size_rules.get(line.options.size.strip().upper(), 0)
            unit_price = row["price"] + size_delta + len(line.options.toppings) * topping_price
            line_total = unit_price * line.quantity
            total += line_total

            items.append({
                "itemCode": row["item_code"],
                "name": row["name"],
                "quantity": line.quantity,
                "unitPrice": unit_price,
                "lineTotal": line_total,
                "options": line.options.model_dump(),
            })

        created = _now()
        expires = created + timedelta(minutes=ORDER_TTL_MINUTES)
        created_iso, expires_iso = created.isoformat(), expires.isoformat()

        # UNIQUE tren order_code -> thu lai neu trung (cung giay + cung 2 ky tu random)
        for _ in range(5):
            order_code = generate_order_code()
            if not db_order_code_exists(c, order_code):
                break
        else:
            raise HTTPException(500, "Khong sinh duoc ma don duy nhat")

        qr_url = build_qr_url(order_code, total)
        db_insert_order(
            c,
            order_code=order_code,
            table_id=payload.tableId,
            total_amount=total,
            qr_url=qr_url,
            created_at=created_iso,
            expires_at=expires_iso,
            items=items,
        )

    log.info("Tao don %s | ban %s | %s d | %d mon",
             order_code, payload.tableId, f"{total:,}", len(items))

    return {
        "orderCode": order_code,
        "tableId": payload.tableId,
        "totalAmount": total,
        "status": STATUS_PENDING,
        "qrUrl": qr_url,
        "transferContent": build_transfer_content(order_code),
        "createdAt": created_iso,
        "expiresAt": expires_iso,
        "secondsRemaining": ORDER_TTL_MINUTES * 60,
        "items": items,
    }


@app.get("/api/orders/{order_code}/status")
def order_status(order_code: str) -> dict[str, Any]:
    with _conn() as c:
        row = db_get_order(c, order_code)
        if row is None:
            raise HTTPException(404, f"Khong tim thay don {order_code}")

        status = row["status"]
        # Het han kieu lazy: kiem tra luc co request doc, khoi can background job
        if status == STATUS_PENDING and seconds_remaining(row["expires_at"]) == 0:
            db_cancel_expired(c, order_code)
            status = STATUS_CANCELLED
            log.info("Don %s het han -> CANCELLED", order_code)

        return {
            "orderCode": row["order_code"],
            "tableId": row["table_id"],
            "status": status,
            "totalAmount": row["total_amount"],
            "paidAmount": row["paid_amount"],
            "paidAt": row["paid_at"],
            "expiresAt": row["expires_at"],
            "secondsRemaining": seconds_remaining(row["expires_at"]),
            "qrUrl": row["qr_url"],
        }


@app.get("/api/orders/{order_code}")
def order_detail(order_code: str) -> dict[str, Any]:
    with _conn() as c:
        row = db_get_order(c, order_code)
        if row is None:
            raise HTTPException(404, f"Khong tim thay don {order_code}")
        items = db_get_order_items(c, order_code)

    detail = order_status(order_code)
    detail["items"] = [
        {
            "itemCode": i["item_code"],
            "name": i["item_name"],
            "quantity": i["quantity"],
            "unitPrice": i["unit_price"],
            "lineTotal": i["line_total"],
            "options": json.loads(i["options"]) if i["options"] else {},
        }
        for i in items
    ]
    return detail


@app.post("/api/payment/webhook")
def sepay_webhook(
    payload: dict[str, Any] = Body(...),
    authorization: str | None = Header(default=None),
) -> dict[str, Any]:
    """Nhan webhook bien dong so du tu Sepay.

    Tra 200 cho moi truong hop da xu ly xong (ke ca khi bo qua) - tra loi se
    khien Sepay retry lien tuc voi nhung giao dich khong lien quan den quan.
    Chi 401 khi xac thuc that bai.
    """
    # 1. Xac thuc. Khong co key cau hinh -> tu choi het (fail closed).
    expected = f"Apikey {SEPAY_API_KEY}"
    if not SEPAY_API_KEY or not authorization or not hmac.compare_digest(authorization, expected):
        log.warning("Webhook bi tu choi: sai hoac thieu Authorization")
        raise HTTPException(401, "Unauthorized")

    # 2. Chi quan tam tien vao
    if str(payload.get("transferType", "")).lower() != "in":
        return {"success": True, "ignored": "transferType != in"}

    content = payload.get("content") or payload.get("description") or ""
    amount = int(payload.get("transferAmount") or 0)
    trans_id = payload.get("referenceCode") or str(payload.get("id") or "")

    # 3. Tach ma don tu noi dung chuyen khoan
    order_code = extract_order_code(content)

    with _conn() as c:
        if order_code is None:
            order_code = db_find_code_in_text(c, normalise_transfer_content(content))
        if order_code is None:
            log.info("Webhook khong khop don nao | noi dung=%r | %s d", content, f"{amount:,}")
            return {"success": True, "ignored": "khong tim thay ma don"}

        row = db_get_order(c, order_code)
        if row is None:
            log.info("Webhook cho ma don khong ton tai: %s", order_code)
            return {"success": True, "ignored": "don khong ton tai"}

        # 4. Idempotent: webhook ban lai khong ghi de paid_at
        if row["status"] == STATUS_PAID:
            log.info("Webhook lap lai cho don da PAID: %s", order_code)
            return {"success": True, "orderCode": order_code, "status": STATUS_PAID,
                    "duplicate": True}

        # 5. Doi chieu so tien - chuyen thieu thi KHONG danh dau da thanh toan
        if amount < row["total_amount"]:
            db_record_underpaid(c, order_code, amount)
            log.warning("Don %s chuyen thieu: nhan %s / can %s",
                        order_code, f"{amount:,}", f"{row['total_amount']:,}")
            return {"success": True, "orderCode": order_code, "status": row["status"],
                    "underpaid": True}

        if seconds_remaining(row["expires_at"]) == 0:
            log.warning("Don %s da qua han nhung tien da vao -> van ghi PAID, "
                        "nhan vien kiem tra lai", order_code)

        db_mark_paid(c, order_code, trans_id, amount)

    log.info("Don %s -> PAID | %s d | ref %s", order_code, f"{amount:,}", trans_id)
    return {"success": True, "orderCode": order_code, "status": STATUS_PAID}


@app.post("/api/payment/mock-pay/{order_code}")
def mock_pay(order_code: str) -> dict[str, Any]:
    """Gia lap thanh toan thanh cong de test khong can chuyen tien that.

    Khong co guard ALLOW_MOCK_PAY thi day chinh la lo hong an do free y het
    webhook khong xac thuc. TAT (ALLOW_MOCK_PAY=0) truoc khi dung o quan.
    """
    if not ALLOW_MOCK_PAY:
        raise HTTPException(403, "mock-pay da bi tat (ALLOW_MOCK_PAY=0)")

    with _conn() as c:
        row = db_get_order(c, order_code)
        if row is None:
            raise HTTPException(404, f"Khong tim thay don {order_code}")
        if row["status"] == STATUS_PAID:
            return {"success": True, "orderCode": order_code, "status": STATUS_PAID,
                    "duplicate": True}
        db_mark_paid(c, order_code, f"MOCK-{secrets.token_hex(4).upper()}",
                     row["total_amount"])

    log.info("Don %s -> PAID (MOCK)", order_code)
    return {"success": True, "orderCode": order_code, "status": STATUS_PAID, "mock": True}


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host="0.0.0.0", port=int(os.environ.get("PORT", "8080")))
