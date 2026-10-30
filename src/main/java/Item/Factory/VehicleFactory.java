package Item.Factory;

import Item.Item;
import Item.Vehicle;

public class VehicleFactory extends ItemFactory {
    private String name;
    private int id;
    private int price;
    private int quantity;
    private String make;
    private String model;
    private int year;

    public VehicleFactory(String name, int id, int price, int quantity, String make, String model, int year) {
        this.name = name;
        this.id = id;
        this.price = price;
        this.quantity = quantity;
        this.make = make;
        this.model = model;
        this.year = year;
    }

    @Override
    public Item createItem() {
        return new Vehicle(this.name, this.id, this.price, this.quantity, this.make, this.model, this.year);
    }
}
