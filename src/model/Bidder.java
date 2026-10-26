package model;

import Item.Item;

public class Bidder extends User {
    private double balance;

    public Bidder(String id, String name, String password, double balance) {
        super(id, name, password);
        this.setBalance(balance);
    }

    public double getBalance() {
        return balance;
    }

    @Override
    public void displayInfo() {
        System.out.println(
                "Role: Bidder | Name: " + this.getName() + " | ID: " + this.getId() + " | Balance: " + balance);
    }

    public void addFund(double amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Deposit must be greater than 0");
        }
        this.balance += amount;
    }

    public void deductFund(double amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be greater than 0");
        }
        if (amount > balance) {
            throw new IllegalArgumentException("Insufficient balance");
        }
        this.balance -= amount;
    }

    public boolean canAfford(Item item) {
        return (balance >= item.getPrice());
    }

    private void setBalance(double balance) {
        if (balance < 0) {
            throw new IllegalArgumentException("Balance must be greater than or equal to 0");
        }
        this.balance = balance;
    }
}

