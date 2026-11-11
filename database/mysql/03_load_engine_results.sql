
-- =====================================================================
-- TransitPulse - load Python engine results into MySQL
--
-- Reads Python-generated CSV files and refreshes analytical tables.
--
-- Input:
--   data-engine/output/csv/
--
-- MySQL container mount:
--   /var/lib/mysql-files/transitpulse/
--
-- IMPORTANT:
--   This script performs a FULL REFRESH of the three analytical tables.
--   It does not modify operational tables.
-- =====================================================================

USE transitpulse;

-- ------------------------------------------------------------ delay_events

TRUNCATE TABLE delay_events;

LOAD DATA INFILE '/var/lib/mysql-files/transitpulse/delay_events.csv'
INTO TABLE delay_events
FIELDS TERMINATED BY ',' OPTIONALLY ENCLOSED BY '"'
LINES TERMINATED BY '\n'
IGNORE 1 LINES
(
    @scheduled_movement_id,
    @trip_id,
    @route_id,
    @stop_id,
    @stop_sequence,
    @scheduled_arrival,
    @scheduled_departure,
    @is_origin,
    @is_destination,
    @actual_arrival,
    @actual_departure,
    @data_quality_flag,
    @is_observed,
    @is_diverted,
    @vehicle_id,
    @block_id,
    @service_date,
    @direction,
    @comparable,
    @delay_min,
    @delay_source,
    @delay_class,
    @is_delayed,
    @is_missing,
    @dwell_min,
    @schedule_adherence_sec
)
SET
    trip_id           = @trip_id,
    route_id          = @route_id,
    stop_id           = @stop_id,
    stop_sequence     = @stop_sequence,
    service_date      = @service_date,
    scheduled_arrival = NULLIF(@scheduled_arrival, ''),
    actual_arrival    = NULLIF(@actual_arrival, ''),
    delay_min         = NULLIF(@delay_min, ''),
    delay_class       = @delay_class,
    delay_source      = @delay_source,
    dwell_min         = NULLIF(@dwell_min, ''),
    is_missing        = IF(@is_missing IN ('True','true','1'), 1, 0);

-- ----------------------------------------------------- propagation_results

TRUNCATE TABLE propagation_results;

LOAD DATA INFILE '/var/lib/mysql-files/transitpulse/propagation_results.csv'
INTO TABLE propagation_results
FIELDS TERMINATED BY ',' OPTIONALLY ENCLOSED BY '"'
LINES TERMINATED BY '\n'
IGNORE 1 LINES
(
    @source_trip_id,
    @source_route_id,
    @source_stop_id,
    @source_delay_min,
    @affected_trip_id,
    @affected_route_id,
    @affected_stop_id,
    @affected_delay_min,
    @propagated_delay_min,
    @mechanism,
    @confidence,
    @lag_min,
    @vehicle_id,
    @evidence,
    @calculation,
    @incident_id
)
SET
    source_trip_id       = @source_trip_id,
    source_route_id      = @source_route_id,
    source_stop_id       = @source_stop_id,
    source_delay_min     = @source_delay_min,
    affected_trip_id     = @affected_trip_id,
    affected_route_id    = @affected_route_id,
    affected_stop_id     = @affected_stop_id,
    affected_delay_min   = NULLIF(@affected_delay_min, ''),
    propagated_delay_min = @propagated_delay_min,
    mechanism            = @mechanism,
    confidence           = @confidence,
    lag_min              = @lag_min,
    vehicle_id           = NULLIF(@vehicle_id, ''),
    incident_id          = NULLIF(@incident_id, ''),
    calculation          = @calculation,
    evidence             = @evidence;

-- -------------------------------------------------------- passenger_impact

TRUNCATE TABLE passenger_impact;

LOAD DATA INFILE '/var/lib/mysql-files/transitpulse/passenger_impact.csv'
INTO TABLE passenger_impact
FIELDS TERMINATED BY ',' OPTIONALLY ENCLOSED BY '"'
LINES TERMINATED BY '\n'
IGNORE 1 LINES
(
    @trip_id,
    @route_id,
    @stop_id,
    @stop_sequence,
    @time_band,
    @scheduled_arrival,
    @actual_arrival,
    @delay_min,
    @delay_class,
    @boardings,
    @average_load,
    @passengers_exposed,
    @passenger_minutes_lost,
    @estimated_transferring,
    @is_low_volume,
    @confidence,
    @estimate_basis,
    @is_estimate,
    @has_volume_data
)
SET
    trip_id                = @trip_id,
    route_id               = @route_id,
    stop_id                = @stop_id,
    stop_sequence          = @stop_sequence,
    time_band              = @time_band,
    delay_min              = @delay_min,
    delay_class            = @delay_class,
    boardings              = @boardings,
    average_load           = @average_load,
    passengers_exposed     = @passengers_exposed,
    passenger_minutes_lost = @passenger_minutes_lost,
    estimated_transferring = @estimated_transferring,
    is_low_volume          = @is_low_volume,
    confidence             = @confidence,
    estimate_basis         = @estimate_basis,
    is_estimate            = 1;

-- --------------------------------------------------------------- verification

SELECT 'delay_events' AS table_name, COUNT(*) AS rows_loaded
FROM delay_events

UNION ALL

SELECT 'propagation_results', COUNT(*)
FROM propagation_results

UNION ALL

SELECT 'passenger_impact', COUNT(*)
FROM passenger_impact;

-- Missing delays must remain NULL.
SELECT
    'missing_delays_must_be_null' AS check_name,
    COUNT(*) AS violations
FROM delay_events
WHERE delay_class = 'MISSING'
  AND delay_min IS NOT NULL;

-- No duplicate source/affected trip pairs.
SELECT
    'no_duplicate_propagation_pairs' AS check_name,
    COUNT(*) AS violations
FROM (
    SELECT source_trip_id, affected_trip_id
    FROM propagation_results
    GROUP BY source_trip_id, affected_trip_id
    HAVING COUNT(*) > 1
) AS duplicates;
