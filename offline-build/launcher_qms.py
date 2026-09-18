# -*- coding: utf-8 -*-
"""
清源 QMS 品控工作台 单机离线版启动器（带守护自愈）
职责：
  1. 首次启动自动初始化包内便携 MariaDB 数据目录（一次性，约 10 秒）
  2. 依次拉起 MariaDB(13306) -> Redis(16379) -> Spring Boot(18080)
  3. 健康检查通过后自动打开浏览器 http://localhost:18080
  4. 守护自愈：运行期周期探测三组件
       - Redis 端口消失/进程退出      -> 自动重启 Redis
       - MariaDB ping 失败/进程退出  -> 自动重启 MariaDB，并连带重启后端
       - 后端进程退出，或健康检查连续 N 次失败 -> 自动重启后端
     滚动 1 小时内自愈超过上限才放弃，避免故障时无限重启
  5. 启动失败时分段输出明确中文原因（端口占用 / 数据库未就绪 / 后端账号密码或配置错误）
  6. 所有子进程加入 Windows 作业对象：关闭本黑窗（或启动器被杀）时，
     操作系统保证数据库/Redis/Java 进程树一并终止，绝不残留
仅使用标准库，PyInstaller onefile 后体积小、无第三方依赖。
"""
import ctypes
import json
import os
import signal
import socket
import subprocess
import sys
import threading
import time
import urllib.error
import urllib.request
import webbrowser
from collections import deque
from ctypes import wintypes

APP_PORT = 18080
DB_PORT = 13306
REDIS_PORT = 16379
URL = "http://localhost:%d" % APP_PORT
# Actuator 健康端点包含 db / redis 分项，用于启动就绪判定与诊断快照
HEALTH_URL = URL + "/actuator/health"
# 业务级探针（真实借 Hikari 连接执行 SELECT 1 + Redis PING），
# 专用于识别“进程活着、health 偶发正常、但业务请求全部转圈”的假死
DIAG_URL = URL + "/api/v1/diag/ping"
THREADDUMP_URL = URL + "/api/v1/diag/threaddump"

# ----------------------------- 路径 -----------------------------
if getattr(sys, "frozen", False):
    PKG_ROOT = os.path.dirname(os.path.abspath(sys.executable))
else:
    PKG_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

JRE_JAVA = os.path.join(PKG_ROOT, "runtime", "jre", "bin", "java.exe")
MARIADB_DIR = os.path.join(PKG_ROOT, "runtime", "mariadb")
MARIADBD = os.path.join(MARIADB_DIR, "bin", "mariadbd.exe")
MARIADB_INSTALL_DB = os.path.join(MARIADB_DIR, "bin", "mariadb-install-db.exe")
MARIADB_CLIENT = os.path.join(MARIADB_DIR, "bin", "mariadb.exe")
MARIADB_ADMIN = os.path.join(MARIADB_DIR, "bin", "mariadb-admin.exe")
REDIS_SERVER = os.path.join(PKG_ROOT, "runtime", "redis", "redis-server.exe")
APP_JAR = os.path.join(PKG_ROOT, "app", "qingyuan-qms-backend.jar")
STATIC_DIR = os.path.join(PKG_ROOT, "app", "static")

DATA_DIR = os.path.join(PKG_ROOT, "data")
DB_DATA_DIR = os.path.join(DATA_DIR, "mysql")
REDIS_DATA_DIR = os.path.join(DATA_DIR, "redis")
ATTACH_DIR = os.path.join(DATA_DIR, "attachments")
LOG_DIR = os.path.join(PKG_ROOT, "logs")
MY_INI = os.path.join(DATA_DIR, "my.ini")
# 后端 JVM 标准输出/错误落盘：启动失败时可直接看到 JVM 致命错误/堆栈
BACKEND_STDOUT = os.path.join(LOG_DIR, "backend-stdout.log")
# 启动器自身日志：无黑窗的 App 模式下排障全靠它
LAUNCHER_LOG = os.path.join(LOG_DIR, "launcher.log")

CREATE_NO_WINDOW = 0x08000000
CREATE_NEW_PROCESS_GROUP = 0x00000200

# 守护自愈参数（v1.2.1：10 秒一探、连续 3 次约 30 秒判假死，
# 配合业务探针 + 假死现场快照，替代旧版 15 秒 ×4 的单一 actuator 判定）
WATCH_INTERVAL = 10
HEALTH_FAIL_LIMIT = 3
RESTART_LIMIT_PER_HOUR = 8   # 滚动 1 小时最多自愈次数
STARTUP_WAIT_BACKEND = 150   # 后端首次启动最长等待秒数

# 运行期组件进程柄：role -> subprocess.Popen
procs = {}
# 最近自愈时间戳（滚动窗口限流）
restart_history = deque()
shutdown_flag = threading.Event()
# 串行化一切自愈动作，避免看门狗与 GUI 手动重启并发互相打架
restart_lock = threading.Lock()
# 最近探测记录（假死快照用）：(时间戳, 是否健康, 详情)
probe_timeline = deque(maxlen=40)
# GUI 订阅钩子：每条日志/状态变化同步一份给界面层（无界面时为 None）
_status_hook = None
# 启动器日志文件句柄（prepare 后才有 LOG_DIR）
_log_fp = None


def set_status_hook(fn):
    """GUI 注入日志回调（签名 fn(str)），供无黑窗模式展示进度。"""
    global _status_hook
    _status_hook = fn


