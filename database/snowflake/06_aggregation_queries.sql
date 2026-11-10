-- =====================================================================
-- TransitPulse - Snowflake analytical queries
-- Representative questions a transit authority actually asks.
-- =====================================================================
USE DATABASE TRANSITPULSE;
USE SCHEMA ANALYTICS;

-- 1. Network headline numbers
SELECT * FROM VW_EXECUTIVE_SUMMARY;

-- 2. Which routes are hurting passengers most, not just running late?
--    A route can be mildly late but carry many passengers.
SELECT p.route_id,
       p.route_name,
       SUM(p.passengers_exposed)                   AS passengers_exposed,
       ROUND(SUM(p.passenger_minutes_lost), 0)     AS passenger_minutes_lost,
       ROUND(AVG(rp.on_time_percentage), 2)        AS on_time_percentage,
       ROUND(AVG(p.avg_delay_min), 2)              AS avg_delay_min
FROM VW_PASSENGER_IMPACT p
JOIN VW_ROUTE_PERFORMANCE rp ON rp.route_id = p.route_id
GROUP BY p.route_id, p.route_name
ORDER BY passenger_minutes_lost DESC;

-- 3. Propagation explained by mechanism and confidence
SELECT mechanism,
       confidence,
       COUNT(*)                                  AS links,
       ROUND(AVG(propagated_delay_min), 2)       AS avg_propagated_min,
       ROUND(SUM(propagated_delay_min), 1)       AS total_propagated_min,
       SUM(CASE WHEN corroborated_by_incident THEN 1 ELSE 0 END)
                                                 AS incident_corroborated
FROM VW_DELAY_PROPAGATION
GROUP BY mechanism, confidence
ORDER BY mechanism, confidence DESC;

-- 4. Only act on high-confidence propagation: the causal chains
SELECT source_route_name, affected_route_name, source_trip_id,
       affected_trip_id, vehicle_id, source_delay_min,
       propagated_delay_min, calculation
FROM VW_DELAY_PROPAGATION
WHERE confidence = 'HIGH'
ORDER BY propagated_delay_min DESC
LIMIT 25;

-- 5. Interchanges where transfers break down most often
SELECT interchange_stop_id,
       interchange_stop_name,
       COUNT(*)                     AS connections,
       SUM(assessed_count)          AS assessed,
       SUM(at_risk_count)           AS at_risk,
       SUM(missed_count)            AS missed,
       SUM(unassessed_count)        AS could_not_assess,
       SUM(passengers_at_risk)      AS passengers_at_risk,
       ROUND(100.0 * SUM(at_risk_count) / NULLIF(SUM(assessed_count), 0), 2)
                                    AS at_risk_pct
FROM VW_CONNECTION_RISK
GROUP BY interchange_stop_id, interchange_stop_name
ORDER BY at_risk_pct DESC NULLS LAST;

-- 6. Structural versus operational connection risk
SELECT CASE WHEN planned_connection_tight
            THEN 'Structural: timetable is tight even on time'
            ELSE 'Operational: caused by a late feeder' END AS risk_type,
       COUNT(*)                AS connections,
       SUM(at_risk_count)      AS at_risk,
       SUM(passengers_at_risk) AS passengers_at_risk
FROM VW_CONNECTION_RISK
WHERE assessed_count = 1
GROUP BY risk_type;

-- 7. Delay by time band: when does the network struggle?
SELECT time_band,
       time_band_name,
       MIN(time_band_order)                AS band_order,
       SUM(observation_count)              AS observations,
       ROUND(AVG(avg_delay_min), 2)        AS avg_delay_min
FROM VW_DELAY_DISTRIBUTION
GROUP BY time_band, time_band_name
ORDER BY band_order;

-- 8. Recovery: how good is the network at catching up?
SELECT route_id,
       route_name,
       SUM(was_delayed)   AS delayed_trips,
       SUM(recovered)     AS recovered_trips,
       ROUND(100.0 * SUM(recovered) / NULLIF(SUM(was_delayed), 0), 2)
                          AS recovery_percentage,
       ROUND(AVG(CASE WHEN recovered = 1 THEN recovery_min END), 2)
                          AS avg_recovery_min
FROM VW_RECOVERY_ANALYTICS
GROUP BY route_id, route_name
ORDER BY recovery_percentage DESC NULLS LAST;

-- 9. Sensitivity: how much of the reported risk is just the assumption?
SELECT required_buffer_min,
       at_risk_connections,
       missed_connections,
       passengers_at_risk,
       delta_at_risk_vs_lowest_buffer,
       at_risk_share_pct
FROM VW_SENSITIVITY_ANALYSIS
ORDER BY required_buffer_min;

-- 10. Data quality by route: where can the numbers be trusted?
SELECT route_id, route_name, total_movements, observed, missing_gps,
       excluded_diverted, coverage_pct, coverage_rating
FROM VW_DATA_QUALITY
ORDER BY coverage_pct ASC;

-- 11. Worst stops, with low-confidence rows called out rather than hidden
SELECT stop_id, stop_name, is_interchange, observations,
       avg_delay_min, delay_frequency_pct, total_boardings, confidence
FROM VW_STOP_PERFORMANCE
WHERE observations > 0
ORDER BY delay_frequency_pct DESC NULLS LAST
LIMIT 20;

-- 12. Daily trend, for the Power BI line charts
SELECT service_date,
       SUM(trips_operated)            AS trips,
       ROUND(AVG(on_time_percentage), 2) AS on_time_percentage,
       ROUND(AVG(avg_delay_min), 2)   AS avg_delay_min,
       SUM(delayed_observations)      AS delayed_observations
FROM VW_ROUTE_PERFORMANCE
GROUP BY service_date
ORDER BY service_date;
