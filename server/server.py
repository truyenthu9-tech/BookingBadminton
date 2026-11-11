# -*- coding: utf-8 -*-
"""
Backend cho app đặt sân cầu lông (khớp BadmintonApiService).
Chỉ dùng thư viện chuẩn của Python 3.8+, database là file badminton.db (SQLite)
nằm cùng thư mục với file này (server/).
Chạy:  python server.py      (mặc định cổng 8000)

Bảo mật:
- Mật khẩu băm PBKDF2 (tài khoản cũ để plaintext sẽ tự nâng cấp khi đăng nhập).
- Đăng nhập trả về token; endpoint yêu cầu token qua header "Authorization: Bearer <token>".
- Endpoint admin yêu cầu tài khoản có role = admin.
"""
import base64, csv, hashlib, hmac, io, json, os, random, re, secrets, sqlite3, sys, threading, time, unicodedata, urllib.request
from datetime import datetime, timedelta, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse, parse_qs, urlencode, quote_plus

# Tránh lỗi UnicodeEncodeError khi console Windows dùng codepage cp1252
if hasattr(sys.stdout, "reconfigure"):
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass

DB = os.path.join(os.path.dirname(os.path.abspath(__file__)), "badminton.db")
AVATAR_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "avatars")
PORT = int(os.environ.get("PORT", 8000))
LOCK = threading.Lock()

# ---------- Cấu hình OTP / SMS ----------
# Để gửi SMS thật bằng Twilio, đặt 3 biến môi trường:
#   TWILIO_SID / TWILIO_TOKEN / TWILIO_FROM
# Nếu chưa đặt, server sẽ ghi mã OTP ra console. DEBUG_OTP=1 sẽ trả thêm mã
# trong response (khóa "debug_code") - CHỈ dùng khi phát triển, mặc định tắt.
DEBUG_OTP = int(os.environ.get("DEBUG_OTP", "0")) == 1
OTP_TTL = 120            # mã OTP có hiệu lực 2 phút
OTP_RESEND = 60          # mỗi số chỉ được gửi lại sau 60 giây
OTP_MAX_ATTEMPTS = 5     # tối đa 5 lần nhập sai
TOKEN_TTL = 30 * 24 * 3600  # token sống 30 ngày

# ---------- Cấu hình VNPay (SANDBOX) ----------
# BẮT BUỘC đặt 2 biến môi trường này trước khi chạy (không ghi vào code):
#   VNP_TMN_CODE   (Mã website / Terminal ID)
#   VNP_HASH_SECRET (Chuỗi bí mật tạo checksum)
# Tùy chọn: VNP_RETURN_URL (mặc định tự sinh từ Host header của request).
VNP_URL = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html"
VNP_TMN_CODE = os.environ.get("VNP_TMN_CODE", "")
VNP_HASH_SECRET = os.environ.get("VNP_HASH_SECRET", "")
VNP_RETURN_URL = os.environ.get("VNP_RETURN_URL", "")
VNP_EXPIRE_MINUTES = 15  # link thanh toán hết hạn sau 15 phút


def conn():
    c = sqlite3.connect(DB)
    c.row_factory = sqlite3.Row
    return c


def init_db():
    c = conn()
    c.executescript("""
    CREATE TABLE IF NOT EXISTS users(
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        student_id TEXT UNIQUE NOT NULL,
        full_name TEXT NOT NULL,
        email TEXT DEFAULT '',
        phone TEXT DEFAULT '',
        avatar TEXT DEFAULT '',
        password TEXT NOT NULL,
        role TEXT DEFAULT 'student');
    CREATE TABLE IF NOT EXISTS tokens(
        token TEXT PRIMARY KEY,
        user_id INTEGER NOT NULL,
        expires_at REAL NOT NULL);
    DROP TABLE IF EXISTS otp_codes;
    CREATE TABLE otp_codes(
        phone TEXT PRIMARY KEY,
        code TEXT NOT NULL,
        expires_at REAL NOT NULL,
        sent_at REAL NOT NULL DEFAULT 0,
        attempts INTEGER NOT NULL DEFAULT 0);
    CREATE TABLE IF NOT EXISTS courts(
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        name TEXT NOT NULL, location TEXT DEFAULT '',
        price_per_hour REAL DEFAULT 0, description TEXT DEFAULT '',
        image_url TEXT DEFAULT '', hidden INTEGER DEFAULT 0);
    CREATE TABLE IF NOT EXISTS slots(
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        court_id INTEGER NOT NULL, start_time TEXT NOT NULL, end_time TEXT NOT NULL);
    CREATE TABLE IF NOT EXISTS bookings(
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        booking_code TEXT, user_id INTEGER, court_id INTEGER, court_name TEXT,
        date TEXT, slot_ids TEXT, slots_text TEXT, start_time TEXT, end_time TEXT,
        total_price REAL, status TEXT DEFAULT 'pending', qr_code TEXT, valid_until TEXT,
        created_at TEXT);
    CREATE TABLE IF NOT EXISTS payments(
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        booking_id INTEGER NOT NULL,
        txn_ref TEXT UNIQUE NOT NULL,
        amount INTEGER NOT NULL,
        status TEXT DEFAULT 'PENDING',
        vnp_transaction_no TEXT DEFAULT '',
        vnp_response_code TEXT DEFAULT '',
        created_at TEXT,
        paid_at TEXT);
    CREATE TABLE IF NOT EXISTS posts(
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        user_id INTEGER, author TEXT, content TEXT, created_at TEXT);
    """)
    # Migrate DB cũ: thêm cột phone / avatar nếu chưa có
    cols = [r["name"] for r in c.execute("PRAGMA table_info(users)")]
    if "phone" not in cols:
        c.execute("ALTER TABLE users ADD COLUMN phone TEXT DEFAULT ''")
    if "avatar" not in cols:
        c.execute("ALTER TABLE users ADD COLUMN avatar TEXT DEFAULT ''")
    # Migrate DB cũ: thêm cột payment_status cho đơn đặt sân
    cols_b = [r["name"] for r in c.execute("PRAGMA table_info(bookings)")]
    if "payment_status" not in cols_b:
        c.execute("ALTER TABLE bookings ADD COLUMN payment_status TEXT DEFAULT 'UNPAID'")
    if c.execute("SELECT COUNT(*) FROM users").fetchone()[0] == 0:
        c.execute("INSERT INTO users(student_id,full_name,email,password,role) VALUES(?,?,?,?,?)",
                  ("admin", "Quản trị viên", "admin@haui.edu.vn", hash_password("admin123"), "admin"))
        c.execute("INSERT INTO users(student_id,full_name,email,password,role) VALUES(?,?,?,?,?)",
                  ("2021600001", "Nguyễn Văn A", "a@haui.edu.vn", hash_password("123456"), "student"))
    if c.execute("SELECT COUNT(*) FROM courts").fetchone()[0] == 0:
        courts = [("Sân 1", "Nhà thi đấu HaUI - Khu A", 80000, "Sân tiêu chuẩn, sàn gỗ, đèn LED"),
                  ("Sân 2", "Nhà thi đấu HaUI - Khu A", 80000, "Sân tiêu chuẩn, có điều hòa"),
                  ("Sân 3", "Nhà thi đấu HaUI - Khu B", 100000, "Sân VIP, sàn PU chuyên dụng"),
                  ("Sân 4", "Nhà thi đấu HaUI - Khu B", 100000, "Sân VIP, có khán đài nhỏ")]
        for name, loc, price, desc in courts:
            cur = c.execute("INSERT INTO courts(name,location,price_per_hour,description) VALUES(?,?,?,?)",
                            (name, loc, price, desc))
            for h in range(6, 22):  # 06:00 -> 22:00, mỗi ca 1 giờ
                c.execute("INSERT INTO slots(court_id,start_time,end_time) VALUES(?,?,?)",
                          (cur.lastrowid, "%02d:00" % h, "%02d:00" % (h + 1)))
    c.commit()
    c.close()