def log(msg):
    line = "[%s] %s" % (time.strftime("%H:%M:%S"), msg)
    try:
        print(line, flush=True)
    except Exception:
        pass  # windowed 打包后 sys.stdout 为 None
    try:
        if _log_fp is not None:
            _log_fp.write(line + "\n")
            _log_fp.flush()
    except Exception:
        pass
    try:
        if _status_hook is not None:
            _status_hook(line)
    except Exception:
        pass


def set_console_title(title):
    try:
        ctypes.windll.kernel32.SetConsoleTitleW(title)
    except Exception:
        pass


def disable_quick_edit():
    """
    禁用控制台“快速编辑模式”。
    用户误点黑窗进入文本选择状态后，控制台输出会被系统挂起，
    容易被误认为“程序卡死”；直接关闭该能力避免误操作。
    """
    try:
        STD_INPUT_HANDLE = -10
        ENABLE_EXTENDED_FLAGS = 0x0080
        ENABLE_QUICK_EDIT_MODE = 0x0040
        k32 = ctypes.windll.kernel32
        h = k32.GetStdHandle(STD_INPUT_HANDLE)
        mode = wintypes.DWORD()
        if k32.GetConsoleMode(h, ctypes.byref(mode)):
            new_mode = (mode.value & ~ENABLE_QUICK_EDIT_MODE) | ENABLE_EXTENDED_FLAGS
            k32.SetConsoleMode(h, new_mode)
    except Exception:
        pass


# 控制台关闭/注销/关机事件回调函数原型（Windows 仅给约 5 秒处理时间，动作必须快）
_PHANDLER_ROUTINE = ctypes.WINFUNCTYPE(wintypes.BOOL, wintypes.DWORD)
CTRL_CLOSE_EVENT = 2
CTRL_LOGOFF_EVENT = 5
CTRL_SHUTDOWN_EVENT = 6


def _quick_redis_shutdown():
    try:
        subprocess.run([REDIS_SERVER.replace("redis-server.exe", "redis-cli.exe"),
                        "-p", str(REDIS_PORT), "shutdown", "save"],
                       stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
                       creationflags=CREATE_NO_WINDOW, timeout=3)
    except Exception:
        pass
    wait_port_closed(REDIS_PORT, 4, "Redis")


def _quick_mariadb_shutdown():
    try:
        subprocess.run([MARIADB_ADMIN, "--protocol=TCP", "-h", "127.0.0.1", "-P",
                        str(DB_PORT), "-uroot", "shutdown"],
                       stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
                       creationflags=CREATE_NO_WINDOW, timeout=5)
    except Exception:
        pass
    wait_port_closed(DB_PORT, 6, "MariaDB")


def _console_ctrl_handler(ctrl):
    # 用户点黑窗 X、注销或关机时触发：抢在 Job Object 强杀前优雅关停数据库，
    # 杜绝 InnoDB 每次启动都执行 crash recovery（数据虽无损，但应尽量干净关闭）
    if ctrl in (CTRL_CLOSE_EVENT, CTRL_LOGOFF_EVENT, CTRL_SHUTDOWN_EVENT):
        try:
            shutdown_flag.set()
            _quick_redis_shutdown()
            _quick_mariadb_shutdown()
        except Exception:
            pass
        return True
    return False


# 回调对象必须保持全局引用，防止被 Python GC 回收后 C 侧回调崩溃
_console_ctrl_handler_ref = _PHANDLER_ROUTINE(_console_ctrl_handler)


def install_close_handler():
    try:
        kernel32.SetConsoleCtrlHandler(_console_ctrl_handler_ref, True)
    except Exception:
        pass


def alert_box(text, title):
    try:
        ctypes.windll.user32.MessageBoxW(0, text, title, 0x10)
    except Exception:
        log(text)


def port_open(port, host="127.0.0.1"):
    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    try:
        s.settimeout(0.5)
        return s.connect_ex((host, port)) == 0
    finally:
        s.close()


def wait_port(port, timeout, label):
    deadline = time.time() + timeout
    while time.time() < deadline:
        if port_open(port):
            return True
        time.sleep(0.7)
    log("[等待] %s 端口 %d 未就绪" % (label, port))
    return False


def wait_port_closed(port, timeout, label=""):
    deadline = time.time() + timeout
    while time.time() < deadline:
        if not port_open(port):
            return True
        time.sleep(0.5)
    log("[等待] %s 端口 %d 长时间未释放" % (label, port))
    return False


def http_get(url, timeout=3):
    """返回 (是否2xx, 状态码, 文本)"""
    try:
        with urllib.request.urlopen(url, timeout=timeout) as resp:
            return 200 <= resp.status < 300, resp.status, resp.read().decode("utf-8", "ignore")
    except urllib.error.HTTPError as e:
        try:
            body = e.read().decode("utf-8", "ignore")
        except Exception:
            body = ""
        return False, e.code, body
    except Exception:
        return False, 0, ""


def backend_healthy():
    """查询 Actuator 健康端点，status=UP 视为健康（启动就绪判定用）"""
    ok, _, body = http_get(HEALTH_URL, timeout=4)
    if ok and body:
        try:
            return json.loads(body).get("status") == "UP"
        except Exception:
            return False
    return False


