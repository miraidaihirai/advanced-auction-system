package com.auction.model;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Bản ghi tin nhắn thảo luận trực tiếp trong một phòng đấu giá (lưu bền vững theo phiên).
 */
public class ChatMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String auctionId;
    private final String senderId;
    private final String senderName;
    private final String role;
    private final String message;
    private final LocalDateTime timestamp;

    public ChatMessage(String auctionId, String senderId, String senderName, String role, String message) {
        this(auctionId, senderId, senderName, role, message, LocalDateTime.now());
    }

    public ChatMessage(
            String auctionId,
            String senderId,
            String senderName,
            String role,
            String message,
            LocalDateTime timestamp) {
        this.auctionId = auctionId != null ? auctionId : "";
        this.senderId = senderId != null ? senderId : "";
        this.senderName = senderName != null ? senderName : "";
        this.role = role != null ? role : "BIDDER";
        this.message = message != null ? message : "";
        this.timestamp = timestamp != null ? timestamp : LocalDateTime.now();
    }

    public String getAuctionId() {
        return auctionId;
    }

    public String getSenderId() {
        return senderId;
    }

    public String getSenderName() {
        return senderName;
    }

    public String getRole() {
        return role;
    }

    public String getMessage() {
        return message;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }
}
