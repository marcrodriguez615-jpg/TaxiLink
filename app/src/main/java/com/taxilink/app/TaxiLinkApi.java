package com.taxilink.app;

import android.content.Context;
import android.provider.Settings;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class TaxiLinkApi {
    public interface Callback<T> { void onResult(T value, Exception error); }

    private final Context context;
    private final UserSession session;

    public TaxiLinkApi(Context context, UserSession session) {
        this.context = context.getApplicationContext();
        this.session = session;
    }

    public String deviceId() {
        return Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
    }

    public String baseUrl() {
        return session.getServerUrl();
    }

    public void createCompany(Company company, Callback<Boolean> callback) {
        run(callback, () -> {
            JSONObject body = new JSONObject();
            body.put("name", company.name);
            body.put("identifier", company.identifier);
            body.put("password", company.password);
            body.put("ownerPassword", company.ownerPassword);
            request("POST", "/companies", body);
            return true;
        });
    }

    public void ownerLogin(String identifier, String ownerPassword, Callback<String> callback) {
        run(callback, () -> {
            JSONObject body = new JSONObject();
            body.put("identifier", identifier);
            body.put("ownerPassword", ownerPassword);
            JSONObject response = request("POST", "/owner-login", body);
            return response.getJSONObject("company").getString("name");
        });
    }

    public void requestAccess(String identifier, String password, String taxiNumber, String driverName, Callback<String> callback) {
        run(callback, () -> {
            JSONObject body = new JSONObject();
            body.put("identifier", identifier);
            body.put("password", password);
            body.put("taxiNumber", taxiNumber);
            body.put("driverName", driverName);
            body.put("deviceId", deviceId());
            JSONObject response = request("POST", "/access-requests", body);
            return response.getJSONObject("request").getString("id");
        });
    }

    public void getRequestStatus(String requestId, Callback<String> callback) {
        run(callback, () -> request("GET", "/access-requests/" + requestId, null).getJSONObject("request").getString("status"));
    }

    public void getPendingRequests(String identifier, Callback<List<AccessRequest>> callback) {
        run(callback, () -> {
            JSONObject response = request("GET", "/access-requests?identifier=" + identifier, null);
            JSONArray array = response.getJSONArray("requests");
            List<AccessRequest> requests = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.getJSONObject(i);
                requests.add(new AccessRequest(o.getString("id"), o.getString("driverName"), o.getInt("taxiNumber"), o.getString("createdAt")));
            }
            return requests;
        });
    }

    public void approveRequest(String requestId, boolean approved, Callback<Boolean> callback) {
        run(callback, () -> {
            JSONObject body = new JSONObject();
            body.put("approved", approved);
            request("POST", "/access-requests/" + requestId + "/approve", body);
            return true;
        });
    }

    public void sendLocation(int taxiNumber, String driverName, double latitude, double longitude, int speed, String direction, Callback<Boolean> callback) {
        run(callback, () -> {
            JSONObject body = new JSONObject();
            body.put("identifier", session.getCompany().identifier);
            body.put("driverName", driverName);
            body.put("latitude", latitude);
            body.put("longitude", longitude);
            body.put("speed", speed);
            body.put("direction", direction);
            request("POST", "/taxis/" + taxiNumber + "/location", body);
            return true;
        });
    }

    public void getTaxis(String identifier, Callback<List<Taxi>> callback) {
        run(callback, () -> {
            JSONObject response = request("GET", "/taxis?identifier=" + identifier, null);
            JSONArray array = response.getJSONArray("taxis");
            List<Taxi> taxis = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.getJSONObject(i);
                if (o.isNull("latitude") || o.isNull("longitude")) continue;
                Taxi taxi = new Taxi(o.getInt("number"), o.optBoolean("online", false), o.optInt("speed", 0), o.optString("direction", "--"), o.getDouble("latitude"), o.getDouble("longitude"), o.optString("lastUpdate", "--"));
                taxi.driverName = o.optString("driverName", "Conductor");
                taxis.add(taxi);
            }
            return taxis;
        });
    }

    public void startWalkie(int taxiNumber, String driverName, Callback<Boolean> callback) {
        run(callback, () -> {
            JSONObject body = new JSONObject();
            body.put("identifier", session.getCompany().identifier);
            body.put("taxiNumber", taxiNumber);
            body.put("driverName", driverName);
            request("POST", "/walkie/start", body);
            return true;
        });
    }

    public void stopWalkie(int taxiNumber, Callback<Boolean> callback) {
        run(callback, () -> {
            JSONObject body = new JSONObject();
            body.put("identifier", session.getCompany().identifier);
            body.put("taxiNumber", taxiNumber);
            request("POST", "/walkie/stop", body);
            return true;
        });
    }

    public void getWalkieStatus(Callback<String> callback) {
        run(callback, () -> {
            JSONObject response = request("GET", "/walkie?identifier=" + session.getCompany().identifier, null);
            JSONObject walkie = response.getJSONObject("walkie");
            if (!walkie.optBoolean("speaking", false)) return "Walkie listo";
            return "Hablando: Taxi " + walkie.optInt("taxiNumber") + " · " + walkie.optString("driverName", "Conductor");
        });
    }

    private interface Work<T> { T execute() throws Exception; }

    private <T> void run(Callback<T> callback, Work<T> work) {
        new Thread(() -> {
            try { callback.onResult(work.execute(), null); }
            catch (Exception e) { callback.onResult(null, e); }
        }).start();
    }

    private JSONObject request(String method, String path, JSONObject body) throws Exception {
        URL url = new URL(baseUrl() + path);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setConnectTimeout(8000);
        con.setReadTimeout(8000);
        con.setRequestMethod(method);
        con.setRequestProperty("Accept", "application/json");
        if (body != null) {
            byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
            con.setDoOutput(true);
            con.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            con.setRequestProperty("Content-Length", String.valueOf(bytes.length));
            OutputStream os = con.getOutputStream();
            os.write(bytes);
            os.close();
        }
        int code = con.getResponseCode();
        InputStream is = code >= 200 && code < 300 ? con.getInputStream() : con.getErrorStream();
        String text = read(is);
        if (code < 200 || code >= 300) throw new Exception(text.isEmpty() ? "Error HTTP " + code : text);
        return text.isEmpty() ? new JSONObject() : new JSONObject(text);
    }

    private String read(InputStream stream) throws Exception {
        if (stream == null) return "";
        BufferedReader br = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        br.close();
        return sb.toString();
    }
}