# ---------------- helpers ----------------
def now():
    return datetime.now().strftime("%Y-%m-%d %H:%M:%S")


def hash_password(pw):
    salt = secrets.token_hex(16)
    digest = hashlib.pbkdf2_hmac("sha256", pw.encode("utf-8"), bytes.fromhex(salt), 120_000).hex()
    return "pbkdf2_sha256$120000$%s$%s" % (salt, digest)


def verify_password(pw, stored):
    """Trả về (đúng/sai, có cần nâng cấp từ plaintext hay không)."""
    if stored.startswith("pbkdf2_sha256$"):
        try:
            _, iters, salt, digest = stored.split("$")
            calc = hashlib.pbkdf2_hmac("sha256", pw.encode("utf-8"),
                                       bytes.fromhex(salt), int(iters)).hex()
            return calc == digest, False
        except Exception:
            return False, False
    return pw == stored, True  # mật khẩu cũ còn để plaintext


def issue_token(c, uid):
    tok = secrets.token_hex(24)
    c.execute("INSERT INTO tokens(token,user_id,expires_at) VALUES(?,?,?)",
              (tok, uid, time.time() + TOKEN_TTL))
    return tok


def expire_stale(c):
    """Vé chờ duyệt quá giờ đặt -> chuyển sang 'expired' (giải phóng ca giờ)."""
    now_dt = datetime.now()
    rows = c.execute("SELECT id, valid_until FROM bookings WHERE status='pending'").fetchall()
    for r in rows:
        try:
            t = datetime.strptime(r["valid_until"] or "", "%H:%M %Y-%m-%d")
            if t < now_dt:
                c.execute("UPDATE bookings SET status='expired' WHERE id=?", (r["id"],))
        except ValueError:
            pass


def booked_slot_ids(c, court_id, date):
    ids = set()
    for r in c.execute("SELECT slot_ids FROM bookings WHERE court_id=? AND date=? "
                       "AND status NOT IN ('cancelled','expired')", (court_id, date)):
        ids.update(json.loads(r["slot_ids"] or "[]"))
    return ids


def court_json(c, r, date=None):
    d = {"id": r["id"], "name": r["name"], "location": r["location"],
         "price_per_hour": r["price_per_hour"], "description": r["description"],
         "image_url": r["image_url"], "hidden": bool(r["hidden"])}
    total = c.execute("SELECT COUNT(*) FROM slots WHERE court_id=?", (r["id"],)).fetchone()[0]
    used = len(booked_slot_ids(c, r["id"], date)) if date else 0
    d["status"] = "booked" if total and used >= total else "available"
    return d


def booking_json(r):
    return {"id": r["id"], "booking_code": r["booking_code"], "user_id": r["user_id"],
            "court_id": r["court_id"], "court_name": r["court_name"], "date": r["date"],
            "slot_ids": json.loads(r["slot_ids"] or "[]"), "slots_text": r["slots_text"],
            "start_time": r["start_time"], "end_time": r["end_time"],
            "total_price": r["total_price"], "status": r["status"],
            "payment_status": col(r, "payment_status", "UNPAID"),
            "qr_code": r["qr_code"], "valid_until": r["valid_until"]}


def col(r, name, default=""):
    try:
        v = r[name]
        return default if v is None else v
    except (IndexError, KeyError):
        return default


def avatar_payload(avatar):
    """avatar trong DB có thể là: rỗng | data URI (DB cũ) | đường dẫn file (mới).
    Trả về (base64 cho app hiển thị, avatar_url)."""
    if not avatar:
        return "", ""
    if avatar.startswith("data:"):
        return avatar, avatar
    if avatar.startswith("/api/uploads/"):
        fpath = os.path.join(AVATAR_DIR, os.path.basename(avatar))
        if os.path.isfile(fpath):
            try:
                with open(fpath, "rb") as f:
                    return "data:image/jpeg;base64," + base64.b64encode(f.read()).decode(), avatar
            except Exception:
                pass
        return "", avatar
    return avatar, avatar


def user_json(r):
    # Trả nhiều tên khóa tương đương để khớp với User.java (Gson bỏ qua khóa thừa)
    av_b64, av_url = avatar_payload(col(r, "avatar"))
    return {"id": r["id"], "user_id": r["id"], "userId": r["id"],
            "student_id": r["student_id"], "studentId": r["student_id"],
            "username": r["student_id"], "code": r["student_id"],
            "full_name": r["full_name"], "fullName": r["full_name"], "name": r["full_name"],
            "email": col(r, "email"), "phone": col(r, "phone"),
            "avatar": av_b64, "avatar_url": av_url,
            "role": r["role"], "is_admin": r["role"] == "admin",
            "isAdmin": r["role"] == "admin"}


