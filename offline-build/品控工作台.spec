# -*- mode: python ; coding: utf-8 -*-
# v1.2.1：无黑窗桌面 App（pywebview + 系统 WebView2），入口 app_gui.py
from PyInstaller.utils.hooks import collect_submodules

# pywebview 在 Windows 上经 pythonnet 动态加载 EdgeChromium(WinForms) 后端，
# 静态分析扫不到这些子模块，需显式收集
hiddenimports = collect_submodules('webview') + [
    'clr',
    'proxy',
    'launcher_qms',
]

a = Analysis(
    ['app_gui.py'],
    pathex=[],
    binaries=[],
    datas=[('app.ico', '.')],
    hiddenimports=hiddenimports,
    hookspath=[],
    hooksconfig={},
    runtime_hooks=[],
    excludes=[],
    noarchive=False,
    optimize=0,
)
pyz = PYZ(a.pure)

exe = EXE(
    pyz,
    a.scripts,
    a.binaries,
    a.datas,
    [],
    name='清源QMS',
    debug=False,
    bootloader_ignore_signals=False,
    strip=False,
    upx=True,
    upx_exclude=[],
    runtime_tmpdir=None,
    console=False,
    disable_windowed_traceback=False,
    argv_emulation=False,
    target_arch=None,
    codesign_identity=None,
    entitlements_file=None,
    icon='app.ico',
)
