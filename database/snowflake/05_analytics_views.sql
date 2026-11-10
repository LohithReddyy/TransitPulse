-- =====================================================================
-- TransitPulse - ANALYTICS views (Power BI ready)
--
-- Each view is denormalised enough to drop straight onto a Power BI
-- canvas, while the star schema underneath stays the source of truth.
-- =====================================================================
USE DATABASE TRANSITPULSE;
USE SCHEMA ANALYTICS;

-- ------------------------------------------------- 1. route performance
CREATE OR REPLACE VIEW VW_ROUTE_PERFORMANCE AS
SELECT
    r.route_id,
    r.route_name,
    r.mode,
    d.calendar_date                                      AS service_date,
    COUNT(DISTINCT f.trip_key)                           AS trips_operated,
    SUM(f.is_assessable)                                 AS observations,
    SUM(CASE WHEN f.is_missing THEN 1 ELSE 0 END)        AS missing_observations,
    ROUND(AVG(f.delay_min), 2)                           AS avg_delay_min,
    ROUND(MEDIAN(f.delay_min), 2)                        AS median_delay_min,
    ROUND(PERCENTILE_CONT(0.90) WITHIN GROUP (ORDER BY f.delay_min), 2)
                                                         AS p90_delay_min,
    ROUND(MAX(f.delay_min), 2)                           AS max_delay_min,
    SUM(f.is_on_time)                                    AS on_time_observations,
    SUM(f.is_delayed)                                    AS delayed_observations,
    ROUND(100.0 * SUM(f.is_on_time) / NULLIF(SUM(f.is_assessable), 0), 2)
                                                         AS on_time_percentage,
    -- data coverage matters: a high score on thin data is not reliable
    ROUND(100.0 * SUM(f.is_assessable) / NULLIF(COUNT(*), 0), 2)
                                                         AS data_coverage_percentage
FROM CORE.FACT_DELAY_EVENT f
JOIN CORE.DIM_ROUTE r ON r.route_key = f.route_key
JOIN CORE.DIM_DATE  d ON d.date_key  = f.date_key
GROUP BY r.route_id, r.route_name, r.mode, d.calendar_date;

-- --------------------------------------------------- 2. stop performance
CREATE OR REPLACE VIEW VW_STOP_PERFORMANCE AS
SELECT
    s.stop_id,
    s.stop_name,
    s.stop_type,
    s.is_interchange,
    s.total_boardings,
    s.is_low_volume_stop,
    COUNT(DISTINCT f.route_key)                      AS routes_serving,
    SUM(f.is_assessable)                             AS observations,
    ROUND(AVG(f.delay_min), 2)                       AS avg_delay_min,
    ROUND(MAX(f.delay_min), 2)                       AS max_delay_min,
    SUM(f.is_delayed)                                AS delayed_observations,
    ROUND(100.0 * SUM(f.is_delayed) / NULLIF(SUM(f.is_assessable), 0), 2)
                                                     AS delay_frequency_pct,
    ROUND(AVG(f.dwell_min), 2)                       AS avg_dwell_min,
    -- flag figures that rest on too little data to quote confidently
    CASE WHEN SUM(f.is_assessable) < 5 OR s.is_low_volume_stop
         THEN 'LOW' ELSE 'OK' END                    AS confidence
FROM CORE.DIM_STOP s
LEFT JOIN CORE.FACT_DELAY_EVENT f ON f.stop_key = s.stop_key
GROUP BY s.stop_id, s.stop_name, s.stop_type, s.is_interchange,
         s.total_boardings, s.is_low_volume_stop;

-- ------------------------------------------------ 3. delay distribution
CREATE OR REPLACE VIEW VW_DELAY_DISTRIBUTION AS
SELECT
    dc.delay_class_key    AS delay_class,
    dc.delay_class_name,
    dc.severity_order,
    dc.definition,
    tb.time_band_key      AS time_band,
    tb.time_band_name,
    tb.sort_order         AS time_band_order,
    r.route_id,
    r.route_name,
    COUNT(*)              AS observation_count,
    ROUND(AVG(f.delay_min), 2) AS avg_delay_min
