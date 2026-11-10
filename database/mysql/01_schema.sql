-- =====================================================================
-- TransitPulse - MySQL application schema
-- CAP-15: Public Transit Delay Propagation & Passenger Impact Analysis
-- Target: MySQL 8.0+
-- =====================================================================

CREATE DATABASE IF NOT EXISTS transitpulse
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE transitpulse;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS passenger_impact;
DROP TABLE IF EXISTS propagation_results;
DROP TABLE IF EXISTS delay_events;
DROP TABLE IF EXISTS incidents;
DROP TABLE IF EXISTS connections;
DROP TABLE IF EXISTS passenger_volumes;
DROP TABLE IF EXISTS actual_movements;
DROP TABLE IF EXISTS scheduled_movements;
DROP TABLE IF EXISTS trips;
DROP TABLE IF EXISTS vehicles;
DROP TABLE IF EXISTS route_stops;
DROP TABLE IF EXISTS stops;
DROP TABLE IF EXISTS routes;
DROP TABLE IF EXISTS system_configuration;
SET FOREIGN_KEY_CHECKS = 1;

-- ---------------------------------------------------------------- routes
CREATE TABLE routes (
    route_id          VARCHAR(16)  NOT NULL,
    route_name        VARCHAR(128) NOT NULL,
    mode              VARCHAR(16)  NOT NULL DEFAULT 'BUS',
    origin_name       VARCHAR(128),
    destination_name  VARCHAR(128),
    stop_count        INT          NOT NULL DEFAULT 0,
    status            VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    description       VARCHAR(512),
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (route_id),
    CONSTRAINT chk_routes_mode   CHECK (mode IN ('BUS','TRAM','METRO','RAIL','FERRY')),
    CONSTRAINT chk_routes_status CHECK (status IN ('ACTIVE','SUSPENDED','RETIRED'))
) ENGINE = InnoDB;

CREATE INDEX idx_routes_status ON routes (status);

-- ----------------------------------------------------------------- stops
CREATE TABLE stops (
    stop_id         VARCHAR(16)  NOT NULL,
    stop_name       VARCHAR(128) NOT NULL,
    stop_type       VARCHAR(24)  NOT NULL DEFAULT 'STANDARD',
    latitude        DECIMAL(10,6),
    longitude       DECIMAL(10,6),
    is_interchange  CHAR(1)      NOT NULL DEFAULT 'N',
    step_free       TINYINT(1)   NOT NULL DEFAULT 0,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (stop_id),
    CONSTRAINT chk_stops_interchange CHECK (is_interchange IN ('Y','N'))
) ENGINE = InnoDB;

CREATE INDEX idx_stops_interchange ON stops (is_interchange);
CREATE INDEX idx_stops_name        ON stops (stop_name);