def pick(d, *keys, default=""):
    for k in keys:
        if d.get(k) not in (None, ""):
            return d[k]
    return default


class HttpError(Exception):
    def __init__(self, code, msg):
        self.code, self.msg = code, msg


# ---------------- OTP / SMS ----------------
def send_sms(phone, code):
    sid = os.environ.get("TWILIO_SID", "")
    token = os.environ.get("TWILIO_TOKEN", "")
    frm = os.environ.get("TWILIO_FROM", "")
    if sid and token and frm:
        try:
            data = urlencode({"To": phone, "From": frm,
                              "Body": "Ma OTP dat san cau long cua ban: %s (hieu luc 2 phut)" % code}).encode()
            req = urllib.request.Request(
                "https://api.twilio.com/2010-04-01/Accounts/%s/Messages.json" % sid,
                data=data, method="POST")
            auth = base64.b64encode(("%s:%s" % (sid, token)).encode()).decode()
            req.add_header("Authorization", "Basic " + auth)
            req.add_header("Content-Type", "application/x-www-form-urlencoded")
            urllib.request.urlopen(req, timeout=10).read()
            print("[SMS] Đã gửi SMS tới %s" % phone)
            return True
        except Exception as e:  # noqa
            print("[SMS] Gửi SMS thất bại:", e)
            return False
    print("[OTP] Gửi mã OTP tới %s: %s" % (phone, code))
    return False


def verify_otp(c, phone, code):
    # Ghi chú: các thay đổi ở đây phải commit ngay, vì nếu register thất bại
    # sau đó, transaction sẽ bị rollback và số lần sai sẽ không được tính.
    r = c.execute("SELECT code, expires_at, attempts FROM otp_codes WHERE phone=?", (phone,)).fetchone()
    if not r:
        raise HttpError(400, "Chưa gửi mã OTP cho số điện thoại này")
    if r["expires_at"] < time.time():
        c.execute("DELETE FROM otp_codes WHERE phone=?", (phone,))
        c.commit()
        raise HttpError(400, "Mã OTP đã hết hạn, vui lòng gửi lại")
    if str(code).strip() != r["code"]:
        attempts = (r["attempts"] or 0) + 1
        if attempts >= OTP_MAX_ATTEMPTS:
            c.execute("DELETE FROM otp_codes WHERE phone=?", (phone,))
            c.commit()
            raise HttpError(400, "Sai mã OTP quá %d lần, vui lòng gửi mã mới" % OTP_MAX_ATTEMPTS)
        c.execute("UPDATE otp_codes SET attempts=? WHERE phone=?", (attempts, phone))
        c.commit()
        raise HttpError(400, "Mã OTP không đúng (còn %d lần thử)" % (OTP_MAX_ATTEMPTS - attempts))
    c.execute("DELETE FROM otp_codes WHERE phone=?", (phone,))
    c.commit()


def send_otp(c, q, body, auth):
    phone = str(pick(body, "phone", "phone_number", "mobile", "tel", "sdt")).strip()
    if not re.fullmatch(r"\+?\d{9,15}", phone):
        raise HttpError(400, "Số điện thoại không hợp lệ")
    now_t = time.time()
    existing = c.execute("SELECT sent_at FROM otp_codes WHERE phone=?", (phone,)).fetchone()
    if existing and existing["sent_at"] and now_t - existing["sent_at"] < OTP_RESEND:
        wait = int(OTP_RESEND - (now_t - existing["sent_at"])) + 1
        raise HttpError(429, "Vui lòng đợi %d giây rồi gửi lại" % wait)
    code = "%06d" % random.randint(0, 999999)
    expires = now_t + OTP_TTL
    c.execute("INSERT INTO otp_codes(phone,code,expires_at,sent_at,attempts) VALUES(?,?,?,?,0) "
              "ON CONFLICT(phone) DO UPDATE SET code=excluded.code, expires_at=excluded.expires_at, "
              "sent_at=excluded.sent_at, attempts=0",
              (phone, code, expires, now_t))
    sent = send_sms(phone, code)
    resp = {"message": "Đã gửi mã OTP", "phone": phone, "expires_in": OTP_TTL, "sent": sent}
    if DEBUG_OTP and not sent:
        resp["debug_code"] = code
    return 200, resp


# ---------------- handlers ----------------
def login(c, q, body, auth):
    sid = str(pick(body, "student_id", "studentId", "username", "code", "id_number", "mssv", "user_id"))
    pw = str(pick(body, "password", "pass"))
    r = c.execute("SELECT * FROM users WHERE student_id=?", (sid,)).fetchone()
    if not r:
        raise HttpError(401, "Sai mã sinh viên hoặc mật khẩu")
    ok, upgrade = verify_password(pw, r["password"])
    if not ok:
        raise HttpError(401, "Sai mã sinh viên hoặc mật khẩu")
    if upgrade:
        c.execute("UPDATE users SET password=? WHERE id=?", (hash_password(pw), r["id"]))
    token = issue_token(c, r["id"])
    return 200, {**user_json(r), "token": token}


def register(c, q, body, auth):
    sid = str(pick(body, "student_id", "studentId", "username", "code", "id_number", "mssv"))
    name = str(pick(body, "full_name", "fullName", "name"))
    pw = str(pick(body, "password", "pass"))
    phone = str(pick(body, "phone", "phone_number", "mobile", "tel", "sdt")).strip()
    otp = str(pick(body, "otp", "otp_code", "verify_code", "code_otp")).strip()
    if not sid or not name or not pw:
        raise HttpError(400, "Thiếu thông tin đăng ký")
    if not phone or not otp:
        raise HttpError(400, "Cần xác thực số điện thoại bằng mã OTP")
    verify_otp(c, phone, otp)
    if c.execute("SELECT 1 FROM users WHERE student_id=?", (sid,)).fetchone():
        raise HttpError(409, "Mã sinh viên đã tồn tại")
    if c.execute("SELECT 1 FROM users WHERE phone=?", (phone,)).fetchone():
        raise HttpError(409, "Số điện thoại đã được sử dụng")
    cur = c.execute("INSERT INTO users(student_id,full_name,email,phone,password) VALUES(?,?,?,?,?)",
                    (sid, name, str(pick(body, "email")), phone, hash_password(pw)))
    r = c.execute("SELECT * FROM users WHERE id=?", (cur.lastrowid,)).fetchone()
    return 201, {**user_json(r), "token": issue_token(c, r["id"])}