FROM CORE.FACT_DELAY_EVENT f
JOIN CORE.DIM_DELAY_CLASS dc ON dc.delay_class_key = f.delay_class_key
JOIN CORE.DIM_TIME_BAND   tb ON tb.time_band_key   = f.time_band_key
JOIN CORE.DIM_ROUTE       r  ON r.route_key        = f.route_key
GROUP BY dc.delay_class_key, dc.delay_class_name, dc.severity_order,
         dc.definition, tb.time_band_key, tb.time_band_name, tb.sort_order,
         r.route_id, r.route_name;

-- ------------------------------------------------ 4. delay propagation
CREATE OR REPLACE VIEW VW_DELAY_PROPAGATION AS
SELECT
    p.mechanism,
    p.confidence,
    p.confidence_rank,
    sr.route_id    AS source_route_id,
    sr.route_name  AS source_route_name,
    ar.route_id    AS affected_route_id,
    ar.route_name  AS affected_route_name,
    p.source_trip_key    AS source_trip_id,
    p.affected_trip_key  AS affected_trip_id,
    ss.stop_name         AS source_stop_name,
    d.calendar_date      AS service_date,
    p.source_delay_min,
    p.affected_delay_min,
    p.propagated_delay_min,
    p.lag_min,
    p.vehicle_key        AS vehicle_id,
    p.incident_id,
    CASE WHEN p.incident_id IS NOT NULL THEN TRUE ELSE FALSE END
                         AS corroborated_by_incident,
    p.calculation,
    p.evidence,
    p.propagation_link_count
FROM CORE.FACT_PROPAGATION p
JOIN CORE.DIM_ROUTE sr ON sr.route_key = p.source_route_key
JOIN CORE.DIM_ROUTE ar ON ar.route_key = p.affected_route_key
LEFT JOIN CORE.DIM_STOP ss ON ss.stop_key = p.source_stop_key
LEFT JOIN CORE.DIM_DATE  d ON d.date_key  = p.date_key;

-- ------------------------------------------------- 5. connection risk
CREATE OR REPLACE VIEW VW_CONNECTION_RISK AS
SELECT
    c.connection_id,
    s.stop_id            AS interchange_stop_id,
    s.stop_name          AS interchange_stop_name,
    fr.route_id          AS feeder_route_id,
    fr.route_name        AS feeder_route_name,
    cr.route_id          AS connecting_route_id,
    cr.route_name        AS connecting_route_name,
    d.calendar_date      AS service_date,
    c.feeder_delay_min,
    c.planned_transfer_min,
    c.available_transfer_min,
    c.required_buffer_min,
    c.shortfall_min,
    c.status,
    c.risk_score,
    c.transferring_passengers,
    c.planned_connection_tight,
    c.at_risk_count,
    c.missed_count,
    c.assessed_count,
    c.unassessed_count,
    c.passengers_at_risk,
    c.passengers_missed
FROM CORE.FACT_CONNECTION_RISK c
LEFT JOIN CORE.DIM_STOP  s  ON s.stop_key   = c.stop_key
LEFT JOIN CORE.DIM_ROUTE fr ON fr.route_key = c.feeder_route_key
LEFT JOIN CORE.DIM_ROUTE cr ON cr.route_key = c.connecting_route_key
LEFT JOIN CORE.DIM_DATE  d  ON d.date_key   = c.date_key;

