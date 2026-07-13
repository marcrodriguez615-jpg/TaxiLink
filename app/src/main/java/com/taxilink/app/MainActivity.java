package com.taxilink.app;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.Address;
import android.location.Geocoder;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.media.ToneGenerator;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ArrayAdapter;
import android.util.Base64;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;
import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends android.app.Activity {
    private final int NAVY = Color.rgb(6, 26, 46);
    private final int NAVY_DARK = Color.rgb(3, 17, 31);
    private final int YELLOW = Color.rgb(245, 196, 0);
    private final int TEAL = Color.rgb(0, 150, 136);
    private final int BG = Color.rgb(244, 246, 248);
    private final int TEXT = Color.rgb(28, 28, 28);
    private final int SECONDARY = Color.rgb(107, 114, 128);
    private final int DANGER = Color.rgb(229, 57, 53);
    private final int LINE = Color.rgb(229, 231, 235);

    private UserSession session;
    private TaxiRepository repository;
    private TextView walkieLabel;
    private Button micButton;
    private MapView mapView;
    private final Map<Integer, Marker> taxiMarkers = new HashMap<>();
    private Marker userMarker;
    private Marker serviceMarker;
    private Polyline serviceLine;
    private ChatMessage activeService;
    private MediaRecorder mediaRecorder;
    private File walkieAudioFile;
    private String lastWalkieClipId = "";
    private long lastUrgentAt = 0;
    private Taxi selectedTaxi;
    private TaxiLinkApi api;
    private Handler handler;
    private LocationListener liveLocationListener;
    private Runnable taxiPoller;
    private Runnable walkiePoller;
    private Runnable chatPoller;
    private boolean localSpeaking;
    private LinearLayout chatList;
    private TextView taxiTitleText;
    private TextView taxiInfoText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        session = new UserSession(this);
        repository = new TaxiRepository();
        api = new TaxiLinkApi(this, session);
        handler = new Handler(Looper.getMainLooper());
        selectedTaxi = repository.getTaxi(safeTaxiNumber());
        restoreSessionOrStart();
    }

    private void restoreSessionOrStart() {
        if (!session.isLoggedIn()) {
            showStartScreen();
            return;
        }
        if ("Propietario".equals(session.getRole())) {
            showOwnerPanel();
        } else if (!session.isDriverApproved()) {
            if (!session.getRequestId().isEmpty()) showWaitingApprovalScreen(session.getRequestId());
            else showLoginScreen();
        } else {
            showMapScreen();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mapView != null) mapView.onResume();
    }

    @Override
    protected void onPause() {
        if (mapView != null) mapView.onPause();
        super.onPause();
    }

    public void showStartScreen() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(NAVY);
        LinearLayout content = column();
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(dp(26), dp(42), dp(26), dp(28));
        root.addView(content, match());

        TextView logo = text("⌖", 54, YELLOW, true);
        logo.setGravity(Gravity.CENTER);
        logo.setBackground(round(YELLOW, 90, 0, YELLOW));
        LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(dp(86), dp(86));
        content.addView(logo, logoLp);

        LinearLayout title = row();
        title.setGravity(Gravity.CENTER);
        title.addView(text("Taxi", 39, Color.WHITE, true));
        title.addView(text("Link", 39, YELLOW, true));
        content.addView(title, wrapMT(20));
        TextView slogan = text("Conecta tu flota. Comunica tu camino.", 16, Color.WHITE, false);
        slogan.setGravity(Gravity.CENTER);
        slogan.setAlpha(.86f);
        content.addView(slogan, wrapMT(8));

        View taxiArt = taxiIllustration();
        content.addView(taxiArt, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(210)));
        Space sp = new Space(this);
        content.addView(sp, new LinearLayout.LayoutParams(1, 0, 1));

        Button create = button("Crear empresa", YELLOW, NAVY_DARK);
        create.setOnClickListener(v -> showCreateCompanyScreen());
        content.addView(create, matchH(56));
        Button login = button("Acceder a empresa", NAVY, Color.WHITE);
        login.setBackground(round(NAVY, 18, 2, Color.WHITE));
        login.setOnClickListener(v -> showLoginScreen());
        content.addView(login, matchHMT(56, 14));

        setContentView(root);
    }

    public void showCreateCompanyScreen() {
        LinearLayout root = baseWithHeader("Crear empresa", "🏢", true, () -> showStartScreen());
        TextView intro = text("Registra tu empresa localmente y empieza a gestionar la flota.", 15, SECONDARY, false);
        intro.setPadding(dp(22), dp(18), dp(22), 0);
        root.addView(intro);
        LinearLayout card = card();
        EditText ownerName = field("Nombre del propietario", "Ej. Marc Rodríguez", false);
        EditText name = field("Nombre de empresa", "Ej. Taxi Central", false);
        EditText id = field("Identificador", "Ej. central", false);
        EditText pass = field("Contraseña conductores", "Para que los conductores soliciten acceso", true);
        EditText ownerPass = field("Contraseña propietario", "Clave única del dueño", true);
        card.addView(ownerName); card.addView(name, mt(12)); card.addView(id, mt(12)); card.addView(pass, mt(12)); card.addView(ownerPass, mt(12));
        Button submit = button("Crear empresa", TEAL, Color.WHITE);
        submit.setOnClickListener(v -> {
            if (empty(ownerName) || empty(name) || empty(id) || pass.getText().toString().trim().length() < 6 || ownerPass.getText().toString().trim().length() < 6) {
                toast("Completa los campos. Las contraseñas deben tener mínimo 6 caracteres.");
                return;
            }
            String centralNumber = generateCentralNumber();
            Company company = new Company(name.getText().toString().trim(), id.getText().toString().trim(), pass.getText().toString().trim(), ownerPass.getText().toString().trim(), centralNumber, ownerName.getText().toString().trim());
            session.saveCompany(company);
            api.createCompany(company, (ok, error) -> runOnUiThread(() -> {
                if (error != null) toast("Empresa guardada localmente. Backend no conectado: " + error.getMessage());
                else toast("Empresa creada y conectada al backend");
                new AlertDialog.Builder(this).setTitle("Número de central").setMessage("Guarda este número. Conductores y propietario lo usarán para iniciar sesión:\n\n" + centralNumber).setPositiveButton("Entendido", null).show();
                showOwnerPanel();
            }));
        });
        card.addView(submit, matchHMT(54, 18));
        root.addView(card, cardLp());
        setContentView(scroll(root));
    }

    public void showLoginScreen() {
        LinearLayout root = baseWithHeader("Iniciar sesión conductor", "👤", true, () -> showStartScreen());
        TextView sub = text("Ingresa tus datos para continuar", 15, SECONDARY, false);
        sub.setPadding(dp(22), dp(14), dp(22), 0);
        root.addView(sub);
        LinearLayout card = card();
        EditText driverName = field("Nombre del conductor", "Ej. Aritz", false);
        EditText company = field("Número de central", "17 dígitos", false);
        company.setText(session.getRememberCompany());
        EditText pass = field("Contraseña", "Contraseña de empresa", true);
        EditText taxi = field("Número de taxi", "Ej. 3", false);
        taxi.setInputType(InputType.TYPE_CLASS_NUMBER);
        taxi.setText(session.getRememberTaxi());
        CheckBox remember = new CheckBox(this);
        remember.setText("Recordar mis datos");
        remember.setTextColor(TEXT);
        card.addView(driverName); card.addView(company, mt(12)); card.addView(pass, mt(12)); card.addView(taxi, mt(12)); card.addView(remember, mt(10));
        Button enter = button("Entrar", TEAL, Color.WHITE);
        enter.setOnClickListener(v -> {
            if (empty(driverName) || empty(company) || empty(pass) || empty(taxi)) { toast("Todos los campos son obligatorios"); return; }
            if (company.getText().toString().trim().length() != 17) { toast("El número de central debe tener 17 dígitos"); return; }
            enter.setEnabled(false);
            enter.setText("Solicitando acceso...");
            api.requestAccess(company.getText().toString().trim(), pass.getText().toString().trim(), taxi.getText().toString().trim(), driverName.getText().toString().trim(), (requestId, error) -> runOnUiThread(() -> {
                enter.setEnabled(true);
                enter.setText("Entrar");
                if (error != null) { toast("No se pudo solicitar acceso: " + error.getMessage()); return; }
                session.saveDriverLogin(company.getText().toString().trim(), taxi.getText().toString().trim(), remember.isChecked());
                session.saveDriverIdentity(driverName.getText().toString().trim(), requestId);
                showWaitingApprovalScreen(requestId);
            }));
        });
        card.addView(enter, matchHMT(54, 18));
        Button ownerEnter = button("Entrar como propietario", NAVY, Color.WHITE);
        ownerEnter.setOnClickListener(v -> {
            if (empty(company) || empty(pass)) { toast("Indica número de central y contraseña de propietario"); return; }
            if (company.getText().toString().trim().length() != 17) { toast("El número de central debe tener 17 dígitos"); return; }
            ownerEnter.setEnabled(false);
            ownerEnter.setText("Validando propietario...");
            api.ownerLogin(company.getText().toString().trim(), pass.getText().toString().trim(), (companyName, error) -> runOnUiThread(() -> {
                ownerEnter.setEnabled(true);
                ownerEnter.setText("Entrar como propietario");
                if (error != null) {
                    Company local = session.getCompany();
                    if (company.getText().toString().trim().equals(local.identifier) && pass.getText().toString().trim().equals(local.ownerPassword)) {
                        session.setRole("Propietario");
                        showOwnerPanel();
                    } else toast("Contraseña de propietario incorrecta");
                    return;
                }
                Company current = session.getCompany();
                String ownerDisplay = current.ownerName == null || current.ownerName.equals("Propietario") ? "Propietario" : current.ownerName;
                session.saveCompany(new Company(companyName, company.getText().toString().trim(), current.password, pass.getText().toString().trim(), company.getText().toString().trim(), ownerDisplay));
                showOwnerPanel();
            }));
        });
        card.addView(ownerEnter, matchHMT(54, 12));
        root.addView(card, cardLp());
        setContentView(scroll(root));
    }

    public void showWaitingApprovalScreen(String requestId) {
        LinearLayout root = baseWithHeader("Esperando aprobación", "←", true, () -> showLoginScreen());
        LinearLayout card = card();
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.addView(circleText("⏳", YELLOW, NAVY, 76));
        TextView title = text("Solicitud enviada", 23, TEXT, true);
        title.setGravity(Gravity.CENTER);
        card.addView(title, wrapMT(16));
        TextView body = text("El propietario debe aprobar a " + session.getDriverName() + " como Taxi " + session.getTaxiNumber() + ". Cuando lo apruebe, entrarás al mapa con GPS real.", 15, SECONDARY, false);
        body.setGravity(Gravity.CENTER);
        body.setPadding(dp(10), dp(8), dp(10), dp(8));
        card.addView(body);
        Button refresh = button("Comprobar ahora", TEAL, Color.WHITE);
        refresh.setOnClickListener(v -> checkApproval(requestId));
        card.addView(refresh, matchHMT(54, 18));
        Button logout = button("Cerrar sesión", DANGER, Color.WHITE);
        logout.setOnClickListener(v -> { session.logout(); showStartScreen(); });
        card.addView(logout, matchHMT(54, 12));
        root.addView(card, cardLp());
        setContentView(scroll(root));
        handler.postDelayed(new Runnable() {
            @Override public void run() {
                if (requestId.equals(session.getRequestId())) {
                    checkApproval(requestId);
                    handler.postDelayed(this, 5000);
                }
            }
        }, 1500);
    }

    private void checkApproval(String requestId) {
        api.getRequestStatus(requestId, (status, error) -> runOnUiThread(() -> {
            if (error != null) { toast("Esperando servidor: " + error.getMessage()); return; }
            if ("approved".equals(status)) {
                toast("Acceso aprobado");
                session.setDriverApproved(true);
                selectedTaxi = new Taxi(safeTaxiNumber(), true, 0, "--", 0, 0, now());
                showMapScreen();
            } else if ("rejected".equals(status)) {
                toast("Acceso rechazado por el propietario");
                showLoginScreen();
            }
        }));
    }

    @SuppressLint({"SetJavaScriptEnabled", "ClickableViewAccessibility"})
    public void showMapScreen() {
        PermissionHelper.requestNeededPermissions(this);
        Configuration.getInstance().setUserAgentValue(getPackageName());
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(BG);
        LinearLayout main = column();
        root.addView(main, match());
        main.addView(appHeader(session.getCompany().name, "● En línea", "☰", "🔔", () -> { if ("Propietario".equals(session.getRole())) showOwnerPanel(); else showProfileSettingsScreen(); }, () -> toast("Sin notificaciones nuevas")));
        mapView = new MapView(this);
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);
        mapView.getController().setZoom(15.0);
        mapView.getController().setCenter(new GeoPoint(41.6080, 2.2877));
        main.addView(mapView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        showActiveServiceOnMap();
        startRealGpsUpdates();
        startTaxiPolling();
        startWalkiePolling();

        LinearLayout panel = column();
        panel.setPadding(dp(22), dp(18), dp(22), dp(16));
        panel.setBackgroundResource(com.taxilink.app.R.drawable.bg_bottom_panel);
        root.addView(panel, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(245), Gravity.BOTTOM));
        LinearLayout info = row(); info.setGravity(Gravity.CENTER_VERTICAL);
        TextView icon = circleText("🚕", YELLOW, NAVY, 44);
        info.addView(icon);
        LinearLayout texts = column(); texts.setPadding(dp(12), 0, 0, 0);
        taxiTitleText = text(selectedTaxi.name(), 20, TEXT, true);
        taxiInfoText = text("Esperando GPS real...", 13, SECONDARY, false);
        texts.addView(taxiTitleText);
        texts.addView(taxiInfoText);
        info.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        panel.addView(info);
        LinearLayout controls = row(); controls.setGravity(Gravity.CENTER); controls.setPadding(0, dp(14), 0, 0);
        Button urgent = roundSmallButton("☎", NAVY, Color.WHITE);
        urgent.setOnClickListener(v -> sendUrgentAlert());
        controls.addView(urgent);
        micButton = button("🎙", TEAL, Color.WHITE);
        micButton.setTextSize(24);
        micButton.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN) { updateWalkieState(true); return true; }
            if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) { updateWalkieState(false); return true; }
            return true;
        });
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(dp(82), dp(70)); mlp.setMargins(dp(22), 0, dp(22), 0);
        controls.addView(micButton, mlp);
        Button chat = roundSmallButton("💬", NAVY, Color.WHITE); chat.setOnClickListener(v -> showChatScreen());
        controls.addView(chat);
        panel.addView(controls);
        walkieLabel = text("Walkie listo", 14, TEAL, true); walkieLabel.setGravity(Gravity.CENTER); walkieLabel.setBackground(round(Color.rgb(232, 249, 247), 18, 1, TEAL));
        LinearLayout.LayoutParams walkieLp = new LinearLayout.LayoutParams(dp(170), dp(36));
        walkieLp.gravity = Gravity.CENTER_HORIZONTAL;
        walkieLp.setMargins(0, dp(14), 0, 0);
        panel.addView(walkieLabel, walkieLp);
        setContentView(root);
    }

    public void updateWalkieState(boolean speaking) {
        if (speaking && !PermissionHelper.hasAudio(this)) { PermissionHelper.requestNeededPermissions(this); toast("Concede permiso de micrófono para usar Walkie"); return; }
        if (speaking) {
            localSpeaking = true;
            walkieLabel.setText("Hablando: " + selectedTaxi.name());
            micButton.setBackground(round(YELLOW, 35, 0, YELLOW));
            micButton.setTextColor(NAVY_DARK);
            startWalkieRecording();
            api.startWalkie(selectedTaxi.number, session.getDriverName(), (ok, error) -> runOnUiThread(() -> { if (error != null) toast("Walkie sin conexión: " + error.getMessage()); }));
        } else {
            localSpeaking = false;
            walkieLabel.setText("Walkie listo");
            micButton.setBackground(round(TEAL, 35, 0, TEAL));
            micButton.setTextColor(Color.WHITE);
            stopWalkieRecordingAndSend();
            api.stopWalkie(selectedTaxi.number, (ok, error) -> { });
        }
    }

    public void showOwnerPanel() {
        session.setRole("Propietario");
        LinearLayout root = baseWithHeader("Panel propietario", "☰", false, null);
        root.addView(subtitle(session.getCompany().name));
        TextView central = text("Número de central: " + session.getCentralNumber(), 15, YELLOW, true);
        central.setPadding(dp(22), dp(8), dp(22), 0);
        root.addView(central);
        TextView serverInfo = text("Conectado al servidor central TaxiLink\nLos conductores pueden conectarse desde cualquier red con Internet.", 14, TEAL, true);
        serverInfo.setPadding(dp(22), dp(10), dp(22), dp(4));
        root.addView(serverInfo);
        root.addView(ownerAction("✅", "Solicitudes de acceso", "Aprueba conductores que quieren entrar", () -> showPendingRequestsDialog()));
        root.addView(ownerAction("🚕", "Gestión de taxis", "Administra y monitorea tu flota", () -> showTaxiListScreen()));
        root.addView(ownerAction("🔐", "Activar/Desactivar usuarios", "Gestiona los accesos de conductores", () -> toast("Gestión local lista para ampliar")));
        root.addView(ownerAction("🔒", "Cambiar contraseña conductores", "Actualiza la clave para solicitar acceso", () -> showChangePasswordDialog(false)));
        root.addView(ownerAction("👑", "Cambiar contraseña propietario", "Actualiza la clave única del dueño", () -> showChangePasswordDialog(true)));
        root.addView(ownerAction("🕓", "Historial de conexiones", "Revisa los inicios de sesión", () -> showHistoryDialog()));
        root.addView(ownerAction("📋", "Vehículos registrados", "Ver listado de vehículos en la flota", () -> showTaxiListScreen()));
        root.addView(ownerAction("📅", "Calendar", "Reservas por día, hora y color", () -> showCalendarScreen()));
        Button logout = button("Cerrar sesión", DANGER, Color.WHITE);
        logout.setOnClickListener(v -> { session.logout(); showStartScreen(); });
        LinearLayout.LayoutParams logoutLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
        logoutLp.setMargins(dp(18), dp(18), dp(18), dp(24));
        root.addView(logout, logoutLp);
        Button delete = button("Eliminar empresa", Color.WHITE, DANGER);
        delete.setBackground(round(Color.WHITE, 16, 1, DANGER));
        delete.setOnClickListener(v -> confirmDeleteCompany());
        LinearLayout.LayoutParams deleteLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
        deleteLp.setMargins(dp(18), 0, dp(18), dp(28));
        root.addView(delete, deleteLp);
        setContentView(scroll(root));
    }

    public void showTaxiListScreen() {
        LinearLayout root = baseWithHeader("Lista de taxis", "☰", false, null);
        root.addView(subtitle(session.getCompany().name));
        EditText search = field("Buscar taxi", "Buscar taxi", false);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)); slp.setMargins(dp(20), dp(14), dp(20), dp(10));
        root.addView(search, slp);
        LinearLayout list = column();
        root.addView(list);
        List<Taxi> liveTaxis = new ArrayList<>();
        Runnable[] render = new Runnable[1];
        render[0] = () -> {
            list.removeAllViews();
            String q = search.getText().toString().trim().toLowerCase(Locale.ROOT);
            if (liveTaxis.isEmpty()) {
                TextView empty = text("Aún no hay taxis con GPS real conectado. Cuando un conductor sea aprobado y active ubicación, aparecerá aquí.", 15, SECONDARY, false);
                empty.setPadding(dp(24), dp(24), dp(24), dp(24));
                list.addView(empty);
                return;
            }
            for (Taxi taxi : liveTaxis) if (q.isEmpty() || taxi.name().toLowerCase(Locale.ROOT).contains(q) || taxi.driverName.toLowerCase(Locale.ROOT).contains(q)) list.addView(taxiRow(taxi));
        };
        search.addTextChangedListener(new android.text.TextWatcher() { public void beforeTextChanged(CharSequence s, int st, int c, int a) {} public void onTextChanged(CharSequence s, int st, int b, int c) { render[0].run(); } public void afterTextChanged(android.text.Editable e) {} });
        render[0].run();
        api.getTaxis(session.getCentralNumber(), (taxis, error) -> runOnUiThread(() -> {
            if (error != null) toast("No se pudo cargar la flota real: " + error.getMessage());
            else { liveTaxis.clear(); liveTaxis.addAll(taxis); render[0].run(); }
        }));
        FrameLayout frame = new FrameLayout(this); frame.setBackgroundColor(BG); frame.addView(scroll(root), match());
        frame.addView(bottomNav("Taxis"), new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(74), Gravity.BOTTOM));
        setContentView(frame);
    }

    public void showChatScreen() {
        LinearLayout root = baseWithHeader("Chats", "☰", false, null);
        root.addView(subtitle(session.getCompany().name));
        chatList = column();
        chatList.setPadding(dp(16), dp(8), dp(16), dp(86));
        root.addView(chatList, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(BG);
        frame.addView(scroll(root), match());

        LinearLayout inputBar = row();
        inputBar.setGravity(Gravity.CENTER_VERTICAL);
        inputBar.setPadding(dp(10), dp(8), dp(10), dp(8));
        inputBar.setBackgroundColor(Color.WHITE);
        Button plus = button("+", YELLOW, NAVY);
        plus.setTextSize(24);
        plus.setOnClickListener(v -> showServiceOptionsDialog());
        inputBar.addView(plus, new LinearLayout.LayoutParams(dp(52), dp(52)));
        EditText input = field("Mensaje", "Escribe un mensaje", false);
        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(0, dp(52), 1);
        inputLp.setMargins(dp(8), 0, dp(8), 0);
        inputBar.addView(input, inputLp);
        Button send = button("Enviar", TEAL, Color.WHITE);
        send.setTextSize(13);
        send.setOnClickListener(v -> {
            String text = input.getText().toString().trim();
            if (text.isEmpty()) return;
            send.setEnabled(false);
            api.sendMessage(text, (ok, error) -> runOnUiThread(() -> {
                send.setEnabled(true);
                if (error != null) toast("No se pudo enviar: " + error.getMessage());
                else { input.setText(""); loadChatMessages(); }
            }));
        });
        inputBar.addView(send, new LinearLayout.LayoutParams(dp(76), dp(52)));
        frame.addView(inputBar, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(72), Gravity.BOTTOM));
        setContentView(frame);
        loadChatMessages();
        startChatPolling();
    }

    private void loadChatMessages() {
        api.getMessages((messages, error) -> runOnUiThread(() -> {
            if (chatList == null) return;
            if (error != null) { toast("Chat sin conexión: " + error.getMessage()); return; }
            chatList.removeAllViews();
            if (messages.isEmpty()) {
                TextView empty = text("No hay mensajes todavía. Pulsa + para añadir un servicio o escribe un mensaje.", 15, SECONDARY, false);
                empty.setGravity(Gravity.CENTER);
                empty.setPadding(dp(14), dp(28), dp(14), dp(28));
                chatList.addView(empty);
                return;
            }
            for (ChatMessage message : messages) chatList.addView(messageBubble(message));
        }));
    }

    private View messageBubble(ChatMessage message) {
        LinearLayout bubble = column();
        bubble.setPadding(dp(14), dp(12), dp(14), dp(12));
        bubble.setBackground(round("service".equals(message.type) ? Color.rgb(255, 250, 225) : Color.WHITE, 18, 1, "service".equals(message.type) ? YELLOW : Color.rgb(230, 232, 235)));
        bubble.setElevation(dp(2));
        bubble.addView(text(message.sender + " · " + message.role, 13, SECONDARY, true));
        if ("service".equals(message.type)) {
            bubble.addView(text("🚕 Nuevo servicio", 18, NAVY, true), wrapMT(6));
            bubble.addView(text("Servicio: " + message.serviceType, 14, TEXT, false), wrapMT(4));
            bubble.addView(text("Tarifa: " + message.tariff, 14, TEXT, false), wrapMT(3));
            bubble.addView(text("Recoger: " + message.pickup, 14, TEXT, false), wrapMT(3));
            bubble.addView(text("Dejar: " + message.destination, 14, TEXT, false), wrapMT(3));
            if (message.phone != null && !message.phone.equals("null") && !message.phone.isEmpty()) bubble.addView(text("Teléfono: " + message.phone, 14, TEXT, false), wrapMT(3));
            if (message.description != null && !message.description.equals("null") && !message.description.isEmpty()) bubble.addView(text("Descripción: " + message.description, 14, TEXT, false), wrapMT(3));
            if ("reserved".equals(message.serviceStatus)) bubble.addView(text("Reserva: " + message.reservationDate + " · " + message.reservationTime + " · Color " + message.reservationColor, 14, NAVY, true), wrapMT(4));
            bubble.addView(text(message.fixedPrice ? "Precio cerrado" + (message.estimatedPrice == null || message.estimatedPrice.isEmpty() ? "" : ": " + message.estimatedPrice + " €") : "Precio por taxímetro", 14, TEAL, true), wrapMT(5));
            String status = message.serviceStatus == null || message.serviceStatus.equals("null") ? "pending" : message.serviceStatus;
            int statusColor = "accepted".equals(status) ? TEAL : ("cancelled".equals(status) ? DANGER : SECONDARY);
            bubble.addView(text("Estado: " + serviceStatusText(status), 14, statusColor, true), wrapMT(6));
            if ("pending".equals(status)) {
                LinearLayout actions = row();
                actions.setGravity(Gravity.CENTER_VERTICAL);
                Button accept = button("Aceptar", TEAL, Color.WHITE);
                accept.setOnClickListener(v -> updateServiceStatus(message, "accepted"));
                Button cancel = button("Cancelar", DANGER, Color.WHITE);
                cancel.setOnClickListener(v -> updateServiceStatus(message, "cancelled"));
                LinearLayout.LayoutParams a = new LinearLayout.LayoutParams(0, dp(48), 1); a.setMargins(0, dp(10), dp(6), 0);
                LinearLayout.LayoutParams c = new LinearLayout.LayoutParams(0, dp(48), 1); c.setMargins(dp(6), dp(10), 0, 0);
                actions.addView(accept, a); actions.addView(cancel, c);
                bubble.addView(actions);
            }
        } else {
            bubble.addView(text(message.text, 16, TEXT, false), wrapMT(5));
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(8), 0, 0);
        bubble.setLayoutParams(lp);
        return bubble;
    }

    private void startChatPolling() {
        if (chatPoller != null) handler.removeCallbacks(chatPoller);
        chatPoller = new Runnable() {
            @Override public void run() {
                if (chatList != null) loadChatMessages();
                handler.postDelayed(this, 5000);
            }
        };
        handler.postDelayed(chatPoller, 5000);
    }

    private String serviceStatusText(String status) {
        if ("accepted".equals(status)) return "Aceptado";
        if ("cancelled".equals(status)) return "Cancelado";
        return "Pendiente";
    }

    private void updateServiceStatus(ChatMessage message, String status) {
        api.updateServiceStatus(message.id, status, (ok, error) -> runOnUiThread(() -> {
            if (error != null) toast("No se pudo actualizar servicio: " + error.getMessage());
            else {
                if ("accepted".equals(status)) {
                    activeService = message;
                    api.setTaxiOccupied(safeTaxiNumber(), true, (ok2, error2) -> { });
                    showAcceptedServiceDistance(message);
                    showMapScreen();
                } else loadChatMessages();
            }
        }));
    }

    private void showAcceptedServiceDistance(ChatMessage message) {
        if (selectedTaxi != null && message.pickupLat != 0 && message.pickupLng != 0 && selectedTaxi.latitude != 0 && selectedTaxi.longitude != 0) {
            float[] result = new float[1];
            Location.distanceBetween(selectedTaxi.latitude, selectedTaxi.longitude, message.pickupLat, message.pickupLng, result);
            toast("Servicio aceptado. Estás a " + String.format(Locale.getDefault(), "%.1f", result[0] / 1000f) + " km de la recogida");
        } else {
            toast("Servicio aceptado. GPS hacia recogida activado.");
        }
    }

    private void showServiceOptionsDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Añadir")
                .setItems(new String[]{"Añadir servicio nuevo", "Poner reserva", "Calendar"}, (dialog, which) -> {
                    if (which == 0) showNewServiceScreen();
                    else if (which == 1) showReservationScreen();
                    else showCalendarScreen();
                })
                .show();
    }

    public void showReservationScreen() {
        LinearLayout root = baseWithHeader("Nueva reserva", "←", true, () -> showChatScreen());
        LinearLayout card = card();
        Spinner serviceType = spinner(new String[]{"Taxi urbano", "Taxi aeropuerto", "Taxi adaptado", "Servicio empresa"});
        Spinner tariff = spinner(new String[]{"Tarifa 1", "Tarifa 2", "Tarifa 3", "Tarifa 4 Aeropuerto - Moll Adossat"});
        Spinner color = spinner(new String[]{"Verde", "Amarillo", "Azul", "Rojo", "Morado"});
        EditText date = field("Día / mes / año", "Ej. 26/06/2026", false);
        EditText time = field("Hora", "Ej. 18:30", false);
        EditText pickupStreet = field("Calle recogida", "Ej. Carrer de Mallorca 401", false);
        EditText pickupCity = field("Ciudad recogida", "Ej. Barcelona", false);
        EditText destStreet = field("Calle destino", "Ej. Estació de Sants", false);
        EditText destCity = field("Ciudad destino", "Ej. Barcelona", false);
        EditText phone = field("Teléfono", "Ej. 600123456", false);
        phone.setInputType(InputType.TYPE_CLASS_PHONE);
        EditText desc = field("Descripción", "Detalles de la reserva", false);
        desc.setSingleLine(false); desc.setMinLines(3);
        card.addView(text("Servicio", 15, NAVY, true)); card.addView(serviceType, matchHMT(58, 8));
        card.addView(text("Tarifa", 15, NAVY, true), wrapMT(12)); card.addView(tariff, matchHMT(58, 8));
        card.addView(text("Fecha y hora", 15, NAVY, true), wrapMT(12)); card.addView(date, matchHMT(58, 8)); card.addView(time, matchHMT(58, 8));
        card.addView(text("Color calendario", 15, NAVY, true), wrapMT(12)); card.addView(color, matchHMT(58, 8));
        card.addView(text("Recogida", 15, NAVY, true), wrapMT(12)); card.addView(pickupStreet, matchHMT(58, 8)); card.addView(pickupCity, matchHMT(58, 8));
        card.addView(text("Destino", 15, NAVY, true), wrapMT(12)); card.addView(destStreet, matchHMT(58, 8)); card.addView(destCity, matchHMT(58, 8));
        card.addView(phone, matchHMT(58, 12)); card.addView(desc, matchHMT(86, 8));
        Button send = button("Guardar reserva", TEAL, Color.WHITE);
        send.setOnClickListener(v -> {
            if (empty(date) || empty(time) || empty(pickupStreet) || empty(pickupCity) || empty(destStreet) || empty(destCity)) { toast("Completa fecha, hora, recogida y destino"); return; }
            String pickup = smartAddress(pickupStreet.getText().toString(), pickupCity.getText().toString());
            String dest = smartAddress(destStreet.getText().toString(), destCity.getText().toString());
            double[] p = geocodeAddress(pickup); double[] d = geocodeAddress(dest);
            api.sendReservation(serviceType.getSelectedItem().toString(), tariff.getSelectedItem().toString(), pickup, dest, date.getText().toString().trim(), time.getText().toString().trim(), color.getSelectedItem().toString(), phone.getText().toString().trim(), desc.getText().toString().trim(), p[0], p[1], d[0], d[1], (ok, error) -> runOnUiThread(() -> {
                if (error != null) toast("No se pudo guardar reserva: " + error.getMessage()); else { toast("Reserva guardada"); showChatScreen(); }
            }));
        });
        card.addView(send, matchHMT(60, 18));
        root.addView(card, cardLp());
        setContentView(scroll(root));
    }

    public void showCalendarScreen() {
        LinearLayout root = baseWithHeader("Calendar", "←", true, () -> showChatScreen());
        root.addView(subtitle("Reservas por colores · " + session.getCompany().name));
        LinearLayout list = column();
        list.setPadding(dp(16), dp(12), dp(16), dp(24));
        root.addView(list);
        setContentView(scroll(root));
        api.getMessages((messages, error) -> runOnUiThread(() -> {
            list.removeAllViews();
            if (error != null) { toast("No se pudo cargar calendar: " + error.getMessage()); return; }
            boolean any = false;
            for (ChatMessage m : messages) {
                if (!"reserved".equals(m.serviceStatus)) continue;
                any = true;
                LinearLayout card = card();
                int color = calendarColor(m.reservationColor);
                card.setBackground(round(Color.WHITE, 18, 4, color));
                card.addView(text(m.reservationDate + " · " + m.reservationTime, 18, color, true));
                card.addView(text(m.pickup + " → " + m.destination, 14, TEXT, false), wrapMT(6));
                card.addView(text("Tarifa: " + m.tariff + " · " + m.serviceType, 14, SECONDARY, false), wrapMT(4));
                if (m.phone != null && !m.phone.equals("null")) card.addView(text("Teléfono: " + m.phone, 14, SECONDARY, false), wrapMT(4));
                list.addView(card, cardLp());
            }
            if (!any) {
                TextView empty = text("No hay reservas todavía. En Chats pulsa + > Poner reserva.", 15, SECONDARY, false);
                empty.setGravity(Gravity.CENTER);
                list.addView(empty);
            }
        }));
    }

    private int calendarColor(String name) {
        if (name == null) return TEAL;
        String n = name.toLowerCase(Locale.ROOT);
        if (n.contains("rojo")) return DANGER;
        if (n.contains("amarillo")) return YELLOW;
        if (n.contains("azul")) return NAVY;
        if (n.contains("morado")) return Color.rgb(126, 87, 194);
        return TEAL;
    }

    public void showNewServiceScreen() {
        LinearLayout root = baseWithHeader("Nuevo servicio", "←", true, () -> showChatScreen());
        TextView sub = text("Configura el trayecto y la tarifa", 16, Color.WHITE, false);
        sub.setGravity(Gravity.CENTER);
        LinearLayout hero = column();
        hero.setGravity(Gravity.CENTER_HORIZONTAL);
        hero.setPadding(dp(20), dp(18), dp(20), 0);
        hero.setBackgroundColor(NAVY_DARK);
        hero.addView(circleText("🚕", YELLOW, NAVY, 76));
        root.addView(hero);

        LinearLayout card = card();
        TextView serviceLabel = text("Servicio", 15, NAVY, true);
        card.addView(serviceLabel);
        Spinner serviceType = spinner(new String[]{"Taxi urbano", "Taxi aeropuerto", "Taxi adaptado", "Servicio empresa"});
        card.addView(serviceType, matchHMT(58, 8));
        card.addView(text("Tarifa de Cataluña", 15, NAVY, true), wrapMT(18));
        Spinner tariff = spinner(new String[]{"Tarifa 1", "Tarifa 2", "Tarifa 3", "Tarifa 4 Aeropuerto - Moll Adossat"});
        card.addView(tariff, matchHMT(58, 8));
        card.addView(text("Recoger al cliente en", 15, NAVY, true), wrapMT(18));
        EditText pickupStreet = field("Calle de recogida", "Ej. Carrer de Mallorca 401", false);
        EditText pickupCity = field("Ciudad de recogida", "Ej. Barcelona", false);
        card.addView(pickupStreet, matchHMT(58, 8));
        card.addView(pickupCity, matchHMT(58, 8));
        card.addView(text("Dejar al cliente en", 15, NAVY, true), wrapMT(18));
        EditText destinationStreet = field("Calle de destino", "Ej. Estació de Sants", false);
        EditText destinationCity = field("Ciudad de destino", "Ej. Barcelona", false);
        card.addView(destinationStreet, matchHMT(58, 8));
        card.addView(destinationCity, matchHMT(58, 8));
        CheckBox fixed = new CheckBox(this);
        fixed.setText("Precio cerrado");
        fixed.setTextColor(NAVY);
        fixed.setTextSize(16);
        card.addView(fixed, mt(14));
        EditText price = field("Precio cerrado", "Ej. 25", false);
        price.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        price.setVisibility(View.GONE);
        fixed.setOnCheckedChangeListener((buttonView, isChecked) -> price.setVisibility(isChecked ? View.VISIBLE : View.GONE));
        card.addView(price, matchHMT(58, 8));
        card.addView(text("Suplementos", 15, NAVY, true), wrapMT(14));
        CheckBox suppAirport = checkbox("Aeropuerto");
        CheckBox suppMoll = checkbox("Estación marítima Moll Adossat");
        CheckBox suppFira = checkbox("Fira Barcelona Gran Via");
        CheckBox suppSants = checkbox("Estación de Sants");
        CheckBox suppLarge = checkbox("Vehículo ocupado por 5-8 pasajeros");
        CheckBox suppSpecialNight = checkbox("Noche especial San Juan / Navidad / Fin de año");
        card.addView(suppAirport); card.addView(suppMoll); card.addView(suppFira); card.addView(suppSants); card.addView(suppLarge); card.addView(suppSpecialNight);
        TextView estimateDetails = text("Aproximación pendiente de calcular", 14, SECONDARY, false);
        estimateDetails.setPadding(dp(8), dp(10), dp(8), dp(4));
        card.addView(estimateDetails);
        Button datos = button("Datos", Color.WHITE, TEAL);
        datos.setBackground(round(Color.WHITE, 16, 1, TEAL));
        card.addView(datos, matchHMT(52, 12));
        LinearLayout extraData = column();
        extraData.setVisibility(View.GONE);
        EditText phone = field("Teléfono del cliente", "Ej. 600123456", false);
        phone.setInputType(InputType.TYPE_CLASS_PHONE);
        EditText description = field("Descripción del servicio", "Ej. Cliente con maletas, espera en puerta", false);
        description.setSingleLine(false);
        description.setMinLines(3);
        extraData.addView(phone, matchHMT(58, 8));
        extraData.addView(description, matchHMT(86, 8));
        datos.setOnClickListener(v -> extraData.setVisibility(extraData.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
        card.addView(extraData);
        Button send = button("Enviar servicio", TEAL, Color.WHITE);
        send.setTextSize(20);
        send.setOnClickListener(v -> {
            if (empty(pickupStreet) || empty(pickupCity) || empty(destinationStreet) || empty(destinationCity)) { toast("Indica calle y ciudad de recogida y destino"); return; }
            send.setEnabled(false);
            String pickupAddress = smartAddress(pickupStreet.getText().toString().trim(), pickupCity.getText().toString().trim());
            String destinationAddress = smartAddress(destinationStreet.getText().toString().trim(), destinationCity.getText().toString().trim());
            double[] pickupPoint = geocodeAddress(pickupAddress);
            double[] destinationPoint = geocodeAddress(destinationAddress);
            TaximeterCalculator.FareResult fare = calculateFare(tariff.getSelectedItem().toString(), pickupPoint, destinationPoint, suppAirport, suppMoll, suppFira, suppSants, suppLarge, suppSpecialNight);
            if (!fixed.isChecked()) price.setText(String.format(Locale.getDefault(), "%.2f", fare.total));
            if (pickupPoint[0] == 0 || destinationPoint[0] == 0) toast("Aviso: una dirección no se detectó bien. Se enviará igualmente.");
            api.sendService(serviceType.getSelectedItem().toString(), tariff.getSelectedItem().toString(), pickupAddress, destinationAddress, fixed.isChecked(), price.getText().toString().trim(), phone.getText().toString().trim(), description.getText().toString().trim(), pickupPoint[0], pickupPoint[1], destinationPoint[0], destinationPoint[1], (ok, error) -> runOnUiThread(() -> {
                send.setEnabled(true);
                if (error != null) toast("No se pudo enviar servicio: " + error.getMessage());
                else { toast("Servicio enviado al chat"); showChatScreen(); }
            }));
        });
        card.addView(send, matchHMT(64, 20));
        Button calc = button("Calcular", Color.WHITE, TEAL);
        calc.setBackground(round(Color.WHITE, 16, 1, TEAL));
        calc.setOnClickListener(v -> {
            if (empty(pickupStreet) || empty(pickupCity) || empty(destinationStreet) || empty(destinationCity)) { toast("Indica calle y ciudad para calcular"); return; }
            String pickupAddress = smartAddress(pickupStreet.getText().toString().trim(), pickupCity.getText().toString().trim());
            String destinationAddress = smartAddress(destinationStreet.getText().toString().trim(), destinationCity.getText().toString().trim());
            double[] pickupPoint = geocodeAddress(pickupAddress);
            double[] destinationPoint = geocodeAddress(destinationAddress);
            TaximeterCalculator.FareResult fare = calculateFare(tariff.getSelectedItem().toString(), pickupPoint, destinationPoint, suppAirport, suppMoll, suppFira, suppSants, suppLarge, suppSpecialNight);
            fixed.setChecked(true);
            price.setText(String.format(Locale.getDefault(), "%.2f", fare.total));
            estimateDetails.setText(joinFareLines(fare));
            toast("Aproximación calculada al alza");
        });
        card.addView(calc, matchHMT(58, 12));
        root.addView(card, cardLp());
        setContentView(scroll(root));
    }

    public void showProfileSettingsScreen() {
        LinearLayout root = baseWithHeader("Perfil y configuración", "←", false, null);
        LinearLayout profile = card(); profile.setGravity(Gravity.CENTER_HORIZONTAL);
        profile.addView(circleText(session.getRole().equals("Propietario") ? "👤" : "🚕", TEAL, Color.WHITE, 76));
        profile.addView(text(session.getDisplayName(), 22, TEXT, true), wrapMT(12));
        TextView role = text(session.getRole(), 14, SECONDARY, false); role.setGravity(Gravity.CENTER); profile.addView(role);
        root.addView(profile, cardLp());
        root.addView(settingsRow("Permisos de usuarios", "Gestiona roles y accesos", null));
        root.addView(ownerAction("✏", "Cambiar nombres", "Actualiza nombre visible y empresa", () -> showChangeNamesDialog()));
        root.addView(settingsRow("Notificaciones", "Alertas de flota y conexión", true));
        root.addView(settingsRow("Micrófono (Walkie)", "Comunicación local visual", true));
        root.addView(settingsRow("Ubicación en primer plano", "Mostrar posición actual", true));
        root.addView(settingsRow("Ubicación en segundo plano", "Preparado para servicio futuro", false));
        root.addView(androidAutoSettingsRow());
        Button logout = button("Cerrar sesión", DANGER, Color.WHITE);
        logout.setOnClickListener(v -> { session.logout(); showStartScreen(); });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)); lp.setMargins(dp(20), dp(18), dp(20), dp(100));
        root.addView(logout, lp);
        FrameLayout frame = new FrameLayout(this); frame.setBackgroundColor(BG); frame.addView(scroll(root), match());
        frame.addView(bottomNav("Más"), new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(74), Gravity.BOTTOM));
        setContentView(frame);
    }

    private LinearLayout baseWithHeader(String title, String left, boolean back, Runnable backAction) {
        LinearLayout root = column(); root.setBackgroundColor(BG);
        root.addView(appHeader(title, "", left, "🔔", back ? backAction : () -> showMapScreen(), () -> toast("Sin notificaciones nuevas")));
        return root;
    }

    private LinearLayout appHeader(String title, String sub, String left, String right, Runnable leftAction, Runnable rightAction) {
        LinearLayout bar = row(); bar.setGravity(Gravity.CENTER_VERTICAL); bar.setPadding(dp(16), dp(16), dp(16), dp(14)); bar.setBackgroundColor(NAVY_DARK);
        TextView l = text(left, 24, Color.WHITE, true); l.setGravity(Gravity.CENTER); l.setOnClickListener(v -> { if (leftAction != null) leftAction.run(); }); bar.addView(l, new LinearLayout.LayoutParams(dp(42), dp(48)));
        LinearLayout mid = column(); mid.addView(text(title, 20, Color.WHITE, true)); if (!sub.isEmpty()) mid.addView(text(sub, 13, TEAL, true)); bar.addView(mid, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView r = text(right, 21, Color.WHITE, false); r.setGravity(Gravity.CENTER); r.setOnClickListener(v -> rightAction.run()); bar.addView(r, new LinearLayout.LayoutParams(dp(42), dp(48)));
        return bar;
    }

    private LinearLayout ownerAction(String icon, String title, String desc, Runnable action) {
        LinearLayout row = row(); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(dp(16), dp(14), dp(14), dp(14)); row.setBackgroundResource(R.drawable.bg_card); row.setOnClickListener(v -> action.run()); row.setElevation(dp(2));
        row.addView(circleText(icon, TEAL, Color.WHITE, 46));
        LinearLayout txt = column(); txt.setPadding(dp(14), 0, 0, 0); txt.addView(text(title, 17, TEXT, true)); txt.addView(text(desc, 13, SECONDARY, false)); row.addView(txt, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row.addView(text("›", 30, SECONDARY, false));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); lp.setMargins(dp(18), dp(10), dp(18), 0); row.setLayoutParams(lp);
        return row;
    }

    private View taxiRow(Taxi taxi) {
        LinearLayout row = row(); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(dp(16), dp(14), dp(14), dp(14)); row.setBackgroundResource(R.drawable.bg_card); row.setElevation(dp(2)); row.setOnClickListener(v -> showTaxiDetail(taxi));
        row.addView(circleText("🚕", taxi.online ? YELLOW : Color.LTGRAY, NAVY, 46));
        LinearLayout txt = column(); txt.setPadding(dp(14), 0, 0, 0); txt.addView(text(taxi.name() + " · " + taxi.driverName, 17, TEXT, true)); txt.addView(text(taxi.online ? "En línea" : "Fuera de línea", 13, taxi.online ? TEAL : DANGER, true)); row.addView(txt, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row.addView(text(taxi.online ? taxi.speed + " km/h" : "--", 14, SECONDARY, true)); row.addView(text("  ›", 26, SECONDARY, false));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); lp.setMargins(dp(18), dp(8), dp(18), 0); row.setLayoutParams(lp);
        return row;
    }

    private void showTaxiDetail(Taxi taxi) {
        selectedTaxi = taxi;
        String message = "Conductor: " + taxi.driverName + "\nEstado: " + (taxi.online ? "En línea" : "Fuera de línea") + "\nVelocidad real: " + (taxi.online ? taxi.speed + " km/h" : "--") + "\nDirección: " + taxi.direction + "\nÚltima conexión: " + taxi.lastUpdate;
        AlertDialog.Builder b = new AlertDialog.Builder(this).setTitle(taxi.name()).setMessage(message).setPositiveButton("Ver en mapa", (d, w) -> showMapScreen()).setNegativeButton("Cerrar", null);
        if (session.getRole().equals("Propietario")) b.setNeutralButton(taxi.online ? "Desactivar" : "Activar", (d, w) -> { repository.toggleTaxi(taxi); showTaxiListScreen(); });
        b.show();
    }

    private LinearLayout settingsRow(String title, String desc, Boolean checked) {
        LinearLayout row = ownerAction("⚙", title, desc, () -> toast("Configuración local"));
        row.removeViewAt(row.getChildCount() - 1);
        if (checked != null) { Switch sw = new Switch(this); sw.setChecked(checked); row.addView(sw); }
        return row;
    }

    private LinearLayout androidAutoSettingsRow() {
        LinearLayout row = ownerAction("🚘", "Taxímetro en Android Auto", "Mostrar taxímetro y estado en la pantalla del coche", () -> {});
        row.removeViewAt(row.getChildCount() - 1);
        Switch sw = new Switch(this);
        sw.setChecked(session.isAndroidAutoTaximeterEnabled());
        sw.setOnCheckedChangeListener((buttonView, isChecked) -> {
            session.setAndroidAutoTaximeterEnabled(isChecked);
            toast(isChecked ? "Android Auto activado" : "Android Auto desactivado");
        });
        row.addView(sw);
        return row;
    }

    private LinearLayout bottomNav(String active) {
        LinearLayout nav = row(); nav.setGravity(Gravity.CENTER); nav.setPadding(dp(4), dp(8), dp(4), dp(8)); nav.setBackgroundColor(Color.WHITE); nav.setElevation(dp(8));
        addNav(nav, "Mapa", "⌖", active, () -> showMapScreen()); addNav(nav, "Taxis", "🚕", active, () -> showTaxiListScreen()); addNav(nav, "", "🎙", active, () -> toast("Mantén pulsado el micrófono en el mapa")); addNav(nav, "Chats", "💬", active, () -> showChatScreen()); addNav(nav, "Más", "☰", active, () -> showProfileSettingsScreen());
        return nav;
    }

    private void addNav(LinearLayout nav, String label, String icon, String active, Runnable action) {
        TextView item = text(icon + (label.isEmpty() ? "" : "\n" + label), label.equals(active) ? 13 : 12, label.equals(active) ? TEAL : SECONDARY, true); item.setGravity(Gravity.CENTER); item.setOnClickListener(v -> action.run());
        nav.addView(item, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
    }

    private void showChangePasswordDialog(boolean owner) {
        EditText input = field("Nueva contraseña", "Mínimo 6 caracteres", true);
        new AlertDialog.Builder(this).setTitle(owner ? "Contraseña propietario" : "Contraseña conductores").setView(input).setPositiveButton("Guardar", (d, w) -> {
            if (input.getText().toString().trim().length() >= 6) {
                if (owner) session.changeOwnerPassword(input.getText().toString().trim()); else session.changePassword(input.getText().toString().trim());
                toast("Contraseña actualizada");
            } else toast("Mínimo 6 caracteres");
        }).setNegativeButton("Cancelar", null).show();
    }

    private void showChangeNamesDialog() {
        LinearLayout box = column();
        box.setPadding(dp(10), dp(8), dp(10), dp(4));
        EditText companyName = field("Nombre empresa", session.getCompany().name, false);
        companyName.setText(session.getCompany().name);
        EditText ownerName = field("Nombre propietario", session.getCompany().ownerName, false);
        ownerName.setText(session.getCompany().ownerName);
        EditText driverName = field("Nombre conductor", session.getDriverName(), false);
        driverName.setText(session.getDriverName());
        if ("Propietario".equals(session.getRole())) {
            box.addView(companyName, matchH(58));
            box.addView(ownerName, matchHMT(58, 10));
        } else {
            box.addView(driverName, matchH(58));
        }
        new AlertDialog.Builder(this)
                .setTitle("Cambiar nombres")
                .setView(box)
                .setPositiveButton("Guardar", (d, w) -> {
                    if ("Propietario".equals(session.getRole())) {
                        session.updateLocalNames(companyName.getText().toString(), ownerName.getText().toString(), null);
                        api.updateCompanyNames(companyName.getText().toString(), ownerName.getText().toString(), (ok, error) -> runOnUiThread(() -> {
                            if (error != null) toast("Guardado local. Firebase: " + error.getMessage()); else toast("Nombres actualizados");
                            showProfileSettingsScreen();
                        }));
                    } else {
                        session.updateLocalNames(null, null, driverName.getText().toString());
                        toast("Nombre actualizado");
                        showProfileSettingsScreen();
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void showHistoryDialog() {
        new AlertDialog.Builder(this).setTitle("Historial de conexiones").setMessage("Hoy 09:14 · Taxi 3 conectado\nHoy 09:20 · Propietario abrió panel\nHoy 10:05 · Taxi 1 actualizó ubicación\nHoy 10:18 · Taxi 6 conectado").setPositiveButton("Cerrar", null).show();
    }

    private void showPendingRequestsDialog() {
        api.getPendingRequests(session.getCentralNumber(), (requests, error) -> runOnUiThread(() -> {
            if (error != null) { toast("No se pudieron cargar solicitudes: " + error.getMessage()); return; }
            if (requests.isEmpty()) { toast("No hay solicitudes pendientes"); return; }
            String[] items = new String[requests.size()];
            for (int i = 0; i < requests.size(); i++) items[i] = requests.get(i).driverName + " quiere entrar como Taxi " + requests.get(i).taxiNumber;
            new AlertDialog.Builder(this).setTitle("Solicitudes pendientes").setItems(items, (dialog, which) -> showApproveDialog(requests.get(which))).show();
        }));
    }

    private void showApproveDialog(AccessRequest request) {
        new AlertDialog.Builder(this)
                .setTitle(request.driverName)
                .setMessage("Quiere entrar como Taxi " + request.taxiNumber + ". ¿Autorizar acceso a la empresa?")
                .setPositiveButton("Aprobar", (d, w) -> api.approveRequest(request.id, true, (ok, error) -> runOnUiThread(() -> toast(error == null ? "Conductor aprobado" : error.getMessage()))))
                .setNegativeButton("Rechazar", (d, w) -> api.approveRequest(request.id, false, (ok, error) -> runOnUiThread(() -> toast(error == null ? "Solicitud rechazada" : error.getMessage()))))
                .setNeutralButton("Cancelar", null)
                .show();
    }

    private void confirmDeleteCompany() {
        new AlertDialog.Builder(this)
                .setTitle("Eliminar empresa")
                .setMessage("Se eliminará la empresa de Firebase. Esta acción no se puede deshacer.")
                .setPositiveButton("Eliminar", (d, w) -> api.deleteCompany((ok, error) -> runOnUiThread(() -> {
                    if (error != null) { toast("No se pudo eliminar: " + error.getMessage()); return; }
                    session.logout();
                    toast("Empresa eliminada");
                    showStartScreen();
                })))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void startRealGpsUpdates() {
        if (!PermissionHelper.hasLocation(this)) {
            PermissionHelper.requestNeededPermissions(this);
            if (taxiInfoText != null) taxiInfoText.setText("Permiso de ubicación necesario para GPS real");
            return;
        }
        try {
            LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
            if (liveLocationListener != null) lm.removeUpdates(liveLocationListener);
            liveLocationListener = new LocationListener() {
                @Override public void onLocationChanged(Location loc) {
                    int speed = Math.max(0, Math.round(loc.getSpeed() * 3.6f));
                    String direction = loc.hasBearing() ? directionFromBearing(loc.getBearing()) : "--";
                    selectedTaxi = new Taxi(safeTaxiNumber(), true, speed, direction, loc.getLatitude(), loc.getLongitude(), now());
                    selectedTaxi.driverName = session.getDriverName();
                    if (taxiTitleText != null) taxiTitleText.setText(selectedTaxi.name());
                    if (taxiInfoText != null) taxiInfoText.setText(speed + " km/h  ·  " + direction + "  ·  Actualizado " + now());
                    updateUserMarker(loc.getLatitude(), loc.getLongitude());
                    updateTaxiMarker(selectedTaxi);
                    api.sendLocation(selectedTaxi.number, session.getDriverName(), loc.getLatitude(), loc.getLongitude(), speed, direction, (ok, error) -> { });
                }
                @Override public void onStatusChanged(String p, int s, Bundle e) {}
                @Override public void onProviderEnabled(String p) {}
                @Override public void onProviderDisabled(String p) { runOnUiThread(() -> toast("Activa el GPS para ubicación real")); }
            };
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000, 5, liveLocationListener);
            lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 8000, 10, liveLocationListener);
        } catch (Exception e) {
            if (taxiInfoText != null) taxiInfoText.setText("GPS no disponible: " + e.getMessage());
        }
    }

    private void startTaxiPolling() {
        if (taxiPoller != null) handler.removeCallbacks(taxiPoller);
        taxiPoller = new Runnable() {
            @Override public void run() {
                if (!"Propietario".equals(session.getRole())) {
                    handler.postDelayed(this, 7000);
                    return;
                }
                api.getTaxis(session.getCentralNumber(), (taxis, error) -> runOnUiThread(() -> {
                    if (error == null && mapView != null) {
                        for (Taxi taxi : taxis) {
                            updateTaxiMarker(taxi);
                        }
                    }
                }));
                handler.postDelayed(this, 7000);
            }
        };
        handler.postDelayed(taxiPoller, 2000);
    }

    private void startWalkiePolling() {
        if (walkiePoller != null) handler.removeCallbacks(walkiePoller);
        walkiePoller = new Runnable() {
            @Override public void run() {
                api.getWalkieStatus((status, error) -> runOnUiThread(() -> {
                    if (error == null && walkieLabel != null && !localSpeaking) {
                        walkieLabel.setText(status);
                    }
                }));
                api.getLatestWalkieClip((clip, error) -> runOnUiThread(() -> {
                    if (error == null && clip != null && clip.id != null && !clip.id.equals(lastWalkieClipId) && !api.deviceId().equals(clip.deviceId)) {
                        lastWalkieClipId = clip.id;
                        playWalkieClip(clip);
                    }
                }));
                api.getUrgentAlert((alert, error) -> runOnUiThread(() -> {
                    if (error == null && alert != null && alert.createdAt > lastUrgentAt && !api.deviceId().equals(alert.deviceId)) {
                        lastUrgentAt = alert.createdAt;
                        playUrgentTone();
                        toast("Aviso urgente de Taxi " + alert.taxiNumber + " · " + alert.sender);
                    }
                }));
                handler.postDelayed(this, 3000);
            }
        };
        handler.postDelayed(walkiePoller, 2500);
    }

    private void startWalkieRecording() {
        try {
            walkieAudioFile = new File(getCacheDir(), "walkie_" + System.currentTimeMillis() + ".3gp");
            mediaRecorder = new MediaRecorder();
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            mediaRecorder.setOutputFile(walkieAudioFile.getAbsolutePath());
            mediaRecorder.prepare();
            mediaRecorder.start();
        } catch (Exception e) {
            toast("No se pudo grabar walkie: " + e.getMessage());
            releaseRecorder();
        }
    }

    private void stopWalkieRecordingAndSend() {
        try {
            if (mediaRecorder != null) {
                mediaRecorder.stop();
                releaseRecorder();
                if (walkieAudioFile != null && walkieAudioFile.length() > 0) {
                    String audio = fileToBase64(walkieAudioFile);
                    api.sendWalkieClip(selectedTaxi.number, session.getDriverName(), audio, (ok, error) -> runOnUiThread(() -> {
                        if (error != null) toast("No se pudo enviar audio: " + error.getMessage());
                    }));
                }
            }
        } catch (Exception e) {
            releaseRecorder();
            toast("Audio walkie demasiado corto o inválido");
        }
    }

    private void releaseRecorder() {
        try { if (mediaRecorder != null) mediaRecorder.release(); } catch (Exception ignored) { }
        mediaRecorder = null;
    }

    private String fileToBase64(File file) throws Exception {
        byte[] bytes = new byte[(int) file.length()];
        FileInputStream in = new FileInputStream(file);
        int read = in.read(bytes);
        in.close();
        if (read <= 0) return "";
        return Base64.encodeToString(bytes, Base64.NO_WRAP);
    }

    private void playWalkieClip(WalkieClip clip) {
        try {
            byte[] bytes = Base64.decode(clip.audioBase64, Base64.DEFAULT);
            File file = new File(getCacheDir(), "incoming_walkie.3gp");
            FileOutputStream out = new FileOutputStream(file);
            out.write(bytes);
            out.close();
            MediaPlayer player = new MediaPlayer();
            player.setDataSource(file.getAbsolutePath());
            player.setOnCompletionListener(MediaPlayer::release);
            player.prepare();
            player.start();
            if (walkieLabel != null) walkieLabel.setText("Escuchando: Taxi " + clip.taxiNumber + " · " + clip.sender);
        } catch (Exception e) {
            toast("No se pudo reproducir walkie");
        }
    }

    private void sendUrgentAlert() {
        playUrgentTone();
        api.sendUrgentAlert(selectedTaxi.number, session.getDriverName(), (ok, error) -> runOnUiThread(() -> {
            if (error != null) toast("No se pudo enviar aviso: " + error.getMessage());
            else toast("Aviso urgente enviado");
        }));
    }

    private void playUrgentTone() {
        try {
            ToneGenerator tone = new ToneGenerator(AudioManager.STREAM_ALARM, 100);
            tone.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 900);
            handler.postDelayed(tone::release, 1200);
        } catch (Exception ignored) { }
    }

    private void updateUserMarker(double latitude, double longitude) {
        if (mapView == null) return;
        GeoPoint point = new GeoPoint(latitude, longitude);
        if (userMarker == null) {
            userMarker = new Marker(mapView);
            userMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            userMarker.setTitle("Tu ubicación");
            mapView.getOverlays().add(userMarker);
        }
        userMarker.setPosition(point);
        mapView.getController().animateTo(point);
        if (activeService != null) showActiveServiceOnMap();
        mapView.invalidate();
    }

    private void showActiveServiceOnMap() {
        if (mapView == null || activeService == null || activeService.pickupLat == 0 || activeService.pickupLng == 0) return;
        GeoPoint pickup = new GeoPoint(activeService.pickupLat, activeService.pickupLng);
        if (serviceMarker == null) {
            serviceMarker = new Marker(mapView);
            serviceMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            mapView.getOverlays().add(serviceMarker);
        }
        serviceMarker.setPosition(pickup);
        serviceMarker.setTitle("📍 Recogida del servicio");
        serviceMarker.setSnippet(activeService.pickup + " → " + activeService.destination);
        if (serviceLine == null) {
            serviceLine = new Polyline();
            serviceLine.setColor(TEAL);
            serviceLine.setWidth(dp(4));
            mapView.getOverlays().add(serviceLine);
        }
        if (selectedTaxi != null && selectedTaxi.latitude != 0 && selectedTaxi.longitude != 0) {
            fetchRouteToService(selectedTaxi.latitude, selectedTaxi.longitude, activeService.pickupLat, activeService.pickupLng);
        }
        mapView.invalidate();
    }

    private void fetchRouteToService(double fromLat, double fromLng, double toLat, double toLng) {
        new Thread(() -> {
            try {
                String urlText = "https://router.project-osrm.org/route/v1/driving/" + fromLng + "," + fromLat + ";" + toLng + "," + toLat + "?overview=full&geometries=geojson&steps=true&language=es";
                HttpURLConnection con = (HttpURLConnection) new URL(urlText).openConnection();
                con.setConnectTimeout(8000);
                con.setReadTimeout(8000);
                BufferedReader reader = new BufferedReader(new InputStreamReader(con.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();
                JSONObject root = new JSONObject(sb.toString());
                JSONArray coords = root.getJSONArray("routes").getJSONObject(0).getJSONObject("geometry").getJSONArray("coordinates");
                JSONArray steps = root.getJSONArray("routes").getJSONObject(0).getJSONArray("legs").getJSONObject(0).getJSONArray("steps");
                List<GeoPoint> points = new ArrayList<>();
                for (int i = 0; i < coords.length(); i++) {
                    JSONArray c = coords.getJSONArray(i);
                    points.add(new GeoPoint(c.getDouble(1), c.getDouble(0)));
                }
                String instruction = "Sigue la ruta hacia la recogida";
                if (steps.length() > 0) {
                    JSONObject maneuver = steps.getJSONObject(0).getJSONObject("maneuver");
                    instruction = maneuver.optString("modifier", "Continúa") + " · " + steps.getJSONObject(0).optString("name", "ruta");
                }
                String finalInstruction = instruction;
                runOnUiThread(() -> {
                    if (serviceLine != null) serviceLine.setPoints(points);
                    if (taxiInfoText != null) taxiInfoText.setText("OCUPADO · GPS servicio · " + finalInstruction);
                    if (mapView != null) mapView.invalidate();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (serviceLine != null) {
                        List<GeoPoint> points = new ArrayList<>();
                        points.add(new GeoPoint(fromLat, fromLng));
                        points.add(new GeoPoint(toLat, toLng));
                        serviceLine.setPoints(points);
                    }
                    if (taxiInfoText != null) taxiInfoText.setText("OCUPADO · GPS servicio hacia recogida");
                });
            }
        }).start();
    }

    private void updateTaxiMarker(Taxi taxi) {
        if (mapView == null || taxi.latitude == 0 || taxi.longitude == 0) return;
        Marker marker = taxiMarkers.get(taxi.number);
        if (marker == null) {
            marker = new Marker(mapView);
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            taxiMarkers.put(taxi.number, marker);
            mapView.getOverlays().add(marker);
        }
        marker.setPosition(new GeoPoint(taxi.latitude, taxi.longitude));
        marker.setTitle("🚕 Taxi " + taxi.number + " · " + taxi.driverName + (taxi.occupied ? " · OCUPADO" : ""));
        marker.setSnippet((taxi.online ? "En línea" : "Fuera de línea") + " · " + (taxi.occupied ? "Ocupado · " : "Libre · ") + taxi.speed + " km/h · " + taxi.direction);
        mapView.invalidate();
    }

    private String directionFromBearing(float bearing) {
        String[] dirs = {"N", "NE", "E", "SE", "S", "SO", "O", "NO"};
        return dirs[Math.round(bearing / 45f) % 8];
    }

    private String js(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("'", "\\'");
    }

    private String generateCentralNumber() {
        Random random = new Random();
        StringBuilder sb = new StringBuilder();
        sb.append(random.nextInt(9) + 1);
        for (int i = 1; i < 17; i++) sb.append(random.nextInt(10));
        return sb.toString();
    }

    private double[] geocodeAddress(String address) {
        try {
            Geocoder geocoder = new Geocoder(this, Locale.getDefault());
            List<Address> results = geocoder.getFromLocationName(address, 1);
            if ((results == null || results.isEmpty()) && !address.toLowerCase(Locale.ROOT).contains("españa")) {
                results = geocoder.getFromLocationName(address + ", España", 1);
            }
            if (results != null && !results.isEmpty()) return new double[]{results.get(0).getLatitude(), results.get(0).getLongitude()};
        } catch (Exception ignored) { }
        return new double[]{0, 0};
    }

    private String smartAddress(String street, String city) {
        String cleanStreet = normalizeAddressPart(street);
        String cleanCity = normalizeAddressPart(city);
        return cleanStreet + ", " + cleanCity + ", España";
    }

    private String normalizeAddressPart(String value) {
        String v = value.trim().replaceAll("\\s+", " ");
        String lower = v.toLowerCase(Locale.ROOT);
        if (lower.startsWith("c/ ")) v = "Calle " + v.substring(3);
        else if (lower.startsWith("c. ")) v = "Calle " + v.substring(3);
        else if (lower.startsWith("av. ")) v = "Avenida " + v.substring(4);
        else if (lower.startsWith("av ")) v = "Avenida " + v.substring(3);
        else if (lower.startsWith("pza. ")) v = "Plaza " + v.substring(5);
        else if (lower.startsWith("pl. ")) v = "Plaza " + v.substring(4);
        return v;
    }

    private TaximeterCalculator.FareResult calculateFare(String tariff, double[] pickup, double[] destination, CheckBox airport, CheckBox moll, CheckBox fira, CheckBox sants, CheckBox large, CheckBox specialNight) {
        double km = 0;
        if (pickup[0] != 0 && destination[0] != 0) {
            float[] result = new float[1];
            Location.distanceBetween(pickup[0], pickup[1], destination[0], destination[1], result);
            km = Math.max(0.8, (result[0] / 1000.0) * 1.18);
        }
        int minutes = (int) Math.ceil((km / 24.0) * 60.0 + 4.0);
        TaximeterCalculator.SupplementOptions options = new TaximeterCalculator.SupplementOptions();
        options.airport = airport.isChecked();
        options.mollAdossat = moll.isChecked();
        options.firaGranVia = fira.isChecked();
        options.santsStation = sants.isChecked();
        options.largeCapacity = large.isChecked();
        options.specialNight = specialNight.isChecked();
        return TaximeterCalculator.estimate(tariff, km, minutes, options);
    }

    private String joinFareLines(TaximeterCalculator.FareResult fare) {
        StringBuilder sb = new StringBuilder();
        for (String line : fare.lines) sb.append(line).append("\n");
        return sb.toString().trim();
    }

    private View taxiIllustration() {
        FrameLayout f = new FrameLayout(this); f.setPadding(0, dp(26), 0, dp(10));
        TextView road = text("━━━━━━━", 50, Color.WHITE, true); road.setAlpha(.18f); road.setGravity(Gravity.CENTER); f.addView(road, match());
        TextView car = text("🚕", 88, YELLOW, true); car.setGravity(Gravity.CENTER); f.addView(car, match());
        return f;
    }

    private TextView subtitle(String s) { TextView t = text(s, 14, SECONDARY, false); t.setPadding(dp(22), dp(10), dp(22), 0); return t; }
    private LinearLayout column() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout row() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); return l; }
    private ScrollView scroll(View v) { ScrollView s = new ScrollView(this); s.setBackgroundColor(BG); s.addView(v); return s; }
    private LinearLayout card() { LinearLayout c = column(); c.setPadding(dp(18), dp(18), dp(18), dp(18)); c.setBackgroundResource(R.drawable.bg_card); c.setElevation(dp(3)); return c; }
    private LinearLayout.LayoutParams cardLp() { LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); lp.setMargins(dp(20), dp(18), dp(20), dp(20)); return lp; }
    private TextView text(String s, int sp, int color, boolean bold) { TextView t = new TextView(this); t.setText(s); t.setTextSize(sp); t.setTextColor(color); if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD); return t; }
    private TextView circleText(String s, int bg, int color, int size) { TextView t = text(s, size > 60 ? 28 : 20, color, true); t.setGravity(Gravity.CENTER); t.setBackground(round(bg, size / 2, 0, bg)); t.setLayoutParams(new LinearLayout.LayoutParams(dp(size), dp(size))); return t; }
    private EditText field(String label, String hint, boolean password) { EditText e = new EditText(this); e.setHint(hint); e.setTextColor(TEXT); e.setHintTextColor(SECONDARY); e.setTextSize(15); e.setSingleLine(true); e.setBackgroundResource(R.drawable.bg_field); e.setInputType(password ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD : InputType.TYPE_CLASS_TEXT); return e; }
    private CheckBox checkbox(String label) { CheckBox c = new CheckBox(this); c.setText(label); c.setTextColor(TEXT); c.setTextSize(14); return c; }
    private Spinner spinner(String[] values) { Spinner s = new Spinner(this); ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, values); adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); s.setAdapter(adapter); s.setBackgroundResource(R.drawable.bg_field); return s; }
    private Button button(String s, int bg, int color) { Button b = new Button(this); b.setText(s); b.setTextColor(color); b.setTextSize(16); b.setTypeface(Typeface.DEFAULT, Typeface.BOLD); b.setAllCaps(false); b.setBackground(round(bg, 18, 0, bg)); return b; }
    private Button roundSmallButton(String s, int bg, int color) { Button b = button(s, bg, color); b.setTextSize(20); b.setLayoutParams(new LinearLayout.LayoutParams(dp(58), dp(58))); return b; }
    private android.graphics.drawable.GradientDrawable round(int color, int radius, int stroke, int strokeColor) { android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable(); g.setColor(color); g.setCornerRadius(dp(radius)); if (stroke > 0) g.setStroke(dp(stroke), strokeColor); return g; }
    private boolean empty(EditText e) { return e.getText().toString().trim().isEmpty(); }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
    private String now() { return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()); }
    private int safeTaxiNumber() { try { return Integer.parseInt(session.getTaxiNumber()); } catch (Exception e) { return 3; } }
    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + .5f); }
    private LinearLayout.LayoutParams matchH(int h) { return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(h)); }
    private LinearLayout.LayoutParams matchHMT(int h, int mt) { LinearLayout.LayoutParams lp = matchH(h); lp.setMargins(0, dp(mt), 0, 0); return lp; }
    private LinearLayout.LayoutParams matchWMT(int mt) { LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); lp.setMargins(0, dp(mt), 0, 0); return lp; }
    private LinearLayout.LayoutParams mt(int mt) { LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); lp.setMargins(0, dp(mt), 0, 0); return lp; }
    private LinearLayout.LayoutParams wrapMT(int mt) { LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT); lp.setMargins(0, dp(mt), 0, 0); return lp; }
    private FrameLayout.LayoutParams match() { return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT); }
}
