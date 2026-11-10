import { useEffect, useState } from 'react';
import {
  CartesianGrid,
  Line,
  LineChart,
  ReferenceLine,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import Badge from '../components/Badge';
import CalculationSteps from '../components/CalculationSteps';
import Card from '../components/Card';
import DataTable from '../components/DataTable';
import EvidenceList from '../components/EvidenceList';
import ErrorState from '../components/ErrorState';
import Loading from '../components/Loading';
import api from '../services/api';
import {
  CHART_AXIS,
  CHART_GRID_COLOR,
  CHART_TOOLTIP_STYLE,
  formatMinutes,
  formatTime,
} from '../services/format';
import useApi from '../services/useApi';

export default function DelayInvestigation() {
  const [routeId, setRouteId] = useState('');
  const [tripId, setTripId] = useState('');
  const [showWorkedExample, setShowWorkedExample] = useState(false);

  const routes = useApi(() => api.getRoutes(), []);
  const trips = useApi(
    () => (routeId ? api.getRouteTrips(routeId) : Promise.resolve([])),
    [routeId]
  );
  const investigation = useApi(
    () => (tripId ? api.getTripInvestigation(tripId) : Promise.resolve(null)),
    [tripId]
  );
  const worked = useApi(
    () => (showWorkedExample ? api.getWorkedExample() : Promise.resolve(null)),
    [showWorkedExample]
  );

  // Selecting a different route invalidates the chosen trip.
  useEffect(() => {
    setTripId('');
  }, [routeId]);

  const inv = investigation.data;

  const chartData = (inv?.stopDelays || []).map((stop) => ({
    sequence: stop.stopSequence,
    label: `${stop.stopSequence}. ${stop.stopName}`,
    delay: stop.delayMin,
  }));

  const stopColumns = [
    { key: 'stopSequence', header: '#', numeric: true },
    { key: 'stopName', header: 'Stop' },
    {
      key: 'scheduledArrival',
      header: 'Scheduled',
      render: (s) => formatTime(s.scheduledArrival),
    },
    {
      key: 'actualArrival',
      header: 'Actual',
      render: (s) => formatTime(s.actualArrival),
    },
    {
      key: 'delayMin',
      header: 'Delay',
      numeric: true,
      render: (s) => (s.delayMin == null ? '—' : formatMinutes(s.delayMin)),
    },
    {
      key: 'delayClass',
      header: 'Class',
      render: (s) => <Badge value={s.delayClass} />,
    },
    {
      key: 'note',
      header: 'Note',
      render: (s) => s.note || (s.observed ? '' : s.dataQualityFlag) || '—',
    },
  ];

  const propagationColumns = [
    {
      key: 'mechanism',
      header: 'Mechanism',
      render: (p) => <Badge value={p.mechanism} />,
    },
    {
      key: 'confidence',
      header: 'Confidence',
      render: (p) => <Badge value={p.confidence} />,
    },
    { key: 'affected_trip_id', header: 'Affected trip' },
    { key: 'affected_route_id', header: 'Affected route' },
    {
      key: 'propagated_delay_min',
      header: 'Propagated',
      numeric: true,
      render: (p) => formatMinutes(p.propagated_delay_min),
    },
    {
      key: 'lag_min',
      header: 'Lag',
      numeric: true,
      render: (p) => formatMinutes(p.lag_min),
    },
  ];

  return (
    <>
      <p className="page-intro">
        Compare the planned and actual movements of a single trip, see how its
        delay changed stop by stop, and inspect which downstream services that
        delay could have reached. Every propagation relationship shows its
        mechanism, the arithmetic behind it and the evidence supporting it, so a
        claim can be checked rather than taken on trust.
      </p>

      <div className="toolbar">
        <div className="field">
          <label htmlFor="inv-route">Route</label>
          <select
            id="inv-route"
            value={routeId}
            onChange={(event) => setRouteId(event.target.value)}
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
          <label htmlFor="inv-trip">Trip</label>
          <select
            id="inv-trip"
            value={tripId}
            onChange={(event) => setTripId(event.target.value)}
            disabled={!routeId || trips.loading}
          >
            <option value="">
              {routeId ? 'Select a trip…' : 'Choose a route first'}
            </option>
            {(trips.data || []).map((trip) => (
              <option key={trip.tripId} value={trip.tripId}>
                {trip.tripId} — departs {formatTime(trip.scheduledStartTime)}
              </option>
            ))}
          </select>
        </div>
        <div className="toolbar-spacer" />
        <button
          type="button"
          className="btn btn-secondary"
          onClick={() => setShowWorkedExample((value) => !value)}
        >
          {showWorkedExample ? 'Hide worked example' : 'Show worked example'}
        </button>
      </div>

      {routes.error && <ErrorState error={routes.error} onRetry={routes.reload} />}

      {showWorkedExample && (
        <Card
          title={worked.data?.title || 'Worked propagation example'}
          subtitle="A hand-checkable walk-through of the full calculation chain"
        >
          {worked.loading ? (
            <Loading label="Loading worked example…" />
          ) : worked.error ? (
            <ErrorState error={worked.error} onRetry={worked.reload} />
          ) : worked.data ? (
            <WorkedExample data={worked.data} />
          ) : null}
        </Card>
      )}

      {!tripId && (
        <Card title="Select a trip">
          <p className="page-intro" style={{ marginBottom: 0 }}>
            Choose a route and then a trip above to investigate its delay
            profile.
          </p>
        </Card>
      )}

      {tripId && investigation.loading && <Loading label="Investigating trip…" />}
      {tripId && investigation.error && (
        <ErrorState error={investigation.error} onRetry={investigation.reload} />
      )}

      {inv && (
        <>
          <Card
            title={`Trip ${inv.tripId} — ${inv.routeName}`}
            subtitle={`Vehicle ${inv.vehicleId || 'unassigned'} · ${inv.serviceDate}`}
          >
            <div className="grid grid-kpi">
              <div className="kpi">
                <div className="kpi-label">Max delay</div>
                <div className="kpi-value">{formatMinutes(inv.maxDelayMin)}</div>
                <div className="kpi-hint">
                  <Badge value={inv.delayClass} />
                </div>
              </div>
              <div className="kpi">
                <div className="kpi-label">Final delay</div>
                <div className="kpi-value">{formatMinutes(inv.finalDelayMin)}</div>
                <div className="kpi-hint">at the last observed stop</div>
              </div>
              <div className="kpi">
                <div className="kpi-label">Recovery</div>
                <div className="kpi-value">{formatMinutes(inv.recoveryMin)}</div>
                <div className="kpi-hint">
                  {inv.recovered ? 'recovered to on time' : 'did not recover'}
                </div>
              </div>
              <div className="kpi">
                <div className="kpi-label">Data confidence</div>
                <div className="kpi-value" style={{ fontSize: '1.1rem' }}>
                  <Badge value={inv.dataConfidence} />
                </div>
                <div className="kpi-hint">
                  {inv.stopsObserved} of {inv.stopsTotal} stops observed
                  {inv.stopsMissing > 0 && `, ${inv.stopsMissing} missing`}
                </div>
              </div>
            </div>

            <p className="page-intro" style={{ marginTop: '1rem', marginBottom: 0 }}>
              {inv.explanation}
            </p>
          </Card>

          <Card
            title="Delay at each stop"
            subtitle="Gaps in the line are stops with no usable observation"
          >
            {chartData.length > 0 && (
              <div className="chart-box">
                <ResponsiveContainer width="100%" height="100%">
                  <LineChart data={chartData}>
                    <CartesianGrid stroke={CHART_GRID_COLOR} />
                    <XAxis dataKey="sequence" {...CHART_AXIS} />
                    <YAxis {...CHART_AXIS} />
                    <Tooltip
                      {...CHART_TOOLTIP_STYLE}
                      formatter={(value) =>
                        value == null ? 'no observation' : `${value} min`
                      }
                      labelFormatter={(value) =>
                        chartData.find((row) => row.sequence === value)?.label ??
                        `Stop ${value}`
                      }
                    />
                    <ReferenceLine y={0} stroke="#22c55e" strokeDasharray="4 4" />
                    <Line
                      type="monotone"
                      dataKey="delay"
                      name="Delay (min)"
                      stroke="#22d3ee"
                      strokeWidth={2}
                      connectNulls={false}
                      dot={{ r: 3 }}
                    />
                  </LineChart>
                </ResponsiveContainer>
              </div>
            )}
            <DataTable
              columns={stopColumns}
              rows={inv.stopDelays || []}
              rowKey={(row) => row.stopSequence}
              emptyMessage="No movements recorded for this trip."
            />
          </Card>

          <Card
            title="Downstream delay propagation"
            subtitle="Services this trip's delay could have reached, with evidence"
          >
            {(!inv.propagation || inv.propagation.length === 0) ? (
              <p className="page-intro" style={{ marginBottom: 0 }}>
                No downstream propagation was identified for this trip. Either it
                ran close enough to schedule, or no vehicle continuity, transfer
                connection or shared-stop relationship linked it to a later
                service inside the propagation window.
              </p>
            ) : (
              <>
                <DataTable
                  columns={propagationColumns}
                  rows={inv.propagation}
                  rowKey={(row, index) => `${row.affected_trip_id}-${index}`}
                />
                <div style={{ marginTop: '1rem' }}>
                  {inv.propagation.map((link, index) => (
                    <div
                      key={`${link.affected_trip_id}-detail-${index}`}
                      className="calc-step"
                      style={{ marginBottom: '0.6rem' }}
                    >
                      <div className="calc-step-body" style={{ width: '100%' }}>
                        <div className="calc-step-desc">
                          <Badge value={link.mechanism} />{' '}
                          <Badge value={link.confidence} /> → trip{' '}
                          <strong>{link.affected_trip_id}</strong> on route{' '}
                          {link.affected_route_id}
                          {link.incident_id && (
                            <>
                              {' '}
                              · corroborated by incident{' '}
                              <strong>{link.incident_id}</strong>
                            </>
                          )}
                        </div>
                        {link.calculation && (
                          <div className="calc-formula">{link.calculation}</div>
                        )}
                        <EvidenceList evidence={link.evidence} />
                      </div>
                    </div>
                  ))}
                </div>
                <p className="chart-legend-note">
                  SHARED_STOP links describe a correlation at a shared location,
                  not a demonstrated cause. VEHICLE_CONTINUITY links rest on a
                  physical constraint and carry the highest confidence.
                </p>
              </>
            )}
          </Card>

          {inv.affectedConnections?.length > 0 && (
            <Card
              title="Affected connecting services"
              subtitle="Transfer opportunities that depend on this trip"
            >
              <DataTable
                columns={[
                  { key: 'connection_id', header: 'Connection' },
                  { key: 'interchange_stop_id', header: 'Interchange' },
                  { key: 'connecting_route_id', header: 'Connecting route' },
                  {
                    key: 'available_transfer_min',
                    header: 'Available',
                    numeric: true,
                    render: (c) =>
                      c.available_transfer_min == null
                        ? '—'
                        : `${c.available_transfer_min} min`,
                  },
                  {
                    key: 'required_buffer_min',
                    header: 'Required',
                    numeric: true,
                    render: (c) => `${c.required_buffer_min} min`,
                  },
                  {
                    key: 'status',
                    header: 'Status',
                    render: (c) => <Badge value={c.status} />,
                  },
                  {
                    key: 'transferring_passengers',
                    header: 'Passengers',
                    numeric: true,
                  },
                ]}
                rows={inv.affectedConnections}
                rowKey={(row) => row.connection_id}
              />
            </Card>
          )}

          {inv.relatedIncidents?.length > 0 && (
            <Card
              title="Operator incidents on this route"
              subtitle="Annotations that may explain the delay"
            >
              <DataTable
                columns={[
                  { key: 'incidentId', header: 'ID' },
                  { key: 'incidentType', header: 'Type' },
                  {
                    key: 'severity',
                    header: 'Severity',
                    render: (i) => <Badge value={i.severity} />,
                  },
                  {
                    key: 'startTime',
                    header: 'From',
                    render: (i) => formatTime(i.startTime),
                  },
                  {
                    key: 'endTime',
                    header: 'To',
                    render: (i) => formatTime(i.endTime),
                  },
                  { key: 'description', header: 'Description' },
                ]}
                rows={inv.relatedIncidents}
                rowKey={(row) => row.incidentId}
              />
            </Card>
          )}
        </>
      )}
    </>
  );
}

/** Renders the five-step worked example returned by the API. */
function WorkedExample({ data }) {
  const propagation = data.step3PropagationToNextService || {};
  const connection = data.step4ConnectionRisk || {};
  const impact = data.step5PassengerImpact || {};

  return (
    <div>
      <h3>Step 1 — Delay at each stop</h3>
      <DataTable
        columns={[
          { key: 'stop', header: 'Stop' },
          { key: 'scheduled', header: 'Scheduled' },
          { key: 'actual', header: 'Actual' },
          {
            key: 'delayMin',
            header: 'Delay',
            numeric: true,
            render: (row) => `${row.delayMin} min`,
          },
          {
            key: 'delayClass',
            header: 'Class',
            render: (row) => <Badge value={row.delayClass} />,
          },
          { key: 'calculation', header: 'Calculation' },
        ]}
        rows={data.step1DelayAtEachStop || []}
        rowKey={(row) => row.stop}
      />

      <h3 style={{ marginTop: '1.2rem' }}>Step 2 — How the delay developed</h3>
      <DataTable
        columns={[
          { key: 'segment', header: 'Segment' },
          {
            key: 'delayBeforeMin',
            header: 'Before',
            numeric: true,
            render: (row) => `${row.delayBeforeMin} min`,
          },
          {
            key: 'delayAfterMin',
            header: 'After',
            numeric: true,
            render: (row) => `${row.delayAfterMin} min`,
          },
          {
            key: 'changeMin',
            header: 'Change',
            numeric: true,
            render: (row) => `${row.changeMin > 0 ? '+' : ''}${row.changeMin} min`,
          },
          { key: 'interpretation', header: 'Interpretation' },
        ]}
        rows={data.step2DelayProgression || []}
        rowKey={(row) => row.segment}
      />

      <h3 style={{ marginTop: '1.2rem' }}>
        Step 3 — Propagation to the next service{' '}
        <Badge value={propagation.mechanism} />{' '}
        <Badge value={propagation.confidence} />
      </h3>
      <CalculationSteps steps={propagation.steps} />
      <EvidenceList evidence={propagation.evidence} />
      {propagation.whyNotMerelyCorrelation && (
        <div className="notice" style={{ marginTop: '0.8rem' }}>
          <strong>Why this is causal, not just correlated: </strong>
          {propagation.whyNotMerelyCorrelation}
        </div>
      )}

      <h3 style={{ marginTop: '1.2rem' }}>
        Step 4 — Connection risk <Badge value={connection.status} />
      </h3>
      <CalculationSteps steps={connection.steps} />
      <p className="page-intro" style={{ marginTop: '0.6rem' }}>
        {connection.explanation}
      </p>

      <h3 style={{ marginTop: '1.2rem' }}>
        Step 5 — Passenger impact
        <span className="estimate-tag">est</span>
      </h3>
      <CalculationSteps steps={impact.steps} />
      <div className="notice notice-warn" style={{ marginTop: '0.8rem' }}>
        {impact.estimateCaveat}
      </div>

      <div className="notice" style={{ marginTop: '0.8rem' }}>
        <strong>Conclusion: </strong>
        {data.conclusion}
      </div>
    </div>
  );
}