def backend_business_probe():
    """
    业务级存活探针，返回 (是否健康, 详情)。
    走完整 Tomcat 工作线程 + MVC + 过滤器链，并真实执行 SELECT 1 与 Redis PING：
    只要连接池耗尽/工作线程被占住/DB 或 Redis 异常，立即反映为失败。
    旧版 jar（无 /diag 端点，返回 404）自动回落到 actuator，避免升级期错杀。
    """
    ok, code, body = http_get(DIAG_URL, timeout=5)
    if ok and body:
        try:
            data = json.loads(body)
            if data.get("code") == "0":
                d = data.get("data") or {}
                db_up = (d.get("db") or {}).get("status") == "UP"
                redis_up = (d.get("redis") or {}).get("status") == "UP"
                if db_up and redis_up:
                    return True, "db/redis UP"
                return False, "diag 分项异常: %s" % body[:200]
            return False, "diag code=%s: %s" % (data.get("code"), body[:200])
        except Exception:
            return False, "diag 响应无法解析: %s" % body[:200]
    if code == 404:
        if backend_healthy():
            return True, "actuator UP（旧版 jar 回落判定）"
        return False, "diag 404 且 actuator 不通过"
    return False, "diag HTTP=%s body=%s" % (code, (body or "")[:160])


def freeze_snapshot(reason):
    """
    判定假死的瞬间固化现场到 logs/freeze-时间戳/：
    探针时间线 + JVM 线程栈 + actuator 明细 + 后端日志尾部，
    让“半小时后偶发一次”的假死也能事后定位，而不是只能靠猜。
    """
    snap_dir = os.path.join(LOG_DIR, "freeze-" + time.strftime("%Y%m%d-%H%M%S"))
    try:
        os.makedirs(snap_dir, exist_ok=True)
        with open(os.path.join(snap_dir, "probe.txt"), "w", encoding="utf-8") as f:
            f.write("reason: %s\n" % reason)
            for ts, healthy, detail in probe_timeline:
                f.write("%s  %s  %s\n" % (time.strftime("%H:%M:%S", time.localtime(ts)),
                                          "UP  " if healthy else "DOWN", detail))
        # 线程栈最关键：假死时本请求可能也超时，拿不到时也要记录“拿不到”这一事实
        ok, td_code, dump = http_get(THREADDUMP_URL, timeout=5)
        with open(os.path.join(snap_dir, "threaddump.txt"), "w", encoding="utf-8") as f:
            if ok and dump:
                f.write(dump)
            else:
                f.write("threaddump unavailable: HTTP=%s（线程栈端点同样无响应，"
                        "说明进程存活但已无法处理请求，是强假死信号）\n" % td_code)
        _, _, health_body = http_get(HEALTH_URL, timeout=4)
        with open(os.path.join(snap_dir, "health.txt"), "w", encoding="utf-8") as f:
            f.write(health_body or "(health endpoint no response)")
        for src, name, lines in ((os.path.join(LOG_DIR, "backend.log"), "backend-tail.log", 200),
                                 (BACKEND_STDOUT, "backend-stdout-tail.log", 100)):
            tail = tail_file(src, lines)
            if tail:
                with open(os.path.join(snap_dir, name), "w", encoding="utf-8") as f:
                    f.write("\n".join(tail))
        log("[守护] 假死现场已保存：%s" % os.path.relpath(snap_dir, PKG_ROOT))
    except Exception as e:
        log("[守护] 保存假死现场失败（不影响自愈）: %s" % e)
    return snap_dir


def tail_file(path, max_lines=30):
    """读取文本文件最后若干行，用于启动失败时把关键日志直接打到控制台"""
    try:
        with open(path, "rb") as f:
            f.seek(0, os.SEEK_END)
            size = f.tell()
            f.seek(max(0, size - 8192))
            text = f.read().decode("utf-8", "ignore")
        lines = [ln for ln in text.splitlines() if ln.strip()]
        return lines[-max_lines:]
    except Exception:
        return []


# ----------------- Windows 作业对象（关窗杀全部子进程） -----------------
kernel32 = ctypes.WinDLL("kernel32", use_last_error=True)


class _IO_COUNTERS(ctypes.Structure):
    _fields_ = [("ReadOperationCount", ctypes.c_ulonglong),
                ("WriteOperationCount", ctypes.c_ulonglong),
                ("OtherOperationCount", ctypes.c_ulonglong),
                ("ReadTransferCount", ctypes.c_ulonglong),
                ("WriteTransferCount", ctypes.c_ulonglong),
                ("OtherTransferCount", ctypes.c_ulonglong)]


class _BASIC_LIMIT(ctypes.Structure):
    _fields_ = [("PerProcessUserTimeLimit", ctypes.c_int64),
                ("PerProcessUserTimeLimit", ctypes.c_int64),
                ("LimitFlags", wintypes.DWORD),
                ("MinimumWorkingSetSize", ctypes.c_size_t),
                ("MaximumWorkingSetSize", ctypes.c_size_t),
                ("ActiveProcessLimit", wintypes.DWORD),
                ("Affinity", ctypes.c_void_p),
                ("PriorityClass", wintypes.DWORD),
                ("SchedulingClass", wintypes.DWORD)]


class _EXT_LIMIT(ctypes.Structure):
    _fields_ = [("BasicLimitInformation", _BASIC_LIMIT),
                ("IoInfo", _IO_COUNTERS),
                ("ProcessMemoryLimit", ctypes.c_size_t),
                ("JobMemoryLimit", ctypes.c_size_t),
                ("PeakProcessMemoryUsed", ctypes.c_size_t),
                ("PeakJobMemoryUsed", ctypes.c_size_t)]


