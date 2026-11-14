package com.auction.ui;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.control.Label;
import javafx.util.Duration;

public class TimelineCard {
    private Timeline countdownTimeLine;

    //Bắt đầu đếm ngược và tự động cập nhật thời gian
    public void startCountdown(LocalDateTime endTime, Label lblTime){
        stop();

        countdownTimeLine = new Timeline(new KeyFrame(Duration.seconds(1), event -> {
            update(endTime, lblTime);
        }));
        countdownTimeLine.setCycleCount(Timeline.INDEFINITE);
        countdownTimeLine.play();
        
        update(endTime, lblTime);
    }
    //update thời gian
    private void update(LocalDateTime endTime, Label lblTime){
        LocalDateTime now = LocalDateTime.now();
        long hours = ChronoUnit.HOURS.between(now, endTime);
        long minutes = ChronoUnit.MINUTES.between(now, endTime) % 60;
        long seconds = ChronoUnit.SECONDS.between(now, endTime) % 60;

        if (hours <= 0 && minutes <= 0 && seconds <= 0) {
            lblTime.setText("Đã kết thúc");
            lblTime.setStyle("-fx-text-fill: #9ca3af;"); // Màu xám khi hết giờ
            stop();
        } else {
            String timeFormatted = String.format("%02dh %02dm %02ds", hours, minutes, seconds);
            lblTime.setText(timeFormatted);
        }
    }
    //Hàm stop sau khi phiên hết hạn
    public void stop(){
        if(countdownTimeLine != null){
            countdownTimeLine.stop();
        }
    }
}
