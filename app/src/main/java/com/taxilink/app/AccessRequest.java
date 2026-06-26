package com.taxilink.app;

public class AccessRequest {
    public final String id;
    public final String driverName;
    public final int taxiNumber;
    public final String createdAt;

    public AccessRequest(String id, String driverName, int taxiNumber, String createdAt) {
        this.id = id;
        this.driverName = driverName;
        this.taxiNumber = taxiNumber;
        this.createdAt = createdAt;
    }
}