def update_user(c, q, body, auth, uid):
    if auth["role"] != "admin" and auth["id"] != uid:
        raise HttpError(403, "Không có quyền sửa tài khoản này")
    r = c.execute("SELECT * FROM users WHERE id=?", (uid,)).fetchone()
    if not r:
        raise HttpError(404, "Không tìm thấy tài khoản")
    name = str(pick(body, "full_name", "fullName", "name", default=col(r, "full_name")))
    email = str(pick(body, "email", default=col(r, "email")))
    phone = str(pick(body, "phone", "phone_number", "mobile", "tel", "sdt", default=col(r, "phone"))).strip()
    pw = str(pick(body, "password", "pass", default=""))
    if not name:
        raise HttpError(400, "Họ tên không được để trống")
    if phone and phone != col(r, "phone") and \
            c.execute("SELECT 1 FROM users WHERE phone=? AND id!=?", (phone, uid)).fetchone():
        raise HttpError(409, "Số điện thoại đã được sử dụng")
    if pw:
        c.execute("UPDATE users SET full_name=?,email=?,phone=?,password=? WHERE id=?",
                  (name, email, phone, hash_password(pw), uid))
    else:
        c.execute("UPDATE users SET full_name=?,email=?,phone=? WHERE id=?",
                  (name, email, phone, uid))
    return 200, user_json(c.execute("SELECT * FROM users WHERE id=?", (uid,)).fetchone())


def update_avatar(c, q, body, auth, uid):
    if auth["role"] != "admin" and auth["id"] != uid:
        raise HttpError(403, "Không có quyền sửa tài khoản này")
    r = c.execute("SELECT * FROM users WHERE id=?", (uid,)).fetchone()
    if not r:
        raise HttpError(404, "Không tìm thấy tài khoản")
    b64 = str(pick(body, "image_base64", "avatar_base64", "avatar", "image")).strip()
    if not b64:
        raise HttpError(400, "Thiếu dữ liệu ảnh")
    if "," in b64 and b64.lower().startswith("data:"):
        b64 = b64.split(",", 1)[1]
    try:
        raw = base64.b64decode(b64)
    except Exception:
        raise HttpError(400, "Ảnh không hợp lệ")
    if len(raw) > 2 * 1024 * 1024:
        raise HttpError(400, "Ảnh quá lớn (tối đa 2MB)")
    os.makedirs(AVATAR_DIR, exist_ok=True)
    fname = "avatar_%d.jpg" % uid
    with open(os.path.join(AVATAR_DIR, fname), "wb") as f:
        f.write(raw)
    # DB chỉ lưu đường dẫn, file ảnh nằm trên đĩa (không làm phình DB)
    avatar_url = "/api/uploads/avatars/" + fname
    c.execute("UPDATE users SET avatar=? WHERE id=?", (avatar_url, uid))
    return 200, user_json(c.execute("SELECT * FROM users WHERE id=?", (uid,)).fetchone())


def list_courts(c, q, body, auth):
    expire_stale(c)
    date = q.get("date", [None])[0]
    return 200, [court_json(c, r, date) for r in c.execute("SELECT * FROM courts ORDER BY id")]


def court_detail(c, q, body, auth, cid):
    expire_stale(c)
    r = c.execute("SELECT * FROM courts WHERE id=?", (cid,)).fetchone()
    if not r:
        raise HttpError(404, "Không tìm thấy sân")
    return 200, court_json(c, r)


def court_slots(c, q, body, auth, cid):
    expire_stale(c)
    date = q.get("date", [datetime.now().strftime("%Y-%m-%d")])[0]
    used = booked_slot_ids(c, cid, date)
    return 200, [{"id": s["id"], "court_id": s["court_id"], "start_time": s["start_time"],
                  "end_time": s["end_time"], "booked": s["id"] in used}
                 for s in c.execute("SELECT * FROM slots WHERE court_id=? ORDER BY start_time", (cid,))]


def create_booking(c, q, body, auth):
    expire_stale(c)
    court = c.execute("SELECT * FROM courts WHERE id=?", (body.get("court_id"),)).fetchone()
    if not court:
        raise HttpError(400, "Sân không tồn tại")
    ids = [int(i) for i in body.get("slot_ids") or []]
    if not ids:
        raise HttpError(400, "Chưa chọn ca giờ")
    date = body.get("date")
    if not date:
        raise HttpError(400, "Thiếu ngày đặt")
    slots = [c.execute("SELECT * FROM slots WHERE id=? AND court_id=?", (i, court["id"])).fetchone() for i in ids]
    if any(s is None for s in slots):
        raise HttpError(400, "Ca giờ không hợp lệ")
    if set(ids) & booked_slot_ids(c, court["id"], date):
        raise HttpError(409, "Ca giờ đã có người đặt")
    slots.sort(key=lambda s: s["start_time"])
    # Các ca chọn phải liền kề nhau (không cho đặt ca cách quãng)
    for a, b in zip(slots, slots[1:]):
        if a["end_time"] != b["start_time"]:
            raise HttpError(400, "Các ca giờ phải liền kề nhau")
    text = ", ".join("%s - %s" % (s["start_time"], s["end_time"]) for s in slots)
    start, end = slots[0]["start_time"], slots[-1]["end_time"]
    cur = c.execute(
        "INSERT INTO bookings(user_id,court_id,court_name,date,slot_ids,slots_text,start_time,end_time,"
        "total_price,status,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
        (auth["id"], court["id"], court["name"], date, json.dumps(ids), text, start, end,
         court["price_per_hour"] * len(ids), "pending", now()))
    code = "BK%06d" % cur.lastrowid
    c.execute("UPDATE bookings SET booking_code=?, qr_code=?, valid_until=? WHERE id=?",
              (code, code, "%s %s" % (end, date), cur.lastrowid))
    return 201, booking_json(c.execute("SELECT * FROM bookings WHERE id=?", (cur.lastrowid,)).fetchone())


