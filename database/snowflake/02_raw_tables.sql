-- =====================================================================
-- TransitPulse - RAW landing tables
-- Mirror the CSV files one-to-one. Nothing is cleaned here, so a reload
-- is always repeatable and the raw record is auditable.
-- =====================================================================
USE DATABASE TRANSITPULSE;
USE SCHEMA RAW;

CREATE OR REPLACE TABLE RAW_ROUTES (
    route_id         STRING,
    route_name       STRING,
    mode             STRING,
    origin_name      STRING,
    destination_name STRING,
    stop_count       NUMBER(5,0),
    status           STRING,
    description      STRING
);

CREATE OR REPLACE TABLE RAW_STOPS (
    stop_id        STRING,
    stop_name      STRING,
    stop_type      STRING,
    latitude       NUMBER(10,6),
    longitude      NUMBER(10,6),
    is_interchange STRING,
    step_free      NUMBER(1,0)
);

CREATE OR REPLACE TABLE RAW_ROUTE_STOPS (
    route_stop_id           STRING,
    route_id                STRING,
    stop_id                 STRING,
    stop_sequence           NUMBER(5,0),
    distance_from_origin_km NUMBER(8,2),
    scheduled_runtime_min   NUMBER(6,0)
);

CREATE OR REPLACE TABLE RAW_VEHICLES (
    vehicle_id   STRING,
    fleet_number STRING,
    vehicle_type STRING,
    capacity     NUMBER(6,0),
    depot        STRING,
    status       STRING
);

CREATE OR REPLACE TABLE RAW_TRIPS (
    trip_id              STRING,
    route_id             STRING,
    vehicle_id           STRING,
    service_date         DATE,
    scheduled_start_time TIMESTAMP_NTZ,
    direction            NUMBER(1,0),
    service_pattern      STRING,
    block_id             STRING,
    status               STRING
);

CREATE OR REPLACE TABLE RAW_SCHEDULED_MOVEMENTS (
    scheduled_movement_id STRING,
    trip_id               STRING,
    route_id              STRING,
    stop_id               STRING,
    stop_sequence         NUMBER(5,0),
    scheduled_arrival     TIMESTAMP_NTZ,
    scheduled_departure   TIMESTAMP_NTZ,
    is_origin             NUMBER(1,0),
    is_destination        NUMBER(1,0)
);

-- actual_arrival is nullable: a missing GPS event is unknown, not on time.
CREATE OR REPLACE TABLE RAW_ACTUAL_MOVEMENTS (
    actual_movement_id STRING,
    trip_id            STRING,
    route_id           STRING,
    stop_id            STRING,
    stop_sequence      NUMBER(5,0),
    actual_arrival     TIMESTAMP_NTZ,
    actual_departure   TIMESTAMP_NTZ,
    observed_delay_min NUMBER(8,2),
    data_quality_flag  STRING,
    is_diverted        STRING
);

CREATE OR REPLACE TABLE RAW_PASSENGER_VOLUMES (
    passenger_volume_id STRING,
    route_id            STRING,
    stop_id             STRING,
    stop_sequence       NUMBER(5,0),
    time_band           STRING,
    boardings           NUMBER(8,0),
    alightings          NUMBER(8,0),
    average_load        NUMBER(8,0),
    average_wait_min    NUMBER(6,2),
    is_low_volume       STRING
);

CREATE OR REPLACE TABLE RAW_CONNECTIONS (
    connection_id                   STRING,
    interchange_stop_id             STRING,
    feeder_trip_id                  STRING,
    feeder_route_id                 STRING,
    connecting_trip_id              STRING,
    connecting_route_id             STRING,
    feeder_scheduled_arrival        TIMESTAMP_NTZ,
    connecting_scheduled_departure  TIMESTAMP_NTZ,
    planned_transfer_min            NUMBER(6,2),
    required_buffer_min             NUMBER(6,2),
    typical_transferring_passengers NUMBER(8,0)
);

CREATE OR REPLACE TABLE RAW_INCIDENTS (
    incident_id   STRING,
    incident_type STRING,
    route_id      STRING,
    stop_id       STRING,
    start_time    TIMESTAMP_NTZ,
    end_time      TIMESTAMP_NTZ,
    severity      STRING,
    description   STRING,
    is_diversion  STRING,
    status        STRING,
    reported_by   STRING
);

CREATE OR REPLACE TABLE RAW_DIVERSIONS (
    diversion_id          STRING,
    trip_id               STRING,
    route_id              STRING,
    skipped_stop_id       STRING,
    replacement_stop_id   STRING,
    skipped_stop_sequence NUMBER(5,0),
    notes                 STRING,
    service_date          DATE
);

