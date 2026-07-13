package com.taxilink.app;

import java.util.ArrayList;
import java.util.List;

public class TaximeterCalculator {
    public static class FareResult {
        public double distanceKm;
        public int minutes;
        public double baseFare;
        public double distanceFare;
        public double timeFare;
        public double supplements;
        public double minimumFare;
        public double total;
        public List<String> lines = new ArrayList<>();
    }

    public static class SupplementOptions {
        public boolean airport;
        public boolean mollAdossat;
        public boolean firaGranVia;
        public boolean santsStation;
        public boolean largeCapacity;
        public boolean specialNight;
    }

    public static FareResult estimate(String tariffName, double distanceKm, int minutes, SupplementOptions supplements) {
        Tariff tariff = tariffFor(tariffName);
        FareResult r = new FareResult();
        r.distanceKm = Math.max(0, distanceKm);
        r.minutes = Math.max(0, minutes);
        r.baseFare = tariff.baseFare;
        r.distanceFare = r.distanceKm * tariff.pricePerKm;
        r.timeFare = r.minutes * tariff.pricePerMinute;
        r.minimumFare = tariff.minimumFare;
        boolean fixedAirportPort = tariff.name.contains("Tarifa 4");
        r.supplements = fixedAirportPort ? 0 : supplementTotal(supplements, r.lines);
        if (fixedAirportPort) r.lines.add("T-4: precio fijo con suplementos incluidos");
        double raw = Math.max(r.minimumFare, r.baseFare + r.distanceFare + r.timeFare) + r.supplements;
        r.total = roundUpToFiveCents(raw);
        r.lines.add("Tarifa: " + tariff.name);
        r.lines.add("Bajada: " + money(r.baseFare));
        r.lines.add("Distancia: " + String.format(java.util.Locale.getDefault(), "%.2f km", r.distanceKm) + " · " + money(r.distanceFare));
        r.lines.add("Tiempo aprox.: " + r.minutes + " min · " + money(r.timeFare));
        if (r.minimumFare > 0) r.lines.add("Mínimo aplicado si corresponde: " + money(r.minimumFare));
        r.lines.add("Total aproximado al alza: " + money(r.total));
        return r;
    }

    public static String money(double value) {
        return String.format(java.util.Locale.getDefault(), "%.2f €", value);
    }

    private static double supplementTotal(SupplementOptions o, List<String> lines) {
        double total = 0;
        if (o == null) return 0;
        if (o.airport) { total += 4.60; lines.add("Suplemento aeropuerto: 4.60 €"); }
        if (o.mollAdossat) { total += 4.60; lines.add("Suplemento Moll Adossat: 4.60 €"); }
        if (o.firaGranVia) { total += 3.30; lines.add("Suplemento Fira Gran Via: 3.30 €"); }
        if (o.santsStation) { total += 2.55; lines.add("Suplemento Estación de Sants: 2.55 €"); }
        if (o.largeCapacity) { total += 4.60; lines.add("Suplemento vehículo 5-8 pasajeros: 4.60 €"); }
        if (o.specialNight) { total += 4.60; lines.add("Suplemento noche especial: 4.60 €"); }
        return total;
    }

    private static double roundUpToFiveCents(double value) {
        return Math.ceil(value * 20.0) / 20.0;
    }

    private static Tariff tariffFor(String name) {
        if (name == null) return tariff1();
        String n = name.toLowerCase(java.util.Locale.ROOT);
        if (n.contains("2")) return new Tariff("Tarifa 2 AMB 2026", 2.80, 1.66, 27.75 / 60.0, 0);
        if (n.contains("3")) return new Tariff("Tarifa 3 precio cerrado AMB 2026", 2.80, 1.35, 27.75 / 60.0, 8.00);
        if (n.contains("aeropuerto")) return new Tariff("Tarifa aeropuerto AMB 2026", 2.80, 1.66, 27.75 / 60.0, 21.00);
        if (n.contains("4")) return new Tariff("Tarifa 4 Aeropuerto - Moll Adossat AMB 2026", 46.00, 0, 0, 46.00);
        return tariff1();
    }

    private static Tariff tariff1() {
        return new Tariff("Tarifa 1 AMB 2026", 2.80, 1.35, 27.75 / 60.0, 0);
    }

    private static class Tariff {
        final String name;
        final double baseFare;
        final double pricePerKm;
        final double pricePerMinute;
        final double minimumFare;

        Tariff(String name, double baseFare, double pricePerKm, double pricePerMinute, double minimumFare) {
            this.name = name;
            this.baseFare = baseFare;
            this.pricePerKm = pricePerKm;
            this.pricePerMinute = pricePerMinute;
            this.minimumFare = minimumFare;
        }
    }
}