JOB_OBJECT_EXTENDED_LIMIT_INFORMATION = 9
JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE = 0x2000

JOB = None


def make_kill_job():
    job = kernel32.CreateJobObjectW(None, None)
    info = _EXT_LIMIT()
    info.BasicLimitInformation.LimitFlags = JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE
    kernel32.SetInformationJobObject(job, JOB_OBJECT_EXTENDED_LIMIT_INFORMATION,
                                     ctypes.byref(info), ctypes.sizeof(info))
    return job


def assign_job(job, proc):
    PROCESS_SET_QUOTA = 0x0100
    PROCESS_TERMINATE = 0x0001
    handle = kernel32.OpenProcess(PROCESS_SET_QUOTA | PROCESS_TERMINATE, False, proc.pid)
    if not handle:
        handle = proc._handle
    ok = kernel32.AssignProcessToJobObject(job, handle)
    if not ok and handle != proc._handle:
        kernel32.AssignProcessToJobObject(job, proc._handle)


# 后端输出统一追加写入同一文件句柄
_stdout_fp = None


def spawn(argv, cwd=None, env=None, label="", role=None, capture_stdout=False):
    global _stdout_fp
    log("[启动] %s" % label)
    if capture_stdout:
        if _stdout_fp is None:
            _stdout_fp = open(BACKEND_STDOUT, "ab")
        out_fp, err_fp = _stdout_fp, _stdout_fp
    else:
        out_fp, err_fp = subprocess.DEVNULL, subprocess.DEVNULL
    p = subprocess.Popen(
        argv,
        cwd=cwd,
        env=env,
        stdout=out_fp,
        stderr=err_fp,
        creationflags=CREATE_NO_WINDOW | CREATE_NEW_PROCESS_GROUP,
        close_fds=True,
    )
    if role:
        procs[role] = p
    if JOB:
        assign_job(JOB, p)
    return p


def task_kill(pid, tree=True):
    """强制结束进程（默认连子进程树一起）"""
    try:
        subprocess.run(["taskkill", "/PID", str(pid), "/F"] + (["/T"] if tree else []),
                       stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
                       creationflags=CREATE_NO_WINDOW, timeout=20)
    except Exception:
        pass


def process_alive(role):
    p = procs.get(role)
    return p is not None and p.poll() is None


# ----------------------------- MariaDB -----------------------------
def write_my_ini():
    # MariaDB/MySQL 在 Windows 下同样支持正斜杠路径，且必须使用正斜杠：
    # 选项文件会把反斜杠当转义符（\s→空格、\r→回车、\t→制表符），
    # 安装路径中的 \smoke、\runtime 等段会被破坏导致启动失败；
    # 编码必须为 UTF-8（MariaDB 10.11 在 Windows 上按 UTF-8 解析 my.ini，
    # GBK 中文路径会直接报 "invalid (non-UTF8) characters" 并中止启动）。
    def p(path):
        return path.replace("\\", "/")

    # 参数说明见各项行内注释；均为可随时调整的服务器参数，不涉及表结构与数据
    content = (
        "[mysqld]\n"
        "basedir=%s\n"
        "datadir=%s\n"
        "port=%d\n"
        "socket=%s\n"
        "pid-file=%s\n"
        "bind-address=127.0.0.1\n"
        "character-set-server=utf8mb4\n"
        "collation-server=utf8mb4_general_ci\n"
        "log-error=%s\n"
        "innodb-flush-method=normal\n"
        # 不做客户端主机名 DNS 反解：网卡/DNS 抖动时新建连接不会因此被拖慢
        "skip-name-resolve\n"
        # 单机低并发，80 连接足够，避免异常场景连接数失控吃满内存
        "max_connections=80\n"
        # 空闲连接 1 小时断开；必须大于连接池 maxLifetime(25 分钟)，由池先回收
        "wait_timeout=3600\n"
        "interactive_timeout=3600\n"
        # 锁等待最多 20 秒即报错回滚，不要让业务线程长时间挂在行锁上
        "innodb-lock-wait-timeout=20\n"
        # 附件/Excel 导入等场景需要较大包体
        "max_allowed_packet=64M\n"
        # 单机版以可用性优先：每秒落盘改为每秒刷 OS 缓存，崩溃最多丢 1 秒事务
        "innodb-flush-log-at-trx-commit=2\n"
        "[client]\n"
        "port=%d\n"
        "protocol=TCP\n"
        "host=127.0.0.1\n"
    ) % (p(MARIADB_DIR), p(DB_DATA_DIR), DB_PORT,
         p(os.path.join(DB_DATA_DIR, "qms_offline.sock")),
         p(os.path.join(DB_DATA_DIR, "qms.pid")),
         p(os.path.join(LOG_DIR, "mariadb.err")),
         DB_PORT)
    with open(MY_INI, "w", encoding="utf-8") as f:
        f.write(content)


def mysql_cli(sql, timeout=60):
    return subprocess.run(
        [MARIADB_CLIENT, "--protocol=TCP", "-h", "127.0.0.1", "-P", str(DB_PORT),
         "-uroot", "-e", sql],
        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
        creationflags=CREATE_NO_WINDOW, timeout=timeout)


def db_ping():
    """端口通不等于可服务：再用 admin ping 确认数据库真正能响应"""
    try:
        r = subprocess.run(
            [MARIADB_ADMIN, "--protocol=TCP", "-h", "127.0.0.1", "-P", str(DB_PORT),
             "-uroot", "ping"],
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
            creationflags=CREATE_NO_WINDOW, timeout=10)
        return r.returncode == 0
    except Exception:
        return False


