-- =====================================================================
-- TransitPulse - CORE star schema
--
-- Dimensions: DIM_DATE, DIM_ROUTE, DIM_STOP, DIM_VEHICLE, DIM_TRIP,
--             DIM_TIME_BAND, DIM_DELAY_CLASS
-- Facts:      FACT_DELAY_EVENT, FACT_PROPAGATION, FACT_CONNECTION_RISK,
--             FACT_PASSENGER_IMPACT, FACT_SENSITIVITY
--
-- Built from RAW with CREATE OR REPLACE so the whole layer is rebuildable.
-- =====================================================================
USE DATABASE TRANSITPULSE;
USE SCHEMA CORE;

-- =================== DIMENSIONS ===================

CREATE OR REPLACE TABLE DIM_DATE AS
WITH bounds AS (
    SELECT MIN(service_date) AS d0, MAX(service_date) AS d1
    FROM RAW.RAW_TRIPS
),
days AS (
    SELECT DATEADD(day, SEQ4(), (SELECT d0 FROM bounds)) AS calendar_date
    FROM TABLE(GENERATOR(ROWCOUNT => 400))
    QUALIFY calendar_date <= (SELECT d1 FROM bounds)
)
SELECT
    TO_NUMBER(TO_CHAR(calendar_date, 'YYYYMMDD')) AS date_key,
    calendar_date,
    YEAR(calendar_date)                           AS calendar_year,
    QUARTER(calendar_date)                        AS calendar_quarter,
    MONTH(calendar_date)                          AS calendar_month,
    MONTHNAME(calendar_date)                      AS month_name,
    DAY(calendar_date)                            AS day_of_month,
    DAYOFWEEK(calendar_date)                      AS day_of_week,
    DAYNAME(calendar_date)                        AS day_name,
    WEEKOFYEAR(calendar_date)                     AS week_of_year,
    CASE WHEN DAYOFWEEK(calendar_date) IN (0, 6) THEN TRUE ELSE FALSE END
                                                  AS is_weekend
FROM days;

CREATE OR REPLACE TABLE DIM_ROUTE AS
SELECT
    route_id                                AS route_key,
    route_id,
    route_name,
    mode,
    origin_name,
    destination_name,
    stop_count,
    status,
    description
FROM RAW.RAW_ROUTES;

CREATE OR REPLACE TABLE DIM_STOP AS
SELECT
    s.stop_id                               AS stop_key,
    s.stop_id,
    s.stop_name,
    s.stop_type,
    s.latitude,
    s.longitude,
    CASE WHEN s.is_interchange = 'Y' THEN TRUE ELSE FALSE END AS is_interchange,
    CASE WHEN s.step_free = 1 THEN TRUE ELSE FALSE END        AS is_step_free,
    COALESCE(v.total_boardings, 0)                            AS total_boardings,
    -- a stop whose whole sample is small cannot support confident statistics
    CASE WHEN COALESCE(v.total_boardings, 0) < 100 THEN TRUE ELSE FALSE END
                                                              AS is_low_volume_stop
FROM RAW.RAW_STOPS s
LEFT JOIN (
    SELECT stop_id, SUM(boardings) AS total_boardings
    FROM RAW.RAW_PASSENGER_VOLUMES
    GROUP BY stop_id
) v ON v.stop_id = s.stop_id;

CREATE OR REPLACE TABLE DIM_VEHICLE AS
SELECT
    vehicle_id      AS vehicle_key,
    vehicle_id,
    fleet_number,
    vehicle_type,
    capacity,
    depot,
    status
FROM RAW.RAW_VEHICLES;

CREATE OR REPLACE TABLE DIM_TRIP AS
SELECT
    t.trip_id        AS trip_key,
    t.trip_id,
    t.route_id,
    t.vehicle_id,
    t.service_date,
    TO_NUMBER(TO_CHAR(t.service_date, 'YYYYMMDD')) AS date_key,
    t.scheduled_start_time,
    t.direction,
    CASE t.direction WHEN 0 THEN 'OUTBOUND' ELSE 'INBOUND' END AS direction_name,
    t.service_pattern,
    t.block_id,
    t.status
FROM RAW.RAW_TRIPS t;

