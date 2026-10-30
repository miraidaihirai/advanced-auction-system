package Item;

public class Electronics extends Item {
    private String brand;
    private String model;
    private int warrantyPeriod;
    private int warrantyPeriod; // in months

    public Electronics(String name, int id, int price, int quantity, String brand, String model, int warrantyPeriod) {
        super(name, id, price, quantity);
        this.brand = brand;
        this.model = model;
        this.warrantyPeriod = warrantyPeriod;
    }

    // Getters and setters for Electronics specific attributes
    public String getBrand() {
        return brand;
    }
    public void setBrand(String brand) {
        this.brand = brand;
    }

    public String getModel() {
        return model;
    }
    public void setModel(String model) {
        this.model = model;
    }

    public int getWarrantyPeriod() {
        return warrantyPeriod;
    }
    public void setWarrantyPeriod(int warrantyPeriod) {
        this.warrantyPeriod = warrantyPeriod;
    }

    
}
