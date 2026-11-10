"""Shared fixtures: a tiny hand-built network with known-by-hand answers."""
from __future__ import annotations

import sys
from pathlib import Path

import pandas as pd
import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from transitpulse_engine.config import EngineConfig  # noqa: E402

DATE = "2026-03-16"


def ts(hhmm: str) -> pd.Timestamp:
    return pd.Timestamp(f"{DATE} {hhmm}:00")


@pytest.fixture
def config() -> EngineConfig:
    return EngineConfig()


@pytest.fixture
def movements() -> pd.DataFrame:
    """
    Three stops on route R01 (trip T1, vehicle V1), reproducing the worked
    example, plus trip T2 on route R02 using the SAME vehicle, and trip T3
    with a missing GPS record.
    """
    rows = [
        # trip, route, stop, seq, sched_arr, sched_dep, act_arr, act_dep, flag
        ("T1", "R01", "SA", 1, "09:00", "09:00", "09:00", "09:00", "OK"),
        ("T1", "R01", "SB", 2, "09:10", "09:11", "09:15", "09:16", "OK"),
        ("T1", "R01", "SC", 3, "09:20", "09:20", "09:28", "09:28", "OK"),
        ("T2", "R02", "SC", 1, "09:25", "09:25", "09:33", "09:33", "OK"),
        ("T2", "R02", "SD", 2, "09:40", "09:40", "09:46", "09:46", "OK"),
        ("T3", "R03", "SE", 1, "09:00", "09:00", "09:02", "09:02", "OK"),
        ("T3", "R03", "SF", 2, "09:12", "09:12", None, None, "MISSING_GPS"),
        ("T3", "R03", "SG", 3, "09:24", "09:24", "09:30", "09:30", "OK"),
    ]
    df = pd.DataFrame(rows, columns=[
        "trip_id", "route_id", "stop_id", "stop_sequence", "scheduled_arrival",
        "scheduled_departure", "actual_arrival", "actual_departure",
        "data_quality_flag"])
    for col in ("scheduled_arrival", "scheduled_departure",
                "actual_arrival", "actual_departure"):
        df[col] = pd.to_datetime(
            df[col].apply(lambda v: None if pd.isna(v) else f"{DATE} {v}:00"))
    df["vehicle_id"] = df["trip_id"].map({"T1": "V1", "T2": "V1", "T3": "V2"})
    df["block_id"] = "BLOCK-1"
    df["service_date"] = DATE
    df["direction"] = 0
    df["is_observed"] = df["actual_arrival"].notna()
    df["is_diverted"] = "N"
    df["comparable"] = df["is_observed"] & df["is_diverted"].ne("Y")
    df["is_origin"] = (df["stop_sequence"] == 1).astype(int)
    df["is_destination"] = 0
    return df


@pytest.fixture
def connections() -> pd.DataFrame:
    """T1 feeds T9 at stop SC: 09:32 departure against a 09:28 arrival."""
    return pd.DataFrame([{
        "connection_id": "C1",
        "interchange_stop_id": "SC",
        "feeder_trip_id": "T1",
        "feeder_route_id": "R01",
        "connecting_trip_id": "T9",
        "connecting_route_id": "R09",
        "feeder_scheduled_arrival": ts("09:20"),
        "connecting_scheduled_departure": ts("09:32"),
        "planned_transfer_min": 12.0,
        "required_buffer_min": 5.0,
        "typical_transferring_passengers": 12,
    }])


@pytest.fixture
def passenger_volumes() -> pd.DataFrame:
    return pd.DataFrame([
        {"passenger_volume_id": "PV1", "route_id": "R01", "stop_id": "SB",
         "stop_sequence": 2, "time_band": "AM_PEAK", "boardings": 40,
         "alightings": 20, "average_load": 8, "average_wait_min": 4.0,
         "is_low_volume": "N", "confidence": "HIGH"},
        {"passenger_volume_id": "PV2", "route_id": "R01", "stop_id": "SC",
         "stop_sequence": 3, "time_band": "AM_PEAK", "boardings": 5,
         "alightings": 30, "average_load": 3, "average_wait_min": 7.0,
         "is_low_volume": "Y", "confidence": "LOW"},
        {"passenger_volume_id": "PV3", "route_id": "R02", "stop_id": "SC",
         "stop_sequence": 1, "time_band": "AM_PEAK", "boardings": 25,
         "alightings": 5, "average_load": 10, "average_wait_min": 3.0,
         "is_low_volume": "N", "confidence": "MEDIUM"},
        {"passenger_volume_id": "PV4", "route_id": "R02", "stop_id": "SD",
         "stop_sequence": 2, "time_band": "AM_PEAK", "boardings": 15,
         "alightings": 15, "average_load": 6, "average_wait_min": 5.0,
         "is_low_volume": "Y", "confidence": "LOW"},
        {"passenger_volume_id": "PV5", "route_id": "R03", "stop_id": "SG",
         "stop_sequence": 3, "time_band": "AM_PEAK", "boardings": 30,
         "alightings": 10, "average_load": 12, "average_wait_min": 4.0,
         "is_low_volume": "N", "confidence": "HIGH"},
    ])


@pytest.fixture
def incidents() -> pd.DataFrame:
    return pd.DataFrame([{
        "incident_id": "INC1", "incident_type": "TRAFFIC_CONGESTION",
        "route_id": "R01", "stop_id": "SB",
        "start_time": ts("09:05"), "end_time": ts("09:45"),
        "severity": "HIGH", "description": "Congestion near SB",
        "is_diversion": "N", "status": "CLOSED", "reported_by": "operations"}])
