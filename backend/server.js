const express = require('express');
const cors = require('cors');
const http = require('http');
const WebSocket = require('ws');
const fs = require('fs');
const path = require('path');

const app = express();
const server = http.createServer(app);
const wss = new WebSocket.Server({ server });
const PORT = process.env.PORT || 3000;

app.use(cors());
app.use(express.json());

const DATA_FILE = path.join(__dirname, 'data.json');
const data = loadData();
const companies = data.companies;
const accessRequests = data.accessRequests;
const taxis = data.taxis;
const history = data.history;
const walkieStates = data.walkieStates || [];
const messages = data.messages || [];
const googleAds = data.googleAds || {};

function loadData() {
  try {
    if (fs.existsSync(DATA_FILE)) return JSON.parse(fs.readFileSync(DATA_FILE, 'utf8'));
  } catch (error) {
    console.warn('No se pudo cargar data.json:', error.message);
  }
  return {
    companies: [{ name: 'Taxi Central', identifier: 'central', password: '123456', ownerPassword: 'admin123' }],
    accessRequests: [],
    taxis: [],
    history: [],
    walkieStates: [],
    messages: []
  };
}

function saveData() {
  try {
    fs.writeFileSync(DATA_FILE, JSON.stringify({ companies, accessRequests, taxis, history, walkieStates, messages, googleAds }, null, 2));
  } catch (error) {
    console.warn('No se pudo guardar data.json:', error.message);
  }
}

function addHistory(text) {
  history.unshift({ text, at: new Date().toISOString() });
  if (history.length > 100) history.pop();
  saveData();
}

function broadcast(type, payload) {
  const message = JSON.stringify({ type, payload });
  wss.clients.forEach(client => {
    if (client.readyState === WebSocket.OPEN) client.send(message);
  });
}

app.get('/health', (req, res) => res.json({ ok: true, service: 'TaxiLink backend' }));

