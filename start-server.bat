@echo off
title Auction Socket Server (Port 8888)
echo ==================================================
echo   DANG KHOI DONG AUCTION SOCKET SERVER
echo ==================================================

if not exist "target\classes\network\AuctionServer.class" (
    echo [1/2] Dang bien dich ma nguon qua Maven...
    call mvn compile
)

echo [2/2] Dang chay Server tai cong 8888...
java -cp target/classes network.AuctionServer
pause