def get_booking(c, q, body, auth, bid):
    expire_stale(c)
    r = c.execute("SELECT * FROM bookings WHERE id=?", (bid,)).fetchone()
    if not r:
        raise HttpError(404, "Không tìm thấy vé")
    return 200, booking_json(r)


def user_bookings(c, q, body, auth, uid):
    if auth["role"] != "admin" and auth["id"] != uid:
        raise HttpError(403, "Không có quyền xem vé của người khác")
    expire_stale(c)
    return 200, [booking_json(r) for r in
                 c.execute("SELECT * FROM bookings WHERE user_id=? ORDER BY id DESC", (uid,))]


def list_posts(c, q, body, auth):
    return 200, [post_json(r) for r in c.execute("SELECT * FROM posts ORDER BY id DESC")]


def post_json(r):
    return {"id": r["id"], "user_id": r["user_id"], "user_name": r["author"], "userName": r["author"],
            "author": r["author"], "author_name": r["author"], "authorName": r["author"],
            "content": r["content"], "created_at": r["created_at"], "createdAt": r["created_at"]}


def create_post(c, q, body, auth):
    content = str(pick(body, "content", "message", "text"))
    if not content:
        raise HttpError(400, "Nội dung trống")
    uid = auth["id"]
    author = str(pick(body, "user_name", "userName", "author", "author_name", "authorName"))
    if not author:
        u = c.execute("SELECT full_name FROM users WHERE id=?", (uid,)).fetchone()
        author = u["full_name"] if u else ""
    cur = c.execute("INSERT INTO posts(user_id,author,content,created_at) VALUES(?,?,?,?)",
                    (uid, author, content, now()))
    return 201, post_json(c.execute("SELECT * FROM posts WHERE id=?", (cur.lastrowid,)).fetchone())


def admin_create_court(c, q, body, auth):
    cur = c.execute("INSERT INTO courts(name,location,price_per_hour,description,image_url,hidden) VALUES(?,?,?,?,?,?)",
                    (body.get("name", ""), body.get("location", ""), body.get("price_per_hour", 0),
                     body.get("description", ""), body.get("image_url", ""), int(bool(body.get("hidden")))))
    for h in range(6, 22):
        c.execute("INSERT INTO slots(court_id,start_time,end_time) VALUES(?,?,?)",
                  (cur.lastrowid, "%02d:00" % h, "%02d:00" % (h + 1)))
    return 201, court_json(c, c.execute("SELECT * FROM courts WHERE id=?", (cur.lastrowid,)).fetchone())


def admin_update_court(c, q, body, auth, cid):
    r = c.execute("SELECT * FROM courts WHERE id=?", (cid,)).fetchone()
    if not r:
        raise HttpError(404, "Không tìm thấy sân")
    c.execute("UPDATE courts SET name=?,location=?,price_per_hour=?,description=?,image_url=?,hidden=? WHERE id=?",
              (body.get("name", r["name"]), body.get("location", r["location"]),
               body.get("price_per_hour", r["price_per_hour"]), body.get("description", r["description"]),
               body.get("image_url", r["image_url"]), int(body.get("hidden", r["hidden"])), cid))
    return 200, court_json(c, c.execute("SELECT * FROM courts WHERE id=?", (cid,)).fetchone())


def admin_visibility(c, q, body, auth, cid):
    hidden = q.get("hidden", ["false"])[0].lower() == "true"
    c.execute("UPDATE courts SET hidden=? WHERE id=?", (int(hidden), cid))
    return 200, court_json(c, c.execute("SELECT * FROM courts WHERE id=?", (cid,)).fetchone())


def admin_bookings(c, q, body, auth):
    expire_stale(c)
    st = q.get("status", [None])[0]
    rows = c.execute("SELECT * FROM bookings WHERE status=? ORDER BY id DESC", (st,)) if st \
        else c.execute("SELECT * FROM bookings ORDER BY id DESC")
    return 200, [booking_json(r) for r in rows]


def set_status(status):
    def h(c, q, body, auth, bid):
        r = c.execute("SELECT * FROM bookings WHERE id=?", (bid,)).fetchone()
        if not r:
            raise HttpError(404, "Không tìm thấy vé")
        if status == "success" and r["status"] != "pending":
            raise HttpError(409, "Vé không còn ở trạng thái chờ duyệt")
        c.execute("UPDATE bookings SET status=? WHERE id=?", (status, bid))
        return 200, booking_json(c.execute("SELECT * FROM bookings WHERE id=?", (bid,)).fetchone())
    return h


def admin_report(c, q, body, auth):
    out = io.StringIO()
    w = csv.writer(out)
    w.writerow(["Mã vé", "Sân", "Ngày", "Ca giờ", "Tổng tiền", "Trạng thái"])
    for r in c.execute("SELECT * FROM bookings ORDER BY id"):
        w.writerow([r["booking_code"], r["court_name"], r["date"], r["slots_text"], r["total_price"], r["status"]])
    return 200, ("\ufeff" + out.getvalue()).encode("utf-8"), "text/csv; charset=utf-8"


# ---------------- Thống kê cho Dashboard ----------------
def admin_stats_revenue(c, q, body, auth):
    expire_stale(c)
    period = q.get("period", ["week"])[0]
    today = datetime.now().date()
    items = []
    if period == "month":
        y, m = today.year, today.month
        day = 1
        week_no = 0
        while day <= 31:
            week_no += 1
            start, end = day, min(day + 6, 31)
            total = 0.0
            for d in range(start, end + 1):
                iso = "%04d-%02d-%02d" % (y, m, d)
                total += float(c.execute(
                    "SELECT COALESCE(SUM(total_price),0) FROM bookings WHERE date=? AND status='success'",
                    (iso,)).fetchone()[0] or 0)
            items.append({"label": "Tuần %d" % week_no, "value": total})
            day = end + 1
    elif period == "year":
        y = today.year
        for m in range(1, 13):
            total = float(c.execute(
                "SELECT COALESCE(SUM(total_price),0) FROM bookings WHERE date LIKE ? AND status='success'",
                ("%04d-%02d%%" % (y, m),)).fetchone()[0] or 0)
            items.append({"label": "T%d" % m, "value": total})
    else:  # week: 7 ngày gần nhất
        for i in range(6, -1, -1):
            d = today - timedelta(days=i)
            iso = d.strftime("%Y-%m-%d")
            total = float(c.execute(
                "SELECT COALESCE(SUM(total_price),0) FROM bookings WHERE date=? AND status='success'",
                (iso,)).fetchone()[0] or 0)
            items.append({"label": d.strftime("%d/%m"), "value": total})
    return 200, {"items": items}


