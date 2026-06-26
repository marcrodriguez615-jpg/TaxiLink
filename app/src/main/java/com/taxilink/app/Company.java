package com.taxilink.app;

public class Company {
    public final String name;
    public final String identifier;
    public final String password;
    public final String ownerPassword;

    public Company(String name, String identifier, String password) {
        this(name, identifier, password, password);
    }

    public Company(String name, String identifier, String password, String ownerPassword) {
        this.name = name;
        this.identifier = identifier;
        this.password = password;
        this.ownerPassword = ownerPassword;
    }
}