CREATE OR REPLACE TABLE DIM_TIME_BAND AS
SELECT * FROM VALUES
    ('EARLY',   'Early morning', 1, '05:00-06:59'),
    ('AM_PEAK', 'Morning peak',  2, '07:00-09:59'),
    ('MIDDAY',  'Midday',        3, '10:00-15:59'),
    ('PM_PEAK', 'Evening peak',  4, '16:00-18:59'),
    ('EVENING', 'Evening',       5, '19:00-23:59')
AS t (time_band_key, time_band_name, sort_order, time_range);

CREATE OR REPLACE TABLE DIM_DELAY_CLASS AS
SELECT * FROM VALUES
    ('EARLY',    'Ahead of schedule',        1, 'Arrived more than 2 min early'),
    ('ON_TIME',  'On time',                  2, 'Within 2 min of schedule'),
    ('MINOR',    'Minor delay',              3, '2 to under 5 min late'),
    ('MODERATE', 'Moderate delay',           4, '5 to under 15 min late'),
    ('SEVERE',   'Severe delay',             5, '15 min or more late'),
    ('MISSING',  'No observation available',  6,
     'No GPS arrival event, or a stop skipped by a diversion. Never counted as on time.')
AS t (delay_class_key, delay_class_name, severity_order, definition);

-- =================== FACTS ===================

-- Grain: one row per trip per stop.
CREATE OR REPLACE TABLE FACT_DELAY_EVENT AS
SELECT
    de.trip_id                                      AS trip_key,
    de.route_id                                     AS route_key,
    de.stop_id                                      AS stop_key,
    de.vehicle_id                                   AS vehicle_key,
    TO_NUMBER(TO_CHAR(de.service_date, 'YYYYMMDD')) AS date_key,
    de.delay_class                                  AS delay_class_key,
    CASE
        WHEN HOUR(de.scheduled_arrival) < 7  THEN 'EARLY'
        WHEN HOUR(de.scheduled_arrival) < 10 THEN 'AM_PEAK'
        WHEN HOUR(de.scheduled_arrival) < 16 THEN 'MIDDAY'
        WHEN HOUR(de.scheduled_arrival) < 19 THEN 'PM_PEAK'
        ELSE 'EVENING'
    END                                             AS time_band_key,
    de.stop_sequence,
    de.scheduled_arrival,
    de.actual_arrival,
    de.delay_min,
    de.delay_source,
    de.dwell_min,
    de.is_observed,
    de.is_missing,
    de.comparable,
    -- measures pre-computed so Power BI DAX stays simple
    CASE WHEN de.delay_min IS NOT NULL AND de.delay_min < 2  THEN 1 ELSE 0 END AS is_on_time,
    CASE WHEN de.delay_min IS NOT NULL AND de.delay_min >= 2 THEN 1 ELSE 0 END AS is_delayed,
    CASE WHEN de.delay_min IS NOT NULL THEN 1 ELSE 0 END                       AS is_assessable
FROM RAW.RAW_DELAY_EVENTS de;

-- Grain: one row per (source trip -> affected trip) relationship.
CREATE OR REPLACE TABLE FACT_PROPAGATION AS
SELECT
    pr.source_trip_id    AS source_trip_key,
    pr.source_route_id   AS source_route_key,
    pr.source_stop_id    AS source_stop_key,
    pr.affected_trip_id  AS affected_trip_key,
    pr.affected_route_id AS affected_route_key,
    pr.affected_stop_id  AS affected_stop_key,
    t.date_key,
    pr.mechanism,
    pr.confidence,
    CASE pr.confidence WHEN 'HIGH' THEN 3 WHEN 'MEDIUM' THEN 2 ELSE 1 END
                         AS confidence_rank,
    pr.source_delay_min,
    pr.affected_delay_min,
    pr.propagated_delay_min,
    pr.lag_min,
    pr.vehicle_id        AS vehicle_key,
    pr.incident_id,
    pr.calculation,
    pr.evidence,
    1                    AS propagation_link_count
FROM RAW.RAW_PROPAGATION_RESULTS pr
LEFT JOIN DIM_TRIP t ON t.trip_id = pr.source_trip_id;

