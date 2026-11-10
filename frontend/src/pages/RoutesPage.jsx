import { useState } from 'react';
import Badge from '../components/Badge';
import Card from '../components/Card';
import DataTable from '../components/DataTable';
import ErrorState from '../components/ErrorState';
import Loading from '../components/Loading';
import api from '../services/api';
import { formatMinutes, formatNumber, formatPercent } from '../services/format';
import useApi from '../services/useApi';

const MODES = ['ALL', 'BUS', 'TRAM', 'METRO', 'RAIL', 'FERRY'];

export default function RoutesPage() {
  const [search, setSearch] = useState('');
  const [mode, setMode] = useState('ALL');
  const [selected, setSelected] = useState(null);

  const routes = useApi(
    () => api.getRoutes({ search: search || undefined, mode }),
    [search, mode]
  );
  const stops = useApi(
    () => (selected ? api.getRouteStops(selected) : Promise.resolve(null)),
    [selected]
  );

  const columns = [
    { key: 'routeId', header: 'Route ID' },
    { key: 'routeName', header: 'Name' },
    { key: 'mode', header: 'Mode', render: (r) => <Badge value={r.mode} /> },
    {
      key: 'stopCount',
      header: 'Stops',
      numeric: true,
      render: (r) => formatNumber(r.stopCount),
    },
    {
      key: 'scheduledTrips',
      header: 'Trips',
      numeric: true,
      render: (r) => formatNumber(r.scheduledTrips),
    },
    {
      key: 'avgDelayMin',
      header: 'Avg delay',
      numeric: true,
      render: (r) => formatMinutes(r.avgDelayMin, 2),
    },
    {
      key: 'onTimePercentage',
      header: 'On time',
      numeric: true,
      render: (r) => formatPercent(r.onTimePercentage),
    },
    {
      key: 'delayedTrips',
      header: 'Delayed',
      numeric: true,
      render: (r) => formatNumber(r.delayedTrips),
    },
    {
      key: 'performanceBand',
      header: 'Performance',
      render: (r) =>
        r.performanceBand ? <Badge value={r.performanceBand} /> : '—',
    },
  ];

  const stopColumns = [
    { key: 'stopSequence', header: '#', numeric: true },
    { key: 'stopId', header: 'Stop ID' },
    {
      key: 'distanceFromOriginKm',
      header: 'Distance (km)',
      numeric: true,
      render: (s) => formatNumber(s.distanceFromOriginKm, 2),
    },
    {
      key: 'scheduledRuntimeMin',
      header: 'Runtime (min)',
      numeric: true,
      render: (s) => formatNumber(s.scheduledRuntimeMin),
    },
  ];

  return (
    <>
      <p className="page-intro">
        Every route in the network with its stop pattern and operational
        performance. Delay figures cover observed arrivals only; stops with no
        GPS event are excluded rather than counted as on time. Select a row to
        see that route's stop pattern.
      </p>

      <div className="toolbar">
        <div className="field">
          <label htmlFor="route-search">Search</label>
          <input
            id="route-search"
            type="search"
            placeholder="Route name or ID"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
          />
        </div>
        <div className="field">
          <label htmlFor="route-mode">Mode</label>
          <select
            id="route-mode"
            value={mode}
            onChange={(event) => setMode(event.target.value)}
          >
            {MODES.map((m) => (
              <option key={m} value={m}>
                {m}
              </option>
            ))}
          </select>
        </div>
        <div className="toolbar-spacer" />
        <button
          type="button"
          className="btn btn-secondary"
          onClick={() => {
            setSearch('');
            setMode('ALL');
          }}
        >
          Clear filters
        </button>
      </div>

      <Card
        title="Routes"
        subtitle={
          routes.data ? `${routes.data.length} route(s) shown` : undefined
        }
      >
        {routes.loading ? (
          <Loading label="Loading routes…" />
        ) : routes.error ? (
          <ErrorState error={routes.error} onRetry={routes.reload} />
        ) : (
          <DataTable
            columns={columns}
            rows={routes.data || []}
            rowKey={(row) => row.routeId}
            onRowClick={(row) =>
              setSelected((current) =>
                current === row.routeId ? null : row.routeId
              )
            }
            selectedKey={selected ?? undefined}
            emptyMessage="No routes match these filters."
            emptyHint="Clear the search box or change the mode."
          />
        )}
      </Card>

      {selected && (
        <Card
          title={`Stop pattern — ${selected}`}
          subtitle="Ordered stops served by this route"
          actions={
            <button
              type="button"
              className="btn btn-secondary btn-sm"
              onClick={() => setSelected(null)}
            >
              Close
            </button>
          }
        >
          {stops.loading ? (
            <Loading label="Loading stop pattern…" />
          ) : stops.error ? (
            <ErrorState error={stops.error} onRetry={stops.reload} />
          ) : (
            <DataTable
              columns={stopColumns}
              rows={stops.data || []}
              rowKey={(row) => row.routeStopId}
              emptyMessage="This route has no stop pattern recorded."
            />
          )}
        </Card>
      )}
    </>
  );
}
