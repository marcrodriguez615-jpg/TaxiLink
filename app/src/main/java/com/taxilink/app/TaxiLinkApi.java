package com.taxilink.app;

import android.content.Context;
import android.provider.Settings;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.auth.FirebaseAuth;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TaxiLinkApi {
    public interface Callback<T> { void onResult(T value, Exception error); }

    private final Context context;
    private final UserSession session;
    private final FirebaseFirestore db;
    private final FirebaseAuth auth;

    public TaxiLinkApi(Context context, UserSession session) {
        this.context = context.getApplicationContext();
        this.session = session;
        this.db = FirebaseFirestore.getInstance();
        this.auth = FirebaseAuth.getInstance();
        ensureAuth(null);
    }

    public String deviceId() {
        return Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
    }

    public String baseUrl() {
        return "Firebase Firestore";
    }

    public void createCompany(Company company, Callback<Boolean> callback) {
        ensureAuth(error -> {
            if (error != null) { callback.onResult(null, error); return; }
        Map<String, Object> data = new HashMap<>();
        data.put("name", company.name);
        data.put("identifier", company.identifier);
        data.put("password", company.password);
        data.put("ownerPassword", company.ownerPassword);
        data.put("centralNumber", company.centralNumber);
        data.put("ownerUid", auth.getCurrentUser().getUid());
        data.put("createdAt", System.currentTimeMillis());
        db.collection("companies").document(company.centralNumber).set(data)
                .addOnSuccessListener(v -> callback.onResult(true, null))
                .addOnFailureListener(e -> callback.onResult(null, e));
        });
    }

    public void ownerLogin(String centralNumber, String ownerPassword, Callback<String> callback) {
        ensureAuth(error -> {
            if (error != null) { callback.onResult(null, error); return; }
        db.collection("companies").document(centralNumber).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) { callback.onResult(null, new Exception("La central no existe")); return; }
                    if (!ownerPassword.equals(doc.getString("ownerPassword"))) { callback.onResult(null, new Exception("Contraseña de propietario incorrecta")); return; }
                    callback.onResult(doc.getString("name"), null);
                })
                .addOnFailureListener(e -> callback.onResult(null, e));
        });
    }

    public void requestAccess(String centralNumber, String password, String taxiNumber, String driverName, Callback<String> callback) {
        ensureAuth(error -> {
            if (error != null) { callback.onResult(null, error); return; }
        db.collection("companies").document(centralNumber).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) { callback.onResult(null, new Exception("La central no existe")); return; }
                    if (!password.equals(doc.getString("password"))) { callback.onResult(null, new Exception("Contraseña de conductores incorrecta")); return; }
                    String requestId = centralNumber + "_" + deviceId();
                    Map<String, Object> req = new HashMap<>();
                    req.put("id", requestId);
                    req.put("identifier", centralNumber);
                    req.put("centralNumber", centralNumber);
                    req.put("taxiNumber", Integer.parseInt(taxiNumber));
                    req.put("driverName", driverName);
                    req.put("deviceId", deviceId());
                    req.put("status", "pending");
                    req.put("createdAt", System.currentTimeMillis());
                    db.collection("companies").document(centralNumber).collection("accessRequests").document(requestId).set(req)
                            .addOnSuccessListener(v -> callback.onResult(requestId, null))
                            .addOnFailureListener(e -> callback.onResult(null, e));
                })
                .addOnFailureListener(e -> callback.onResult(null, e));
        });
    }

    public void getRequestStatus(String requestId, Callback<String> callback) {
        ensureAuth(error -> {
            if (error != null) { callback.onResult(null, error); return; }
        String central = session.getCentralNumber();
        db.collection("companies").document(central).collection("accessRequests").document(requestId).get()
                .addOnSuccessListener(doc -> callback.onResult(doc.exists() ? doc.getString("status") : "pending", null))
                .addOnFailureListener(e -> callback.onResult(null, e));
        });
    }

    public void getPendingRequests(String centralNumber, Callback<List<AccessRequest>> callback) {
        ensureAuth(error -> {
            if (error != null) { callback.onResult(null, error); return; }
        db.collection("companies").document(centralNumber).collection("accessRequests").whereEqualTo("status", "pending").get()
                .addOnSuccessListener(q -> {
                    List<AccessRequest> result = new ArrayList<>();
                    for (DocumentSnapshot doc : q.getDocuments()) {
                        Long taxi = doc.getLong("taxiNumber");
                        result.add(new AccessRequest(doc.getId(), doc.getString("driverName"), taxi == null ? 0 : taxi.intValue(), String.valueOf(doc.getLong("createdAt"))));
                    }
                    callback.onResult(result, null);
                })
                .addOnFailureListener(e -> callback.onResult(null, e));
        });
    }

    public void approveRequest(String requestId, boolean approved, Callback<Boolean> callback) {
        ensureAuth(error -> {
            if (error != null) { callback.onResult(null, error); return; }
        String central = session.getCentralNumber();
        db.collection("companies").document(central).collection("accessRequests").document(requestId).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) { callback.onResult(null, new Exception("Solicitud no encontrada")); return; }
                    Map<String, Object> update = new HashMap<>();
                    update.put("status", approved ? "approved" : "rejected");
                    update.put("reviewedAt", System.currentTimeMillis());
                    doc.getReference().update(update);
                    if (approved) {
                        Long taxi = doc.getLong("taxiNumber");
                        Map<String, Object> t = new HashMap<>();
                        t.put("number", taxi == null ? 0 : taxi.intValue());
                        t.put("driverName", doc.getString("driverName"));
                        t.put("online", false);
                        db.collection("companies").document(central).collection("taxis").document(String.valueOf(t.get("number"))).set(t);
                    }
                    callback.onResult(true, null);
                })
                .addOnFailureListener(e -> callback.onResult(null, e));
        });
    }

    public void sendLocation(int taxiNumber, String driverName, double latitude, double longitude, int speed, String direction, Callback<Boolean> callback) {
        ensureAuth(error -> {
            if (error != null) { callback.onResult(null, error); return; }
        String central = session.getCentralNumber();
        Map<String, Object> data = new HashMap<>();
        data.put("number", taxiNumber);
        data.put("driverName", driverName);
        data.put("latitude", latitude);
        data.put("longitude", longitude);
        data.put("speed", speed);
        data.put("direction", direction);
        data.put("online", true);
        data.put("lastUpdate", System.currentTimeMillis());
        db.collection("companies").document(central).collection("taxis").document(String.valueOf(taxiNumber)).set(data)
                .addOnSuccessListener(v -> callback.onResult(true, null))
                .addOnFailureListener(e -> callback.onResult(null, e));
        });
    }

    public void getTaxis(String centralNumber, Callback<List<Taxi>> callback) {
        ensureAuth(error -> {
            if (error != null) { callback.onResult(null, error); return; }
        db.collection("companies").document(centralNumber).collection("taxis").get()
                .addOnSuccessListener(q -> {
                    List<Taxi> result = new ArrayList<>();
                    long now = System.currentTimeMillis();
                    for (DocumentSnapshot doc : q.getDocuments()) {
                        Long n = doc.getLong("number"); Long speed = doc.getLong("speed"); Long last = doc.getLong("lastUpdate");
                        Double lat = doc.getDouble("latitude"); Double lng = doc.getDouble("longitude");
                        if (n == null || lat == null || lng == null) continue;
                        boolean online = last != null && now - last < 45000;
                        Taxi taxi = new Taxi(n.intValue(), online, speed == null ? 0 : speed.intValue(), doc.getString("direction"), lat, lng, last == null ? "--" : String.valueOf(last));
                        taxi.driverName = doc.getString("driverName");
                        result.add(taxi);
                    }
                    callback.onResult(result, null);
                })
                .addOnFailureListener(e -> callback.onResult(null, e));
        });
    }

    public void startWalkie(int taxiNumber, String driverName, Callback<Boolean> callback) {
        ensureAuth(error -> {
            if (error != null) { callback.onResult(null, error); return; }
        Map<String, Object> data = new HashMap<>();
        data.put("taxiNumber", taxiNumber); data.put("driverName", driverName); data.put("speaking", true); data.put("updatedAt", System.currentTimeMillis());
        db.collection("companies").document(session.getCentralNumber()).collection("state").document("walkie").set(data)
                .addOnSuccessListener(v -> callback.onResult(true, null)).addOnFailureListener(e -> callback.onResult(null, e));
        });
    }

    public void stopWalkie(int taxiNumber, Callback<Boolean> callback) {
        ensureAuth(error -> {
            if (error != null) { callback.onResult(null, error); return; }
        db.collection("companies").document(session.getCentralNumber()).collection("state").document("walkie").update("speaking", false, "updatedAt", System.currentTimeMillis())
                .addOnSuccessListener(v -> callback.onResult(true, null)).addOnFailureListener(e -> callback.onResult(null, e));
        });
    }

    public void getWalkieStatus(Callback<String> callback) {
        ensureAuth(error -> {
            if (error != null) { callback.onResult(null, error); return; }
        db.collection("companies").document(session.getCentralNumber()).collection("state").document("walkie").get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists() || !Boolean.TRUE.equals(doc.getBoolean("speaking"))) { callback.onResult("Walkie listo", null); return; }
                    Long updated = doc.getLong("updatedAt");
                    if (updated == null || System.currentTimeMillis() - updated > 30000) { callback.onResult("Walkie listo", null); return; }
                    callback.onResult("Hablando: Taxi " + doc.getLong("taxiNumber") + " · " + doc.getString("driverName"), null);
                })
                .addOnFailureListener(e -> callback.onResult(null, e));
        });
    }

    public void getMessages(Callback<List<ChatMessage>> callback) {
        ensureAuth(error -> {
            if (error != null) { callback.onResult(null, error); return; }
        db.collection("companies").document(session.getCentralNumber()).collection("messages").orderBy("createdAt", Query.Direction.ASCENDING).limit(100).get()
                .addOnSuccessListener(q -> {
                    List<ChatMessage> result = new ArrayList<>();
                    for (DocumentSnapshot doc : q.getDocuments()) result.add(toMessage(doc));
                    callback.onResult(result, null);
                })
                .addOnFailureListener(e -> callback.onResult(null, e));
        });
    }

    public void sendMessage(String text, Callback<Boolean> callback) {
        ensureAuth(error -> {
            if (error != null) { callback.onResult(null, error); return; }
        Map<String, Object> data = baseMessage("text", text);
        db.collection("companies").document(session.getCentralNumber()).collection("messages").add(data)
                .addOnSuccessListener(v -> callback.onResult(true, null)).addOnFailureListener(e -> callback.onResult(null, e));
        });
    }

    public void sendService(String serviceType, String tariff, String pickup, String destination, boolean fixedPrice, String estimatedPrice, Callback<Boolean> callback) {
        ensureAuth(error -> {
            if (error != null) { callback.onResult(null, error); return; }
        Map<String, Object> service = new HashMap<>();
        service.put("serviceType", serviceType); service.put("tariff", tariff); service.put("pickup", pickup); service.put("destination", destination); service.put("fixedPrice", fixedPrice); service.put("estimatedPrice", estimatedPrice);
        Map<String, Object> data = baseMessage("service", "Nuevo servicio: " + pickup + " → " + destination);
        data.put("service", service);
        db.collection("companies").document(session.getCentralNumber()).collection("messages").add(data)
                .addOnSuccessListener(v -> callback.onResult(true, null)).addOnFailureListener(e -> callback.onResult(null, e));
        });
    }

    public void deleteCompany(Callback<Boolean> callback) {
        ensureAuth(error -> {
            if (error != null) { callback.onResult(null, error); return; }
        db.collection("companies").document(session.getCentralNumber()).delete()
                .addOnSuccessListener(v -> callback.onResult(true, null)).addOnFailureListener(e -> callback.onResult(null, e));
        });
    }

    private interface AuthReady { void onReady(Exception error); }

    private void ensureAuth(AuthReady ready) {
        if (auth.getCurrentUser() != null) {
            if (ready != null) ready.onReady(null);
            return;
        }
        auth.signInAnonymously()
                .addOnSuccessListener(result -> { if (ready != null) ready.onReady(null); })
                .addOnFailureListener(e -> { if (ready != null) ready.onReady(e); });
    }

    private Map<String, Object> baseMessage(String type, String text) {
        Map<String, Object> data = new HashMap<>();
        data.put("sender", "Propietario".equals(session.getRole()) ? "Propietario" : session.getDriverName());
        data.put("role", session.getRole());
        data.put("type", type);
        data.put("text", text);
        data.put("createdAt", System.currentTimeMillis());
        return data;
    }

    private ChatMessage toMessage(DocumentSnapshot doc) {
        ChatMessage m = new ChatMessage();
        m.id = doc.getId(); m.sender = doc.getString("sender"); m.role = doc.getString("role"); m.type = doc.getString("type"); m.text = doc.getString("text"); m.createdAt = String.valueOf(doc.getLong("createdAt"));
        Map<String, Object> s = (Map<String, Object>) doc.get("service");
        if (s != null) { m.serviceType = String.valueOf(s.get("serviceType")); m.tariff = String.valueOf(s.get("tariff")); m.pickup = String.valueOf(s.get("pickup")); m.destination = String.valueOf(s.get("destination")); m.fixedPrice = Boolean.TRUE.equals(s.get("fixedPrice")); m.estimatedPrice = String.valueOf(s.get("estimatedPrice")); }
        return m;
    }
}