-- ------------------------------------------------- 6. passenger impact
CREATE OR REPLACE VIEW VW_PASSENGER_IMPACT AS
SELECT
    r.route_id,
    r.route_name,
    s.stop_id,
    s.stop_name,
    tb.time_band_key  AS time_band,
    tb.time_band_name,
    d.calendar_date   AS service_date,
    COUNT(*)                                      AS delayed_stop_events,
    SUM(f.passengers_exposed)                     AS passengers_exposed,
    ROUND(SUM(f.passenger_minutes_lost), 1)       AS passenger_minutes_lost,
    ROUND(AVG(f.delay_min), 2)                    AS avg_delay_min,
    ROUND(SUM(f.estimated_transferring), 1)       AS estimated_transferring,
    ROUND(SUM(f.passenger_minutes_lost)
          / NULLIF(SUM(f.passengers_exposed), 0), 2)
                                                  AS avg_added_wait_per_passenger_min,
    SUM(CASE WHEN f.confidence IN ('LOW', 'NONE') THEN 1 ELSE 0 END)
                                                  AS low_confidence_events,
    MAX(CASE WHEN f.is_low_volume THEN 'Y' ELSE 'N' END)
                                                  AS includes_low_volume_stop,
    -- every row here is modelled, never a counted passenger total
    TRUE                                          AS is_estimate
FROM CORE.FACT_PASSENGER_IMPACT f
JOIN CORE.DIM_ROUTE     r  ON r.route_key      = f.route_key
JOIN CORE.DIM_STOP      s  ON s.stop_key       = f.stop_key
JOIN CORE.DIM_TIME_BAND tb ON tb.time_band_key = f.time_band_key
LEFT JOIN CORE.DIM_DATE d  ON d.date_key       = f.date_key
GROUP BY r.route_id, r.route_name, s.stop_id, s.stop_name,
         tb.time_band_key, tb.time_band_name, d.calendar_date;

-- ---------------------------------------------- 7. recovery analytics
CREATE OR REPLACE VIEW VW_RECOVERY_ANALYTICS AS
WITH trip_profile AS (
    SELECT
        f.trip_key,
        f.route_key,
        f.date_key,
        MAX(f.delay_min)                                      AS max_delay_min,
        -- the delay at the last stop where we actually observed the vehicle
        MAX_BY(f.delay_min, f.stop_sequence)                   AS final_delay_min,
        MIN(CASE WHEN f.is_delayed = 1 THEN f.stop_sequence END) AS first_delayed_sequence,
        COUNT(*)                                              AS stops_total,
        SUM(f.is_assessable)                                  AS stops_observed
    FROM CORE.FACT_DELAY_EVENT f
    WHERE f.delay_min IS NOT NULL
    GROUP BY f.trip_key, f.route_key, f.date_key
)
SELECT
    r.route_id,
    r.route_name,
    d.calendar_date                       AS service_date,
    tp.trip_key                           AS trip_id,
    tp.max_delay_min,
    tp.final_delay_min,
    ROUND(tp.max_delay_min - tp.final_delay_min, 2) AS recovery_min,
    tp.first_delayed_sequence,
    tp.stops_total,
    tp.stops_observed,
    ROUND(100.0 * tp.stops_observed / NULLIF(tp.stops_total, 0), 1)
                                          AS coverage_pct,
    CASE WHEN tp.max_delay_min >= 2 THEN 1 ELSE 0 END  AS was_delayed,
    -- a trip only counts as recovered if it was genuinely delayed first
    CASE WHEN tp.max_delay_min >= 2
              AND tp.max_delay_min - tp.final_delay_min >= 1
              AND tp.final_delay_min < 2
         THEN 1 ELSE 0 END                             AS recovered,
    CASE WHEN tp.stops_observed >= 0.9 * tp.stops_total THEN 'HIGH'
         WHEN tp.stops_observed >= 0.6 * tp.stops_total THEN 'MEDIUM'
         ELSE 'LOW' END                                AS data_confidence
FROM trip_profile tp
JOIN CORE.DIM_ROUTE r ON r.route_key = tp.route_key
LEFT JOIN CORE.DIM_DATE d ON d.date_key = tp.date_key;

