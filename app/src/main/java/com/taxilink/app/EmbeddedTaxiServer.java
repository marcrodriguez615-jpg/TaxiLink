package com.taxilink.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EmbeddedTaxiServer {
    private static EmbeddedTaxiServer instance;
    private final int port = 3000;
    private final List<AccessRequestState> requests = Collections.synchronizedList(new ArrayList<>());
    private final List<TaxiState> taxis = Collections.synchronizedList(new ArrayList<>());
    private Company company;
    private boolean running;
    private ServerSocket serverSocket;

    public static synchronized EmbeddedTaxiServer get() {
        if (instance == null) instance = new EmbeddedTaxiServer();
        return instance;
    }

    public synchronized void start(Company company) {
        this.company = company;
        if (running) return;
        running = true;
        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(port);
                while (running) new Thread(new Client(serverSocket.accept())).start();
            } catch (Exception ignored) {
                running = false;
            }
        }).start();
    }

    public String url() {
        return "http://" + localIp() + ":" + port;
    }

    public String localIp() {
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                for (java.net.InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (!a.isLoopbackAddress() && a instanceof Inet4Address) return a.getHostAddress();
                }
            }
        } catch (Exception ignored) {}
        return "127.0.0.1";
    }

    private class Client implements Runnable {
        private final Socket socket;
        Client(Socket socket) { this.socket = socket; }

        @Override public void run() {
            try {
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                String first = in.readLine();
                if (first == null) return;
                String[] parts = first.split(" ");
                String method = parts[0];
                String rawPath = parts.length > 1 ? parts[1] : "/";
                int length = 0;
                String line;
                while ((line = in.readLine()) != null && line.length() > 0) {
                    String lower = line.toLowerCase();
                    if (lower.startsWith("content-length:")) length = Integer.parseInt(line.substring(15).trim());
                }
                char[] chars = new char[length];
                if (length > 0) in.read(chars);
                JSONObject body = length > 0 ? new JSONObject(new String(chars)) : new JSONObject();
                JSONObject response = route(method, rawPath, body);
                write(200, response);
            } catch (HttpError e) {
                try { write(e.code, new JSONObject().put("error", e.getMessage())); } catch (Exception ignored) {}
            } catch (Exception e) {
                try { write(500, new JSONObject().put("error", e.getMessage())); } catch (Exception ignored) {}
            } finally {
                try { socket.close(); } catch (Exception ignored) {}
            }
        }

        private JSONObject route(String method, String rawPath, JSONObject body) throws Exception {
            String path = rawPath.split("\\?")[0];
            Map<String, String> query = query(rawPath);
            if (path.equals("/health")) return new JSONObject().put("ok", true).put("mode", "android-owner-server");
            if (path.equals("/companies") && method.equals("POST")) return createCompany(body);
            if (path.equals("/owner-login") && method.equals("POST")) return ownerLogin(body);
            if (path.equals("/access-requests") && method.equals("POST")) return createRequest(body);
            if (path.equals("/access-requests") && method.equals("GET")) return pendingRequests(query.get("identifier"));
            if (path.startsWith("/access-requests/") && path.endsWith("/approve") && method.equals("POST")) return approve(path, body);
            if (path.startsWith("/access-requests/") && method.equals("GET")) return requestStatus(path);
            if (path.equals("/taxis") && method.equals("GET")) return listTaxis(query.get("identifier"));
            if (path.startsWith("/taxis/") && path.endsWith("/location") && method.equals("POST")) return updateLocation(path, body);
            if (path.equals("/history")) return new JSONObject().put("history", new JSONArray());
            throw new HttpError(404, "Ruta no encontrada");
        }

        private JSONObject createCompany(JSONObject body) throws Exception {
            company = new Company(body.getString("name"), body.getString("identifier"), body.getString("password"), body.getString("ownerPassword"));
            return new JSONObject().put("company", new JSONObject().put("name", company.name).put("identifier", company.identifier));
        }

        private JSONObject ownerLogin(JSONObject body) throws Exception {
            requireCompany();
            if (!company.identifier.equals(body.getString("identifier")) || !company.ownerPassword.equals(body.getString("ownerPassword"))) throw new HttpError(401, "Contraseña de propietario incorrecta");
            return new JSONObject().put("company", new JSONObject().put("name", company.name).put("identifier", company.identifier));
        }

        private JSONObject createRequest(JSONObject body) throws Exception {
            requireCompany();
            if (!company.identifier.equals(body.getString("identifier")) || !company.password.equals(body.getString("password"))) throw new HttpError(401, "Contraseña de conductor incorrecta");
            AccessRequestState r = new AccessRequestState();
            r.id = String.valueOf(System.currentTimeMillis()) + body.getString("deviceId");
            r.identifier = body.getString("identifier");
            r.driverName = body.getString("driverName");
            r.taxiNumber = body.getInt("taxiNumber");
            r.status = "pending";
            r.createdAt = String.valueOf(System.currentTimeMillis());
            requests.add(r);
            return new JSONObject().put("request", r.json());
        }

        private JSONObject pendingRequests(String identifier) throws Exception {
            JSONArray arr = new JSONArray();
            synchronized (requests) {
                for (AccessRequestState r : requests) if (r.status.equals("pending") && (identifier == null || r.identifier.equals(identifier))) arr.put(r.json());
            }
            return new JSONObject().put("requests", arr);
        }

        private JSONObject requestStatus(String path) throws Exception {
            String id = path.substring("/access-requests/".length());
            synchronized (requests) {
                for (AccessRequestState r : requests) if (r.id.equals(id)) return new JSONObject().put("request", r.json());
            }
            throw new HttpError(404, "Solicitud no encontrada");
        }

        private JSONObject approve(String path, JSONObject body) throws Exception {
            String id = path.substring("/access-requests/".length(), path.length() - "/approve".length());
            synchronized (requests) {
                for (AccessRequestState r : requests) if (r.id.equals(id)) {
                    r.status = body.optBoolean("approved", true) ? "approved" : "rejected";
                    if (r.status.equals("approved")) ensureTaxi(r.identifier, r.taxiNumber, r.driverName);
                    return new JSONObject().put("request", r.json());
                }
            }
            throw new HttpError(404, "Solicitud no encontrada");
        }

        private JSONObject listTaxis(String identifier) throws Exception {
            JSONArray arr = new JSONArray();
            long now = System.currentTimeMillis();
            synchronized (taxis) {
                for (TaxiState t : taxis) if (identifier == null || t.identifier.equals(identifier)) {
                    t.online = now - t.lastMillis < 45000;
                    arr.put(t.json());
                }
            }
            return new JSONObject().put("taxis", arr);
        }

        private JSONObject updateLocation(String path, JSONObject body) throws Exception {
            int start = "/taxis/".length();
            int end = path.indexOf("/location");
            int number = Integer.parseInt(path.substring(start, end));
            TaxiState t = ensureTaxi(body.optString("identifier", company.identifier), number, body.optString("driverName", "Conductor"));
            t.latitude = body.getDouble("latitude");
            t.longitude = body.getDouble("longitude");
            t.speed = body.optInt("speed", 0);
            t.direction = body.optString("direction", "--");
            t.online = true;
            t.lastUpdate = String.valueOf(System.currentTimeMillis());
            t.lastMillis = System.currentTimeMillis();
            return new JSONObject().put("taxi", t.json());
        }

        private TaxiState ensureTaxi(String identifier, int number, String driverName) {
            synchronized (taxis) {
                for (TaxiState t : taxis) if (t.identifier.equals(identifier) && t.number == number) { t.driverName = driverName; return t; }
                TaxiState t = new TaxiState();
                t.identifier = identifier;
                t.number = number;
                t.driverName = driverName;
                t.direction = "--";
                taxis.add(t);
                return t;
            }
        }

        private void requireCompany() throws HttpError {
            if (company == null) throw new HttpError(400, "Primero crea la empresa en el móvil propietario");
        }

        private Map<String, String> query(String rawPath) throws Exception {
            Map<String, String> map = new HashMap<>();
            int q = rawPath.indexOf('?');
            if (q < 0) return map;
            for (String part : rawPath.substring(q + 1).split("&")) {
                String[] kv = part.split("=", 2);
                if (kv.length == 2) map.put(URLDecoder.decode(kv[0], "UTF-8"), URLDecoder.decode(kv[1], "UTF-8"));
            }
            return map;
        }

        private void write(int code, JSONObject json) throws Exception {
            byte[] data = json.toString().getBytes(StandardCharsets.UTF_8);
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            out.write("HTTP/1.1 " + code + " OK\r\n");
            out.write("Content-Type: application/json; charset=utf-8\r\n");
            out.write("Access-Control-Allow-Origin: *\r\n");
            out.write("Content-Length: " + data.length + "\r\n");
            out.write("Connection: close\r\n\r\n");
            out.flush();
            socket.getOutputStream().write(data);
            socket.getOutputStream().flush();
        }
    }

    private static class AccessRequestState {
        String id, identifier, driverName, status, createdAt;
        int taxiNumber;
        JSONObject json() throws Exception { return new JSONObject().put("id", id).put("identifier", identifier).put("driverName", driverName).put("taxiNumber", taxiNumber).put("status", status).put("createdAt", createdAt); }
    }

    private static class TaxiState {
        String identifier, driverName, direction, lastUpdate;
        int number, speed;
        double latitude, longitude;
        boolean online;
        long lastMillis;
        JSONObject json() throws Exception { return new JSONObject().put("identifier", identifier).put("driverName", driverName).put("number", number).put("online", online).put("speed", speed).put("direction", direction).put("latitude", latitude).put("longitude", longitude).put("lastUpdate", lastUpdate == null ? "--" : lastUpdate); }
    }

    private static class HttpError extends Exception {
        final int code;
        HttpError(int code, String message) { super(message); this.code = code; }
    }
}
