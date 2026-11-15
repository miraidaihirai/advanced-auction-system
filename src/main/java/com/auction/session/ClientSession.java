
package com.auction.session;

import java.io.IOException;
import network.AuctionClient;

public class ClientSession {

    private final AuctionClient auctionClient;
    // Lưu một Socket Client duy nhất nhận địa chỉ server và taok AuctionClient

    public ClientSession(String host, int port) {
        this.auctionClient = new AuctionClient(host, port);
    }

    public synchronized void connect() throws IOException {
        if (!auctionClient.isConnected()) {
            auctionClient.connect();
        }
    }

    public AuctionClient getClient() {
        return auctionClient;
    }

    public boolean isConnected() {
        return auctionClient.isConnected();
    }

    public void disconnect() {
        auctionClient.disconnect();
    }
}

