package Item;

public abstract class Item {
    private final String name;
    private final int id;
    private int price;
    private int quantity;

    protected Item(String name, int id, int price, int quantity) {
        this.name = name;
        this.id = id;
        this.price = price;
        this.quantity = quantity;
    }

    public String getName() {
        return name;
    }

    public int getId() {
        return id;
    }

    public int getPrice() {
        return price;
    }
    public void setPrice(int price) {
        this.price = price;
    }

    public int getQuantity() {
        return quantity;
    }
    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }
}