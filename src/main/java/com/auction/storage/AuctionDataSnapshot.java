package com.auction.storage;

import com.auction.model.Auction;
import com.auction.model.User;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Đối tượng gói dữ liệu (Snapshot) chứa toàn bộ tài khoản người dùng và phiên đấu giá.
 * Hỗ trợ Serialization / Deserialization để lưu trữ và phục hồi toàn vẹn hệ thống.
 */
public class AuctionDataSnapshot implements Serializable {
    private static final long serialVersionUID = 1L;

    private final List<User> users;
    private final List<Auction> auctions;
    private final long savedAt;

    public AuctionDataSnapshot(List<User> users, List<Auction> auctions) {
        this.users = users != null ? new ArrayList<>(users) : new ArrayList<>();
        this.auctions = auctions != null ? new ArrayList<>(auctions) : new ArrayList<>();
        this.savedAt = System.currentTimeMillis();
    }

    public List<User> getUsers() {
        return Collections.unmodifiableList(users);
    }

    public List<Auction> getAuctions() {
        return Collections.unmodifiableList(auctions);
    }

    public long getSavedAt() {
        return savedAt;
    }

    @Override
    public String toString() {
        return String.format("AuctionDataSnapshot[users=%d, auctions=%d, savedAt=%d]",
                users.size(), auctions.size(), savedAt);
    }
}

