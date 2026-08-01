package com.taxilink.app;

import android.content.Context;
import android.content.SharedPreferences;

public class UserSession {
    private static final String PREFS = "taxilink_session";
    private final SharedPreferences prefs;

    public UserSession(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public void saveCompany(Company company) {
        prefs.edit()
                .putString("company_name", company.name)
                .putString("company_id", company.identifier)
                .putString("owner_email", company.ownerEmail == null ? "" : company.ownerEmail)
                .putString("central_number", company.centralNumber)
                .putString("owner_name", company.ownerName)
                .putString("service_towns", company.serviceTowns)
                .putString("tariff_profile", company.tariffProfile)
                .putString("role", "Propietario")
                .putBoolean("logged_in", true)
                .apply();
    }

    public Company getCompany() {
        String name = prefs.getString("company_name", "Taxi Central");
        String id = prefs.getString("company_id", "central");
        String pass = "";
        String ownerPass = "";
        String central = prefs.getString("central_number", "00000000000000000");
        String ownerName = prefs.getString("owner_name", "Propietario");
        String towns = prefs.getString("service_towns", "");
        String tariffProfile = prefs.getString("tariff_profile", "AMB 2026");
        Company company = new Company(name, id, pass, ownerPass, central, ownerName, towns, tariffProfile);
        company.ownerEmail = prefs.getString("owner_email", "");
        return company;
    }

    public void saveDriverLogin(String companyId, String taxiNumber, boolean remember) {
        SharedPreferences.Editor editor = prefs.edit()
                .putString("company_id", companyId)
                .putString("central_number", companyId)
                .putString("taxi_number", taxiNumber)
                .putString("role", "Conductor")
                .putBoolean("logged_in", true)
                .putBoolean("driver_approved", false)
                .remove("assigned_reservation_color");
        if (remember) {
            editor.putString("remember_company", companyId).putString("remember_taxi", taxiNumber);
        }
        editor.apply();
    }

    public void saveDriverIdentity(String name, String requestId) {
        prefs.edit().putString("driver_name", name).putString("request_id", requestId).apply();
    }

    public void setDriverApproved(boolean approved) {
        prefs.edit().putBoolean("driver_approved", approved).apply();
    }

    public boolean isDriverApproved() {
        return prefs.getBoolean("driver_approved", false);
    }

    public void setRole(String role) {
        prefs.edit().putString("role", role).putBoolean("logged_in", role != null && !role.isEmpty()).apply();
    }

    public boolean isLoggedIn() {
        return prefs.getBoolean("logged_in", false) && !getRole().isEmpty();
    }

    public String getRole() {
        return prefs.getString("role", "Conductor");
    }

    public String getTaxiNumber() {
        return prefs.getString("taxi_number", "3");
    }

    public String getDriverName() {
        return prefs.getString("driver_name", "Conductor");
    }

    public String getDisplayName() {
        return "Propietario".equals(getRole()) ? getCompany().ownerName : getDriverName();
    }

    public void updateLocalNames(String companyName, String ownerName, String driverName) {
        SharedPreferences.Editor editor = prefs.edit();
        if (companyName != null && !companyName.trim().isEmpty()) editor.putString("company_name", companyName.trim());
        if (ownerName != null && !ownerName.trim().isEmpty()) editor.putString("owner_name", ownerName.trim());
        if (driverName != null && !driverName.trim().isEmpty()) editor.putString("driver_name", driverName.trim());
        editor.apply();
    }

    public void updateTariffSettings(String serviceTowns, String tariffProfile) {
        SharedPreferences.Editor editor = prefs.edit();
        if (serviceTowns != null) editor.putString("service_towns", serviceTowns.trim());
        if (tariffProfile != null) editor.putString("tariff_profile", tariffProfile.trim());
        editor.apply();
    }

    public String getRequestId() {
        return prefs.getString("request_id", "");
    }

    public String getCentralNumber() {
        return prefs.getString("central_number", "00000000000000000");
    }

    public String getServerUrl() {
        return ApiConfig.SERVER_URL;
    }

    public void setServerUrl(String url) {
        prefs.edit().putString("server_url", ApiConfig.SERVER_URL).apply();
    }

    public String getRememberCompany() {
        return prefs.getString("remember_company", "");
    }

    public String getRememberTaxi() {
        return prefs.getString("remember_taxi", "");
    }

    public void changePassword(String password) {
        // Driver access is approved by the owner and no longer uses a shared password.
    }

    public void changeOwnerPassword(String password) {
        // Firebase Authentication owns the password; this is intentionally not persisted.
    }

    public boolean isAndroidAutoTaximeterEnabled() {
        return prefs.getBoolean("android_auto_taximeter", true);
    }

    public void setAndroidAutoTaximeterEnabled(boolean enabled) {
        prefs.edit().putBoolean("android_auto_taximeter", enabled).apply();
    }

    public boolean isAdminCountsAsTaxi() {
        return prefs.getBoolean("admin_counts_as_taxi", false);
    }

    public void setAdminCountsAsTaxi(boolean enabled) {
        prefs.edit().putBoolean("admin_counts_as_taxi", enabled).apply();
    }

    public String getAdminTaxiNumber() {
        return prefs.getString("admin_taxi_number", "");
    }

    public void setAdminTaxiNumber(String taxiNumber) {
        prefs.edit().putString("admin_taxi_number", taxiNumber == null ? "" : taxiNumber.trim()).apply();
    }

    public String getAssignedReservationColor() {
        return prefs.getString("assigned_reservation_color", "");
    }

    public void setAssignedReservationColor(String color) {
        prefs.edit().putString("assigned_reservation_color", color == null ? "" : color.trim()).apply();
    }

    public void startCarTaximeterFromMobileService(String serviceId) {
        prefs.edit()
                .putBoolean("car_taximeter_mobile_service_running", true)
                .putLong("car_taximeter_mobile_service_started_at", System.currentTimeMillis())
                .putString("car_taximeter_mobile_service_id", serviceId == null ? "" : serviceId)
                .apply();
    }

    public void stopCarTaximeterFromMobileService() {
        prefs.edit()
                .putBoolean("car_taximeter_mobile_service_running", false)
                .putLong("car_taximeter_mobile_service_started_at", 0)
                .remove("car_taximeter_mobile_service_id")
                .apply();
    }

    public boolean isMobileServiceTaximeterRunning() {
        return prefs.getBoolean("car_taximeter_mobile_service_running", false);
    }

    public long getMobileServiceTaximeterStartedAt() {
        return prefs.getLong("car_taximeter_mobile_service_started_at", 0);
    }

    public String getMobileServiceTaximeterId() {
        return prefs.getString("car_taximeter_mobile_service_id", "");
    }

    public String getGoogleAdsAccount() {
        return prefs.getString("google_ads_account", "");
    }

    public void setGoogleAdsAccount(String account) {
        prefs.edit().putString("google_ads_account", account == null ? "" : account.trim()).apply();
    }

    public int getGoogleAdsClicks() {
        return prefs.getInt("google_ads_clicks", 0);
    }

    public int getGoogleAdsCalls() {
        return prefs.getInt("google_ads_calls", 0);
    }

    public void addGoogleAdsClick() {
        prefs.edit().putInt("google_ads_clicks", getGoogleAdsClicks() + 1).apply();
    }

    public void addGoogleAdsCall() {
        prefs.edit().putInt("google_ads_calls", getGoogleAdsCalls() + 1).apply();
    }

    public void resetGoogleAdsStats() {
        prefs.edit().putInt("google_ads_clicks", 0).putInt("google_ads_calls", 0).apply();
    }

    public void setGoogleAdsStats(int clicks, int calls) {
        prefs.edit().putInt("google_ads_clicks", Math.max(0, clicks)).putInt("google_ads_calls", Math.max(0, calls)).apply();
    }

    public void logout() {
        prefs.edit().putString("role", "").putBoolean("logged_in", false).putBoolean("driver_approved", false).remove("taxi_number").remove("request_id").remove("assigned_reservation_color").apply();
    }
}
