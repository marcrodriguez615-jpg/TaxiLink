const express = require('express');
const cors = require('cors');
const http = require('http');
const WebSocket = require('ws');

const app = express();
const server = http.createServer(app);
const wss = new WebSocket.Server({ server });
const PORT = process.env.PORT || 3000;

app.use(cors());
app.use(express.json());

const companies = [{ name: 'Taxi Central', identifier: 'central', password: '123456', ownerPassword: 'admin123' }];
const accessRequests = [];
const taxis = [];
const history = [];

function addHistory(text) {
  history.unshift({ text, at: new Date().toISOString() });
  if (history.length > 100) history.pop();
}

function broadcast(type, payload) {
  const message = JSON.stringify({ type, payload });
  wss.clients.forEach(client => {
    if (client.readyState === WebSocket.OPEN) client.send(message);
  });
}

app.get('/health', (req, res) => res.json({ ok: true, service: 'TaxiLink backend' }));

app.post('/companies', (req, res) => {
  const { name, identifier, password, ownerPassword } = req.body;
  if (!name || !identifier || !password || !ownerPassword) return res.status(400).json({ error: 'Faltan datos' });
  if (companies.some(c => c.identifier === identifier)) return res.status(409).json({ error: 'Identificador ya existe' });
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
  const company = companies.find(c => c.identifier === identifier && c.password === password);
  if (!company) return res.status(401).json({ error: 'Empresa o contraseña incorrecta' });
  if (!taxiNumber || !driverName || !deviceId) return res.status(400).json({ error: 'Faltan datos del conductor' });
  let request = accessRequests.find(r => r.deviceId === deviceId && r.identifier === identifier && r.status === 'pending');
  if (!request) {
    request = { id: `${Date.now()}-${Math.random().toString(16).slice(2)}`, identifier, taxiNumber: Number(taxiNumber), driverName, deviceId, status: 'pending', createdAt: new Date().toISOString() };
    accessRequests.push(request);
    addHistory(`${driverName} pidió acceso como Taxi ${taxiNumber}`);
    broadcast('access-request', request);
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
  res.json({ taxi });
});

app.get('/history', (req, res) => res.json({ history }));

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
