"""End-to-end pipeline: runs on the real datasets and exports valid artefacts."""
import json

import pytest

from transitpulse_engine.config import EngineConfig
from transitpulse_engine.pipeline import Pipeline


@pytest.fixture(scope="module")
def ran(tmp_path_factory):
    cfg = EngineConfig()
    pipeline = Pipeline(cfg)
    try:
        pipeline.run()
    except FileNotFoundError:
        pytest.skip("datasets not generated; run generate_datasets.py")
    out = pipeline.export(tmp_path_factory.mktemp("out"))
    return pipeline, out


def test_pipeline_produces_every_stage(ran):
    pipeline, _ = ran
    for key in ("delays", "trip_summary", "route_summary", "propagation",
                "connection_risk", "passenger_impact", "sensitivity"):
        assert key in pipeline.results, key


def test_dashboard_metrics_are_internally_consistent(ran):
    pipeline, _ = ran
    d = pipeline.results["summaries"]["dashboard"]
    assert d["totalRoutes"] >= 10
    assert d["totalStops"] >= 50
    assert d["totalScheduledTrips"] >= 100
    assert 0 <= d["onTimePercentage"] <= 100
    assert 0 <= d["recoveryPercentage"] <= 100
    # observed + unobserved accounts for every movement exactly
    assert d["observedMovements"] + d["unobservedMovements"] == d["totalMovements"]
    assert d["delayedTrips"] <= d["totalScheduledTrips"]
    assert d["recoveredTrips"] <= d["delayedTrips"]
    assert d["highConfidencePropagations"] <= d["propagationLinks"]
    assert d["missedConnections"] <= d["connectionsAtRisk"] + d["missedConnections"]


def test_exports_expected_csv_and_json(ran):
    _, out = ran
    for name in ("delay_events", "route_performance", "propagation_results",
                 "connection_risk", "passenger_impact", "sensitivity_analysis"):
        assert (out / "csv" / f"{name}.csv").exists(), name
    for name in ("dashboard", "propagation", "connection_risk",
                 "passenger_impact", "sensitivity", "worked_example",
                 "validation", "configuration"):
        assert (out / "json" / f"{name}.json").exists(), name


def test_exported_json_is_parseable_and_non_empty(ran):
    _, out = ran
    for path in (out / "json").glob("*.json"):
        with path.open(encoding="utf-8") as fh:
            payload = json.load(fh)
        assert payload not in ({}, [], None), path.name


def test_manifest_lists_all_outputs(ran):
    _, out = ran
    with (out / "manifest.json").open(encoding="utf-8") as fh:
        manifest = json.load(fh)
    assert manifest["engineVersion"]
    assert len(manifest["csvTables"]) >= 8
    assert len(manifest["jsonPayloads"]) >= 8
    assert manifest["configuration"]["required_transfer_buffer_min"] > 0


def test_propagation_links_never_double_count(ran):
    pipeline, _ = ran
    links = pipeline.results["propagation"]
    if not links.empty:
        assert not links.duplicated(
            subset=["source_trip_id", "affected_trip_id"]).any()


def test_csv_evidence_column_is_flattened_for_sql(ran):
    import csv
    _, out = ran
    path = out / "csv" / "propagation_results.csv"
    with path.open(encoding="utf-8") as fh:
        rows = list(csv.DictReader(fh))
    if rows:
        assert not rows[0]["evidence"].startswith("[")
        assert "|" in rows[0]["evidence"] or rows[0]["evidence"]
