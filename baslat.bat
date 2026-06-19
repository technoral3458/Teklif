@echo off
chcp 65001 >nul
title Teklif - Baslatici
cd /d "%~dp0"

if not exist backend\venv (
    echo [UYARI] Kurulum yapilmamis. Once "kur.bat" dosyasini calistirin.
    pause
    exit /b 1
)

echo Teklif baslatiliyor...

REM --- Backend: veritabanini guncelle ve sunucuyu baslat ---
start "Teklif Backend" cmd /k "cd /d "%~dp0backend" && call venv\Scripts\activate.bat && python manage.py migrate && python manage.py runserver"

REM --- Frontend: arayuzu baslat ---
start "Teklif Arayuz" cmd /k "cd /d "%~dp0frontend" && npm run dev"

REM --- Tarayiciyi ac ---
echo Tarayici aciliyor (birkac saniye bekleyin)...
timeout /t 6 >nul
start http://localhost:3000

echo.
echo Program acildi. Bu pencereyi kapatabilirsiniz.
echo Programi kapatmak icin acilan "Teklif Backend" ve "Teklif Arayuz"
echo pencerelerini kapatin.
timeout /t 4 >nul
