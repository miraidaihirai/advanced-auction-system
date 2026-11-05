package com.auction.model;

import com.auction.item.Art;
import com.auction.item.Electronics;
import com.auction.item.Vehicle;
import com.auction.observer.AuctionObserver;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class ModelSerializationTest {

    @SuppressWarnings("unchecked")
    private <T> T serializeAndDeserialize(T object) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(object);
            oos.flush();
        }

        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        try (ObjectInputStream ois = new ObjectInputStream(bais)) {
            return (T) ois.readObject();
        }
    }

    @Test
    void testUserHierarchySerialization() throws Exception {
        Bidder originalBidder = new Bidder("B-99", "Bidder Name", "pass123456");
        Bidder restoredBidder = serializeAndDeserialize(originalBidder);

        assertNotNull(restoredBidder);
        assertEquals(originalBidder.getId(), restoredBidder.getId());
        assertEquals(originalBidder.getName(), restoredBidder.getName());
        assertEquals(originalBidder.getPassword(), restoredBidder.getPassword());

        Seller originalSeller = new Seller("S-99", "Seller Name", "pass123456");
        Seller restoredSeller = serializeAndDeserialize(originalSeller);

        assertNotNull(restoredSeller);
        assertEquals(originalSeller.getId(), restoredSeller.getId());
        assertEquals(originalSeller.getName(), restoredSeller.getName());
        assertEquals(originalSeller.getPassword(), restoredSeller.getPassword());
    }

    @Test
    void testItemHierarchySerialization() throws Exception {
        Art art = new Art("Mona Lisa", 1, 100_000_000, 1, "Da Vinci", "Oil on canvas", 1503);
        Art restoredArt = serializeAndDeserialize(art);
        assertEquals("Mona Lisa", restoredArt.getName());
        assertEquals(1, restoredArt.getId());
        assertEquals(100_000_000, restoredArt.getPrice());
        assertEquals(1, restoredArt.getQuantity());
        assertEquals("Da Vinci", restoredArt.getArtist());
        assertEquals("Oil on canvas", restoredArt.getMedium());
        assertEquals(1503, restoredArt.getYearCreated());

        Electronics phone = new Electronics("iPhone", 2, 20_000_000, 5, "Apple", "15 Pro", 12);
        Electronics restoredPhone = serializeAndDeserialize(phone);
        assertEquals("iPhone", restoredPhone.getName());
        assertEquals("Apple", restoredPhone.getBrand());
        assertEquals("15 Pro", restoredPhone.getModel());
        assertEquals(12, restoredPhone.getWarrantyPeriod());

        Vehicle car = new Vehicle("Sedan", 3, 500_000_000, 2, "Toyota", "Camry", 2024);
        Vehicle restoredCar = serializeAndDeserialize(car);
        assertEquals("Sedan", restoredCar.getName());
        assertEquals("Toyota", restoredCar.getMake());
        assertEquals("Camry", restoredCar.getModel());
        assertEquals(2024, restoredCar.getYear());
    }

    @Test
    void testBidTransactionSerialization() throws Exception {
        BidTransaction tx = new BidTransaction("TX-99", "AUC-1", "BIDDER-1", 15_000_000);
        BidTransaction restoredTx = serializeAndDeserialize(tx);

        assertEquals("TX-99", restoredTx.getId());
        assertEquals("AUC-1", restoredTx.getAuctionId());
        assertEquals("BIDDER-1", restoredTx.getBidderId());
        assertEquals(15_000_000, restoredTx.getAmount());
        assertNotNull(restoredTx.getTimestamp());
    }

    @Test
    void testAuctionTransientLockAndObserversReinitializedAfterDeserialization() throws Exception {
        Auction auction = new Auction("AUC-500", "Rolex Submariner", "SELLER-1", 50_000_000, 2_000_000);
        auction.startAuction(Duration.ofMinutes(10));

        BidTransaction bid1 = new BidTransaction("TX-101", "AUC-500", "BIDDER-1", 52_000_000);
        auction.placeBid(bid1);

        Auction deserialized = serializeAndDeserialize(auction);

        // Kiểm tra dữ liệu được phục hồi đầy đủ
        assertEquals("AUC-500", deserialized.getId());
        assertEquals("Rolex Submariner", deserialized.getItemId());
        assertEquals(52_000_000.0, deserialized.getCurrentPrice());
        assertEquals("BIDDER-1", deserialized.getHighestBidderId());
        assertEquals(Auction.Status.RUNNING, deserialized.getStatus());
        assertEquals(1, deserialized.getBidHistory().size());

        // Kiểm tra ReentrantLock hoạt động bình thường trên đối tượng deserialized (không bị NullPointerException)
        AtomicBoolean observerReceivedBid = new AtomicBoolean(false);
        AtomicReference<Double> receivedPrice = new AtomicReference<>(0.0);

        AuctionObserver testObserver = new AuctionObserver() {
            @Override
            public void onNewBid(Auction a, BidTransaction bid) {
                observerReceivedBid.set(true);
                receivedPrice.set(bid.getAmount());
            }

            @Override
            public void onAuctionClosed(Auction a) {
            }
        };

        // Đăng ký observer vào đối tượng sau deserialization
        deserialized.addObserver(testObserver);

        // Đặt bid tiếp theo trên đối tượng đã deserialized
        BidTransaction bid2 = new BidTransaction("TX-102", "AUC-500", "BIDDER-2", 55_000_000);
        boolean bidSuccess = deserialized.placeBid(bid2);

        assertTrue(bidSuccess);
        assertEquals(55_000_000.0, deserialized.getCurrentPrice());
        assertEquals("BIDDER-2", deserialized.getHighestBidderId());
        assertEquals(2, deserialized.getBidHistory().size());

        // Đảm bảo Observer đã được gọi thành công
        assertTrue(observerReceivedBid.get());
        assertEquals(55_000_000.0, receivedPrice.get());

        // Đóng phiên đấu giá
        deserialized.closeAuction();
        assertEquals(Auction.Status.FINISHED, deserialized.getStatus());
    }
}

