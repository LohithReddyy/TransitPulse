import { useState } from 'react';
import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Legend,
  Pie,
  PieChart,
  ResponsiveContainer,
  Scatter,
  ScatterChart,
  Tooltip,
  XAxis,
  YAxis,
  ZAxis,
} from 'recharts';
import Badge from '../components/Badge';
import Card from '../components/Card';
import DataTable from '../components/DataTable';
import ErrorState from '../components/ErrorState';
import EvidenceList from '../components/EvidenceList';
import KpiCard from '../components/KpiCard';
import Loading from '../components/Loading';
import api from '../services/api';
import {
  CHART_AXIS,
  CHART_GRID_COLOR,
  CHART_TOOLTIP_STYLE,
  DELAY_CLASS_COLORS,
  formatMinutes,
  formatNumber,
  formatPercent,
  toChartData,
} from '../services/format';
import useApi from '../services/useApi';

const MECHANISMS = ['ALL', 'VEHICLE_CONTINUITY', 'CONNECTION', 'SHARED_STOP'];
const CONFIDENCES = ['ALL', 'HIGH', 'MEDIUM', 'LOW'];

export default function Analytics() {
  const [mechanism, setMechanism] = useState('ALL');
  const [confidence, setConfidence] = useState('ALL');
  const [expanded, setExpanded] = useState(null);

  const routePerf = useApi(() => api.getRoutePerformance(), []);
  const distribution = useApi(() => api.getDelayDistribution(), []);
  const propagation = useApi(
    () => api.getPropagation({ mechanism, confidence, limit: 100 }),
    [mechanism, confidence]
  );
  const recovery = useApi(() => api.getRecovery(), []);
  const validation = useApi(() => api.getValidation().catch(() => null), []);

  const classData = toChartData(
    distribution.data?.distribution,
    'delayClass',
    'count'
  );
  const propSummary = propagation.data?.summary || {};
  const mechanismData = toChartData(propSummary.byMechanism, 'mechanism', 'count');
  const confidenceData = toChartData(propSummary.byConfidence, 'confidence', 'count');

  return (
    <>
      <p className="page-intro">
        Network-wide analysis: route performance, how delays are distributed,
        which mechanisms carry delay between services, and how well the network
        recovers.
      </p>

      {/* ------------------------- route performance ------------------------- */}
      <Card title="Route performance" subtitle="Observed arrivals per route">
        {routePerf.loading ? (
          <Loading />
        ) : routePerf.error ? (
          <ErrorState error={routePerf.error} onRetry={routePerf.reload} />
        ) : (
          <>
            <div className="chart-box chart-box-tall">
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={routePerf.data || []}>
                  <CartesianGrid stroke={CHART_GRID_COLOR} vertical={false} />
                  <XAxis dataKey="route_id" {...CHART_AXIS} />
                  <YAxis yAxisId="left" {...CHART_AXIS} />
                  <YAxis
                    yAxisId="right"
                    orientation="right"
                    domain={[0, 100]}
                    {...CHART_AXIS}
                  />
                  <Tooltip {...CHART_TOOLTIP_STYLE} />
                  <Legend wrapperStyle={{ fontSize: '0.75rem' }} />
                  <Bar
                    yAxisId="left"
                    dataKey="avg_delay_min"
                    name="Avg delay (min)"
                    fill="#2f6fed"
                    radius={[4, 4, 0, 0]}
                  />
                  <Bar
                    yAxisId="left"
                    dataKey="p90_delay_min"
                    name="P90 delay (min)"
                    fill="#f97316"
                    radius={[4, 4, 0, 0]}
                  />
                  <Bar
                    yAxisId="right"
                    dataKey="on_time_percentage"
                    name="On time %"
                    fill="#22c55e"
                    radius={[4, 4, 0, 0]}
                  />
                </BarChart>
              </ResponsiveContainer>
            </div>

            <DataTable
              columns={[
                { key: 'route_id', header: 'Route' },
                { key: 'observations', header: 'Observations', numeric: true },
                {
                  key: 'avg_delay_min',
                  header: 'Avg delay',
                  numeric: true,
                  render: (r) => formatMinutes(r.avg_delay_min, 2),
                },
                {
                  key: 'p90_delay_min',
                  header: 'P90 delay',
                  numeric: true,
                  render: (r) => formatMinutes(r.p90_delay_min, 2),
                },
                {
                  key: 'max_delay_min',
                  header: 'Max delay',
                  numeric: true,
                  render: (r) => formatMinutes(r.max_delay_min, 2),
                },
                {
                  key: 'on_time_percentage',
                  header: 'On time',
                  numeric: true,
                  render: (r) => formatPercent(r.on_time_percentage),
                },
                { key: 'trips_total', header: 'Trips', numeric: true },
                { key: 'trips_delayed', header: 'Delayed', numeric: true },
                {
                  key: 'recovery_percentage',
                  header: 'Recovery',
                  numeric: true,
                  render: (r) => formatPercent(r.recovery_percentage),
                },
                {
                  key: 'performance_band',
                  header: 'Band',
                  render: (r) => <Badge value={r.performance_band} />,
                },
              ]}
              rows={routePerf.data || []}
              rowKey={(row) => row.route_id}
            />
          </>
        )}
      </Card>

      {/* ------------------------- distribution ------------------------- */}
      <div className="grid grid-2" style={{ marginTop: '1rem' }}>
        <Card
          title="Delay distribution"
          subtitle="MISSING is a separate class, not an on-time arrival"
        >
          {distribution.loading ? (
            <Loading />
          ) : distribution.error ? (
            <ErrorState error={distribution.error} onRetry={distribution.reload} />
          ) : (
            <>
              <div className="chart-box">
                <ResponsiveContainer width="100%" height="100%">
                  <PieChart>
                    <Pie
                      data={classData}
                      dataKey="count"
                      nameKey="delayClass"
                      innerRadius={55}
                      outerRadius={95}
                      paddingAngle={2}
                    >
                      {classData.map((row) => (
                        <Cell
                          key={row.delayClass}
                          fill={DELAY_CLASS_COLORS[row.delayClass] || '#2f6fed'}
                        />
                      ))}
                    </Pie>
                    <Tooltip {...CHART_TOOLTIP_STYLE} />
                    <Legend wrapperStyle={{ fontSize: '0.75rem' }} />
                  </PieChart>
                </ResponsiveContainer>
              </div>
              <div className="grid grid-kpi">
                <KpiCard
                  label="Average"
                  value={distribution.data?.averageDelayMin}
                  unit="min"
                  decimals={2}
                />
                <KpiCard
                  label="90th percentile"
                  value={distribution.data?.p90DelayMin}
                  unit="min"
                  decimals={2}
                />
                <KpiCard
                  label="Worst"
                  value={distribution.data?.maxDelayMin}
                  unit="min"
                  decimals={2}
                />
              </div>
            </>
          )}
        </Card>

        <Card
          title="Delay recovery"
          subtitle="Trips that fell behind and then caught up"
        >
          {recovery.loading ? (
            <Loading />
          ) : recovery.error ? (
            <ErrorState error={recovery.error} onRetry={recovery.reload} />
          ) : (
            <>
              <div className="grid grid-kpi">
                <KpiCard
                  label="Delayed trips"
                  value={recovery.data?.delayedTrips}
                />
                <KpiCard
                  label="Recovered"
                  value={recovery.data?.recoveredTrips}
                />
                <KpiCard
                  label="Recovery rate"
                  value={recovery.data?.recoveryPercentage}
                  unit="%"
                  decimals={1}
                />
                <KpiCard
                  label="Avg recovery"
                  value={recovery.data?.averageRecoveryMin}
                  unit="min"
                  decimals={2}
                />
              </div>
              <DataTable
                columns={[
                  { key: 'trip_id', header: 'Trip' },
                  { key: 'route_id', header: 'Route' },
                  {
                    key: 'max_delay_min',
                    header: 'Peak delay',
                    numeric: true,
                    render: (r) => formatMinutes(r.max_delay_min),
                  },
                  {
                    key: 'final_delay_min',
                    header: 'Final delay',
                    numeric: true,
                    render: (r) => formatMinutes(r.final_delay_min),
                  },
                  {
                    key: 'recovery_min',
                    header: 'Recovered',
                    numeric: true,
                    render: (r) => `${r.recovery_min} min`,
                  },
                  {
                    key: 'data_confidence',
                    header: 'Confidence',
                    render: (r) => <Badge value={r.data_confidence} />,
                  },
                ]}
                rows={recovery.data?.topRecoveries || []}
                rowKey={(row) => row.trip_id}
                emptyMessage="No trips recovered from a delay."
              />
            </>
          )}
        </Card>
      </div>

      {/* ------------------------- propagation ------------------------- */}
      <Card
        title="Delay propagation"
        subtitle="How delay travelled between services, and how strong the evidence is"
      >
        {propagation.loading ? (
          <Loading />
        ) : propagation.error ? (
          <ErrorState error={propagation.error} onRetry={propagation.reload} />
        ) : (
          <>
            <div className="grid grid-kpi">
              <KpiCard label="Total links" value={propSummary.totalLinks} />
              <KpiCard
                label="Affected services"
                value={propSummary.affectedTripsUnique}
                hint="de-duplicated"
              />
              <KpiCard
                label="Avg propagated"
                value={propSummary.avgPropagatedDelayMin}
                unit="min"
                decimals={2}
              />
              <KpiCard
                label="Worst propagated"
                value={propSummary.maxPropagatedDelayMin}
                unit="min"
                decimals={2}
              />
            </div>

            <div className="grid grid-2" style={{ marginTop: '1rem' }}>
              <div className="chart-box">
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart data={mechanismData}>
                    <CartesianGrid stroke={CHART_GRID_COLOR} vertical={false} />
                    <XAxis dataKey="mechanism" {...CHART_AXIS} />
                    <YAxis {...CHART_AXIS} />
                    <Tooltip {...CHART_TOOLTIP_STYLE} />
                    <Bar
                      dataKey="count"
                      name="Links"
                      fill="#22d3ee"
                      radius={[4, 4, 0, 0]}
                    />
                  </BarChart>
                </ResponsiveContainer>
              </div>
              <div className="chart-box">
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart data={confidenceData}>
                    <CartesianGrid stroke={CHART_GRID_COLOR} vertical={false} />
                    <XAxis dataKey="confidence" {...CHART_AXIS} />
                    <YAxis {...CHART_AXIS} />
                    <Tooltip {...CHART_TOOLTIP_STYLE} />
                    <Bar dataKey="count" name="Links" radius={[4, 4, 0, 0]}>
                      {confidenceData.map((row) => (
                        <Cell
                          key={row.confidence}
                          fill={
                            row.confidence === 'HIGH'
                              ? '#22c55e'
                              : row.confidence === 'MEDIUM'
                                ? '#f59e0b'
                                : '#64748b'
                          }
                        />
                      ))}
                    </Bar>
                  </BarChart>
                </ResponsiveContainer>
              </div>
            </div>

            <div className="toolbar" style={{ marginTop: '1rem' }}>
              <div className="field">
                <label htmlFor="prop-mechanism">Mechanism</label>
                <select
                  id="prop-mechanism"
                  value={mechanism}
                  onChange={(event) => setMechanism(event.target.value)}
                >
                  {MECHANISMS.map((m) => (
                    <option key={m} value={m}>
                      {m.replace(/_/g, ' ')}
                    </option>
                  ))}
                </select>
              </div>
              <div className="field">
                <label htmlFor="prop-confidence">Confidence</label>
                <select
                  id="prop-confidence"
                  value={confidence}
                  onChange={(event) => setConfidence(event.target.value)}
                >
                  {CONFIDENCES.map((c) => (
                    <option key={c} value={c}>
                      {c}
                    </option>
                  ))}
                </select>
              </div>
            </div>

            <div className="chart-box">
              <ResponsiveContainer width="100%" height="100%">
                <ScatterChart>
                  <CartesianGrid stroke={CHART_GRID_COLOR} />
                  <XAxis
                    dataKey="source_delay_min"
                    name="Source delay (min)"
                    {...CHART_AXIS}
                  />
                  <YAxis
                    dataKey="propagated_delay_min"
                    name="Propagated delay (min)"
                    {...CHART_AXIS}
                  />
                  <ZAxis range={[40, 40]} />
                  <Tooltip {...CHART_TOOLTIP_STYLE} />
                  <Scatter
                    data={propagation.data?.links || []}
                    name="Propagation links"
                    fill="#22d3ee"
                  />
                </ScatterChart>
              </ResponsiveContainer>
            </div>
            <p className="chart-legend-note">
              Source delay against the delay forced onto the downstream service.
            </p>

            <DataTable
              columns={[
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
                { key: 'source_trip_id', header: 'Source trip' },
                { key: 'source_route_id', header: 'Source route' },
                { key: 'affected_trip_id', header: 'Affected trip' },
                { key: 'affected_route_id', header: 'Affected route' },
                {
                  key: 'propagated_delay_min',
                  header: 'Propagated',
                  numeric: true,
                  render: (p) => formatMinutes(p.propagated_delay_min),
                },
                {
                  key: 'incident_id',
                  header: 'Incident',
                  render: (p) => p.incident_id || '—',
                },
              ]}
              rows={propagation.data?.links || []}
              rowKey={(row, index) =>
                `${row.source_trip_id}-${row.affected_trip_id}-${index}`
              }
              onRowClick={(row) =>
                setExpanded((current) =>
                  current &&
                  current.source_trip_id === row.source_trip_id &&
                  current.affected_trip_id === row.affected_trip_id
                    ? null
                    : row
                )
              }
              emptyMessage="No propagation links match these filters."
            />

            {expanded && (
              <div className="calc-step" style={{ marginTop: '1rem' }}>
                <div className="calc-step-body" style={{ width: '100%' }}>
                  <div className="calc-step-desc">
                    <strong>
                      {expanded.source_trip_id} → {expanded.affected_trip_id}
                    </strong>{' '}
                    <Badge value={expanded.mechanism} />{' '}
                    <Badge value={expanded.confidence} />
                  </div>
                  {expanded.calculation && (
                    <div className="calc-formula">{expanded.calculation}</div>
                  )}
                  <EvidenceList evidence={expanded.evidence} />
                </div>
              </div>
            )}

            {propagation.data?.note && (
              <p className="chart-legend-note">{propagation.data.note}</p>
            )}
          </>
        )}
      </Card>

      {/* ------------------------- data quality ------------------------- */}
      {validation.data && (
        <Card
          title="Data quality"
          subtitle="What the ingestion layer found, reported rather than hidden"
        >
          <div className="grid grid-kpi">
            <KpiCard
              label="Missing GPS records"
              value={validation.data.missingGpsRecords}
            />
            <KpiCard
              label="Diverted records"
              value={validation.data.divertedRecords}
            />
            <KpiCard
              label="Duplicates removed"
              value={Object.values(validation.data.duplicatesRemoved || {}).reduce(
                (sum, value) => sum + Number(value || 0),
                0
              )}
            />
            <KpiCard
              label="Orphan references"
              value={Object.keys(validation.data.orphanReferences || {}).length}
            />
          </div>

          {(validation.data.warnings || []).length > 0 && (
            <ul className="evidence-list" style={{ marginTop: '0.8rem' }}>
              {validation.data.warnings.map((warning, index) => (
                <li key={index}>{warning}</li>
              ))}
            </ul>
          )}

          <DataTable
            columns={[
              { key: 'dataset', header: 'Dataset' },
              { key: 'rows', header: 'Rows loaded', numeric: true },
            ]}
            rows={Object.entries(validation.data.rowCounts || {}).map(
              ([dataset, rows]) => ({ dataset, rows: formatNumber(rows) })
            )}
            rowKey={(row) => row.dataset}
          />
        </Card>
      )}
    </>
  );
}
