@echo off
title BidX — Luxury Live Auction Client (JavaFX)
echo ==================================================
echo   DANG KHOI DONG GIAO DIEN BIDX CLIENT (JAVAFX)
echo ==================================================
if exist "target\OnlineAuctionApp.jar" (
    echo Khoi dong sieu toc tu target\OnlineAuctionApp.jar...
    java -jar target\OnlineAuctionApp.jar
) else (
    echo Khoi dong qua Maven JavaFX...
    call mvn javafx:run
)
pause
