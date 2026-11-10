import {
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
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

export default function PassengerImpact() {
  const impact = useApi(() => api.getPassengerImpact(), []);

  if (impact.loading) return <Loading label="Loading passenger impact…" />;
  if (impact.error) {
    return <ErrorState error={impact.error} onRetry={impact.reload} />;
  }

  const summary = impact.data?.summary || {};
  const byRoute = impact.data?.byRoute || [];
  const byStop = impact.data?.byStop || [];

  return (
    <>
      <p className="page-intro">
        Estimated passenger exposure to delay and missed transfers. Exposure at
        a stop is taken as average boardings plus average on-board load for that
        time band, and passenger-minutes lost is that figure multiplied by the
        delay.
      </p>

      <div className="notice notice-warn">
        <strong>These are estimates, not passenger counts. </strong>
        {impact.data?.caveat ||
          'Figures are modelled from average volume profiles. Confirming how many passengers were actually affected would require ticketing or automatic passenger counting data.'}
      </div>

      <div className="grid grid-kpi">
        <KpiCard
          label="Passengers exposed"
          value={summary.passengersExposed}
          estimate
        />
        <KpiCard
          label="Passenger-minutes lost"
          value={summary.passengerMinutesLost}
          estimate
        />
        <KpiCard
          label="Delayed stop events"
          value={summary.delayedStopEvents}
          hint="observed arrivals at or above the on-time threshold"
        />
        <KpiCard
          label="Missed transfers"
          value={summary.missedTransferPassengers}
          estimate
          hint="passengers on missed connections"
        />
        <KpiCard
          label="At-risk transfers"
          value={summary.atRiskTransferPassengers}
          estimate
        />
        <KpiCard
          label="Additional waiting"
          value={summary.additionalWaitMinutes}
          unit="pax-min"
          estimate
          hint={
            summary.assumedWaitPerMissedConnectionMin
              ? `assumes ${summary.assumedWaitPerMissedConnectionMin} min for the next service`
              : undefined
          }
        />
        <KpiCard
          label="Low-confidence share"
          value={summary.lowConfidenceShare}
          unit="%"
          decimals={1}
          hint="events at low-volume stops"
        />
      </div>

      <Card
        title="Impact by route"
        subtitle="Ranked by estimated passenger-minutes lost"
      >
        {byRoute.length === 0 ? (
          <p className="page-intro" style={{ marginBottom: 0 }}>
            No passenger impact recorded. Either no delays were observed, or the
            data engine has not run.
          </p>
        ) : (
          <>
            <div className="chart-box">
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={byRoute}>
                  <CartesianGrid stroke={CHART_GRID_COLOR} vertical={false} />
                  <XAxis dataKey="route_id" {...CHART_AXIS} />
                  <YAxis {...CHART_AXIS} />
                  <Tooltip {...CHART_TOOLTIP_STYLE} />
                  <Legend wrapperStyle={{ fontSize: '0.75rem' }} />
                  <Bar
                    dataKey="passenger_minutes_lost"
                    name="Passenger-minutes lost (est)"
                    fill="#a78bfa"
                    radius={[4, 4, 0, 0]}
                  />
                  <Bar
                    dataKey="passengers_exposed"
                    name="Passengers exposed (est)"
                    fill="#22d3ee"
                    radius={[4, 4, 0, 0]}
                  />
                </BarChart>
              </ResponsiveContainer>
            </div>

            <DataTable
              columns={[
                { key: 'route_id', header: 'Route' },
                {
                  key: 'delayed_stop_events',
                  header: 'Delayed events',
                  numeric: true,
                },
                {
                  key: 'passengers_exposed',
                  header: 'Passengers exposed',
                  numeric: true,
                  render: (r) => formatNumber(r.passengers_exposed),
                },
                {
                  key: 'passenger_minutes_lost',
                  header: 'Passenger-min lost',
                  numeric: true,
                  render: (r) => formatNumber(r.passenger_minutes_lost, 1),
                },
                {
                  key: 'avg_delay_min',
                  header: 'Avg delay',
                  numeric: true,
                  render: (r) => `${r.avg_delay_min} min`,
                },
                {
                  key: 'avg_additional_wait_per_passenger_min',
                  header: 'Added wait / passenger',
                  numeric: true,
                  render: (r) =>
                    `${r.avg_additional_wait_per_passenger_min} min`,
                },
                {
                  key: 'confidence',
                  header: 'Confidence',
                  render: (r) => <Badge value={r.confidence} />,
                },
              ]}
              rows={byRoute}
              rowKey={(row) => row.route_id}
            />
          </>
        )}
      </Card>

      <Card
        title="Impact by stop"
        subtitle="Low-volume stops are flagged: small samples carry wide uncertainty"
      >
        <DataTable
          columns={[
            { key: 'stop_id', header: 'Stop' },
            { key: 'delayed_events', header: 'Delayed events', numeric: true },
            {
              key: 'passengers_exposed',
              header: 'Passengers exposed',
              numeric: true,
              render: (r) => formatNumber(r.passengers_exposed),
            },
            {
              key: 'passenger_minutes_lost',
              header: 'Passenger-min lost',
              numeric: true,
              render: (r) => formatNumber(r.passenger_minutes_lost, 1),
            },
            {
              key: 'avg_delay_min',
              header: 'Avg delay',
              numeric: true,
              render: (r) => `${r.avg_delay_min} min`,
            },
            {
              key: 'low_confidence',
              header: 'Confidence',
              render: (r) => (
                <Badge value={r.low_confidence ? 'LOW' : 'ok'} />
              ),
            },
          ]}
          rows={byStop.slice(0, 30)}
          rowKey={(row) => row.stop_id}
          emptyMessage="No stop-level impact recorded."
        />
      </Card>
    </>
  );
}