-- Grain: one row per timetabled transfer opportunity.
CREATE OR REPLACE TABLE FACT_CONNECTION_RISK AS
SELECT
    cr.connection_id,
    cr.interchange_stop_id  AS stop_key,
    cr.feeder_trip_id       AS feeder_trip_key,
    cr.feeder_route_id      AS feeder_route_key,
    cr.connecting_trip_id   AS connecting_trip_key,
    cr.connecting_route_id  AS connecting_route_key,
    t.date_key,
    cr.feeder_scheduled_arrival,
    cr.feeder_actual_arrival,
    cr.feeder_delay_min,
    cr.connecting_scheduled_departure,
    cr.planned_transfer_min,
    cr.available_transfer_min,
    cr.required_buffer_min,
    cr.shortfall_min,
    cr.status,
    cr.risk_score,
    cr.transferring_passengers,
    cr.planned_connection_tight,
    CASE WHEN cr.is_at_risk THEN 1 ELSE 0 END                       AS at_risk_count,
    CASE WHEN cr.is_missed  THEN 1 ELSE 0 END                       AS missed_count,
    CASE WHEN cr.status = 'UNKNOWN_NO_GPS' THEN 1 ELSE 0 END        AS unassessed_count,
    CASE WHEN cr.status <> 'UNKNOWN_NO_GPS' THEN 1 ELSE 0 END       AS assessed_count,
    CASE WHEN cr.is_at_risk THEN cr.transferring_passengers ELSE 0 END
                                                                    AS passengers_at_risk,
    CASE WHEN cr.is_missed  THEN cr.transferring_passengers ELSE 0 END
                                                                    AS passengers_missed
FROM RAW.RAW_CONNECTION_RISK cr
LEFT JOIN DIM_TRIP t ON t.trip_id = cr.feeder_trip_id;

-- Grain: one row per delayed trip/stop event with a passenger estimate.
CREATE OR REPLACE TABLE FACT_PASSENGER_IMPACT AS
SELECT
    pi.trip_id   AS trip_key,
    pi.route_id  AS route_key,
    pi.stop_id   AS stop_key,
    t.date_key,
    pi.time_band AS time_band_key,
    pi.delay_class AS delay_class_key,
    pi.stop_sequence,
    pi.delay_min,
    pi.boardings,
    pi.average_load,
    pi.passengers_exposed,
    pi.passenger_minutes_lost,
    pi.estimated_transferring,
    pi.confidence,
    CASE WHEN pi.is_low_volume = 'Y' THEN TRUE ELSE FALSE END AS is_low_volume,
    pi.has_volume_data,
    -- TRUE for every row: these are modelled estimates, not counted passengers
    TRUE AS is_estimate,
    pi.estimate_basis
FROM RAW.RAW_PASSENGER_IMPACT pi
LEFT JOIN DIM_TRIP t ON t.trip_id = pi.trip_id;

-- Grain: one row per tested transfer-buffer assumption.
CREATE OR REPLACE TABLE FACT_SENSITIVITY AS
SELECT
    required_buffer_min,
    connections_assessed,
    at_risk_connections,
    missed_connections,
    safe_connections,
    at_risk_percentage,
    passengers_at_risk,
    passengers_missed,
    additional_wait_minutes,
    delta_at_risk_vs_lowest_buffer,
    delta_passengers_vs_lowest_buffer
FROM RAW.RAW_SENSITIVITY_ANALYSIS;

-- Dimensional integrity checks: all must return zero rows.
SELECT 'fact_delay_event_orphan_route' AS check_name, COUNT(*) AS violations
FROM FACT_DELAY_EVENT f
LEFT JOIN DIM_ROUTE d ON d.route_key = f.route_key
WHERE d.route_key IS NULL
UNION ALL
SELECT 'fact_delay_event_orphan_stop', COUNT(*)
FROM FACT_DELAY_EVENT f
LEFT JOIN DIM_STOP d ON d.stop_key = f.stop_key
WHERE d.stop_key IS NULL
UNION ALL
SELECT 'fact_propagation_orphan_trip', COUNT(*)
FROM FACT_PROPAGATION f
LEFT JOIN DIM_TRIP d ON d.trip_key = f.source_trip_key
WHERE d.trip_key IS NULL
UNION ALL
SELECT 'missing_class_must_have_null_delay', COUNT(*)
FROM FACT_DELAY_EVENT
WHERE delay_class_key = 'MISSING' AND delay_min IS NOT NULL;
