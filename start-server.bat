@echo off
title BidX Socket Server (Port 8888)
echo ==================================================
echo   DANG KHOI DONG BIDX AUCTION SOCKET SERVER
echo ==================================================

echo [1/2] Dang bien dich ma nguon moi nhat qua Maven...
call mvn -q compile
if errorlevel 1 (
    echo [LOI] Bien dich that bai! Kiem tra lai ma nguon hoac Maven.
    pause
    exit /b 1
)

echo [2/2] Dang chay BidX Server tai cong 8888...
java -cp target/classes network.AuctionServer
pause
