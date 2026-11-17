package com.auction.ui;

import com.auction.session.ClientSession;

public interface SessionAware {
    void setClientSession(ClientSession session);
}
// Quy ước cho các Controller cần ClientSesion
// Hỗ trợ ViewManager trong việc kiểm tra các Controller


