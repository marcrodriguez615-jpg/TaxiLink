package com.taxilink.app;

public class Company {
    public final String name;
    public final String identifier;
    public final String password;
    public final String ownerPassword;
    public final String centralNumber;
    public final String ownerName;

    public Company(String name, String identifier, String password) {
        this(name, identifier, password, password, "00000000000000000", "Propietario");
    }

    public Company(String name, String identifier, String password, String ownerPassword) {
        this(name, identifier, password, ownerPassword, "00000000000000000", "Propietario");
    }

    public Company(String name, String identifier, String password, String ownerPassword, String centralNumber) {
        this(name, identifier, password, ownerPassword, centralNumber, "Propietario");
    }

    public Company(String name, String identifier, String password, String ownerPassword, String centralNumber, String ownerName) {
        this.name = name;
        this.identifier = identifier;
        this.password = password;
        this.ownerPassword = ownerPassword;
        this.centralNumber = centralNumber;
        this.ownerName = ownerName;
    }
}
