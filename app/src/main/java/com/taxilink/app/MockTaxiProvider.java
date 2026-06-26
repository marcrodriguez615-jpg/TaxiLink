package com.taxilink.app;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MockTaxiProvider {
    public List<Taxi> createInitialTaxis() {
        String now = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
        List<Taxi> taxis = new ArrayList<>();
        taxis.add(new Taxi(1, true, 52, "NE", 41.5407, 2.2139, now));
        taxis.add(new Taxi(2, true, 41, "E", 41.5442, 2.2072, now));
        taxis.add(new Taxi(3, true, 48, "NE", 41.5356, 2.2197, now));
        taxis.add(new Taxi(4, true, 35, "SO", 41.5486, 2.2228, now));
        taxis.add(new Taxi(5, false, 0, "--", 41.5322, 2.2028, now));
        taxis.add(new Taxi(6, true, 60, "N", 41.5516, 2.2144, now));
        return taxis;
    }
}
