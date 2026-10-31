package com.auction.Item;

public class Vehicle extends Item {
    private String make;
    private String model;
    private int year;

    public Vehicle(String name, int id, int price, int quantity, String make, String model, int year) {
        super(name, id, price, quantity);
        this.make = make;
        this.model = model;
        this.year = year;
    }

    public String getMake() {
        return make;
    }
    public void setMake(String make) {
        this.make = make;
    }

    public String getModel() {
        return model;
    }
    public void setModel(String model) {
        this.model = model;
    }

    public int getYear() {
        return year;
    }
    public void setYear(int year) {
        this.year = year;
    }

}
