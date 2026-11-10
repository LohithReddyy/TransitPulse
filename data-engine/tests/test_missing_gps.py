"""Missing GPS events must never be silently treated as zero delay."""
import pandas as pd

from transitpulse_engine.config import EngineConfig
from transitpulse_engine.delay_calculator import DelayCalculator


def test_missing_record_is_not_zero_delay(movements, config):
    d = DelayCalculator(config).compute(movements)
    gap = d[(d.trip_id == "T3") & (d.stop_sequence == 2)].iloc[0]
    assert pd.isna(gap["delay_min"]), "missing GPS must not become a delay value"
    assert gap["delay_class"] == "MISSING"
    assert gap["delay_source"] == "UNAVAILABLE"
    assert bool(gap["is_missing"])
    assert not bool(gap["is_delayed"])


def test_missing_records_excluded_from_averages(movements, config):
    d = DelayCalculator(config).compute(movements)
    t3 = d[d.trip_id == "T3"]
    observed = t3[t3["delay_min"].notna()]["delay_min"]
    assert len(observed) == 2                     # seq 1 and 3 only
    assert round(float(observed.mean()), 2) == 4.0  # (2 + 6) / 2, not (2+0+6)/3


def test_estimate_strategy_interpolates_and_labels(movements):
    cfg = EngineConfig(missing_gps_strategy="ESTIMATE")
    d = DelayCalculator(cfg).compute(movements)
    gap = d[(d.trip_id == "T3") & (d.stop_sequence == 2)].iloc[0]
    assert not pd.isna(gap["delay_min"])
    assert gap["delay_source"] == "ESTIMATED", "estimates must be labelled as such"
    assert gap["delay_min"] == 4.0                # interpolated between 2 and 6


def test_estimate_strategy_does_not_extrapolate_at_trip_edges():
    """A missing first or last stop stays unavailable rather than guessed."""
    cfg = EngineConfig(missing_gps_strategy="ESTIMATE")
    rows = [("T7", "R01", "S1", 1, "09:00", None),
            ("T7", "R01", "S2", 2, "09:10", "09:14"),
            ("T7", "R01", "S3", 3, "09:20", "09:26"),
            ("T7", "R01", "S4", 4, "09:30", None)]
    df = pd.DataFrame(rows, columns=["trip_id", "route_id", "stop_id",
                                     "stop_sequence", "scheduled_arrival",
                                     "actual_arrival"])
    df["scheduled_arrival"] = pd.to_datetime("2026-03-16 " + df["scheduled_arrival"])
    df["actual_arrival"] = pd.to_datetime(
        df["actual_arrival"].apply(
            lambda v: None if pd.isna(v) else f"2026-03-16 {v}"))
    df["actual_departure"] = df["actual_arrival"]
    df["scheduled_departure"] = df["scheduled_arrival"]
    df["comparable"] = df["actual_arrival"].notna()
    df["data_quality_flag"] = ["MISSING_GPS", "OK", "OK", "MISSING_GPS"]
    df["vehicle_id"] = "V1"
    d = DelayCalculator(cfg).compute(df)
    edges = d[d.stop_sequence.isin([1, 4])]
    assert edges["delay_min"].isna().all(), "must not extrapolate beyond observations"


def test_invalid_strategy_rejected():
    import pytest
    with pytest.raises(ValueError, match="EXCLUDE or ESTIMATE"):
        EngineConfig(missing_gps_strategy="ASSUME_ZERO")