def admin_stats_courts(c, q, body, auth):
    expire_stale(c)
    items = []
    for r in c.execute("SELECT * FROM courts ORDER BY id"):
        total = c.execute("SELECT COUNT(*) FROM bookings WHERE court_id=?", (r["id"],)).fetchone()[0]
        success = c.execute("SELECT COUNT(*) FROM bookings WHERE court_id=? AND status='success'",
                            (r["id"],)).fetchone()[0]
        rate = (success * 100.0 / total) if total else 0.0
        items.append({"label": r["name"], "value": round(rate, 1),
                      "extra": "%d/%d" % (success, total)})
    return 200, {"items": items}


# ---------------- Thanh toán VNPay (sandbox) ----------------
def vn_now():
    """Giờ Việt Nam GMT+7, không có giờ mùa hè."""
    return datetime.now(timezone(timedelta(hours=7)))


def vnpay_params_to_query(params):
    return "&".join("%s=%s" % (k, quote_plus(str(v)))
                    for k, v in sorted(params.items(), key=lambda kv: kv[0]))


def vnpay_sign(params):
    """Ký HMAC-SHA512 trên query string đã sắp xếp a-z và mã hóa URL."""
    return hmac.new(VNP_HASH_SECRET.encode("utf-8"),
                    vnpay_params_to_query(params).encode("utf-8"),
                    hashlib.sha512).hexdigest().upper()


def vnpay_verify(params, secure_hash):
    """Kiểm tra chữ ký trả về. Thử cả 2 cách: bỏ tham số rỗng (giống demo chính
    thức của VNPay) và giữ nguyên mọi tham số, để tương thích nhiều phiên bản."""
    if not secure_hash:
        return False
    filtered = {k: v for k, v in params.items()
                if k not in ("vnp_SecureHash", "vnp_SecureHashType") and v != ""}
    calc = vnpay_sign(filtered)
    if hmac.compare_digest(calc, secure_hash):
        return True
    calc_all = vnpay_sign({k: v for k, v in params.items()
                           if k not in ("vnp_SecureHash", "vnp_SecureHashType")})
    return hmac.compare_digest(calc_all, secure_hash)


def strip_vietnamese(text):
    s = unicodedata.normalize("NFD", text or "")
    s = s.encode("ascii", "ignore").decode("ascii")
    s = re.sub(r"[^0-9A-Za-z ]", " ", s)
    return re.sub(r"\s+", " ", s).strip()


def default_return_url(headers):
    host = headers.get("Host", "127.0.0.1:%d" % PORT)
    scheme = "https" if ".ngrok" in host or ".loca.lt" in host else "http"
    return "%s://%s/api/payments/vnpay/return" % (scheme, host)


def mask_vnpay_hash(url):
    """Che bớt vnp_SecureHash khi in log (không in toàn bộ chữ ký)."""
    marker = "vnp_SecureHash="
    idx = url.find(marker)
    if idx < 0:
        return url
    head = url[:idx + len(marker)]
    tail = url[idx + len(marker):].split("&")[0]
    masked = (tail[:6] + "..." + tail[-4:]) if len(tail) > 14 else "***"
    return head + masked


def vnpay_create(c, q, body, auth):
    if not VNP_TMN_CODE or not VNP_HASH_SECRET:
        raise HttpError(503, "Server chưa cấu hình thanh toán VNPay "
                             "(thiếu biến môi trường VNP_TMN_CODE / VNP_HASH_SECRET)")
    bid = int(body.get("booking_id") or 0)
    b = c.execute("SELECT * FROM bookings WHERE id=?", (bid,)).fetchone()
    if not b:
        raise HttpError(404, "Không tìm thấy đơn đặt sân")
    if auth["role"] != "admin" and b["user_id"] != auth["id"]:
        raise HttpError(403, "Đơn không thuộc tài khoản này")
    if b["status"] in ("cancelled", "expired"):
        raise HttpError(409, "Đơn đã hủy hoặc hết hạn, không thể thanh toán")
    if col(b, "payment_status", "UNPAID") == "PAID":
        raise HttpError(409, "Đơn đã được thanh toán")
    # Số tiền lấy từ DB, không tin số tiền app gửi lên
    amount_vnd = int(round(b["total_price"] or 0))
    if amount_vnd <= 0:
        raise HttpError(400, "Số tiền đơn không hợp lệ")

    now_dt = vn_now()
    create_date = now_dt.strftime("%Y%m%d%H%M%S")
    expire_date = (now_dt + timedelta(minutes=VNP_EXPIRE_MINUTES)).strftime("%Y%m%d%H%M%S")
    code = b["booking_code"] or ("BK%06d" % b["id"])
    txn_ref = re.sub(r"[^0-9A-Za-z]", "", "%s%s%06d" % (code, create_date, random.randint(0, 999999)))
    return_url = VNP_RETURN_URL or q.get("__return_url",
                                         ["http://127.0.0.1:%d/api/payments/vnpay/return" % PORT])[0]
    client_ip = q.get("__client_ip", ["127.0.0.1"])[0]

    params = {
        "vnp_Version": "2.1.0",
        "vnp_Command": "pay",
        "vnp_TmnCode": VNP_TMN_CODE,
        "vnp_Amount": str(amount_vnd * 100),
        "vnp_CurrCode": "VND",
        "vnp_TxnRef": txn_ref,
        "vnp_OrderInfo": strip_vietnamese("Thanh toan don %s" % code),
        "vnp_OrderType": "other",
        "vnp_Locale": "vn",
        "vnp_ReturnUrl": return_url,
        "vnp_IpAddr": client_ip,
        "vnp_CreateDate": create_date,
        "vnp_ExpireDate": expire_date,
    }
    secure = vnpay_sign(params)
    url = "%s?%s&vnp_SecureHash=%s" % (VNP_URL, vnpay_params_to_query(params), secure)
    # In URL ra console để dễ kiểm tra. Hash bị che bớt theo mặc định.
    print("[VNPAY] payment_url (da che vnp_SecureHash): %s" % mask_vnpay_hash(url))
    if os.environ.get("VNP_LOG_FULL_URL") == "1":
        print("[VNPAY] payment_url day du (chi de test tren trinh duyet, link het han sau %d phut): %s"
              % (VNP_EXPIRE_MINUTES, url))
    try:
        c.execute("INSERT INTO payments(booking_id,txn_ref,amount,status,created_at) VALUES(?,?,?,?,?)",
                  (bid, txn_ref, amount_vnd * 100, "PENDING", now()))
    except sqlite3.IntegrityError:
        raise HttpError(409, "Mã giao dịch bị trùng, vui lòng thử lại")
    c.execute("UPDATE bookings SET payment_status='PENDING' WHERE id=?", (bid,))
    print("[VNPAY] Tạo giao dịch %s cho đơn %s (%d VND), link hết hạn lúc %s"
          % (txn_ref, code, amount_vnd, expire_date))
    return 200, {"payment_url": url, "txn_ref": txn_ref}


