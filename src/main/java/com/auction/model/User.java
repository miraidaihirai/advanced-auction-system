package com.auction.model;

public abstract class User {
    private final String id;
    private String name;
    private String password;

    public User(String id, String name, String password) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Invalid id, id must not be empty");
        }
        this.id = id;
        this.setName(name);
        this.setPassword(password);
    }

    public final String getName() {
        return name;
    }

    public final String getId() {
        return id;
    }

    public final String getPassword() {
        return password;
    }

    public final void setName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Invalid name, name must not be empty");
        }
        this.name = name;
    }

    public final void setPassword(String password) {
        if (password == null || password.trim().isEmpty() || password.length() < 8) {
            throw new IllegalArgumentException(
                    "Invalid password, password must not be empty and should contain at least 8 characters");
        }
        this.password = password;
    }

    public abstract void displayInfo();
}

