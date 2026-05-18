# MinIO 启动与关闭指南

## 1. 概述

MinIO 是一个高性能的对象存储服务，兼容 Amazon S3 API。WeLink 项目使用 MinIO 存储用户上传的图片和文件。

**默认端口：**
- API 端口：9000
- Console 端口：9001

---

## 2. 前置准备

### 2.1 下载 MinIO

访问 MinIO 官网下载 Windows 版本：

```powershell
# 使用 PowerShell 下载
Invoke-WebRequest -Uri "https://dl.min.io/server/minio/release/windows-amd64/minio.exe" -OutFile "C:\minio\minio.exe"
```

或从官网手动下载：https://min.io/download#/windows

### 2.2 创建目录

```powershell
# 创建数据目录
mkdir C:\minio\data

# 创建配置目录（可选）
mkdir C:\minio\config
```

---

## 3. 启动 MinIO

### 3.1 命令行启动（推荐用于开发）

#### 3.1.1 基本启动

```powershell
# 进入 MinIO 目录
cd C:\minio

# 启动 MinIO
.\minio.exe server C:\minio\data --console-address ":9001"
 ./bin/minio.exe server /mnt/D/MinIO/data --console-address ":9001"
```

启动成功后会输出：

```
API: http://192.168.x.x:9000  http://127.0.0.1:9000
Console: http://192.168.x.x:9001 http://127.0.0.1:9001

Documentation: https://min.io/docs/minio/linux/index.html
Warning: The standard parity is set to 0. This can lead to data loss.
```

#### 3.1.2 指定端口启动

```powershell
# 指定 API 端口为 9000，控制台端口为 9001
.\minio.exe server C:\minio\data --address ":9000" --console-address ":9001"
./bin/minio.exe server /mnt/D/MinIO/data --address ":9000" --console-address ":9001"
```

#### 3.1.3 设置访问密钥

```powershell
# 设置环境变量
$env:MINIO_ROOT_USER = "minioadmin"
$env:MINIO_ROOT_PASSWORD = "minioadmin"

# 启动 MinIO
.\minio.exe server C:\minio\data --console-address ":9001"
```

### 3.2 后台启动

#### 3.2.1 使用 Start-Process

```powershell
# 后台启动 MinIO
Start-Process -FilePath "C:\minio\minio.exe" -ArgumentList "server C:\minio\data --address `":9000`" --console-address `":9001`"" -WindowStyle Hidden

# 验证是否启动成功
curl http://localhost:9000/minio/health/live
```

#### 3.2.2 使用批处理文件

创建 `start-minio.bat`：

```batch
@echo off
echo Starting MinIO...
cd C:\minio
start "MinIO" /MIN minio.exe server data --address ":9000" --console-address ":9001"
echo MinIO started at http://localhost:9000
echo Console at http://localhost:9001
timeout /t 3
```

双击运行即可后台启动。

### 3.3 创建 Windows 服务（开机自启）

#### 3.3.1 下载 NSSM

```powershell
# 下载 NSSM
Invoke-WebRequest -Uri "https://nssm.cc/release/nssm-2.24.zip" -OutFile "C:\nssm.zip"

# 解压
Expand-Archive -Path "C:\nssm.zip" -DestinationPath "C:\nssm"
```

#### 3.3.2 创建服务

```powershell
# 安装服务
C:\nssm\win64\nssm.exe install MinIO
```

在弹出的 GUI 中配置：

| 选项卡 | 配置项 | 值 |
|--------|--------|-----|
| Application | Path | `C:\minio\minio.exe` |
| Application | Arguments | `server C:\minio\data --address ":9000" --console-address ":9001"` |
| Application | Startup directory | `C:\minio` |
| Details | Display name | `MinIO Object Storage` |
| Details | Description | `MinIO 对象存储服务` |
| Log on | This account | 留空（使用本地系统账户） |

#### 3.3.3 管理服务

```powershell
# 启动服务
net start MinIO

# 停止服务
net stop MinIO

# 重启服务
net restart MinIO

# 查看服务状态
sc query MinIO

