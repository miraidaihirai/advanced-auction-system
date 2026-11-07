package factory;
import Item.Factory.*;
import Item.Art;
import Item.Electronics;
import Item.Vehicle;
import Item.Item;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

public class FactoryTest {
    @Test
    void artFactory_shouldCreateArtWithCorrectValue(){
        ItemFactory factory = new ArtFactory("Mona Lisa", 101, 5000, 1, "Leonardo de Vinci", "Oil painting", 1503);
        Item item = factory.createItem();
        assertInstanceOf(Art.class, item);
        Art art = (Art) item;
        assertAll(
            () -> assertEquals("Mona Lisa", art.getName()),
            () -> assertEquals(101, art.getId()),
            () -> assertEquals(5000, art.getPrice()),
            () -> assertEquals(1, art.getQuantity()),
            () -> assertEquals("Leonardo de Vinci", art.getArtist()),
            () -> assertEquals("Oil painting", art.getMedium()),
            () -> assertEquals(1503, art.getYearCreated())
        );
    }
    @Test
    void electronicsFactory_shouldCreateElectronicsWithCorrectValue(){
        ItemFactory factory = new ElectronicsFactory("Laptop", 102, 5000, 5, "Lenovo", "G14 7+", 24);
        Item item = factory.createItem();
        assertInstanceOf(Electronics.class, item);
        Electronics electronics = (Electronics) item;
        assertAll(
            () -> assertEquals("Laptop", electronics.getName()),
            () -> assertEquals(102, electronics.getId()),
            () -> assertEquals(5000, electronics.getPrice()),
            () -> assertEquals(5, electronics.getQuantity()),
            () -> assertEquals("Lenovo", electronics.getBrand()),
            () -> assertEquals("G14 7+", electronics.getModel()),
            () -> assertEquals(24, electronics.getWarrantyPeriod())
        );
    }
    @Test
    void vehicleFactory_shouldCreateVehicleWithCorrectValues() {
        ItemFactory factory = new VehicleFactory(
                "Car", 103, 30000, 2,
                "Toyota", "Camry", 2025
        );

        Item item = factory.createItem();
        assertInstanceOf(Vehicle.class, item);
        Vehicle vehicle = (Vehicle) item;
        assertAll(
                () -> assertEquals("Car", vehicle.getName()),
                () -> assertEquals(103, vehicle.getId()),
                () -> assertEquals(30000, vehicle.getPrice()),
                () -> assertEquals(2, vehicle.getQuantity()),
                () -> assertEquals("Toyota", vehicle.getMake()),
                () -> assertEquals("Camry", vehicle.getModel()),
                () -> assertEquals(2025, vehicle.getYear())
        );
    }
    @Test
    void displayInfo_shouldPrintItemInformation(){
        ItemFactory factory = new ArtFactory("Mona Lisa", 101, 5000, 1, "Leonardo de Vinci", "Oil painting", 1503);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        try (PrintStream capture = new PrintStream(output, true, StandardCharsets.UTF_8)){
            System.setOut(capture);
            factory.displayInfo();
        } finally{
            System.setOut(originalOut);
        }
        String result = output.toString(StandardCharsets.UTF_8);
         assertAll(
                () -> assertTrue(result.contains("Item Name: Mona Lisa")),
                () -> assertTrue(result.contains("Item ID: 101")),
                () -> assertTrue(result.contains("Item Price: 5000")),
                () -> assertTrue(result.contains("Item Quantity: 1"))
        );
    }
}
