package com.auction.exceptions;

public class AuctionClosedException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public AuctionClosedException(String message) {
        super(message);
    }
}