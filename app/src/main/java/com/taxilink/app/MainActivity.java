package com.taxilink.app;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.Address;
import android.location.Geocoder;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.media.ToneGenerator;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
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
import java.util.Collections;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.util.MapTileIndex;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Overlay;
import org.osmdroid.views.overlay.Polyline;
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay;
import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends android.app.Activity {
    private final int NAVY = Color.rgb(10, 24, 48);
    private final int NAVY_DARK = Color.rgb(5, 12, 28);
    private final int YELLOW = Color.rgb(255, 199, 44);
    private final int TEAL = Color.rgb(0, 166, 150);
    private final int BG = Color.rgb(239, 243, 248);
    private final int TEXT = Color.rgb(19, 28, 45);
    private final int SECONDARY = Color.rgb(96, 110, 130);
    private final int DANGER = Color.rgb(220, 53, 69);
    private final int LINE = Color.rgb(219, 226, 236);
    private final int CARD = Color.rgb(255, 255, 255);
    private final int BLUE = Color.rgb(36, 99, 235);
    private final int INK = Color.rgb(11, 18, 32);

    private UserSession session;
    private TaxiRepository repository;
    private TextView walkieLabel;
    private Button micButton;
    private MapView mapView;
    private final Map<Integer, Marker> taxiMarkers = new HashMap<>();
    private Marker userMarker;
    private Marker navigationArrowMarker;
    private Marker serviceMarker;
    private Polyline serviceLineShadow;
    private Polyline serviceLine;
    private ChatMessage activeService;
    private String navInstruction = "Sigue la ruta hacia la recogida";
    private String navDistance = "--";
    private String navEta = "--";
    private String navNext = "Después  ↱";
    private String navRouteInfo = "";
    private boolean activeRouteHasTolls;
    private String activeRouteTollInfo = "";
    private int activeRouteTrafficDelaySeconds;
    private int activeRouteEtaMinutes = 7;
    private boolean routeFetchInFlight;
    private long lastRoadAlertAt = 0;
    private String lastRoadAlert = "";
    private float lastStableBearing = 0f;
    private boolean followGpsBearing = false;
    private boolean routingToDestination = false;
    private TextView navStreetText;
    private TextView navNextText;
    private TextView navEtaText;
    private TextView navMetaText;
    private TextView navSpeedText;
    private GpsArrowOverlay gpsArrowOverlay;
    private final List<GeoPoint> activeRoutePoints = new ArrayList<>();
    private final List<NavManeuver> activeManeuvers = new ArrayList<>();
    private int nextManeuverIndex = 0;
    private long lastRouteFetchAt = 0;
    private MediaRecorder mediaRecorder;
    private File walkieAudioFile;
    private String lastWalkieClipId = "";
    private long lastWalkieClipAt = System.currentTimeMillis();
    private long lastUrgentAt = System.currentTimeMillis();
    private boolean urgentAlertPrimed = true;
    private Location lastAcceptedLocation;
    private long lastLocationSentAt = 0;
    private long lastLocationUiAt = 0;
    private long lastCameraMoveAt = 0;
    private long lastGpsFixAt = 0;
    private Taxi selectedTaxi;
    private TaxiLinkApi api;
    private Handler handler;
    private LocationListener liveLocationListener;
    private Runnable taxiPoller;
    private Runnable chatPoller;
    private Runnable serviceAlertPoller;
    private Runnable approvalPoller;
    private boolean walkieListenersStarted;
    private boolean localSpeaking;
    private LinearLayout chatList;
    private TextView taxiTitleText;
    private TextView taxiInfoText;
    private TextView centerMapButton;
    private boolean mapManuallyMoved;
    private ChatMessage pendingServiceAlert;
    private String lastPromptedServiceId = "";
    private boolean servicePopupShowing;
    private View serviceBubbleView;
    private MediaPlayer serviceAlertPlayer;
    private MediaPlayer reservationReminderPlayer;
    private Runnable reservationReminderSpeaker;
    private String assignedReservationColor = "";
    private boolean loadingReservationColor;
    private long lastReservationColorRefreshAt;
    private long lastAccessValidationAt;
    private final java.util.Calendar calendarCursor = java.util.Calendar.getInstance();
    private String calendarViewMode = "Mes";
    private int calendarRenderGeneration;
    private Bitmap startScreenBitmap;
    private boolean completingActiveService;
    private TextToSpeech tts;

    private static class NavManeuver {
        String instruction;
        GeoPoint point;
        NavManeuver(String instruction, GeoPoint point) { this.instruction = instruction; this.point = point; }
    }

    private static class RouteInsight {
        boolean hasTolls;
        String tollInfo = "";
        int trafficDelaySeconds;
        String source = "OSM";
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        trimCacheDir(getCacheDir(), 40L * 1024L * 1024L);
        trimCacheDir(getExternalCacheDir(), 40L * 1024L * 1024L);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        session = new UserSession(this);
        repository = new TaxiRepository();
        api = new TaxiLinkApi(this, session);
        handler = new Handler(Looper.getMainLooper());
        tts = new TextToSpeech(this, status -> { if (status == TextToSpeech.SUCCESS) tts.setLanguage(new Locale("es", "ES")); });
        assignedReservationColor = session.getAssignedReservationColor();
        selectedTaxi = repository.getTaxi(safeTaxiNumber());
        restoreSessionOrStart();
        handleReservationReminderIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleReservationReminderIntent(intent);
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
            validateDriverSessionOnStart();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mapView != null && mapView.isAttachedToWindow()) {
            mapView.onResume();
            if (session != null && session.isLoggedIn()) startRealGpsUpdates();
        }
    }

    @Override
    protected void onPause() {
        if (mapView != null) mapView.onPause();
        stopRealGpsUpdates();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        stopRealGpsUpdates();
        stopApprovalPolling();
        if (api != null) api.stopRealtimeListeners();
        if (handler != null) handler.removeCallbacksAndMessages(null);
        releaseRecorder();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        stopServiceAlertSound();
        stopReservationReminderLoop();
        super.onDestroy();
    }

    public void showStartScreen() {
        stopLiveWork();
        setContentView(new View(this) {
            private final Bitmap image = loadStartScreenBitmap();
            private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            private float scale;
            private float imageLeft;
            private float imageTop;

            {
                setBackgroundColor(Color.BLACK);
                setContentDescription("TaxiLink. Acceder a empresa o crear empresa");
                setClickable(true);
            }

            @Override protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                if (image == null) return;
                scale = Math.min(getWidth() / (float) image.getWidth(), getHeight() / (float) image.getHeight());
                float width = image.getWidth() * scale;
                float height = image.getHeight() * scale;
                imageLeft = (getWidth() - width) / 2f;
                imageTop = (getHeight() - height) / 2f;
                android.graphics.RectF destination = new android.graphics.RectF(imageLeft, imageTop, imageLeft + width, imageTop + height);
                canvas.drawBitmap(image, null, destination, paint);
            }

            @Override public boolean onTouchEvent(MotionEvent event) {
                if (event.getAction() != MotionEvent.ACTION_UP || image == null || scale <= 0) return true;
                float x = (event.getX() - imageLeft) / scale;
                float y = (event.getY() - imageTop) / scale;
                if (x >= 126 && x <= 895 && y >= 628 && y <= 858) {
                    performClick();
                    showLoginScreen();
                } else if (x >= 126 && x <= 895 && y >= 886 && y <= 1118) {
                    performClick();
                    showCreateCompanyScreen();
                }
                return true;
            }

            @Override public boolean performClick() {
                super.performClick();
                return true;
            }
        });
    }

    private Bitmap loadStartScreenBitmap() {
        if (startScreenBitmap == null || startScreenBitmap.isRecycled()) {
            startScreenBitmap = android.graphics.BitmapFactory.decodeResource(getResources(), R.drawable.taxilink_start_screen);
        }
        return startScreenBitmap;
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
        EditText ownerPass = field("Contraseña propietario", "Clave única del dueño", true);
        EditText driverPass = field("Contraseña conductores", "Una clave compartida por todos", true);
        card.addView(ownerName); card.addView(name, mt(12)); card.addView(id, mt(12)); card.addView(ownerPass, mt(12)); card.addView(driverPass, mt(12));
        Button submit = button("Crear empresa", TEAL, Color.WHITE);
        submit.setOnClickListener(v -> {
            if (empty(ownerName) || empty(name) || empty(id)
                    || ownerPass.getText().toString().trim().length() < 6
                    || driverPass.getText().toString().trim().length() < 6) {
                toast("Completa la empresa y las dos contraseñas de mínimo 6 caracteres.");
                return;
            }
            String centralNumber = generateCentralNumber();
            Company company = new Company(name.getText().toString().trim(), id.getText().toString().trim(),
                    driverPass.getText().toString().trim(), ownerPass.getText().toString().trim(),
                    centralNumber, ownerName.getText().toString().trim());
            api.createCompany(company, (ok, error) -> runOnUiThread(() -> {
                if (error != null) { showError("No se pudo crear la empresa", error); return; }
                session.saveCompany(company);
                toast("Empresa creada y conectada al backend");
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
        EditText driverName = field("Tu nombre", "Ej. Aritz", false);
        EditText company = field("Número de central", "17 dígitos", false);
        String rememberedCompany = session.getRememberCompany();
        company.setText(rememberedCompany.isEmpty() ? session.getCentralNumber() : rememberedCompany);
        EditText pass = field("Contraseña", "Propietario o conductores", true);
        EditText taxi = field("Número de taxi", "Ej. 3", false);
        taxi.setInputType(InputType.TYPE_CLASS_NUMBER);
        taxi.setText(session.getRememberTaxi());
        CheckBox remember = new CheckBox(this);
        remember.setText("Recordar mis datos");
        remember.setTextColor(TEXT);
        card.addView(driverName); card.addView(company, mt(12)); card.addView(pass, mt(12)); card.addView(taxi, mt(12)); card.addView(remember, mt(10));
        Button enter = button("Entrar", TEAL, Color.WHITE);
        enter.setOnClickListener(v -> {
            if (empty(driverName) || empty(company) || empty(pass) || empty(taxi)) { toast("Nombre, central, contraseña y taxi son obligatorios"); return; }
            if (company.getText().toString().trim().length() != 17) { toast("El número de central debe tener 17 dígitos"); return; }
            String taxiNumber = taxi.getText().toString().trim();
            try {
                if (Integer.parseInt(taxiNumber) <= 0) { toast("El número de taxi debe ser mayor que cero"); return; }
            } catch (NumberFormatException error) {
                toast("Número de taxi inválido");
                return;
            }
            enter.setEnabled(false);
            enter.setText("Solicitando acceso...");
            api.requestAccess(company.getText().toString().trim(), pass.getText().toString().trim(), taxiNumber, driverName.getText().toString().trim(), (requestId, error) -> runOnUiThread(() -> {
                enter.setEnabled(true);
                enter.setText("Entrar");
                if (error != null) { showError("No se pudo solicitar acceso", error); return; }
                session.saveDriverLogin(company.getText().toString().trim(), taxiNumber, remember.isChecked());
                session.saveDriverIdentity(driverName.getText().toString().trim(), requestId);
                showWaitingApprovalScreen(requestId);
            }));
        });
        card.addView(enter, matchHMT(54, 18));
        Button ownerEnter = button("Entrar como propietario", NAVY, Color.WHITE);
        ownerEnter.setOnClickListener(v -> {
            if (empty(driverName) || empty(company) || empty(pass)) { toast("Indica tu nombre, número de central y contraseña de propietario"); return; }
            if (company.getText().toString().trim().length() != 17) { toast("El número de central debe tener 17 dígitos"); return; }
            ownerEnter.setEnabled(false);
            ownerEnter.setText("Validando propietario...");
            api.ownerLogin(company.getText().toString().trim(), pass.getText().toString().trim(), driverName.getText().toString().trim(), (companyName, error) -> runOnUiThread(() -> {
                ownerEnter.setEnabled(true);
                ownerEnter.setText("Entrar como propietario");
                if (error != null) {
                    showError("No se pudo validar al propietario", error);
                    return;
                }
                showOwnerPanel();
            }));
        });
        card.addView(ownerEnter, matchHMT(54, 12));
        root.addView(card, cardLp());
        setContentView(scroll(root));
    }

    public void showWaitingApprovalScreen(String requestId) {
        stopApprovalPolling();
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
        logout.setOnClickListener(v -> logoutAndShowStart());
        card.addView(logout, matchHMT(54, 12));
        root.addView(card, cardLp());
        setContentView(scroll(root));
        approvalPoller = new Runnable() {
            @Override public void run() {
                if (requestId.equals(session.getRequestId()) && !session.isDriverApproved()) {
                    checkApproval(requestId);
                    handler.postDelayed(this, 5000);
                }
            }
        };
        handler.postDelayed(approvalPoller, 1500);
    }

    private void stopApprovalPolling() {
        if (handler != null && approvalPoller != null) handler.removeCallbacks(approvalPoller);
        approvalPoller = null;
    }

    private void checkApproval(String requestId) {
        api.getRequestStatus(requestId, (status, error) -> runOnUiThread(() -> {
            if (error != null) { toast("Esperando servidor: " + error.getMessage()); return; }
            if ("approved".equals(status)) {
                stopApprovalPolling();
                toast("Acceso aprobado");
                session.setDriverApproved(true);
                selectedTaxi = new Taxi(safeTaxiNumber(), true, 0, "--", 0, 0, now());
                showMapScreen();
            } else if ("rejected".equals(status)) {
                stopApprovalPolling();
                toast("Acceso rechazado por el propietario");
                showLoginScreen();
            }
        }));
    }

    private void validateDriverSessionOnStart() {
        String requestId = session.getRequestId();
        if (!hasText(requestId)) {
            logoutAndShowStart();
            return;
        }
        api.getRequestStatus(requestId, (status, error) -> runOnUiThread(() -> {
            if (error != null) {
                restoreActiveDriverService();
                return;
            }
            if ("approved".equals(status)) restoreActiveDriverService();
            else forceRevokedDriverLogout();
        }));
    }

    private void restoreActiveDriverService() {
        api.getMessages((messages, error) -> runOnUiThread(() -> {
            activeService = null;
            if (error == null && messages != null) {
                for (int i = messages.size() - 1; i >= 0; i--) {
                    ChatMessage message = messages.get(i);
                    if ("service".equals(message.type) && "accepted".equals(message.serviceStatus) && message.acceptedTaxi == safeTaxiNumber()) {
                        activeService = message;
                        session.startCarTaximeterFromMobileService(message.id);
                        break;
                    }
                }
            }
            showMapScreen();
        }));
    }

    private void validateActiveDriverAccess() {
        if ("Propietario".equals(session.getRole()) || !session.isDriverApproved()) return;
        long now = System.currentTimeMillis();
        if (now - lastAccessValidationAt < 10000) return;
        lastAccessValidationAt = now;
        api.getRequestStatus(session.getRequestId(), (status, error) -> runOnUiThread(() -> {
            if (error == null && !"approved".equals(status)) forceRevokedDriverLogout();
        }));
    }

    private void forceRevokedDriverLogout() {
        stopRealGpsUpdates();
        if (serviceAlertPoller != null) handler.removeCallbacks(serviceAlertPoller);
        serviceAlertPoller = null;
        stopServiceAlertSound();
        stopReservationReminderLoop();
        logoutAndShowStart();
        toast("El administrador ha revocado tu acceso");
    }

    @SuppressLint({"SetJavaScriptEnabled", "ClickableViewAccessibility"})
    public void showMapScreen() {
        PermissionHelper.requestNeededPermissions(this);
        Configuration.getInstance().setUserAgentValue(getPackageName());
        File osmdroidCache = new File(getCacheDir(), "osmdroid");
        Configuration.getInstance().setOsmdroidBasePath(osmdroidCache);
        Configuration.getInstance().setOsmdroidTileCache(new File(osmdroidCache, "tiles"));
        detachCurrentMap();
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(BG);
        LinearLayout main = column();
        root.addView(main, match());
        if (activeService == null) main.addView(mapHeader());
        mapView = new MapView(this);
        applyBestMapTiles();
        mapView.setUseDataConnection(true);
        mapView.setTilesScaledToDpi(true);
        mapView.setMinZoomLevel(3.0);
        mapView.setMaxZoomLevel(19.0);
        mapView.setMultiTouchControls(true);
        mapView.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN || e.getAction() == MotionEvent.ACTION_POINTER_DOWN || e.getAction() == MotionEvent.ACTION_MOVE) {
                mapManuallyMoved = true;
                showCenterMapButton();
            }
            return false;
        });
        enableMapRotationGestures();
        mapView.getController().setZoom(18.5);
        mapView.getController().setCenter(new GeoPoint(41.6080, 2.2877));
        main.addView(mapView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        showActiveServiceOnMap();
        startRealGpsUpdates();
        startTaxiPolling();
        startWalkiePolling();
        startServiceAlertPolling();

        if (activeService != null) addNavigationOverlay(root); else addTaxiControlPanel(root);
        addCenterMapButton(root);
        setContentView(root);
        showPendingServiceBubble();
    }

    private void detachCurrentMap() {
        if (mapView != null) {
            try {
                mapView.onPause();
                mapView.onDetach();
            } catch (Exception ignored) { }
        }
        taxiMarkers.clear();
        userMarker = null;
        navigationArrowMarker = null;
        serviceMarker = null;
        serviceLineShadow = null;
        serviceLine = null;
        gpsArrowOverlay = null;
        mapView = null;
    }

    private void addCenterMapButton(FrameLayout root) {
        centerMapButton = text("Centrar", 16, Color.WHITE, true);
        centerMapButton.setGravity(Gravity.CENTER);
        centerMapButton.setBackground(round(Color.rgb(30, 102, 220), 22, 0, Color.rgb(30, 102, 220)));
        centerMapButton.setElevation(dp(9));
        centerMapButton.setVisibility(View.GONE);
        centerMapButton.setOnClickListener(v -> centerMapNow());
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(132), dp(46), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        lp.setMargins(0, activeService == null ? dp(86) : dp(92), 0, 0);
        root.addView(centerMapButton, lp);
    }

    private void showCenterMapButton() {
        if (centerMapButton != null) centerMapButton.setVisibility(View.VISIBLE);
    }

    private void hideCenterMapButton() {
        if (centerMapButton != null) centerMapButton.setVisibility(View.GONE);
    }

    private void centerMapNow() {
        if (mapView == null) return;
        mapManuallyMoved = false;
        hideCenterMapButton();
        if (activeService != null && selectedTaxi != null && selectedTaxi.latitude != 0 && selectedTaxi.longitude != 0) {
            followGpsBearing = true;
            mapView.getController().setZoom(19.0);
            setNavigationCamera(new GeoPoint(selectedTaxi.latitude, selectedTaxi.longitude));
            return;
        }
        if (selectedTaxi != null && selectedTaxi.latitude != 0 && selectedTaxi.longitude != 0) {
            mapView.getController().setZoom(18.5);
            mapView.getController().animateTo(new GeoPoint(selectedTaxi.latitude, selectedTaxi.longitude));
            return;
        }
        centerFleetOnMap();
    }

    private void centerFleetOnMap() {
        if (mapView == null || taxiMarkers.isEmpty()) return;
        List<GeoPoint> points = new ArrayList<>();
        for (Marker marker : taxiMarkers.values()) if (marker != null && marker.getPosition() != null) points.add(marker.getPosition());
        if (points.isEmpty()) return;
        if (points.size() == 1) {
            mapView.getController().setZoom(17.5);
            mapView.getController().animateTo(points.get(0));
            return;
        }
        mapView.zoomToBoundingBox(BoundingBox.fromGeoPoints(points), true, dp(80));
    }

    private void addNavigationOverlay(FrameLayout root) {
        LinearLayout top = column();
        top.setPadding(dp(14), dp(12), dp(14), dp(12));
        top.setBackground(navGradient());
        top.setElevation(dp(10));
        LinearLayout turn = row(); turn.setGravity(Gravity.CENTER_VERTICAL);
        TextView arrow = text("↑", 38, Color.WHITE, true); arrow.setGravity(Gravity.CENTER); turn.addView(arrow, new LinearLayout.LayoutParams(dp(50), dp(58)));
        LinearLayout turnText = column();
        turnText.addView(text(routingToDestination ? "llevar a" : "recoger en", 16, Color.WHITE, true));
        navStreetText = text(shortStreet(routingToDestination ? activeService.destination : activeService.pickup), 24, Color.WHITE, true);
        turnText.addView(navStreetText, wrapMT(1));
        turn.addView(turnText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView mic = circleText("🎙", Color.WHITE, TEAL, 46); turn.addView(mic);
        top.addView(turn);
        navNextText = text(navNext, 16, Color.WHITE, true); navNextText.setPadding(dp(16), dp(7), dp(16), dp(8)); navNextText.setBackground(round(Color.argb(72, 0, 0, 0), 14, 1, Color.argb(60, 255, 255, 255))); top.addView(navNextText, new LinearLayout.LayoutParams(dp(168), ViewGroup.LayoutParams.WRAP_CONTENT));
        FrameLayout.LayoutParams topLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP);
        topLp.setMargins(dp(18), dp(18), dp(18), 0);
        root.addView(top, topLp);

        addFloatingNavButton(root, "◈", dp(56), Gravity.RIGHT | Gravity.CENTER_VERTICAL, dp(16), -dp(64), () -> toggleMapBearingMode());
        addFloatingNavButton(root, "⌕", dp(56), Gravity.RIGHT | Gravity.CENTER_VERTICAL, dp(16), 0, () -> toast("Buscar en ruta"));
        addFloatingNavButton(root, "🔊", dp(56), Gravity.RIGHT | Gravity.CENTER_VERTICAL, dp(16), dp(64), () -> toast("Sonido de navegación"));

        navSpeedText = text(selectedTaxi != null && selectedTaxi.speed > 0 ? selectedTaxi.speed + "\nkm/h" : "--\nkm/h", 15, Color.WHITE, true);
        navSpeedText.setGravity(Gravity.CENTER); navSpeedText.setBackground(round(Color.argb(232, 11, 18, 32), 42, 2, Color.argb(170, 255, 255, 255))); navSpeedText.setElevation(dp(8));
        FrameLayout.LayoutParams speedLp = new FrameLayout.LayoutParams(dp(66), dp(66), Gravity.LEFT | Gravity.BOTTOM); speedLp.setMargins(dp(14), 0, 0, dp(136)); root.addView(navSpeedText, speedLp);

        TextView street = text(shortStreet(routingToDestination ? activeService.destination : activeService.pickup), 20, Color.WHITE, true);
        street.setGravity(Gravity.CENTER); street.setBackground(round(BLUE, 18, 0, BLUE)); street.setElevation(dp(8));
        FrameLayout.LayoutParams streetLp = new FrameLayout.LayoutParams(dp(220), dp(40), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL); streetLp.setMargins(0, 0, 0, dp(136)); root.addView(street, streetLp);

        Button notify = button("⚠  Notificar", Color.BLACK, Color.WHITE); notify.setTextSize(15); notify.setOnClickListener(v -> sendUrgentAlert());
        FrameLayout.LayoutParams notifyLp = new FrameLayout.LayoutParams(dp(170), dp(48), Gravity.RIGHT | Gravity.BOTTOM); notifyLp.setMargins(0, 0, dp(12), dp(132)); root.addView(notify, notifyLp);

        LinearLayout panel = column();
        panel.setPadding(dp(20), dp(12), dp(20), dp(16));
        panel.setBackground(round(Color.rgb(9, 14, 25), 30, 0, Color.rgb(9, 14, 25)));
        panel.setElevation(dp(14));
        panel.addView(handleBar());
        LinearLayout etaRow = row(); etaRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView close = text("×", 38, Color.WHITE, false); close.setGravity(Gravity.CENTER); close.setOnClickListener(v -> confirmAbandonActiveService()); etaRow.addView(close, new LinearLayout.LayoutParams(dp(60), dp(66)));
        LinearLayout eta = column(); eta.setGravity(Gravity.CENTER); navEtaText = text(navEta, 34, Color.rgb(125, 220, 160), true); navMetaText = text(navDistance + " · " + arrivalClock(), 18, Color.LTGRAY, false); eta.addView(navEtaText); eta.addView(navMetaText); etaRow.addView(eta, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView alt = text("↱↰", 28, Color.WHITE, true); alt.setGravity(Gravity.CENTER); alt.setBackground(round(Color.BLACK, 30, 2, Color.DKGRAY)); etaRow.addView(alt, new LinearLayout.LayoutParams(dp(60), dp(60)));
        panel.addView(etaRow);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(122), Gravity.BOTTOM);
        lp.setMargins(0, 0, 0, 0);
        root.addView(panel, lp);
    }

    private void addFloatingNavButton(FrameLayout root, String label, int size, int gravity, int marginRight, int yOffset, Runnable action) {
        TextView b = text(label, 32, Color.WHITE, true); b.setGravity(Gravity.CENTER); b.setBackground(round(Color.argb(232, 11, 18, 32), size / 2, 1, Color.argb(150, 255, 255, 255))); b.setElevation(dp(9)); b.setOnClickListener(v -> action.run());
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(size, size, gravity); lp.setMargins(0, Math.max(0, yOffset), marginRight, Math.max(0, -yOffset)); root.addView(b, lp);
    }

    private void enableMapRotationGestures() {
        if (mapView == null) return;
        RotationGestureOverlay rotation = new RotationGestureOverlay(mapView);
        rotation.setEnabled(true);
        mapView.getOverlays().add(rotation);
    }

    private void toggleMapBearingMode() {
        if (mapView == null) return;
        followGpsBearing = !followGpsBearing;
        if (followGpsBearing) {
            mapView.setMapOrientation(-lastStableBearing);
            toast("Mapa siguiendo el rumbo GPS");
        } else {
            mapView.setMapOrientation(0);
            toast("Mapa libre: gira con dos dedos");
        }
        mapView.invalidate();
    }

    private TextView navStat(String label, String value) {
        TextView v = text(label + "\n" + value, 14, Color.WHITE, true); v.setGravity(Gravity.CENTER); return v;
    }

    private TextView handleBar() {
        TextView h = text("━━━━", 18, Color.LTGRAY, true); h.setGravity(Gravity.CENTER); return h;
    }

    private String shortStreet(String address) {
        if (address == null || address.equals("null") || address.trim().isEmpty()) return "la recogida";
        String s = address.split(",")[0].trim();
        return s.length() > 22 ? s.substring(0, 22) : s;
    }

    private String arrivalClock() {
        return new SimpleDateFormat("H:mm", Locale.getDefault()).format(new Date(System.currentTimeMillis() + Math.max(1, activeRouteEtaMinutes) * 60000L));
    }

    private View routeLine(String icon, String label, String value, int color) {
        LinearLayout row = row(); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(0, dp(6), 0, 0);
        row.addView(text(icon, 20, color, true), new LinearLayout.LayoutParams(dp(34), ViewGroup.LayoutParams.WRAP_CONTENT));
        row.addView(text(label + ": ", 15, SECONDARY, false));
        row.addView(text(value == null || value.equals("null") ? "--" : value, 15, TEXT, true));
        return row;
    }

    private void addTaxiControlPanel(FrameLayout root) {
        LinearLayout panel = column();
        panel.setPadding(dp(22), dp(18), dp(22), dp(16));
        panel.setBackground(round(Color.WHITE, 30, 1, Color.rgb(232, 237, 246)));
        panel.setElevation(dp(14));
        root.addView(panel, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(300), Gravity.BOTTOM));
        LinearLayout info = row(); info.setGravity(Gravity.CENTER_VERTICAL);
        info.addView(circleText("🚕", YELLOW, NAVY, 44));
        LinearLayout texts = column(); texts.setPadding(dp(12), 0, 0, 0);
        taxiTitleText = text(selectedTaxi.name(), 20, TEXT, true);
        taxiInfoText = text("Esperando GPS real...", 13, SECONDARY, false);
        texts.addView(taxiTitleText); texts.addView(taxiInfoText); info.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1)); panel.addView(info);
        LinearLayout controls = row(); controls.setGravity(Gravity.CENTER); controls.setPadding(0, dp(14), 0, 0);
        Button urgent = roundSmallButton("☎", NAVY, Color.WHITE); urgent.setOnClickListener(v -> sendUrgentAlert()); controls.addView(urgent);
        micButton = button("🎙", TEAL, Color.WHITE); micButton.setTextSize(24); micButton.setOnTouchListener((v, e) -> { if (e.getAction() == MotionEvent.ACTION_DOWN) { updateWalkieState(true); return true; } if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) { updateWalkieState(false); return true; } return true; });
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(dp(82), dp(70)); mlp.setMargins(dp(22), 0, dp(22), 0); controls.addView(micButton, mlp);
        Button chat = roundSmallButton("💬", NAVY, Color.WHITE); chat.setOnClickListener(v -> showChatScreen()); controls.addView(chat); panel.addView(controls);
        walkieLabel = text("Walkie listo", 14, TEAL, true); walkieLabel.setGravity(Gravity.CENTER); walkieLabel.setBackground(round(Color.rgb(232, 249, 247), 18, 1, TEAL)); LinearLayout.LayoutParams walkieLp = new LinearLayout.LayoutParams(dp(170), dp(36)); walkieLp.gravity = Gravity.CENTER_HORIZONTAL; walkieLp.setMargins(0, dp(14), 0, 0); panel.addView(walkieLabel, walkieLp);
    }

    private void applyBestMapTiles() {
        if (mapView == null) return;
        if (BuildConfig.HERE_API_KEY != null && !BuildConfig.HERE_API_KEY.trim().isEmpty()) {
            setHereMapTileSource();
            return;
        }
        setMapTileSource("OpenStreetMap", new String[]{"https://a.tile.openstreetmap.org/", "https://b.tile.openstreetmap.org/", "https://c.tile.openstreetmap.org/"});
        new Thread(() -> {
            if (!tileServerWorks("https://a.tile.openstreetmap.org/12/2072/1524.png")) {
                runOnUiThread(() -> {
                    if (mapView != null) {
                        setMapTileSource("Carto Voyager", new String[]{"https://a.basemaps.cartocdn.com/rastertiles/voyager/", "https://b.basemaps.cartocdn.com/rastertiles/voyager/", "https://c.basemaps.cartocdn.com/rastertiles/voyager/"});
                        mapView.invalidate();
                    }
                });
            }
        }).start();
    }

    private void setHereMapTileSource() {
        mapView.setTileSource(new OnlineTileSourceBase("HERE", 1, 20, 256, ".png", new String[]{"https://maps.hereapi.com/v3/base/mc/"}) {
            @Override public String getTileURLString(long pMapTileIndex) {
                int z = MapTileIndex.getZoom(pMapTileIndex);
                int x = MapTileIndex.getX(pMapTileIndex);
                int y = MapTileIndex.getY(pMapTileIndex);
                return getBaseUrl() + z + "/" + x + "/" + y + "/png?size=256&lang=es&style=explore.day&apiKey=" + BuildConfig.HERE_API_KEY;
            }
        });
    }

    private void setMapTileSource(String name, String[] baseUrls) {
        mapView.setTileSource(new OnlineTileSourceBase(name, 1, 19, 256, ".png", baseUrls) {
            @Override public String getTileURLString(long pMapTileIndex) {
                int z = MapTileIndex.getZoom(pMapTileIndex);
                int x = MapTileIndex.getX(pMapTileIndex);
                int y = MapTileIndex.getY(pMapTileIndex);
                return getBaseUrl() + z + "/" + x + "/" + y + ".png";
            }
        });
    }

    private boolean tileServerWorks(String urlText) {
        try {
            HttpURLConnection con = (HttpURLConnection) new URL(urlText).openConnection();
            con.setConnectTimeout(3500);
            con.setReadTimeout(3500);
            con.setRequestProperty("User-Agent", getPackageName());
            int code = con.getResponseCode();
            return code >= 200 && code < 300;
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean isXiaomiFamilyDevice() {
        String maker = android.os.Build.MANUFACTURER == null ? "" : android.os.Build.MANUFACTURER.toLowerCase(Locale.ROOT);
        String brand = android.os.Build.BRAND == null ? "" : android.os.Build.BRAND.toLowerCase(Locale.ROOT);
        return maker.contains("xiaomi") || maker.contains("redmi") || maker.contains("poco") || brand.contains("xiaomi") || brand.contains("redmi") || brand.contains("poco");
    }

    public void updateWalkieState(boolean speaking) {
        if (speaking && !PermissionHelper.hasAudio(this)) { PermissionHelper.requestNeededPermissions(this); toast("Concede permiso de micrófono para usar Walkie"); return; }
        if (speaking) {
            localSpeaking = true;
            walkieLabel.setText("Hablando: " + selectedTaxi.name());
            micButton.setBackground(round(YELLOW, 35, 0, YELLOW));
            micButton.setTextColor(NAVY_DARK);
            startWalkieRecording();
            api.startWalkie(selectedTaxi.number, session.getDisplayName(), (ok, error) -> runOnUiThread(() -> { if (error != null) toast("Walkie sin conexión: " + error.getMessage()); }));
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
        LinearLayout root = column();
        root.setBackgroundColor(BG);
        root.addView(ownerHeader());

        LinearLayout grid = column();
        grid.setPadding(dp(18), dp(10), dp(18), 0);
        addOwnerDashboardGrid(grid);
        root.addView(grid);

        root.addView(fleetStatusCard(), ownerPageLp(14));
        root.addView(ownerBigWalkieButton(), ownerPageLp(14));

        Button logout = button("↪  Cerrar sesión", DANGER, Color.WHITE);
        logout.setTextSize(20);
        logout.setOnClickListener(v -> logoutAndShowStart());
        LinearLayout.LayoutParams logoutLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(70));
        logoutLp.setMargins(dp(18), dp(14), dp(18), dp(98));
        root.addView(logout, logoutLp);
        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(BG);
        frame.addView(scroll(root), match());
        frame.addView(ownerBottomNav(), new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(82), Gravity.BOTTOM));
        setContentView(frame);
        startWalkiePolling();
        if (session.isAdminCountsAsTaxi()) startRealGpsUpdates();
    }

    private LinearLayout ownerHeader() {
        LinearLayout bar = row();
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(18), dp(24), dp(18), dp(18));
        bar.setBackgroundColor(BG);
        TextView menu = text("☰", 32, NAVY_DARK, true);
        menu.setGravity(Gravity.CENTER);
        menu.setOnClickListener(v -> showProfileSettingsScreen());
        bar.addView(menu, new LinearLayout.LayoutParams(dp(58), dp(58)));
        LinearLayout title = row();
        title.setGravity(Gravity.CENTER);
        title.addView(text("Taxi", 34, NAVY_DARK, true));
        title.addView(text("Link", 34, YELLOW, true));
        bar.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView status = text("● Conectado", 14, NAVY_DARK, true);
        status.setTextColor(Color.rgb(12, 190, 70));
        bar.addView(status, new LinearLayout.LayoutParams(dp(108), ViewGroup.LayoutParams.WRAP_CONTENT));
        return bar;
    }

    private LinearLayout ownerConnectionCard() {
        LinearLayout card = column();
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        card.setBackground(ownerLightRound(24));
        card.setElevation(dp(5));
        LinearLayout row = row();
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout copy = column();
        copy.addView(text(session.getCompany().name, 18, NAVY_DARK, true));
        copy.addView(text("Central: " + session.getCentralNumber(), 17, Color.rgb(255, 147, 0), true), wrapMT(4));
        copy.addView(text("Conectado al servidor central TaxiLink", 15, Color.rgb(12, 190, 70), true), wrapMT(8));
        copy.addView(text("Los conductores pueden conectarse desde cualquier red con Internet.", 14, NAVY_DARK, false), wrapMT(4));
        row.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView cloud = text("☁✓", 28, Color.WHITE, true);
        cloud.setGravity(Gravity.CENTER);
        cloud.setBackground(round(Color.rgb(53, 198, 36), 42, 0, Color.rgb(53, 198, 36)));
        row.addView(cloud, new LinearLayout.LayoutParams(dp(84), dp(84)));
        card.addView(row);
        return card;
    }

    private void addOwnerDashboardGrid(LinearLayout grid) {
        String[] icons = {"👤", "🎧", "📍", "📋", "📅", "📣", "🛡", "👥", "⚙"};
        String[] titles = {"Mi flota", "Walkie", "Mapa", "Servicios", "Reservas", "Google Ads", "Solicitudes", "Usuarios", "Ajustes"};
        String[] subtitles = {"Ver vehículos", "Comunicarse", "Ver flota", "Historial", "Calendario", "Clicks/llamadas", "Accesos", "Gestionar", "Configuración"};
        int[] colors = {Color.rgb(36, 170, 255), Color.rgb(27, 213, 109), YELLOW, Color.rgb(255, 148, 18), Color.rgb(181, 85, 255), BLUE, Color.rgb(23, 214, 180), Color.rgb(255, 68, 102), Color.rgb(154, 164, 178)};
        Runnable[] actions = {
                () -> showTaxiListScreen(),
                () -> toast("Mantén pulsado el botón verde para hablar"),
                () -> showMapScreen(),
                () -> showServiceHistoryScreen(),
                () -> showCalendarScreen(),
                () -> showGoogleAdsScreen(),
                () -> showPendingRequestsDialog(),
                () -> toast("Gestión local lista para ampliar"),
                () -> showProfileSettingsScreen()
        };
        int columns = ownerDashboardColumns();
        LinearLayout currentRow = null;
        for (int i = 0; i < titles.length; i++) {
            if (i % columns == 0) {
                currentRow = row();
                grid.addView(currentRow, i == 0 ? new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT) : matchWMT(8));
            }
            boolean lastColumn = i % columns == columns - 1;
            currentRow.addView(ownerDashboardAction(icons[i], titles[i], subtitles[i], actions[i], colors[i]), ownerGridLp(lastColumn));
        }
    }

    private LinearLayout ownerDashboardAction(String icon, String title, String subtitle, Runnable action, int color) {
        LinearLayout box = column();
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(5), dp(10), dp(5), dp(8));
        box.setBackground(ownerLightRound(20));
        box.setElevation(dp(4));
        box.setOnClickListener(v -> action.run());
        TextView i = text(icon, ownerDashboardIconSp(), color, true);
        i.setGravity(Gravity.CENTER);
        box.addView(i);
        TextView t = text(title, ownerDashboardTitleSp(), NAVY_DARK, true);
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(false);
        t.setMaxLines(1);
        box.addView(t, wrapMT(6));
        TextView s = text(subtitle, ownerDashboardSubtitleSp(), NAVY_DARK, false);
        s.setGravity(Gravity.CENTER);
        s.setMaxLines(1);
        box.addView(s, wrapMT(2));
        return box;
    }

    private LinearLayout fleetStatusCard() {
        int online = 0;
        int occupied = 0;
        int offline = 0;
        for (Taxi taxi : repository.getTaxis()) {
            if (taxi.online) {
                online++;
                if (taxi.occupied) occupied++;
            } else {
                offline++;
            }
        }
        LinearLayout card = column();
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        card.setBackground(ownerLightRound(24));
        card.setElevation(dp(5));
        LinearLayout title = row();
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.addView(text("🚕", 24, Color.rgb(12, 190, 70), true), new LinearLayout.LayoutParams(dp(42), ViewGroup.LayoutParams.WRAP_CONTENT));
        title.addView(text("Estado de la flota", 21, NAVY_DARK, true));
        card.addView(title);
        LinearLayout stats = row();
        stats.setGravity(Gravity.CENTER);
        stats.addView(fleetMetric(String.valueOf(Math.max(0, online - occupied)), "En servicio", Color.rgb(31, 234, 119)), ownerMetricLp(false));
        stats.addView(fleetMetric(String.valueOf(occupied), "En pausa", YELLOW), ownerMetricLp(false));
        stats.addView(fleetMetric(String.valueOf(offline), "Sin conexión", Color.rgb(255, 55, 65)), ownerMetricLp(true));
        card.addView(stats, wrapMT(14));
        return card;
    }

    private LinearLayout fleetMetric(String value, String label, int color) {
        LinearLayout box = column();
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(8), dp(10), dp(8), dp(10));
        box.setBackground(round(Color.WHITE, 14, 1, Color.rgb(229, 233, 240)));
        box.setElevation(dp(2));
        box.addView(text(value, 34, color, true));
        TextView l = text(label, 13, NAVY_DARK, false);
        l.setGravity(Gravity.CENTER);
        box.addView(l);
        return box;
    }

    private LinearLayout ownerBigWalkieButton() {
        LinearLayout box = row();
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(dp(28), dp(18), dp(22), dp(18));
        box.setBackground(navGradient());
        View.OnTouchListener pushToTalk = (v, e) -> { if (e.getAction() == MotionEvent.ACTION_DOWN) { updateWalkieState(true); return true; } if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) { updateWalkieState(false); return true; } return true; };
        box.setOnTouchListener(pushToTalk);
        micButton = button("🎙", Color.TRANSPARENT, Color.WHITE);
        micButton.setTextSize(36);
        micButton.setElevation(0);
        micButton.setBackground(round(Color.TRANSPARENT, 1, 0, Color.TRANSPARENT));
        micButton.setOnTouchListener(pushToTalk);
        box.addView(micButton, new LinearLayout.LayoutParams(dp(72), dp(78)));
        LinearLayout texts = column();
        texts.setPadding(dp(14), 0, 0, 0);
        texts.addView(text("PULSAR PARA HABLAR", 22, Color.WHITE, true));
        walkieLabel = text("Mantén pulsado mientras hablas", 15, Color.rgb(220, 255, 235), false);
        texts.addView(walkieLabel, wrapMT(4));
        box.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        return box;
    }

    private LinearLayout ownerCompactAction(String icon, String title, String desc, Runnable action, int color) {
        LinearLayout row = row();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(18), dp(14), dp(16), dp(14));
        row.setBackground(ownerLightRound(20));
        row.setElevation(dp(4));
        row.setOnClickListener(v -> action.run());
        row.addView(text(icon, 30, color, true), new LinearLayout.LayoutParams(dp(56), ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout copy = column();
        copy.addView(text(title, 18, NAVY_DARK, true));
        copy.addView(text(desc, 14, NAVY_DARK, false));
        row.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row.addView(text("›", 34, Color.rgb(148, 163, 184), false));
        return row;
    }

    private LinearLayout ownerBottomNav() {
        LinearLayout nav = row();
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(8), dp(8), dp(8), dp(8));
        nav.setBackground(round(Color.WHITE, 0, 1, Color.rgb(229, 233, 240)));
        nav.setElevation(dp(8));
        nav.addView(ownerNavItem("🏠", "Inicio", YELLOW, () -> showOwnerPanel()), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
        nav.addView(ownerNavItem("🚕", "Flota", Color.rgb(148, 163, 184), () -> showTaxiListScreen()), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
        nav.addView(ownerNavItem("📣", "Ads", Color.rgb(148, 163, 184), () -> showGoogleAdsScreen()), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
        nav.addView(ownerNavItem("⚙", "Ajustes", Color.rgb(148, 163, 184), () -> showProfileSettingsScreen()), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
        return nav;
    }

    private LinearLayout ownerNavItem(String icon, String label, int color, Runnable action) {
        LinearLayout item = column();
        item.setGravity(Gravity.CENTER);
        item.setOnClickListener(v -> action.run());
        TextView i = text(icon, 24, color, true);
        i.setGravity(Gravity.CENTER);
        item.addView(i);
        TextView l = text(label, 12, color, false);
        l.setGravity(Gravity.CENTER);
        item.addView(l);
        return item;
    }

    private android.graphics.drawable.GradientDrawable ownerDarkRound(int radius) {
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM, new int[]{Color.rgb(9, 27, 45), Color.rgb(4, 15, 29)});
        g.setCornerRadius(dp(radius));
        g.setStroke(dp(1), Color.rgb(26, 45, 64));
        return g;
    }

    private android.graphics.drawable.GradientDrawable ownerLightRound(int radius) {
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setColor(Color.WHITE);
        g.setCornerRadius(dp(radius));
        g.setStroke(dp(1), Color.rgb(224, 230, 238));
        return g;
    }

    private LinearLayout.LayoutParams ownerPageLp(int top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(18), dp(top), dp(18), 0);
        return lp;
    }

    private LinearLayout.LayoutParams ownerGridLp(boolean last) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(ownerGridCardHeightDp()), 1);
        lp.setMargins(0, 0, last ? 0 : dp(10), 0);
        return lp;
    }

    private int ownerDashboardColumns() {
        return 3;
    }

    private int ownerGridCardHeightDp() {
        int available = Math.max(300, ownerScreenWidthDp() - 36 - 20);
        int cardWidth = available / 3;
        return Math.max(118, Math.min(136, (int) (cardWidth * 1.05f)));
    }

    private int ownerDashboardIconSp() {
        return ownerScreenWidthDp() < 360 ? 30 : 34;
    }

    private int ownerDashboardTitleSp() {
        return ownerScreenWidthDp() < 360 ? 14 : 16;
    }

    private int ownerDashboardSubtitleSp() {
        return ownerScreenWidthDp() < 360 ? 11 : 13;
    }

    private int ownerScreenWidthDp() {
        int configured = getResources().getConfiguration().screenWidthDp;
        if (configured > 0) return configured;
        return (int) (getResources().getDisplayMetrics().widthPixels / getResources().getDisplayMetrics().density);
    }

    private LinearLayout.LayoutParams ownerMetricLp(boolean last) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        lp.setMargins(0, 0, last ? 0 : dp(4), 0);
        return lp;
    }

    private LinearLayout adminCountsAsTaxiRow() {
        LinearLayout row = ownerAction("🚖", "Contar administrador como taxista", "Desactivado: el admin no ocupa número de taxi", () -> showAdminTaxiNumberDialog());
        row.removeViewAt(row.getChildCount() - 1);
        Switch sw = new Switch(this);
        sw.setChecked(session.isAdminCountsAsTaxi());
        sw.setOnCheckedChangeListener((buttonView, isChecked) -> {
            int previousTaxiNumber = safeTaxiNumber();
            if (isChecked && (session.getAdminTaxiNumber() == null || session.getAdminTaxiNumber().trim().isEmpty())) {
                buttonView.setChecked(false);
                showAdminTaxiNumberDialog();
                return;
            }
            session.setAdminCountsAsTaxi(isChecked);
            selectedTaxi = repository.getTaxi(safeTaxiNumber());
            if (isChecked) {
                toast("Administrador cuenta como Taxi " + safeTaxiNumber());
                startRealGpsUpdates();
            } else {
                api.setTaxiOccupied(previousTaxiNumber, false, (ok, error) -> { });
                toast("Administrador no cuenta como taxi");
            }
        });
        row.addView(sw);
        return row;
    }

    public void showGoogleAdsScreen() {
        LinearLayout root = baseWithHeader("Google Ads", "←", true, () -> showOwnerPanel());
        root.addView(subtitle("Cuenta y resultados del anuncio de taxi"));

        LinearLayout accountCard = card();
        accountCard.addView(text("Cuenta de Google Ads", 20, TEXT, true));
        EditText account = field("Cuenta Google Ads", "Ej. 123-456-7890", false);
        account.setText(session.getGoogleAdsAccount());
        accountCard.addView(account, matchHMT(58, 12));
        Button save = button("Guardar cuenta", TEAL, Color.WHITE);
        save.setOnClickListener(v -> {
            session.setGoogleAdsAccount(account.getText().toString());
            toast("Cuenta de Google Ads guardada");
            showGoogleAdsScreen();
        });
        accountCard.addView(save, matchHMT(58, 12));
        Button connect = button("Conectar cuenta Google", BLUE, Color.WHITE);
        connect.setOnClickListener(v -> openGoogleAdsConnect(account.getText().toString()));
        accountCard.addView(connect, matchHMT(58, 10));
        Button refresh = button("Actualizar datos reales", NAVY, Color.WHITE);
        refresh.setOnClickListener(v -> fetchGoogleAdsRealStats(account.getText().toString()));
        accountCard.addView(refresh, matchHMT(58, 10));
        TextView note = text("Usa el ID de cliente Google Ads (ej. 123-456-7890). La conexión se abre con tu cuenta Google en el navegador.", 13, SECONDARY, false);
        accountCard.addView(note, wrapMT(12));
        root.addView(accountCard, cardLp());

        LinearLayout metrics = row();
        metrics.setPadding(dp(18), 0, dp(18), 0);
        metrics.addView(adsMetricCard("👆", String.valueOf(session.getGoogleAdsClicks()), "Clicks anuncio", BLUE), ownerMetricLp(false));
        metrics.addView(adsMetricCard("☎", String.valueOf(session.getGoogleAdsCalls()), "Llamadas", Color.rgb(31, 184, 78)), ownerMetricLp(true));
        root.addView(metrics, matchWMT(0));

        LinearLayout actions = card();
        actions.addView(text("Registrar actividad", 20, TEXT, true));
        Button click = button("+ Click en anuncio", BLUE, Color.WHITE);
        click.setOnClickListener(v -> { session.addGoogleAdsClick(); showGoogleAdsScreen(); });
        actions.addView(click, matchHMT(60, 14));
        Button call = button("+ Llamada desde anuncio", TEAL, Color.WHITE);
        call.setOnClickListener(v -> { session.addGoogleAdsCall(); showGoogleAdsScreen(); });
        actions.addView(call, matchHMT(60, 10));
        Button reset = button("Reiniciar contadores", Color.WHITE, DANGER);
        reset.setBackground(round(Color.WHITE, 16, 1, DANGER));
        reset.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Reiniciar métricas")
                .setMessage("Se pondrán a cero los clicks y llamadas registrados en TaxiLink.")
                .setPositiveButton("Reiniciar", (d, w) -> { session.resetGoogleAdsStats(); showGoogleAdsScreen(); })
                .setNegativeButton("Cancelar", null)
                .show());
        actions.addView(reset, matchHMT(56, 10));
        root.addView(actions, cardLp());

        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(BG);
        frame.addView(scroll(root), match());
        frame.addView(ownerBottomNav(), new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(82), Gravity.BOTTOM));
        setContentView(frame);
    }

    private void openGoogleAdsConnect(String account) {
        session.setGoogleAdsAccount(account);
        try {
            String url = ApiConfig.SERVER_URL + "/google-ads/connect?company=" + URLEncoder.encode(session.getCentralNumber(), "UTF-8");
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            toast("No se pudo abrir Google: " + e.getMessage());
        }
    }

    private void fetchGoogleAdsRealStats(String account) {
        String customerId = account == null ? "" : account.replaceAll("[^0-9]", "");
        if (customerId.isEmpty()) { toast("Indica el ID de cliente Google Ads"); return; }
        session.setGoogleAdsAccount(account);
        new Thread(() -> {
            try {
                String url = ApiConfig.SERVER_URL + "/google-ads/summary?company=" + URLEncoder.encode(session.getCentralNumber(), "UTF-8") + "&customerId=" + URLEncoder.encode(customerId, "UTF-8");
                JSONObject json = readJson(url);
                int clicks = json.optInt("clicks", 0);
                int calls = json.optInt("calls", 0);
                session.setGoogleAdsStats(clicks, calls);
                runOnUiThread(() -> { toast("Datos Google Ads actualizados"); showGoogleAdsScreen(); });
            } catch (Exception e) {
                runOnUiThread(() -> showError("No se pudo leer Google Ads", e));
            }
        }).start();
    }

    private LinearLayout adsMetricCard(String icon, String value, String label, int color) {
        LinearLayout card = column();
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(12), dp(18), dp(12), dp(18));
        card.setBackground(ownerLightRound(22));
        card.setElevation(dp(5));
        card.addView(text(icon, 30, color, true));
        TextView number = text(value, 42, color, true);
        number.setGravity(Gravity.CENTER);
        card.addView(number, wrapMT(4));
        TextView caption = text(label, 15, NAVY_DARK, true);
        caption.setGravity(Gravity.CENTER);
        card.addView(caption, wrapMT(2));
        return card;
    }

    private void showAdminTaxiNumberDialog() {
        EditText input = field("Número de taxi admin", "Ej. 99", false);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setText(session.getAdminTaxiNumber());
        new AlertDialog.Builder(this)
                .setTitle("Taxi del administrador")
                .setMessage("Solo se contará como taxista mientras esta opción esté activada.")
                .setView(input)
                .setPositiveButton("Activar", (d, w) -> {
                    String value = input.getText().toString().trim();
                    if (value.isEmpty()) { toast("Pon un número de taxi"); return; }
                    session.setAdminTaxiNumber(value);
                    session.setAdminCountsAsTaxi(true);
                    selectedTaxi = repository.getTaxi(safeTaxiNumber());
                    startRealGpsUpdates();
                    showOwnerPanel();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    public void showTaxiListScreen() {
        LinearLayout root = baseWithHeader("Lista de taxis", "☰", false, null);
        root.addView(subtitle(session.getCompany().name));
        if (!"Propietario".equals(session.getRole())) root.addView(subtitle("Tu número: Taxi " + safeTaxiNumber() + " · En tu dispositivo se muestra la flecha GPS real"));
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
                TextView empty = text("Aún no hay trabajadores aprobados. Aprueba solicitudes desde el panel propietario.", 15, SECONDARY, false);
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
        startServiceAlertPolling();
        showPendingServiceBubble();
    }

    private void loadChatMessages() {
        api.getMessages((messages, error) -> runOnUiThread(() -> {
            if (chatList == null) return;
            if (error != null) { toast("Chat sin conexión: " + error.getMessage()); return; }
            chatList.removeAllViews();
            scheduleReservationReminders(messages);
            List<ChatMessage> visible = new ArrayList<>();
            for (ChatMessage message : messages) if (!("service".equals(message.type) && "done".equals(message.serviceStatus))) visible.add(message);
            if (visible.isEmpty()) {
                inspectPendingServices(visible, false);
                TextView empty = text("No hay mensajes todavía. Pulsa + para añadir un servicio o escribe un mensaje.", 15, SECONDARY, false);
                empty.setGravity(Gravity.CENTER);
                empty.setPadding(dp(14), dp(28), dp(14), dp(28));
                chatList.addView(empty);
                return;
            }
            inspectPendingServices(visible, false);
            for (ChatMessage message : visible) chatList.addView(messageBubble(message));
        }));
    }

    private View messageBubble(ChatMessage message) {
        LinearLayout bubble = column();
        bubble.setPadding(dp(14), dp(12), dp(14), dp(12));
        bubble.setBackground(round("service".equals(message.type) ? Color.rgb(255, 250, 225) : Color.WHITE, 18, 1, "service".equals(message.type) ? YELLOW : Color.rgb(230, 232, 235)));
        bubble.setElevation(dp(2));
        bubble.addView(text(message.sender + " · " + message.role, 13, SECONDARY, true));
        if ("service".equals(message.type)) {
            String status = message.serviceStatus == null || message.serviceStatus.equals("null") ? "pending" : message.serviceStatus;
            if ("cancelled".equals(status)) {
                bubble.setBackground(round(Color.rgb(255, 235, 238), 18, 1, DANGER));
                bubble.addView(text("El servicio se ha cancelado", 18, DANGER, true), wrapMT(6));
                return finishBubble(bubble);
            }
            bubble.addView(text("🚕 Nuevo servicio", 18, NAVY, true), wrapMT(6));
            bubble.addView(text("Servicio: " + message.serviceType, 14, TEXT, false), wrapMT(4));
            bubble.addView(text("Tarifa: " + message.tariff, 14, TEXT, false), wrapMT(3));
            bubble.addView(text("Recoger: " + message.pickup, 14, TEXT, false), wrapMT(3));
            bubble.addView(text("Dejar: " + message.destination, 14, TEXT, false), wrapMT(3));
            if (message.phone != null && !message.phone.equals("null") && !message.phone.isEmpty()) bubble.addView(text("Teléfono: " + message.phone, 14, TEXT, false), wrapMT(3));
            if (message.description != null && !message.description.equals("null") && !message.description.isEmpty()) bubble.addView(text("Descripción: " + message.description, 14, TEXT, false), wrapMT(3));
            if ("reserved".equals(message.serviceStatus)) bubble.addView(text("Reserva: " + message.reservationDate + " · " + message.reservationTime + " · Color " + message.reservationColor, 14, NAVY, true), wrapMT(4));
            bubble.addView(text(message.fixedPrice ? "Precio cerrado" + (message.estimatedPrice == null || message.estimatedPrice.isEmpty() ? "" : ": " + message.estimatedPrice + " €") : "Precio por taxímetro", 14, TEAL, true), wrapMT(5));
            int statusColor = "accepted".equals(status) || "done".equals(status) ? TEAL : ("cancelled".equals(status) ? DANGER : SECONDARY);
            bubble.addView(text("Estado: " + serviceStatusText(status), 14, statusColor, true), wrapMT(6));
            if ("accepted".equals(status)) {
                if (message.acceptedTaxi > 0 || hasText(message.acceptedDriverName) || hasText(message.acceptedBy)) bubble.addView(text("Lo lleva: " + acceptedServiceLabel(message), 14, TEAL, true), wrapMT(4));
                bubble.setOnClickListener(v -> showAcceptedServiceScreen(message));
                Button details = button("Ver datos del servicio", NAVY, Color.WHITE);
                details.setOnClickListener(v -> showAcceptedServiceScreen(message));
                bubble.addView(details, matchHMT(48, 10));
            }
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
        return finishBubble(bubble);
    }

    private View finishBubble(View bubble) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(8), 0, 0);
        bubble.setLayoutParams(lp);
        return bubble;
    }

    private void startChatPolling() {
        if (chatPoller != null) handler.removeCallbacks(chatPoller);
        chatPoller = new Runnable() {
            @Override public void run() {
                if (chatList == null || !chatList.isAttachedToWindow()) {
                    chatList = null;
                    chatPoller = null;
                    return;
                }
                loadChatMessages();
                handler.postDelayed(this, 7000);
            }
        };
        handler.postDelayed(chatPoller, 7000);
    }

    private void startServiceAlertPolling() {
        if (serviceAlertPoller != null || "Propietario".equals(session.getRole()) || !session.isDriverApproved()) return;
        serviceAlertPoller = new Runnable() {
            @Override public void run() {
                validateActiveDriverAccess();
                api.getMessages((messages, error) -> runOnUiThread(() -> {
                    if (error == null) {
                        scheduleReservationReminders(messages);
                        inspectPendingServices(messages, true);
                    }
                }));
                handler.postDelayed(this, 3500);
            }
        };
        handler.postDelayed(serviceAlertPoller, 1200);
    }

    private void inspectPendingServices(List<ChatMessage> messages, boolean alert) {
        if ("Propietario".equals(session.getRole()) || activeService != null) {
            pendingServiceAlert = null;
            stopServiceAlertSound();
            hidePendingServiceBubble();
            return;
        }
        ChatMessage pending = null;
        for (ChatMessage m : messages) {
            String status = m.serviceStatus == null || m.serviceStatus.equals("null") ? "pending" : m.serviceStatus;
            if ("service".equals(m.type) && "pending".equals(status)) { pending = m; break; }
        }
        pendingServiceAlert = pending;
        if (pending == null) {
            stopServiceAlertSound();
            hidePendingServiceBubble();
            return;
        }
        showPendingServiceBubble();
        if (alert && !pending.id.equals(lastPromptedServiceId) && !servicePopupShowing) {
            lastPromptedServiceId = pending.id;
            playServiceAlertSound();
            showIncomingServicePopup(pending);
        }
    }

    private void showPendingServiceBubble() {
        if (pendingServiceAlert == null || servicePopupShowing || isFinishing()) return;
        hidePendingServiceBubble();
        TextView bubble = text("1", 14, Color.WHITE, true);
        bubble.setGravity(Gravity.CENTER);
        bubble.setBackground(round(DANGER, 15, 0, DANGER));
        bubble.setElevation(dp(10));
        bubble.setOnClickListener(v -> showIncomingServicePopup(pendingServiceAlert));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(30), dp(30), Gravity.RIGHT | Gravity.TOP);
        lp.setMargins(0, dp(12), dp(12), 0);
        serviceBubbleView = bubble;
        addContentView(bubble, lp);
    }

    private void hidePendingServiceBubble() {
        if (serviceBubbleView != null && serviceBubbleView.getParent() instanceof ViewGroup) ((ViewGroup) serviceBubbleView.getParent()).removeView(serviceBubbleView);
        serviceBubbleView = null;
    }

    private void playServiceAlertSound() {
        try {
            if (serviceAlertPlayer == null) {
                serviceAlertPlayer = MediaPlayer.create(this, R.raw.sonido_mensaje);
                if (serviceAlertPlayer != null) serviceAlertPlayer.setLooping(true);
            }
            if (serviceAlertPlayer != null && !serviceAlertPlayer.isPlaying()) serviceAlertPlayer.start();
        } catch (Exception ignored) { }
        try {
            if (tts != null) tts.speak("Servicio, servicio", TextToSpeech.QUEUE_ADD, null, "service_alert");
        } catch (Exception ignored) { }
    }

    private void stopServiceAlertSound() {
        try {
            if (serviceAlertPlayer != null) {
                serviceAlertPlayer.stop();
                serviceAlertPlayer.release();
                serviceAlertPlayer = null;
            }
            if (tts != null) tts.stop();
        } catch (Exception ignored) { }
    }

    private void showIncomingServicePopup(ChatMessage message) {
        if (message == null || servicePopupShowing) return;
        servicePopupShowing = true;
        hidePendingServiceBubble();
        Dialog dialog = new Dialog(this);
        dialog.setOnDismissListener(d -> { servicePopupShowing = false; showPendingServiceBubble(); });
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(16, 24, 39));
        LinearLayout card = column();
        card.setPadding(dp(22), dp(24), dp(22), dp(22));
        card.setBackground(round(Color.WHITE, 26, 0, Color.WHITE));
        card.addView(text("SERVICIO", 30, DANGER, true));
        card.addView(text("Nuevo servicio entrante", 18, NAVY, true), wrapMT(4));
        addServiceDetail(card, "Recogida", message.pickup);
        addServiceDetail(card, "Dejada", message.destination);
        addServiceDetail(card, "Descripción cliente", message.description);
        addServiceDetail(card, "Teléfono", message.phone);
        addServiceDetail(card, "Kilómetros", serviceKilometersText(message));
        LinearLayout actions = row();
        actions.setGravity(Gravity.CENTER_VERTICAL);
        Button reject = button("Rechazar", DANGER, Color.WHITE);
        reject.setOnClickListener(v -> { stopServiceAlertSound(); pendingServiceAlert = null; hidePendingServiceBubble(); dialog.dismiss(); updateServiceStatus(message, "cancelled"); });
        Button accept = button("Aceptar", TEAL, Color.WHITE);
        accept.setOnClickListener(v -> { stopServiceAlertSound(); pendingServiceAlert = null; hidePendingServiceBubble(); dialog.dismiss(); updateServiceStatus(message, "accepted"); });
        LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0, dp(58), 1); left.setMargins(0, dp(20), dp(7), 0);
        LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(0, dp(58), 1); right.setMargins(dp(7), dp(20), 0, 0);
        actions.addView(reject, left); actions.addView(accept, right);
        card.addView(actions);
        FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        cardLp.setMargins(dp(16), 0, dp(16), 0);
        root.addView(card, cardLp);
        dialog.setContentView(root);
        if (dialog.getWindow() != null) dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        dialog.show();
        if (dialog.getWindow() != null) dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private String serviceStatusText(String status) {
        if ("accepted".equals(status)) return "Aceptado";
        if ("done".equals(status)) return "Hecho";
        if ("cancelled".equals(status)) return "Cancelado";
        if ("reserved".equals(status)) return "Reserva";
        return "Pendiente";
    }

    private void updateServiceStatus(ChatMessage message, String status) {
        api.updateServiceStatus(message.id, status, (ok, error) -> runOnUiThread(() -> {
            if (error != null) toast("No se pudo actualizar servicio: " + error.getMessage());
            else if ("accepted".equals(status) && !Boolean.TRUE.equals(ok)) {
                stopServiceAlertSound();
                pendingServiceAlert = null;
                hidePendingServiceBubble();
                toast("Servicio ya aceptado por otro taxi");
                loadChatMessages();
            }
            else {
                message.serviceStatus = status;
                stopServiceAlertSound();
                if (message.id != null && message.id.equals(lastPromptedServiceId)) pendingServiceAlert = null;
                hidePendingServiceBubble();
                if ("accepted".equals(status)) {
                    activeService = message;
                    message.acceptedTaxi = safeTaxiNumber();
                    message.acceptedDriverName = "Propietario".equals(session.getRole()) ? session.getCompany().ownerName : session.getDriverName();
                    message.acceptedTaxiName = message.acceptedTaxi > 0 ? "Taxi " + message.acceptedTaxi : message.acceptedDriverName;
                    completingActiveService = false;
                    routingToDestination = false;
                    activeRoutePoints.clear();
                    activeManeuvers.clear();
                    clearRouteInsight();
                    lastRouteFetchAt = 0;
                    api.setTaxiOccupied(safeTaxiNumber(), true, (ok2, error2) -> { });
                    session.startCarTaximeterFromMobileService(message.id);
                    showAcceptedServiceDistance(message);
                    showAcceptedServiceScreen(message);
                } else {
                    if (activeService != null && message.id != null && message.id.equals(activeService.id)) {
                        api.setTaxiOccupied(safeTaxiNumber(), false, (ok2, error2) -> { });
                        session.stopCarTaximeterFromMobileService();
                        activeService = null;
                        completingActiveService = false;
                    }
                    loadChatMessages();
                }
            }
        }));
    }

    private void confirmAbandonActiveService() {
        if (activeService == null) { showMapScreen(); return; }
        new AlertDialog.Builder(this)
                .setTitle("Abandonar servicio")
                .setMessage("¿Quieres abandonar este servicio? El taxi dejará de estar ocupado.")
                .setPositiveButton("Abandonar", (d, w) -> cancelActiveService())
                .setNegativeButton("Seguir", null)
                .show();
    }

    private void cancelActiveService() {
        if (activeService == null) return;
        ChatMessage service = activeService;
        api.updateServiceStatus(service.id, "cancelled", (ok, error) -> runOnUiThread(() -> {
            api.setTaxiOccupied(safeTaxiNumber(), false, (ok2, error2) -> { });
            session.stopCarTaximeterFromMobileService();
            if (selectedTaxi != null) selectedTaxi.occupied = false;
            activeService = null;
            routingToDestination = false;
            completingActiveService = false;
            activeRoutePoints.clear();
            activeManeuvers.clear();
            clearRouteInsight();
            toast(error == null ? "Servicio cancelado. Taxi libre." : "Servicio abandonado localmente. Revisa conexión.");
            showMapScreen();
        }));
    }

    private void showAcceptedServiceScreen(ChatMessage message) {
        activeService = message;
        LinearLayout root = baseWithHeader("Servicio aceptado", "←", true, () -> showChatScreen());
        LinearLayout card = card();
        card.addView(text("🚕 Datos del servicio", 22, NAVY, true));
        card.addView(text("Estado: " + serviceStatusText(message.serviceStatus), 15, TEAL, true), wrapMT(8));
        addServiceDetail(card, "Servicio", message.serviceType);
        addServiceDetail(card, "Tarifa", message.tariff);
        addServiceDetail(card, "Recogida", message.pickup);
        addServiceDetail(card, "Destino", message.destination);
        if (message.phone != null && !message.phone.equals("null") && !message.phone.trim().isEmpty()) addServiceDetail(card, "Teléfono", message.phone);
        if (message.description != null && !message.description.equals("null") && !message.description.trim().isEmpty()) addServiceDetail(card, "Descripción", message.description);
        if ("reserved".equals(message.serviceStatus)) addServiceDetail(card, "Reserva", message.reservationDate + " · " + message.reservationTime + " · Color " + message.reservationColor);
        if (message.reservationSupplement) addServiceDetail(card, "Suplemento reserva", "4.60 €");
        if (hasText(message.supplements)) addServiceDetail(card, "Suplementos", message.supplements);
        if (message.acceptedTaxi > 0 || hasText(message.acceptedDriverName) || hasText(message.acceptedBy)) addServiceDetail(card, "Taxista asignado", acceptedServiceLabel(message));
        addServiceDetail(card, "Precio", message.fixedPrice ? ("Cerrado" + (message.estimatedPrice == null || message.estimatedPrice.isEmpty() ? "" : ": " + message.estimatedPrice + " €")) : "Por taxímetro");
        addServiceDetail(card, "Kilómetros", serviceKilometersText(message));
        if (message.pickupLat != 0 || message.pickupLng != 0) addServiceDetail(card, "GPS recogida", String.format(Locale.getDefault(), "%.5f, %.5f", message.pickupLat, message.pickupLng));
        if (message.destinationLat != 0 || message.destinationLng != 0) addServiceDetail(card, "GPS destino", String.format(Locale.getDefault(), "%.5f, %.5f", message.destinationLat, message.destinationLng));

        Button map = button("Ir para allá", TEAL, Color.WHITE);
        map.setOnClickListener(v -> showMapScreen());
        card.addView(map, matchHMT(56, 18));
        Button cancelService = button("Cancelar servicio", DANGER, Color.WHITE);
        cancelService.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Cancelar servicio")
                .setMessage("¿Seguro que quieres cancelar este servicio? El taxi quedará libre.")
                .setPositiveButton("Cancelar servicio", (d, w) -> cancelActiveService())
                .setNegativeButton("Volver", null)
                .show());
        card.addView(cancelService, matchHMT(56, 10));
        if (message.phone != null && !message.phone.equals("null") && !message.phone.trim().isEmpty()) {
            Button call = button("Llamar cliente", NAVY, Color.WHITE);
            call.setOnClickListener(v -> {
                session.addGoogleAdsCall();
                startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + message.phone.trim())));
            });
            card.addView(call, matchHMT(56, 10));
        }
        Button chat = button("Volver al chat", Color.WHITE, NAVY);
        chat.setBackground(round(Color.WHITE, 18, 1, NAVY));
        chat.setOnClickListener(v -> showChatScreen());
        card.addView(chat, matchHMT(56, 10));
        root.addView(card, cardLp());
        setContentView(scroll(root));
        showPendingServiceBubble();
    }

    private String serviceKilometersText(ChatMessage message) {
        List<String> lines = new ArrayList<>();
        if (selectedTaxi != null && selectedTaxi.latitude != 0 && selectedTaxi.longitude != 0 && message.pickupLat != 0 && message.pickupLng != 0) {
            float[] toPickup = new float[1];
            Location.distanceBetween(selectedTaxi.latitude, selectedTaxi.longitude, message.pickupLat, message.pickupLng, toPickup);
            lines.add("hasta recogida " + String.format(Locale.getDefault(), "%.1f km", toPickup[0] / 1000f));
        }
        if (message.pickupLat != 0 && message.pickupLng != 0 && message.destinationLat != 0 && message.destinationLng != 0) {
            float[] trip = new float[1];
            Location.distanceBetween(message.pickupLat, message.pickupLng, message.destinationLat, message.destinationLng, trip);
            lines.add("trayecto aprox. " + String.format(Locale.getDefault(), "%.1f km", trip[0] / 1000f));
        }
        if (lines.isEmpty()) return "Pendiente de GPS";
        StringBuilder sb = new StringBuilder(lines.get(0));
        for (int i = 1; i < lines.size(); i++) sb.append(" · ").append(lines.get(i));
        return sb.toString();
    }

    private void addServiceDetail(LinearLayout card, String label, String value) {
        if (value == null || value.equals("null") || value.trim().isEmpty()) value = "--";
        card.addView(text(label, 13, SECONDARY, true), wrapMT(12));
        card.addView(text(value, 17, TEXT, false), wrapMT(3));
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
        Spinner tariff = spinner(new String[]{"T-1 urbana diurna", "T-2 urbana nocturna/festivos", "T-3 urbana precio cerrado", "T-6 urbana diurna", "T-7 urbana nocturna/festivos"});
        Spinner color = spinner(reservationColors());
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
        CheckBox reservationSupplement = checkbox("Añadir suplemento de reserva (4.60 €)");
        reservationSupplement.setChecked(true);
        card.addView(text("Servicio", 15, NAVY, true)); card.addView(serviceType, matchHMT(58, 8));
        card.addView(text("Tarifa", 15, NAVY, true), wrapMT(12)); card.addView(tariff, matchHMT(58, 8));
        card.addView(text("Fecha y hora", 15, NAVY, true), wrapMT(12)); card.addView(date, matchHMT(58, 8)); card.addView(time, matchHMT(58, 8));
        card.addView(text("Color calendario", 15, NAVY, true), wrapMT(12)); card.addView(color, matchHMT(58, 8));
        card.addView(text("Recogida", 15, NAVY, true), wrapMT(12)); card.addView(pickupStreet, matchHMT(58, 8)); card.addView(pickupCity, matchHMT(58, 8));
        card.addView(text("Destino", 15, NAVY, true), wrapMT(12)); card.addView(destStreet, matchHMT(58, 8)); card.addView(destCity, matchHMT(58, 8));
        card.addView(reservationSupplement, mt(12));
        card.addView(phone, matchHMT(58, 12)); card.addView(desc, matchHMT(86, 8));
        Button send = button("Guardar reserva", TEAL, Color.WHITE);
        send.setOnClickListener(v -> {
            if (empty(date) || empty(time) || empty(pickupStreet) || empty(pickupCity) || empty(destStreet) || empty(destCity)) { toast("Completa fecha, hora, recogida y destino"); return; }
            String pickup = smartAddress(pickupStreet.getText().toString(), pickupCity.getText().toString());
            String dest = smartAddress(destStreet.getText().toString(), destCity.getText().toString());
            double[] p = geocodeAddress(pickupStreet.getText().toString(), pickupCity.getText().toString()); double[] d = geocodeAddress(destStreet.getText().toString(), destCity.getText().toString());
            String supplements = reservationSupplement.isChecked() ? "Suplemento reserva: 4.60 €" : "";
            api.sendReservation(serviceType.getSelectedItem().toString(), tariff.getSelectedItem().toString(), pickup, dest, date.getText().toString().trim(), time.getText().toString().trim(), color.getSelectedItem().toString(), phone.getText().toString().trim(), desc.getText().toString().trim(), reservationSupplement.isChecked(), supplements, p[0], p[1], d[0], d[1], (ok, error) -> runOnUiThread(() -> {
                if (error != null) toast("No se pudo guardar reserva: " + error.getMessage()); else { toast("Reserva guardada. Avisará 1 hora antes."); showChatScreen(); }
            }));
        });
        card.addView(send, matchHMT(60, 18));
        root.addView(card, cardLp());
        setContentView(scroll(root));
    }

    public void showCalendarScreen() {
        int generation = ++calendarRenderGeneration;
        LinearLayout root = baseWithHeader("Calendar", "←", true, () -> showChatScreen());
        LinearLayout hero = column();
        hero.setPadding(dp(20), dp(18), dp(20), dp(16));
        hero.setBackground(round(Color.WHITE, 28, 1, Color.rgb(232, 237, 246)));
        hero.setElevation(dp(6));
        LinearLayout titleRow = row();
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView previous = calendarArrow("‹");
        previous.setOnClickListener(v -> moveCalendarCursor(-1));
        titleRow.addView(previous, new LinearLayout.LayoutParams(dp(46), dp(46)));
        TextView title = text(calendarPeriodTitle(), 24, TEXT, true);
        title.setGravity(Gravity.CENTER);
        titleRow.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView next = calendarArrow("›");
        next.setOnClickListener(v -> moveCalendarCursor(1));
        titleRow.addView(next, new LinearLayout.LayoutParams(dp(46), dp(46)));
        hero.addView(titleRow);
        hero.addView(calendarModeTabs(), wrapMT(14));
        Button today = button("Hoy", Color.rgb(232, 240, 254), BLUE);
        today.setOnClickListener(v -> {
            calendarCursor.setTimeInMillis(System.currentTimeMillis());
            showCalendarScreen();
        });
        hero.addView(today, matchHMT(44, 12));
        root.addView(hero, cardLp());
        LinearLayout content = column();
        content.setPadding(dp(16), 0, dp(16), dp(24));
        root.addView(content);
        setContentView(scroll(root));
        api.getMessages((messages, error) -> runOnUiThread(() -> {
            if (generation != calendarRenderGeneration || isFinishing()) return;
            content.removeAllViews();
            if (error != null) { toast("No se pudo cargar calendar: " + error.getMessage()); return; }
            scheduleReservationReminders(messages);
            List<ChatMessage> reservations = new ArrayList<>();
            for (ChatMessage m : messages) {
                if (!"service".equals(m.type) || "cancelled".equals(m.serviceStatus) || calendarFromReservation(m.reservationDate) == null) continue;
                reservations.add(m);
            }
            Collections.sort(reservations, (a, b) -> Long.compare(reservationSortTime(a), reservationSortTime(b)));
            renderCalendarPeriod(content, reservations);
        }));
    }

    public void showServiceHistoryScreen() {
        LinearLayout root = baseWithHeader("Historial servicios", "←", true, () -> showOwnerPanel());
        root.addView(subtitle("Servicios hechos, cancelados y aceptados · " + session.getCompany().name));
        LinearLayout list = column();
        list.setPadding(dp(16), dp(12), dp(16), dp(24));
        root.addView(list);
        setContentView(scroll(root));
        api.getMessages((messages, error) -> runOnUiThread(() -> {
            list.removeAllViews();
            if (error != null) { toast("No se pudo cargar historial: " + error.getMessage()); return; }
            boolean any = false;
            for (ChatMessage m : messages) {
                if (!"service".equals(m.type)) continue;
                String status = m.serviceStatus == null || m.serviceStatus.equals("null") ? "pending" : m.serviceStatus;
                if ("pending".equals(status)) continue;
                any = true;
                LinearLayout card = card();
                int color = "done".equals(status) ? TEAL : ("cancelled".equals(status) ? DANGER : NAVY);
                card.setBackground(round(Color.WHITE, 18, 3, color));
                card.addView(text(serviceStatusText(status), 18, color, true));
                card.addView(text(m.pickup + " → " + m.destination, 14, TEXT, false), wrapMT(6));
                card.addView(text("Servicio: " + safeText(m.serviceType) + " · Tarifa: " + safeText(m.tariff), 14, SECONDARY, false), wrapMT(4));
                if (m.acceptedTaxi > 0 || hasText(m.acceptedDriverName) || hasText(m.acceptedBy)) card.addView(text("Taxista: " + acceptedServiceLabel(m), 14, SECONDARY, false), wrapMT(4));
                if (m.reservationDate != null && !m.reservationDate.equals("null")) card.addView(text("Reserva: " + m.reservationDate + " · " + m.reservationTime, 14, SECONDARY, false), wrapMT(4));
                if (m.reservationSupplement) card.addView(text("Suplemento reserva: 4.60 €", 14, SECONDARY, false), wrapMT(4));
                if (m.description != null && !m.description.equals("null") && !m.description.trim().isEmpty()) card.addView(text("Descripción: " + m.description, 14, SECONDARY, false), wrapMT(4));
                list.addView(card, cardLp());
            }
            if (!any) {
                TextView empty = text("Aún no hay servicios en historial.", 15, SECONDARY, false);
                empty.setGravity(Gravity.CENTER);
                list.addView(empty);
            }
        }));
    }

    private String safeText(String value) {
        return value == null || value.equals("null") || value.trim().isEmpty() ? "--" : value;
    }

    private boolean hasText(String value) {
        return value != null && !value.equals("null") && !value.trim().isEmpty();
    }

    private String acceptedServiceLabel(ChatMessage message) {
        String taxiName = hasText(message.acceptedTaxiName) ? message.acceptedTaxiName : (message.acceptedTaxi > 0 ? "Taxi " + message.acceptedTaxi : "Taxi sin número");
        String driver = hasText(message.acceptedDriverName) ? message.acceptedDriverName : message.acceptedBy;
        return taxiName + (hasText(driver) ? " · " + driver : "");
    }

    private void scheduleReservationReminders(List<ChatMessage> messages) {
        if (messages == null) return;
        if (!canScheduleReservationRemindersNow(messages)) return;
        ReservationAlarmScheduler.sync(this, messages, assignedReservationColor);
    }

    private boolean canScheduleReservationRemindersNow(List<ChatMessage> messages) {
        if ("Propietario".equals(session.getRole()) && !session.isAdminCountsAsTaxi()) return false;
        long now = System.currentTimeMillis();
        if (hasText(assignedReservationColor) && now - lastReservationColorRefreshAt < 30000) return true;
        if (loadingReservationColor) return false;
        loadingReservationColor = true;
        api.getTaxis(session.getCentralNumber(), (taxis, error) -> runOnUiThread(() -> {
            loadingReservationColor = false;
            lastReservationColorRefreshAt = System.currentTimeMillis();
            if (error == null) {
                int taxiNumber = safeTaxiNumber();
                assignedReservationColor = "";
                for (Taxi taxi : taxis) {
                    if (taxi.number == taxiNumber) {
                        assignedReservationColor = taxi.reservationColor == null ? "" : taxi.reservationColor.trim();
                        session.setAssignedReservationColor(assignedReservationColor);
                        if (selectedTaxi != null) selectedTaxi.reservationColor = assignedReservationColor;
                        break;
                    }
                }
                session.setAssignedReservationColor(assignedReservationColor);
            }
            if (hasText(assignedReservationColor)) scheduleReservationReminders(messages);
        }));
        return false;
    }

    private void handleReservationReminderIntent(Intent intent) {
        if (intent == null || !intent.getBooleanExtra("reservation_reminder", false)) return;
        String title = intent.getStringExtra("reservation_reminder_title");
        String detail = intent.getStringExtra("reservation_reminder_detail");
        int minutes = intent.getIntExtra("reservation_reminder_minutes", 60);
        if (!hasText(title)) title = reservationReminderText(minutes);
        if (!hasText(detail)) detail = "Reserva TaxiLink";
        String finalTitle = title;
        String finalDetail = detail;
        handler.postDelayed(() -> showReservationReminderDialog(finalTitle, finalDetail), 350);
    }

    private void showReservationReminderDialog(String title, String detail) {
        if (isFinishing()) return;
        try {
            if (android.os.Build.VERSION.SDK_INT >= 27) {
                setShowWhenLocked(true);
                setTurnScreenOn(true);
            }
            getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON | android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } catch (Exception ignored) { }
        startReservationReminderLoop(title);
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(detail)
                .setPositiveButton("Vale", (d, w) -> stopReservationReminderLoop())
                .setOnDismissListener(d -> stopReservationReminderLoop())
                .show();
    }

    private void startReservationReminderLoop(String text) {
        stopReservationReminderLoop();
        try {
            reservationReminderPlayer = MediaPlayer.create(this, R.raw.sonido_mensaje);
            if (reservationReminderPlayer != null) {
                reservationReminderPlayer.setLooping(true);
                reservationReminderPlayer.start();
            }
        } catch (Exception ignored) { }
        reservationReminderSpeaker = new Runnable() {
            @Override public void run() {
                try { if (tts != null) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "reservation_loop"); } catch (Exception ignored) { }
                handler.postDelayed(this, 4500);
            }
        };
        reservationReminderSpeaker.run();
    }

    private void stopReservationReminderLoop() {
        if (handler != null && reservationReminderSpeaker != null) handler.removeCallbacks(reservationReminderSpeaker);
        reservationReminderSpeaker = null;
        try {
            if (reservationReminderPlayer != null) {
                reservationReminderPlayer.stop();
                reservationReminderPlayer.release();
            }
        } catch (Exception ignored) { }
        reservationReminderPlayer = null;
        try { if (tts != null) tts.stop(); } catch (Exception ignored) { }
        try { getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); } catch (Exception ignored) { }
    }

    private String reservationReminderText(int minutes) {
        if (minutes == 30) return "Tienes un servicio en media hora";
        if (minutes == 15) return "Tienes un servicio en un cuarto de hora";
        if (minutes == 5) return "Tienes un servicio en 5 minutos";
        return "Tienes un servicio en 1 hora";
    }

    private boolean isReservationToday(String date) {
        java.util.Calendar reservation = calendarFromReservation(date);
        return reservation != null && sameCalendarDay(reservation, java.util.Calendar.getInstance());
    }

    private int calendarColor(String name) {
        if (name == null) return TEAL;
        String n = normalizeColorName(name);
        if (n.contains("rojo")) return DANGER;
        if (n.contains("amarillo")) return YELLOW;
        if (n.contains("azul")) return BLUE;
        if (n.contains("morado")) return Color.rgb(126, 87, 194);
        if (n.contains("naranja")) return Color.rgb(245, 124, 0);
        if (n.contains("rosa")) return Color.rgb(219, 39, 119);
        if (n.contains("turquesa")) return Color.rgb(6, 182, 212);
        if (n.contains("lila")) return Color.rgb(168, 85, 247);
        if (n.contains("marron")) return Color.rgb(121, 85, 72);
        if (n.contains("gris")) return Color.rgb(100, 116, 139);
        if (n.contains("negro")) return Color.rgb(17, 24, 39);
        return TEAL;
    }

    private String[] reservationColors() {
        return new String[]{"Verde", "Amarillo", "Azul", "Rojo", "Morado", "Naranja", "Rosa", "Turquesa", "Lila", "Marrón", "Gris", "Negro"};
    }

    private String normalizeColorName(String value) {
        if (value == null) return "";
        return value.trim().toLowerCase(Locale.ROOT).replace("á", "a").replace("é", "e").replace("í", "i").replace("ó", "o").replace("ú", "u").replace("ü", "u");
    }

    private void setSpinnerSelection(Spinner spinner, String value) {
        if (spinner == null || value == null) return;
        String target = normalizeColorName(value);
        for (int i = 0; i < spinner.getCount(); i++) {
            if (normalizeColorName(String.valueOf(spinner.getItemAtPosition(i))).equals(target)) {
                spinner.setSelection(i);
                return;
            }
        }
    }

    private TextView calendarArrow(String label) {
        TextView arrow = text(label, 32, BLUE, true);
        arrow.setGravity(Gravity.CENTER);
        arrow.setBackground(round(Color.rgb(232, 240, 254), 18, 0, Color.rgb(232, 240, 254)));
        return arrow;
    }

    private String calendarPeriodTitle() {
        Locale es = new Locale("es", "ES");
        if ("Día".equals(calendarViewMode)) return new SimpleDateFormat("d 'de' MMMM yyyy", es).format(calendarCursor.getTime());
        if ("Semana".equals(calendarViewMode)) {
            java.util.Calendar start = startOfCalendarWeek(calendarCursor);
            java.util.Calendar end = (java.util.Calendar) start.clone();
            end.add(java.util.Calendar.DAY_OF_MONTH, 6);
            return new SimpleDateFormat("d MMM", es).format(start.getTime()) + " - " + new SimpleDateFormat("d MMM yyyy", es).format(end.getTime());
        }
        if ("Año".equals(calendarViewMode)) return String.valueOf(calendarCursor.get(java.util.Calendar.YEAR));
        return calendarMonthTitle(calendarCursor);
    }

    private View calendarModeTabs() {
        LinearLayout tabs = row();
        tabs.setGravity(Gravity.CENTER);
        for (String mode : new String[]{"Día", "Semana", "Mes", "Año"}) {
            TextView tab = text(mode, 13, mode.equals(calendarViewMode) ? Color.WHITE : SECONDARY, true);
            tab.setGravity(Gravity.CENTER);
            tab.setBackground(round(mode.equals(calendarViewMode) ? BLUE : Color.rgb(248, 250, 252), 16, 1, mode.equals(calendarViewMode) ? BLUE : LINE));
            tab.setOnClickListener(v -> {
                calendarViewMode = mode;
                showCalendarScreen();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(42), 1);
            lp.setMargins(dp(3), 0, dp(3), 0);
            tabs.addView(tab, lp);
        }
        return tabs;
    }

    private void moveCalendarCursor(int direction) {
        if ("Día".equals(calendarViewMode)) calendarCursor.add(java.util.Calendar.DAY_OF_MONTH, direction);
        else if ("Semana".equals(calendarViewMode)) calendarCursor.add(java.util.Calendar.WEEK_OF_YEAR, direction);
        else if ("Año".equals(calendarViewMode)) calendarCursor.add(java.util.Calendar.YEAR, direction);
        else calendarCursor.add(java.util.Calendar.MONTH, direction);
        showCalendarScreen();
    }

    private void renderCalendarPeriod(LinearLayout content, List<ChatMessage> reservations) {
        if ("Año".equals(calendarViewMode)) {
            content.addView(calendarYearGrid(reservations));
            return;
        }
        if ("Mes".equals(calendarViewMode)) {
            content.addView(calendarMonthGrid(reservations));
            List<ChatMessage> selectedDay = filterReservationsForDay(reservations, calendarCursor);
            content.addView(calendarDayHeader(formatCalendarDate(calendarCursor)), wrapMT(12));
            renderCalendarAgenda(content, selectedDay, false);
            return;
        }
        if ("Semana".equals(calendarViewMode)) {
            content.addView(calendarWeekOverview(reservations));
            renderCalendarAgenda(content, filterReservationsForWeek(reservations, calendarCursor), true);
            return;
        }
        content.addView(calendarDayHeader(formatCalendarDate(calendarCursor)));
        renderCalendarAgenda(content, filterReservationsForDay(reservations, calendarCursor), false);
    }

    private View calendarMonthGrid(List<ChatMessage> reservations) {
        LinearLayout grid = column();
        grid.setPadding(dp(8), dp(10), dp(8), dp(12));
        grid.setBackground(round(Color.WHITE, 24, 1, Color.rgb(232, 237, 246)));
        grid.setElevation(dp(5));
        LinearLayout weekNames = row();
        for (String name : new String[]{"L", "M", "X", "J", "V", "S", "D"}) {
            TextView dayName = text(name, 12, SECONDARY, true);
            dayName.setGravity(Gravity.CENTER);
            weekNames.addView(dayName, new LinearLayout.LayoutParams(0, dp(30), 1));
        }
        grid.addView(weekNames);

        java.util.Calendar first = (java.util.Calendar) calendarCursor.clone();
        first.set(java.util.Calendar.DAY_OF_MONTH, 1);
        int offset = first.get(java.util.Calendar.DAY_OF_WEEK) - java.util.Calendar.MONDAY;
        if (offset < 0) offset += 7;
        int daysInMonth = first.getActualMaximum(java.util.Calendar.DAY_OF_MONTH);
        int cell = 0;
        for (int rowIndex = 0; rowIndex < 6; rowIndex++) {
            LinearLayout week = row();
            for (int col = 0; col < 7; col++, cell++) {
                int dayNumber = cell - offset + 1;
                if (dayNumber < 1 || dayNumber > daysInMonth) {
                    week.addView(new Space(this), new LinearLayout.LayoutParams(0, dp(58), 1));
                    continue;
                }
                java.util.Calendar day = (java.util.Calendar) first.clone();
                day.set(java.util.Calendar.DAY_OF_MONTH, dayNumber);
                int count = filterReservationsForDay(reservations, day).size();
                boolean selected = sameCalendarDay(day, calendarCursor);
                TextView cellView = text(String.valueOf(dayNumber) + (count > 0 ? "\n• " + count : ""), count > 0 ? 13 : 15, selected ? Color.WHITE : TEXT, selected);
                cellView.setGravity(Gravity.CENTER);
                cellView.setBackground(round(selected ? BLUE : Color.TRANSPARENT, 16, 0, selected ? BLUE : Color.TRANSPARENT));
                cellView.setOnClickListener(v -> {
                    calendarCursor.setTimeInMillis(day.getTimeInMillis());
                    showCalendarScreen();
                });
                LinearLayout.LayoutParams cellLp = new LinearLayout.LayoutParams(0, dp(58), 1);
                cellLp.setMargins(dp(2), dp(2), dp(2), dp(2));
                week.addView(cellView, cellLp);
            }
            grid.addView(week);
            if (cell - offset > daysInMonth) break;
        }
        return grid;
    }

    private View calendarWeekOverview(List<ChatMessage> reservations) {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout days = row();
        java.util.Calendar day = startOfCalendarWeek(calendarCursor);
        for (int i = 0; i < 7; i++) {
            java.util.Calendar current = (java.util.Calendar) day.clone();
            int count = filterReservationsForDay(reservations, current).size();
            boolean selected = sameCalendarDay(current, calendarCursor);
            String label = new SimpleDateFormat("EEE", new Locale("es", "ES")).format(current.getTime()).replace(".", "") + "\n" + current.get(java.util.Calendar.DAY_OF_MONTH) + (count > 0 ? "\n" + count : "");
            TextView cell = text(label, 13, selected ? Color.WHITE : TEXT, true);
            cell.setGravity(Gravity.CENTER);
            cell.setBackground(round(selected ? BLUE : Color.WHITE, 18, 1, selected ? BLUE : LINE));
            cell.setOnClickListener(v -> {
                calendarCursor.setTimeInMillis(current.getTimeInMillis());
                calendarViewMode = "Día";
                showCalendarScreen();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(68), dp(82));
            lp.setMargins(0, 0, dp(8), 0);
            days.addView(cell, lp);
            day.add(java.util.Calendar.DAY_OF_MONTH, 1);
        }
        scroll.addView(days);
        return scroll;
    }

    private View calendarYearGrid(List<ChatMessage> reservations) {
        LinearLayout grid = column();
        grid.setPadding(dp(8), dp(8), dp(8), dp(16));
        String[] months = new java.text.DateFormatSymbols(new Locale("es", "ES")).getMonths();
        int year = calendarCursor.get(java.util.Calendar.YEAR);
        for (int rowIndex = 0; rowIndex < 4; rowIndex++) {
            LinearLayout row = row();
            for (int col = 0; col < 3; col++) {
                int month = rowIndex * 3 + col;
                int count = countReservationsForMonth(reservations, year, month);
                TextView item = text(months[month] + (count > 0 ? "\n" + count + " reservas" : "\nSin reservas"), 14, count > 0 ? BLUE : SECONDARY, true);
                item.setGravity(Gravity.CENTER);
                item.setBackground(round(Color.WHITE, 20, 1, LINE));
                item.setOnClickListener(v -> {
                    calendarCursor.set(java.util.Calendar.YEAR, year);
                    calendarCursor.set(java.util.Calendar.MONTH, month);
                    calendarCursor.set(java.util.Calendar.DAY_OF_MONTH, 1);
                    calendarViewMode = "Mes";
                    showCalendarScreen();
                });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(88), 1);
                lp.setMargins(dp(4), dp(4), dp(4), dp(4));
                row.addView(item, lp);
            }
            grid.addView(row);
        }
        return grid;
    }

    private void renderCalendarAgenda(LinearLayout content, List<ChatMessage> reservations, boolean groupedByDay) {
        if (reservations.isEmpty()) {
            LinearLayout empty = card();
            TextView message = text("No hay reservas en este " + ("Semana".equals(calendarViewMode) ? "periodo" : "día"), 16, SECONDARY, true);
            message.setGravity(Gravity.CENTER);
            empty.addView(message);
            content.addView(empty, cardLp());
            return;
        }
        String currentKey = "";
        for (ChatMessage reservation : reservations) {
            java.util.Calendar date = calendarFromReservation(reservation.reservationDate);
            if (date == null) continue;
            String key = calendarDayKey(date);
            if (groupedByDay && !key.equals(currentKey)) {
                currentKey = key;
                content.addView(calendarDayHeader(formatCalendarDate(date)), wrapMT(10));
            }
            content.addView(calendarEventRow(reservation), cardLp());
        }
    }

    private List<ChatMessage> filterReservationsForDay(List<ChatMessage> reservations, java.util.Calendar day) {
        List<ChatMessage> result = new ArrayList<>();
        for (ChatMessage reservation : reservations) {
            java.util.Calendar date = calendarFromReservation(reservation.reservationDate);
            if (date != null && sameCalendarDay(date, day)) result.add(reservation);
        }
        return result;
    }

    private List<ChatMessage> filterReservationsForWeek(List<ChatMessage> reservations, java.util.Calendar cursor) {
        List<ChatMessage> result = new ArrayList<>();
        java.util.Calendar start = startOfCalendarWeek(cursor);
        java.util.Calendar end = (java.util.Calendar) start.clone();
        end.add(java.util.Calendar.DAY_OF_MONTH, 7);
        for (ChatMessage reservation : reservations) {
            java.util.Calendar date = calendarFromReservation(reservation.reservationDate);
            if (date != null && !date.before(start) && date.before(end)) result.add(reservation);
        }
        return result;
    }

    private int countReservationsForMonth(List<ChatMessage> reservations, int year, int month) {
        int count = 0;
        for (ChatMessage reservation : reservations) {
            java.util.Calendar date = calendarFromReservation(reservation.reservationDate);
            if (date != null && date.get(java.util.Calendar.YEAR) == year && date.get(java.util.Calendar.MONTH) == month) count++;
        }
        return count;
    }

    private java.util.Calendar startOfCalendarWeek(java.util.Calendar cursor) {
        java.util.Calendar start = (java.util.Calendar) cursor.clone();
        start.setFirstDayOfWeek(java.util.Calendar.MONDAY);
        int offset = start.get(java.util.Calendar.DAY_OF_WEEK) - java.util.Calendar.MONDAY;
        if (offset < 0) offset += 7;
        start.add(java.util.Calendar.DAY_OF_MONTH, -offset);
        start.set(java.util.Calendar.HOUR_OF_DAY, 0);
        start.set(java.util.Calendar.MINUTE, 0);
        start.set(java.util.Calendar.SECOND, 0);
        start.set(java.util.Calendar.MILLISECOND, 0);
        return start;
    }

    private boolean sameCalendarDay(java.util.Calendar left, java.util.Calendar right) {
        return left.get(java.util.Calendar.YEAR) == right.get(java.util.Calendar.YEAR)
                && left.get(java.util.Calendar.DAY_OF_YEAR) == right.get(java.util.Calendar.DAY_OF_YEAR);
    }

    private String calendarDayKey(java.util.Calendar date) {
        return String.format(Locale.ROOT, "%04d-%02d-%02d", date.get(java.util.Calendar.YEAR), date.get(java.util.Calendar.MONTH) + 1, date.get(java.util.Calendar.DAY_OF_MONTH));
    }

    private String formatCalendarDate(java.util.Calendar date) {
        return new SimpleDateFormat("dd/MM/yyyy", Locale.ROOT).format(date.getTime());
    }

    private String calendarMonthTitle(java.util.Calendar calendar) {
        return new SimpleDateFormat("MMMM yyyy", new Locale("es", "ES")).format(calendar.getTime());
    }

    private View calendarWeekStrip(java.util.Calendar base) {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout days = row();
        java.util.Calendar c = (java.util.Calendar) base.clone();
        int diff = c.get(java.util.Calendar.DAY_OF_WEEK) - java.util.Calendar.MONDAY;
        if (diff < 0) diff += 7;
        c.add(java.util.Calendar.DAY_OF_MONTH, -diff);
        int todayDay = base.get(java.util.Calendar.DAY_OF_YEAR);
        int todayYear = base.get(java.util.Calendar.YEAR);
        for (int i = 0; i < 7; i++) {
            boolean today = c.get(java.util.Calendar.DAY_OF_YEAR) == todayDay && c.get(java.util.Calendar.YEAR) == todayYear;
            LinearLayout cell = column();
            cell.setGravity(Gravity.CENTER);
            cell.setPadding(dp(12), dp(8), dp(12), dp(8));
            cell.setBackground(round(today ? BLUE : Color.rgb(248, 250, 252), 20, 1, today ? BLUE : Color.rgb(232, 237, 246)));
            TextView dow = text(new SimpleDateFormat("EEE", new Locale("es", "ES")).format(c.getTime()).replace(".", ""), 12, today ? Color.WHITE : SECONDARY, true);
            dow.setGravity(Gravity.CENTER);
            TextView num = text(String.valueOf(c.get(java.util.Calendar.DAY_OF_MONTH)), 21, today ? Color.WHITE : TEXT, true);
            num.setGravity(Gravity.CENTER);
            cell.addView(dow);
            cell.addView(num);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(70), dp(76));
            lp.setMargins(0, 0, dp(8), 0);
            days.addView(cell, lp);
            c.add(java.util.Calendar.DAY_OF_MONTH, 1);
        }
        scroll.addView(days);
        return scroll;
    }

    private View calendarDayHeader(String date) {
        LinearLayout header = row();
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(4), dp(16), dp(4), dp(6));
        java.util.Calendar c = calendarFromReservation(date);
        TextView dayNum = text(c == null ? "--" : String.valueOf(c.get(java.util.Calendar.DAY_OF_MONTH)), 30, BLUE, true);
        dayNum.setGravity(Gravity.CENTER);
        header.addView(dayNum, new LinearLayout.LayoutParams(dp(54), ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout labels = column();
        labels.addView(text(c == null ? date : new SimpleDateFormat("EEEE", new Locale("es", "ES")).format(c.getTime()), 16, TEXT, true));
        labels.addView(text(c == null ? "" : new SimpleDateFormat("d 'de' MMMM", new Locale("es", "ES")).format(c.getTime()), 13, SECONDARY, false));
        header.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        return header;
    }

    private View calendarEventRow(ChatMessage message) {
        LinearLayout row = row();
        row.setGravity(Gravity.TOP);
        row.setPadding(0, 0, 0, 0);
        TextView time = text(cleanCalendarTime(message.reservationTime), 14, SECONDARY, true);
        time.setGravity(Gravity.RIGHT);
        row.addView(time, new LinearLayout.LayoutParams(dp(58), ViewGroup.LayoutParams.WRAP_CONTENT));

        int color = calendarColor(message.reservationColor);
        LinearLayout event = column();
        event.setPadding(dp(14), dp(12), dp(14), dp(12));
        event.setBackground(round(color, 20, 0, color));
        event.setElevation(dp(5));
        int textColor = calendarTextColor(color);
        LinearLayout top = row();
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text(shortCalendarTitle(message), 17, textColor, true);
        top.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        top.addView(calendarStatusChip(message));
        event.addView(top);
        event.addView(text(message.pickup + " → " + message.destination, 14, textColor, false), wrapMT(6));
        event.addView(text(message.tariff + " · " + message.serviceType, 13, textColor, false), wrapMT(4));
        if (message.acceptedTaxi > 0 || hasText(message.acceptedDriverName) || hasText(message.acceptedBy)) event.addView(text("Asignado: " + acceptedServiceLabel(message), 13, textColor, true), wrapMT(5));
        if (message.reservationSupplement) event.addView(text("Suplemento reserva: 4.60 €", 13, textColor, false), wrapMT(4));
        if (hasText(message.phone)) event.addView(text("Tel. " + message.phone, 13, textColor, false), wrapMT(4));
        event.setOnClickListener(v -> showReservationDetailsDialog(message));
        LinearLayout.LayoutParams eventLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        eventLp.setMargins(dp(12), 0, 0, 0);
        row.addView(event, eventLp);
        row.setOnClickListener(v -> showReservationDetailsDialog(message));
        return row;
    }

    private void showReservationDetailsDialog(ChatMessage message) {
        LinearLayout box = column();
        box.setPadding(dp(18), dp(8), dp(18), dp(12));
        box.addView(text(shortCalendarTitle(message), 20, TEXT, true));
        addServiceDetail(box, "Recogida", message.pickup);
        addServiceDetail(box, "Destino", message.destination);
        if (hasText(message.phone)) addServiceDetail(box, "Teléfono", message.phone);
        if (hasText(message.description)) addServiceDetail(box, "Descripción", message.description);

        EditText date = field("Fecha", "Ej. 26/06/2026", false);
        date.setText(cleanCalendarDate(message.reservationDate));
        EditText time = field("Hora", "Ej. 18:30", false);
        time.setText(cleanCalendarTime(message.reservationTime));
        Spinner color = spinner(reservationColors());
        setSpinnerSelection(color, message.reservationColor);
        box.addView(text("Fecha", 14, NAVY, true), wrapMT(16));
        box.addView(date, matchHMT(56, 6));
        box.addView(text("Hora", 14, NAVY, true), wrapMT(12));
        box.addView(time, matchHMT(56, 6));
        box.addView(text("Color", 14, NAVY, true), wrapMT(12));
        box.addView(color, matchHMT(56, 6));

        Button cancel = button("Cancelar reserva", DANGER, Color.WHITE);
        box.addView(cancel, matchHMT(56, 22));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(box);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Datos de la reserva")
                .setView(scroll)
                .setNegativeButton("Cerrar", null)
                .setPositiveButton("Guardar cambios", null)
                .create();
        cancel.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Cancelar reserva")
                .setMessage("La reserva desaparecerá del Calendar y se cancelarán sus avisos.")
                .setNegativeButton("Volver", null)
                .setPositiveButton("Cancelar reserva", (confirm, which) -> {
                    cancel.setEnabled(false);
                    api.cancelReservation(message.id, (ok, error) -> runOnUiThread(() -> {
                        cancel.setEnabled(true);
                        if (error != null) { showError("No se pudo cancelar la reserva", error); return; }
                        message.serviceStatus = "cancelled";
                        dialog.dismiss();
                        toast("Reserva cancelada");
                        showCalendarScreen();
                    }));
                })
                .show());
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String newDate = cleanCalendarDate(date.getText().toString());
            String newTime = cleanCalendarTime(time.getText().toString());
            if (!isValidReservationDateTime(newDate, newTime)) {
                toast("Indica una fecha y hora válidas");
                return;
            }
            String newColor = String.valueOf(color.getSelectedItem());
            Button save = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            save.setEnabled(false);
            api.updateReservation(message.id, newDate, newTime, newColor, (ok, error) -> runOnUiThread(() -> {
                save.setEnabled(true);
                if (error != null) { showError("No se pudo actualizar la reserva", error); return; }
                message.reservationDate = newDate;
                message.reservationTime = newTime;
                message.reservationColor = newColor;
                java.util.Calendar updatedDate = calendarFromReservation(newDate);
                if (updatedDate != null) calendarCursor.setTimeInMillis(updatedDate.getTimeInMillis());
                dialog.dismiss();
                toast("Reserva actualizada");
                showCalendarScreen();
            }));
        }));
        dialog.show();
    }

    private boolean isValidReservationDateTime(String date, String time) {
        if (calendarFromReservation(date) == null || !hasText(time)) return false;
        String[] parts = time.split(":");
        if (parts.length != 2) return false;
        try {
            int hour = Integer.parseInt(parts[0].trim());
            int minute = Integer.parseInt(parts[1].trim());
            return hour >= 0 && hour <= 23 && minute >= 0 && minute <= 59;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private TextView calendarStatusChip(ChatMessage message) {
        String label = "done".equals(message.serviceStatus) ? "Hecho" : (isReservationToday(message.reservationDate) ? "Hoy" : "Pendiente");
        TextView chip = text(label, 12, Color.WHITE, true);
        chip.setGravity(Gravity.CENTER);
        int bg = "Hecho".equals(label) ? Color.rgb(22, 163, 74) : ("Hoy".equals(label) ? DANGER : Color.argb(105, 0, 0, 0));
        chip.setBackground(round(bg, 14, 0, bg));
        chip.setPadding(dp(10), dp(4), dp(10), dp(4));
        return chip;
    }

    private String shortCalendarTitle(ChatMessage message) {
        if (hasText(message.description)) return message.description.length() > 28 ? message.description.substring(0, 28) : message.description;
        if (hasText(message.pickup)) return shortStreet(message.pickup);
        return "Reserva";
    }

    private int calendarTextColor(int bg) {
        if (bg == YELLOW) return Color.rgb(42, 32, 0);
        return Color.WHITE;
    }

    private String cleanCalendarDate(String date) {
        return date == null ? "" : date.trim().replace('-', '/');
    }

    private String cleanCalendarTime(String time) {
        if (time == null || time.trim().isEmpty() || "null".equals(time)) return "--:--";
        return time.trim();
    }

    private long reservationSortTime(ChatMessage message) {
        java.util.Calendar c = calendarFromReservation(message.reservationDate);
        if (c == null) return Long.MAX_VALUE;
        String[] parts = cleanCalendarTime(message.reservationTime).split(":");
        try {
            if (parts.length >= 2) {
                c.set(java.util.Calendar.HOUR_OF_DAY, Integer.parseInt(parts[0].trim()));
                c.set(java.util.Calendar.MINUTE, Integer.parseInt(parts[1].trim()));
            }
        } catch (Exception ignored) { }
        return c.getTimeInMillis();
    }

    private java.util.Calendar calendarFromReservation(String date) {
        if (date == null) return null;
        String clean = cleanCalendarDate(date);
        String[] formats = {"d/M/yyyy", "dd/MM/yyyy", "d/M/yy", "dd/MM/yy"};
        for (String f : formats) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(f, Locale.getDefault());
                sdf.setLenient(false);
                Date parsed = sdf.parse(clean);
                if (parsed != null) {
                    java.util.Calendar c = java.util.Calendar.getInstance();
                    c.setTime(parsed);
                    return c;
                }
            } catch (Exception ignored) { }
        }
        return null;
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
        Spinner tariff = spinner(new String[]{"T-1 urbana diurna", "T-2 urbana nocturna/festivos", "T-3 urbana precio cerrado", "T-6 urbana diurna", "T-7 urbana nocturna/festivos"});
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
        CheckBox suppLarge = checkbox("Vehículo ocupado por 4-8 pasajeros");
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
            double[] pickupPoint = geocodeAddress(pickupStreet.getText().toString(), pickupCity.getText().toString());
            double[] destinationPoint = geocodeAddress(destinationStreet.getText().toString(), destinationCity.getText().toString());
            String detectedTariff = detectTariffForService(pickupCity.getText().toString(), destinationCity.getText().toString(), suppAirport, suppMoll, suppSpecialNight);
            selectTariffSpinner(tariff, detectedTariff);
            TaximeterCalculator.FareResult fare = calculateFare(detectedTariff, pickupPoint, destinationPoint, suppAirport, suppMoll, suppFira, suppSants, suppLarge, suppSpecialNight);
            if (!fixed.isChecked()) price.setText(String.format(Locale.getDefault(), "%.2f", fare.total));
            estimateDetails.setText("Tarifa detectada: " + detectedTariff + "\n" + joinFareLines(fare));
            if (pickupPoint[0] == 0 || destinationPoint[0] == 0) toast("Aviso: una dirección no se detectó bien. Se enviará igualmente.");
            api.sendService(serviceType.getSelectedItem().toString(), detectedTariff, pickupAddress, destinationAddress, fixed.isChecked(), price.getText().toString().trim(), phone.getText().toString().trim(), description.getText().toString().trim(), pickupPoint[0], pickupPoint[1], destinationPoint[0], destinationPoint[1], (ok, error) -> runOnUiThread(() -> {
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
            double[] pickupPoint = geocodeAddress(pickupStreet.getText().toString(), pickupCity.getText().toString());
            double[] destinationPoint = geocodeAddress(destinationStreet.getText().toString(), destinationCity.getText().toString());
            String detectedTariff = detectTariffForService(pickupCity.getText().toString(), destinationCity.getText().toString(), suppAirport, suppMoll, suppSpecialNight);
            selectTariffSpinner(tariff, detectedTariff);
            TaximeterCalculator.FareResult fare = calculateFare(detectedTariff, pickupPoint, destinationPoint, suppAirport, suppMoll, suppFira, suppSants, suppLarge, suppSpecialNight);
            fixed.setChecked(true);
            price.setText(String.format(Locale.getDefault(), "%.2f", fare.total));
            estimateDetails.setText("Tarifa detectada: " + detectedTariff + "\nTrayecto: " + pickupCity.getText().toString().trim() + " → " + destinationCity.getText().toString().trim() + "\n" + joinFareLines(fare));
            toast("IA detectó tarifa y precio aproximado");
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
        root.addView(androidAutoSettingsRow());
        Button logout = button("Cerrar sesión", DANGER, Color.WHITE);
        logout.setOnClickListener(v -> logoutAndShowStart());
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
        LinearLayout bar = row(); bar.setGravity(Gravity.CENTER_VERTICAL); bar.setPadding(dp(16), dp(18), dp(16), dp(16)); bar.setBackground(headerGradient()); bar.setElevation(dp(8));
        TextView l = text(left, 24, Color.WHITE, true); l.setGravity(Gravity.CENTER); l.setBackground(round(Color.argb(42, 255, 255, 255), 18, 1, Color.argb(55, 255, 255, 255))); l.setOnClickListener(v -> { if (leftAction != null) leftAction.run(); }); bar.addView(l, new LinearLayout.LayoutParams(dp(46), dp(48)));
        LinearLayout mid = column(); mid.setPadding(dp(12), 0, dp(12), 0); mid.addView(text(title, 21, Color.WHITE, true)); if (!sub.isEmpty()) mid.addView(text(sub, 13, Color.rgb(128, 242, 222), true)); bar.addView(mid, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView r = text(right, 21, Color.WHITE, false); r.setGravity(Gravity.CENTER); r.setBackground(round(Color.argb(32, 255, 255, 255), 18, 1, Color.argb(45, 255, 255, 255))); r.setOnClickListener(v -> rightAction.run()); bar.addView(r, new LinearLayout.LayoutParams(dp(46), dp(48)));
        return bar;
    }

    private LinearLayout mapHeader() {
        LinearLayout bar = row();
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(16), dp(18), dp(14), dp(16));
        bar.setBackground(headerGradient());
        bar.setElevation(dp(8));

        TextView menu = text("☰", 24, Color.WHITE, true);
        menu.setGravity(Gravity.CENTER);
        menu.setBackground(round(Color.argb(42, 255, 255, 255), 18, 1, Color.argb(55, 255, 255, 255)));
        menu.setOnClickListener(v -> { if ("Propietario".equals(session.getRole())) showOwnerPanel(); else showProfileSettingsScreen(); });
        bar.addView(menu, new LinearLayout.LayoutParams(dp(46), dp(48)));

        LinearLayout title = column();
        title.setPadding(dp(12), 0, dp(8), 0);
        title.addView(text(session.getCompany().name, 20, Color.WHITE, true));
        title.addView(text("● En línea", 13, Color.rgb(128, 242, 222), true));
        bar.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView calendar = headerAction("📅 Calendar");
        calendar.setOnClickListener(v -> showCalendarScreen());
        bar.addView(calendar, new LinearLayout.LayoutParams(dp(118), dp(44)));

        TextView add = headerAction("+");
        add.setTextSize(28);
        add.setOnClickListener(v -> showReservationScreen());
        LinearLayout.LayoutParams addLp = new LinearLayout.LayoutParams(dp(46), dp(44));
        addLp.setMargins(dp(8), 0, dp(8), 0);
        bar.addView(add, addLp);

        TextView bell = text("🔔", 21, Color.WHITE, false);
        bell.setGravity(Gravity.CENTER);
        bell.setBackground(round(Color.argb(32, 255, 255, 255), 18, 1, Color.argb(45, 255, 255, 255)));
        bell.setOnClickListener(v -> toast("Sin notificaciones nuevas"));
        bar.addView(bell, new LinearLayout.LayoutParams(dp(46), dp(48)));
        return bar;
    }

    private TextView headerAction(String label) {
        TextView action = text(label, 14, NAVY_DARK, true);
        action.setGravity(Gravity.CENTER);
        action.setBackground(round(YELLOW, 18, 0, YELLOW));
        action.setPadding(dp(8), 0, dp(8), 0);
        return action;
    }

    private LinearLayout ownerAction(String icon, String title, String desc, Runnable action) {
        LinearLayout row = row(); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(dp(16), dp(14), dp(14), dp(14)); row.setBackground(round(CARD, 24, 1, Color.rgb(232, 237, 246))); row.setOnClickListener(v -> action.run()); row.setElevation(dp(5));
        row.addView(circleText(icon, Color.rgb(229, 250, 247), TEAL, 46));
        LinearLayout txt = column(); txt.setPadding(dp(14), 0, 0, 0); txt.addView(text(title, 17, TEXT, true)); txt.addView(text(desc, 13, SECONDARY, false)); row.addView(txt, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row.addView(text("›", 30, Color.rgb(148, 163, 184), false));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); lp.setMargins(dp(18), dp(10), dp(18), 0); row.setLayoutParams(lp);
        return row;
    }

    private View taxiRow(Taxi taxi) {
        LinearLayout row = row(); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(dp(16), dp(14), dp(14), dp(14)); row.setBackground(round(CARD, 24, 1, Color.rgb(232, 237, 246))); row.setElevation(dp(5)); row.setOnClickListener(v -> showTaxiDetail(taxi));
        row.addView(circleText("🚕", taxi.online ? YELLOW : Color.LTGRAY, NAVY, 46));
        LinearLayout txt = column(); txt.setPadding(dp(14), 0, 0, 0); txt.addView(text(taxi.name() + " · " + taxi.driverName, 17, TEXT, true)); txt.addView(text(taxi.online ? "En línea" : "Fuera de línea", 13, taxi.online ? TEAL : DANGER, true)); row.addView(txt, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row.addView(text(taxi.occupied ? "OCUPADO" : "LIBRE", 13, taxi.occupied ? DANGER : TEAL, true));
        row.addView(text(taxi.online ? "  " + taxi.speed + " km/h" : "  --", 14, SECONDARY, true)); row.addView(text("  ›", 26, SECONDARY, false));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); lp.setMargins(dp(18), dp(8), dp(18), 0); row.setLayoutParams(lp);
        return row;
    }

    private void showTaxiDetail(Taxi taxi) {
        selectedTaxi = taxi;
        LinearLayout box = column();
        box.setPadding(dp(12), dp(6), dp(12), 0);
        TextView title = text(taxi.name(), 24, TEXT, true);
        box.addView(title);
        box.addView(text("Número interno: " + taxi.number, 13, SECONDARY, false), wrapMT(2));
        if ("Propietario".equals(session.getRole())) {
            EditText name = field("Nombre visible", "Ej. Aeropuerto, Caravelle, Marc", false);
            name.setText(taxi.displayName == null || taxi.displayName.trim().isEmpty() ? "" : taxi.displayName.trim());
            Spinner color = spinner(reservationColors());
            setSpinnerSelection(color, taxi.reservationColor);
            box.addView(text("Nombre personalizado", 13, SECONDARY, true), wrapMT(16));
            box.addView(name, matchHMT(58, 6));
            TextView hint = text("Si lo dejas vacío volverá a verse como Taxi " + taxi.number + ".", 12, SECONDARY, false);
            box.addView(hint, wrapMT(6));
            box.addView(text("Color de reservas", 13, SECONDARY, true), wrapMT(16));
            box.addView(color, matchHMT(58, 6));
            box.addView(text("Solo las reservas con este color sonarán en este móvil.", 12, SECONDARY, false), wrapMT(6));
            box.addView(taxiDetailLine("Conductor", taxi.driverName), wrapMT(14));
            box.addView(taxiDetailLine("Estado", taxi.online ? "En línea" : "Fuera de línea"));
            box.addView(taxiDetailLine("Servicio", taxi.occupied ? "Ocupado" : "Libre"));
            box.addView(taxiDetailLine("Velocidad", taxi.online ? taxi.speed + " km/h" : "--"));
            box.addView(taxiDetailLine("Dirección", taxi.direction));
            box.addView(taxiDetailLine("Última conexión", taxi.lastUpdate));
            Button remove = button("Expulsar taxista", DANGER, Color.WHITE);
            remove.setOnClickListener(v -> confirmRevokeTaxi(taxi));
            box.addView(remove, matchHMT(54, 18));
            new AlertDialog.Builder(this)
                    .setTitle("Editar taxi")
                    .setView(box)
                    .setPositiveButton("Guardar", (d, w) -> saveTaxiSettings(taxi, name.getText().toString(), color.getSelectedItem().toString()))
                    .setNeutralButton("Ver en mapa", (d, w) -> showMapScreen())
                    .setNegativeButton("Cerrar", null)
                    .show();
            return;
        }
        box.addView(taxiDetailLine("Conductor", taxi.driverName), wrapMT(14));
        box.addView(taxiDetailLine("Estado", taxi.online ? "En línea" : "Fuera de línea"));
        box.addView(taxiDetailLine("Servicio", taxi.occupied ? "Ocupado" : "Libre"));
        box.addView(taxiDetailLine("Velocidad", taxi.online ? taxi.speed + " km/h" : "--"));
        box.addView(taxiDetailLine("Dirección", taxi.direction));
        box.addView(taxiDetailLine("Última conexión", taxi.lastUpdate));
        new AlertDialog.Builder(this).setTitle(taxi.name()).setView(box).setPositiveButton("Ver en mapa", (d, w) -> showMapScreen()).setNegativeButton("Cerrar", null).show();
    }

    private void confirmRevokeTaxi(Taxi taxi) {
        new AlertDialog.Builder(this)
                .setTitle("Expulsar a " + taxi.name())
                .setMessage("Se cerrará su acceso a la empresa y desaparecerá de la flota. Podrá volver a solicitar acceso más adelante.")
                .setPositiveButton("Expulsar", (dialog, which) -> api.revokeTaxiAccess(taxi.number, (ok, error) -> runOnUiThread(() -> {
                    if (error != null) toast("No se pudo expulsar: " + error.getMessage());
                    else {
                        toast(taxi.name() + " expulsado");
                        showTaxiListScreen();
                    }
                })))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private TextView taxiDetailLine(String label, String value) {
        TextView line = text(label + ": " + (value == null || "null".equals(value) ? "--" : value), 15, TEXT, false);
        line.setPadding(0, dp(3), 0, dp(3));
        return line;
    }

    private void saveTaxiDisplayName(Taxi taxi, String name) {
        String clean = name == null ? "" : name.trim();
        api.updateTaxiName(taxi.number, clean, (ok, error) -> runOnUiThread(() -> {
            if (error != null) toast("No se pudo cambiar nombre: " + error.getMessage());
            else {
                taxi.displayName = clean;
                toast(clean.isEmpty() ? "Nombre quitado" : "Nombre actualizado");
                showTaxiListScreen();
            }
        }));
    }

    private void saveTaxiSettings(Taxi taxi, String name, String color) {
        String cleanName = name == null ? "" : name.trim();
        String cleanColor = color == null ? "" : color.trim();
        api.updateTaxiName(taxi.number, cleanName, (ok, error) -> runOnUiThread(() -> {
            if (error != null) { toast("No se pudo cambiar nombre: " + error.getMessage()); return; }
            api.updateTaxiReservationColor(taxi.number, cleanColor, (ok2, error2) -> runOnUiThread(() -> {
                if (error2 != null) toast("Nombre guardado, color no: " + error2.getMessage());
                else {
                    taxi.displayName = cleanName;
                    taxi.reservationColor = cleanColor;
                    if (taxi.number == safeTaxiNumber()) {
                        assignedReservationColor = cleanColor;
                        session.setAssignedReservationColor(cleanColor);
                    }
                    toast("Taxi actualizado");
                    showTaxiListScreen();
                }
            }));
        }));
    }

    public void showTaxiColorsScreen() {
        LinearLayout root = baseWithHeader("Colores por taxista", "←", true, () -> showOwnerPanel());
        root.addView(subtitle("Asigna un color a cada taxi. Las reservas de ese color solo sonarán en ese móvil."));
        LinearLayout list = column();
        list.setPadding(dp(16), dp(8), dp(16), dp(28));
        root.addView(list);
        setContentView(scroll(root));
        api.getTaxis(session.getCentralNumber(), (taxis, error) -> runOnUiThread(() -> {
            list.removeAllViews();
            if (error != null) { toast("No se pudo cargar taxis: " + error.getMessage()); return; }
            if (taxis.isEmpty()) {
                LinearLayout empty = card();
                empty.addView(text("Aún no hay taxis aprobados", 18, TEXT, true));
                empty.addView(text("Aprueba primero los conductores y luego asigna colores.", 14, SECONDARY, false), wrapMT(6));
                list.addView(empty, cardLp());
                return;
            }
            for (Taxi taxi : taxis) list.addView(taxiColorCard(taxi), cardLp());
        }));
    }

    private View taxiColorCard(Taxi taxi) {
        LinearLayout card = card();
        LinearLayout top = row();
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(circleText("🚕", calendarColor(taxi.reservationColor), calendarTextColor(calendarColor(taxi.reservationColor)), 46));
        LinearLayout info = column();
        info.setPadding(dp(12), 0, 0, 0);
        info.addView(text(taxi.name(), 18, TEXT, true));
        info.addView(text("Taxi " + taxi.number + " · " + taxi.driverName, 13, SECONDARY, false));
        top.addView(info, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        card.addView(top);
        Spinner color = spinner(reservationColors());
        setSpinnerSelection(color, taxi.reservationColor);
        card.addView(color, matchHMT(58, 14));
        Button save = button("Guardar color", TEAL, Color.WHITE);
        save.setOnClickListener(v -> api.updateTaxiReservationColor(taxi.number, color.getSelectedItem().toString(), (ok, error) -> runOnUiThread(() -> {
            if (error != null) toast("No se pudo guardar color: " + error.getMessage());
            else {
                String cleanColor = color.getSelectedItem().toString();
                if (taxi.number == safeTaxiNumber()) {
                    assignedReservationColor = cleanColor;
                    session.setAssignedReservationColor(cleanColor);
                }
                toast("Color guardado para " + taxi.name());
                showTaxiColorsScreen();
            }
        })));
        card.addView(save, matchHMT(54, 12));
        return card;
    }

    private void showRenameTaxiDialog(Taxi taxi) {
        EditText input = field("Nombre visible del taxi", "Ej. Aeropuerto, Marc, Caravelle", false);
        input.setText(taxi.displayName == null || taxi.displayName.trim().isEmpty() ? "" : taxi.displayName.trim());
        new AlertDialog.Builder(this)
                .setTitle("Cambiar nombre del taxi")
                .setMessage("Solo el administrador puede cambiar este nombre. Puedes quitar 'Taxi' y poner cualquier nombre.")
                .setView(input)
                .setPositiveButton("Guardar", (d, w) -> {
                    saveTaxiDisplayName(taxi, input.getText().toString());
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private LinearLayout settingsRow(String title, String desc, Boolean checked) {
        LinearLayout row = ownerAction("⚙", title, desc, () -> toast("Configuración local"));
        row.removeViewAt(row.getChildCount() - 1);
        if (checked != null) { Switch sw = new Switch(this); sw.setChecked(checked); row.addView(sw); }
        return row;
    }

    private LinearLayout androidAutoSettingsRow() {
        LinearLayout row = ownerAction("🚘", "Taxímetro en Android Auto", "Toca para diagnóstico. Interruptor: activar en coche", () -> showAndroidAutoDiagnosticDialog());
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

    private void showAndroidAutoDiagnosticDialog() {
        boolean serviceDeclared = false;
        try {
            getPackageManager().getServiceInfo(new ComponentName(this, TaxiLinkCarAppService.class), PackageManager.GET_META_DATA);
            serviceDeclared = true;
        } catch (PackageManager.NameNotFoundException ignored) {
        }

        String message = "Taxímetro en Android Auto: " + (session.isAndroidAutoTaximeterEnabled() ? "activado" : "desactivado")
                + "\nPaquete: " + getPackageName()
                + "\nServicio: .TaxiLinkCarAppService"
                + "\nServicio declarado: " + (serviceDeclared ? "sí" : "no")
                + "\nCategoría: POI + NAVIGATION"
                + "\n\nSi no aparece en el coche:"
                + "\n1. Abre TaxiLink una vez en el móvil."
                + "\n2. Activa Fuentes desconocidas en Android Auto."
                + "\n3. Fuerza cierre de Android Auto y vuelve a conectar."
                + "\n4. Si sigue sin aparecer, pruébalo con DHU o Play Console Internal Testing.";

        new AlertDialog.Builder(this)
                .setTitle("Diagnóstico Android Auto")
                .setMessage(message)
                .setPositiveButton("Cerrar", null)
                .show();
    }

    private LinearLayout bottomNav(String active) {
        LinearLayout nav = row(); nav.setGravity(Gravity.CENTER); nav.setPadding(dp(6), dp(8), dp(6), dp(8)); nav.setBackground(round(Color.WHITE, 26, 1, Color.rgb(232, 237, 246))); nav.setElevation(dp(12));
        addNav(nav, "Mapa", "⌖", active, () -> showMapScreen()); addNav(nav, "Taxis", "🚕", active, () -> showTaxiListScreen()); addNav(nav, "", "🎙", active, () -> toast("Mantén pulsado el micrófono en el mapa")); addNav(nav, "Chats", "💬", active, () -> showChatScreen()); addNav(nav, "Más", "☰", active, () -> showProfileSettingsScreen());
        return nav;
    }

    private void addNav(LinearLayout nav, String label, String icon, String active, Runnable action) {
        TextView item = text(icon + (label.isEmpty() ? "" : "\n" + label), label.equals(active) ? 13 : 12, label.equals(active) ? TEAL : SECONDARY, true); item.setGravity(Gravity.CENTER); item.setPadding(0, dp(5), 0, dp(5)); if (label.equals(active)) item.setBackground(round(Color.rgb(229, 250, 247), 18, 1, Color.rgb(191, 239, 232))); item.setOnClickListener(v -> action.run());
        nav.addView(item, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
    }

    private void showChangePasswordDialog(boolean owner) {
        EditText input = field("Nueva contraseña", "Mínimo 6 caracteres", true);
        new AlertDialog.Builder(this).setTitle(owner ? "Contraseña propietario" : "Contraseña conductores").setView(input).setPositiveButton("Guardar", (d, w) -> {
            String password = input.getText().toString().trim();
            if (password.length() < 6) { toast("Mínimo 6 caracteres"); return; }
            TaxiLinkApi.Callback<Boolean> callback = (ok, error) -> runOnUiThread(() -> {
                if (error != null) showError("No se pudo cambiar la contraseña", error);
                else toast("Contraseña actualizada");
            });
            if (owner) api.updateOwnerPassword(password, callback);
            else api.updateDriverPassword(password, callback);
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
                    api.signOut();
                    session.logout();
                    toast("Empresa eliminada");
                    showStartScreen();
                })))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void logoutAndShowStart() {
        if (api != null) api.signOut();
        session.logout();
        showStartScreen();
    }

    private void startRealGpsUpdates() {
        if (!PermissionHelper.hasLocation(this)) {
            PermissionHelper.requestNeededPermissions(this);
            if (taxiInfoText != null) taxiInfoText.setText("Permiso de ubicación necesario para GPS real");
            return;
        }
        if (android.os.Build.VERSION.SDK_INT >= 23
                && checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return;
        try {
            LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
            if (lm == null) {
                if (taxiInfoText != null) taxiInfoText.setText("GPS no disponible en este dispositivo");
                return;
            }
            if (liveLocationListener != null) lm.removeUpdates(liveLocationListener);
            liveLocationListener = new LocationListener() {
                @Override public void onLocationChanged(Location loc) {
                    applyGpsFix(loc);
                }
                @Override public void onStatusChanged(String p, int s, Bundle e) {}
                @Override public void onProviderEnabled(String p) {}
                @Override public void onProviderDisabled(String p) { runOnUiThread(() -> toast("Activa el GPS para ubicación real")); }
            };
            Location lastGps = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            Location lastNetwork = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            Location initial = betterLocation(lastGps, lastNetwork);
            if (initial != null) applyGpsFix(initial);
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 500, 0, liveLocationListener);
            lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000, 0, liveLocationListener);
            try { lm.requestLocationUpdates(LocationManager.PASSIVE_PROVIDER, 750, 0, liveLocationListener); } catch (Exception ignored) { }
        } catch (Exception e) {
            if (taxiInfoText != null) taxiInfoText.setText("GPS no disponible: " + e.getMessage());
        }
    }

    private void stopRealGpsUpdates() {
        if (liveLocationListener == null) return;
        try {
            LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            if (lm != null) lm.removeUpdates(liveLocationListener);
        } catch (Exception ignored) { }
        liveLocationListener = null;
    }

    private void applyGpsFix(Location loc) {
        if (!isUsableNavigationFix(loc)) return;
        int speed = Math.max(0, Math.round(loc.getSpeed() * 3.6f));
        float bearing = navigationBearing(loc);
        String direction = directionFromBearing(bearing);
        lastGpsFixAt = System.currentTimeMillis();
        selectedTaxi = new Taxi(safeTaxiNumber(), true, speed, direction, loc.getLatitude(), loc.getLongitude(), now());
        selectedTaxi.driverName = session.getDisplayName();
        selectedTaxi.reservationColor = assignedReservationColor;
        selectedTaxi.occupied = activeService != null;
        long nowMs = System.currentTimeMillis();
        if (nowMs - lastLocationUiAt > 250) {
            lastLocationUiAt = nowMs;
            updateLiveNavigation(loc);
            if (taxiTitleText != null) taxiTitleText.setText(selectedTaxi.name());
            if (taxiInfoText != null) taxiInfoText.setText(speed + " km/h  ·  " + direction + "  ·  Actualizado " + now());
            updateTaxiMarker(selectedTaxi);
            updateUserMarker(loc.getLatitude(), loc.getLongitude(), bearing);
        }
        if (nowMs - lastLocationSentAt > 2000) {
            lastLocationSentAt = System.currentTimeMillis();
            api.sendLocation(selectedTaxi.number, session.getDisplayName(), loc.getLatitude(), loc.getLongitude(), speed, direction, (ok, error) -> { });
        }
        lastAcceptedLocation = new Location(loc);
    }

    private Location betterLocation(Location a, Location b) {
        if (a == null) return b;
        if (b == null) return a;
        if (a.hasAccuracy() && b.hasAccuracy() && b.getAccuracy() < a.getAccuracy()) return b;
        return a.getTime() >= b.getTime() ? a : b;
    }

    private boolean isUsableNavigationFix(Location loc) {
        if (loc == null || loc.getLatitude() == 0 || loc.getLongitude() == 0) return false;
        if (loc.getTime() > 0 && System.currentTimeMillis() - loc.getTime() > 120000) return false;
        if (loc.hasAccuracy()) {
            String provider = loc.getProvider() == null ? "" : loc.getProvider();
            if (LocationManager.GPS_PROVIDER.equals(provider) && loc.getAccuracy() > 80f) return false;
            if (!LocationManager.GPS_PROVIDER.equals(provider) && loc.getAccuracy() > 120f) return false;
        }
        if (lastAcceptedLocation != null && loc.getTime() > lastAcceptedLocation.getTime()) {
            float seconds = Math.max(1f, (loc.getTime() - lastAcceptedLocation.getTime()) / 1000f);
            float distance = loc.distanceTo(lastAcceptedLocation);
            if (distance / seconds > 65f && (!loc.hasAccuracy() || loc.getAccuracy() > 30f)) return false;
        }
        return true;
    }

    private float navigationBearing(Location loc) {
        if (loc.hasBearing() && loc.getSpeed() > 0.8f) {
            lastStableBearing = loc.getBearing();
            return lastStableBearing;
        }
        if (selectedTaxi != null && selectedTaxi.latitude != 0 && selectedTaxi.longitude != 0) {
            float[] out = new float[2];
            Location.distanceBetween(selectedTaxi.latitude, selectedTaxi.longitude, loc.getLatitude(), loc.getLongitude(), out);
            if (out[0] > 4f) lastStableBearing = out[1];
        }
        return lastStableBearing;
    }

    private void startTaxiPolling() {
        if (taxiPoller != null) handler.removeCallbacks(taxiPoller);
        taxiPoller = new Runnable() {
            @Override public void run() {
                if (!"Propietario".equals(session.getRole())) {
                    handler.postDelayed(this, 10000);
                    return;
                }
                api.getTaxis(session.getCentralNumber(), (taxis, error) -> runOnUiThread(() -> {
                    if (error == null && mapView != null && mapView.isAttachedToWindow()) {
                        for (Taxi taxi : taxis) {
                            updateTaxiMarker(taxi);
                        }
                    }
                }));
                handler.postDelayed(this, 10000);
            }
        };
        handler.postDelayed(taxiPoller, 2000);
    }

    private void startWalkiePolling() {
        startWalkieFirestoreFallback();
    }

    private void startWalkieFirestoreFallback() {
        if (walkieListenersStarted) return;
        walkieListenersStarted = true;
        if (walkieLabel != null) walkieLabel.setText("Walkie listo");
        api.listenWalkieStatus((status, error) -> runOnUiThread(() -> {
            if (error == null && walkieLabel != null && walkieLabel.isAttachedToWindow() && !localSpeaking) walkieLabel.setText(status);
        }));
        api.listenLatestWalkieClip((clip, error) -> runOnUiThread(() -> {
            if (error == null && clip != null && clip.createdAt > lastWalkieClipAt && !api.deviceId().equals(clip.deviceId)) {
                lastWalkieClipId = clip.id;
                lastWalkieClipAt = clip.createdAt;
                playWalkieClip(clip);
            }
        }));
        api.listenUrgentAlert((alert, error) -> runOnUiThread(() -> {
            if (error == null && alert != null && alert.createdAt > lastUrgentAt && !api.deviceId().equals(alert.deviceId)) {
                lastUrgentAt = alert.createdAt;
                playUrgentTone();
                toast("Aviso urgente de Taxi " + alert.taxiNumber + " · " + alert.sender);
            }
        }));
    }

    private void stopLiveWork() {
        stopRealGpsUpdates();
        if (handler != null) {
            if (taxiPoller != null) handler.removeCallbacks(taxiPoller);
            if (chatPoller != null) handler.removeCallbacks(chatPoller);
            if (serviceAlertPoller != null) handler.removeCallbacks(serviceAlertPoller);
        }
        taxiPoller = null;
        chatPoller = null;
        serviceAlertPoller = null;
        walkieListenersStarted = false;
        if (api != null) api.stopRealtimeListeners();
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
                    api.sendWalkieClip(selectedTaxi.number, session.getDisplayName(), audio, (ok, error) -> runOnUiThread(() -> {
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

    private void trimCacheDir(File dir, long maxBytes) {
        try {
            if (dir == null || !dir.exists()) return;
            File[] files = dir.listFiles();
            if (files == null) return;
            long total = 0;
            for (File f : files) total += folderSize(f);
            if (total <= maxBytes) return;
            for (File f : files) {
                if (System.currentTimeMillis() - f.lastModified() > 15L * 60L * 1000L) deleteQuietly(f);
            }
        } catch (Exception ignored) { }
    }

    private long folderSize(File f) {
        if (f == null || !f.exists()) return 0;
        if (f.isFile()) return f.length();
        long total = 0;
        File[] files = f.listFiles();
        if (files != null) for (File child : files) total += folderSize(child);
        return total;
    }

    private void deleteQuietly(File f) {
        try {
            if (f == null || !f.exists()) return;
            if (f.isDirectory()) {
                File[] files = f.listFiles();
                if (files != null) for (File child : files) deleteQuietly(child);
            }
            f.delete();
        } catch (Exception ignored) { }
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
        api.sendUrgentAlert(selectedTaxi.number, session.getDisplayName(), (ok, error) -> runOnUiThread(() -> {
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

    private void updateUserMarker(double latitude, double longitude, float bearing) {
        if (mapView == null || !mapView.isAttachedToWindow()) return;
        GeoPoint point = new GeoPoint(latitude, longitude);
        setNavigationCamera(point);
        updateNavigationArrow(point, bearing);
        mapView.invalidate();
    }

    private void setNavigationCamera(GeoPoint point) {
        if (mapView == null) return;
        if (mapManuallyMoved) return;
        if (mapView.getZoomLevelDouble() < 18.8) mapView.getController().setZoom(19.0);
        long now = System.currentTimeMillis();
        if (now - lastCameraMoveAt > 500) {
            lastCameraMoveAt = now;
            mapView.getController().animateTo(point);
        }
    }

    private void updateNavigationArrow(GeoPoint point, float bearing) {
        if (mapView == null) return;
        if (gpsArrowOverlay == null) gpsArrowOverlay = new GpsArrowOverlay();
        gpsArrowOverlay.set(point, bearing);
        List<Overlay> overlays = mapView.getOverlays();
        if (overlays.isEmpty() || overlays.get(overlays.size() - 1) != gpsArrowOverlay) {
            overlays.remove(gpsArrowOverlay);
            overlays.add(gpsArrowOverlay);
        }
    }

    private class GpsArrowOverlay extends Overlay {
        private GeoPoint point;
        private float bearing;
        private final Point screenPoint = new Point();

        void set(GeoPoint point, float bearing) {
            this.point = point;
            this.bearing = bearing;
        }

        @Override public void draw(Canvas canvas, MapView osmv, boolean shadow) {
            if (shadow || point == null || osmv == null) return;
            osmv.getProjection().toPixels(point, screenPoint);
            float x = screenPoint.x;
            float y = screenPoint.y;
            float radius = dp(12);
            canvas.save();
            canvas.rotate(bearing + osmv.getMapOrientation(), x, y);

            Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
            glow.setColor(Color.argb(70, 25, 103, 210));
            canvas.drawCircle(x, y, radius + dp(4), glow);

            Paint circle = new Paint(Paint.ANTI_ALIAS_FLAG);
            circle.setColor(Color.rgb(25, 103, 210));
            canvas.drawCircle(x, y, radius, circle);

            Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
            border.setColor(Color.WHITE);
            border.setStyle(Paint.Style.STROKE);
            border.setStrokeWidth(dp(2));
            canvas.drawCircle(x, y, radius, border);

            Paint arrow = new Paint(Paint.ANTI_ALIAS_FLAG);
            arrow.setColor(Color.WHITE);
            arrow.setStyle(Paint.Style.FILL);
            Path path = new Path();
            path.moveTo(x, y - dp(8));
            path.lineTo(x + dp(7), y + dp(7));
            path.lineTo(x, y + dp(3));
            path.lineTo(x - dp(7), y + dp(7));
            path.close();
            canvas.drawPath(path, arrow);
            canvas.restore();
        }
    }

    private Drawable navigationArrowIcon() {
        int size = dp(92);
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
        shadow.setColor(Color.argb(85, 0, 0, 0));
        Path shadowPath = googleChevronPath(size, dp(3), dp(5));
        canvas.drawPath(shadowPath, shadow);
        Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
        border.setColor(Color.WHITE);
        border.setStyle(Paint.Style.FILL);
        canvas.drawPath(googleChevronPath(size, 0, 0), border);
        Paint arrow = new Paint(Paint.ANTI_ALIAS_FLAG);
        arrow.setColor(Color.rgb(25, 103, 210));
        arrow.setStyle(Paint.Style.FILL);
        canvas.drawPath(googleChevronPath((int) (size * 0.74f), (int) (size * 0.13f), (int) (size * 0.10f)), arrow);
        return new BitmapDrawable(getResources(), bitmap);
    }

    private Path googleChevronPath(int size, int offsetX, int offsetY) {
        Path path = new Path();
        path.moveTo(offsetX + size / 2f, offsetY + size * 0.05f);
        path.lineTo(offsetX + size * 0.88f, offsetY + size * 0.86f);
        path.lineTo(offsetX + size / 2f, offsetY + size * 0.66f);
        path.lineTo(offsetX + size * 0.12f, offsetY + size * 0.86f);
        path.close();
        return path;
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
        if (serviceLineShadow == null) {
            serviceLineShadow = new Polyline();
            serviceLineShadow.setColor(Color.rgb(0, 82, 90));
            serviceLineShadow.setWidth(dp(13));
            mapView.getOverlays().add(serviceLineShadow);
        }
        if (serviceLine == null) {
            serviceLine = new Polyline();
            serviceLine.setColor(Color.rgb(0, 220, 230));
            serviceLine.setWidth(dp(8));
            mapView.getOverlays().add(serviceLine);
        }
        if (selectedTaxi != null && hasRecentGpsFix() && selectedTaxi.latitude != 0 && selectedTaxi.longitude != 0 && shouldFetchRoute()) {
            fetchRouteToService(selectedTaxi.latitude, selectedTaxi.longitude, currentTargetLat(), currentTargetLng());
        } else if (!hasRecentGpsFix() && taxiInfoText != null) {
            taxiInfoText.setText("Esperando GPS real para iniciar ruta");
        }
        mapView.invalidate();
    }

    private boolean hasRecentGpsFix() { return lastGpsFixAt > 0 && System.currentTimeMillis() - lastGpsFixAt < 20000; }

    private double currentTargetLat() { return routingToDestination && activeService != null && activeService.destinationLat != 0 ? activeService.destinationLat : activeService.pickupLat; }
    private double currentTargetLng() { return routingToDestination && activeService != null && activeService.destinationLng != 0 ? activeService.destinationLng : activeService.pickupLng; }

    private boolean shouldFetchRoute() {
        long now = System.currentTimeMillis();
        return activeRoutePoints.isEmpty() || now - lastRouteFetchAt > 15000;
    }

    private void fetchRouteToService(double fromLat, double fromLng, double toLat, double toLng) {
        if (toLat == 0 || toLng == 0 || fromLat == 0 || fromLng == 0 || routeFetchInFlight) return;
        routeFetchInFlight = true;
        new Thread(() -> {
            try {
                lastRouteFetchAt = System.currentTimeMillis();
                if (BuildConfig.HERE_API_KEY != null && !BuildConfig.HERE_API_KEY.trim().isEmpty()) {
                    fetchHereRoute(fromLat, fromLng, toLat, toLng);
                    return;
                }
                fetchOsrmRoute(fromLat, fromLng, toLat, toLng);
            } catch (Exception e) {
                showFallbackRoute(fromLat, fromLng, toLat, toLng);
            } finally {
                routeFetchInFlight = false;
            }
        }).start();
    }

    private void fetchHereRoute(double fromLat, double fromLng, double toLat, double toLng) throws Exception {
        String urlText = "https://router.hereapi.com/v8/routes?transportMode=car&origin=" + fromLat + "," + fromLng + "&destination=" + toLat + "," + toLng + "&return=polyline,summary,travelSummary,actions,instructions,tolls&lang=es-es&apikey=" + BuildConfig.HERE_API_KEY;
        JSONObject root = readJson(urlText);
        JSONObject section = root.getJSONArray("routes").getJSONObject(0).getJSONArray("sections").getJSONObject(0);
        List<GeoPoint> points = decodeHerePolyline(section.getString("polyline"));
        JSONObject summary = section.getJSONObject("summary");
        RouteInsight insight = parseHereRouteInsight(section, summary);
        JSONArray actions = section.optJSONArray("actions");
        List<NavManeuver> maneuvers = new ArrayList<>();
        String instruction = "Sigue la ruta hacia la recogida";
        if (actions != null && actions.length() > 0) {
            instruction = actions.getJSONObject(0).optString("instruction", instruction);
            for (int i = 0; i < actions.length(); i++) {
                JSONObject action = actions.getJSONObject(i);
                int offset = Math.max(0, Math.min(points.size() - 1, action.optInt("offset", 0)));
                maneuvers.add(new NavManeuver(action.optString("instruction", "Continúa"), points.get(offset)));
            }
        }
        String finalInstruction = instruction;
        String finalDistance = String.format(Locale.getDefault(), "%.1f km", summary.optDouble("length", 0) / 1000.0);
        String finalEta = Math.max(1, (int) Math.ceil(summary.optDouble("duration", 0) / 60.0)) + " min";
        runOnUiThread(() -> applyRoute(points, maneuvers, finalInstruction, finalDistance, finalEta, insight));
    }

    private void fetchOsrmRoute(double fromLat, double fromLng, double toLat, double toLng) throws Exception {
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
                JSONObject route = root.getJSONArray("routes").getJSONObject(0);
                JSONArray coords = route.getJSONObject("geometry").getJSONArray("coordinates");
                JSONObject leg = route.getJSONArray("legs").getJSONObject(0);
                JSONArray steps = leg.getJSONArray("steps");
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
                String finalDistance = String.format(Locale.getDefault(), "%.1f km", leg.optDouble("distance", 0) / 1000.0);
                String finalEta = Math.max(1, (int) Math.ceil(leg.optDouble("duration", 0) / 60.0)) + " min";
                RouteInsight insight = new RouteInsight();
                insight.source = "OSRM";
                runOnUiThread(() -> applyRoute(points, new ArrayList<>(), finalInstruction, finalDistance, finalEta, insight));
    }

    private RouteInsight parseHereRouteInsight(JSONObject section, JSONObject summary) {
        RouteInsight insight = new RouteInsight();
        insight.source = "HERE";
        JSONObject travelSummary = section.optJSONObject("travelSummary");
        if (travelSummary != null) {
            int base = travelSummary.optInt("baseDuration", summary.optInt("baseDuration", 0));
            int duration = travelSummary.optInt("duration", summary.optInt("duration", 0));
            insight.trafficDelaySeconds = Math.max(0, duration - base);
        } else {
            int base = summary.optInt("baseDuration", 0);
            int duration = summary.optInt("duration", 0);
            insight.trafficDelaySeconds = Math.max(0, duration - base);
        }
        JSONArray tolls = section.optJSONArray("tolls");
        if (tolls != null && tolls.length() > 0) {
            insight.hasTolls = true;
            insight.tollInfo = summarizeTolls(tolls);
        }
        return insight;
    }

    private String summarizeTolls(JSONArray tolls) {
        double total = 0;
        String currency = "EUR";
        int priced = 0;
        for (int i = 0; i < tolls.length(); i++) {
            JSONObject toll = tolls.optJSONObject(i);
            if (toll == null) continue;
            JSONArray fares = toll.optJSONArray("fares");
            if (fares == null || fares.length() == 0) continue;
            JSONObject fare = fares.optJSONObject(0);
            if (fare == null) continue;
            JSONObject price = fare.optJSONObject("price");
            if (price == null) continue;
            double value = price.optDouble("value", -1);
            if (value >= 0) {
                total += value;
                priced++;
                currency = price.optString("currency", currency);
            }
        }
        if (priced > 0) return String.format(Locale.getDefault(), "Peaje %.2f %s", total, currency);
        return "Peaje en ruta";
    }

    private JSONObject readJson(String urlText) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(urlText).openConnection();
        con.setConnectTimeout(8000);
        con.setReadTimeout(8000);
        BufferedReader reader = new BufferedReader(new InputStreamReader(con.getInputStream()));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line);
        reader.close();
        return new JSONObject(sb.toString());
    }

    private void applyRoute(List<GeoPoint> points, List<NavManeuver> maneuvers, String instruction, String distance, String eta, RouteInsight insight) {
        navInstruction = prettyInstruction(instruction);
        navDistance = distance;
        navEta = eta;
        activeRouteEtaMinutes = parseEtaMinutes(eta);
        activeRouteHasTolls = insight != null && insight.hasTolls;
        activeRouteTollInfo = insight == null ? "" : insight.tollInfo;
        activeRouteTrafficDelaySeconds = insight == null ? 0 : insight.trafficDelaySeconds;
        navRouteInfo = buildRouteInfo(insight);
        activeRoutePoints.clear();
        activeRoutePoints.addAll(points);
        activeManeuvers.clear();
        activeManeuvers.addAll(maneuvers);
        nextManeuverIndex = 0;
        if (serviceLineShadow != null) serviceLineShadow.setPoints(points);
        if (serviceLine != null) serviceLine.setPoints(points);
        updateNavigationTexts();
        if (taxiInfoText != null) taxiInfoText.setText("OCUPADO · GPS servicio · " + instruction + (navRouteInfo.isEmpty() ? "" : " · " + navRouteInfo));
        announceRouteInsightIfNeeded();
        if (mapView != null) {
            if (selectedTaxi != null && hasRecentGpsFix() && selectedTaxi.latitude != 0 && selectedTaxi.longitude != 0) {
                updateVisibleRouteFromLocation(selectedTaxi.latitude, selectedTaxi.longitude);
                setNavigationCamera(new GeoPoint(selectedTaxi.latitude, selectedTaxi.longitude));
            } else {
                mapView.getController().setZoom(19.0);
            }
            mapView.invalidate();
        }
    }

    private String buildRouteInfo(RouteInsight insight) {
        List<String> tags = new ArrayList<>();
        if (insight != null && insight.hasTolls) tags.add(insight.tollInfo == null || insight.tollInfo.isEmpty() ? "Peaje" : insight.tollInfo);
        if (insight != null && insight.trafficDelaySeconds >= 180) tags.add("Tráfico +" + Math.max(3, Math.round(insight.trafficDelaySeconds / 60f)) + " min");
        if (insight != null && insight.source != null && !insight.source.isEmpty()) tags.add(insight.source);
        return joinTags(tags);
    }

    private int parseEtaMinutes(String eta) {
        if (eta == null) return 7;
        try {
            String digits = eta.replaceAll("[^0-9]", "");
            if (digits.isEmpty()) return 7;
            return Math.max(1, Integer.parseInt(digits));
        } catch (Exception ignored) {
            return 7;
        }
    }

    private String joinTags(List<String> tags) {
        StringBuilder out = new StringBuilder();
        for (String tag : tags) {
            if (tag == null || tag.trim().isEmpty()) continue;
            if (out.length() > 0) out.append(" · ");
            out.append(tag.trim());
        }
        return out.toString();
    }

    private void announceRouteInsightIfNeeded() {
        if (activeRouteHasTolls) speakRoadAlert(activeRouteTollInfo == null || activeRouteTollInfo.isEmpty() ? "Ruta con peaje" : activeRouteTollInfo);
        if (activeRouteTrafficDelaySeconds >= 300) speakRoadAlert("Tráfico en ruta, retraso aproximado " + Math.round(activeRouteTrafficDelaySeconds / 60f) + " minutos");
    }

    private void showFallbackRoute(double fromLat, double fromLng, double toLat, double toLng) {
        runOnUiThread(() -> {
            if (serviceLine != null) {
                List<GeoPoint> points = new ArrayList<>();
                points.add(new GeoPoint(fromLat, fromLng));
                points.add(new GeoPoint(toLat, toLng));
                if (serviceLineShadow != null) serviceLineShadow.setPoints(points);
                serviceLine.setPoints(points);
                activeRoutePoints.clear();
                activeRoutePoints.addAll(points);
                float[] result = new float[1];
                Location.distanceBetween(fromLat, fromLng, toLat, toLng, result);
                navDistance = String.format(Locale.getDefault(), "%.1f km", result[0] / 1000f);
                activeRouteEtaMinutes = Math.max(1, (int) Math.ceil((result[0] / 1000f / 35f) * 60f));
                navEta = activeRouteEtaMinutes + " min";
                navRouteInfo = "HERE sin ruta · línea directa";
                updateNavigationTexts();
            }
            if (taxiInfoText != null) taxiInfoText.setText("OCUPADO · GPS servicio hacia recogida");
        });
    }

    private void updateLiveNavigation(Location loc) {
        if (activeService == null || mapView == null) return;
        if (routingToDestination && activeService.destinationLat != 0 && activeService.destinationLng != 0 && distanceMeters(loc.getLatitude(), loc.getLongitude(), new GeoPoint(activeService.destinationLat, activeService.destinationLng)) < 70) {
            completeActiveServiceAtDestination();
            return;
        }
        if (!routingToDestination && activeService.pickupLat != 0 && activeService.pickupLng != 0 && distanceMeters(loc.getLatitude(), loc.getLongitude(), new GeoPoint(activeService.pickupLat, activeService.pickupLng)) < 80) {
            routingToDestination = true;
            activeRoutePoints.clear();
            activeManeuvers.clear();
            clearRouteInsight();
            lastRouteFetchAt = 0;
            toast("Recogida alcanzada. Ruta al destino activada.");
            if (activeService.destinationLat != 0 && activeService.destinationLng != 0) fetchRouteToService(loc.getLatitude(), loc.getLongitude(), activeService.destinationLat, activeService.destinationLng);
        }
        if (followGpsBearing) mapView.setMapOrientation(-lastStableBearing);
        if (navSpeedText != null) navSpeedText.setText(Math.max(0, Math.round(loc.getSpeed() * 3.6f)) + "\nkm/h");
        updateRoadSafetyAlerts(loc);
        if (activeRoutePoints.isEmpty() && shouldFetchRoute()) {
            fetchRouteToService(loc.getLatitude(), loc.getLongitude(), currentTargetLat(), currentTargetLng());
        }
        updateVisibleRouteFromLocation(loc.getLatitude(), loc.getLongitude());
        if (!activeManeuvers.isEmpty()) {
            while (nextManeuverIndex < activeManeuvers.size() - 1 && distanceMeters(loc.getLatitude(), loc.getLongitude(), activeManeuvers.get(nextManeuverIndex).point) < 25) {
                nextManeuverIndex++;
            }
            NavManeuver maneuver = activeManeuvers.get(nextManeuverIndex);
            float distance = distanceMeters(loc.getLatitude(), loc.getLongitude(), maneuver.point);
            navInstruction = prettyInstruction(maneuver.instruction);
            navNext = "Después  " + maneuverArrow(maneuver.instruction);
            navDistance = formatRouteDistance(remainingRouteMeters(loc.getLatitude(), loc.getLongitude()));
            updateNavigationTexts();
        } else if (!activeRoutePoints.isEmpty()) {
            navDistance = formatRouteDistance(remainingRouteMeters(loc.getLatitude(), loc.getLongitude()));
            updateNavigationTexts();
        }
        if (!activeRoutePoints.isEmpty() && distanceToRouteMeters(loc.getLatitude(), loc.getLongitude()) > 90 && shouldFetchRoute()) {
            fetchRouteToService(loc.getLatitude(), loc.getLongitude(), currentTargetLat(), currentTargetLng());
        }
    }

    private void completeActiveServiceAtDestination() {
        if (activeService == null || completingActiveService) return;
        completingActiveService = true;
        ChatMessage completed = activeService;
        api.updateServiceStatus(completed.id, "done", (ok, error) -> runOnUiThread(() -> {
            api.setTaxiOccupied(safeTaxiNumber(), false, (ok2, error2) -> { });
            session.stopCarTaximeterFromMobileService();
            if (selectedTaxi != null) selectedTaxi.occupied = false;
            activeService = null;
            routingToDestination = false;
            completingActiveService = false;
            activeRoutePoints.clear();
            activeManeuvers.clear();
            clearRouteInsight();
            toast(error == null ? "Servicio hecho. Taxi libre." : "Servicio terminado localmente. Revisa conexión.");
            showMapScreen();
        }));
    }

    private void updateNavigationTexts() {
        if (navStreetText != null) navStreetText.setText(navInstruction);
        if (navNextText != null) navNextText.setText(navNext);
        if (navEtaText != null) navEtaText.setText(navEta);
        if (navMetaText != null) navMetaText.setText(navDistance + " · " + arrivalClock() + (navRouteInfo == null || navRouteInfo.isEmpty() ? "" : " · " + navRouteInfo));
    }

    private void clearRouteInsight() {
        navRouteInfo = "";
        activeRouteHasTolls = false;
        activeRouteTollInfo = "";
        activeRouteTrafficDelaySeconds = 0;
        activeRouteEtaMinutes = 7;
        routeFetchInFlight = false;
    }

    private void updateRoadSafetyAlerts(Location loc) {
        int speed = Math.max(0, Math.round(loc.getSpeed() * 3.6f));
        if (speed < 35) return;
        int estimatedLimit = estimateSpeedLimitKmh();
        if (estimatedLimit > 0 && speed >= estimatedLimit + 8) {
            speakRoadAlert("Atención, velocidad por encima del límite estimado de " + estimatedLimit + " kilómetros por hora");
        } else if (speed >= 115 && activeRouteTrafficDelaySeconds > 0) {
            speakRoadAlert("Atención a posibles controles y retenciones en ruta");
        }
    }

    private int estimateSpeedLimitKmh() {
        String text = ((navInstruction == null ? "" : navInstruction) + " " + (navNext == null ? "" : navNext)).toLowerCase(Locale.ROOT);
        if (text.contains("autopista") || text.contains("autovía") || text.contains("autovia") || text.matches(".*\\b(a|ap)-?\\d+.*")) return 120;
        if (text.contains("nacional") || text.matches(".*\\bn-?\\d+.*")) return 90;
        if (text.contains("calle") || text.contains("avenida") || text.contains("rotonda") || text.contains("glorieta")) return 50;
        return 50;
    }

    private void speakRoadAlert(String message) {
        if (message == null || message.trim().isEmpty()) return;
        long now = System.currentTimeMillis();
        if (message.equals(lastRoadAlert) && now - lastRoadAlertAt < 60000) return;
        if (now - lastRoadAlertAt < 20000) return;
        lastRoadAlert = message;
        lastRoadAlertAt = now;
        if (tts != null) tts.speak(message, TextToSpeech.QUEUE_ADD, null, "road-alert-" + now);
        if (taxiInfoText != null) taxiInfoText.setText("Aviso GPS · " + message);
    }

    private void updateVisibleRouteFromLocation(double lat, double lng) {
        if (activeRoutePoints.isEmpty() || serviceLine == null) return;
        int nearest = nearestRoutePointIndex(lat, lng);
        List<GeoPoint> visible = new ArrayList<>();
        visible.add(new GeoPoint(lat, lng));
        for (int i = nearest; i < activeRoutePoints.size(); i++) visible.add(activeRoutePoints.get(i));
        if (serviceLineShadow != null) serviceLineShadow.setPoints(visible);
        serviceLine.setPoints(visible);
    }

    private int nearestRoutePointIndex(double lat, double lng) {
        int nearest = 0;
        float nearestDistance = Float.MAX_VALUE;
        for (int i = 0; i < activeRoutePoints.size(); i++) {
            float d = distanceMeters(lat, lng, activeRoutePoints.get(i));
            if (d < nearestDistance) { nearestDistance = d; nearest = i; }
        }
        return nearest;
    }

    private String maneuverArrow(String instruction) {
        String i = instruction == null ? "" : instruction.toLowerCase(Locale.ROOT);
        if (i.contains("izquierda") || i.contains("left")) return "↰";
        if (i.contains("derecha") || i.contains("right")) return "↱";
        if (i.contains("rotonda") || i.contains("roundabout")) return "↻";
        if (i.contains("cambio") || i.contains("merge")) return "↗";
        return "↑";
    }

    private float distanceMeters(double lat, double lng, GeoPoint point) {
        float[] out = new float[1];
        Location.distanceBetween(lat, lng, point.getLatitude(), point.getLongitude(), out);
        return out[0];
    }

    private String formatRouteDistance(float meters) {
        if (meters < 1000) return Math.max(5, Math.round(meters / 5f) * 5) + " m";
        return String.format(Locale.getDefault(), "%.1f km", meters / 1000f);
    }

    private float remainingRouteMeters(double lat, double lng) {
        if (activeRoutePoints.isEmpty()) return 0f;
        int nearest = 0;
        float nearestDistance = Float.MAX_VALUE;
        for (int i = 0; i < activeRoutePoints.size(); i++) {
            float d = distanceMeters(lat, lng, activeRoutePoints.get(i));
            if (d < nearestDistance) { nearestDistance = d; nearest = i; }
        }
        float total = nearestDistance;
        for (int i = nearest; i < activeRoutePoints.size() - 1; i++) {
            GeoPoint a = activeRoutePoints.get(i);
            GeoPoint b = activeRoutePoints.get(i + 1);
            total += distanceMeters(a.getLatitude(), a.getLongitude(), b);
        }
        return total;
    }

    private float distanceToRouteMeters(double lat, double lng) {
        float min = Float.MAX_VALUE;
        for (GeoPoint p : activeRoutePoints) min = Math.min(min, distanceMeters(lat, lng, p));
        return min == Float.MAX_VALUE ? 0 : min;
    }

    private List<GeoPoint> decodeHerePolyline(String encoded) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";
        int[] index = {0};
        decodeUnsigned(encoded, index, chars);
        long header = decodeUnsigned(encoded, index, chars);
        int precision = (int) (header & 15);
        int thirdDim = (int) ((header >> 4) & 7);
        double factor = Math.pow(10, precision);
        long lat = 0, lng = 0;
        List<GeoPoint> points = new ArrayList<>();
        while (index[0] < encoded.length()) {
            lat += decodeSigned(encoded, index, chars);
            lng += decodeSigned(encoded, index, chars);
            if (thirdDim != 0) decodeSigned(encoded, index, chars);
            points.add(new GeoPoint(lat / factor, lng / factor));
        }
        return points;
    }

    private long decodeUnsigned(String encoded, int[] index, String chars) {
        long result = 0;
        int shift = 0;
        int value;
        do {
            value = chars.indexOf(encoded.charAt(index[0]++));
            result |= (long) (value & 31) << shift;
            shift += 5;
        } while ((value & 32) != 0 && index[0] < encoded.length());
        return result;
    }

    private long decodeSigned(String encoded, int[] index, String chars) {
        long value = decodeUnsigned(encoded, index, chars);
        return (value & 1) != 0 ? ~(value >> 1) : (value >> 1);
    }

    private String prettyInstruction(String instruction) {
        if (instruction == null || instruction.trim().isEmpty()) return "Sigue la ruta";
        String clean = instruction.replace("right", "Gira a la derecha").replace("left", "Gira a la izquierda").replace("straight", "Continúa recto");
        return clean.length() > 34 ? clean.substring(0, 34) : clean;
    }

    private void updateTaxiMarker(Taxi taxi) {
        MapView currentMap = mapView;
        if (currentMap == null || !currentMap.isAttachedToWindow() || taxi == null || taxi.latitude == 0 || taxi.longitude == 0) return;
        if (!"Propietario".equals(session.getRole()) && taxi.number == safeTaxiNumber()) {
            Marker ownMarker = taxiMarkers.remove(taxi.number);
            if (ownMarker != null) currentMap.getOverlays().remove(ownMarker);
            return;
        }
        Marker marker = taxiMarkers.get(taxi.number);
        if (marker == null) {
            try {
                marker = new Marker(currentMap);
            } catch (RuntimeException ignored) {
                return;
            }
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            taxiMarkers.put(taxi.number, marker);
            currentMap.getOverlays().add(marker);
        }
        marker.setPosition(new GeoPoint(taxi.latitude, taxi.longitude));
        marker.setTitle("🚕 Taxi " + taxi.number + " · " + taxi.driverName + (taxi.occupied ? " · OCUPADO" : ""));
        marker.setSnippet((taxi.online ? "En línea" : "Fuera de línea") + " · " + (taxi.occupied ? "Ocupado · " : "Libre · ") + taxi.speed + " km/h · " + taxi.direction);
        currentMap.invalidate();
        if ("Propietario".equals(session.getRole()) && !mapManuallyMoved && activeService == null) centerFleetOnMap();
    }

    private String directionFromBearing(float bearing) {
        String[] dirs = {"N", "NE", "E", "SE", "S", "SO", "O", "NO"};
        float normalized = ((bearing % 360f) + 360f) % 360f;
        return dirs[Math.round(normalized / 45f) % 8];
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
        double[] here = geocodeAddressHere(address);
        if (here[0] != 0 || here[1] != 0) return here;
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

    private double[] geocodeAddress(String street, String city) {
        String cleanStreet = normalizeAddressPart(street);
        String cleanCity = normalizeAddressPart(city);
        double[] here = geocodeAddressHereStructured(cleanStreet, cleanCity);
        if (here[0] != 0 || here[1] != 0) return here;
        return geocodeAddress(cleanStreet + ", " + cleanCity + ", Barcelona, Catalunya, España");
    }

    private double[] geocodeAddressHere(String address) {
        if (BuildConfig.HERE_API_KEY == null || BuildConfig.HERE_API_KEY.trim().isEmpty()) return new double[]{0, 0};
        try {
            String q = URLEncoder.encode(address + ", Barcelona, Catalunya", "UTF-8");
            JSONObject root = readJson("https://geocode.search.hereapi.com/v1/geocode?q=" + q + "&limit=5&in=countryCode:ESP&lang=es-es&apikey=" + BuildConfig.HERE_API_KEY);
            JSONArray items = root.optJSONArray("items");
            if (items != null && items.length() > 0) {
                JSONObject pos = items.getJSONObject(0).getJSONObject("position");
                return new double[]{pos.getDouble("lat"), pos.getDouble("lng")};
            }
        } catch (Exception ignored) { }
        return new double[]{0, 0};
    }

    private double[] geocodeAddressHereStructured(String street, String city) {
        if (BuildConfig.HERE_API_KEY == null || BuildConfig.HERE_API_KEY.trim().isEmpty()) return new double[]{0, 0};
        try {
            String qq = "street=" + URLEncoder.encode(street, "UTF-8")
                    + ";city=" + URLEncoder.encode(city, "UTF-8")
                    + ";county=" + URLEncoder.encode("Barcelona", "UTF-8")
                    + ";country=" + URLEncoder.encode("ESP", "UTF-8");
            JSONObject root = readJson("https://geocode.search.hereapi.com/v1/geocode?qq=" + qq + "&limit=5&lang=es-es&apikey=" + BuildConfig.HERE_API_KEY);
            JSONArray items = root.optJSONArray("items");
            if (items == null || items.length() == 0) return new double[]{0, 0};
            int fallback = 0;
            String wantedCity = cleanTown(city);
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.getJSONObject(i);
                JSONObject address = item.optJSONObject("address");
                if (address == null) continue;
                String resultCity = cleanTown(firstNonEmpty(address.optString("city", ""), address.optString("district", ""), address.optString("county", ""), address.optString("label", "")));
                String label = cleanTown(address.optString("label", ""));
                if (resultCity.equals(wantedCity) || label.contains(wantedCity)) {
                    JSONObject pos = item.getJSONObject("position");
                    return new double[]{pos.getDouble("lat"), pos.getDouble("lng")};
                }
            }
            JSONObject pos = items.getJSONObject(fallback).getJSONObject("position");
            return new double[]{pos.getDouble("lat"), pos.getDouble("lng")};
        } catch (Exception ignored) { }
        return new double[]{0, 0};
    }

    private String firstNonEmpty(String... values) {
        for (String value : values) if (value != null && !value.trim().isEmpty() && !"null".equals(value)) return value;
        return "";
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
        else if (lower.startsWith("carrer ")) v = v;
        else if (lower.startsWith("cr ")) v = "Carrer " + v.substring(3);
        else if (lower.startsWith("av. ")) v = "Avenida " + v.substring(4);
        else if (lower.startsWith("av ")) v = "Avenida " + v.substring(3);
        else if (lower.startsWith("pza. ")) v = "Plaza " + v.substring(5);
        else if (lower.startsWith("pl. ")) v = "Plaza " + v.substring(4);
        return v;
    }

    private String detectTariffForService(String pickupCity, String destinationCity, CheckBox airport, CheckBox moll, CheckBox specialNight) {
        String from = cleanTown(pickupCity);
        String to = cleanTown(destinationCity);
        if (!from.equals(to)) return isNightOrHolidayTariff() ? "T-7 urbana nocturna/festivos" : "T-6 urbana diurna";
        if (specialNight.isChecked() || isNightOrHolidayTariff()) return "T-2 urbana nocturna/festivos";
        return "T-1 urbana diurna";
    }

    private String cleanTown(String town) {
        return town == null ? "" : town.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private boolean isNightOrHolidayTariff() {
        java.util.Calendar c = java.util.Calendar.getInstance();
        int hour = c.get(java.util.Calendar.HOUR_OF_DAY);
        int day = c.get(java.util.Calendar.DAY_OF_WEEK);
        return hour >= 20 || hour < 8 || day == java.util.Calendar.SATURDAY || day == java.util.Calendar.SUNDAY;
    }

    private void selectTariffSpinner(Spinner spinner, String tariffName) {
        String target = tariffName == null ? "" : tariffName.toLowerCase(Locale.ROOT);
        for (int i = 0; i < spinner.getCount(); i++) {
            String item = String.valueOf(spinner.getItemAtPosition(i)).toLowerCase(Locale.ROOT);
            if ((target.contains("7") && item.contains("7")) || (target.contains("6") && item.contains("6")) || (target.contains("3") && item.contains("3")) || (target.contains("2") && item.contains("2")) || (target.contains("1") && item.contains("1"))) {
                spinner.setSelection(i);
                return;
            }
        }
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

    private View startBackground() {
        return new View(this) {
            private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);

            @Override protected void onDraw(Canvas canvas) {
                int w = getWidth();
                int h = getHeight();
                paint.setStyle(Paint.Style.FILL);
                android.graphics.LinearGradient bg = new android.graphics.LinearGradient(0, 0, 0, h, new int[]{Color.rgb(3, 12, 18), Color.rgb(4, 10, 16), Color.rgb(1, 5, 10)}, null, android.graphics.Shader.TileMode.CLAMP);
                paint.setShader(bg);
                canvas.drawRect(0, 0, w, h, paint);
                paint.setShader(null);

                paint.setColor(Color.argb(26, 255, 255, 255));
                for (int i = 0; i < 8; i++) {
                    float x = dp(32) + i * dp(58);
                    float top = dp(70) + (i % 3) * dp(28);
                    float bottom = h * .50f + (i % 2) * dp(34);
                    canvas.drawRoundRect(new android.graphics.RectF(x, top, x + dp(36), bottom), dp(8), dp(8), paint);
                    paint.setColor(Color.argb(28, 255, 199, 44));
                    for (float y = top + dp(26); y < bottom - dp(16); y += dp(38)) canvas.drawRoundRect(new android.graphics.RectF(x + dp(10), y, x + dp(25), y + dp(8)), dp(3), dp(3), paint);
                    paint.setColor(Color.argb(26, 255, 255, 255));
                }

                paint.setColor(Color.argb(90, 255, 199, 44));
                canvas.drawOval(new android.graphics.RectF(w * .60f, h * .32f, w * 1.04f, h * .50f), paint);
                paint.setColor(Color.argb(140, 15, 18, 22));
                canvas.drawRoundRect(new android.graphics.RectF(w * .67f, h * .35f, w * .94f, h * .41f), dp(8), dp(8), paint);
                paint.setColor(Color.argb(145, 255, 199, 44));
                paint.setTextAlign(Paint.Align.CENTER);
                paint.setTypeface(Typeface.DEFAULT_BOLD);
                paint.setTextSize(dp(29));
                canvas.drawText("TAXI", w * .805f, h * .395f, paint);

                stroke.setStyle(Paint.Style.STROKE);
                stroke.setStrokeWidth(dp(2));
                stroke.setColor(Color.argb(40, 255, 255, 255));
                canvas.drawLine(w * .14f, h * .82f, w * .88f, h * .58f, stroke);
                canvas.drawLine(w * .30f, h, w * .94f, h * .55f, stroke);
                paint.setColor(Color.argb(150, 0, 0, 0));
                canvas.drawRect(0, 0, w, h, paint);
                paint.setColor(Color.argb(80, 0, 0, 0));
                canvas.drawRect(0, h * .55f, w, h, paint);
            }
        };
    }

    private View startLogoMark() {
        return new View(this) {
            private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Path path = new Path();

            @Override protected void onDraw(Canvas canvas) {
                float w = getWidth();
                float h = getHeight();
                paint.setColor(YELLOW);
                paint.setStyle(Paint.Style.FILL);
                path.reset();
                path.addCircle(w / 2f, h * .36f, w * .38f, Path.Direction.CW);
                path.moveTo(w * .18f, h * .58f);
                path.quadTo(w / 2f, h * .98f, w * .82f, h * .58f);
                path.close();
                canvas.drawPath(path, paint);

                paint.setColor(Color.rgb(4, 11, 18));
                canvas.drawCircle(w / 2f, h * .36f, w * .27f, paint);
                paint.setColor(YELLOW);
                canvas.drawRoundRect(new android.graphics.RectF(w * .29f, h * .34f, w * .71f, h * .50f), dp(6), dp(6), paint);
                path.reset();
                path.moveTo(w * .36f, h * .34f);
                path.lineTo(w * .43f, h * .26f);
                path.lineTo(w * .58f, h * .26f);
                path.lineTo(w * .66f, h * .34f);
                path.close();
                canvas.drawPath(path, paint);
                paint.setColor(Color.rgb(4, 11, 18));
                canvas.drawCircle(w * .36f, h * .51f, dp(5), paint);
                canvas.drawCircle(w * .64f, h * .51f, dp(5), paint);
                paint.setColor(YELLOW);
                canvas.drawRect(w * .43f, h * .21f, w * .47f, h * .24f, paint);
                canvas.drawRect(w * .49f, h * .21f, w * .53f, h * .24f, paint);
                canvas.drawRect(w * .55f, h * .21f, w * .59f, h * .24f, paint);
            }
        };
    }

    private View startMenuCard(int iconType, String title, String desc, Runnable action) {
        LinearLayout card = row();
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(24), dp(18), dp(22), dp(18));
        card.setBackground(round(Color.argb(210, 15, 23, 31), 20, 1, Color.argb(120, 160, 169, 184)));
        card.setElevation(dp(10));
        card.setOnClickListener(v -> action.run());

        card.addView(startCardIcon(iconType), new LinearLayout.LayoutParams(dp(86), ViewGroup.LayoutParams.MATCH_PARENT));

        View separator = new View(this);
        separator.setBackgroundColor(Color.argb(105, 160, 169, 184));
        LinearLayout.LayoutParams sepLp = new LinearLayout.LayoutParams(dp(1), dp(82));
        sepLp.setMargins(dp(18), 0, dp(26), 0);
        card.addView(separator, sepLp);

        LinearLayout textBox = column();
        textBox.addView(text(title, 20, Color.WHITE, true));
        TextView subtitle = text(desc, 16, Color.rgb(185, 193, 207), false);
        subtitle.setLineSpacing(dp(2), 1.0f);
        textBox.addView(subtitle, wrapMT(8));
        card.addView(textBox, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView arrow = text("›", 46, YELLOW, true);
        arrow.setGravity(Gravity.CENTER);
        card.addView(arrow, new LinearLayout.LayoutParams(dp(34), ViewGroup.LayoutParams.MATCH_PARENT));
        return card;
    }

    private View startCardIcon(int type) {
        return new View(this) {
            private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

            @Override protected void onDraw(Canvas canvas) {
                float w = getWidth();
                float h = getHeight();
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(YELLOW);
                if (type == 0) drawBuilding(canvas, w, h); else drawPersonPlus(canvas, w, h);
            }

            private void drawBuilding(Canvas canvas, float w, float h) {
                float left = w * .24f;
                float top = h * .20f;
                float bw = w * .44f;
                float bh = h * .58f;
                canvas.drawRoundRect(new android.graphics.RectF(left, top, left + bw, top + bh), dp(6), dp(6), paint);
                canvas.drawRect(left - dp(10), top + bh - dp(4), left + bw + dp(24), top + bh + dp(4), paint);
                canvas.drawRect(left + bw + dp(10), top + h * .31f, left + bw + dp(18), top + bh, paint);
                paint.setColor(Color.rgb(9, 14, 22));
                for (int row = 0; row < 3; row++) {
                    for (int col = 0; col < 2; col++) {
                        float x = left + dp(12) + col * dp(18);
                        float y = top + dp(14) + row * dp(18);
                        canvas.drawRect(x, y, x + dp(10), y + dp(10), paint);
                    }
                }
                canvas.drawRect(left + bw * .38f, top + bh - dp(26), left + bw * .64f, top + bh, paint);
                paint.setColor(YELLOW);
            }

            private void drawPersonPlus(Canvas canvas, float w, float h) {
                canvas.drawCircle(w * .42f, h * .30f, dp(23), paint);
                canvas.drawRoundRect(new android.graphics.RectF(w * .18f, h * .54f, w * .66f, h * .86f), dp(22), dp(22), paint);
                canvas.drawRect(w * .68f, h * .58f, w * .92f, h * .68f, paint);
                canvas.drawRect(w * .75f, h * .50f, w * .85f, h * .77f, paint);
            }
        };
    }

    private View startDividerMark() {
        return new View(this) {
            private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

            @Override protected void onDraw(Canvas canvas) {
                paint.setColor(YELLOW);
                float base = getHeight() * .72f;
                float start = getWidth() / 2f - dp(28);
                for (int i = 0; i < 3; i++) {
                    float h = i == 1 ? dp(28) : dp(18);
                    canvas.drawRect(start + i * dp(20), base - h, start + i * dp(20) + dp(12), base, paint);
                }
                paint.setColor(Color.argb(90, 255, 199, 44));
                canvas.drawCircle(getWidth() / 2f, base - dp(8), dp(32), paint);
            }
        };
    }

    private LinearLayout.LayoutParams startCardLp(int top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(128));
        lp.setMargins(0, dp(top), 0, 0);
        return lp;
    }

    private View taxiIllustration() {
        return new View(this) {
            private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Path path = new Path();
            private final long startedAt = System.currentTimeMillis();

            @Override protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                int w = getWidth();
                int h = getHeight();
                if (w <= 0 || h <= 0) return;

                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.argb(28, 255, 255, 255));
                canvas.drawRoundRect(new android.graphics.RectF(dp(8), dp(24), w - dp(8), h - dp(18)), dp(28), dp(28), paint);

                float[] lanes = {h * .30f, h * .48f, h * .66f};
                for (float y : lanes) drawRoad(canvas, w, y);

                long elapsed = System.currentTimeMillis() - startedAt;
                drawMovingTaxi(canvas, w, lanes[0], elapsed, 0, .92f);
                drawMovingTaxi(canvas, w, lanes[1], elapsed, 1350, 1.05f);
                drawMovingTaxi(canvas, w, lanes[2], elapsed, 2700, .86f);
                drawMovingTaxi(canvas, w, lanes[0], elapsed, 4050, .72f);
                drawMovingTaxi(canvas, w, lanes[2], elapsed, 5200, 1.02f);

                postInvalidateDelayed(16);
            }

            private void drawRoad(Canvas canvas, int w, float y) {
                stroke.setStyle(Paint.Style.STROKE);
                stroke.setStrokeCap(Paint.Cap.ROUND);
                stroke.setStrokeWidth(dp(18));
                stroke.setColor(Color.argb(34, 255, 255, 255));
                canvas.drawLine(dp(18), y, w - dp(18), y, stroke);
                stroke.setStrokeWidth(dp(3));
                stroke.setColor(Color.argb(95, 255, 255, 255));
                for (int x = dp(28); x < w; x += dp(46)) canvas.drawLine(x, y, x + dp(20), y, stroke);
            }

            private void drawMovingTaxi(Canvas canvas, int w, float laneY, long elapsed, long delay, float scale) {
                float travel = w + dp(150);
                float progress = ((elapsed + delay) % 6500L) / 6500f;
                float x = -dp(90) + travel * progress;
                float bob = (float) Math.sin((elapsed + delay) / 180.0) * dp(2);
                canvas.save();
                canvas.translate(x, laneY - dp(24) + bob);
                canvas.scale(scale, scale, dp(42), dp(24));
                drawTaxi(canvas);
                canvas.restore();
            }

            private void drawTaxi(Canvas canvas) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.argb(85, 0, 0, 0));
                canvas.drawOval(new android.graphics.RectF(dp(8), dp(42), dp(82), dp(55)), paint);

                paint.setColor(YELLOW);
                canvas.drawRoundRect(new android.graphics.RectF(dp(8), dp(19), dp(82), dp(45)), dp(10), dp(10), paint);
                path.reset();
                path.moveTo(dp(25), dp(19));
                path.lineTo(dp(36), dp(5));
                path.lineTo(dp(60), dp(5));
                path.lineTo(dp(72), dp(19));
                path.close();
                canvas.drawPath(path, paint);

                paint.setColor(Color.rgb(165, 220, 245));
                canvas.drawRoundRect(new android.graphics.RectF(dp(38), dp(9), dp(57), dp(20)), dp(4), dp(4), paint);
                canvas.drawRoundRect(new android.graphics.RectF(dp(59), dp(10), dp(70), dp(20)), dp(4), dp(4), paint);

                paint.setColor(Color.rgb(25, 25, 25));
                for (int i = 0; i < 4; i++) {
                    for (int j = 0; j < 2; j++) {
                        paint.setColor((i + j) % 2 == 0 ? Color.rgb(20, 20, 20) : YELLOW);
                        canvas.drawRect(dp(30 + i * 6), dp(29 + j * 6), dp(36 + i * 6), dp(35 + j * 6), paint);
                    }
                }

                paint.setColor(Color.rgb(35, 35, 35));
                canvas.drawCircle(dp(23), dp(46), dp(9), paint);
                canvas.drawCircle(dp(67), dp(46), dp(9), paint);
                paint.setColor(Color.rgb(230, 230, 230));
                canvas.drawCircle(dp(23), dp(46), dp(4), paint);
                canvas.drawCircle(dp(67), dp(46), dp(4), paint);

                paint.setColor(Color.WHITE);
                canvas.drawRect(dp(38), dp(1), dp(55), dp(6), paint);
                paint.setColor(Color.rgb(255, 85, 70));
                canvas.drawRoundRect(new android.graphics.RectF(dp(78), dp(28), dp(84), dp(40)), dp(3), dp(3), paint);
                paint.setColor(Color.WHITE);
                canvas.drawRoundRect(new android.graphics.RectF(dp(5), dp(28), dp(11), dp(38)), dp(3), dp(3), paint);
            }
        };
    }

    private TextView subtitle(String s) { TextView t = text(s, 14, SECONDARY, false); t.setPadding(dp(22), dp(10), dp(22), 0); return t; }
    private LinearLayout column() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout row() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); return l; }
    private ScrollView scroll(View v) { ScrollView s = new ScrollView(this); s.setBackgroundColor(BG); s.addView(v); return s; }
    private LinearLayout card() { LinearLayout c = column(); c.setPadding(dp(20), dp(20), dp(20), dp(20)); c.setBackground(round(CARD, 26, 1, Color.rgb(232, 237, 246))); c.setElevation(dp(7)); return c; }
    private LinearLayout.LayoutParams cardLp() { LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); lp.setMargins(dp(18), dp(18), dp(18), dp(22)); return lp; }
    private TextView text(String s, int sp, int color, boolean bold) { TextView t = new TextView(this); t.setText(s); t.setTextSize(sp); t.setTextColor(color); t.setIncludeFontPadding(true); if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD); return t; }
    private TextView circleText(String s, int bg, int color, int size) { TextView t = text(s, size > 60 ? 28 : 20, color, true); t.setGravity(Gravity.CENTER); t.setBackground(round(bg, size / 2, 0, bg)); t.setLayoutParams(new LinearLayout.LayoutParams(dp(size), dp(size))); return t; }
    private EditText field(String label, String hint, boolean password) { EditText e = new EditText(this); e.setHint(hint); e.setTextColor(TEXT); e.setHintTextColor(Color.rgb(137, 151, 172)); e.setTextSize(15); e.setSingleLine(true); e.setPadding(dp(14), 0, dp(14), 0); e.setBackground(round(Color.rgb(248, 250, 252), 16, 1, LINE)); e.setInputType(password ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD : InputType.TYPE_CLASS_TEXT); return e; }
    private CheckBox checkbox(String label) { CheckBox c = new CheckBox(this); c.setText(label); c.setTextColor(TEXT); c.setTextSize(14); return c; }
    private Spinner spinner(String[] values) { Spinner s = new Spinner(this); ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, values); adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); s.setAdapter(adapter); s.setBackgroundResource(R.drawable.bg_field); return s; }
    private Button button(String s, int bg, int color) { Button b = new Button(this); b.setText(s); b.setTextColor(color); b.setTextSize(16); b.setTypeface(Typeface.DEFAULT, Typeface.BOLD); b.setAllCaps(false); b.setMinHeight(0); b.setMinWidth(0); b.setPadding(dp(14), 0, dp(14), 0); b.setBackground(round(bg, 18, 0, bg)); b.setElevation(dp(4)); return b; }
    private Button roundSmallButton(String s, int bg, int color) { Button b = button(s, bg, color); b.setTextSize(20); b.setLayoutParams(new LinearLayout.LayoutParams(dp(58), dp(58))); b.setBackground(round(bg, 22, 0, bg)); return b; }
    private android.graphics.drawable.GradientDrawable round(int color, int radius, int stroke, int strokeColor) { android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable(); g.setColor(color); g.setCornerRadius(dp(radius)); if (stroke > 0) g.setStroke(dp(stroke), strokeColor); return g; }
    private android.graphics.drawable.GradientDrawable headerGradient() { android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT, new int[]{NAVY_DARK, NAVY, Color.rgb(0, 92, 102)}); g.setCornerRadius(0); return g; }
    private android.graphics.drawable.GradientDrawable navGradient() { android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT, new int[]{Color.rgb(0, 125, 112), Color.rgb(0, 166, 150)}); g.setCornerRadius(dp(22)); return g; }
    private boolean empty(EditText e) { return e.getText().toString().trim().isEmpty(); }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
    private void showError(String title, Exception error) { new AlertDialog.Builder(this).setTitle(title).setMessage(error == null ? "Error desconocido" : error.getMessage()).setPositiveButton("Cerrar", null).show(); }
    private String now() { return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()); }
    private int safeTaxiNumber() {
        try {
            if ("Propietario".equals(session.getRole())) {
                if (!session.isAdminCountsAsTaxi()) return 0;
                String adminTaxi = session.getAdminTaxiNumber();
                return adminTaxi == null || adminTaxi.trim().isEmpty() ? 0 : Integer.parseInt(adminTaxi.trim());
            }
            return Integer.parseInt(session.getTaxiNumber());
        } catch (Exception e) { return 0; }
    }
    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + .5f); }
    private LinearLayout.LayoutParams matchH(int h) { return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(h)); }
    private LinearLayout.LayoutParams matchHMT(int h, int mt) { LinearLayout.LayoutParams lp = matchH(h); lp.setMargins(0, dp(mt), 0, 0); return lp; }
    private LinearLayout.LayoutParams matchWMT(int mt) { LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); lp.setMargins(0, dp(mt), 0, 0); return lp; }
    private LinearLayout.LayoutParams mt(int mt) { LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); lp.setMargins(0, dp(mt), 0, 0); return lp; }
    private LinearLayout.LayoutParams wrapMT(int mt) { LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT); lp.setMargins(0, dp(mt), 0, 0); return lp; }
    private FrameLayout.LayoutParams match() { return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT); }
}
