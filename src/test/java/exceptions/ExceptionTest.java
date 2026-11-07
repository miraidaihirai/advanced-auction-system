package exceptions;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExceptionsTest {

    @Test
    void auctionClosedException_shouldStoreMessage() {
        AuctionClosedException exception =
                new AuctionClosedException("Auction is closed");

        assertAll(
                () -> assertEquals(
                        "Auction is closed",
                        exception.getMessage()
                ),
                () -> assertInstanceOf(
                        RuntimeException.class,
                        exception
                )
        );
    }

    @Test
    void authenticationException_shouldStoreMessage() {
        AuthenticationException exception =
                new AuthenticationException("Invalid credentials");

        assertAll(
                () -> assertEquals(
                        "Invalid credentials",
                        exception.getMessage()
                ),
                () -> assertInstanceOf(
                        RuntimeException.class,
                        exception
                )
        );
    }

    @Test
    void invalidBidException_shouldStoreMessage() {
        InvalidBidException exception =
                new InvalidBidException("Bid amount is invalid");

        assertAll(
                () -> assertEquals(
                        "Bid amount is invalid",
                        exception.getMessage()
                ),
                () -> assertInstanceOf(
                        RuntimeException.class,
                        exception
                )
        );
    }
}
