
package com.auction.UI;

import java.util.Locale;

import com.auction.sessions.ClientSession;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import network.AuctionClient;

public class AuctionController implements
        SessionAware,
        ViewLifecycle,
        AuctionClient.AuctionEventListener {
    // SessionAware nhaanj SocketClient dùng chung từ ViewManager
    // ViewLifecycle quản lí Auction screen xuất hiện hoặc bị ẩn
    // AuctionEventListener nhận thông báo từ Server
    private volatile boolean joinPending = false;

    @FXML
    private void handlePlaceBid() {
        statusLabel.setText("Place Bid button clicked.");
    }

    @FXML
    private void handleBackToHome() {
        ViewManager.SwitchView("Home");
    }

    @FXML
    private void handleJoinAuction() {
        if (clientSession == null || !clientSession.isConnected()) {
            statusLabel.setText("Server is not connected.");
            return;
        }

        String auctionId = auctionIdField.getText().trim();

        if (auctionId.isEmpty()) {
            statusLabel.setText("Please enter an Auction ID.");
            return;
        }

        if (joinPending) {
            return;
        }

        joinPending = true;
        statusLabel.setText("Joining auction...");

        try {
            clientSession.getClient().joinAuction(auctionId);
        } catch (RuntimeException e) {
            joinPending = false;
            statusLabel.setText("Failed to join: " + e.getMessage());
        }
    }

    @Override
    public void onMessageReceived(String rawMessage) {
        if (!joinPending) {
            return;
        }

        if (rawMessage.startsWith("SUCCESS JOIN ")) {
            handleJoinSuccess(rawMessage);
        } else if (rawMessage.startsWith("ERROR ")) {
            joinPending = false;

            String errorMessage = rawMessage.substring("ERROR ".length());

            Platform.runLater(() -> {
                if (viewActive) {
                    statusLabel.setText("Join failed: " + errorMessage);
                }
            });
        }
    }

    private void handleJoinSuccess(String rawMessage) {
        joinPending = false;

        String[] parts = rawMessage.trim().split("\\s+");

        if (parts.length < 7) {
            Platform.runLater(() -> {
                if (viewActive) {
                    statusLabel.setText("Invalid server response.");
                }
            });
            return;
        }

        String auctionId = parts[2];

        String itemId = String.join(
                " ",
                java.util.Arrays.copyOfRange(parts, 3, parts.length - 3));
        // Xử lí item có dấu cách
        try {
            double currentPrice = Double.parseDouble(parts[parts.length - 3]);

            double stepPrice = Double.parseDouble(parts[parts.length - 2]);

            String auctionStatus = parts[parts.length - 1];

            Platform.runLater(() -> {
                if (!viewActive) {
                    return;
                }

                auctionInfoLabel.setText(String.format(
                        Locale.US,
                        "Auction ID: %s\nItem: %s\nCurrent Price: %,.2f\nStep Price: %,.2f\nStatus: %s",
                        auctionId,
                        itemId,
                        currentPrice,
                        stepPrice,
                        auctionStatus));

                statusLabel.setText("Successfully joined auction.");
            });

        } catch (NumberFormatException e) {
            Platform.runLater(() -> {
                if (viewActive) {
                    statusLabel.setText("Invalid auction information from server.");
                }
            });
        }
    }

    @FXML
    private TextField auctionIdField;

    @FXML
    private TextField bidAmountField;

    @FXML
    private Label auctionInfoLabel;

    @FXML
    private Label statusLabel;

    private ClientSession clientSession;
    private boolean listenerRegistered = false;
    private volatile boolean viewActive = false;

    @Override
    public void setClientSession(ClientSession session) {
        this.clientSession = session;
    }

    @Override
    public void onViewShown() {
        if (clientSession == null || listenerRegistered) {
            return;
        }

        viewActive = true;
        clientSession.getClient().addListener(this);
        listenerRegistered = true;
    }

    @Override
    public void onViewHidden() {
        viewActive = false;

        if (clientSession != null && listenerRegistered) {
            clientSession.getClient().removeListener(this);
            listenerRegistered = false;
        }
    }

    @Override
    public void dispose() {
        onViewHidden();
    }

    @Override
    public void onBidUpdate(
            String auctionId,
            double newPrice,
            String bidderId,
            String itemId) {

        Platform.runLater(() -> {
            if (!viewActive) {
                return;
            }

            auctionInfoLabel.setText(
                    String.format(
                            java.util.Locale.US,
                            "Auction: %s | Item: %s | Current bid: %,.2f | Bidder: %s",
                            auctionId,
                            itemId,
                            newPrice,
                            bidderId));
        });
    }
}
