package item;

public class Art extends Item {
    private String artist;
    private String medium;
    private int yearCreated;

    public Art(String name, int id, int price, int quantity, String artist, String medium, int yearCreated) {
        super(name, id, price, quantity);
        this.artist = artist;
        this.medium = medium;
        this.yearCreated = yearCreated;
    }


    // Getters and setters for Art specific attributes
    public String getArtist() {
        return artist;
    }
    public void setArtist(String artist) {
        this.artist = artist;
    }

    public String getMedium() {
        return medium;
    }
    public void setMedium(String medium) {
        this.medium = medium;
    }

    public int getYearCreated() {
        return yearCreated;
    }
    public void setYearCreated(int yearCreated) {
        this.yearCreated = yearCreated;
    }



}
