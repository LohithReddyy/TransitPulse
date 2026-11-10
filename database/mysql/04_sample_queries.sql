-- =====================================================================
-- TransitPulse - representative analytical queries (MySQL)
-- =====================================================================
USE transitpulse;

-- 1. Worst performing routes by on-time percentage
SELECT route_id, route_name, trips_total, observations,
       avg_delay_min, on_time_percentage
FROM v_route_performance
WHERE observations > 0
ORDER BY on_time_percentage ASC
LIMIT 10;

-- 2. Stops where delays occur most often, excluding low-volume noise
SELECT stop_id, stop_name, observations, avg_delay_min,
       delay_frequency_pct, total_boardings
FROM v_stop_performance
WHERE observations >= 5 AND COALESCE(is_low_volume, 'N') = 'N'
ORDER BY delay_frequency_pct DESC
LIMIT 15;

-- 3. Full delay profile of one trip, stop by stop
SELECT de.stop_sequence, s.stop_name, de.scheduled_arrival,
       de.actual_arrival, de.delay_min, de.delay_class, de.delay_source
FROM delay_events de
JOIN stops s ON s.stop_id = de.stop_id
WHERE de.trip_id = 'T0001'
ORDER BY de.stop_sequence;

-- 4. High-confidence propagation only, strongest first
SELECT mechanism, confidence, source_trip_id, source_route_name,
       affected_trip_id, affected_route_name, propagated_delay_min, calculation
FROM v_propagation_detail
WHERE confidence = 'HIGH'
ORDER BY propagated_delay_min DESC
LIMIT 20;

-- 5. How much propagation each mechanism explains
SELECT mechanism,
       confidence,
       COUNT(*)                            AS links,
       ROUND(AVG(propagated_delay_min), 2) AS avg_propagated_min,
       ROUND(MAX(propagated_delay_min), 2) AS max_propagated_min
FROM propagation_results
GROUP BY mechanism, confidence
ORDER BY mechanism, confidence;

-- 6. Routes ranked by estimated passenger impact
SELECT route_id, route_name, delayed_stop_events, passengers_exposed,
       passenger_minutes_lost, low_confidence_events
FROM v_passenger_impact_by_route
ORDER BY passenger_minutes_lost DESC;

-- 7. Delay recovery: trips that were late and then caught up
SELECT de.trip_id,
       de.route_id,
       ROUND(MAX(de.delay_min), 2)                                AS max_delay_min,
       ROUND(SUBSTRING_INDEX(GROUP_CONCAT(de.delay_min
             ORDER BY de.stop_sequence), ',', -1), 2)             AS final_delay_min
FROM delay_events de
WHERE de.delay_min IS NOT NULL
GROUP BY de.trip_id, de.route_id
HAVING max_delay_min >= 5
   AND final_delay_min < 2
ORDER BY max_delay_min DESC;

-- 8. Data quality: how much of the day is actually observed
SELECT * FROM v_data_quality ORDER BY record_count DESC;

-- 9. Incidents overlapping the biggest delays, for cause investigation
SELECT i.incident_id, i.incident_type, i.severity, i.route_id,
       i.start_time, i.end_time,
       COUNT(de.delay_event_id)    AS delay_events_in_window,
       ROUND(AVG(de.delay_min), 2) AS avg_delay_in_window
FROM incidents i
LEFT JOIN delay_events de
       ON de.route_id = i.route_id
      AND de.actual_arrival BETWEEN i.start_time AND COALESCE(i.end_time, i.start_time)
GROUP BY i.incident_id, i.incident_type, i.severity, i.route_id,
         i.start_time, i.end_time
ORDER BY delay_events_in_window DESC;

-- 10. Structurally tight connections: risky even on a perfect day
SELECT c.connection_id, c.interchange_stop_id, s.stop_name,
       c.feeder_route_id, c.connecting_route_id,
       c.planned_transfer_min, c.required_buffer_min,
       c.typical_transferring_passengers
FROM connections c
JOIN stops s ON s.stop_id = c.interchange_stop_id
WHERE c.planned_transfer_min < c.required_buffer_min
ORDER BY c.planned_transfer_min ASC;

-- 11. Current analytical configuration
SELECT category, config_key, config_value, value_type, description
FROM system_configuration
ORDER BY category, config_key;