def vnpay_process(c, q):
    """Xử lý chung cho trang return (web) và IPN (server-to-server).
    Trả về dict: {ok, code, msg, already}."""
    params = {k: (v[0] if isinstance(v, list) else v)
              for k, v in q.items() if k.startswith("vnp_")}
    txn_ref = params.get("vnp_TxnRef", "")
    pay = c.execute("SELECT * FROM payments WHERE txn_ref=?", (txn_ref,)).fetchone() if txn_ref else None
    if not pay:
        return {"ok": False, "code": "01", "msg": "Không tìm thấy giao dịch", "already": False}
    if pay["status"] == "PAID":
        return {"ok": True, "code": "02", "msg": "Giao dịch đã được xác nhận trước đó", "already": True}

    secure_hash = q.get("vnp_SecureHash", [""])[0]
    if not vnpay_verify(params, secure_hash):
        print("[VNPAY] Chữ ký không hợp lệ cho giao dịch %s" % txn_ref)
        return {"ok": False, "code": "97", "msg": "Chữ ký không hợp lệ", "already": False}

    amount = params.get("vnp_Amount", "")
    if str(pay["amount"]) != str(amount):
        c.execute("UPDATE payments SET status='FAILED', vnp_response_code=? WHERE id=?",
                  (params.get("vnp_ResponseCode", ""), pay["id"]))
        c.execute("UPDATE bookings SET payment_status='FAILED' WHERE id=?", (pay["booking_id"],))
        print("[VNPAY] Sai số tiền txn %s: mong đợi %s, nhận %s" % (txn_ref, pay["amount"], amount))
        return {"ok": False, "code": "04", "msg": "Số tiền không khớp", "already": False}

    resp_code = params.get("vnp_ResponseCode", "")
    txn_status = params.get("vnp_TransactionStatus", "")
    if resp_code == "00" and txn_status in ("", "00"):
        # Chỉ cập nhật một lần; WHERE status!='PAID' chống xử lý trùng lặp
        c.execute("UPDATE payments SET status='PAID', vnp_transaction_no=?, vnp_response_code=?, paid_at=? "
                  "WHERE id=? AND status!='PAID'",
                  (params.get("vnp_TransactionNo", ""), resp_code, now(), pay["id"]))
        c.execute("UPDATE bookings SET payment_status='PAID' WHERE id=?", (pay["booking_id"],))
        print("[VNPAY] Thanh toán thành công txn %s cho đơn %s" % (txn_ref, pay["booking_id"]))
        return {"ok": True, "code": "00", "msg": "Confirm Success", "already": False}
    c.execute("UPDATE payments SET status='FAILED', vnp_transaction_no=?, vnp_response_code=? "
              "WHERE id=? AND status!='PAID'",
              (params.get("vnp_TransactionNo", ""), resp_code, pay["id"]))
    c.execute("UPDATE bookings SET payment_status='FAILED' WHERE id=?", (pay["booking_id"],))
    print("[VNPAY] Thanh toán thất bại txn %s, vnp_ResponseCode=%s" % (txn_ref, resp_code))
    return {"ok": False, "code": "00", "msg": "Đã ghi nhận giao dịch thất bại", "already": False}


def _html_result(title, color, icon, detail):
    return ("<!DOCTYPE html><html lang='vi'><head><meta charset='utf-8'>"
            "<meta name='viewport' content='width=device-width, initial-scale=1'>"
            "<title>%s</title></head>"
            "<body style='font-family:sans-serif;background:#F5F7F5;margin:0;display:flex;"
            "align-items:center;justify-content:center;min-height:100vh'>"
            "<div style='text-align:center;background:#fff;padding:32px 40px;border-radius:16px;"
            "box-shadow:0 2px 8px rgba(0,0,0,.15);max-width:90%%'>"
            "<div style='font-size:48px'>%s</div>"
            "<h2 style='color:%s;margin:8px 0'>%s</h2>"
            "<p style='color:#555'>%s</p>"
            "</div></body></html>" % (title, icon, color, title, detail))


def vnpay_return(c, q, body, auth):
    res = vnpay_process(c, q)
    if res["ok"]:
        html = _html_result("Thanh toán thành công", "#2E7D32", "&#9989;",
                            "Bạn có thể đóng cửa sổ này và quay lại ứng dụng.")
    else:
        reason = {"97": "Chữ ký xác thực không hợp lệ.",
                  "04": "Số tiền không khớp với đơn hàng.",
                  "01": "Không tìm thấy giao dịch."}.get(res["code"], "Giao dịch không thành công.")
        html = _html_result("Thanh toán thất bại", "#C62828", "&#10060;", reason)
    return 200, html.encode("utf-8"), "text/html; charset=utf-8"


def vnpay_ipn(c, q, body, auth):
    res = vnpay_process(c, q)
    if res["already"]:
        return 200, {"RspCode": "02", "Message": "Order already confirmed"}
    if res["ok"] or res["code"] == "00":
        # Thành công, hoặc đã ghi nhận giao dịch thất bại -> xác nhận để VNPay không gửi lại
        return 200, {"RspCode": "00", "Message": "Confirm Success"}
    return 200, {"RspCode": res["code"], "Message": res["msg"]}


