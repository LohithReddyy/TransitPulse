import { useState } from 'react';
import {
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import Badge from '../components/Badge';
import Card from '../components/Card';
import DataTable from '../components/DataTable';
import ErrorState from '../components/ErrorState';
import KpiCard from '../components/KpiCard';
import Loading from '../components/Loading';
import api from '../services/api';
import {
  CHART_AXIS,
  CHART_GRID_COLOR,
  CHART_TOOLTIP_STYLE,
  formatNumber,
} from '../services/format';
import useApi from '../services/useApi';

const STATUSES = [
  'ALL',
  'AT_RISK',
  'MISSED',
  'SAFE',
  'HELD_BY_CONNECTING_SERVICE',
  'UNKNOWN_NO_GPS',
];

export default function ConnectionRisk() {
  const [status, setStatus] = useState('ALL');

  const risk = useApi(() => api.getConnectionRisk({ status }), [status]);
  const sensitivity = useApi(() => api.getSensitivity(), []);

  const summary = risk.data?.summary || {};
  const sensitivityRows = sensitivity.data?.results || [];
  const interpretation = sensitivity.data?.interpretation || {};

  const columns = [
    { key: 'connection_id', header: 'ID' },
    { key: 'interchange_stop_id', header: 'Interchange' },
    { key: 'feeder_route_id', header: 'Feeder' },
    { key: 'connecting_route_id', header: 'Connecting' },
    {
      key: 'feeder_delay_min',
      header: 'Feeder delay',
      numeric: true,
      render: (c) =>
        c.feeder_delay_min == null ? '—' : `${c.feeder_delay_min} min`,
    },
    {
      key: 'planned_transfer_min',
      header: 'Planned',
      numeric: true,
      render: (c) => `${c.planned_transfer_min} min`,
    },
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
      key: 'shortfall_min',
      header: 'Shortfall',
      numeric: true,
      render: (c) => (c.shortfall_min == null ? '—' : `${c.shortfall_min} min`),
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
      render: (c) => formatNumber(c.transferring_passengers),
    },
  ];

  return (
    <>
      <p className="page-intro">
        A transfer is at risk when the time left between a feeder's actual
        arrival and the connecting service's departure falls below the buffer
        passengers need:{' '}
        <code>available = connecting departure − feeder actual arrival</code>,
        at risk when <code>available &lt; required buffer</code>. Connections
        whose feeder has no GPS record are reported as UNKNOWN rather than
        assumed safe.
      </p>

      {risk.loading && <Loading label="Loading connection risk…" />}
      {risk.error && <ErrorState error={risk.error} onRetry={risk.reload} />}

      {risk.data && (
        <>
          <div className="grid grid-kpi">
            <KpiCard label="Total connections" value={summary.totalConnections} />
            <KpiCard
              label="Assessed"
              value={summary.assessed}
              hint="feeder arrival observed"
            />
            <KpiCard label="At risk" value={summary.atRisk} />
            <KpiCard label="Missed" value={summary.missed} />
            <KpiCard label="Safe" value={summary.safe} />
            <KpiCard
              label="Could not assess"
              value={summary.unknown}
              hint="no GPS for the feeder"
            />
            <KpiCard
              label="Passengers at risk"
              value={summary.passengersAtRisk}
              estimate
            />
            <KpiCard
              label="Structurally tight"
              value={summary.plannedTightConnections}
              hint="tight even on a perfect day"
            />
          </div>

          <div className="toolbar" style={{ marginTop: '1rem' }}>
            <div className="field">
              <label htmlFor="risk-status">Status</label>
              <select
                id="risk-status"
                value={status}
                onChange={(event) => setStatus(event.target.value)}
              >
                {STATUSES.map((s) => (
                  <option key={s} value={s}>
                    {s.replace(/_/g, ' ')}
                  </option>
                ))}
              </select>
            </div>
          </div>

          <Card
            title="Transfer opportunities"
            subtitle={`${risk.data.returned} of ${risk.data.totalAvailable} connections shown`}
          >
            <DataTable
              columns={columns}
              rows={risk.data.connections || []}
              rowKey={(row) => row.connection_id}
              emptyMessage="No connections match this status."
            />
          </Card>
        </>
      )}

      <Card
        title="Sensitivity analysis"
        subtitle="How much of the reported risk is driven by the buffer assumption"
      >
        {sensitivity.loading ? (
          <Loading label="Loading sensitivity analysis…" />
        ) : sensitivity.error ? (
          <ErrorState error={sensitivity.error} onRetry={sensitivity.reload} />
        ) : (
          <>
            {interpretation.note && (
              <div
                className={
                  interpretation.verdict === 'ROBUST' ? 'notice' : 'notice notice-warn'
                }
              >
                <strong>{String(interpretation.verdict || '').replace(/_/g, ' ')}: </strong>
                {interpretation.note}
              </div>
            )}

            {sensitivityRows.length > 0 && (
              <>
                <div className="grid grid-2">
                  <div className="chart-box">
                    <ResponsiveContainer width="100%" height="100%">
                      <BarChart data={sensitivityRows}>
                        <CartesianGrid stroke={CHART_GRID_COLOR} vertical={false} />
                        <XAxis
                          dataKey="required_buffer_min"
                          {...CHART_AXIS}
                          label={{
                            value: 'Required buffer (min)',
                            position: 'insideBottom',
                            offset: -2,
                            fill: '#6f87a6',
                            fontSize: 11,
                          }}
                        />
                        <YAxis {...CHART_AXIS} />
                        <Tooltip {...CHART_TOOLTIP_STYLE} />
                        <Legend wrapperStyle={{ fontSize: '0.75rem' }} />
                        <Bar
                          dataKey="at_risk_connections"
                          name="At risk"
                          fill="#f59e0b"
                          radius={[4, 4, 0, 0]}
                        />
                        <Bar
                          dataKey="missed_connections"
                          name="Missed"
                          fill="#ef4444"
                          radius={[4, 4, 0, 0]}
                        />
                        <Bar
                          dataKey="safe_connections"
                          name="Safe"
                          fill="#22c55e"
                          radius={[4, 4, 0, 0]}
                        />
                      </BarChart>
                    </ResponsiveContainer>
                  </div>

                  <div className="chart-box">
                    <ResponsiveContainer width="100%" height="100%">
                      <LineChart data={sensitivityRows}>
                        <CartesianGrid stroke={CHART_GRID_COLOR} />
                        <XAxis dataKey="required_buffer_min" {...CHART_AXIS} />
                        <YAxis {...CHART_AXIS} />
                        <Tooltip {...CHART_TOOLTIP_STYLE} />
                        <Legend wrapperStyle={{ fontSize: '0.75rem' }} />
                        <Line
                          type="monotone"
                          dataKey="passengers_at_risk"
                          name="Passengers at risk (est)"
                          stroke="#22d3ee"
                          strokeWidth={2}
                        />
                        <Line
                          type="monotone"
                          dataKey="additional_wait_minutes"
                          name="Additional wait (passenger-min, est)"
                          stroke="#a78bfa"
                          strokeWidth={2}
                        />
                      </LineChart>
                    </ResponsiveContainer>
                  </div>
                </div>

                <DataTable
                  columns={[
                    {
                      key: 'required_buffer_min',
                      header: 'Buffer (min)',
                      numeric: true,
                    },
                    {
                      key: 'connections_assessed',
                      header: 'Assessed',
                      numeric: true,
                    },
                    {
                      key: 'at_risk_connections',
                      header: 'At risk',
                      numeric: true,
                    },
                    {
                      key: 'missed_connections',
                      header: 'Missed',
                      numeric: true,
                    },
                    {
                      key: 'at_risk_percentage',
                      header: 'At risk %',
                      numeric: true,
                      render: (row) => `${row.at_risk_percentage}%`,
                    },
                    {
                      key: 'passengers_at_risk',
                      header: 'Passengers (est)',
                      numeric: true,
                      render: (row) => formatNumber(row.passengers_at_risk),
                    },
                    {
                      key: 'additional_wait_minutes',
                      header: 'Added wait (est)',
                      numeric: true,
                      render: (row) => formatNumber(row.additional_wait_minutes),
                    },
                    {
                      key: 'delta_at_risk_vs_lowest_buffer',
                      header: 'Δ vs lowest buffer',
                      numeric: true,
                      render: (row) =>
                        row.delta_at_risk_vs_lowest_buffer == null
                          ? '—'
                          : `+${row.delta_at_risk_vs_lowest_buffer}`,
                    },
                  ]}
                  rows={sensitivityRows}
                  rowKey={(row) => row.required_buffer_min}
                />
                <p className="chart-legend-note">
                  Missed connections do not change with the buffer: whether a
                  feeder arrives after the connecting service has left is a fact
                  about the operation, not about the assumption.
                </p>
              </>
            )}
          </>
        )}
      </Card>
    </>
  );
}
