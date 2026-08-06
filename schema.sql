-- =============================================================================
--  CADEBOT / VIVA RESERVE - SCHEMA + SEED DATA
--  Dialect: SQLite (khop voi demo_cafe.db dang chay)
--
--  Dung lai toan bo DB tu dau:
--      rm -f demo_cafe.db && sqlite3 demo_cafe.db < schema.sql
--  Hoac bang Python neu chua cai sqlite3 CLI:
--      python3 -c "import sqlite3,pathlib; sqlite3.connect('demo_cafe.db')\
--          .executescript(pathlib.Path('schema.sql').read_text())"
--
--  LUU Y: file nay truoc day viet bang dialect PostgreSQL (SERIAL/JSONB/BOOLEAN)
--  nen khong chay duoc tren demo_cafe.db, va da lech khoi DB that
--  (is_available vs available, thua cot updated_at, thieu cot attributes).
--  Da viet lai toan bo theo SQLite - xem file backup schema.sql.bak-* neu can
--  ban PostgreSQL cu.
-- =============================================================================

PRAGMA journal_mode = WAL;
PRAGMA foreign_keys = ON;


-- =============================================================================
--  PHAN 1: KNOWLEDGE BASE - nhan vien cap nhat gia mon / khuyen mai o day,
--          demo_db_to_dify.py doc 3 bang nay de xuat db_exported_kb.md
-- =============================================================================

-- 1. Bang thuc don do uong & mon an
CREATE TABLE IF NOT EXISTS menu_items (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    item_code      TEXT    UNIQUE NOT NULL,
    name           TEXT    NOT NULL,
    category       TEXT    NOT NULL,
    price          INTEGER NOT NULL,
    description    TEXT,
    brewing_method TEXT,
    available      INTEGER DEFAULT 1,
    tags           TEXT,
    attributes     TEXT              -- JSON: size/sweetness/ice/temperature/toppings
);

-- 2. Bang chuong trinh khuyen mai & Combo
CREATE TABLE IF NOT EXISTS promotions (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    promo_code      TEXT    UNIQUE NOT NULL,
    title           TEXT    NOT NULL,
    discount_detail TEXT    NOT NULL,
    start_date      TEXT,
    end_date        TEXT,
    conditions      TEXT
);

-- 3. Bang bo cau hoi thuong gap (FAQ)
CREATE TABLE IF NOT EXISTS faqs (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    faq_id        TEXT    UNIQUE NOT NULL,
    question      TEXT    NOT NULL,
    answer        TEXT    NOT NULL,
    related_items TEXT,
    tags          TEXT
);


-- =============================================================================
--  PHAN 2: DON HANG & THANH TOAN VIETQR (Sepay)
--          Chi payment_server.py ghi vao 2 bang nay.
--          KHONG dua vao knowledge base (xem cadebot-plan.md).
-- =============================================================================

CREATE TABLE IF NOT EXISTS orders (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    order_code       TEXT    UNIQUE NOT NULL,   -- vd ORD7K3A9X, nhung trong noi dung CK
    table_id         TEXT    NOT NULL,          -- T01..T05 | BAR
    total_amount     INTEGER NOT NULL,          -- VND, tinh o server tu menu_items
    status           TEXT    NOT NULL DEFAULT 'PENDING',
    payment_method   TEXT    NOT NULL DEFAULT 'VIETQR',
    payment_trans_id TEXT,                      -- Sepay referenceCode
    paid_amount      INTEGER,                   -- so tien thuc nhan, de doi soat
    qr_url           TEXT,
    created_at       TEXT    NOT NULL,          -- ISO8601 UTC
    expires_at       TEXT    NOT NULL,          -- created_at + 10 phut
    paid_at          TEXT,

    -- Khop chinh xac enum OrderStatus trong OrderModels.kt cua app Android
    CHECK (status IN ('PENDING','PAID','PREPARING','READY',
                      'DISPATCHED','DELIVERED','CANCELLED'))
);

CREATE INDEX IF NOT EXISTS idx_orders_status  ON orders(status);
CREATE INDEX IF NOT EXISTS idx_orders_created ON orders(created_at);

