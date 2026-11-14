package com.auction.UI;
import com.auction.sessions.ClientSession;
public interface SessionAware{
    void setClientSession(ClientSession session);
    // Controller này nhận và sử dụng ClientSession
    
}