def init_database():
    log("[初始化] 首次启动，正在创建本地数据库（约 10~30 秒）...")
    os.makedirs(DB_DATA_DIR, exist_ok=True)
    r = subprocess.run(
        [MARIADB_INSTALL_DB, "--datadir=%s" % DB_DATA_DIR],
        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
        creationflags=CREATE_NO_WINDOW, timeout=180)
    if r.returncode != 0:
        raise RuntimeError("mariadb-install-db 失败，返回码 %s" % r.returncode)
    write_my_ini()

    # 首次用跳过权限表方式启动，写入本地访问账号（空密码，仅 127.0.0.1 演示用）
    spawn([MARIADBD, "--defaults-file=%s" % MY_INI, "--skip-grant-tables"],
          label="MariaDB 初始化实例")
    if not wait_port(DB_PORT, 60, "MariaDB 初始化"):
        raise RuntimeError("数据库初始化实例启动超时")
    time.sleep(2)
    sql = (
        "FLUSH PRIVILEGES;"
        "CREATE USER IF NOT EXISTS 'root'@'127.0.0.1' IDENTIFIED BY '';"
        "ALTER USER 'root'@'127.0.0.1' IDENTIFIED BY '';"
        "GRANT ALL PRIVILEGES ON *.* TO 'root'@'127.0.0.1' WITH GRANT OPTION;"
        "CREATE USER IF NOT EXISTS 'root'@'localhost' IDENTIFIED BY '';"
        "ALTER USER 'root'@'localhost' IDENTIFIED BY '';"
        "GRANT ALL PRIVILEGES ON *.* TO 'root'@'localhost' WITH GRANT OPTION;"
        "FLUSH PRIVILEGES;"
    )
    rc = mysql_cli(sql).returncode
    if rc != 0:
        raise RuntimeError("数据库账号初始化失败，返回码 %s" % rc)
    subprocess.run([MARIADB_ADMIN, "--protocol=TCP", "-h", "127.0.0.1", "-P",
                    str(DB_PORT), "-uroot", "shutdown"],
                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
                   creationflags=CREATE_NO_WINDOW, timeout=30)
    wait_port_closed(DB_PORT, 30, "MariaDB 初始化实例")
    log("[初始化] 数据库账号就绪")


def start_mariadb():
    if not os.path.exists(os.path.join(DB_DATA_DIR, "mysql")):
        init_database()
    else:
        write_my_ini()
    spawn([MARIADBD, "--defaults-file=%s" % MY_INI],
          label="MariaDB (%d)" % DB_PORT, role="mariadb")
    # 先等端口，再做 ping 二次确认，防止“端口在但数据库还不能服务”的误判
    if not wait_port(DB_PORT, 60, "MariaDB") or not _wait_db_ready(30):
        raise RuntimeError("MariaDB 启动超时或无法连接，详见 logs\\mariadb.err")
    log("[就绪] MariaDB 端口 %d" % DB_PORT)


def _wait_db_ready(timeout):
    deadline = time.time() + timeout
    while time.time() < deadline:
        if db_ping():
            return True
        time.sleep(1.0)
    return False


def stop_mariadb_graceful():
    """优先优雅关闭（保证 InnoDB 正常 shutdown，杜绝 crash recovery）"""
    try:
        subprocess.run([MARIADB_ADMIN, "--protocol=TCP", "-h", "127.0.0.1", "-P",
                        str(DB_PORT), "-uroot", "shutdown"],
                       stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
                       creationflags=CREATE_NO_WINDOW, timeout=20)
    except Exception:
        p = procs.get("mariadb")
        if p:
            task_kill(p.pid)
    wait_port_closed(DB_PORT, 30, "MariaDB")


def start_redis():
    os.makedirs(REDIS_DATA_DIR, exist_ok=True)
    spawn([REDIS_SERVER, "--port", str(REDIS_PORT), "--bind", "127.0.0.1",
           "--protected-mode", "no", "--dir", REDIS_DATA_DIR,
           "--dbfilename", "dump.rdb", "--save", "900", "1",
           "--appendonly", "no",
           # 关键：BGSAVE 失败时拒绝写入会造成全站写操作被熔断，
           # 单机版降级为仅允许写入并继续服务（缓存数据可重建）
           "--stop-writes-on-bgsave-error", "no",
           # 空闲连接 TCP 保活，尽快探测失效连接
           "--tcp-keepalive", "60"],
          label="Redis (%d)" % REDIS_PORT, role="redis")
    if not wait_port(REDIS_PORT, 30, "Redis"):
        raise RuntimeError("Redis 启动超时")
    log("[就绪] Redis 端口 %d" % REDIS_PORT)


def backend_env():
    env = os.environ.copy()
    env.update({
        "SPRING_PROFILES_ACTIVE": "offline",
        "APP_PORT": str(APP_PORT),
        "DB_HOST": "127.0.0.1",
        "DB_PORT": str(DB_PORT),
        "DB_NAME": "qms_offline",
        "DB_USER": "root",
        "DB_PASSWORD": "",
        "REDIS_HOST": "127.0.0.1",
        "REDIS_PORT": str(REDIS_PORT),
        "WEB_STATIC_DIR": STATIC_DIR,
        "STORAGE_LOCAL_DIR": ATTACH_DIR,
        "LOG_FILE": os.path.join(LOG_DIR, "backend.log"),
    })
    return env


