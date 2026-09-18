# -*- coding: utf-8 -*-
"""
清源 QMS 品控工作台 —— 无黑窗桌面 App 入口（pywebview + 系统 WebView2）

形态：双击桌面图标 → 无控制台窗口 → 应用窗口直接显示启动进度 →
     就绪后载入内嵌的工作台页面；点 × 优雅停止 MariaDB/Redis/Java 后完全退出。

本文件只做“界面与编排”，全部服务生命周期逻辑复用 launcher_qms.py（core）：
  - 启动/初始化失败：窗口内显示中文原因 + 打开日志目录 + 退出（不再弹系统黑框）
  - 守护自愈：core 的看门狗线程每 10 秒业务探针巡检，约 30 秒无响应自动重启后端
  - 关窗：closing 事件拦截 → core.graceful_shutdown() → destroy
     （兜底仍由 Windows Job Object KILL_ON_JOB_CLOSE 保证零进程残留）
  - 首次启动自动创建桌面/开始菜单快捷方式（自定义图标）
"""
import ctypes
import json
import os
import sys
import threading
import time

# 与启动器同目录（开发态）；PyInstaller 打包后 core 被打进同一个 exe
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import launcher_qms as core  # noqa: E402

APP_VERSION = "v1.2.1"
WINDOW_TITLE = "清源QMS 品控工作台 %s" % APP_VERSION

# 后台线程向窗口推状态时去重/节流用
_last_pushed = {"line": "", "ts": 0.0}
_state = {
    "started": False,       # 服务是否已就绪
    "app_loaded": False,    # 工作台页面是否已载入
    "closing": False,
    "allow_close": False,
    "reload_pending": False,
}
_window = None


