"""
Data ingestion and validation.

Loads every CSV dataset, normalises timestamps, removes duplicates, records
validation problems rather than silently dropping rows, and — critically —
distinguishes a *missing* actual movement from a *zero delay* one.
"""
from __future__ import annotations

import logging
from dataclasses import dataclass, field
from pathlib import Path
from typing import Dict, List

import numpy as np
import pandas as pd

from .config import EngineConfig

log = logging.getLogger(__name__)

TIMESTAMP_COLUMNS = {
    "trips": ["scheduled_start_time"],
    "scheduled_movements": ["scheduled_arrival", "scheduled_departure"],
    "actual_movements": ["actual_arrival", "actual_departure"],
    "connections": ["feeder_scheduled_arrival", "connecting_scheduled_departure"],
    "incidents": ["start_time", "end_time"],
}

REQUIRED_FILES = [
    "routes", "stops", "route_stops", "vehicles", "trips",
    "scheduled_movements", "actual_movements", "passenger_volumes",
    "connections", "incidents", "diversions",
]

KEY_COLUMNS = {
    "routes": ["route_id"],
    "stops": ["stop_id"],
    "route_stops": ["route_stop_id"],
    "vehicles": ["vehicle_id"],
    "trips": ["trip_id"],
    "scheduled_movements": ["trip_id", "stop_sequence"],
    "actual_movements": ["trip_id", "stop_sequence"],
    "passenger_volumes": ["passenger_volume_id"],
    "connections": ["connection_id"],
    "incidents": ["incident_id"],
    "diversions": ["diversion_id"],
}


@dataclass
class ValidationReport:
    """Everything the ingestion layer noticed, kept for the UI and docs."""
    duplicates_removed: Dict[str, int] = field(default_factory=dict)
    row_counts: Dict[str, int] = field(default_factory=dict)
    missing_gps_records: int = 0
    diverted_records: int = 0
    orphan_references: Dict[str, int] = field(default_factory=dict)
    timestamp_parse_failures: Dict[str, int] = field(default_factory=dict)
    warnings: List[str] = field(default_factory=list)

    def to_dict(self) -> dict:
        return {
            "duplicatesRemoved": self.duplicates_removed,
            "rowCounts": self.row_counts,
            "missingGpsRecords": self.missing_gps_records,
            "divertedRecords": self.diverted_records,
            "orphanReferences": self.orphan_references,
            "timestampParseFailures": self.timestamp_parse_failures,
            "warnings": self.warnings,
        }

    @property
    def is_clean(self) -> bool:
        return not self.orphan_references and not self.timestamp_parse_failures