def start_backend():
    return spawn([JRE_JAVA, "-Xms128m", "-Xmx768m", "-Dfile.encoding=UTF-8",
                  "-jar", APP_JAR],
                 cwd=os.path.join(PKG_ROOT, "app"), env=backend_env(),
                 label="品控工作台后端 (%d)" % APP_PORT, role="backend",
                 capture_stdout=True)


def diagnose_backend_failure():
    """后端未在预期内就绪：把可能原因用中文直接打印到控制台"""
    log("=" * 62)
    log("[诊断] 后端未能在 %d 秒内就绪，开始收集失败原因 ..." % STARTUP_WAIT_BACKEND)
    p = procs.get("backend")
    if p is not None and p.poll() is not None:
        log("[诊断] 后端进程已异常退出，返回码：%s" % p.returncode)
    else:
        log("[诊断] 后端进程仍在运行，但健康检查不通过（疑似启动卡住或依赖未就绪）")
    if not port_open(DB_PORT):
        log("[诊断] 数据库端口 %d 不可用 —— 原因：数据库启动失败。请查看 logs\\mariadb.err" % DB_PORT)
    elif not db_ping():
        log("[诊断] 数据库端口在但无法响应 ping —— 原因：数据库仍在恢复或异常，详见 logs\\mariadb.err")
    # 从应用日志中识别高频根因关键词
    keywords = {
        "Access denied": "数据库账号或密码错误（DB_PASSWORD 与库内 root 密码不一致）",
        "Communications link failure": "后端无法连接数据库（数据库尚未就绪或已退出）",
        "Unknown database": "数据库 qms_offline 不存在且自动创建失败",
        "already in use": "端口 18080 被其他程序占用",
        "Unable to access jarfile": "应用 jar 文件缺失或损坏，请重新解压完整安装包",
        "Flyway": "数据库版本迁移（Flyway）执行失败，详见上方迁移错误",
        "OutOfMemoryError": "内存不足，可联系维护者调整启动内存参数",
    }
    hint_printed = False
    for path in (os.path.join(LOG_DIR, "backend.log"), BACKEND_STDOUT):
        for line in tail_file(path, 80):
            for kw, reason in keywords.items():
                if kw in line:
                    log("[诊断] 命中关键错误（%s）：%s" % (kw, reason))
                    hint_printed = True
                    break
    log("[诊断] 应用日志最后内容（logs\\backend.log）：")
    for line in tail_file(os.path.join(LOG_DIR, "backend.log"), 25):
        print("        " + line)
    stdout_tail = tail_file(BACKEND_STDOUT, 15)
    if stdout_tail:
        log("[诊断] JVM 输出最后内容（logs\\backend-stdout.log）：")
        for line in stdout_tail:
            print("        " + line)
    if not hint_printed:
        log("[诊断] 未能自动识别具体原因，请将 logs 目录整体反馈给维护者。")
    log("=" * 62)


def open_browser_later():
    time.sleep(2)
    try:
        webbrowser.open(URL)
    except Exception:
        log("浏览器未自动打开，请手动访问 %s" % URL)


# ----------------------------- 守护自愈 -----------------------------
def allow_restart():
    """滚动 1 小时窗口限流，超出上限返回 False（避免故障状态下疯狂重启）"""
    now = time.time()
    while restart_history and now - restart_history[0] > 3600:
        restart_history.popleft()
    if len(restart_history) >= RESTART_LIMIT_PER_HOUR:
        return False
    restart_history.append(now)
    return True


def restart_redis():
    if not allow_restart():
        log("[自愈] Redis 重启次数已达 1 小时上限 %d 次，停止自动重启 Redis" % RESTART_LIMIT_PER_HOUR)
        return
    log("[自愈] Redis 不可用，正在自动重启 ...")
    p = procs.get("redis")
    if p and p.poll() is None:
        task_kill(p.pid)
    wait_port_closed(REDIS_PORT, 15, "Redis")
    try:
        start_redis()
        log("[自愈] Redis 已恢复")
    except Exception as e:
        log("[自愈] Redis 重启失败: %s" % e)


def restart_mariadb_and_backend():
    if not allow_restart():
        log("[自愈] 数据库重启次数已达 1 小时上限 %d 次，停止自动重启" % RESTART_LIMIT_PER_HOUR)
        return
    log("[自愈] 数据库不可用，正在自动重启数据库与后端 ...")
    bp = procs.get("backend")
    if bp and bp.poll() is None:
        task_kill(bp.pid)
    wait_port_closed(APP_PORT, 15, "后端")
    stop_mariadb_graceful()
    try:
        start_mariadb()
        start_backend()
        if wait_backend_healthy(STARTUP_WAIT_BACKEND):
            log("[自愈] 数据库与后端均已恢复")
        else:
            diagnose_backend_failure()
    except Exception as e:
        log("[自愈] 数据库/后端重启失败: %s" % e)


def restart_backend(reason):
    if not allow_restart():
        log("[自愈] 后端重启次数已达 1 小时上限 %d 次，停止自动重启，请手动关闭窗口后重新启动"
            % RESTART_LIMIT_PER_HOUR)
        return
    log("[自愈] %s，正在自动重启后端 ..." % reason)
    p = procs.get("backend")
    if p and p.poll() is None:
        task_kill(p.pid)
    else:
        log("[自愈] 后端进程已自行退出，返回码 %s" % (p.returncode if p else "未知"))
    wait_port_closed(APP_PORT, 20, "后端")
    try:
        start_backend()
        if wait_backend_healthy(STARTUP_WAIT_BACKEND):
            log("[自愈] 后端已恢复，无需手动操作，可刷新浏览器继续使用")
        else:
            diagnose_backend_failure()
    except Exception as e:
        log("[自愈] 后端重启异常: %s" % e)


