# Database schema

## MySQL — application database

Fourteen tables. Scripts: `database/mysql/01_schema.sql` (DDL),
`02_seed_data.sql` (generated from the CSVs), `03_load_engine_results.sql`
(engine output), `04_sample_queries.sql`.

### Entity relationships

```
routes ──┬── route_stops ──── stops
         │                      │
         ├── trips ──┬── scheduled_movements ──┘
         │           ├── actual_movements
         │           ├── connections (feeder + connecting)
         │           ├── delay_events
         │           ├── propagation_results (source + affected)
         │           └── passenger_impact
         ├── passenger_volumes
         └── incidents

vehicles ──── trips
system_configuration (standalone)
```

### Master data

#### `routes`
| Column | Type | Notes |
|--------|------|-------|
| `route_id` | VARCHAR(16) | PK |
| `route_name` | VARCHAR(128) | NOT NULL |
| `mode` | VARCHAR(16) | CHECK in BUS, TRAM, METRO, RAIL, FERRY |
| `origin_name`, `destination_name` | VARCHAR(128) | |
| `stop_count` | INT | |
| `status` | VARCHAR(16) | CHECK in ACTIVE, SUSPENDED, RETIRED |
| `description` | VARCHAR(512) | |

Index: `status`.

#### `stops`
| Column | Type | Notes |
|--------|------|-------|
| `stop_id` | VARCHAR(16) | PK |
| `stop_name` | VARCHAR(128) | NOT NULL |
| `stop_type` | VARCHAR(24) | STANDARD or INTERCHANGE |
| `latitude`, `longitude` | DECIMAL(10,6) | |
| `is_interchange` | CHAR(1) | CHECK Y/N |
| `step_free` | TINYINT(1) | |

Indexes: `is_interchange`, `stop_name`.

#### `route_stops`
The ordered stop pattern. PK `route_stop_id`; FKs to `routes` (cascade) and
`stops` (restrict); **UNIQUE `(route_id, stop_sequence)`** so a route cannot
have two stops at the same position; CHECK `stop_sequence > 0`.

#### `vehicles`
PK `vehicle_id`; UNIQUE `fleet_number`; CHECK `capacity >= 0`.

#### `trips`
| Column | Type | Notes |
|--------|------|-------|
| `trip_id` | VARCHAR(16) | PK |
| `route_id` | VARCHAR(16) | FK → routes |
| `vehicle_id` | VARCHAR(16) | FK → vehicles, nullable |
| `service_date` | DATE | NOT NULL |
| `scheduled_start_time` | DATETIME | NOT NULL |
| `direction` | TINYINT | CHECK 0/1 |
| `block_id` | VARCHAR(32) | Groups a vehicle's consecutive trips |

Indexes: `(route_id, service_date)`, `(vehicle_id, scheduled_start_time)`,
`block_id`. The second is what makes vehicle-continuity analysis efficient.

### Movements

#### `scheduled_movements`
PK `scheduled_movement_id`; FKs to `trips` (cascade) and `stops`;
UNIQUE `(trip_id, stop_sequence)`. Indexes `(stop_id, scheduled_arrival)`,
`route_id`.

#### `actual_movements`
| Column | Type | Notes |
|--------|------|-------|
| `actual_movement_id` | VARCHAR(32) | PK |
| `trip_id`, `stop_id` | VARCHAR(16) | FKs |
| `stop_sequence` | INT | UNIQUE with `trip_id` |
| `actual_arrival` | DATETIME | **NULLABLE — deliberately** |
| `actual_departure` | DATETIME | NULLABLE |
| `observed_delay_min` | DECIMAL(8,2) | NULLABLE |
| `data_quality_flag` | VARCHAR(24) | CHECK in OK, MISSING_GPS, DIVERTED_SKIPPED, SUSPECT, ESTIMATED |
| `is_diverted` | CHAR(1) | CHECK Y/N |

`actual_arrival` is nullable so a missing GPS event is representable as
*unknown*. A NOT NULL column with a sentinel default would make it
indistinguishable from an on-time arrival, biasing performance upward exactly
where telemetry is worst.

