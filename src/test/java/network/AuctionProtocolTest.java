package network;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

public class AuctionProtocolTest {
  @Test
  void formatBidNotification_shouldFormatCorrectly() {
    String result =
        AuctionProtocol.formatBidNotification("AUC-001", 15000000, "BIDDER-01", "ITEM-01");

    assertEquals("NOTIFY BID AUC-001 15000000.00 BIDDER-01 ITEM-01", result);
  }

  @Test
  void formatSuccess_shouldHandleNullDetails() {
    String result = AuctionProtocol.formatSuccess("LOGIN", null);
    assertEquals("SUCCESS LOGIN", result);
  }

  @Test
  void formatSuccess_shouldHandleBlankDetails() {
    String result = AuctionProtocol.formatSuccess("LOGIN", " ");
    assertEquals("SUCCESS LOGIN", result);
  }

  @Test
  void formatError_shouldReturnCorrectMessage() {
    String result = AuctionProtocol.formatError("Invalid user");
    assertEquals("ERROR Invalid user", result);
  }
}
