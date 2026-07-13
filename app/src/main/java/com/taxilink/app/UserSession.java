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
                .putString("company_password", company.password)
                .putString("owner_password", company.ownerPassword)
                .putString("central_number", company.centralNumber)
                .putString("role", "Propietario")
                .putBoolean("logged_in", true)
                .apply();
    }

    public Company getCompany() {
        String name = prefs.getString("company_name", "Taxi Central");
        String id = prefs.getString("company_id", "central");
        String pass = prefs.getString("company_password", "123456");
        String ownerPass = prefs.getString("owner_password", pass);
        String central = prefs.getString("central_number", "00000000000000000");
        return new Company(name, id, pass, ownerPass, central);
    }

    public void saveDriverLogin(String companyId, String taxiNumber, boolean remember) {
        SharedPreferences.Editor editor = prefs.edit()
                .putString("company_id", companyId)
                .putString("central_number", companyId)
                .putString("taxi_number", taxiNumber)
                .putString("role", "Conductor")
                .putBoolean("logged_in", true)
                .putBoolean("driver_approved", false);
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
        prefs.edit().putString("company_password", password).apply();
    }

    public void changeOwnerPassword(String password) {
        prefs.edit().putString("owner_password", password).apply();
    }

    public boolean isAndroidAutoTaximeterEnabled() {
        return prefs.getBoolean("android_auto_taximeter", true);
    }

    public void setAndroidAutoTaximeterEnabled(boolean enabled) {
        prefs.edit().putBoolean("android_auto_taximeter", enabled).apply();
    }

    public void logout() {
        prefs.edit().putString("role", "").putBoolean("logged_in", false).putBoolean("driver_approved", false).remove("taxi_number").remove("request_id").apply();
    }
}
