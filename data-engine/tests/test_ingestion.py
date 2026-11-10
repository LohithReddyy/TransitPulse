"""Ingestion: validation, duplicates, referential integrity, low volume."""
import pandas as pd
import pytest

from transitpulse_engine.ingestion import DataIngestion, REQUIRED_FILES


@pytest.fixture(scope="module")
def ingested():
    ing = DataIngestion()
    try:
        ing.load_all()
    except FileNotFoundError:
        pytest.skip("datasets not generated; run generate_datasets.py")
    return ing


def test_all_required_datasets_loaded(ingested):
    assert set(ingested.frames) == set(REQUIRED_FILES)
    for name, df in ingested.frames.items():
        assert not df.empty, f"{name} is empty"


def test_dataset_meets_specified_minimum_size(ingested):
    assert len(ingested.frames["routes"]) >= 10
    assert len(ingested.frames["stops"]) >= 50
    assert len(ingested.frames["trips"]) >= 100


def test_no_orphan_foreign_keys(ingested):
    assert ingested.report.orphan_references == {}


def test_timestamps_parsed(ingested):
    assert ingested.report.timestamp_parse_failures == {}
    sched = ingested.frames["scheduled_movements"]
    assert pd.api.types.is_datetime64_any_dtype(sched["scheduled_arrival"])


def test_duplicates_removed_on_business_key(ingested):
    for name, keys in [("trips", ["trip_id"]), ("routes", ["route_id"]),
                       ("scheduled_movements", ["trip_id", "stop_sequence"])]:
        assert not ingested.frames[name].duplicated(subset=keys).any()


def test_missing_gps_counted_and_warned(ingested):
    assert ingested.report.missing_gps_records > 0
    assert any("GPS" in w for w in ingested.report.warnings)


def test_low_volume_stops_flagged(ingested):
    pv = ingested.frames["passenger_volumes"]
    assert set(pv["is_low_volume"].unique()) <= {"Y", "N"}
    assert set(pv["confidence"].unique()) <= {"LOW", "MEDIUM", "HIGH"}
    low = pv[pv["is_low_volume"] == "Y"]
    assert (low["boardings"] < 20).all()


def test_movements_join_is_one_to_one(ingested):
    m = ingested.movements()
    assert len(m) == len(ingested.frames["scheduled_movements"])
    assert not m.duplicated(subset=["trip_id", "stop_sequence"]).any()


def test_missing_datasets_raise_clear_error(tmp_path):
    with pytest.raises(FileNotFoundError, match="generate_datasets"):
        DataIngestion(datasets_dir=tmp_path).load_all()
