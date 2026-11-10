import { useState } from 'react';
import Badge from '../components/Badge';
import Card from '../components/Card';
import DataTable from '../components/DataTable';
import ErrorState from '../components/ErrorState';
import Loading from '../components/Loading';
import api from '../services/api';
import { formatMinutes, formatNumber, formatPercent } from '../services/format';
import useApi from '../services/useApi';

export default function Stops() {
  const [search, setSearch] = useState('');
  const [interchangeOnly, setInterchangeOnly] = useState(false);
  const [lowVolumeOnly, setLowVolumeOnly] = useState(false);
  const [selected, setSelected] = useState(null);

  const stops = useApi(
    () =>
      api.getStops({
        search: search || undefined,
        interchangeOnly,
        lowVolumeOnly,
      }),
    [search, interchangeOnly, lowVolumeOnly]
  );

  const volumes = useApi(
    () => (selected ? api.getStopPassengerVolumes(selected) : Promise.resolve(null)),
    [selected]
  );

  const columns = [
    { key: 'stopId', header: 'Stop ID' },
    { key: 'stopName', header: 'Name' },
    {
      key: 'interchange',
      header: 'Type',
      render: (s) => (
        <Badge value={s.interchange ? 'INTERCHANGE' : s.stopType || 'STANDARD'} />
      ),
    },
    {
      key: 'routeConnections',
      header: 'Routes',
      render: (s) =>
        s.routeConnections?.length ? s.routeConnections.join(', ') : '—',
    },
    {
      key: 'passengerVolume',
      header: 'Boardings',
      numeric: true,
      render: (s) => formatNumber(s.passengerVolume),
    },
    {
      key: 'averageWaitMin',
      header: 'Avg wait',
      numeric: true,
      render: (s) =>
        s.averageWaitMin == null ? '—' : `${s.averageWaitMin.toFixed(1)} min`,
    },
    {
      key: 'avgDelayMin',
      header: 'Avg delay',
      numeric: true,
      render: (s) => formatMinutes(s.avgDelayMin, 2),
    },
    {
      key: 'delayFrequencyPct',
      header: 'Delay frequency',
      numeric: true,
      render: (s) => formatPercent(s.delayFrequencyPct),
    },
    {
      key: 'confidence',
      header: 'Confidence',
      render: (s) => (
        <Badge
          value={s.confidence}
          title={
            s.lowVolume
              ? 'Low passenger volume: statistics from a small sample'
              : 'Sample size is adequate'
          }
        />
      ),
    },
  ];

  const volumeColumns = [
    { key: 'timeBand', header: 'Time band' },
    { key: 'routeId', header: 'Route' },
    {
      key: 'boardings',
      header: 'Boardings',
      numeric: true,
      render: (v) => formatNumber(v.boardings),
    },
    {
      key: 'alightings',
      header: 'Alightings',
      numeric: true,
      render: (v) => formatNumber(v.alightings),
    },
    {
      key: 'averageLoad',
      header: 'Avg load',
      numeric: true,
      render: (v) => formatNumber(v.averageLoad),
    },
    {
      key: 'averageWaitMin',
      header: 'Avg wait (min)',
      numeric: true,
      render: (v) => formatNumber(v.averageWaitMin, 1),
    },
    {
      key: 'isLowVolume',
      header: 'Low volume',
      render: (v) => <Badge value={v.isLowVolume === 'Y' ? 'LOW' : 'ok'} />,
    },
  ];

  const lowVolumeCount = (stops.data || []).filter((s) => s.lowVolume).length;

  return (
    <>
      <p className="page-intro">
        Stops with their route connections, passenger volume and delay
        behaviour. Stops whose passenger sample is too small to support
        confident statistics are marked LOW confidence rather than hidden, so a
        high delay rate drawn from a handful of observations is not over-read.
      </p>

      {lowVolumeCount > 0 && (
        <div className="notice">
          <strong>{lowVolumeCount}</strong> of {stops.data?.length} stops shown
          have low passenger volume. Their delay and wait statistics carry wide
          uncertainty.
        </div>
      )}

      <div className="toolbar">
        <div className="field">
          <label htmlFor="stop-search">Search</label>
          <input
            id="stop-search"
            type="search"
            placeholder="Stop name or ID"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
          />
        </div>
        <div className="field">
          <label htmlFor="stop-interchange">Interchanges only</label>
          <select
            id="stop-interchange"
            value={interchangeOnly ? 'yes' : 'no'}
            onChange={(event) => setInterchangeOnly(event.target.value === 'yes')}
          >
            <option value="no">All stops</option>
            <option value="yes">Interchanges only</option>
          </select>
        </div>
        <div className="field">
          <label htmlFor="stop-lowvolume">Low volume</label>
          <select
            id="stop-lowvolume"
            value={lowVolumeOnly ? 'yes' : 'no'}
            onChange={(event) => setLowVolumeOnly(event.target.value === 'yes')}
          >
            <option value="no">Include all</option>
            <option value="yes">Low volume only</option>
          </select>
        </div>
        <div className="toolbar-spacer" />
        <button
          type="button"
          className="btn btn-secondary"
          onClick={() => {
            setSearch('');
            setInterchangeOnly(false);
            setLowVolumeOnly(false);
          }}
        >
          Clear filters
        </button>
      </div>

      <Card
        title="Stops"
        subtitle={stops.data ? `${stops.data.length} stop(s) shown` : undefined}
      >
        {stops.loading ? (
          <Loading label="Loading stops…" />
        ) : stops.error ? (
          <ErrorState error={stops.error} onRetry={stops.reload} />
        ) : (
          <DataTable
            columns={columns}
            rows={stops.data || []}
            rowKey={(row) => row.stopId}
            onRowClick={(row) =>
              setSelected((current) => (current === row.stopId ? null : row.stopId))
            }
            selectedKey={selected ?? undefined}
            emptyMessage="No stops match these filters."
          />
        )}
      </Card>

      {selected && (
        <Card
          title={`Passenger volume profile — ${selected}`}
          subtitle="Average boardings, alightings and load by time band"
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
          {volumes.loading ? (
            <Loading label="Loading passenger volumes…" />
          ) : volumes.error ? (
            <ErrorState error={volumes.error} onRetry={volumes.reload} />
          ) : (
            <DataTable
              columns={volumeColumns}
              rows={volumes.data || []}
              rowKey={(row) => row.passengerVolumeId}
              emptyMessage="No passenger volume profile recorded for this stop."
              emptyHint="Impact estimates for this stop will be marked NONE confidence."
            />
          )}
        </Card>
      )}
    </>
  );
}
