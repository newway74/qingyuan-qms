<#
.SYNOPSIS
    清源QMS Windows 单机版一键构建脚本（后端 -> 前端 -> 启动器 -> 组装 -> 压缩 -> 冷启动冒烟）

.DESCRIPTION
    在仓库根的 scripts 目录下执行；产出 offline-dist\清源QMS_单机版_v<Version>.zip，
    zip 内根目录固定为“清源QMS_单机版”。runtime\（内置 JRE/MariaDB/Redis）需事先放入
    组装目录，本脚本不重新下载/打包运行时（体积大且基本不变）。

.PARAMETER Version
    版本号，默认 1.1.0，决定 zip 文件名。

.PARAMETER SkipTests
    后端打包跳过测试（默认执行 mvn clean package 全量测试）。

.PARAMETER SkipLauncher
    不重新执行 PyInstaller，直接复用 offline-build\dist\清源QMS.exe
    （仅改后端/前端/文档时使用）。

.PARAMETER SkipSmoke
    跳过“解压到全新目录首次启动”的冷冒烟验证。

.PARAMETER RebuildOnly
    跳过所有构建步骤，只重新组装 app 目录并压缩（用于仅改使用说明/CHANGELOG 的场景）。

.EXAMPLE
    .\scripts\build-offline.ps1
    .\scripts\build-offline.ps1 -Version 1.2.0 -SkipTests
#>
[CmdletBinding()]
param(
    [string]$Version = "1.1.0",
    [string]$JavaHome = "C:\tools\jdk-17.0.20.1+1",
    [string]$MavenHome = "C:\tools\apache-maven-3.9.16",
    [string]$NodeHome = "C:\tools\node-v20.18.0-win-x64",
    [switch]$SkipTests,
    [switch]$SkipLauncher,
    [switch]$SkipSmoke,
    [switch]$RebuildOnly
)

$ErrorActionPreference = 'Stop'
$RepoRoot = Split-Path -Parent $PSScriptRoot
$DistDir  = Join-Path $RepoRoot 'offline-dist'
$PkgName  = '清源QMS_单机版'
$PkgDir   = Join-Path $DistDir $PkgName
$ZipPath  = Join-Path $DistDir ("清源QMS_单机版_v{0}.zip" -f $Version)
$ExeName  = '清源QMS.exe'

function Write-Step([string]$msg) { Write-Host "`n========== $msg ==========" -ForegroundColor Cyan }
function Die([string]$msg) { Write-Host "构建失败：$msg" -ForegroundColor Red; exit 1 }

# ---------- 0. 工具链 ----------
Write-Step '0. 检查工具链'
if (-not (Test-Path (Join-Path $JavaHome 'bin\java.exe'))) { Die "JAVA_HOME 无效：$JavaHome（可用 -JavaHome 指定）" }
$env:JAVA_HOME = $JavaHome
if ($NodeHome -and (Test-Path $NodeHome)) { $env:PATH = "$NodeHome;$env:JAVA_HOME\bin;$env:PATH" }
$mvn = Join-Path $MavenHome 'bin\mvn.cmd'