# 删除服务
C:\nssm\win64\nssm.exe remove MinIO confirm
```

### 3.4 使用 PowerShell 脚本启动

创建 `Start-MinIO.ps1`：

```powershell
# MinIO 启动脚本

$MinIOPath = "C:\minio"
$DataPath = "C:\minio\data"
$ApiPort = "9000"
$ConsolePort = "9001"

# 检查 MinIO 是否已安装
if (-not (Test-Path "$MinIOPath\minio.exe")) {
    Write-Error "MinIO not found at $MinIOPath"
    exit 1
}

# 检查数据目录
if (-not (Test-Path $DataPath)) {
    Write-Host "Creating data directory..."
    New-Item -ItemType Directory -Path $DataPath -Force
}

# 检查是否已运行
$process = Get-Process -Name "minio" -ErrorAction SilentlyContinue
if ($process) {
    Write-Host "MinIO is already running (PID: $($process.Id))"
    exit 0
}

# 设置环境变量
$env:MINIO_ROOT_USER = "minioadmin"
$env:MINIO_ROOT_PASSWORD = "minioadmin"

# 启动 MinIO
Write-Host "Starting MinIO..."
Start-Process -FilePath "$MinIOPath\minio.exe" `
    -ArgumentList "server $DataPath --address `":$ApiPort`" --console-address `":$ConsolePort`"" `
    -WindowStyle Normal

# 等待启动
Start-Sleep -Seconds 3

# 验证启动
try {
    $response = Invoke-WebRequest -Uri "http://localhost:$ApiPort/minio/health/live" -UseBasicParsing
    if ($response.StatusCode -eq 200) {
        Write-Host "MinIO started successfully!"
        Write-Host "API: http://localhost:$ApiPort"
        Write-Host "Console: http://localhost:$ConsolePort"
        Write-Host "Username: minioadmin"
        Write-Host "Password: minioadmin"
    }
} catch {
    Write-Error "Failed to start MinIO"
}
```

运行脚本：

```powershell
# 允许执行脚本（首次需要）
Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser

# 执行脚本
.\Start-MinIO.ps1
```

---

## 4. 关闭 MinIO

### 4.1 命令行启动的关闭方式

```powershell
# 在运行 MinIO 的终端中按 Ctrl+C

# 或查找进程并终止
Get-Process -Name "minio" | Stop-Process -Force
```

### 4.2 后台启动的关闭方式

```powershell
# 方式一：通过进程名关闭
Get-Process -Name "minio" -ErrorAction SilentlyContinue | Stop-Process -Force

# 方式二：通过端口查找并关闭
$port = 9000
$pid = (Get-NetTCPConnection -LocalPort $port -ErrorAction SilentlyContinue).OwningProcess
if ($pid) {
    Stop-Process -Id $pid -Force
    Write-Host "MinIO stopped (PID: $pid)"
} else {
    Write-Host "MinIO is not running on port $port"
}

# 方式三：使用 taskkill
taskkill /F /IM minio.exe
```

### 4.3 Windows 服务的关闭方式

```powershell
# 停止服务
net stop MinIO

# 或使用 PowerShell
Stop-Service -Name MinIO

# 查看服务状态
Get-Service -Name MinIO
```

### 4.4 使用 PowerShell 脚本关闭

创建 `Stop-MinIO.ps1`：

```powershell
# MinIO 关闭脚本

Write-Host "Stopping MinIO..."

# 检查是否运行在服务模式
$service = Get-Service -Name "MinIO" -ErrorAction SilentlyContinue
if ($service -and $service.Status -eq "Running") {
    Write-Host "Stopping MinIO service..."
    Stop-Service -Name MinIO
    Write-Host "MinIO service stopped"
    exit 0
}

# 检查进程
$process = Get-Process -Name "minio" -ErrorAction SilentlyContinue
if ($process) {
    Write-Host "Stopping MinIO process (PID: $($process.Id))..."
    Stop-Process -Id $process.Id -Force
    Write-Host "MinIO process stopped"
} else {
    Write-Host "MinIO is not running"
}

# 验证端口是否释放
Start-Sleep -Seconds 2
$port = Get-NetTCPConnection -LocalPort 9000 -ErrorAction SilentlyContinue
if ($port) {
    Write-Warning "Port 9000 is still in use"
} else {
    Write-Host "Port 9000 is free"
}
```

