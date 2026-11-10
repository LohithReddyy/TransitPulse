import { useState } from 'react';
import Badge from '../components/Badge';
import Card from '../components/Card';
import DataTable from '../components/DataTable';
import ErrorState from '../components/ErrorState';
import Loading from '../components/Loading';
import api from '../services/api';
import { formatDateTime } from '../services/format';
import useApi from '../services/useApi';

const TYPES = [
  'SIGNAL_FAILURE',
  'ROAD_WORKS',
  'TRAFFIC_CONGESTION',
  'VEHICLE_BREAKDOWN',
  'DIVERSION',
  'WEATHER',
  'SPECIAL_EVENT',
  'OTHER',
];
const SEVERITIES = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];
const STATUSES = ['OPEN', 'MONITORING', 'CLOSED'];

const EMPTY_FORM = {
  incidentType: 'TRAFFIC_CONGESTION',
  routeId: '',
  stopId: '',
  startTime: '',
  endTime: '',
  severity: 'MEDIUM',
  description: '',
  isDiversion: 'N',
  status: 'OPEN',
  reportedBy: 'operations',
  skippedStopId: '',
  replacementStopId: '',
};

export default function Incidents() {
  const [form, setForm] = useState(EMPTY_FORM);
  const [editingId, setEditingId] = useState(null);
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState(null);
  const [message, setMessage] = useState(null);
  const [statusFilter, setStatusFilter] = useState('');

  const incidents = useApi(
    () => api.getIncidents({ status: statusFilter || undefined }),
    [statusFilter]
  );
  const routes = useApi(() => api.getRoutes(), []);
  const stops = useApi(() => api.getStops(), []);

  const update = (field) => (event) =>
    setForm((current) => ({ ...current, [field]: event.target.value }));

  const resetForm = () => {
    setForm(EMPTY_FORM);
    setEditingId(null);
    setSaveError(null);
  };

  const startEdit = (incident) => {
    setEditingId(incident.incidentId);
    setSaveError(null);
    setMessage(null);
    setForm({
      incidentType: incident.incidentType || 'OTHER',
      routeId: incident.routeId || '',
      stopId: incident.stopId || '',
      startTime: (incident.startTime || '').slice(0, 16),
      endTime: (incident.endTime || '').slice(0, 16),
      severity: incident.severity || 'MEDIUM',
      description: incident.description || '',
      isDiversion: incident.isDiversion || 'N',
      status: incident.status || 'OPEN',
      reportedBy: incident.reportedBy || 'operations',
      skippedStopId: '',
      replacementStopId: '',
    });
  };

  const submit = async (event) => {
    event.preventDefault();
    setSaving(true);
    setSaveError(null);
    setMessage(null);

    // The API expects ISO-8601 with seconds; datetime-local omits them.
    const withSeconds = (value) =>
      !value ? undefined : value.length === 16 ? `${value}:00` : value;

    const payload = {
      ...form,
      stopId: form.stopId || undefined,
      startTime: withSeconds(form.startTime),
      endTime: withSeconds(form.endTime),
      skippedStopId: form.skippedStopId || undefined,
      replacementStopId: form.replacementStopId || undefined,
    };

    try {
      if (editingId) {
        await api.updateIncident(editingId, payload);
        setMessage(`Incident ${editingId} updated.`);
      } else {
        const created = await api.createIncident(payload);
        setMessage(`Incident ${created.incidentId} created.`);
      }
      resetForm();
      incidents.reload();
    } catch (error) {
      setSaveError(error);
    } finally {
      setSaving(false);
    }
  };

  const remove = async (incident) => {
    setMessage(null);
    setSaveError(null);
    try {
      await api.deleteIncident(incident.incidentId);
      setMessage(`Incident ${incident.incidentId} deleted.`);
      if (editingId === incident.incidentId) resetForm();
      incidents.reload();
    } catch (error) {
      setSaveError(error);
    }
  };

  const columns = [
    { key: 'incidentId', header: 'ID' },
    { key: 'incidentType', header: 'Type', render: (i) => i.incidentType },
    { key: 'routeId', header: 'Route' },
    { key: 'stopId', header: 'Stop', render: (i) => i.stopId || '—' },
    {
      key: 'startTime',
      header: 'Start',
      render: (i) => formatDateTime(i.startTime),
    },
    { key: 'endTime', header: 'End', render: (i) => formatDateTime(i.endTime) },
    {
      key: 'severity',
      header: 'Severity',
      render: (i) => <Badge value={i.severity} />,
    },
    {
      key: 'isDiversion',
      header: 'Diversion',
      render: (i) => (i.isDiversion === 'Y' ? <Badge value="info" label="yes" /> : 'no'),
    },
    { key: 'status', header: 'Status', render: (i) => <Badge value={i.status} /> },
    { key: 'description', header: 'Description' },
    {
      key: 'actions',
      header: '',
      render: (i) => (
        <span style={{ display: 'flex', gap: '0.35rem' }}>
          <button
            type="button"
            className="btn btn-secondary btn-sm"
            onClick={(event) => {
              event.stopPropagation();
              startEdit(i);
            }}
          >
            Edit
          </button>
          <button
            type="button"
            className="btn btn-danger btn-sm"
            onClick={(event) => {
              event.stopPropagation();
              remove(i);
            }}
          >
            Delete
          </button>
        </span>
      ),
    },
  ];

  return (
    <>
      <p className="page-intro">
        Operator annotations describing what happened on the network. These are
        the human evidence the propagation engine uses to raise a relationship
        from correlation to a defensible cause, so recording them accurately
        improves every downstream analysis. Diversions also tell the engine
        which planned stops must be excluded from schedule comparison.
      </p>

      {message && <div className="notice">{message}</div>}
      {saveError && (
        <ErrorState
          error={saveError}
          title="Could not save the incident"
          onRetry={() => setSaveError(null)}
        />
      )}

      <Card
        title={editingId ? `Edit incident ${editingId}` : 'Create a disruption annotation'}
        subtitle="Required fields are marked with an asterisk"
        actions={
          editingId ? (
            <button type="button" className="btn btn-secondary btn-sm" onClick={resetForm}>
              Cancel edit
            </button>
          ) : null
        }
      >
        <form onSubmit={submit}>
          <div className="grid grid-3">
            <div className="field">
              <label htmlFor="inc-type">Incident type *</label>
              <select
                id="inc-type"
                value={form.incidentType}
                onChange={update('incidentType')}
                required
              >
                {TYPES.map((t) => (
                  <option key={t} value={t}>
                    {t.replace(/_/g, ' ')}
                  </option>
                ))}
              </select>
            </div>

            <div className="field">
              <label htmlFor="inc-route">Affected route *</label>
              <select
                id="inc-route"
                value={form.routeId}
                onChange={update('routeId')}
                required
              >
                <option value="">Select a route…</option>
                {(routes.data || []).map((route) => (
                  <option key={route.routeId} value={route.routeId}>
                    {route.routeId} — {route.routeName}
                  </option>
                ))}
              </select>
            </div>

            <div className="field">
              <label htmlFor="inc-stop">Affected stop</label>
              <select id="inc-stop" value={form.stopId} onChange={update('stopId')}>
                <option value="">Whole route / not stop specific</option>
                {(stops.data || []).map((stop) => (
                  <option key={stop.stopId} value={stop.stopId}>
                    {stop.stopId} — {stop.stopName}
                  </option>
                ))}
              </select>
            </div>

            <div className="field">
              <label htmlFor="inc-start">Start time *</label>
              <input
                id="inc-start"
                type="datetime-local"
                value={form.startTime}
                onChange={update('startTime')}
                required
              />
            </div>

            <div className="field">
              <label htmlFor="inc-end">End time</label>
              <input
                id="inc-end"
                type="datetime-local"
                value={form.endTime}
                onChange={update('endTime')}
              />
              <div className="field-hint">Leave empty while the incident is ongoing.</div>
            </div>

            <div className="field">
              <label htmlFor="inc-severity">Severity</label>
              <select
                id="inc-severity"
                value={form.severity}
                onChange={update('severity')}
              >
                {SEVERITIES.map((s) => (
                  <option key={s} value={s}>
                    {s}
                  </option>
                ))}
              </select>
            </div>

            <div className="field">
              <label htmlFor="inc-diversion">Is this a diversion?</label>
              <select
                id="inc-diversion"
                value={form.isDiversion}
                onChange={update('isDiversion')}
              >
                <option value="N">No</option>
                <option value="Y">Yes — stops were not served as planned</option>
              </select>
            </div>

            <div className="field">
              <label htmlFor="inc-status">Status</label>
              <select id="inc-status" value={form.status} onChange={update('status')}>
                {STATUSES.map((s) => (
                  <option key={s} value={s}>
                    {s}
                  </option>
                ))}
              </select>
            </div>

            <div className="field">
              <label htmlFor="inc-reporter">Reported by</label>
              <input
                id="inc-reporter"
                type="text"
                value={form.reportedBy}
                onChange={update('reportedBy')}
              />
            </div>
          </div>

          {form.isDiversion === 'Y' && (
            <div className="grid grid-2">
              <div className="field">
                <label htmlFor="inc-skipped">Stop not served</label>
                <select
                  id="inc-skipped"
                  value={form.skippedStopId}
                  onChange={update('skippedStopId')}
                >
                  <option value="">Select the skipped stop…</option>
                  {(stops.data || []).map((stop) => (
                    <option key={stop.stopId} value={stop.stopId}>
                      {stop.stopId} — {stop.stopName}
                    </option>
                  ))}
                </select>
                <div className="field-hint">
                  Needed so the engine excludes this stop from schedule comparison
                  instead of recording a false delay.
                </div>
              </div>
              <div className="field">
                <label htmlFor="inc-replacement">Replacement stop served</label>
                <select
                  id="inc-replacement"
                  value={form.replacementStopId}
                  onChange={update('replacementStopId')}
                >
                  <option value="">None</option>
                  {(stops.data || []).map((stop) => (
                    <option key={stop.stopId} value={stop.stopId}>
                      {stop.stopId} — {stop.stopName}
                    </option>
                  ))}
                </select>
              </div>
            </div>
          )}

          <div className="field">
            <label htmlFor="inc-description">Description</label>
            <textarea
              id="inc-description"
              value={form.description}
              onChange={update('description')}
              placeholder="What happened, and what was done about it"
            />
          </div>

          <div style={{ display: 'flex', gap: '0.5rem' }}>
            <button type="submit" className="btn" disabled={saving}>
              {saving
                ? 'Saving…'
                : editingId
                  ? 'Save changes'
                  : 'Create incident'}
            </button>
            <button
              type="button"
              className="btn btn-secondary"
              onClick={resetForm}
              disabled={saving}
            >
              Reset
            </button>
          </div>
        </form>
      </Card>

      <div className="toolbar" style={{ marginTop: '1rem' }}>
        <div className="field">
          <label htmlFor="inc-filter">Filter by status</label>
          <select
            id="inc-filter"
            value={statusFilter}
            onChange={(event) => setStatusFilter(event.target.value)}
          >
            <option value="">All statuses</option>
            {STATUSES.map((s) => (
              <option key={s} value={s}>
                {s}
              </option>
            ))}
          </select>
        </div>
      </div>

      <Card
        title="Recorded incidents"
        subtitle={
          incidents.data ? `${incidents.data.length} incident(s)` : undefined
        }
      >
        {incidents.loading ? (
          <Loading label="Loading incidents…" />
        ) : incidents.error ? (
          <ErrorState error={incidents.error} onRetry={incidents.reload} />
        ) : (
          <DataTable
            columns={columns}
            rows={incidents.data || []}
            rowKey={(row) => row.incidentId}
            emptyMessage="No incidents recorded."
            emptyHint="Use the form above to annotate a disruption."
          />
        )}
      </Card>
    </>
  );
}
