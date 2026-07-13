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
        public boolean station;
        public boolean nightHoliday;
        public boolean luggage;
        public boolean pet;
        public boolean booking;
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
        r.supplements = supplementTotal(supplements, r.lines);
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
        if (o.airport) { total += 4.50; lines.add("Suplemento aeropuerto: 4.50 €"); }
        if (o.station) { total += 2.50; lines.add("Suplemento estación/puerto/feria: 2.50 €"); }
        if (o.nightHoliday) { total += 3.10; lines.add("Suplemento noche/festivo: 3.10 €"); }
        if (o.luggage) { total += 1.00; lines.add("Suplemento equipaje especial: 1.00 €"); }
        if (o.pet) { total += 1.50; lines.add("Suplemento mascota: 1.50 €"); }
        if (o.booking) { total += 3.40; lines.add("Suplemento emisora/reserva: 3.40 €"); }
        return total;
    }

    private static double roundUpToFiveCents(double value) {
        return Math.ceil(value * 20.0) / 20.0;
    }

    private static Tariff tariffFor(String name) {
        if (name == null) return tariff1();
        String n = name.toLowerCase(java.util.Locale.ROOT);
        if (n.contains("2")) return new Tariff("Tarifa 2", 2.55, 1.55, 0.41, 7.50);
        if (n.contains("3")) return new Tariff("Tarifa 3", 3.10, 1.80, 0.48, 8.50);
        if (n.contains("aeropuerto")) return new Tariff("Tarifa aeropuerto", 3.10, 1.80, 0.48, 21.00);
        return tariff1();
    }

    private static Tariff tariff1() {
        return new Tariff("Tarifa 1", 2.40, 1.35, 0.35, 7.00);
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