运行脚本：

```powershell
.\Stop-MinIO.ps1
```

---

## 5. 一键启停脚本

### 5.1 创建管理脚本

创建 `MinIO-Manager.ps1`：

```powershell
# MinIO 管理脚本

param(
    [Parameter(Mandatory=$true)]
    [ValidateSet("start", "stop", "status", "restart")]
    [string]$Action
)

$MinIOPath = "C:\minio"
$ApiPort = 9000
$ConsolePort = 9001

function Start-MinIO {
    Write-Host "`n=== Starting MinIO ===" -ForegroundColor Green
    
    # 检查是否已运行
    $process = Get-Process -Name "minio" -ErrorAction SilentlyContinue
    if ($process) {
        Write-Host "MinIO is already running (PID: $($process.Id))" -ForegroundColor Yellow
        return
    }
    
    # 设置环境变量
    $env:MINIO_ROOT_USER = "minioadmin"
    $env:MINIO_ROOT_PASSWORD = "minioadmin"
    
    # 启动
    Start-Process -FilePath "$MinIOPath\minio.exe" `
        -ArgumentList "server $MinIOPath\data --address `":$ApiPort`" --console-address `":$ConsolePort`"" `
        -WindowStyle Normal
    
    Start-Sleep -Seconds 3
    
    # 验证
    try {
        $response = Invoke-WebRequest -Uri "http://localhost:$ApiPort/minio/health/live" -UseBasicParsing
        if ($response.StatusCode -eq 200) {
            Write-Host "MinIO started successfully!" -ForegroundColor Green
            Write-Host "API: http://localhost:$ApiPort" -ForegroundColor Cyan
            Write-Host "Console: http://localhost:$ConsolePort" -ForegroundColor Cyan
        }
    } catch {
        Write-Error "Failed to start MinIO"
    }
}

function Stop-MinIO {
    Write-Host "`n=== Stopping MinIO ===" -ForegroundColor Red
    
    $process = Get-Process -Name "minio" -ErrorAction SilentlyContinue
    if ($process) {
        Stop-Process -Id $process.Id -Force
        Write-Host "MinIO stopped" -ForegroundColor Green
    } else {
        Write-Host "MinIO is not running" -ForegroundColor Yellow
    }
}

function Get-MinIOStatus {
    Write-Host "`n=== MinIO Status ===" -ForegroundColor Cyan
    
    $process = Get-Process -Name "minio" -ErrorAction SilentlyContinue
    if ($process) {
        Write-Host "Status: Running" -ForegroundColor Green
        Write-Host "PID: $($process.Id)" -ForegroundColor Cyan
        Write-Host "API: http://localhost:$ApiPort" -ForegroundColor Cyan
        Write-Host "Console: http://localhost:$ConsolePort" -ForegroundColor Cyan
    } else {
        Write-Host "Status: Stopped" -ForegroundColor Red
    }
}

# 执行操作
switch ($Action) {
    "start" { Start-MinIO }
    "stop" { Stop-MinIO }
    "status" { Get-MinIOStatus }
    "restart" {
        Stop-MinIO
        Start-Sleep -Seconds 2
        Start-MinIO
    }
}
```

### 5.2 使用管理脚本

```powershell
# 启动 MinIO
.\MinIO-Manager.ps1 -Action start

# 停止 MinIO
.\MinIO-Manager.ps1 -Action stop

# 查看状态
.\MinIO-Manager.ps1 -Action status

