# TaxiLink

TaxiLink es un proyecto Android nativo en Java para una empresa de taxis. Funciona en modo local sin claves externas, usa WebView con OpenStreetMap/Leaflet desde `assets/map.html` y deja un backend Node.js opcional preparado para futuras ubicaciones en tiempo real y walkie visual.

## Estructura

- `app/src/main/AndroidManifest.xml`: permisos, actividad principal y configuración de la app.
- `app/src/main/java/com/taxilink/app`: código Java de la aplicación.
- Mapa nativo Android con OpenStreetMap mediante `osmdroid`, sin WebView, sin Google Maps y sin claves.
- `app/src/main/res`: colores, estilos y drawables redondeados.
- `backend`: servidor opcional Express + WebSocket.

## Compilar APK

1. Abre la carpeta `TaxiLink` en Android Studio o AIDE compatible con Gradle.
2. Deja que Gradle sincronice el proyecto.
3. Compila con `gradle assembleDebug` o desde Android Studio con `Build > Build APK(s)`.
4. El APK debug se genera en `app/build/outputs/apk/debug/app-debug.apk`.

Si usas AIDE y no reconoce Gradle 8, puedes crear un proyecto Android Java vacío y copiar `app/src/main` manteniendo el paquete `com.taxilink.app`.

## Permisos

- `INTERNET`: cargar tiles de OpenStreetMap en WebView.
- `ACCESS_FINE_LOCATION` y `ACCESS_COARSE_LOCATION`: mostrar ubicación actual si el usuario concede permiso.
- `ACCESS_BACKGROUND_LOCATION`: preparado para ampliación futura.
- `RECORD_AUDIO`: walkie-talkie visual preparado para voz real futura.
- `POST_NOTIFICATIONS`: Android 13+.
- `FOREGROUND_SERVICE`: preparado para seguimiento en primer plano.

## Funcionamiento real con varios móviles

TaxiLink funciona como una app tipo WhatsApp: los móviles no escriben IP ni necesitan estar en la misma WiFi. Todos se conectan a un servidor central público configurado en:

`app/src/main/java/com/taxilink/app/ApiConfig.java`

```java
public static final String SERVER_URL = "https://taxilink-api.tudominio.com";
```

Para que funcione con datos móviles/4G/5G desde cualquier sitio, ese backend debe estar desplegado en Internet con HTTPS. WhatsApp funciona así: no conecta móvil con móvil directamente, todos pasan por servidores centrales.

Flujo real:

1. Propietario crea empresa con contraseña de conductores y contraseña única de propietario.
2. Conductor pone nombre, identificador, contraseña de conductores y número de taxi.
3. La solicitud llega al servidor central.
4. Propietario abre `Solicitudes de acceso` y aprueba.
5. El conductor aprobado envía GPS real, velocidad real y dirección real al servidor.
6. Propietario y conductores ven la flota desde cualquier conexión a Internet.

## Backend opcional en PC/servidor

También puedes ejecutar el backend Node.js en un ordenador o servidor accesible por todos.

1. En el PC/servidor ejecuta:

```bash
cd backend
npm install
npm start
```

2. Busca la IP del PC en la red WiFi. Ejemplo: `192.168.1.50`.
3. En la app, en `Crear empresa` o `Iniciar sesión conductor`, escribe el servidor así: `http://192.168.1.50:3000`.
4. El propietario crea la empresa con dos claves: contraseña de conductores y contraseña única de propietario.
5. Cada conductor entra con su nombre, identificador de empresa, contraseña de conductores y número de taxi.
6. El conductor queda en `Esperando aprobación`.
7. El propietario abre `Panel propietario > Solicitudes de acceso` y aprueba o rechaza.
8. Si se aprueba, el conductor entra al mapa y su móvil envía GPS real, velocidad real y dirección real al backend.
9. El propietario y otros móviles ven los taxis conectados desde el backend.

En emulador Android usa `http://10.0.2.2:3000`. En móviles reales no uses `10.0.2.2`; usa la IP del PC o dominio del servidor.

## Funciones locales/simuladas

- Crear empresa con `SharedPreferences`.
- Entrar como conductor y guardar número de taxi.
- Panel propietario.
- Lista y detalle de taxis.
- La versión actual ya no inventa ubicación para la flota conectada: los taxis aparecen cuando un móvil aprobado envía GPS real.
- La velocidad sale de `Location.getSpeed()` y se convierte a km/h.
- La dirección sale del bearing GPS del móvil.
- Mapa nativo OpenStreetMap con `osmdroid`, sin WebView, sin Google Maps ni claves.
- Walkie-talkie visual al mantener pulsado el micrófono.
- Perfil y configuración con cierre de sesión.

## Backend opcional

El backend no es obligatorio para que la app Android funcione.

Para probarlo:

```bash
cd backend
npm install
npm start
```

Endpoints incluidos:

- `POST /companies`: crear empresa.
- `POST /owner-login`: iniciar sesión como propietario con contraseña única.
- `POST /login`: iniciar sesión.
- `POST /access-requests`: conductor solicita entrar.
- `GET /access-requests?identifier=empresa`: propietario lista solicitudes pendientes.
- `POST /access-requests/:id/approve`: propietario aprueba o rechaza.
- `GET /taxis`: listar taxis.
- `POST /taxis/:number/location`: actualizar ubicación.
- `GET /history`: historial de conexiones.
- WebSocket en `ws://localhost:3000` para `taxi-location`, `walkie-start` y `walkie-stop`.

Para conectarlo en Android, añade una clase de red que envíe/reciba JSON y actualice `TaxiRepository` y `map.html` con `WebView.evaluateJavascript()`.

## Cambiar colores o logo

- Colores XML: `app/src/main/res/values/colors.xml`.
- Colores usados por código: constantes al inicio de `MainActivity.java`.
- Logo actual: construido con texto y formas nativas en `showStartScreen()`.
- Iconos: emojis limpios para máxima compatibilidad sin librerías externas.

## Ampliar de 6 taxis a más vehículos

Edita `MockTaxiProvider.java` y añade más instancias de `Taxi`. La lista, detalle y navegación funcionan con cualquier cantidad de taxis. Para el mapa, actualiza también el arreglo inicial de `taxis` en `app/src/main/assets/map.html` o envía posiciones dinámicas con `updateTaxi(number, lat, lng, online)` desde Android.
