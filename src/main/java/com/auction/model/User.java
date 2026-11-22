package com.auction.model;

import java.io.Serializable;

public abstract class User implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String id;
    private String name;
    private String password;
    private double balance;

    public User(String id, String name, String password) {
        this(id, name, password, 0.0);
    }

    public User(String id, String name, String password, double initialBalance) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Invalid id, id must not be empty");
        }
        this.id = id;
        this.setName(name);
        this.setPassword(password);
        this.balance = Math.max(0.0, initialBalance);
    }

    public synchronized double getBalance() {
        return balance;
    }

    public synchronized void setBalance(double balance) {
        if (!Double.isFinite(balance) || balance < 0) {
            throw new IllegalArgumentException("Balance must be a non-negative finite number");
        }
        this.balance = balance;
    }

    public synchronized void addBalance(double amount) {
        if (!Double.isFinite(amount) || amount <= 0) {
            throw new IllegalArgumentException("Amount to add must be positive");
        }
        this.balance += amount;
    }

    public synchronized boolean deductBalance(double amount) {
        if (!Double.isFinite(amount) || amount <= 0) {
            throw new IllegalArgumentException("Amount to deduct must be positive");
        }
        if (this.balance >= amount) {
            this.balance -= amount;
            return true;
        }
        this.balance = 0.0;
        return false;
    }

    public String getRole() {
        return getClass().getSimpleName().toUpperCase();
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
