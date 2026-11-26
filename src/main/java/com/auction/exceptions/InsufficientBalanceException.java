package com.auction.exceptions;

/**
 * Ngoại lệ ném ra khi số dư ví của người dùng không đủ để thực hiện thanh toán phiên đấu giá
 * hoặc rút tiền.
 */
public class InsufficientBalanceException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public InsufficientBalanceException(String message) {
        super(message);
    }
}