class DataIngestion:
    """Loads and validates the TransitPulse dataset family."""

    def __init__(self, config: EngineConfig | None = None,
                 datasets_dir: str | Path | None = None):
        self.config = config or EngineConfig()
        self.datasets_dir = Path(datasets_dir or self.config.datasets_dir)
        self.report = ValidationReport()
        self.frames: Dict[str, pd.DataFrame] = {}

    # ------------------------------------------------------------------ #

    def load_all(self) -> Dict[str, pd.DataFrame]:
        """Load every required dataset, validate it and return the frames."""
        missing = [n for n in REQUIRED_FILES
                   if not (self.datasets_dir / f"{n}.csv").exists()]
        if missing:
            raise FileNotFoundError(
                f"Missing dataset files in {self.datasets_dir}: "
                + ", ".join(f"{m}.csv" for m in missing)
                + ". Run `python generate_datasets.py` first.")

        for name in REQUIRED_FILES:
            self.frames[name] = self._load_one(name)

        self._normalise_actuals()
        self._check_referential_integrity()
        self._flag_low_volume_stops()
        return self.frames

    # ------------------------------------------------------------------ #

    def _load_one(self, name: str) -> pd.DataFrame:
        path = self.datasets_dir / f"{name}.csv"
        df = pd.read_csv(path, dtype=str, keep_default_na=False, na_values=[""])

        # --- duplicate handling: keep the last record for a business key ---
        keys = [k for k in KEY_COLUMNS.get(name, []) if k in df.columns]
        if keys:
            before = len(df)
            df = df.drop_duplicates(subset=keys, keep="last").reset_index(drop=True)
            removed = before - len(df)
            if removed:
                self.report.duplicates_removed[name] = removed
                log.warning("%s: removed %d duplicate rows", name, removed)

        # --- timestamp normalisation ---
        for col in TIMESTAMP_COLUMNS.get(name, []):
            if col in df.columns:
                raw_present = df[col].notna()
                df[col] = pd.to_datetime(df[col], errors="coerce",
                                         format="mixed", dayfirst=False)
                failures = int((raw_present & df[col].isna()).sum())
                if failures:
                    key = f"{name}.{col}"
                    self.report.timestamp_parse_failures[key] = failures

        # --- numeric coercion ---
        for col in df.columns:
            if col.endswith(("_min", "_km", "_sequence", "_count", "boardings",
                             "alightings", "average_load", "capacity",
                             "latitude", "longitude", "direction", "step_free")):
                df[col] = pd.to_numeric(df[col], errors="coerce")

        self.report.row_counts[name] = len(df)
        return df

    def _normalise_actuals(self) -> None:
        """
        Classify every actual movement record by data quality.

        A record with no `actual_arrival` is NEVER treated as an on-time
        arrival. It is marked MISSING_GPS (or DIVERTED_SKIPPED) and excluded
        from, or estimated in, downstream delay maths according to the
        configured strategy.
        """
        df = self.frames["actual_movements"]
        if "data_quality_flag" not in df.columns:
            df["data_quality_flag"] = "OK"

        df["data_quality_flag"] = df["data_quality_flag"].fillna("OK")
        no_arrival = df["actual_arrival"].isna()
        diverted = df.get("is_diverted", pd.Series("N", index=df.index)).eq("Y")

        df.loc[no_arrival & ~diverted, "data_quality_flag"] = "MISSING_GPS"
        df.loc[diverted, "data_quality_flag"] = "DIVERTED_SKIPPED"
        df["is_observed"] = ~no_arrival

        self.report.missing_gps_records = int(
            (df["data_quality_flag"] == "MISSING_GPS").sum())
        self.report.diverted_records = int(
            (df["data_quality_flag"] == "DIVERTED_SKIPPED").sum())

        if self.report.missing_gps_records:
            self.report.warnings.append(
                f"{self.report.missing_gps_records} actual movement records have no "
                f"GPS arrival event. Strategy = {self.config.missing_gps_strategy}; "
                "these are never counted as zero delay.")
        if self.report.diverted_records:
            self.report.warnings.append(
                f"{self.report.diverted_records} records belong to diverted trips and "
                "are excluded from stop-to-stop schedule comparison.")
        self.frames["actual_movements"] = df

    def _check_referential_integrity(self) -> None:
        """Count rows that point at parents which do not exist."""
        checks = [
            ("route_stops", "route_id", "routes", "route_id"),
            ("route_stops", "stop_id", "stops", "stop_id"),
            ("trips", "route_id", "routes", "route_id"),
            ("trips", "vehicle_id", "vehicles", "vehicle_id"),
            ("scheduled_movements", "trip_id", "trips", "trip_id"),
            ("scheduled_movements", "stop_id", "stops", "stop_id"),
            ("actual_movements", "trip_id", "trips", "trip_id"),
            ("passenger_volumes", "stop_id", "stops", "stop_id"),
            ("connections", "feeder_trip_id", "trips", "trip_id"),
            ("connections", "connecting_trip_id", "trips", "trip_id"),
            ("incidents", "route_id", "routes", "route_id"),
            ("diversions", "trip_id", "trips", "trip_id"),
        ]
        for child, ckey, parent, pkey in checks:
            if child not in self.frames or parent not in self.frames:
                continue
            valid = set(self.frames[parent][pkey].dropna())
            orphans = int((~self.frames[child][ckey].isin(valid)
                           & self.frames[child][ckey].notna()).sum())
            if orphans:
                self.report.orphan_references[f"{child}.{ckey}->{parent}"] = orphans

    def _flag_low_volume_stops(self) -> None:
        """Mark passenger-volume rows whose sample is too small to trust."""
        pv = self.frames["passenger_volumes"]
        threshold = self.config.low_volume_stop_threshold
        pv["boardings"] = pv["boardings"].fillna(0).astype(int)
        pv["is_low_volume"] = np.where(pv["boardings"] < threshold, "Y", "N")
        pv["confidence"] = np.where(pv["boardings"] < threshold, "LOW",
                                    np.where(pv["boardings"] < threshold * 3,
                                             "MEDIUM", "HIGH"))
        self.frames["passenger_volumes"] = pv

    # ------------------------------------------------------------------ #

    def movements(self) -> pd.DataFrame:
        """
        Join scheduled to actual movements on (trip_id, stop_sequence).

        This is the single source of truth for every delay computation. Rows
        where a planned stop was not served because of a diversion are kept
        but flagged, so no incorrect stop-to-stop comparison is made.
        """
        sched = self.frames["scheduled_movements"]
        act = self.frames["actual_movements"]
        trips = self.frames["trips"][["trip_id", "vehicle_id", "block_id",
                                      "service_date", "direction"]]

        merged = sched.merge(
            act[["trip_id", "stop_sequence", "actual_arrival", "actual_departure",
                 "data_quality_flag", "is_observed", "is_diverted"]],
            on=["trip_id", "stop_sequence"], how="left", validate="one_to_one")
        merged = merged.merge(trips, on="trip_id", how="left")
        merged["data_quality_flag"] = merged["data_quality_flag"].fillna("NO_RECORD")
        merged["is_observed"] = merged["is_observed"].fillna(False).astype(bool)
        merged["is_diverted"] = merged["is_diverted"].fillna("N")
        merged["comparable"] = merged["is_observed"] & merged["is_diverted"].ne("Y")
        return merged.sort_values(["trip_id", "stop_sequence"]).reset_index(drop=True)
