package com.auction.storage;

import com.auction.model.Auction;
import com.auction.model.BidTransaction;
import com.auction.model.User;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Đối tượng gói dữ liệu (Snapshot) chứa toàn bộ tài khoản người dùng, phiên đấu giá và lịch sử giao dịch.
 * Hỗ trợ Serialization / Deserialization để lưu trữ và phục hồi toàn vẹn hệ thống.
 */
public class AuctionDataSnapshot implements Serializable {
    private static final long serialVersionUID = 1L;

    private final List<User> users;
    private final List<Auction> auctions;
    private final List<BidTransaction> transactions;
    private final long savedAt;

    public AuctionDataSnapshot(List<User> users, List<Auction> auctions) {
        this(users, auctions, null);
    }

    public AuctionDataSnapshot(List<User> users, List<Auction> auctions, List<BidTransaction> transactions) {
        this.users = users != null ? new ArrayList<>(users) : new ArrayList<>();
        this.auctions = auctions != null ? new ArrayList<>(auctions) : new ArrayList<>();
        if (transactions != null) {
            this.transactions = new ArrayList<>(transactions);
        } else {
            List<BidTransaction> allTx = new ArrayList<>();
            for (Auction a : this.auctions) {
                allTx.addAll(a.getBidHistory());
            }
            this.transactions = allTx;
        }
        this.savedAt = System.currentTimeMillis();
    }

    public List<User> getUsers() {
        return Collections.unmodifiableList(users);
    }

    public List<Auction> getAuctions() {
        return Collections.unmodifiableList(auctions);
    }

    public List<BidTransaction> getTransactions() {
        return Collections.unmodifiableList(transactions != null ? transactions : Collections.emptyList());
    }

    public long getSavedAt() {
        return savedAt;
    }

    @Override
    public String toString() {
        return String.format("AuctionDataSnapshot[users=%d, auctions=%d, transactions=%d, savedAt=%d]", users.size(), auctions.size(), (transactions != null ? transactions.size() : 0), savedAt);
    }
}
