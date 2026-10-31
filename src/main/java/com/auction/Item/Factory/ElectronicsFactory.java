package com.auction.Item.Factory;

import com.auction.Item.Electronics;
import com.auction.Item.Item;

public class ElectronicsFactory extends ItemFactory {
    private String name;
    private int id;
    private int price;
    private int quantity;
    private String brand;
    private String model;
    private int warrantyPeriod;

    public ElectronicsFactory(String name, int id, int price, int quantity, String brand, String model, int warrantyPeriod) {
        this.name = name;
        this.id = id;
        this.price = price;
        this.quantity = quantity;
        this.brand = brand;
        this.model = model;
        this.warrantyPeriod = warrantyPeriod;
    }

    @Override
    public Item createItem() {
        return new Electronics(this.name, this.id, this.price, this.quantity, this.brand, this.model, this.warrantyPeriod);
    }
}
