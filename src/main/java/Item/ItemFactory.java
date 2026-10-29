package Item;

public abstract class ItemFactory {

    public abstract Item createItem();

    public void displayInfo() {
        Item item = createItem();
        System.out.println("Item Name: " + item.getName());
        System.out.println("Item ID: " + item.getId());
        System.out.println("Item Price: " + item.getPrice());
        System.out.println("Item Quantity: " + item.getQuantity());
    }

}