Indexes: `data_quality_flag`, `(stop_id, actual_arrival)`.

#### `passenger_volumes`
Average boardings, alightings, load and wait per route, stop and time band.
CHECK on `time_band` (EARLY, AM_PEAK, MIDDAY, PM_PEAK, EVENING) and on
non-negative counts. `is_low_volume` marks samples too small to quote
confidently. Indexes `(route_id, stop_id, time_band)`, `is_low_volume`.

#### `connections`
Timetabled transfer opportunities. FKs to `stops` and twice to `trips`
(cascade). CHECK `feeder_trip_id <> connecting_trip_id` — a service cannot
connect with itself.

#### `incidents`
Operator annotations. CHECKs on `severity`, `status`, and
`end_time IS NULL OR end_time >= start_time`. `created_at` / `updated_at`
maintained by MySQL. Indexes `(route_id, start_time)`, `status`,
`incident_type`.

### Analytical tables (written by the engine)

#### `delay_events`
One row per trip and stop. `delay_min` nullable; CHECK on `delay_class`
(EARLY, ON_TIME, MINOR, MODERATE, SEVERE, MISSING) and `delay_source`
(OBSERVED, ESTIMATED, UNAVAILABLE). UNIQUE `(trip_id, stop_sequence)`.

#### `propagation_results`
One row per upstream → downstream relationship, with `mechanism`,
`confidence`, `calculation` and `evidence`.

**UNIQUE `(source_trip_id, affected_trip_id)`** — the database enforces the
de-duplication rule, so the invariant holds even if a future loader forgets it.

CHECKs on `mechanism` (VEHICLE_CONTINUITY, CONNECTION, SHARED_STOP) and
`confidence` (LOW, MEDIUM, HIGH). FK to `incidents` with ON DELETE SET NULL,
so deleting an annotation does not delete the analysis.

#### `passenger_impact`
Estimated exposure per delayed stop event.

```sql
CONSTRAINT chk_pi_estimate CHECK (is_estimate = 1)
```

That constraint is intentional: there is no path by which a row in this table
is a measured passenger count, and the schema says so.

#### `system_configuration`
`config_key` PK, with `value_type`, `category`, `description` and optional
`min_value` / `max_value` bounds that the API enforces.

### Views

| View | Purpose |
|------|---------|
| `v_route_performance` | Per-route trips, averages, on-time percentage, missing count |
| `v_stop_performance` | Per-stop delay frequency and boardings |
| `v_propagation_detail` | Propagation joined to route names |
| `v_passenger_impact_by_route` | Route-level impact aggregates |
| `v_data_quality` | Record counts by data quality flag |


### Analytics views

`VW_ROUTE_PERFORMANCE`, `VW_STOP_PERFORMANCE`, `VW_DELAY_DISTRIBUTION`,
`VW_DELAY_PROPAGATION`, `VW_CONNECTION_RISK`, `VW_PASSENGER_IMPACT`,
`VW_RECOVERY_ANALYTICS`, `VW_SENSITIVITY_ANALYSIS`, `VW_EXECUTIVE_SUMMARY`,
`VW_DATA_QUALITY`.

Each is denormalised enough to drop straight onto a Power BI canvas while the
star schema underneath remains the source of truth.

### Integrity checks

`03_star_schema.sql` ends with checks that must all return zero:

- no fact row referencing a missing route or stop
- no propagation row referencing a missing trip
- **no row where `delay_class_key = 'MISSING'` has a non-null `delay_min`**

The last one guards the project's central invariant at the warehouse boundary.

---

## Loading order

```
# MySQL
01_schema.sql → 02_seed_data.sql → (run the engine) → 03_load_engine_results.sql

# Snowflake
01_database_setup.sql → 02_raw_tables.sql → PUT the CSVs
  → 04_load_data.sql → 03_star_schema.sql → 05_analytics_views.sql
```

`03_star_schema.sql` runs *after* loading because it builds `CORE` from `RAW`
with `CREATE OR REPLACE ... AS SELECT`. Rerunning it rebuilds the whole
dimensional layer, which is the intended way to refresh it.
