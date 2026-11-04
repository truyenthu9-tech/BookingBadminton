# -*- coding: utf-8 -*-
"""
Backend cho app đặt sân cầu lông (khớp BadmintonApiService).
Chỉ dùng thư viện chuẩn của Python 3.8+, database là file badminton.db (SQLite).
Chạy:  python server.py      (mặc định cổng 8000)
"""
import csv, io, json, os, re, sqlite3, threading
from datetime import datetime
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse, parse_qs

DB = os.path.join(os.path.dirname(os.path.abspath(__file__)), "badminton.db")
PORT = int(os.environ.get("PORT", 8000))
LOCK = threading.Lock()


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
        password TEXT NOT NULL,
        role TEXT DEFAULT 'student');
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
    CREATE TABLE IF NOT EXISTS posts(
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        user_id INTEGER, author TEXT, content TEXT, created_at TEXT);
    """)
    if c.execute("SELECT COUNT(*) FROM users").fetchone()[0] == 0:
        c.execute("INSERT INTO users(student_id,full_name,email,password,role) VALUES(?,?,?,?,?)",
                  ("admin", "Quản trị viên", "admin@haui.edu.vn", "admin123", "admin"))
        c.execute("INSERT INTO users(student_id,full_name,email,password,role) VALUES(?,?,?,?,?)",
                  ("2021600001", "Nguyễn Văn A", "a@haui.edu.vn", "123456", "student"))
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


def booked_slot_ids(c, court_id, date):
    ids = set()
    for r in c.execute("SELECT slot_ids FROM bookings WHERE court_id=? AND date=? AND status!='cancelled'",
                       (court_id, date)):
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
            "qr_code": r["qr_code"], "valid_until": r["valid_until"]}


def user_json(r):
    # Trả nhiều tên khóa tương đương để khớp với User.java (Gson bỏ qua khóa thừa)
    return {"id": r["id"], "user_id": r["id"], "userId": r["id"],
            "student_id": r["student_id"], "studentId": r["student_id"],
            "username": r["student_id"], "code": r["student_id"],
            "full_name": r["full_name"], "fullName": r["full_name"], "name": r["full_name"],
            "email": r["email"], "role": r["role"], "is_admin": r["role"] == "admin",
            "isAdmin": r["role"] == "admin"}


def pick(d, *keys, default=""):
    for k in keys:
        if d.get(k) not in (None, ""):
            return d[k]
    return default


class HttpError(Exception):
    def __init__(self, code, msg):
        self.code, self.msg = code, msg


# ---------------- handlers ----------------
def login(c, q, body):
    sid = str(pick(body, "student_id", "studentId", "username", "code", "id_number", "mssv", "user_id"))
    pw = str(pick(body, "password", "pass"))
    r = c.execute("SELECT * FROM users WHERE student_id=? AND password=?", (sid, pw)).fetchone()
    if not r:
        raise HttpError(401, "Sai mã sinh viên hoặc mật khẩu")
    return 200, user_json(r)


def register(c, q, body):
    sid = str(pick(body, "student_id", "studentId", "username", "code", "id_number", "mssv"))
    name = str(pick(body, "full_name", "fullName", "name"))
    pw = str(pick(body, "password", "pass"))
    if not sid or not name or not pw:
        raise HttpError(400, "Thiếu thông tin đăng ký")
    if c.execute("SELECT 1 FROM users WHERE student_id=?", (sid,)).fetchone():
        raise HttpError(409, "Mã sinh viên đã tồn tại")
    cur = c.execute("INSERT INTO users(student_id,full_name,email,password) VALUES(?,?,?,?)",
                    (sid, name, str(pick(body, "email")), pw))
    return 201, user_json(c.execute("SELECT * FROM users WHERE id=?", (cur.lastrowid,)).fetchone())


def list_courts(c, q, body):
    date = q.get("date", [None])[0]
    return 200, [court_json(c, r, date) for r in c.execute("SELECT * FROM courts ORDER BY id")]


def court_detail(c, q, body, cid):
    r = c.execute("SELECT * FROM courts WHERE id=?", (cid,)).fetchone()
    if not r:
        raise HttpError(404, "Không tìm thấy sân")
    return 200, court_json(c, r)


def court_slots(c, q, body, cid):
    date = q.get("date", [datetime.now().strftime("%Y-%m-%d")])[0]
    used = booked_slot_ids(c, cid, date)
    return 200, [{"id": s["id"], "court_id": s["court_id"], "start_time": s["start_time"],
                  "end_time": s["end_time"], "booked": s["id"] in used}
                 for s in c.execute("SELECT * FROM slots WHERE court_id=? ORDER BY start_time", (cid,))]


def create_booking(c, q, body):
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
    text = ", ".join("%s - %s" % (s["start_time"], s["end_time"]) for s in slots)
    start, end = slots[0]["start_time"], slots[-1]["end_time"]
    cur = c.execute(
        "INSERT INTO bookings(user_id,court_id,court_name,date,slot_ids,slots_text,start_time,end_time,"
        "total_price,status,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
        (body.get("user_id") or 0, court["id"], court["name"], date, json.dumps(ids), text, start, end,
         court["price_per_hour"] * len(ids), "pending", now()))
    code = "BK%06d" % cur.lastrowid
    c.execute("UPDATE bookings SET booking_code=?, qr_code=?, valid_until=? WHERE id=?",
              (code, code, "%s %s" % (end, date), cur.lastrowid))
    return 201, booking_json(c.execute("SELECT * FROM bookings WHERE id=?", (cur.lastrowid,)).fetchone())


def get_booking(c, q, body, bid):
    r = c.execute("SELECT * FROM bookings WHERE id=?", (bid,)).fetchone()
    if not r:
        raise HttpError(404, "Không tìm thấy vé")
    return 200, booking_json(r)


def user_bookings(c, q, body, uid):
    return 200, [booking_json(r) for r in
                 c.execute("SELECT * FROM bookings WHERE user_id=? ORDER BY id DESC", (uid,))]


def list_posts(c, q, body):
    return 200, [post_json(r) for r in c.execute("SELECT * FROM posts ORDER BY id DESC")]


def post_json(r):
    return {"id": r["id"], "user_id": r["user_id"], "user_name": r["author"], "userName": r["author"],
            "author": r["author"], "author_name": r["author"], "authorName": r["author"],
            "content": r["content"], "created_at": r["created_at"], "createdAt": r["created_at"]}


def create_post(c, q, body):
    content = str(pick(body, "content", "message", "text"))
    if not content:
        raise HttpError(400, "Nội dung trống")
    uid = pick(body, "user_id", "userId", default=0)
    author = str(pick(body, "user_name", "userName", "author", "author_name", "authorName"))
    if not author and uid:
        u = c.execute("SELECT full_name FROM users WHERE id=?", (uid,)).fetchone()
        author = u["full_name"] if u else ""
    cur = c.execute("INSERT INTO posts(user_id,author,content,created_at) VALUES(?,?,?,?)",
                    (uid, author, content, now()))
    return 201, post_json(c.execute("SELECT * FROM posts WHERE id=?", (cur.lastrowid,)).fetchone())


def admin_create_court(c, q, body):
    cur = c.execute("INSERT INTO courts(name,location,price_per_hour,description,image_url,hidden) VALUES(?,?,?,?,?,?)",
                    (body.get("name", ""), body.get("location", ""), body.get("price_per_hour", 0),
                     body.get("description", ""), body.get("image_url", ""), int(bool(body.get("hidden")))))
    for h in range(6, 22):
        c.execute("INSERT INTO slots(court_id,start_time,end_time) VALUES(?,?,?)",
                  (cur.lastrowid, "%02d:00" % h, "%02d:00" % (h + 1)))
    return 201, court_json(c, c.execute("SELECT * FROM courts WHERE id=?", (cur.lastrowid,)).fetchone())


def admin_update_court(c, q, body, cid):
    r = c.execute("SELECT * FROM courts WHERE id=?", (cid,)).fetchone()
    if not r:
        raise HttpError(404, "Không tìm thấy sân")
    c.execute("UPDATE courts SET name=?,location=?,price_per_hour=?,description=?,image_url=?,hidden=? WHERE id=?",
              (body.get("name", r["name"]), body.get("location", r["location"]),
               body.get("price_per_hour", r["price_per_hour"]), body.get("description", r["description"]),
               body.get("image_url", r["image_url"]), int(body.get("hidden", r["hidden"])), cid))
    return 200, court_json(c, c.execute("SELECT * FROM courts WHERE id=?", (cid,)).fetchone())


def admin_visibility(c, q, body, cid):
    hidden = q.get("hidden", ["false"])[0].lower() == "true"
    c.execute("UPDATE courts SET hidden=? WHERE id=?", (int(hidden), cid))
    return 200, court_json(c, c.execute("SELECT * FROM courts WHERE id=?", (cid,)).fetchone())


def admin_bookings(c, q, body):
    st = q.get("status", [None])[0]
    rows = c.execute("SELECT * FROM bookings WHERE status=? ORDER BY id DESC", (st,)) if st \
        else c.execute("SELECT * FROM bookings ORDER BY id DESC")
    return 200, [booking_json(r) for r in rows]


def set_status(status):
    def h(c, q, body, bid):
        if not c.execute("SELECT 1 FROM bookings WHERE id=?", (bid,)).fetchone():
            raise HttpError(404, "Không tìm thấy vé")
        c.execute("UPDATE bookings SET status=? WHERE id=?", (status, bid))
        return 200, booking_json(c.execute("SELECT * FROM bookings WHERE id=?", (bid,)).fetchone())
    return h


def admin_report(c, q, body):
    out = io.StringIO()
    w = csv.writer(out)
    w.writerow(["Mã vé", "Sân", "Ngày", "Ca giờ", "Tổng tiền", "Trạng thái"])
    for r in c.execute("SELECT * FROM bookings ORDER BY id"):
        w.writerow([r["booking_code"], r["court_name"], r["date"], r["slots_text"], r["total_price"], r["status"]])
    return 200, ("\ufeff" + out.getvalue()).encode("utf-8"), "text/csv; charset=utf-8"


ROUTES = [
    ("POST", r"auth/login", login),
    ("POST", r"auth/register", register),
    ("GET", r"courts", list_courts),
    ("GET", r"courts/(\d+)", court_detail),
    ("GET", r"courts/(\d+)/slots", court_slots),
    ("POST", r"bookings", create_booking),
    ("GET", r"bookings/(\d+)", get_booking),
    ("GET", r"users/(\d+)/bookings", user_bookings),
    ("GET", r"posts", list_posts),
    ("POST", r"posts", create_post),
    ("POST", r"admin/courts", admin_create_court),
    ("PUT", r"admin/courts/(\d+)", admin_update_court),
    ("PATCH", r"admin/courts/(\d+)/visibility", admin_visibility),
    ("GET", r"admin/bookings", admin_bookings),
    ("PATCH", r"admin/bookings/(\d+)/approve", set_status("success")),
    ("PATCH", r"admin/bookings/(\d+)/cancel", set_status("cancelled")),
    ("GET", r"admin/report", admin_report),
]


class Handler(BaseHTTPRequestHandler):
    def _handle(self, method):
        u = urlparse(self.path)
        path = u.path.strip("/")
        if path.startswith("api/"):
            path = path[4:]
        q = parse_qs(u.query)
        length = int(self.headers.get("Content-Length") or 0)
        raw = self.rfile.read(length) if length else b""
        try:
            body = json.loads(raw.decode("utf-8")) if raw else {}
        except ValueError:
            return self._send(400, {"message": "JSON không hợp lệ"})
        for m, pat, fn in ROUTES:
            mt = re.fullmatch(pat, path)
            if m == method and mt:
                with LOCK:
                    c = conn()
                    try:
                        res = fn(c, q, body, *[int(g) for g in mt.groups()])
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
    ThreadingHTTPServer(("0.0.0.0", PORT), Handler).serve_forever()