-- ---------------- engine output tables ----------------
CREATE OR REPLACE TABLE RAW_DELAY_EVENTS (
    scheduled_movement_id STRING,
    trip_id               STRING,
    route_id              STRING,
    stop_id               STRING,
    stop_sequence         NUMBER(5,0),
    scheduled_arrival     TIMESTAMP_NTZ,
    scheduled_departure   TIMESTAMP_NTZ,
    is_origin             NUMBER(1,0),
    is_destination        NUMBER(1,0),
    actual_arrival        TIMESTAMP_NTZ,
    actual_departure      TIMESTAMP_NTZ,
    data_quality_flag     STRING,
    is_observed           BOOLEAN,
    is_diverted           STRING,
    comparable            BOOLEAN,
    vehicle_id            STRING,
    block_id              STRING,
    service_date          DATE,
    direction             NUMBER(1,0),
    delay_min             NUMBER(8,2),
    delay_source          STRING,
    delay_class           STRING,
    is_delayed            BOOLEAN,
    is_missing            BOOLEAN,
    dwell_min             NUMBER(8,2),
    schedule_adherence_sec NUMBER(10,0)
);

CREATE OR REPLACE TABLE RAW_PROPAGATION_RESULTS (
    source_trip_id       STRING,
    source_route_id      STRING,
    source_stop_id       STRING,
    source_delay_min     NUMBER(8,2),
    affected_trip_id     STRING,
    affected_route_id    STRING,
    affected_stop_id     STRING,
    affected_delay_min   NUMBER(8,2),
    propagated_delay_min NUMBER(8,2),
    mechanism            STRING,
    confidence           STRING,
    lag_min              NUMBER(8,2),
    vehicle_id           STRING,
    evidence             STRING,
    calculation          STRING,
    incident_id          STRING
);

CREATE OR REPLACE TABLE RAW_CONNECTION_RISK (
    connection_id                  STRING,
    interchange_stop_id            STRING,
    feeder_trip_id                 STRING,
    feeder_route_id                STRING,
    connecting_trip_id             STRING,
    connecting_route_id            STRING,
    feeder_scheduled_arrival       TIMESTAMP_NTZ,
    feeder_actual_arrival          TIMESTAMP_NTZ,
    feeder_delay_min               NUMBER(8,2),
    connecting_scheduled_departure TIMESTAMP_NTZ,
    connecting_actual_departure    TIMESTAMP_NTZ,
    planned_transfer_min           NUMBER(8,2),
    available_transfer_min         NUMBER(8,2),
    available_transfer_actual_min  NUMBER(8,2),
    required_buffer_min            NUMBER(8,2),
    shortfall_min                  NUMBER(8,2),
    planned_connection_tight       BOOLEAN,
    is_at_risk                     BOOLEAN,
    is_missed                      BOOLEAN,
    status                         STRING,
    transferring_passengers        NUMBER(8,0),
    risk_score                     NUMBER(6,3),
    explanation                    STRING
);

CREATE OR REPLACE TABLE RAW_PASSENGER_IMPACT (
    trip_id                STRING,
    route_id               STRING,
    stop_id                STRING,
    stop_sequence          NUMBER(5,0),
    time_band              STRING,
    scheduled_arrival      TIMESTAMP_NTZ,
    actual_arrival         TIMESTAMP_NTZ,
    delay_min              NUMBER(8,2),
    delay_class            STRING,
    boardings              NUMBER(8,0),
    average_load           NUMBER(8,0),
    passengers_exposed     NUMBER(10,0),
    passenger_minutes_lost NUMBER(14,2),
    estimated_transferring NUMBER(10,2),
    is_low_volume          STRING,
    confidence             STRING,
    estimate_basis         STRING,
    is_estimate            BOOLEAN,
    has_volume_data        BOOLEAN
);

CREATE OR REPLACE TABLE RAW_SENSITIVITY_ANALYSIS (
    required_buffer_min              NUMBER(8,2),
    connections_assessed             NUMBER(8,0),
    at_risk_connections              NUMBER(8,0),
    missed_connections               NUMBER(8,0),
    safe_connections                 NUMBER(8,0),
    at_risk_percentage               NUMBER(8,2),
    passengers_at_risk               NUMBER(10,0),
    passengers_missed                NUMBER(10,0),
    additional_wait_minutes          NUMBER(12,2),
    delta_at_risk_vs_lowest_buffer   NUMBER(8,0),
    delta_passengers_vs_lowest_buffer NUMBER(10,0)
);

SHOW TABLES IN SCHEMA RAW;
