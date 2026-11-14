package com.auction.sessions;
import network.AuctionClient;
import java.io.IOException;
public class ClientSession {
    private final AuctionClient auctionClient;
    public ClientSession(String host, int port){
        this.auctionClient = new AuctionClient(host, port);
    }
    public void connect() throws IOException{
        auctionClient.connect();
    }
    public AuctionClient getClient(){
        return auctionClient;
    }
    public boolean isConnected(){
        return auctionClient.isConnected();
    }
    public void disconnect(){
        auctionClient.disconnect();
    }
}