CREATE TABLE IF NOT EXISTS order_items (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    order_code TEXT    NOT NULL REFERENCES orders(order_code) ON DELETE CASCADE,
    item_code  TEXT    NOT NULL,
    item_name  TEXT    NOT NULL,   -- snapshot ten mon luc dat, phong khi menu doi sau
    quantity   INTEGER NOT NULL,
    unit_price INTEGER NOT NULL,   -- gia mon + phu thu topping (5.000d/topping)
    line_total INTEGER NOT NULL,   -- unit_price * quantity
    options    TEXT,               -- JSON: size/sweetness/ice/temperature/toppings/note
    created_at TEXT    NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_order_items_code ON order_items(order_code);


-- =============================================================================
--  PHAN 3: BANG GIA PHU THU
--          Nhan vien sua gia size ngay o day, khong can build lai app:
--          app goi GET /api/pricing luc mo gio hang de lay ban moi nhat.
-- =============================================================================

CREATE TABLE IF NOT EXISTS pricing_rules (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    rule_type  TEXT    NOT NULL,   -- 'SIZE' | 'TOPPING'
    rule_key   TEXT    NOT NULL,   -- SIZE: S/M/L | TOPPING: 'default'
    surcharge  INTEGER NOT NULL,   -- VND, cong them vao gia goc (am = giam gia)
    note       TEXT,
    UNIQUE (rule_type, rule_key),
    CHECK (rule_type IN ('SIZE','TOPPING'))
);

-- Gia trong menu_items la gia size M, nen M co phu thu = 0.
INSERT OR IGNORE INTO pricing_rules (rule_type, rule_key, surcharge, note) VALUES
('SIZE',    'S',       -5000, 'Size nho, re hon size M 5.000d'),
('SIZE',    'M',           0, 'Size mac dinh - gia goc trong menu_items'),
('SIZE',    'L',       10000, 'Size lon, phu thu 10.000d'),
('TOPPING', 'default',  5000, 'Dong gia moi topping');


-- =============================================================================
--  SEED DATA - nap tu Cadebot UI (app-debug.apk / assets/config/menu.json)
-- =============================================================================

-- Thuc don (12 mon)
INSERT OR IGNORE INTO menu_items (item_code, name, category, price, description, brewing_method, available, tags, attributes) VALUES
('VR_LATTE_M', 'Viva Latte', 'Cà Phê', 55000, 'Latte signature của Viva, vị sữa béo nhẹ và espresso cân bằng.', 'Pha Máy', 1, 'best_seller, milk, coffee', '{"caffeine": true, "temperatureOptions": ["hot", "iced"], "defaultTemperature": "iced", "sweetnessOptions": ["0%", "30%", "50%", "70%", "100%"], "defaultSweetness": "50%", "iceOptions": ["no_ice", "less_ice", "normal_ice", "extra_ice"], "defaultIce": "normal_ice", "sizeOptions": ["S", "M", "L"], "defaultSize": "M", "toppings": ["extra_shot", "oat_milk", "pearl", "jelly"]}'),
('VR_CAPPUCCINO', 'Cappuccino', 'Cà Phê', 50000, 'Cappuccino cổ điển với lớp bọt sữa dày mịn, hương espresso đậm đà.', 'Pha Máy', 1, 'classic, milk, coffee', '{"caffeine": true, "temperatureOptions": ["hot", "iced"], "defaultTemperature": "hot", "sweetnessOptions": ["0%", "30%", "50%", "70%", "100%"], "defaultSweetness": "30%", "iceOptions": ["no_ice", "less_ice", "normal_ice"], "defaultIce": "normal_ice", "sizeOptions": ["S", "M", "L"], "defaultSize": "M", "toppings": ["extra_shot", "oat_milk", "cinnamon"]}'),
('VR_AMERICANO', 'Americano', 'Cà Phê', 45000, 'Espresso pha loãng với nước nóng, thanh thoát và nhẹ nhàng.', 'Pha Máy', 1, 'light, no_milk, coffee', '{"caffeine": true, "temperatureOptions": ["hot", "iced"], "defaultTemperature": "iced", "sweetnessOptions": ["0%", "30%", "50%"], "defaultSweetness": "0%", "iceOptions": ["no_ice", "less_ice", "normal_ice", "extra_ice"], "defaultIce": "normal_ice", "sizeOptions": ["S", "M", "L"], "defaultSize": "M", "toppings": ["extra_shot"]}'),
('VR_MATCHA_LATTE', 'Matcha Latte', 'Trà', 60000, 'Matcha Nhật Bản cao cấp hòa quyện cùng sữa tươi thơm béo.', 'Pha Trực Tiếp', 1, 'no_coffee, milk, matcha, best_seller', '{"caffeine": false, "temperatureOptions": ["hot", "iced"], "defaultTemperature": "iced", "sweetnessOptions": ["0%", "30%", "50%", "70%", "100%"], "defaultSweetness": "50%", "iceOptions": ["no_ice", "less_ice", "normal_ice", "extra_ice"], "defaultIce": "normal_ice", "sizeOptions": ["S", "M", "L"], "defaultSize": "M", "toppings": ["oat_milk", "pearl", "extra_matcha"]}'),
('VR_JASMINE_TEA', 'Trà Hoa Nhài', 'Trà', 40000, 'Trà hoa nhài thanh mát, hương thơm tự nhiên dịu nhẹ.', 'Pha Trực Tiếp', 1, 'no_coffee, no_milk, light, refreshing', '{"caffeine": false, "temperatureOptions": ["hot", "iced"], "defaultTemperature": "iced", "sweetnessOptions": ["0%", "30%", "50%", "70%", "100%"], "defaultSweetness": "50%", "iceOptions": ["no_ice", "less_ice", "normal_ice", "extra_ice"], "defaultIce": "normal_ice", "sizeOptions": ["S", "M", "L"], "defaultSize": "M", "toppings": ["pearl", "lychee_jelly"]}'),
('VR_PEACH_TEA', 'Trà Đào Cam Sả', 'Trà', 45000, 'Kết hợp đào, cam và sả tươi, vừa thơm vừa mát lạnh.', 'Pha Trực Tiếp', 1, 'no_coffee, fruity, refreshing', '{"caffeine": false, "temperatureOptions": ["iced"], "defaultTemperature": "iced", "sweetnessOptions": ["30%", "50%", "70%", "100%"], "defaultSweetness": "70%", "iceOptions": ["less_ice", "normal_ice", "extra_ice"], "defaultIce": "normal_ice", "sizeOptions": ["M", "L"], "defaultSize": "M", "toppings": ["lychee_jelly", "aloe_vera"]}'),
('VR_STRAWBERRY_BLEND', 'Dâu Đá Xay', 'Đá Xay', 65000, 'Đá xay dâu tây tươi, mịn như kem, ngọt ngào và bắt mắt.', 'Đá Xay', 1, 'no_coffee, fruity, sweet, best_seller', '{"caffeine": false, "temperatureOptions": ["iced"], "defaultTemperature": "iced", "sweetnessOptions": ["50%", "70%", "100%"], "defaultSweetness": "70%", "iceOptions": ["normal_ice"], "defaultIce": "normal_ice", "sizeOptions": ["M", "L"], "defaultSize": "M", "toppings": ["whipped_cream", "strawberry_sauce"]}'),
('VR_MOCHA_BLEND', 'Mocha Đá Xay', 'Đá Xay', 65000, 'Espresso và sô cô la xay cùng đá, phủ kem tươi béo ngậy.', 'Đá Xay', 1, 'coffee, chocolate, sweet', '{"caffeine": true, "temperatureOptions": ["iced"], "defaultTemperature": "iced", "sweetnessOptions": ["50%", "70%", "100%"], "defaultSweetness": "70%", "iceOptions": ["normal_ice"], "defaultIce": "normal_ice", "sizeOptions": ["M", "L"], "defaultSize": "M", "toppings": ["whipped_cream", "chocolate_sauce", "extra_shot"]}'),
('VR_CROISSANT', 'Bánh Croissant Bơ', 'Bánh Ngọt', 35000, 'Croissant bơ kiểu Pháp, vỏ giòn xốp, ruột mềm thơm lừng.', 'Pha Trực Tiếp', 1, 'bakery, butter, classic', '{"caffeine": false, "temperatureOptions": ["warm"], "defaultTemperature": "warm", "sweetnessOptions": [], "sizeOptions": [], "toppings": []}'),
('VR_TIRAMISU', 'Bánh Tiramisu', 'Bánh Ngọt', 55000, 'Tiramisu Ý thơm nức với mascarpone, cà phê và cacao nguyên chất.', 'Pha Trực Tiếp', 1, 'coffee, dessert, italian', '{"caffeine": true, "temperatureOptions": ["cold"], "defaultTemperature": "cold", "sweetnessOptions": [], "sizeOptions": [], "toppings": []}'),
('VR_COMBO_A', 'Combo Sáng Sớm', 'Combo', 85000, '1 Americano + 1 Croissant Bơ. Tiết kiệm 5.000đ so với gọi riêng.', 'Đá Xay', 1, 'combo, morning, value', '{"caffeine": true, "temperatureOptions": ["hot", "iced"], "defaultTemperature": "hot", "sweetnessOptions": ["0%", "30%", "50%"], "defaultSweetness": "0%", "iceOptions": ["no_ice", "normal_ice"], "defaultIce": "no_ice", "sizeOptions": [], "toppings": []}'),
('VR_COMBO_B', 'Combo Chiều Thư Giãn', 'Combo', 110000, '1 Matcha Latte + 1 Dâu Đá Xay + 1 Bánh Tiramisu. Ưu đãi đặc biệt.', 'Đá Xay', 1, 'combo, afternoon, no_coffee, best_seller', '{"caffeine": false, "temperatureOptions": ["iced"], "defaultTemperature": "iced", "sweetnessOptions": ["50%", "70%"], "defaultSweetness": "50%", "iceOptions": ["normal_ice"], "defaultIce": "normal_ice", "sizeOptions": [], "toppings": []}');

-- Khuyen mai (3 chuong trinh)
INSERT OR IGNORE INTO promotions (promo_code, title, discount_detail, start_date, end_date, conditions) VALUES
('camp_001', 'Combo Sáng Sớm', 'Americano + Croissant chỉ 85.000đ - Khởi đầu ngày mới năng lượng với combo tiết kiệm 5.000đ.', '2026-06-01', '2026-12-31', 'Áp dụng cho khách hàng tại Viva Reserve'),
('camp_002', 'Chiều Thư Giãn', 'Matcha + Dâu Đá Xay + Tiramisu 110.000đ - Bộ 3 hoàn hảo cho chiều tà, tiết kiệm hơn 10.000đ.', '2026-06-01', '2026-12-31', 'Áp dụng cho khách hàng tại Viva Reserve'),
('camp_003', 'Viva Reserve Experience', 'Khám phá không gian cà phê độc đáo - Cadebot L100 phục vụ tận tay — trải nghiệm cà phê tương lai tại Viva Reserve.', '2026-06-01', '2026-12-31', 'Áp dụng cho khách hàng tại Viva Reserve');

-- FAQ (21 cau)
INSERT OR IGNORE INTO faqs (faq_id, question, answer, related_items, tags) VALUES
('faq_001', 'Viva Latte có vị như thế nào?', 'Viva Latte có vị sữa béo nhẹ kết hợp espresso cân bằng, không quá đắng cũng không quá ngọt. Đây là món best seller của Viva Reserve.', 'VR_LATTE_M', 'menu_qa, coffee'),
('faq_002', 'Latte có caffeine không?', 'Có, Viva Latte chứa espresso nên có caffeine. Nếu bạn muốn tránh caffeine, mình gợi ý Matcha Latte hoặc Trà Đào Cam Sả — đều không có cà phê.', 'VR_LATTE_M, VR_MATCHA_LATTE, VR_PEACH_TEA', 'product_qa, caffeine'),
('faq_003', 'Món nào không có cà phê?', 'Các món không chứa cà phê tại Viva: Matcha Latte, Trà Hoa Nhài, Trà Đào Cam Sả, Dâu Đá Xay, Croissant Bơ. Bạn muốn thử món nào?', 'VR_MATCHA_LATTE, VR_JASMINE_TEA, VR_PEACH_TEA, VR_STRAWBERRY_BLEND', 'recommendation, no_coffee'),
('faq_004', 'Có món nào ít ngọt không?', 'Bạn có thể chọn độ ngọt 0% hoặc 30% cho hầu hết các món. Americano và Trà Hoa Nhài rất phù hợp nếu bạn thích ít ngọt tự nhiên.', 'VR_AMERICANO, VR_JASMINE_TEA', 'recommendation, less_sweet'),
('faq_005', 'Gọi món như thế nào?', 'Bạn có thể chọn món trực tiếp từ menu trên màn hình, hoặc nói với mình để mình gợi ý và thêm vào giỏ hàng. Sau khi xác nhận giỏ hàng, bạn quét mã QR để thanh toán.', '', 'how_to_order'),
('faq_006', 'Thanh toán bằng gì?', 'Tại Viva Reserve, bạn thanh toán bằng mã QR (MoMo, VNPay, VietQR) hoặc tiền mặt qua nhân viên. Robot sẽ hiển thị mã QR sau khi bạn xác nhận giỏ hàng.', '', 'payment, how_to'),
('faq_007', 'Robot có giao món đến bàn không?', 'Có! Sau khi thanh toán, nhân viên sẽ chuẩn bị đồ uống và robot sẽ giao đến tận bàn của bạn. Màn hình sẽ thông báo khi robot đang đến.', '', 'delivery, robot'),
('faq_008', 'Matcha Latte có sữa không?', 'Có, Matcha Latte dùng sữa tươi thơm béo. Nếu bạn không dùng được sữa bò, có thể chọn topping Oat Milk để thay thế.', 'VR_MATCHA_LATTE', 'product_qa, milk, allergen'),
('faq_009', 'Combo nào tiết kiệm nhất?', 'Combo Sáng Sớm (Americano + Croissant) giá 85.000đ, tiết kiệm 5.000đ. Combo Chiều Thư Giãn (Matcha Latte + Dâu Đá Xay + Tiramisu) giá 110.000đ, tiết kiệm hơn 10.000đ so với gọi riêng.', 'VR_COMBO_A, VR_COMBO_B', 'promotion, combo, value'),
('faq_010', 'Có ưu đãi gì không?', 'Hiện tại Viva có Combo Sáng Sớm và Combo Chiều Thư Giãn với giá ưu đãi. Bạn có thể hỏi nhân viên về các chương trình khuyến mãi mới nhất.', 'VR_COMBO_A, VR_COMBO_B', 'promotion'),
('faq_011', 'Croissant có vị gì?', 'Croissant Bơ của Viva kiểu Pháp, vỏ ngoài giòn xốp nhiều lớp, bên trong mềm thơm mùi bơ. Phù hợp ăn kèm cà phê buổi sáng.', 'VR_CROISSANT', 'product_qa, pastry'),
('faq_012', 'Dâu Đá Xay có ngọt không?', 'Dâu Đá Xay mặc định 70% đường, khá ngọt và thơm mùi dâu tươi. Bạn có thể chọn 50% đường nếu muốn vừa ngọt hơn.', 'VR_STRAWBERRY_BLEND', 'product_qa, sweetness'),
('faq_013', 'Gọi thêm món được không?', 'Được! Bạn nhấn nút Bắt đầu đặt món hoặc nói với mình để thêm món. Nhân viên cũng hỗ trợ nếu bạn cần.', '', 'how_to_order'),
('faq_014', 'Trà Đào Cam Sả có vị chua không?', 'Trà Đào Cam Sả có vị chua nhẹ tự nhiên từ cam, kết hợp ngọt thơm của đào và mát của sả. Rất sảng khoái, phù hợp buổi chiều.', 'VR_PEACH_TEA', 'product_qa, tea'),
('faq_015', 'Món nào phù hợp uống nóng?', 'Cappuccino, Americano và Viva Latte đều có option nóng rất ngon. Trà Hoa Nhài nóng cũng rất thơm và dễ uống.', 'VR_CAPPUCCINO, VR_AMERICANO, VR_LATTE_M, VR_JASMINE_TEA', 'recommendation, hot'),
('faq_016', 'Có topping nào không?', 'Viva có các topping: Extra Shot (espresso thêm), Oat Milk (sữa yến mạch), Trân Châu, Thạch, Kem Tươi, Sốt Sô Cô La, Sốt Dâu. Tuỳ từng món sẽ có topping khác nhau.', '', 'product_qa, topping'),
('faq_017', 'Tôi muốn gọi nhân viên', 'Bạn nhấn nút Gọi nhân viên trên màn hình, nhân viên Viva sẽ đến hỗ trợ bạn ngay.', '', 'call_staff'),
('faq_018', 'Size nào phổ biến nhất?', 'Size M (medium) là size phổ biến nhất tại Viva. Nếu bạn muốn nhiều hơn hoặc chia sẻ, size L là lựa chọn tốt.', '', 'product_qa, size'),
('faq_019', 'Tiramisu có cà phê không?', 'Có, Tiramisu Ý chứa espresso và cacao, nên có caffeine. Nếu bạn tránh caffeine, Croissant Bơ và Dâu Đá Xay là lựa chọn không có cà phê.', 'VR_TIRAMISU, VR_CROISSANT, VR_STRAWBERRY_BLEND', 'product_qa, caffeine, pastry'),
('faq_020', 'Bao lâu thì nhận được món?', 'Sau khi thanh toán, nhân viên sẽ pha chế trong khoảng 3-5 phút và robot sẽ giao đến bàn. Màn hình sẽ hiển thị trạng thái đơn hàng của bạn.', '', 'how_to, delivery, time'),
('faq_021', 'Bao lâu thì nhận được món?', 'Sau khi thanh toán, nhân viên sẽ pha chế trong khoảng 3-5 phút và robot sẽ giao đến bàn. Màn hình sẽ hiển thị trạng thái đơn hàng của bạn.', '', 'how_to, delivery, time');