-- -------------------------------------------- 8. sensitivity analysis
CREATE OR REPLACE VIEW VW_SENSITIVITY_ANALYSIS AS
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
    delta_passengers_vs_lowest_buffer,
    ROUND(100.0 * at_risk_connections / NULLIF(connections_assessed, 0), 2)
        AS at_risk_share_pct
FROM CORE.FACT_SENSITIVITY;

-- ------------------------------------------------ 9. executive summary
CREATE OR REPLACE VIEW VW_EXECUTIVE_SUMMARY AS
SELECT
    (SELECT COUNT(*) FROM CORE.DIM_ROUTE)                          AS total_routes,
    (SELECT COUNT(*) FROM CORE.DIM_STOP)                           AS total_stops,
    (SELECT COUNT(*) FROM CORE.DIM_TRIP)                           AS total_trips,
    (SELECT COUNT(*) FROM CORE.FACT_DELAY_EVENT)                   AS total_movements,
    (SELECT SUM(is_assessable) FROM CORE.FACT_DELAY_EVENT)         AS observed_movements,
    (SELECT COUNT(*) FROM CORE.FACT_DELAY_EVENT WHERE is_missing)  AS missing_movements,
    (SELECT ROUND(100.0 * SUM(is_on_time) / NULLIF(SUM(is_assessable), 0), 2)
     FROM CORE.FACT_DELAY_EVENT)                                   AS on_time_percentage,
    (SELECT ROUND(AVG(delay_min), 2) FROM CORE.FACT_DELAY_EVENT)   AS avg_delay_min,
    (SELECT COUNT(*) FROM CORE.FACT_PROPAGATION)                   AS propagation_links,
    (SELECT COUNT(*) FROM CORE.FACT_PROPAGATION WHERE confidence = 'HIGH')
                                                                   AS high_confidence_links,
    (SELECT SUM(at_risk_count) FROM CORE.FACT_CONNECTION_RISK)     AS connections_at_risk,
    (SELECT SUM(missed_count)  FROM CORE.FACT_CONNECTION_RISK)     AS missed_connections,
    (SELECT SUM(passengers_exposed) FROM CORE.FACT_PASSENGER_IMPACT)
                                                                   AS passengers_affected_est,
    (SELECT ROUND(SUM(passenger_minutes_lost), 0) FROM CORE.FACT_PASSENGER_IMPACT)
                                                                   AS passenger_minutes_lost_est,
    (SELECT ROUND(100.0 * SUM(recovered) / NULLIF(SUM(was_delayed), 0), 2)
     FROM VW_RECOVERY_ANALYTICS)                                   AS recovery_percentage;

-- ----------------------------------------------- 10. data quality view
CREATE OR REPLACE VIEW VW_DATA_QUALITY AS
SELECT
    r.route_id,
    r.route_name,
    COUNT(*)                                                AS total_movements,
    SUM(f.is_assessable)                                    AS observed,
    SUM(CASE WHEN f.is_missing THEN 1 ELSE 0 END)           AS missing_gps,
    SUM(CASE WHEN NOT f.comparable AND NOT f.is_missing THEN 1 ELSE 0 END)
                                                            AS excluded_diverted,
    SUM(CASE WHEN f.delay_source = 'ESTIMATED' THEN 1 ELSE 0 END)
                                                            AS estimated_values,
    ROUND(100.0 * SUM(f.is_assessable) / NULLIF(COUNT(*), 0), 2)
                                                            AS coverage_pct,
    CASE WHEN 100.0 * SUM(f.is_assessable) / NULLIF(COUNT(*), 0) >= 95 THEN 'GOOD'
         WHEN 100.0 * SUM(f.is_assessable) / NULLIF(COUNT(*), 0) >= 85 THEN 'FAIR'
         ELSE 'POOR' END                                    AS coverage_rating
FROM CORE.FACT_DELAY_EVENT f
JOIN CORE.DIM_ROUTE r ON r.route_key = f.route_key
GROUP BY r.route_id, r.route_name;

SHOW VIEWS IN SCHEMA ANALYTICS;
