# Datasets to import

All from `TRANSITPULSE.ANALYTICS` unless noted.

## Fact-style views

### `VW_ROUTE_PERFORMANCE` — one row per route per service date
| Column | Meaning |
|--------|---------|
| `route_id`, `route_name`, `mode` | route identity |
| `service_date` | service day |
| `trips_operated` | distinct trips |
| `observations` | stop arrivals with a usable observation |
| `missing_observations` | arrivals with no GPS event — excluded from averages |
| `avg_delay_min`, `median_delay_min`, `p90_delay_min`, `max_delay_min` | delay distribution |
| `on_time_observations`, `delayed_observations` | counts by band |
| `on_time_percentage` | share of observed arrivals under the on-time threshold |
| `data_coverage_percentage` | observed ÷ total movements — how much to trust the row |

### `VW_STOP_PERFORMANCE` — one row per stop
Key columns: `delay_frequency_pct`, `total_boardings`, `is_low_volume_stop`,
`confidence`. Filter out `confidence = "LOW"` before ranking stops, or the
list will be topped by stops with three observations.

### `VW_DELAY_DISTRIBUTION` — delay class × time band × route
Use `severity_order` and `time_band_order` to sort axes correctly; alphabetical
order would put SEVERE before ON_TIME.

### `VW_DELAY_PROPAGATION` — one row per upstream → downstream relationship
Key columns: `mechanism`, `confidence`, `confidence_rank`,
`propagated_delay_min`, `corroborated_by_incident`, `calculation`, `evidence`.
`SHARED_STOP` rows are correlation only; never present them as cause.

### `VW_CONNECTION_RISK` — one row per timetabled transfer
Key columns: `available_transfer_min`, `required_buffer_min`, `shortfall_min`,
`status`, `risk_score`, `planned_connection_tight`, and the pre-aggregated
flags `at_risk_count`, `missed_count`, `assessed_count`, `unassessed_count`.
Divide by `assessed_count`, not by the row count: connections whose feeder had
no GPS record cannot be judged either way.

### `VW_PASSENGER_IMPACT` — route × stop × time band
Key columns: `passengers_exposed`, `passenger_minutes_lost`,
`estimated_transferring`, `low_confidence_events`, `is_estimate` (always true).

### `VW_RECOVERY_ANALYTICS` — one row per trip
Key columns: `max_delay_min`, `final_delay_min`, `recovery_min`, `was_delayed`,
`recovered`, `data_confidence`.

### `VW_SENSITIVITY_ANALYSIS` — one row per tested buffer
Key columns: `required_buffer_min`, `at_risk_connections`,
`missed_connections`, `passengers_at_risk`, `delta_at_risk_vs_lowest_buffer`.

### `VW_DATA_QUALITY` — one row per route
Key columns: `coverage_pct`, `coverage_rating`, `missing_gps`,
`excluded_diverted`, `estimated_values`.

### `VW_EXECUTIVE_SUMMARY` — a single row
Convenient for card visuals when you do not need slicer interaction. Because
it is one pre-aggregated row it will not respond to filters; use the measures
in `DAX_MEASURES.md` for anything interactive.

## Dimensions (from `TRANSITPULSE.CORE`)

`DIM_DATE`, `DIM_ROUTE`, `DIM_STOP`, `DIM_VEHICLE`, `DIM_TRIP`,
`DIM_TIME_BAND`, `DIM_DELAY_CLASS`.

Import these as well as the views: they give you clean slicers and let one
date slicer filter every page.
