#!/usr/bin/env python3
"""
Generate MySQL seed INSERT statements from the synthetic CSV datasets.

Keeping the seed SQL generated rather than hand-written guarantees that the
database content always matches datasets/ and the JPA entities.

Run:  python database/generate_seed_sql.py
"""
from __future__ import annotations

import csv
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DATASETS = ROOT / "datasets"
OUT = ROOT / "database" / "mysql" / "02_seed_data.sql"

# table -> (csv file, columns in insert order, nullable columns)
TABLES = [
    ("routes", "routes.csv",
     ["route_id", "route_name", "mode", "origin_name", "destination_name",
      "stop_count", "status", "description"], set()),
    ("stops", "stops.csv",
     ["stop_id", "stop_name", "stop_type", "latitude", "longitude",
      "is_interchange", "step_free"], set()),
    ("route_stops", "route_stops.csv",
     ["route_stop_id", "route_id", "stop_id", "stop_sequence",
      "distance_from_origin_km", "scheduled_runtime_min"], set()),
    ("vehicles", "vehicles.csv",
     ["vehicle_id", "fleet_number", "vehicle_type", "capacity", "depot",
      "status"], set()),
    ("trips", "trips.csv",
     ["trip_id", "route_id", "vehicle_id", "service_date",
      "scheduled_start_time", "direction", "service_pattern", "block_id",
      "status"], {"vehicle_id", "block_id"}),
    ("scheduled_movements", "scheduled_movements.csv",
     ["scheduled_movement_id", "trip_id", "route_id", "stop_id",
      "stop_sequence", "scheduled_arrival", "scheduled_departure",
      "is_origin", "is_destination"], set()),
    ("actual_movements", "actual_movements.csv",
     ["actual_movement_id", "trip_id", "route_id", "stop_id", "stop_sequence",
      "actual_arrival", "actual_departure", "observed_delay_min",
      "data_quality_flag", "is_diverted"],
     {"actual_arrival", "actual_departure", "observed_delay_min"}),
    ("passenger_volumes", "passenger_volumes.csv",
     ["passenger_volume_id", "route_id", "stop_id", "stop_sequence",
      "time_band", "boardings", "alightings", "average_load",
      "average_wait_min", "is_low_volume"], set()),
    ("connections", "connections.csv",
     ["connection_id", "interchange_stop_id", "feeder_trip_id",
      "feeder_route_id", "connecting_trip_id", "connecting_route_id",
      "feeder_scheduled_arrival", "connecting_scheduled_departure",
      "planned_transfer_min", "required_buffer_min",
      "typical_transferring_passengers"], set()),
    ("incidents", "incidents.csv",
     ["incident_id", "incident_type", "route_id", "stop_id", "start_time",
      "end_time", "severity", "description", "is_diversion", "status",
      "reported_by"], {"stop_id", "end_time"}),
]

NUMERIC_SUFFIXES = ("_min", "_km", "_sequence", "_count", "capacity",
                    "boardings", "alightings", "average_load", "latitude",
                    "longitude", "direction", "step_free", "is_origin",
                    "is_destination", "typical_transferring_passengers")

CONFIG_ROWS = [
    ("on_time_threshold_min", "2.0", "NUMBER", "DELAY",
     "A vehicle is on time if its delay is below this many minutes", 0, 15),
    ("minor_delay_threshold_min", "5.0", "NUMBER", "DELAY",
     "Upper bound of a MINOR delay in minutes", 1, 30),
    ("moderate_delay_threshold_min", "15.0", "NUMBER", "DELAY",
     "Upper bound of a MODERATE delay; above this is SEVERE", 5, 60),
    ("early_arrival_threshold_min", "-2.0", "NUMBER", "DELAY",
     "Arrivals earlier than this many minutes are flagged EARLY", -15, 0),
    ("propagation_window_min", "90", "NUMBER", "PROPAGATION",
     "How far ahead the propagation engine looks, in minutes", 10, 240),
    ("min_propagated_delay_min", "2.0", "NUMBER", "PROPAGATION",
     "Minimum knock-on delay worth reporting as propagation", 0, 30),
    ("vehicle_turnaround_min", "5.0", "NUMBER", "PROPAGATION",
     "Scheduled turnaround between consecutive trips of one vehicle", 0, 60),
    ("recovery_min_reduction_min", "1.0", "NUMBER", "PROPAGATION",
     "Minimum delay reduction counted as recovery", 0, 30),
    ("required_transfer_buffer_min", "5.0", "NUMBER", "CONNECTION",
     "Minutes passengers need to change services at an interchange", 0, 30),
    ("max_transfer_wait_min", "30.0", "NUMBER", "CONNECTION",
     "Longest wait still treated as a usable connection", 5, 120),
    ("missed_connection_margin_min", "0.0", "NUMBER", "CONNECTION",
     "Available transfer time below this counts as missed", -10, 10),
    ("low_volume_stop_threshold", "20", "NUMBER", "PASSENGER",
     "Boardings below this mark a stop as low volume / low confidence", 0, 500),
    ("transfer_rate", "0.18", "NUMBER", "PASSENGER",
     "Assumed share of boarding passengers who transfer", 0, 1),
    ("additional_wait_on_missed_connection_min", "15.0", "NUMBER", "PASSENGER",
     "Assumed wait for the next service after a missed connection", 1, 120),
    ("missing_gps_strategy", "EXCLUDE", "STRING", "DATA_QUALITY",
     "How missing GPS events are handled: EXCLUDE or ESTIMATE", None, None),
    ("sensitivity_buffers_min", "3,5,8,10", "LIST", "SENSITIVITY",
     "Transfer buffers tested by the sensitivity analysis", None, None),
]