def wait_backend_healthy(timeout):
    deadline = time.time() + timeout
    while time.time() < deadline:
        if backend_healthy():
            return True
        time.sleep(1.5)
    return False


def watchdog_loop():
    """运行期守护：三组件存活 + 业务探针双保险，异常自动恢复并留现场"""
    health_fails = 0

    def guarded(action, label):
        """所有自愈动作串行：拿不到锁说明另一处重启正在进行，本轮直接跳过。"""
        if not restart_lock.acquire(blocking=False):
            log("[守护] 已有自愈动作进行中，跳过本轮 %s" % label)
            return
        try:
            action()
        finally:
            restart_lock.release()

    while not shutdown_flag.is_set():
        if shutdown_flag.wait(WATCH_INTERVAL):
            break
        try:
            # 1) Redis：端口在即可（轻量，不额外发 PING 命令）
            if not port_open(REDIS_PORT) or not process_alive("redis"):
                guarded(restart_redis, "Redis 重启")
                health_fails = 0
                continue

            # 2) MariaDB：必须能 ping 通
            if not process_alive("mariadb") or not db_ping():
                guarded(restart_mariadb_and_backend, "数据库重启")
                health_fails = 0
                continue

            # 3) 后端：进程退出立即重启；进程活着但业务探针连续失败则判“假死”
            if not process_alive("backend"):
                guarded(lambda: restart_backend("后端进程已退出"), "后端重启")
                health_fails = 0
                continue

            healthy, detail = backend_business_probe()
            probe_timeline.append((time.time(), healthy, detail))
            if healthy:
                if health_fails:
                    log("[守护] 业务探针恢复正常（此前连续失败 %d 次）" % health_fails)
                health_fails = 0
            else:
                health_fails += 1
                log("[守护] 业务探针失败 %d/%d — %s" % (health_fails, HEALTH_FAIL_LIMIT, detail))
                if health_fails >= HEALTH_FAIL_LIMIT:
                    health_fails = 0

                    def _backend_freeze_restart():
                        reason = "业务探针连续 %d 次失败（约 %d 秒无响应，疑似假死）" % (
                            HEALTH_FAIL_LIMIT, WATCH_INTERVAL * HEALTH_FAIL_LIMIT)
                        freeze_snapshot(reason)
                        restart_backend(reason)

                    guarded(_backend_freeze_restart, "假死重启")
        except Exception as e:
            # 守护线程自身任何意外都不能死，死了就没有自愈了
            log("[守护] 守护循环出现异常（已忽略，下轮继续）: %s" % e)


# ----------------------------- 主流程（编排层，控制台/GUI 共用） -----------------------------
class StartupError(Exception):
    """启动失败：携带是否为端口占用等结构化信息，供界面层渲染。"""

    def __init__(self, message, port_busy=None):
        super().__init__(message)
        self.port_busy = port_busy or []


def init_file_logging():
    """启动器自身日志落盘；超过 2MB 滚动一次，避免长期使用无限增长。"""
    global _log_fp
    try:
        if os.path.isfile(LAUNCHER_LOG) and os.path.getsize(LAUNCHER_LOG) > 2 * 1024 * 1024:
            try:
                if os.path.isfile(LAUNCHER_LOG + ".1"):
                    os.remove(LAUNCHER_LOG + ".1")
                os.rename(LAUNCHER_LOG, LAUNCHER_LOG + ".1")
            except Exception:
                pass
        _log_fp = open(LAUNCHER_LOG, "a", encoding="utf-8")
    except Exception:
        _log_fp = None


def preflight_ports(interactive=True):
    """
    启动前三端口预检。上一实例关闭后数据库等子进程可能仍在收尾，
    先给 20 秒等待其自行释放；仍占用则按端口给出明确中文原因。
    interactive=False（GUI 模式）时不弹窗、不退出，而是抛 StartupError。
    """
    wait_seconds = 20
    deadline = time.time() + wait_seconds
    busy = [p for p in (APP_PORT, DB_PORT, REDIS_PORT) if port_open(p)]
    while busy and time.time() < deadline:
        time.sleep(1)
        busy = [p for p in (APP_PORT, DB_PORT, REDIS_PORT) if port_open(p)]
    if not busy:
        return
    reasons = {
        APP_PORT: "服务端口 %d 被占用：本程序可能已经在运行，请先关闭已打开的清源QMS窗口。" % APP_PORT,
        DB_PORT: "数据库端口 %d 被占用：上一次程序的数据库仍在后台运行，请稍候 10 秒后重试；"
                 "若一直存在，请打开任务管理器结束残留的 mariadbd 进程，或重启电脑。" % DB_PORT,
        REDIS_PORT: "缓存端口 %d 被占用：上一次程序的缓存进程尚未退出，请稍候 10 秒后重试，"
                    "或在任务管理器结束残留的 redis-server 进程。" % REDIS_PORT,
    }
    msg = "\n\n".join(reasons[p] for p in busy)
    if interactive:
        alert_box(msg, "端口被占用，无法启动")
        sys.exit(1)
    raise StartupError(msg, port_busy=busy)