if (-not $RebuildOnly) {
    # ---------- 1. 后端 ----------
    Write-Step '1. 后端构建 mvn clean package'
    if (-not (Test-Path $mvn)) { Die "找不到 Maven：$mvn（可用 -MavenHome 指定）" }
    Push-Location (Join-Path $RepoRoot 'backend')
    try {
        $mvnArgs = @('clean', 'package')
        if ($SkipTests) { $mvnArgs += '-DskipTests' }
        # 原生命令（mvn/npm/pyinstaller）会把进度日志写到 stderr，
        # 在 Stop 首选项下会被误判为终止错误；子作用域内放宽并合并 stderr，
        # 真实成败一律以 $LASTEXITCODE 为准。
        & { $ErrorActionPreference = 'Continue'; & $mvn @mvnArgs 2>&1 | Out-Host }
        if ($LASTEXITCODE -ne 0) { Die '后端构建失败' }
    } finally { Pop-Location }
    $jar = Join-Path $RepoRoot 'backend\target\qingyuan-qms-backend.jar'
    if (-not (Test-Path $jar)) { Die "未找到后端产物：$jar" }

    # ---------- 2. 前端 ----------
    Write-Step '2. 前端构建 npm run build'
    Push-Location (Join-Path $RepoRoot 'frontend')
    try {
        if (-not (Test-Path 'node_modules')) {
            & { $ErrorActionPreference = 'Continue'; npm ci 2>&1 | Out-Host }
            if ($LASTEXITCODE -ne 0) { Die 'npm ci 失败' }
        }
        & { $ErrorActionPreference = 'Continue'; npm run build 2>&1 | Out-Host }
        if ($LASTEXITCODE -ne 0) { Die '前端构建失败' }
    } finally { Pop-Location }
    if (-not (Test-Path (Join-Path $RepoRoot 'frontend\dist\index.html'))) { Die '未找到前端产物 frontend\dist\index.html' }

    # ---------- 3. 启动器 ----------
    if (-not $SkipLauncher) {
        Write-Step '3. 无黑窗 App 构建 PyInstaller（windowed, 入口 app_gui.py）'
        Push-Location (Join-Path $RepoRoot 'offline-build')
        try {
            python -m py_compile 'launcher_qms.py' 'app_gui.py'
            if ($LASTEXITCODE -ne 0) { Die '启动器脚本语法检查失败' }
            $pyi = Get-Command pyinstaller -ErrorAction SilentlyContinue
            if (-not $pyi) {
                python -m PyInstaller --version > $null
                if ($LASTEXITCODE -ne 0) { Die '未安装 pyinstaller（pip install pyinstaller），或改用 -SkipLauncher 复用现有 exe' }
                & { $ErrorActionPreference = 'Continue'; python -m PyInstaller --noconfirm --clean '品控工作台.spec' 2>&1 | Out-Host }
            } else {
                & { $ErrorActionPreference = 'Continue'; pyinstaller --noconfirm --clean '品控工作台.spec' 2>&1 | Out-Host }
            }
            if ($LASTEXITCODE -ne 0) { Die '启动器构建失败' }
        } finally { Pop-Location }
    } else {
        Write-Step '3. 跳过启动器构建（-SkipLauncher，复用现有 exe）'
    }
} else {
    Write-Step '跳过全部构建（-RebuildOnly），仅重新组装压缩'
}

# ---------- 4. 组装单机目录 ----------
Write-Step '4. 组装单机版目录'
$srcExe = Join-Path $RepoRoot "offline-build\dist\$ExeName"
if (-not (Test-Path $srcExe)) { Die "启动器产物不存在：$srcExe（源码初次构建请不要加 -SkipLauncher，或手工放置 exe）" }
if (-not (Test-Path (Join-Path $PkgDir 'runtime'))) {
    Die "组装目录缺少 runtime（内置 JRE/MariaDB/Redis）：$(Join-Path $PkgDir 'runtime')；请先放置便携运行时再构建"
}

New-Item -ItemType Directory -Force -Path (Join-Path $PkgDir 'app') | Out-Null

# 4.1 静态资源：先删后拷，避免旧版本 hash 文件残留
$staticDst = Join-Path $PkgDir 'app\static'
if (Test-Path $staticDst) { Remove-Item $staticDst -Recurse -Force }
Copy-Item (Join-Path $RepoRoot 'frontend\dist') $staticDst -Recurse

# 4.2 jar
Copy-Item (Join-Path $RepoRoot 'backend\target\qingyuan-qms-backend.jar') (Join-Path $PkgDir 'app\qingyuan-qms-backend.jar') -Force

# 4.3 启动器
Copy-Item $srcExe (Join-Path $PkgDir $ExeName) -Force

# 4.4 文档（仓库根为权威版本）
Copy-Item (Join-Path $RepoRoot 'offline-build\使用说明.txt') (Join-Path $PkgDir '使用说明.txt') -Force
Copy-Item (Join-Path $RepoRoot 'CHANGELOG.txt') (Join-Path $PkgDir 'CHANGELOG.txt') -Force

# 4.5 出厂清理：不带演示数据、日志与运行期临时文件
foreach ($d in @('data', 'logs', 'log', 'offline-build')) {
    $p = Join-Path $PkgDir $d
    if (Test-Path $p) { Remove-Item $p -Recurse -Force }
}
Get-ChildItem $PkgDir -Recurse -File -Include *.log,*.err,*.rdb,*.aof,*.pid,*.sock -ErrorAction SilentlyContinue | Remove-Item -Force

