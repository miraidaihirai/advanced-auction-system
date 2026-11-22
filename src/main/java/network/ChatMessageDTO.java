package network;

/**
 * Bản ghi tin nhắn phòng đấu giá truyền qua Socket cho UI.
 */
public record ChatMessageDTO(
        String auctionId,
        String senderId,
        String senderName,
        String role,
        String timestamp,
        String message) {
}
