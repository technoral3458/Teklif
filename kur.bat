@echo off
chcp 65001 >nul
title Teklif - Ilk Kurulum
cd /d "%~dp0"

echo ============================================================
echo   TEKLIF - YEREL KURULUM (sadece ilk seferde calistirin)
echo ============================================================
echo.

REM --- Python kontrolu ---
python --version >nul 2>&1
if errorlevel 1 (
    echo [HATA] Python bulunamadi.
    echo Lutfen https://www.python.org/downloads/ adresinden Python 3.11+ kurun
    echo ve kurulumda "Add Python to PATH" secenegini isaretleyin.
    pause
    exit /b 1
)

REM --- Node kontrolu ---
node --version >nul 2>&1
if errorlevel 1 (
    echo [HATA] Node.js bulunamadi.
    echo Lutfen https://nodejs.org/ adresinden LTS surumunu kurun.
    pause
    exit /b 1
)

echo [1/4] Backend sanal ortami olusturuluyor...
cd backend
if not exist venv (
    python -m venv venv
)
call venv\Scripts\activate.bat

echo [2/4] Python paketleri kuruluyor...
python -m pip install --upgrade pip >nul
pip install -r requirements.txt
if errorlevel 1 (
    echo [HATA] Python paketleri kurulamadi.
    pause
    exit /b 1
)

echo [3/4] Veritabani hazirlaniyor ve demo veriler ekleniyor...
python manage.py migrate
python seed_data.py
cd ..

echo [4/4] Frontend paketleri kuruluyor (bu biraz surebilir)...
cd frontend
call npm install
if errorlevel 1 (
    echo [HATA] Frontend paketleri kurulamadi.
    pause
    exit /b 1
)
cd ..

echo.
echo ============================================================
echo   KURULUM TAMAMLANDI!
echo   Artik "baslat.bat" dosyasina cift tiklayarak programi
echo   acabilirsiniz.
echo.
echo   Giris bilgileri:
echo     Yonetici : admin / admin123
echo     CNC      : cnc   / cnc123
echo ============================================================
pause
