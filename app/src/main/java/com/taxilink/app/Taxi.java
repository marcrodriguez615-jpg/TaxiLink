package com.taxilink.app;

public class Taxi {
    public int number;
    public boolean online;
    public int speed;
    public String direction;
    public double latitude;
    public double longitude;
    public String lastUpdate;
    public String driverName;
    public boolean occupied;

    public Taxi(int number, boolean online, int speed, String direction, double latitude, double longitude, String lastUpdate) {
        this.number = number;
        this.online = online;
        this.speed = speed;
        this.direction = direction;
        this.latitude = latitude;
        this.longitude = longitude;
        this.lastUpdate = lastUpdate;
        this.driverName = "Conductor";
    }

    public String name() {
        return "Taxi " + number;
    }
}
