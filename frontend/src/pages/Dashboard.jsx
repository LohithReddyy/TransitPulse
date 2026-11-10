import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Legend,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import Card from '../components/Card';
import ErrorState from '../components/ErrorState';
import KpiCard from '../components/KpiCard';
import Loading from '../components/Loading';
import api from '../services/api';
import {
  CHART_AXIS,
  CHART_GRID_COLOR,
  CHART_TOOLTIP_STYLE,
  DELAY_CLASS_COLORS,
  formatPercent,
  toChartData,
} from '../services/format';
import useApi from '../services/useApi';

const CLASS_ORDER = ['EARLY', 'ON_TIME', 'MINOR', 'MODERATE', 'SEVERE', 'MISSING'];

export default function Dashboard() {
  const dashboard = useApi(() => api.getDashboard(), []);
  const routePerf = useApi(
    () => api.getRoutePerformance().catch(() => []),
    []
  );
  const impact = useApi(
    () => api.getPassengerImpact().catch(() => null),
    []
  );

  if (dashboard.loading) return <Loading label="Loading dashboard…" />;
  if (dashboard.error) {
    return <ErrorState error={dashboard.error} onRetry={dashboard.reload} />;
  }

  const d = dashboard.data || {};

  const classData = toChartData(d.delayClassDistribution, 'delayClass', 'count')
    .sort(
      (a, b) =>
        CLASS_ORDER.indexOf(a.delayClass) - CLASS_ORDER.indexOf(b.delayClass)
    );

  const routeRows = (routePerf.data || [])
    .slice()
    .sort((a, b) => (a.on_time_percentage ?? 0) - (b.on_time_percentage ?? 0));

  const impactRows = (impact.data?.byRoute || []).slice(0, 10);

  return (
    <>
      {d.analyticsAvailable === false && (
        <div className="notice notice-warn">
          <strong>Delay analytics have not been generated yet.</strong> Network
          counts below come from the database. To populate delay, propagation and
          passenger figures, run:{' '}
          <code>cd data-engine &amp;&amp; python -m transitpulse_engine.pipeline</code>
        </div>
      )}

      <div className="grid grid-kpi">
        <KpiCard label="Total routes" value={d.totalRoutes} />
        <KpiCard label="Total stops" value={d.totalStops} />
        <KpiCard label="Scheduled trips" value={d.totalScheduledTrips} />
        <KpiCard
          label="Delayed trips"
          value={d.delayedTrips}
          hint={
            d.totalScheduledTrips
              ? `of ${d.totalScheduledTrips} trips`
              : undefined
          }
        />
        <KpiCard
          label="On-time percentage"
          value={d.onTimePercentage}
          unit="%"
          decimals={1}
          hint="observed arrivals only"
        />
        <KpiCard
          label="Average delay"
          value={d.averageDelayMin}
          unit="min"
          decimals={2}
        />
        <KpiCard
          label="Passengers affected"
          value={d.passengersAffected}
          estimate
          hint="modelled from volume profiles"
        />
        <KpiCard
          label="Recovery percentage"
          value={d.recoveryPercentage}
          unit="%"
          decimals={1}
          hint={`${d.recoveredTrips ?? 0} of ${d.delayedTrips ?? 0} delayed trips`}
        />
      </div>

      <div className="grid grid-2" style={{ marginTop: '1rem' }}>
        <Card
          title="Delay class distribution"
          subtitle="MISSING records are shown separately and never counted as on time"
        >
          {classData.length === 0 ? (
            <p className="page-intro">No delay observations available yet.</p>
          ) : (
            <div className="chart-box">
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={classData}>
                  <CartesianGrid stroke={CHART_GRID_COLOR} vertical={false} />
                  <XAxis dataKey="delayClass" {...CHART_AXIS} />
                  <YAxis {...CHART_AXIS} />
                  <Tooltip {...CHART_TOOLTIP_STYLE} />
                  <Bar dataKey="count" name="Observations" radius={[4, 4, 0, 0]}>
                    {classData.map((row) => (
                      <Cell
                        key={row.delayClass}
                        fill={DELAY_CLASS_COLORS[row.delayClass] || '#2f6fed'}
                      />
                    ))}
                  </Bar>
                </BarChart>
              </ResponsiveContainer>
            </div>
          )}
        </Card>

        <Card
          title="Route performance"
          subtitle="On-time percentage, worst routes first"
        >
          {routeRows.length === 0 ? (
            <p className="page-intro">
              Route performance appears once the data engine has run.
            </p>
          ) : (
            <div className="chart-box">
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={routeRows} layout="vertical">
                  <CartesianGrid stroke={CHART_GRID_COLOR} horizontal={false} />
                  <XAxis type="number" domain={[0, 100]} {...CHART_AXIS} />
                  <YAxis
                    type="category"
                    dataKey="route_id"
                    width={48}
                    {...CHART_AXIS}
                  />
                  <Tooltip
                    {...CHART_TOOLTIP_STYLE}
                    formatter={(value) => formatPercent(value)}
                  />
                  <Bar
                    dataKey="on_time_percentage"
                    name="On time %"
                    fill="#22d3ee"
                    radius={[0, 4, 4, 0]}
                  />
                </BarChart>
              </ResponsiveContainer>
            </div>
          )}
        </Card>
      </div>

      <div className="grid grid-2" style={{ marginTop: '1rem' }}>
        <Card
          title="Average delay by route"
          subtitle="Mean delay across observed arrivals"
        >
          {routeRows.length === 0 ? (
            <p className="page-intro">Not available yet.</p>
          ) : (
            <div className="chart-box">
              <ResponsiveContainer width="100%" height="100%">
                <LineChart data={routeRows.slice().sort((a, b) =>
                  String(a.route_id).localeCompare(String(b.route_id))
                )}>
                  <CartesianGrid stroke={CHART_GRID_COLOR} />
                  <XAxis dataKey="route_id" {...CHART_AXIS} />
                  <YAxis {...CHART_AXIS} />
                  <Tooltip {...CHART_TOOLTIP_STYLE} />
                  <Legend wrapperStyle={{ fontSize: '0.75rem' }} />
                  <Line
                    type="monotone"
                    dataKey="avg_delay_min"
                    name="Average delay (min)"
                    stroke="#2f6fed"
                    strokeWidth={2}
                    dot={{ r: 3 }}
                  />
                  <Line
                    type="monotone"
                    dataKey="p90_delay_min"
                    name="90th percentile (min)"
                    stroke="#f97316"
                    strokeWidth={2}
                    dot={{ r: 3 }}
                  />
                </LineChart>
              </ResponsiveContainer>
            </div>
          )}
        </Card>

        <Card
          title="Passenger impact by route"
          subtitle="Passenger-minutes lost — all figures are estimates"
        >
          {impactRows.length === 0 ? (
            <p className="page-intro">
              Passenger impact appears once the data engine has run.
            </p>
          ) : (
            <div className="chart-box">
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={impactRows}>
                  <CartesianGrid stroke={CHART_GRID_COLOR} vertical={false} />
                  <XAxis dataKey="route_id" {...CHART_AXIS} />
                  <YAxis {...CHART_AXIS} />
                  <Tooltip {...CHART_TOOLTIP_STYLE} />
                  <Bar
                    dataKey="passenger_minutes_lost"
                    name="Passenger-minutes lost (est)"
                    fill="#a78bfa"
                    radius={[4, 4, 0, 0]}
                  />
                </BarChart>
              </ResponsiveContainer>
            </div>
          )}
          <p className="chart-legend-note">
            Estimated from average boardings and load, not confirmed counts.
          </p>
        </Card>
      </div>

      <Card
        title="Data quality"
        subtitle="How much of the service day was actually observed"
        className="grid-3"
      >
        <div className="grid grid-kpi">
          <KpiCard label="Total movements" value={d.totalMovements} />
          <KpiCard label="Observed" value={d.observedMovements} />
          <KpiCard
            label="Missing GPS"
            value={d.missingGpsRecords}
            hint="no arrival event; never counted as zero delay"
          />
          <KpiCard
            label="Diverted records"
            value={d.divertedRecords}
            hint="stop not served; excluded from comparison"
          />
          <KpiCard
            label="Unobserved total"
            value={d.unobservedMovements}
            hint="missing GPS plus diverted stops"
          />
          <KpiCard label="Propagation links" value={d.propagationLinks} />
          <KpiCard
            label="High-confidence links"
            value={d.highConfidencePropagations}
            hint="vehicle continuity or incident-corroborated"
          />
          <KpiCard label="Connections at risk" value={d.connectionsAtRisk} />
          <KpiCard label="Open incidents" value={d.openIncidents} />
        </div>
      </Card>
    </>
  );
}
