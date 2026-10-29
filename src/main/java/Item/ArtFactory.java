package Item;

public class ArtFactory extends ItemFactory {
    private String name;
    private int id;
    private int price;
    private int quantity;
    private String artist;
    private String medium;
    private int yearCreated;

    public ArtFactory(String name, int id, int price, int quantity, String artist, String medium, int yearCreated) {
        this.name = name;
        this.id = id;
        this.price = price;
        this.quantity = quantity;
        this.artist = artist;
        this.medium = medium;
        this.yearCreated = yearCreated;
    }

    @Override
    public Item createItem() {
        return new Art(this.name, this.id, this.price, this.quantity, this.artist, this.medium, this.yearCreated);
    }
}