def check_runtime_files():
    """返回缺失的关键运行文件列表（空列表=齐全）。"""
    missing = [f for f in (JRE_JAVA, MARIADBD, MARIADB_INSTALL_DB, REDIS_SERVER, APP_JAR)
               if not os.path.isfile(f)]
    return missing


def prepare(interactive=True):
    """建目录、日志、文件预检、端口预检、作业对象与关闭回调。失败抛 StartupError。"""
    global JOB
    os.makedirs(LOG_DIR, exist_ok=True)
    os.makedirs(ATTACH_DIR, exist_ok=True)
    os.makedirs(REDIS_DATA_DIR, exist_ok=True)
    init_file_logging()

    log("=" * 62)
    log("  清源 QMS · 品控工作台（单机离线版）正在启动 ...")
    log("  首次启动需初始化数据库，约 30~60 秒；之后启动约 10~20 秒")
    log("  运行期内置守护自愈：每 %d 秒业务探针巡检，约 %d 秒无响应自动重启并保存现场"
        % (WATCH_INTERVAL, WATCH_INTERVAL * HEALTH_FAIL_LIMIT))
    log("=" * 62)

    missing = check_runtime_files()
    if missing:
        raise StartupError("缺少运行文件：\n%s\n\n请解压完整后再运行，勿在压缩包内直接启动。"
                           % "\n".join(missing))

    preflight_ports(interactive=interactive)

    JOB = make_kill_job()
    install_close_handler()

    def on_signal(sig, frame):
        shutdown_flag.set()
        graceful_shutdown()
        sys.exit(0)

    try:
        signal.signal(signal.SIGINT, on_signal)
        signal.signal(signal.SIGBREAK, on_signal)
    except Exception:
        pass


def startup():
    """依次拉起 MariaDB -> Redis -> 后端并等待就绪；失败收集诊断后抛 StartupError。"""
    try:
        start_mariadb()
        start_redis()
        start_backend()
    except Exception as e:
        log("[错误] %s" % e)
        if not port_open(DB_PORT) or not db_ping():
            log("[诊断] 当前失败环节在数据库侧，详见 logs\\mariadb.err 最后 30 行：")
            for line in tail_file(os.path.join(LOG_DIR, "mariadb.err"), 30):
                log("        " + line)
        raise StartupError(str(e))

    log("[启动] 后端加载中（Flyway 自动建表/灌种子），最长等待 %d 秒 ..." % STARTUP_WAIT_BACKEND)
    if not wait_backend_healthy(STARTUP_WAIT_BACKEND):
        diagnose_backend_failure()
        raise StartupError("服务未能在 %d 秒内就绪，详见 logs 目录。" % STARTUP_WAIT_BACKEND)

    # 就绪后立即做一次业务探针确认 DB/Redis 真实可用（不仅是 actuator 偶发 UP）
    healthy, detail = backend_business_probe()
    if healthy:
        log("[就绪] 业务探针确认数据库/缓存可用：%s" % detail)
    else:
        log("[就绪] 健康端点已 UP，但业务探针异常（继续观察，看门狗会自动处理）：%s" % detail)
    log("[就绪] 清源QMS已启动：%s" % URL)


_shutdown_done = False


def graceful_shutdown():
    """关窗/退出时优雅关停：后端 → Redis 存盘 → MariaDB 干净 shutdown；作业对象兜底杀残余。"""
    global _shutdown_done
    if _shutdown_done:
        return
    _shutdown_done = True
    shutdown_flag.set()
    log("[退出] 正在停止品控工作台 ...")
    # 先停后端，避免它在数据库关闭过程中继续持有连接/写数据
    p = procs.get("backend")
    if p is not None and p.poll() is None:
        task_kill(p.pid)
        wait_port_closed(APP_PORT, 20, "后端")
    log("[退出] Redis 存盘并关闭 ...")
    _quick_redis_shutdown()
    log("[退出] MariaDB 干净关闭 ...")
    _quick_mariadb_shutdown()
    log("[退出] 全部服务已停止，再见")
    time.sleep(0.3)
    try:
        if _log_fp is not None:
            _log_fp.flush()
            _log_fp.close()
    except Exception:
        pass


def start_watchdog():
    """以守护线程方式运行看门狗（GUI 模式使用，主线程留给界面事件循环）。"""
    t = threading.Thread(target=watchdog_loop, name="qms-watchdog", daemon=True)
    t.start()
    return t


def main():
    """控制台（黑窗）模式入口。"""
    disable_quick_edit()
    set_console_title("清源QMS单机版运行中 | 关闭此窗口即停止")
    try:
        prepare(interactive=True)
        startup()
    except StartupError as e:
        if e.port_busy:
            sys.exit(1)
        alert_box("%s\n\n排查日志见文件夹内 logs 目录。" % e, "启动失败")
        time.sleep(3)
        sys.exit(3)

    threading.Thread(target=open_browser_later, daemon=True).start()

    # 进入守护循环（主线程）：正常情况下一直运行，直到窗口被关闭
    try:
        watchdog_loop()
    except KeyboardInterrupt:
        pass
    # Ctrl+C 等正常退出路径：先优雅关停数据库；窗口关闭路径由控制台回调负责。
    # 未停掉的残余进程最终由作业对象兜底强杀，绝不残留。
    graceful_shutdown()


if __name__ == "__main__":
    main()
