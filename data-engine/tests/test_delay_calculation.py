"""Delay calculation, classification and recovery detection."""
import pandas as pd
import pytest

from transitpulse_engine.config import EngineConfig
from transitpulse_engine.delay_calculator import DelayCalculator


def test_delay_is_actual_minus_scheduled(movements, config):
    d = DelayCalculator(config).compute(movements)
    t1 = d[d.trip_id == "T1"].set_index("stop_sequence")
    assert t1.loc[1, "delay_min"] == 0.0        # 09:00 - 09:00
    assert t1.loc[2, "delay_min"] == 5.0        # 09:15 - 09:10
    assert t1.loc[3, "delay_min"] == 8.0        # 09:28 - 09:20


def test_delay_classification_boundaries(config):
    assert config.classify_delay(0.0) == "ON_TIME"
    assert config.classify_delay(1.9) == "ON_TIME"
    assert config.classify_delay(2.0) == "MINOR"
    assert config.classify_delay(4.9) == "MINOR"
    assert config.classify_delay(5.0) == "MODERATE"
    assert config.classify_delay(14.9) == "MODERATE"
    assert config.classify_delay(15.0) == "SEVERE"
    assert config.classify_delay(-5.0) == "EARLY"
    assert config.classify_delay(None) == "MISSING"


def test_thresholds_are_configurable():
    cfg = EngineConfig(on_time_threshold_min=1.0,
                       minor_delay_threshold_min=3.0,
                       moderate_delay_threshold_min=10.0)
    assert cfg.classify_delay(2.0) == "MINOR"      # MODERATE under defaults
    assert cfg.classify_delay(11.0) == "SEVERE"    # MODERATE under defaults


def test_invalid_thresholds_rejected():
    with pytest.raises(ValueError, match="strictly increasing"):
        EngineConfig(on_time_threshold_min=10.0, minor_delay_threshold_min=5.0)


def test_trip_summary_reports_max_and_final_delay(movements, config):
    dc = DelayCalculator(config)
    d = dc.compute(movements)
    ts = dc.trip_summary(d).set_index("trip_id")
    assert ts.loc["T1", "max_delay_min"] == 8.0
    assert ts.loc["T1", "final_delay_min"] == 8.0
    assert ts.loc["T1", "first_delay_stop_sequence"] == 2


def test_recovery_detected_only_when_trip_was_delayed(config):
    """A trip that is late then arrives on time counts as recovered."""
    rows = [("T5", "R01", "S1", 1, "09:00", "09:12"),   # 12 min late
            ("T5", "R01", "S2", 2, "09:10", "09:16"),   # 6 min late
            ("T5", "R01", "S3", 3, "09:20", "09:21")]   # 1 min late -> on time
    df = pd.DataFrame(rows, columns=["trip_id", "route_id", "stop_id",
                                     "stop_sequence", "scheduled_arrival",
                                     "actual_arrival"])
    for c in ("scheduled_arrival", "actual_arrival"):
        df[c] = pd.to_datetime("2026-03-16 " + df[c])
    df["actual_departure"] = df["actual_arrival"]
    df["scheduled_departure"] = df["scheduled_arrival"]
    df["comparable"] = True
    df["vehicle_id"] = "V9"
    dc = DelayCalculator(config)
    ts = dc.trip_summary(dc.compute(df)).set_index("trip_id")
    assert ts.loc["T5", "recovered"] is True or bool(ts.loc["T5", "recovered"])
    assert ts.loc["T5", "recovery_min"] == 11.0          # 12 -> 1


def test_on_time_trip_is_never_marked_recovered(config):
    rows = [("T6", "R01", "S1", 1, "09:00", "09:00"),
            ("T6", "R01", "S2", 2, "09:10", "09:10")]
    df = pd.DataFrame(rows, columns=["trip_id", "route_id", "stop_id",
                                     "stop_sequence", "scheduled_arrival",
                                     "actual_arrival"])
    for c in ("scheduled_arrival", "actual_arrival"):
        df[c] = pd.to_datetime("2026-03-16 " + df[c])
    df["actual_departure"] = df["actual_arrival"]
    df["scheduled_departure"] = df["scheduled_arrival"]
    df["comparable"] = True
    df["vehicle_id"] = "V9"
    dc = DelayCalculator(config)
    ts = dc.trip_summary(dc.compute(df)).set_index("trip_id")
    assert not bool(ts.loc["T6", "recovered"])


def test_route_summary_on_time_and_recovery_within_bounds(movements, config):
    dc = DelayCalculator(config)
    d = dc.compute(movements)
    rs = dc.route_summary(d, dc.trip_summary(d))
    assert (rs["on_time_percentage"].dropna() <= 100).all()
    assert (rs["on_time_percentage"].dropna() >= 0).all()
    assert (rs["recovery_percentage"] <= 100).all()
