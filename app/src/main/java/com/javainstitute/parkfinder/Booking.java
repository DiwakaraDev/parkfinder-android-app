package com.javainstitute.parkfinder;

public class Booking {
    private String location;
    private String date;
    private String time;
    private double balance;
    private String docId;  // Firestore document ID

    public Booking(String location, String date, String time, double balance) {
        this.location = location;
        this.date = date;
        this.time = time;
        this.balance = balance;
    }

    public String getLocation() {
        return location;
    }

    public String getDate() {
        return date;
    }

    public String getTime() {
        return time;
    }

    public double getBalance() {
        return balance;
    }

    public String getDocId() {
        return docId;
    }

    public void setDocId(String docId) {
        this.docId = docId;
    }
}