# --------------------------- WebView2 运行时检测 ---------------------------
def webview2_available():
    """检测 Evergreen WebView2 Runtime 是否安装（Win10/11 一般自带）。"""
    try:
        import winreg
        sub = r"SOFTWARE\WOW6432Node\Microsoft\EdgeUpdate\Clients" \
              r"\{F3017226-FE2A-4295-8BDF-00C3A9A7E4C5}"
        for root in (winreg.HKEY_LOCAL_MACHINE, winreg.HKEY_CURRENT_USER):
            try:
                with winreg.OpenKey(root, sub) as k:
                    winreg.QueryValueEx(k, "pv")
                    return True
            except OSError:
                pass
    except Exception:
        pass
    # 兜底：存在独立安装的 Edge 浏览器（其内置运行时也可被部分场景复用）
    return os.path.isfile(r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe")


def prompt_install_webview2():
    """无窗口环境下用系统对话框提示安装 WebView2。"""
    msg = ("本程序需要 Microsoft Edge WebView2 运行时（Windows 10/11 通常已自带）。\n\n"
           "点“是”打开官方下载页面，安装“Evergreen Standalone Installer”后重新启动本程序。")
    try:
        MB_YESNO = 0x04
        MB_ICONWARNING = 0x30
        IDYES = 6
        if ctypes.windll.user32.MessageBoxW(0, msg, "清源QMS 缺少运行组件",
                                            MB_YESNO | MB_ICONWARNING) == IDYES:
            os.startfile("https://developer.microsoft.com/microsoft-edge/webview2/")
    except Exception:
        pass


def icon_path():
    """窗口图标：打包后在 _MEIPASS，开发态在 offline-build 目录。"""
    candidates = []
    meipass = getattr(sys, "_MEIPASS", None)
    if meipass:
        candidates.append(os.path.join(meipass, "app.ico"))
    candidates.append(os.path.join(os.path.dirname(os.path.abspath(__file__)), "app.ico"))
    for p in candidates:
        if os.path.isfile(p):
            return p
    return None


# --------------------------- 桌面快捷方式 ---------------------------
def create_shortcuts_once():
    """首次成功启动后创建桌面 + 开始菜单快捷方式；marker 存在则跳过。"""
    marker = os.path.join(core.DATA_DIR, ".shortcut-v1")
    exe_path = os.path.abspath(sys.executable if getattr(sys, "frozen", False)
                               else os.path.join(os.path.dirname(os.path.abspath(__file__)),
                                                 "..", "offline-build", "dist", "清源QMS.exe"))
    if not os.path.isfile(exe_path):
        return
    # marker 存在但快捷方式丢失（如用户手动删除）时重新创建。
    # 桌面真实路径由 PowerShell 侧 GetFolderPath 决定（可能被 OneDrive 重定向），
    # 不在这里用 ~/Desktop 猜测；执行后由 PS 回传实际路径。
    if os.path.isfile(marker):
        return
    # 注意：Windows PowerShell 5.1 会按 ANSI 代码页解析 -Command 文本，
    # 直接内联中文路径/文件名会乱码导致语法错误。这里命令体保持纯 ASCII，
    # 所有 Unicode 值（exe 路径/快捷方式名）经环境变量传入（env 块是 UTF-16）。
    ps = (
        "$ErrorActionPreference='Stop';"
        "$ws=New-Object -ComObject WScript.Shell;"
        "$name=[IO.Path]::GetFileNameWithoutExtension($env:QMS_EXE);"
        "$dirs=@([Environment]::GetFolderPath('Desktop'),"
        "[Environment]::GetFolderPath('Programs'));"
        "foreach($d in $dirs){"
        "$lnk=$ws.CreateShortcut((Join-Path $d ($name+'.lnk')));"
        "$lnk.TargetPath=$env:QMS_EXE;"
        "$lnk.WorkingDirectory=(Split-Path $env:QMS_EXE -Parent);"
        "$lnk.IconLocation=($env:QMS_EXE+',0');"
        "$lnk.Description='Qingyuan QMS Workstation';"
        "$lnk.Save()};"
        "Write-Output (Join-Path ([Environment]::GetFolderPath('Desktop')) ($name+'.lnk'))"
    )
    try:
        import subprocess
        env = dict(os.environ)
        env["QMS_EXE"] = exe_path
        r = subprocess.run(["powershell", "-NoProfile", "-NonInteractive", "-Command", ps],
                           stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                           env=env, creationflags=core.CREATE_NO_WINDOW, timeout=20)
        created = r.stdout.decode("mbcs", "replace").strip().splitlines()[-1] if r.stdout else ""
        if r.returncode != 0 or not created or not os.path.isfile(created):
            err = r.stderr.decode("gbk", "replace")[:200]
            core.log("[App] 创建快捷方式失败（不影响使用）: rc=%s %s" % (r.returncode, err))
            return
        with open(marker, "w", encoding="utf-8") as f:
            f.write("created at %s\n" % time.strftime("%Y-%m-%d %H:%M:%S"))
        core.log("[App] 桌面/开始菜单快捷方式已创建")
    except Exception as e:
        core.log("[App] 创建快捷方式失败（不影响使用）: %s" % e)


# --------------------------- 页面内容 ---------------------------
SPLASH_HTML = r"""<!doctype html><html lang="zh-CN"><head><meta charset="utf-8">
<style>
*{box-sizing:border-box;margin:0;padding:0}
body{font-family:"Microsoft YaHei UI","Microsoft YaHei",sans-serif;background:#f5f8f7;
height:100vh;display:flex;align-items:center;justify-content:center;color:#1f2d2d}
.card{width:520px;background:#fff;border-radius:16px;box-shadow:0 12px 48px rgba(22,101,82,.12);
padding:40px 44px 32px}
.brand{display:flex;align-items:center;gap:14px;margin-bottom:6px}
.logo{width:46px;height:46px;border-radius:12px;background:linear-gradient(135deg,#16a085,#0e7a64);
display:flex;align-items:center;justify-content:center;color:#fff;font-size:24px;font-weight:700}
h1{font-size:22px;font-weight:700}.sub{color:#6b7f7c;font-size:13px;margin:2px 0 26px 60px}
.stage{font-size:15px;font-weight:600;margin-bottom:14px;min-height:22px}
.spinner{display:inline-block;width:14px;height:14px;border:2px solid #bfe3da;border-top-color:#0e8a6f;
border-radius:50%;animation:spin .8s linear infinite;vertical-align:-2px;margin-right:10px}
@keyframes spin{to{transform:rotate(360deg)}}
.logbox{background:#0f2f2a;color:#9fd8c9;font-family:Consolas,monospace;font-size:12px;
border-radius:10px;padding:12px 14px;height:168px;overflow:hidden;line-height:1.7;white-space:pre-wrap;word-break:break-all}
.foot{margin-top:22px;color:#8aa19d;font-size:12px;text-align:center;line-height:1.7}
</style></head><body>
<div class="card">
  <div class="brand"><div class="logo">Q</div><h1>清源QMS 品控工作台</h1></div>
  <div class="sub">单机版 %VERSION% · 正在准备本地运行环境</div>
  <div class="stage"><span class="spinner"></span><span id="stage">正在启动…</span></div>
  <div class="logbox" id="logs"></div>
  <div class="foot">关闭窗口即停止全部服务并退出，数据保存在程序目录 data 文件夹<br>
  服务卡住时会在 30 秒内自动重启恢复，无需人工干预</div>
</div>
<script>
function qmsStage(t){document.getElementById('stage').textContent=t;}
function qmsLog(line){
  var box=document.getElementById('logs');
  box.textContent=(box.textContent+line+'\n').split('\n').slice(-9).join('\n');
  box.scrollTop=box.scrollHeight;
  if(line.indexOf('初始化本地数据库')>=0)qmsStage('正在初始化本地数据库（首次约 30~60 秒）…');
  else if(line.indexOf('MariaDB')>=0)qmsStage('正在启动本地数据库…');
  else if(line.indexOf('Redis')>=0)qmsStage('正在启动缓存服务…');
  else if(line.indexOf('品控工作台后端')>=0||line.indexOf('后端加载中')>=0)qmsStage('正在启动应用服务（建表/升级，约 10~20 秒）…');
  else if(line.indexOf('业务探针确认')>=0||line.indexOf('已启动')>=0)qmsStage('启动完成，正在进入工作台…');
}
</script></body></html>""".replace("%VERSION%", APP_VERSION)

STOPPING_HTML = r"""<!doctype html><html lang="zh-CN"><head><meta charset="utf-8">
<style>
*{box-sizing:border-box;margin:0;padding:0}
body{font-family:"Microsoft YaHei UI","Microsoft YaHei",sans-serif;background:#f5f8f7;
height:100vh;display:flex;align-items:center;justify-content:center;color:#1f2d2d}
.box{text-align:center}
.sp{width:42px;height:42px;border:4px solid #bfe3da;border-top-color:#0e8a6f;border-radius:50%;
animation:sp .8s linear infinite;margin:0 auto 22px}
@keyframes sp{to{transform:rotate(360deg)}}
h2{font-size:19px;margin-bottom:10px}p{color:#6b7f7c;font-size:13px;line-height:1.8}
</style></head><body><div class="box"><div class="sp"></div>
<h2>正在停止服务并保存数据…</h2><p>请稍候，窗口将自动关闭。<br>此过程可确保数据库干净关闭、不丢未保存数据。</p>
</div></body></html>"""


def error_html(title, message, port_busy):
    msg = json.dumps(str(message))[1:-1]  # 转义后交给 JS 文本
    tip = ("请先关闭已经打开的清源QMS窗口，再重新双击启动；"
           "若仍提示占用，请重启电脑后再试。") if port_busy else \
          ("可点击下方按钮查看日志目录，将问题反馈给维护者。")
    return r"""<!doctype html><html lang="zh-CN"><head><meta charset="utf-8">
<style>
*{box-sizing:border-box;margin:0;padding:0}
body{font-family:"Microsoft YaHei UI","Microsoft YaHei",sans-serif;background:#f5f8f7;
height:100vh;display:flex;align:center;justify-content:center;color:#1f2d2d}
.card{width:560px;background:#fff;border-radius:16px;box-shadow:0 12px 48px rgba(22,101,82,.12);
padding:38px 42px 30px}
h1{font-size:20px;margin-bottom:16px;color:#c0392b}
.msg{background:#fdf1ef;border:1px solid #f5d5cf;border-radius:10px;padding:14px 16px;
font-size:13px;line-height:1.8;white-space:pre-wrap;word-break:break-all;color:#5d3a34;max-height:230px;overflow:auto}
.tip{color:#7a8e8a;font-size:12.5px;margin:16px 0 24px;line-height:1.7}
button{border:0;border-radius:9px;padding:10px 20px;font-size:14px;cursor:pointer;margin-right:12px}
.primary{background:#0e8a6f;color:#fff}.ghost{background:#eef3f2;color:#33504c}
</style></head><body><div class="card">
<h1>""" + json.dumps(title)[1:-1] + r"""</h1>
<div class="msg">""" + msg + r"""</div>
<div class="tip">""" + tip + r"""</div>
<button class="ghost" onclick="window.pywebview.api.open_logs()">打开日志目录</button>
<button class="primary" onclick="window.pywebview.api.quit_app()">退出程序</button>
</div></body></html>"""


# 注入到工作台页面右下角的极简工具条（不改前端代码，运行时注入，SPA 内常驻）
TOOLBAR_JS = r"""
(function(){
  if(window.__qmsToolbar)return;window.__qmsToolbar=true;
  function btn(t,f){var b=document.createElement('button');b.textContent=t;
    b.style.cssText='display:block;width:100%;margin:6px 0;padding:8px 0;border:0;border-radius:8px;'
      +'background:#f2f7f6;color:#24524a;font-size:13px;cursor:pointer;font-family:inherit;';
    b.onmouseover=function(){b.style.background='#e0eeeb'};
    b.onmouseout=function(){b.style.background='#f2f7f6'};
    b.onclick=f;return b;}
  var chip=document.createElement('div');
  chip.style.cssText='position:fixed;right:14px;bottom:14px;z-index:2147483000;font-family:"Microsoft YaHei",sans-serif;';
  var head=document.createElement('div');
  head.textContent='⚙ 清源工具';
  head.style.cssText='background:#0e8a6f;color:#fff;font-size:12px;padding:7px 14px;border-radius:18px;'
    +'cursor:pointer;box-shadow:0 4px 14px rgba(14,138,111,.35);user-select:none;';
  var panel=document.createElement('div');
  panel.style.cssText='display:none;margin-top:8px;background:#fff;border:1px solid #dce8e5;border-radius:12px;'
    +'padding:8px 12px;width:148px;box-shadow:0 8px 28px rgba(0,0,0,.12)';
  panel.appendChild(btn('刷新页面',function(){window.pywebview.api.reload_page()}));
  panel.appendChild(btn('重启服务',function(){if(confirm('将重启本地服务，约 20 秒后自动刷新页面。继续？'))window.pywebview.api.restart_service()}));
  panel.appendChild(btn('打开日志目录',function(){window.pywebview.api.open_logs()}));
  panel.appendChild(btn('打开数据目录',function(){window.pywebview.api.open_data()}));
  panel.appendChild(btn('关于',function(){window.pywebview.api.about()}));
  head.onclick=function(){panel.style.display=panel.style.display==='none'?'block':'none'};
  chip.appendChild(head);chip.appendChild(panel);
  function mount(){document.body.appendChild(chip);}
  if(document.body)mount();else document.addEventListener('DOMContentLoaded',mount);
  window.__qmsToast=function(text){
    var t=document.createElement('div');
    t.textContent=text;
    t.style.cssText='position:fixed;right:14px;bottom:64px;z-index:2147483001;background:#24524a;color:#fff;'
      +'padding:10px 16px;border-radius:10px;font-size:13px;box-shadow:0 6px 20px rgba(0,0,0,.2);'
      +'font-family:"Microsoft YaHei",sans-serif;max-width:360px;line-height:1.5;';
    document.body.appendChild(t);setTimeout(function(){t.remove()},6000);
  };
})();
"""


# --------------------------- 暴露给页面的 API ---------------------------
class JsApi:
    def reload_page(self):
        try:
            _window.load_url(core.URL)
        except Exception:
            pass

    def restart_service(self):
        def work():
            _ui_toast("正在重启本地服务…")
            if core.restart_lock.acquire(blocking=False):
                try:
                    core.restart_backend("用户从工具条手动重启服务")
                finally:
                    core.restart_lock.release()
            else:
                _ui_toast("已有重启动作进行中，请稍候")
        threading.Thread(target=work, daemon=True).start()

    def open_logs(self):
        try:
            os.makedirs(core.LOG_DIR, exist_ok=True)
            os.startfile(core.LOG_DIR)
        except Exception as e:
            core.log("[App] 打开日志目录失败: %s" % e)

    def open_data(self):
        try:
            os.makedirs(core.DATA_DIR, exist_ok=True)
            os.startfile(core.DATA_DIR)
        except Exception as e:
            core.log("[App] 打开数据目录失败: %s" % e)

    def about(self):
        _ui_toast("清源QMS 品控工作台 单机版 %s\n服务地址 %s\n运行日志见 logs 目录"
                  % (APP_VERSION, core.URL))

    def quit_app(self):
        threading.Thread(target=_shutdown_worker, daemon=True).start()


# --------------------------- 线程与窗口编排 ---------------------------
def _ui_eval(js):
    """线程安全地向窗口推 JS，窗口未就绪/销毁时静默。"""
    if _window is None:
        return
    try:
        _window.evaluate_js(js)
    except Exception:
        pass


def _ui_toast(text):
    _ui_eval("window.__qmsToast && window.__qmsToast(%s)" % json.dumps(text))


def _on_log(line):
    # 节流：同一行 0.3 秒内不重复推
    now = time.time()
    if line == _last_pushed["line"] and now - _last_pushed["ts"] < 0.3:
        return
    _last_pushed["line"] = line
    _last_pushed["ts"] = now
    if _state["app_loaded"]:
        # 自愈相关事件在页面上以浮层提示；恢复后自动刷新一次以摆脱错误态
        if "正在自动重启" in line:
            _ui_toast("检测到服务无响应，正在自动重启，请稍候…")
        if "已恢复" in line or "数据库与后端均已恢复" in line:
            if not _state["reload_pending"]:
                _state["reload_pending"] = True
                _ui_toast("服务已自动恢复，2 秒后刷新页面…")
                threading.Timer(2.0, _do_recovery_reload).start()
    else:
        _ui_eval("window.qmsLog && window.qmsLog(%s)" % json.dumps(line))


def _do_recovery_reload():
    _state["reload_pending"] = False
    if _state["app_loaded"] and _state["started"]:
        try:
            _window.load_url(core.URL)
        except Exception:
            pass


def _show_error(title, message, port_busy=False):
    try:
        _window.load_html(error_html(title, message, port_busy))
    except Exception:
        core.alert_box(str(message), title)


def _startup_worker():
    try:
        core.prepare(interactive=False)
        core.startup()
    except core.StartupError as e:
        core.log("[App] 启动失败：%s" % e)
        title = "端口被占用，无法启动" if e.port_busy else "服务启动失败"
        _show_error(title, str(e), port_busy=bool(e.port_busy))
        return
    except Exception as e:
        core.log("[App] 启动异常：%s" % e)
        _show_error("服务启动异常", str(e))
        return

    _state["started"] = True
    create_shortcuts_once()
    core.start_watchdog()
    _ui_eval("window.qmsStage && window.qmsStage('启动完成，正在进入工作台…')")
    time.sleep(1)
    try:
        _window.load_url(core.URL)
    except Exception as e:
        core.log("[App] 载入工作台失败: %s" % e)


def _shutdown_worker():
    if _state["closing"]:
        return
    _state["closing"] = True
    try:
        _window.load_html(STOPPING_HTML)
    except Exception:
        pass
    try:
        core.graceful_shutdown()
    except Exception as e:
        core.log("[App] 关停异常: %s" % e)
    _state["allow_close"] = True
    time.sleep(0.5)
    try:
        _window.destroy()
    except Exception:
        pass


def _on_closing():
    # 返回 False 暂时阻止关闭，等优雅关停完成后由 _shutdown_worker destroy
    if _state["allow_close"]:
        return True
    if not _state["closing"]:
        threading.Thread(target=_shutdown_worker, daemon=True).start()
    return False


def _on_loaded():
    try:
        url = _window.get_current_url() or ""
    except Exception:
        url = ""
    if "18080" in url:
        _state["app_loaded"] = True
        _ui_eval(TOOLBAR_JS)


def main():
    global _window
    if not webview2_available():
        prompt_install_webview2()
        sys.exit(5)

    import webview

    core.set_status_hook(_on_log)
    _window = webview.create_window(
        WINDOW_TITLE,
        html=SPLASH_HTML,
        js_api=JsApi(),
        width=1180,
        height=800,
        min_size=(960, 640),
        text_select=False,
    )
    _window.events.closing += _on_closing
    _window.events.loaded += _on_loaded

    # GUI 事件循环就绪后再启动服务线程（确保 evaluate_js 可用）
    threading.Thread(target=_startup_worker, daemon=True).start()
    webview.start(gui="edgechromium", icon=icon_path())
    # 事件循环结束（窗口销毁）后兜底一次，覆盖异常退出路径
    try:
        core.graceful_shutdown()
    except Exception:
        pass


if __name__ == "__main__":
    main()
