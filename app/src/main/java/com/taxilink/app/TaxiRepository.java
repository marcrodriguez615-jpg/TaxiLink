package com.taxilink.app;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TaxiRepository {
    private final List<Taxi> taxis;

    public TaxiRepository() {
        taxis = new MockTaxiProvider().createInitialTaxis();
    }

    public List<Taxi> getTaxis() {
        return taxis;
    }

    public Taxi getTaxi(int number) {
        for (Taxi taxi : taxis) {
            if (taxi.number == number) return taxi;
        }
        return taxis.get(2);
    }

    public void toggleTaxi(Taxi taxi) {
        taxi.online = !taxi.online;
        if (!taxi.online) taxi.speed = 0;
        taxi.lastUpdate = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
    }
}