def payment_status(c, q, body, auth):
    bid = int(q.get("booking_id", ["0"])[0] or 0)
    b = c.execute("SELECT * FROM bookings WHERE id=?", (bid,)).fetchone()
    if not b:
        raise HttpError(404, "Không tìm thấy đơn đặt sân")
    if auth["role"] != "admin" and b["user_id"] != auth["id"]:
        raise HttpError(403, "Không có quyền xem đơn này")
    return 200, {"booking_id": bid, "payment_status": col(b, "payment_status", "UNPAID")}


# (method, pattern, handler, cần đăng nhập, chỉ dành cho admin)
ROUTES = [
    ("POST", r"auth/login", login, False, False),
    ("POST", r"auth/register", register, False, False),
    ("POST", r"auth/send-otp", send_otp, False, False),
    ("PATCH", r"users/(\d+)", update_user, True, False),
    ("POST", r"users/(\d+)/avatar", update_avatar, True, False),
    ("GET", r"courts", list_courts, False, False),
    ("GET", r"courts/(\d+)", court_detail, False, False),
    ("GET", r"courts/(\d+)/slots", court_slots, False, False),
    ("POST", r"bookings", create_booking, True, False),
    ("GET", r"bookings/(\d+)", get_booking, False, False),
    ("GET", r"users/(\d+)/bookings", user_bookings, True, False),
    ("GET", r"posts", list_posts, False, False),
    ("POST", r"posts", create_post, True, False),
    ("POST", r"admin/courts", admin_create_court, True, True),
    ("PUT", r"admin/courts/(\d+)", admin_update_court, True, True),
    ("PATCH", r"admin/courts/(\d+)/visibility", admin_visibility, True, True),
    ("GET", r"admin/bookings", admin_bookings, True, True),
    ("PATCH", r"admin/bookings/(\d+)/approve", set_status("success"), True, True),
    ("PATCH", r"admin/bookings/(\d+)/cancel", set_status("cancelled"), True, True),
    ("GET", r"admin/report", admin_report, True, True),
    ("GET", r"admin/stats/revenue", admin_stats_revenue, True, True),
    ("GET", r"admin/stats/courts", admin_stats_courts, True, True),
    ("POST", r"payments/vnpay/create", vnpay_create, True, False),
    ("GET", r"payments/vnpay/return", vnpay_return, False, False),
    ("GET", r"payments/vnpay/ipn", vnpay_ipn, False, False),
    ("GET", r"payments/status", payment_status, True, False),
]


class Handler(BaseHTTPRequestHandler):
    def _handle(self, method):
        u = urlparse(self.path)
        path = u.path.strip("/")
        if path.startswith("api/"):
            path = path[4:]
        q = parse_qs(u.query)
        # Cung cấp thông tin request cho handler VNPay (IP client + return URL mặc định)
        q["__client_ip"] = [self.client_address[0] if self.client_address else "127.0.0.1"]
        q["__return_url"] = [default_return_url(self.headers)]

        # Ảnh đại diện được phục vụ trực tiếp từ thư mục avatars/
        if method == "GET" and path.startswith("uploads/avatars/"):
            return self._serve_avatar(path[len("uploads/avatars/"):])

        auth_header = self.headers.get("Authorization", "")
        token = auth_header[7:].strip() if auth_header.lower().startswith("bearer ") else ""

        length = int(self.headers.get("Content-Length") or 0)
        raw = self.rfile.read(length) if length else b""
        try:
            body = json.loads(raw.decode("utf-8")) if raw else {}
        except ValueError:
            return self._send(400, {"message": "JSON không hợp lệ"})
        for m, pat, fn, need_auth, need_admin in ROUTES:
            mt = re.fullmatch(pat, path)
            if m == method and mt:
                with LOCK:
                    c = conn()
                    try:
                        auth = None
                        if token:
                            row = c.execute(
                                "SELECT u.id, u.role, t.expires_at FROM tokens t "
                                "JOIN users u ON u.id = t.user_id WHERE t.token=?", (token,)).fetchone()
                            if row and row["expires_at"] > time.time():
                                auth = {"id": row["id"], "role": row["role"]}
                        if need_auth and auth is None:
                            raise HttpError(401, "Cần đăng nhập để thực hiện thao tác này")
                        if need_admin and (auth is None or auth["role"] != "admin"):
                            raise HttpError(403, "Chỉ quản trị viên mới được dùng chức năng này")
                        res = fn(c, q, body, auth, *[int(g) for g in mt.groups()])
                        c.commit()
                    except HttpError as e:
                        c.rollback()
                        return self._send(e.code, {"message": e.msg})
                    except Exception as e:  # noqa
                        c.rollback()
                        return self._send(500, {"message": str(e)})
                    finally:
                        c.close()
                return self._send(*res)
        self._send(404, {"message": "Không có endpoint " + method + " /" + path})

    def _serve_avatar(self, fname):
        fname = os.path.basename(fname)
        fpath = os.path.join(AVATAR_DIR, fname)
        if not os.path.isfile(fpath):
            return self._send(404, {"message": "Không tìm thấy ảnh"})
        with open(fpath, "rb") as f:
            data = f.read()
        return self._send(200, data, "image/jpeg")

    def _send(self, code, data, ctype="application/json; charset=utf-8"):
        payload = data if isinstance(data, bytes) else json.dumps(data, ensure_ascii=False).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

    def do_GET(self): self._handle("GET")
    def do_POST(self): self._handle("POST")
    def do_PUT(self): self._handle("PUT")
    def do_PATCH(self): self._handle("PATCH")

    def log_message(self, fmt, *args):
        print("%s %s" % (self.command, self.path), "->", args[1] if len(args) > 1 else "")


if __name__ == "__main__":
    init_db()
    print("Server chạy tại http://0.0.0.0:%d  (emulator dùng http://10.0.2.2:%d/api/)" % (PORT, PORT))
    print("Tài khoản mẫu: admin / admin123   |   2021600001 / 123456")
    if not VNP_TMN_CODE or not VNP_HASH_SECRET:
        print("[VNPAY] CẢNH BÁO: chưa đặt biến môi trường VNP_TMN_CODE / VNP_HASH_SECRET, "
              "thanh toán VNPay sẽ báo lỗi cấu hình (HTTP 503).")
    else:
        print("[VNPAY] Đã nạp cấu hình VNPay cho website %s" % VNP_TMN_CODE)
    ThreadingHTTPServer(("0.0.0.0", PORT), Handler).serve_forever()
