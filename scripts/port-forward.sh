#!/bin/bash
# 点餐系统 WSL2 端口转发刷新脚本
# 在 WSL 内运行（需要 Windows 管理员权限，会弹出 UAC 对话框）

WSL_IP=$(hostname -I | awk '{print $1}')

# 通过 PowerShell 启动管理员权限的端口转发
powershell.exe -Command "
Start-Process powershell -Verb RunAs -ArgumentList '-NoProfile -Command \"
netsh interface portproxy delete v4tov4 listenport=8080 listenaddress=0.0.0.0 2>&1 | Out-Null;
netsh interface portproxy add v4tov4 listenport=8080 listenaddress=0.0.0.0 connectport=8080 connectaddress=$WSL_IP 2>&1;
Write-Host \"Port forwarding 8080 → ${WSL_IP}:8080 OK\";
netsh interface portproxy show v4tov4 | findstr :8080
\""
