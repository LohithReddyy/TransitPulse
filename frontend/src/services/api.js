/**
 * TransitPulse API client.
 *
 * Every backend endpoint returns the envelope
 *   { success, message, data, timestamp }
 * so `unwrap` returns `data` and lets errors surface with the server's own
 * message, which is written to be actionable (for example, telling the user
 * to run the Python engine when analytics are missing).
 */
import axios from 'axios';

const baseURL = import.meta.env?.VITE_API_BASE_URL || '';

export const client = axios.create({
  baseURL,
  timeout: 30000,
  headers: { 'Content-Type': 'application/json' },
});

/** Turn any failure into an Error carrying the most useful message available. */
function toError(error) {
  const response = error?.response;
  if (response?.data) {
    const { message, error: errorName, fieldErrors } = response.data;
    const parts = [];
    if (message) parts.push(message);
    if (fieldErrors) {
      parts.push(
        Object.entries(fieldErrors)
          .map(([field, problem]) => `${field}: ${problem}`)
          .join('; ')
      );
    }
    const err = new Error(parts.join(' ') || errorName || 'Request failed');
    err.status = response.status;
    err.fieldErrors = fieldErrors;
    return err;
  }
  if (error?.code === 'ECONNABORTED') {
    return new Error('The request timed out. Is the backend running on port 8080?');
  }
  const err = new Error(
    error?.message ||
      'Could not reach the TransitPulse API. Start the backend with: cd backend && mvn spring-boot:run'
  );
  err.status = 0;
  return err;
}

async function unwrap(promise) {
  try {
    const response = await promise;
    const body = response.data;
    if (body && typeof body === 'object' && 'data' in body) {
      return body.data;
    }
    return body;
  } catch (error) {
    throw toError(error);
  }
}

const get = (url, params) => unwrap(client.get(url, { params }));
const post = (url, body) => unwrap(client.post(url, body));
const put = (url, body) => unwrap(client.put(url, body));
const del = (url) => unwrap(client.delete(url));

export const api = {
  // ---------------- health ----------------
  health: () => get('/api/health'),

  // ---------------- routes ----------------
  getRoutes: (params) => get('/api/routes', params),
  getRoute: (routeId) => get(`/api/routes/${routeId}`),
  getRouteStops: (routeId) => get(`/api/routes/${routeId}/stops`),
  getRouteTrips: (routeId) => get(`/api/routes/${routeId}/trips`),

  // ---------------- stops ----------------
  getStops: (params) => get('/api/stops', params),
  getStop: (stopId) => get(`/api/stops/${stopId}`),
  getStopPassengerVolumes: (stopId) =>
    get(`/api/stops/${stopId}/passenger-volumes`),

  // ---------------- trips ----------------
  getTrips: (params) => get('/api/trips', params),
  getTrip: (tripId) => get(`/api/trips/${tripId}`),
  getScheduledMovements: (tripId) =>
    get(`/api/trips/${tripId}/scheduled-movements`),
  getActualMovements: (tripId) => get(`/api/trips/${tripId}/actual-movements`),
  getTripDelays: (tripId) => get(`/api/trips/${tripId}/delays`),
  getTripInvestigation: (tripId) => get(`/api/trips/${tripId}/investigation`),
  getVehicleChain: (tripId) => get(`/api/trips/${tripId}/vehicle-chain`),

  // ---------------- analytics ----------------
  getDashboard: () => get('/api/analytics/dashboard'),
  getRoutePerformance: () => get('/api/analytics/route-performance'),
  getStopPerformance: () => get('/api/analytics/stop-performance'),
  getTripSummary: () => get('/api/analytics/trip-summary'),
  getDelayDistribution: () => get('/api/analytics/delay-distribution'),
  getPropagation: (params) => get('/api/analytics/propagation', params),
  getConnectionRisk: (params) => get('/api/analytics/connection-risk', params),
  getPassengerImpact: () => get('/api/analytics/passenger-impact'),
  getSensitivity: () => get('/api/analytics/sensitivity'),
  getRecovery: () => get('/api/analytics/recovery'),
  getWorkedExample: () => get('/api/analytics/worked-example'),
  getValidation: () => get('/api/analytics/validation'),
  getEngineStatus: () => get('/api/analytics/engine-status'),

  // ---------------- incidents ----------------
  getIncidents: (params) => get('/api/incidents', params),
  getIncident: (id) => get(`/api/incidents/${id}`),
  getDiversions: () => get('/api/incidents/diversions'),
  createIncident: (body) => post('/api/incidents', body),
  updateIncident: (id, body) => put(`/api/incidents/${id}`, body),
  deleteIncident: (id) => del(`/api/incidents/${id}`),

  // ---------------- configuration ----------------
  getConfig: (params) => get('/api/config', params),
  getConfigGrouped: () => get('/api/config/grouped'),
  updateConfig: (key, configValue) =>
    put(`/api/config/${key}`, { configValue }),

  // ---------------- reference data ----------------
  getVehicles: () => get('/api/vehicles'),
  getConnections: (params) => get('/api/connections', params),
};

export default api;