# ---------- 5. 压缩 ----------
Write-Step '5. 压缩 zip'
Add-Type -AssemblyName System.IO.Compression.FileSystem
if (Test-Path $ZipPath) { Remove-Item $ZipPath -Force }
[System.IO.Compression.ZipFile]::CreateFromDirectory($PkgDir, $ZipPath, [System.IO.Compression.CompressionLevel]::Optimal, $true)

# ---------- 6. 冷启动冒烟 ----------
if (-not $SkipSmoke) {
    Write-Step '6. 全新解压冷启动冒烟（GUI窗口/无黑窗/双健康端点+业务探针/登录/假死自愈/关窗清理）'

    Add-Type -TypeDefinition @'
using System;
using System.Text;
using System.Collections.Generic;
using System.Runtime.InteropServices;
namespace QmsSmoke {
public class WinApi {
  public delegate bool EnumProc(IntPtr h, IntPtr l);
  [DllImport("user32.dll")] public static extern bool EnumWindows(EnumProc cb, IntPtr l);
  [DllImport("user32.dll", CharSet=CharSet.Unicode)] public static extern int GetWindowText(IntPtr h, StringBuilder s, int n);
  [DllImport("user32.dll")] public static extern bool IsWindowVisible(IntPtr h);
  [DllImport("user32.dll")] public static extern uint GetWindowThreadProcessId(IntPtr h, out uint pid);
  [DllImport("user32.dll")] public static extern bool PostMessage(IntPtr h, uint m, IntPtr w, IntPtr l);
  [DllImport("kernel32.dll", SetLastError=true)] public static extern IntPtr OpenProcess(uint a, bool i, int p);
  [DllImport("kernel32.dll")] public static extern bool CloseHandle(IntPtr h);
  [DllImport("ntdll.dll")] public static extern int NtSuspendProcess(IntPtr h);
  public static IntPtr FindQmsWindow(uint[] pids) {
    HashSet<uint> set = new HashSet<uint>(pids);
    IntPtr found = IntPtr.Zero;
    EnumWindows(delegate(IntPtr h, IntPtr l) {
      if (IsWindowVisible(h)) {
        uint pid; GetWindowThreadProcessId(h, out pid);
        if (set.Contains(pid)) {
          StringBuilder sb = new StringBuilder(256);
          GetWindowText(h, sb, 256);
          if (sb.ToString().StartsWith("清源QMS 品控工作台")) { found = h; return false; }
        }
      }
      return true;
    }, IntPtr.Zero);
    return found;
  }
}
}
'@

    # 冒烟目录特意放在用户目录下（路径含 C:\Users 这类“反斜杠+u”片段）：
    # Windows 版 Redis INFO 输出携带启动路径，可借此回归验证健康检查不依赖 INFO 解析。
    $smokeRoot = Join-Path $env:USERPROFILE ('.qms-smoke-' + (Get-Date -Format 'HHmmss'))
    $smoke = $smokeRoot
    if (Test-Path $smoke) { Remove-Item $smoke -Recurse -Force }
    [System.IO.Compression.ZipFile]::ExtractToDirectory($ZipPath, $smoke)
    $smokePkg = Join-Path $smoke $PkgName
    $smokeExe = Join-Path $smokePkg $ExeName

    # 只认本次解压目录里的进程，避免与机器上其它已安装实例互相误伤
    function Get-SmokeProcs {
        Get-CimInstance Win32_Process -ErrorAction SilentlyContinue |
            Where-Object { ($_.Name -eq 'java.exe' -or $_.Name -eq $ExeName) -and $_.ExecutablePath -and
                           $_.ExecutablePath.StartsWith($smokePkg, [StringComparison]::OrdinalIgnoreCase) }
    }

    $proc = Start-Process -FilePath $smokeExe -WorkingDirectory $smokePkg -PassThru
    $ok = $false
    try {
        $deadline = (Get-Date).AddSeconds(180)
        $health = $null; $actuator = $null
        while ((Get-Date) -lt $deadline) {
            if ($proc.HasExited) { Die "冒烟实例进程提前退出（现场保留）：$smoke" }
            try {
                # 业务健康端点（统一返回体 {code,data:{status:UP}}）
                $health = Invoke-RestMethod -Uri 'http://127.0.0.1:18080/api/v1/health' -TimeoutSec 3
                # 启动器实际轮询的 Actuator 端点（聚合各组件真实状态，必须根状态 UP）
                $actuator = Invoke-RestMethod -Uri 'http://127.0.0.1:18080/actuator/health' -TimeoutSec 3
                $bizStatus = $health.data.status; if (-not $bizStatus) { $bizStatus = $health.status }
                if ($bizStatus -eq 'UP' -and $actuator.status -eq 'UP') { break }
            } catch { Start-Sleep -Seconds 2 }
        }
        $bizStatus2 = $health.data.status; if (-not $bizStatus2) { $bizStatus2 = $health.status }
        if (-not $actuator -or $actuator.status -ne 'UP') { Die "180 秒内 /actuator/health 未 UP（现场保留）：$smoke" }
        if ($bizStatus2 -ne 'UP') { Die "/api/v1/health 未 UP（现场保留）：$smoke" }
        if ($actuator.components.redis.status -ne 'UP') { Die "Redis 健康分项非 UP（现场保留）：$smoke" }

        # v1.2.1：业务探针端点必须可用且 db/redis 双 UP（看门狗据此判定假死）
        $diag = Invoke-RestMethod -Uri 'http://127.0.0.1:18080/api/v1/diag/ping' -TimeoutSec 6
        if ("$($diag.code)" -ne '0' -or $diag.data.db.status -ne 'UP' -or $diag.data.redis.status -ne 'UP') {
            Die "业务探针 /diag/ping 异常：$($diag | ConvertTo-Json -Compress)（现场保留）：$smoke"
        }
        Write-Host '  双健康端点 UP，Redis PING 分项 UP，业务探针 db/redis UP' -ForegroundColor Green

        # GUI 窗口必须真实渲染；App 进程树不得挂 conhost（无黑窗）
        $appProcs = @(Get-CimInstance Win32_Process -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -eq $ExeName -and $_.ExecutablePath -and
                          $_.ExecutablePath.StartsWith($smokePkg, [StringComparison]::OrdinalIgnoreCase) })
        if ($appProcs.Count -eq 0) { Die "找不到 App 进程（现场保留）：$smoke" }
        $appPids = [uint32[]]($appProcs | ForEach-Object { $_.ProcessId })
        Start-Sleep -Seconds 2
        $hwnd = [QmsSmoke.WinApi]::FindQmsWindow($appPids)
        if ($hwnd -eq [IntPtr]::Zero) { Die "未找到 App 主窗口（GUI 未渲染？现场保留）：$smoke" }
        $conhost = @(Get-CimInstance Win32_Process -Filter "Name='conhost.exe'" -ErrorAction SilentlyContinue |
            Where-Object { $appPids -contains [uint32]$_.ParentProcessId })
        if ($conhost.Count -ne 0) { Die "App 进程挂有 conhost（出现黑窗回退），现场保留：$smoke" }
        Write-Host '  GUI 主窗口可见，进程树无 conhost（无黑窗）' -ForegroundColor Green

        # 登录冒烟
        $loginBody = @{ username = 'admin'; password = 'Qms@Demo2026' } | ConvertTo-Json
        $login = Invoke-RestMethod -Uri 'http://127.0.0.1:18080/api/v1/auth/login' -Method Post -ContentType 'application/json' -Body $loginBody -TimeoutSec 10
        if ("$($login.code)" -ne '0' -or -not $login.data.accessToken) { Die "登录冒烟失败（现场保留）：$smoke" }
        Write-Host '  admin 登录成功（data.accessToken 已签发）' -ForegroundColor Green

        # v1.2.1：假死自愈 —— 挂起后端 java（进程活着但完全不响应），
        # 看门狗应在约 1 分钟内完成“3 次探针失败 -> 现场快照 -> 重启恢复”
        $javaPid = (Get-SmokeProcs | Where-Object { $_.Name -eq 'java.exe' } | Select-Object -First 1).ProcessId
        if (-not $javaPid) { Die "找不到冒烟 java 后端进程（现场保留）：$smoke" }
        $hProc = [QmsSmoke.WinApi]::OpenProcess(0x0800, $false, [int]$javaPid)
        if ($hProc -eq [IntPtr]::Zero) { Die "OpenProcess 失败（现场保留）：$smoke" }
        try {
            $sr = [QmsSmoke.WinApi]::NtSuspendProcess($hProc)
            if ($sr -ne 0) { Die "NtSuspendProcess 失败 ntstatus=$sr（现场保留）：$smoke" }
        } finally { [void][QmsSmoke.WinApi]::CloseHandle($hProc) }
        Write-Host "  已挂起 java(pid=$javaPid) 模拟假死，等待看门狗自愈（最长 180 秒）..." -ForegroundColor Yellow
        $healed = $false
        $healDeadline = (Get-Date).AddSeconds(180)
        while ((Get-Date) -lt $healDeadline) {
            Start-Sleep -Seconds 5
            try {
                $d2 = Invoke-RestMethod -Uri 'http://127.0.0.1:18080/api/v1/diag/ping' -TimeoutSec 5
                if ("$($d2.code)" -eq '0' -and $d2.data.db.status -eq 'UP' -and $d2.data.redis.status -eq 'UP') { $healed = $true; break }
            } catch { }
        }
        if (-not $healed) { Die "假死自愈失败：180 秒内业务探针未恢复 UP（现场保留）：$smoke" }
        $snap = Get-ChildItem (Join-Path $smokePkg 'logs') -Directory -Filter 'freeze-*' -ErrorAction SilentlyContinue | Select-Object -First 1
        if (-not $snap -or -not (Test-Path (Join-Path $snap.FullName 'threaddump.txt')) -or -not (Test-Path (Join-Path $snap.FullName 'probe.txt'))) {
            Die "自愈已触发但假死现场快照不完整（现场保留）：$smoke"
        }
        Write-Host "  假死已自愈，现场快照：$($snap.Name)" -ForegroundColor Green

        # 关窗即退：向 App 主窗口发 WM_CLOSE（不再是控制台窗口）
        $hwnd2 = [QmsSmoke.WinApi]::FindQmsWindow($appPids)
        if ($hwnd2 -eq [IntPtr]::Zero) { $hwnd2 = $hwnd }
        [void][QmsSmoke.WinApi]::PostMessage($hwnd2, 0x0010, [IntPtr]::Zero, [IntPtr]::Zero)
        $exitDeadline = (Get-Date).AddSeconds(90)
        while ((Get-Date) -lt $exitDeadline) {
            if (@(Get-SmokeProcs).Count -eq 0) { break }
            Start-Sleep -Seconds 2
        }
        if (@(Get-SmokeProcs).Count -ne 0) {
            Write-Host '  优雅关停超时，强制结束冒烟实例' -ForegroundColor Yellow
            Get-SmokeProcs | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
        }
        Start-Sleep -Seconds 3
        $leftover = Get-NetTCPConnection -State Listen -LocalPort 18080,13306,16379 -ErrorAction SilentlyContinue
        if ($leftover) { Die "关停后端口仍被占用（现场保留）：$smoke" }
        $mariaErr = Join-Path $smokePkg 'logs\mariadb.err'
        if (-not (Test-Path $mariaErr) ) { Die "缺少 logs\mariadb.err（现场保留）：$smoke" }
        $errTail = (Get-Content $mariaErr -Tail 30) -join "`n"
        if ($errTail -notmatch 'Normal shutdown') { Die "MariaDB 非正常关闭（crash recovery 风险，现场保留）：$smoke" }
        $launcherLog = Get-Content (Join-Path $smokePkg 'logs\launcher.log') -Raw
        if ($launcherLog -notmatch '全部服务已停止') { Die "launcher.log 缺少完整关停记录（现场保留）：$smoke" }
        Write-Host '  关窗即退完成：三端口释放，MariaDB Normal shutdown，关停记录完整' -ForegroundColor Green
        $ok = $true
    } finally {
        if ($ok) {
            Remove-Item $smoke -Recurse -Force -ErrorAction SilentlyContinue
            Write-Host '  冒烟通过，临时目录已清理' -ForegroundColor Green
        } else {
            Write-Host "  冒烟失败，现场与日志保留在：$smokePkg\logs" -ForegroundColor Yellow
        }
    }
} else {
    Write-Step '6. 跳过冷启动冒烟（-SkipSmoke）'
}

# ---------- 7. 交付信息 ----------
$zip = Get-Item $ZipPath
$sha = (Get-FileHash $ZipPath -Algorithm SHA256).Hash
Write-Step '构建完成'
Write-Host "交付 zip ：$($zip.FullName)"
Write-Host ("大小     ：{0:N1} MB" -f ($zip.Length / 1MB))
Write-Host "SHA256   ：$sha"