-- ------------------------------------------------------------ route_stops
-- The ordered stop pattern of each route.
CREATE TABLE route_stops (
    route_stop_id            VARCHAR(32) NOT NULL,
    route_id                 VARCHAR(16) NOT NULL,
    stop_id                  VARCHAR(16) NOT NULL,
    stop_sequence            INT         NOT NULL,
    distance_from_origin_km  DECIMAL(8,2) NOT NULL DEFAULT 0,
    scheduled_runtime_min    INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (route_stop_id),
    CONSTRAINT fk_rs_route FOREIGN KEY (route_id) REFERENCES routes (route_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_rs_stop  FOREIGN KEY (stop_id)  REFERENCES stops (stop_id)
        ON DELETE RESTRICT,
    CONSTRAINT uq_rs_route_seq UNIQUE (route_id, stop_sequence),
    CONSTRAINT chk_rs_sequence CHECK (stop_sequence > 0)
) ENGINE = InnoDB;

CREATE INDEX idx_rs_stop ON route_stops (stop_id);

-- -------------------------------------------------------------- vehicles
CREATE TABLE vehicles (
    vehicle_id    VARCHAR(16) NOT NULL,
    fleet_number  VARCHAR(32) NOT NULL,
    vehicle_type  VARCHAR(16) NOT NULL DEFAULT 'BUS',
    capacity      INT         NOT NULL DEFAULT 0,
    depot         VARCHAR(64),
    status        VARCHAR(24) NOT NULL DEFAULT 'IN_SERVICE',
    PRIMARY KEY (vehicle_id),
    CONSTRAINT uq_vehicles_fleet UNIQUE (fleet_number),
    CONSTRAINT chk_vehicles_capacity CHECK (capacity >= 0)
) ENGINE = InnoDB;

-- ----------------------------------------------------------------- trips
CREATE TABLE trips (
    trip_id              VARCHAR(16) NOT NULL,
    route_id             VARCHAR(16) NOT NULL,
    vehicle_id           VARCHAR(16),
    service_date         DATE        NOT NULL,
    scheduled_start_time DATETIME    NOT NULL,
    direction            TINYINT     NOT NULL DEFAULT 0,
    service_pattern      VARCHAR(24) NOT NULL DEFAULT 'WEEKDAY',
    block_id             VARCHAR(32),
    status               VARCHAR(24) NOT NULL DEFAULT 'SCHEDULED',
    PRIMARY KEY (trip_id),
    CONSTRAINT fk_trips_route   FOREIGN KEY (route_id)   REFERENCES routes (route_id),
    CONSTRAINT fk_trips_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicles (vehicle_id),
    CONSTRAINT chk_trips_direction CHECK (direction IN (0,1))
) ENGINE = InnoDB;

CREATE INDEX idx_trips_route_date ON trips (route_id, service_date);
CREATE INDEX idx_trips_vehicle    ON trips (vehicle_id, scheduled_start_time);
CREATE INDEX idx_trips_block      ON trips (block_id);

-- --------------------------------------------------- scheduled_movements
CREATE TABLE scheduled_movements (
    scheduled_movement_id VARCHAR(32) NOT NULL,
    trip_id               VARCHAR(16) NOT NULL,
    route_id              VARCHAR(16) NOT NULL,
    stop_id               VARCHAR(16) NOT NULL,
    stop_sequence         INT         NOT NULL,
    scheduled_arrival     DATETIME    NOT NULL,
    scheduled_departure   DATETIME    NOT NULL,
    is_origin             TINYINT(1)  NOT NULL DEFAULT 0,
    is_destination        TINYINT(1)  NOT NULL DEFAULT 0,
    PRIMARY KEY (scheduled_movement_id),
    CONSTRAINT fk_sm_trip FOREIGN KEY (trip_id) REFERENCES trips (trip_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_sm_stop FOREIGN KEY (stop_id) REFERENCES stops (stop_id),
    CONSTRAINT uq_sm_trip_seq UNIQUE (trip_id, stop_sequence)
) ENGINE = InnoDB;

CREATE INDEX idx_sm_stop_arrival ON scheduled_movements (stop_id, scheduled_arrival);
CREATE INDEX idx_sm_route        ON scheduled_movements (route_id);

-- ------------------------------------------------------ actual_movements
-- actual_arrival is NULLABLE on purpose: a missing GPS event must be
-- representable as "unknown", never as an on-time arrival.
CREATE TABLE actual_movements (
    actual_movement_id VARCHAR(32) NOT NULL,
    trip_id            VARCHAR(16) NOT NULL,
    route_id           VARCHAR(16) NOT NULL,
    stop_id            VARCHAR(16) NOT NULL,
    stop_sequence      INT         NOT NULL,
    actual_arrival     DATETIME     NULL,
    actual_departure   DATETIME     NULL,
    observed_delay_min DECIMAL(8,2) NULL,
    data_quality_flag  VARCHAR(24) NOT NULL DEFAULT 'OK',
    is_diverted        CHAR(1)     NOT NULL DEFAULT 'N',
    PRIMARY KEY (actual_movement_id),
    CONSTRAINT fk_am_trip FOREIGN KEY (trip_id) REFERENCES trips (trip_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_am_stop FOREIGN KEY (stop_id) REFERENCES stops (stop_id),
    CONSTRAINT uq_am_trip_seq UNIQUE (trip_id, stop_sequence),
    CONSTRAINT chk_am_quality CHECK (data_quality_flag IN
        ('OK','MISSING_GPS','DIVERTED_SKIPPED','SUSPECT','ESTIMATED')),
    CONSTRAINT chk_am_diverted CHECK (is_diverted IN ('Y','N'))
) ENGINE = InnoDB;

CREATE INDEX idx_am_quality ON actual_movements (data_quality_flag);
CREATE INDEX idx_am_arrival ON actual_movements (stop_id, actual_arrival);

-- ----------------------------------------------------- passenger_volumes
CREATE TABLE passenger_volumes (
    passenger_volume_id VARCHAR(48) NOT NULL,
    route_id            VARCHAR(16) NOT NULL,
    stop_id             VARCHAR(16) NOT NULL,
    stop_sequence       INT         NOT NULL DEFAULT 0,
    time_band           VARCHAR(16) NOT NULL,
    boardings           INT         NOT NULL DEFAULT 0,
    alightings          INT         NOT NULL DEFAULT 0,
    average_load        INT         NOT NULL DEFAULT 0,
    average_wait_min    DECIMAL(6,2) NOT NULL DEFAULT 0,
    is_low_volume       CHAR(1)     NOT NULL DEFAULT 'N',
    PRIMARY KEY (passenger_volume_id),
    CONSTRAINT fk_pv_route FOREIGN KEY (route_id) REFERENCES routes (route_id),
    CONSTRAINT fk_pv_stop  FOREIGN KEY (stop_id)  REFERENCES stops (stop_id),
    CONSTRAINT chk_pv_band CHECK (time_band IN
        ('EARLY','AM_PEAK','MIDDAY','PM_PEAK','EVENING')),
    CONSTRAINT chk_pv_counts CHECK (boardings >= 0 AND alightings >= 0)
) ENGINE = InnoDB;

CREATE INDEX idx_pv_route_stop ON passenger_volumes (route_id, stop_id, time_band);
CREATE INDEX idx_pv_low_volume ON passenger_volumes (is_low_volume);

-- ----------------------------------------------------------- connections
-- Timetabled passenger transfer opportunities at interchanges.
CREATE TABLE connections (
    connection_id                  VARCHAR(16) NOT NULL,
    interchange_stop_id            VARCHAR(16) NOT NULL,
    feeder_trip_id                 VARCHAR(16) NOT NULL,
    feeder_route_id                VARCHAR(16) NOT NULL,
    connecting_trip_id             VARCHAR(16) NOT NULL,
    connecting_route_id            VARCHAR(16) NOT NULL,
    feeder_scheduled_arrival       DATETIME    NOT NULL,
    connecting_scheduled_departure DATETIME    NOT NULL,
    planned_transfer_min           DECIMAL(6,2) NOT NULL,
    required_buffer_min            DECIMAL(6,2) NOT NULL DEFAULT 5.00,
    typical_transferring_passengers INT        NOT NULL DEFAULT 0,
    PRIMARY KEY (connection_id),
    CONSTRAINT fk_conn_stop    FOREIGN KEY (interchange_stop_id) REFERENCES stops (stop_id),
    CONSTRAINT fk_conn_feeder  FOREIGN KEY (feeder_trip_id)      REFERENCES trips (trip_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_conn_connect FOREIGN KEY (connecting_trip_id)  REFERENCES trips (trip_id)
        ON DELETE CASCADE,
    CONSTRAINT chk_conn_distinct CHECK (feeder_trip_id <> connecting_trip_id)
) ENGINE = InnoDB;

CREATE INDEX idx_conn_interchange ON connections (interchange_stop_id);
CREATE INDEX idx_conn_feeder      ON connections (feeder_trip_id);

-- ------------------------------------------------------------- incidents
-- Operator disruption annotations, including diversions.
CREATE TABLE incidents (
    incident_id   VARCHAR(16)  NOT NULL,
    incident_type VARCHAR(32)  NOT NULL,
    route_id      VARCHAR(16)  NOT NULL,
    stop_id       VARCHAR(16),
    start_time    DATETIME     NOT NULL,
    end_time      DATETIME,
    severity      VARCHAR(16)  NOT NULL DEFAULT 'MEDIUM',
    description   VARCHAR(1024),
    is_diversion  CHAR(1)      NOT NULL DEFAULT 'N',
    status        VARCHAR(16)  NOT NULL DEFAULT 'OPEN',
    reported_by   VARCHAR(64),
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
                                ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (incident_id),
    CONSTRAINT fk_inc_route FOREIGN KEY (route_id) REFERENCES routes (route_id),
    CONSTRAINT fk_inc_stop  FOREIGN KEY (stop_id)  REFERENCES stops (stop_id),
    CONSTRAINT chk_inc_severity CHECK (severity IN ('LOW','MEDIUM','HIGH','CRITICAL')),
    CONSTRAINT chk_inc_status   CHECK (status IN ('OPEN','MONITORING','CLOSED')),
    CONSTRAINT chk_inc_window   CHECK (end_time IS NULL OR end_time >= start_time)
) ENGINE = InnoDB;

CREATE INDEX idx_inc_route_time ON incidents (route_id, start_time);
CREATE INDEX idx_inc_status     ON incidents (status);
CREATE INDEX idx_inc_type       ON incidents (incident_type);

-- ----------------------------------------------------------- delay_events
-- One row per trip/stop, written by the Python engine.
CREATE TABLE delay_events (
    delay_event_id   BIGINT       NOT NULL AUTO_INCREMENT,
    trip_id          VARCHAR(16)  NOT NULL,
    route_id         VARCHAR(16)  NOT NULL,
    stop_id          VARCHAR(16)  NOT NULL,
    stop_sequence    INT          NOT NULL,
    service_date     DATE         NOT NULL,
    scheduled_arrival DATETIME    NOT NULL,
    actual_arrival   DATETIME     NULL,
    delay_min        DECIMAL(8,2) NULL,
    delay_class      VARCHAR(16)  NOT NULL,
    delay_source     VARCHAR(16)  NOT NULL DEFAULT 'OBSERVED',
    dwell_min        DECIMAL(6,2) NULL,
    is_missing       TINYINT(1)   NOT NULL DEFAULT 0,
    PRIMARY KEY (delay_event_id),
    CONSTRAINT fk_de_trip FOREIGN KEY (trip_id) REFERENCES trips (trip_id)
        ON DELETE CASCADE,
    CONSTRAINT uq_de_trip_seq UNIQUE (trip_id, stop_sequence),
    CONSTRAINT chk_de_class CHECK (delay_class IN
        ('EARLY','ON_TIME','MINOR','MODERATE','SEVERE','MISSING')),
    CONSTRAINT chk_de_source CHECK (delay_source IN
        ('OBSERVED','ESTIMATED','UNAVAILABLE'))
) ENGINE = InnoDB;

CREATE INDEX idx_de_route_date ON delay_events (route_id, service_date);
CREATE INDEX idx_de_class      ON delay_events (delay_class);
CREATE INDEX idx_de_stop       ON delay_events (stop_id);

-- ----------------------------------------------------- propagation_results
-- One row per traceable upstream -> downstream relationship.
CREATE TABLE propagation_results (
    propagation_id        BIGINT       NOT NULL AUTO_INCREMENT,
    source_trip_id        VARCHAR(16)  NOT NULL,
    source_route_id       VARCHAR(16)  NOT NULL,
    source_stop_id        VARCHAR(16)  NOT NULL,
    source_delay_min      DECIMAL(8,2) NOT NULL,
    affected_trip_id      VARCHAR(16)  NOT NULL,
    affected_route_id     VARCHAR(16)  NOT NULL,
    affected_stop_id      VARCHAR(16)  NOT NULL,
    affected_delay_min    DECIMAL(8,2) NULL,
    propagated_delay_min  DECIMAL(8,2) NOT NULL,
    mechanism             VARCHAR(32)  NOT NULL,
    confidence            VARCHAR(8)   NOT NULL,
    lag_min               DECIMAL(8,2) NOT NULL,
    vehicle_id            VARCHAR(16)  NULL,
    incident_id           VARCHAR(16)  NULL,
    calculation           VARCHAR(1024) NOT NULL,
    evidence              TEXT         NOT NULL,
    PRIMARY KEY (propagation_id),
    CONSTRAINT fk_pr_source   FOREIGN KEY (source_trip_id)   REFERENCES trips (trip_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_pr_affected FOREIGN KEY (affected_trip_id) REFERENCES trips (trip_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_pr_incident FOREIGN KEY (incident_id) REFERENCES incidents (incident_id)
        ON DELETE SET NULL,
    -- prevents the same affected service being counted twice for one source
    CONSTRAINT uq_pr_pair UNIQUE (source_trip_id, affected_trip_id),
    CONSTRAINT chk_pr_mechanism CHECK (mechanism IN
        ('VEHICLE_CONTINUITY','CONNECTION','SHARED_STOP')),
    CONSTRAINT chk_pr_confidence CHECK (confidence IN ('LOW','MEDIUM','HIGH'))
) ENGINE = InnoDB;

CREATE INDEX idx_pr_mechanism  ON propagation_results (mechanism);
CREATE INDEX idx_pr_confidence ON propagation_results (confidence);
CREATE INDEX idx_pr_affected   ON propagation_results (affected_trip_id);

-- ------------------------------------------------------- passenger_impact
-- All figures are ESTIMATES; is_estimate is NOT NULL DEFAULT 1 deliberately.
CREATE TABLE passenger_impact (
    impact_id              BIGINT       NOT NULL AUTO_INCREMENT,
    trip_id                VARCHAR(16)  NOT NULL,
    route_id               VARCHAR(16)  NOT NULL,
    stop_id                VARCHAR(16)  NOT NULL,
    stop_sequence          INT          NOT NULL,
    time_band              VARCHAR(16)  NOT NULL,
    delay_min              DECIMAL(8,2) NOT NULL,
    delay_class            VARCHAR(16)  NOT NULL,
    boardings              INT          NOT NULL DEFAULT 0,
    average_load           INT          NOT NULL DEFAULT 0,
    passengers_exposed     INT          NOT NULL DEFAULT 0,
    passenger_minutes_lost DECIMAL(12,2) NOT NULL DEFAULT 0,
    estimated_transferring DECIMAL(8,2) NOT NULL DEFAULT 0,
    is_low_volume          CHAR(1)      NOT NULL DEFAULT 'N',
    confidence             VARCHAR(8)   NOT NULL DEFAULT 'MEDIUM',
    estimate_basis         VARCHAR(512) NOT NULL,
    is_estimate            TINYINT(1)   NOT NULL DEFAULT 1,
    PRIMARY KEY (impact_id),
    CONSTRAINT fk_pi_trip FOREIGN KEY (trip_id) REFERENCES trips (trip_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_pi_stop FOREIGN KEY (stop_id) REFERENCES stops (stop_id),
    CONSTRAINT chk_pi_confidence CHECK (confidence IN ('NONE','LOW','MEDIUM','HIGH')),
    CONSTRAINT chk_pi_estimate   CHECK (is_estimate = 1)
) ENGINE = InnoDB;

CREATE INDEX idx_pi_route ON passenger_impact (route_id);
CREATE INDEX idx_pi_stop  ON passenger_impact (stop_id);

-- --------------------------------------------------- system_configuration
-- Analytical parameters, editable from the Settings page.
CREATE TABLE system_configuration (
    config_key   VARCHAR(64)  NOT NULL,
    config_value VARCHAR(256) NOT NULL,
    value_type   VARCHAR(16)  NOT NULL DEFAULT 'NUMBER',
    category     VARCHAR(32)  NOT NULL DEFAULT 'GENERAL',
    description  VARCHAR(512),
    min_value    DECIMAL(12,4) NULL,
    max_value    DECIMAL(12,4) NULL,
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
                               ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (config_key),
    CONSTRAINT chk_cfg_type CHECK (value_type IN ('NUMBER','STRING','BOOLEAN','LIST'))
) ENGINE = InnoDB;

-- ------------------------------------------------------------------ views
CREATE OR REPLACE VIEW v_route_performance AS
SELECT r.route_id,
       r.route_name,
       r.mode,
       COUNT(DISTINCT t.trip_id)                                  AS trips_total,
       COUNT(de.delay_event_id)                                   AS observations,
       ROUND(AVG(de.delay_min), 2)                                AS avg_delay_min,
       ROUND(MAX(de.delay_min), 2)                                AS max_delay_min,
       SUM(CASE WHEN de.delay_min < 2 THEN 1 ELSE 0 END)          AS on_time_observations,
       ROUND(100.0 * SUM(CASE WHEN de.delay_min < 2 THEN 1 ELSE 0 END)
             / NULLIF(COUNT(de.delay_min), 0), 2)                 AS on_time_percentage,
       SUM(CASE WHEN de.is_missing = 1 THEN 1 ELSE 0 END)         AS missing_observations
FROM routes r
LEFT JOIN trips t        ON t.route_id = r.route_id
LEFT JOIN delay_events de ON de.trip_id = t.trip_id
GROUP BY r.route_id, r.route_name, r.mode;

CREATE OR REPLACE VIEW v_stop_performance AS
SELECT s.stop_id,
       s.stop_name,
       s.is_interchange,
       COUNT(de.delay_event_id)                                   AS observations,
       ROUND(AVG(de.delay_min), 2)                                AS avg_delay_min,
       SUM(CASE WHEN de.delay_min >= 2 THEN 1 ELSE 0 END)         AS delayed_observations,
       ROUND(100.0 * SUM(CASE WHEN de.delay_min >= 2 THEN 1 ELSE 0 END)
             / NULLIF(COUNT(de.delay_min), 0), 2)                 AS delay_frequency_pct,
       COALESCE(SUM(pv.boardings), 0)                             AS total_boardings,
       MAX(pv.is_low_volume)                                      AS is_low_volume
FROM stops s
LEFT JOIN delay_events de      ON de.stop_id = s.stop_id
LEFT JOIN passenger_volumes pv ON pv.stop_id = s.stop_id
GROUP BY s.stop_id, s.stop_name, s.is_interchange;

CREATE OR REPLACE VIEW v_propagation_detail AS
SELECT pr.propagation_id,
       pr.mechanism,
       pr.confidence,
       pr.source_trip_id,
       sr.route_name  AS source_route_name,
       pr.source_delay_min,
       pr.affected_trip_id,
       ar.route_name  AS affected_route_name,
       pr.propagated_delay_min,
       pr.lag_min,
       pr.vehicle_id,
       pr.incident_id,
       pr.calculation
FROM propagation_results pr
JOIN routes sr ON sr.route_id = pr.source_route_id
JOIN routes ar ON ar.route_id = pr.affected_route_id;

CREATE OR REPLACE VIEW v_passenger_impact_by_route AS
SELECT pi.route_id,
       r.route_name,
       COUNT(*)                                     AS delayed_stop_events,
       SUM(pi.passengers_exposed)                   AS passengers_exposed,
       ROUND(SUM(pi.passenger_minutes_lost), 1)     AS passenger_minutes_lost,
       ROUND(AVG(pi.delay_min), 2)                  AS avg_delay_min,
       SUM(CASE WHEN pi.confidence IN ('LOW','NONE') THEN 1 ELSE 0 END)
                                                    AS low_confidence_events
FROM passenger_impact pi
JOIN routes r ON r.route_id = pi.route_id
GROUP BY pi.route_id, r.route_name;

CREATE OR REPLACE VIEW v_data_quality AS
SELECT am.data_quality_flag,
       COUNT(*)                                            AS record_count,
       ROUND(100.0 * COUNT(*) / (SELECT COUNT(*) FROM actual_movements), 2)
                                                           AS share_pct
FROM actual_movements am
GROUP BY am.data_quality_flag;