function baseUrl(req) {
  const configuredUrl = process.env.PUBLIC_BASE_URL;
  if (configuredUrl && /^https?:\/\//.test(configuredUrl)) return configuredUrl;
  return `${req.protocol}://${req.get('host')}`;
}

function requiredGoogleAdsEnv() {
  return ['GOOGLE_ADS_CLIENT_ID', 'GOOGLE_ADS_CLIENT_SECRET', 'GOOGLE_ADS_DEVELOPER_TOKEN'].filter(name => !process.env[name]);
}

app.get('/google-ads/connect', (req, res) => {
  const missing = requiredGoogleAdsEnv();
  if (missing.length) return res.status(500).send(`Faltan variables: ${missing.join(', ')}`);
  const company = String(req.query.company || 'default');
  const params = new URLSearchParams({
    client_id: process.env.GOOGLE_ADS_CLIENT_ID,
    redirect_uri: `${baseUrl(req)}/google-ads/oauth2callback`,
    response_type: 'code',
    scope: 'https://www.googleapis.com/auth/adwords',
    access_type: 'offline',
    prompt: 'consent',
    state: Buffer.from(JSON.stringify({ company })).toString('base64url')
  });
  res.redirect(`https://accounts.google.com/o/oauth2/v2/auth?${params.toString()}`);
});

app.get('/google-ads/oauth2callback', async (req, res) => {
  const missing = requiredGoogleAdsEnv();
  if (missing.length) return res.status(500).send(`Faltan variables: ${missing.join(', ')}`);
  try {
    const state = JSON.parse(Buffer.from(String(req.query.state || ''), 'base64url').toString('utf8'));
    const company = state.company || 'default';
    const body = new URLSearchParams({
      code: String(req.query.code || ''),
      client_id: process.env.GOOGLE_ADS_CLIENT_ID,
      client_secret: process.env.GOOGLE_ADS_CLIENT_SECRET,
      redirect_uri: `${baseUrl(req)}/google-ads/oauth2callback`,
      grant_type: 'authorization_code'
    });
    const response = await fetch('https://oauth2.googleapis.com/token', { method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, body });
    const token = await response.json();
    if (!response.ok || token.error) return res.status(400).send(token.error_description || token.error || 'No se pudo conectar Google Ads');
    googleAds[company] = {
      refreshToken: token.refresh_token || googleAds[company]?.refreshToken || '',
      accessToken: token.access_token || '',
      expiresAt: Date.now() + Number(token.expires_in || 3600) * 1000
    };
    saveData();
    res.send('Google Ads conectado correctamente. Ya puedes volver a TaxiLink y pulsar Actualizar datos reales.');
  } catch (error) {
    res.status(500).send(`Error conectando Google Ads: ${error.message}`);
  }
});

async function googleAdsAccessToken(company) {
  const auth = googleAds[company];
  if (!auth || !auth.refreshToken) throw new Error('Cuenta Google Ads no conectada');
  if (auth.accessToken && auth.expiresAt && Date.now() < auth.expiresAt - 60000) return auth.accessToken;
  const body = new URLSearchParams({
    client_id: process.env.GOOGLE_ADS_CLIENT_ID,
    client_secret: process.env.GOOGLE_ADS_CLIENT_SECRET,
    refresh_token: auth.refreshToken,
    grant_type: 'refresh_token'
  });
  const response = await fetch('https://oauth2.googleapis.com/token', { method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, body });
  const token = await response.json();
  if (!response.ok || token.error) throw new Error(token.error_description || token.error || 'No se pudo refrescar Google Ads');
  auth.accessToken = token.access_token;
  auth.expiresAt = Date.now() + Number(token.expires_in || 3600) * 1000;
  saveData();
  return auth.accessToken;
}

app.get('/google-ads/summary', async (req, res) => {
  const missing = requiredGoogleAdsEnv();
  if (missing.length) return res.status(500).json({ error: `Faltan variables: ${missing.join(', ')}` });
  const company = String(req.query.company || 'default');
  const customerId = String(req.query.customerId || '').replace(/\D/g, '');
  if (!customerId) return res.status(400).json({ error: 'Falta customerId de Google Ads' });
  try {
    const accessToken = await googleAdsAccessToken(company);
    const query = `SELECT metrics.clicks, metrics.phone_calls, metrics.impressions, metrics.cost_micros FROM campaign WHERE segments.date DURING LAST_30_DAYS`;
    const response = await fetch(`https://googleads.googleapis.com/v24/customers/${customerId}/googleAds:searchStream`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${accessToken}`,
        'developer-token': process.env.GOOGLE_ADS_DEVELOPER_TOKEN,
        ...(process.env.GOOGLE_ADS_LOGIN_CUSTOMER_ID ? { 'login-customer-id': process.env.GOOGLE_ADS_LOGIN_CUSTOMER_ID.replace(/\D/g, '') } : {})
      },
      body: JSON.stringify({ query })
    });
    const payload = await response.json();
    if (!response.ok) return res.status(response.status).json({ error: payload.error?.message || 'Google Ads rechazó la consulta', detail: payload });
    let clicks = 0;
    let calls = 0;
    let impressions = 0;
    let costMicros = 0;
    for (const chunk of payload) {
      for (const result of chunk.results || []) {
        const metrics = result.metrics || {};
        clicks += Number(metrics.clicks || 0);
        calls += Number(metrics.phoneCalls || metrics.phone_calls || 0);
        impressions += Number(metrics.impressions || 0);
        costMicros += Number(metrics.costMicros || metrics.cost_micros || 0);
      }
    }
    res.json({ connected: true, customerId, period: 'LAST_30_DAYS', clicks, calls, impressions, costMicros });
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
});

app.post('/companies', (req, res) => {
  const { name, identifier, password, ownerPassword } = req.body;
  if (!name || !identifier || !password || !ownerPassword) return res.status(400).json({ error: 'Faltan datos' });
  const existing = companies.find(c => c.identifier === identifier);
  if (existing) {
    if (existing.ownerPassword !== ownerPassword) return res.status(409).json({ error: 'El identificador ya existe con otra contraseña de propietario' });
    existing.name = name;
    existing.password = password;
    existing.ownerPassword = ownerPassword;
    saveData();
    return res.json({ company: { name: existing.name, identifier: existing.identifier }, updated: true });
  }
  const company = { name, identifier, password, ownerPassword };
  companies.push(company);
  addHistory(`Empresa creada: ${name}`);
  res.status(201).json({ company: { name, identifier } });
});

app.post('/owner-login', (req, res) => {
  const { identifier, ownerPassword } = req.body;
  const company = companies.find(c => c.identifier === identifier && c.ownerPassword === ownerPassword);
  if (!company) return res.status(401).json({ error: 'Contraseña de propietario incorrecta' });
  addHistory(`Propietario inició sesión en ${company.name}`);
  res.json({ company: { name: company.name, identifier: company.identifier }, role: 'Propietario' });
});

app.post('/login', (req, res) => {
  const { identifier, password, taxiNumber } = req.body;
  const company = companies.find(c => c.identifier === identifier && c.password === password);
  if (!company) return res.status(401).json({ error: 'Credenciales incorrectas' });
  addHistory(`Taxi ${taxiNumber || 'propietario'} inició sesión en ${company.name}`);
  res.json({ company: { name: company.name, identifier: company.identifier }, role: taxiNumber ? 'Conductor' : 'Propietario' });
});

app.post('/access-requests', (req, res) => {
  const { identifier, password, taxiNumber, driverName, deviceId } = req.body;
  const company = companies.find(c => c.identifier === identifier);
  if (!company) return res.status(404).json({ error: 'La empresa no existe. Primero créala desde el móvil del propietario.' });
  if (company.password !== password) return res.status(401).json({ error: 'Contraseña de conductores incorrecta' });
  if (!taxiNumber || !driverName || !deviceId) return res.status(400).json({ error: 'Faltan datos del conductor' });
  let request = accessRequests.find(r => r.deviceId === deviceId && r.identifier === identifier && r.status === 'pending');
  if (!request) {
    request = { id: `${Date.now()}-${Math.random().toString(16).slice(2)}`, identifier, taxiNumber: Number(taxiNumber), driverName, deviceId, status: 'pending', createdAt: new Date().toISOString() };
    accessRequests.push(request);
    addHistory(`${driverName} pidió acceso como Taxi ${taxiNumber}`);
    broadcast('access-request', request);
    saveData();
  }
  res.status(201).json({ request });
});

app.get('/access-requests', (req, res) => {
  const identifier = req.query.identifier;
  res.json({ requests: accessRequests.filter(r => (!identifier || r.identifier === identifier) && r.status === 'pending') });
});

app.get('/access-requests/:id', (req, res) => {
  const request = accessRequests.find(r => r.id === req.params.id);
  if (!request) return res.status(404).json({ error: 'Solicitud no encontrada' });
  res.json({ request });
});

app.post('/access-requests/:id/approve', (req, res) => {
  const request = accessRequests.find(r => r.id === req.params.id);
  if (!request) return res.status(404).json({ error: 'Solicitud no encontrada' });
  request.status = req.body.approved === false ? 'rejected' : 'approved';
  request.reviewedAt = new Date().toISOString();
  if (request.status === 'approved') {
    const existing = taxis.find(t => t.identifier === request.identifier && t.number === request.taxiNumber);
    if (!existing) taxis.push({ identifier: request.identifier, number: request.taxiNumber, driverName: request.driverName, online: false, speed: 0, direction: '--', latitude: null, longitude: null, lastUpdate: null });
    else existing.driverName = request.driverName;
  }
  addHistory(`${request.driverName} fue ${request.status === 'approved' ? 'aprobado' : 'rechazado'} como Taxi ${request.taxiNumber}`);
  broadcast('access-reviewed', request);
  saveData();
  res.json({ request });
});

app.get('/taxis', (req, res) => {
  const identifier = req.query.identifier;
  const now = Date.now();
  const visible = taxis.filter(t => !identifier || t.identifier === identifier).map(t => ({ ...t, online: t.lastUpdate ? now - new Date(t.lastUpdate).getTime() < 45000 : false }));
  res.json({ taxis: visible });
});

app.post('/taxis/:number/location', (req, res) => {
  const identifier = req.body.identifier || req.query.identifier || 'central';
  let taxi = taxis.find(t => t.identifier === identifier && t.number === Number(req.params.number));
  if (!taxi) {
    taxi = { identifier, number: Number(req.params.number), driverName: req.body.driverName || 'Conductor', online: true };
    taxis.push(taxi);
  }
  Object.assign(taxi, { driverName: req.body.driverName || taxi.driverName, latitude: Number(req.body.latitude), longitude: Number(req.body.longitude), speed: Number(req.body.speed || 0), direction: req.body.direction || '--', lastUpdate: new Date().toISOString(), online: true });
  addHistory(`Taxi ${taxi.number} actualizó ubicación`);
  broadcast('taxi-location', taxi);
  saveData();
  res.json({ taxi });
});

app.get('/history', (req, res) => res.json({ history }));

app.get('/messages', (req, res) => {
  const identifier = req.query.identifier;
  const visible = messages.filter(m => !identifier || m.identifier === identifier).slice(-100);
  res.json({ messages: visible });
});

app.post('/messages', (req, res) => {
  const { identifier, sender, role, text } = req.body;
  if (!identifier || !sender || !text) return res.status(400).json({ error: 'Faltan datos del mensaje' });
  const message = { id: `${Date.now()}-${Math.random().toString(16).slice(2)}`, identifier, sender, role: role || 'Usuario', type: 'text', text, createdAt: new Date().toISOString() };
  messages.push(message);
  saveData();
  broadcast('message', message);
  res.status(201).json({ message });
});

app.post('/services', (req, res) => {
  const { identifier, sender, role, serviceType, tariff, pickup, destination, fixedPrice, estimatedPrice } = req.body;
  if (!identifier || !sender || !serviceType || !tariff || !pickup || !destination) return res.status(400).json({ error: 'Faltan datos del servicio' });
  const service = { serviceType, tariff, pickup, destination, fixedPrice: !!fixedPrice, estimatedPrice: estimatedPrice || '' };
  const message = {
    id: `${Date.now()}-${Math.random().toString(16).slice(2)}`,
    identifier,
    sender,
    role: role || 'Usuario',
    type: 'service',
    text: `Nuevo servicio: ${pickup} → ${destination}`,
    service,
    createdAt: new Date().toISOString()
  };
  messages.push(message);
  saveData();
  broadcast('service', message);
  res.status(201).json({ message });
});

app.post('/walkie/start', (req, res) => {
  const { identifier, taxiNumber, driverName } = req.body;
  if (!identifier || !taxiNumber || !driverName) return res.status(400).json({ error: 'Faltan datos de walkie' });
  let state = walkieStates.find(w => w.identifier === identifier);
  if (!state) {
    state = { identifier };
    walkieStates.push(state);
  }
  state.taxiNumber = Number(taxiNumber);
  state.driverName = driverName;
  state.speaking = true;
  state.updatedAt = new Date().toISOString();
  saveData();
  broadcast('walkie-start', state);
  res.json({ walkie: state });
});

app.post('/walkie/stop', (req, res) => {
  const { identifier, taxiNumber } = req.body;
  const state = walkieStates.find(w => w.identifier === identifier);
  if (state && (!taxiNumber || Number(taxiNumber) === Number(state.taxiNumber))) {
    state.speaking = false;
    state.updatedAt = new Date().toISOString();
    saveData();
    broadcast('walkie-stop', state);
  }
  res.json({ walkie: state || { identifier, speaking: false } });
});

app.get('/walkie', (req, res) => {
  const identifier = req.query.identifier;
  const state = walkieStates.find(w => w.identifier === identifier);
  if (!state) return res.json({ walkie: { identifier, speaking: false } });
  const active = state.speaking && state.updatedAt && Date.now() - new Date(state.updatedAt).getTime() < 30000;
  if (state.speaking && !active) {
    state.speaking = false;
    saveData();
  }
  res.json({ walkie: { ...state, speaking: active } });
});

wss.on('connection', ws => {
  ws.send(JSON.stringify({ type: 'welcome', payload: 'Conectado a TaxiLink WS' }));
  ws.on('message', raw => {
    try {
      const message = JSON.parse(raw.toString());
      if (message.type === 'walkie-start' || message.type === 'walkie-stop') broadcast(message.type, message.payload || {});
      if (message.type === 'taxi-location') broadcast('taxi-location', message.payload || {});
    } catch (error) {
      ws.send(JSON.stringify({ type: 'error', payload: 'Mensaje inválido' }));
    }
  });
});

server.listen(PORT, () => console.log(`TaxiLink backend escuchando en http://localhost:${PORT}`));