# Primary-key column for each CSV-backed table. Validate source data before
# generating SQL so duplicate IDs are caught here, not halfway through import.
PRIMARY_KEYS = {
    "routes": "route_id",
    "stops": "stop_id",
    "route_stops": "route_stop_id",
    "vehicles": "vehicle_id",
    "trips": "trip_id",
    "scheduled_movements": "scheduled_movement_id",
    "actual_movements": "actual_movement_id",
    "passenger_volumes": "passenger_volume_id",
    "connections": "connection_id",
    "incidents": "incident_id",
}


def validate_primary_keys(table: str, rows: list[dict[str, str]]) -> None:
    """Raise a clear error if a CSV contains duplicate or blank primary keys."""
    pk = PRIMARY_KEYS.get(table)
    if not pk:
        return

    first_seen: dict[str, int] = {}
    errors: list[str] = []

    for csv_line, row in enumerate(rows, start=2):  # header is line 1
        value = (row.get(pk) or "").strip()
        if not value:
            errors.append(f"blank {pk} at CSV line {csv_line}")
        elif value in first_seen:
            errors.append(
                f"duplicate {pk} {value!r} at CSV lines "
                f"{first_seen[value]} and {csv_line}"
            )
        else:
            first_seen[value] = csv_line

    if errors:
        details = "\\n".join(f"  - {error}" for error in errors[:20])
        if len(errors) > 20:
            details += f"\\n  - ... and {len(errors) - 20} more"
        raise ValueError(f"Invalid primary keys in {table}.csv:\\n{details}")


def sql_value(col: str, raw: str, nullable: set) -> str:
    if raw is None or raw == "":
        return "NULL" if col in nullable else "''"
    if col.endswith(NUMERIC_SUFFIXES) or col in NUMERIC_SUFFIXES:
        try:
            float(raw)
            return raw
        except ValueError:
            pass
    return "'" + raw.replace("\\", "\\\\").replace("'", "''") + "'"


def main() -> None:
    lines = [
        "-- =====================================================================",
        "-- TransitPulse - seed data",
        "-- GENERATED by database/generate_seed_sql.py from datasets/*.csv",
        "-- Do not edit by hand: regenerate instead.",
        "-- =====================================================================",
        "",
        "USE transitpulse;",
        "",
        "SET FOREIGN_KEY_CHECKS = 0;",
        "SET autocommit = 0;",
        "START TRANSACTION;",
        "",
    ]
    total = 0
    for table, filename, columns, nullable in TABLES:
        path = DATASETS / filename
        if not path.exists():
            raise SystemExit(f"missing dataset: {path}")
        with path.open(newline="", encoding="utf-8") as fh:
            rows = list(csv.DictReader(fh))

        # Validate before writing any SQL so bad source data fails early.
        validate_primary_keys(table, rows)

        lines.append(f"-- {table}: {len(rows)} rows")
        lines.append(f"DELETE FROM {table};")
        if rows:
            collist = ", ".join(columns)
            # batch inserts in chunks so the file stays readable and fast
            for start in range(0, len(rows), 100):
                chunk = rows[start:start + 100]
                lines.append(f"INSERT INTO {table} ({collist}) VALUES")
                values = [
                    "  (" + ", ".join(sql_value(c, r.get(c, ""), nullable)
                                      for c in columns) + ")"
                    for r in chunk]
                lines.append(",\n".join(values) + ";")
        lines.append("")
        total += len(rows)

    lines.append("-- system_configuration: analytical parameters")
    lines.append("DELETE FROM system_configuration;")
    lines.append("INSERT INTO system_configuration "
                 "(config_key, config_value, value_type, category, description, "
                 "min_value, max_value) VALUES")
    cfg_vals = []
    for key, value, vtype, cat, desc, lo, hi in CONFIG_ROWS:
        cfg_vals.append(
            f"  ('{key}', '{value}', '{vtype}', '{cat}', "
            f"'{desc.replace(chr(39), chr(39) * 2)}', "
            f"{'NULL' if lo is None else lo}, {'NULL' if hi is None else hi})")
    lines.append(",\n".join(cfg_vals) + ";")
    total += len(CONFIG_ROWS)

    lines += ["", "COMMIT;", "SET FOREIGN_KEY_CHECKS = 1;", "",
              f"-- total seeded rows: {total}"]

    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"wrote {OUT.relative_to(ROOT)} ({total} rows, "
          f"{len(lines)} lines)")


if __name__ == "__main__":
    main()