# 重启 MinIO
.\MinIO-Manager.ps1 -Action restart
```

---

## 6. 创建存储桶

### 6.1 使用浏览器

1. 访问 http://localhost:9001
2. 登录（用户名/密码：`minioadmin`/`minioadmin`）
3. 点击左侧菜单 "Buckets"
4. 点击 "Create Bucket"
5. 输入 Bucket 名称：`welink`
6. 点击 "Create"

### 6.2 使用 MinIO 客户端（mc）

```powershell
# 下载 mc 客户端
Invoke-WebRequest -Uri "https://dl.min.io/client/mc/release/windows-amd64/mc.exe" -OutFile "C:\minio\mc.exe"

# 配置别名
.\mc.exe alias set welink http://localhost:9000 minioadmin minioadmin

# 创建存储桶
.\mc.exe mb welink/welink

# 设置公开访问权限
.\mc.exe anonymous set public welink/welink

# 验证
.\mc.exe ls welink/welink
```

---

## 7. 验证 MinIO 状态

### 7.1 健康检查

```powershell
# API 健康检查
curl http://localhost:9000/minio/health/live

# 集群健康检查
curl http://localhost:9000/minio/health/cluster

# 就绪检查
curl http://localhost:9000/minio/health/ready
```

### 7.2 查看日志

```powershell
# 如果使用服务方式
Get-EventLog -LogName Application -Source MinIO -Newest 20

# 如果重定向了日志
Get-Content C:\minio\minio.log -Tail 50
```

---

## 8. 常见问题

### 8.1 端口被占用

**问题：** `listen tcp :9000: bind: address already in use`

**解决方案：**

```powershell
# 查找占用端口的进程
netstat -ano | findstr :9000

# 或使用 PowerShell
Get-NetTCPConnection -LocalPort 9000 | Select-Object OwningProcess

# 终止进程
taskkill /PID <PID> /F

# 或使用其他端口
.\minio.exe server data --address ":9002" --console-address ":9003"
```

### 8.2 无法访问控制台

**问题：** 浏览器无法打开 http://localhost:9001

**解决方案：**

```powershell
# 检查端口是否监听
netstat -an | findstr :9001

# 检查防火墙
Get-NetFirewallRule | Where-Object {$_.DisplayName -like "*minio*"}

# 添加防火墙规则
New-NetFirewallRule -DisplayName "MinIO" -Direction Inbound -LocalPort 9000,9001 -Protocol TCP -Action Allow
```

### 8.3 数据目录权限问题

**问题：** `Unable to write to the data directory`

**解决方案：**

```powershell
# 修改目录权限
icacls C:\minio\data /grant Everyone:F

# 或以管理员身份运行 MinIO
Start-Process powershell -Verb RunAs
```

### 8.4 服务启动失败

**问题：** Windows 服务无法启动

**解决方案：**

```powershell
# 查看服务错误
Get-Service -Name MinIO | Select-Object *

# 重新安装服务
C:\nssm\win64\nssm.exe remove MinIO confirm
C:\nssm\win64\nssm.exe install MinIO

# 检查 NSSM 日志
Get-Content C:\nssm\MinIO.err.log -Tail 50
```

---

## 9. 快速参考

### 9.1 常用命令速查

| 操作 | 命令 |
|------|------|
| 启动 | `.\minio.exe server C:\minio\data --console-address ":9001"` |
| 停止 | `Get-Process minio \| Stop-Process -Force` |
| 重启 | 先停止再启动 |
| 状态 | `Get-Process minio` |
| 健康检查 | `curl http://localhost:9000/minio/health/live` |
| 访问 API | http://localhost:9000 |
| 访问控制台 | http://localhost:9001 |

### 9.2 默认配置

| 配置项 | 默认值 |
|--------|--------|
| API 端口 | 9000 |
| Console 端口 | 9001 |
| 用户名 | minioadmin |
| 密码 | minioadmin |
| 数据目录 | C:\minio\data |
| 存储桶名称 | welink |

---

## 10. 安全建议

1. **修改默认密码** - 生产环境务必修改 MINIO_ROOT_USER 和 MINIO_ROOT_PASSWORD
2. **启用 HTTPS** - 使用 TLS 证书加密传输
3. **配置防火墙** - 仅允许必要的 IP 访问
4. **定期备份** - 备份数据目录和配置
5. **监控日志** - 定期检查 MinIO 日志文件
