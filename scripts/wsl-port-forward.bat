@echo off
REM 点餐系统 - 宿主机端口转发安装脚本
REM 以管理员身份运行一次即可
REM 将 Windows 8080 端口转发到 WSL2

echo === 获取 WSL2 IP ===
for /f "tokens=1" %%i in ('wsl hostname -I') do set WSL_IP=%%i
echo WSL2 IP: %WSL_IP%

echo === 设置端口转发 8080 → WSL2 ===
netsh interface portproxy delete v4tov4 listenport=8080 listenaddress=0.0.0.0 >nul 2>&1
netsh interface portproxy add v4tov4 listenport=8080 listenaddress=0.0.0.0 connectport=8080 connectaddress=%WSL_IP%

echo === 验证 ===
netsh interface portproxy show v4tov4 | findstr :8080

echo.
echo 完成！现在可以访问 http://localhost:8080
echo 如需删除转发规则，以管理员身份运行:
echo   netsh interface portproxy delete v4tov4 listenport=8080

pause
