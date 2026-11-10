"""Diverted trips must not produce false stop-to-stop comparisons."""
import pandas as pd

from transitpulse_engine.delay_calculator import DelayCalculator


def _diverted_frame():
    rows = [
        ("TD", "R01", "S1", 1, "09:00", "09:03", "N", "OK"),
        ("TD", "R01", "S2", 2, "09:10", None, "Y", "DIVERTED_SKIPPED"),
        ("TD", "R01", "S3", 3, "09:20", "09:26", "N", "OK"),
    ]
    df = pd.DataFrame(rows, columns=["trip_id", "route_id", "stop_id",
                                     "stop_sequence", "scheduled_arrival",
                                     "actual_arrival", "is_diverted",
                                     "data_quality_flag"])
    df["scheduled_arrival"] = pd.to_datetime("2026-03-16 " + df["scheduled_arrival"])
    df["actual_arrival"] = pd.to_datetime(
        df["actual_arrival"].apply(lambda v: None if pd.isna(v) else f"2026-03-16 {v}"))
    df["actual_departure"] = df["actual_arrival"]
    df["scheduled_departure"] = df["scheduled_arrival"]
    df["is_observed"] = df["actual_arrival"].notna()
    df["comparable"] = df["is_observed"] & df["is_diverted"].ne("Y")
    df["vehicle_id"] = "V1"
    return df


def test_skipped_stop_produces_no_delay_value(config):
    d = DelayCalculator(config).compute(_diverted_frame())
    skipped = d[d.stop_sequence == 2].iloc[0]
    assert pd.isna(skipped["delay_min"])
    assert skipped["delay_class"] == "MISSING"
    assert skipped["data_quality_flag"] == "DIVERTED_SKIPPED"


def test_diverted_stop_excluded_from_trip_averages(config):
    dc = DelayCalculator(config)
    d = dc.compute(_diverted_frame())
    ts = dc.trip_summary(d).set_index("trip_id")
    assert ts.loc["TD", "stops_observed"] == 2
    assert ts.loc["TD", "stops_total"] == 3
    assert ts.loc["TD", "avg_delay_min"] == 4.5       # (3 + 6) / 2


def test_diverted_trip_confidence_is_reduced(config):
    dc = DelayCalculator(config)
    d = dc.compute(_diverted_frame())
    ts = dc.trip_summary(d).set_index("trip_id")
    assert ts.loc["TD", "data_confidence"] in ("MEDIUM", "LOW")


def test_real_dataset_marks_diverted_records():
    """The generated dataset must actually contain diverted records."""
    from transitpulse_engine.ingestion import DataIngestion
    ing = DataIngestion()
    try:
        ing.load_all()
    except FileNotFoundError:
        import pytest
        pytest.skip("datasets not generated")
    assert ing.report.diverted_records > 0
    m = ing.movements()
    assert (~m["comparable"]).sum() >= ing.report.diverted_records
