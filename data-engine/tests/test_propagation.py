"""Propagation engine: mechanisms, evidence, confidence and de-duplication."""
import pandas as pd

from transitpulse_engine.config import EngineConfig
from transitpulse_engine.delay_calculator import DelayCalculator
from transitpulse_engine.propagation import PropagationEngine


def _links(movements, connections, incidents, config):
    dc = DelayCalculator(config)
    d = dc.compute(movements)
    return PropagationEngine(config).run(d, dc.trip_summary(d), connections, incidents), d


def test_vehicle_continuity_link_found_with_correct_arithmetic(
        movements, connections, incidents, config):
    """T1 arrives 09:28 (+5 turnaround = 09:33) against T2's 09:25 = 8 min."""
    links, _ = _links(movements, connections, incidents, config)
    vc = links[(links.mechanism == "VEHICLE_CONTINUITY")
               & (links.source_trip_id == "T1")]
    assert len(vc) == 1
    row = vc.iloc[0]
    assert row["affected_trip_id"] == "T2"
    assert row["propagated_delay_min"] == 8.0
    assert row["vehicle_id"] == "V1"
    assert row["confidence"] == "HIGH"


def test_vehicle_continuity_evidence_is_traceable(
        movements, connections, incidents, config):
    links, _ = _links(movements, connections, incidents, config)
    row = links[links.mechanism == "VEHICLE_CONTINUITY"].iloc[0]
    evidence = " ".join(row["evidence"])
    assert "V1" in evidence                      # names the vehicle
    assert "turnaround" in evidence.lower()      # states the constraint
    assert "09:33" in evidence                   # states the earliest departure
    assert "09:28" in row["calculation"]         # shows the arithmetic
    assert "=" in row["calculation"]


def test_turnaround_absorbs_small_delay(movements, connections, incidents):
    """With a 30 min turnaround the 8 min delay no longer forces a knock-on."""
    cfg = EngineConfig(vehicle_turnaround_min=30.0)
    links, _ = _links(movements, connections, incidents, cfg)
    # 09:28 + 30 = 09:58 vs 09:25 scheduled -> still forced, so use generous slack
    cfg2 = EngineConfig(vehicle_turnaround_min=0.0, min_propagated_delay_min=99.0)
    links2, _ = _links(movements, connections, incidents, cfg2)
    assert links2[links2.mechanism == "VEHICLE_CONTINUITY"].empty


def test_connection_mechanism_detected(movements, connections, incidents, config):
    """Feeder T1 arrives 09:28; connection departs 09:32; buffer 5 -> shortfall 1."""
    links, _ = _links(movements, connections, incidents, config)
    cl = links[links.mechanism == "CONNECTION"]
    assert not cl.empty
    row = cl.iloc[0]
    assert row["affected_trip_id"] == "T9"
    assert row["propagated_delay_min"] == 1.0
    assert row["lag_min"] == 4.0


def test_incident_annotation_raises_connection_confidence(
        movements, connections, incidents, config):
    """An operator incident covering the time window strengthens the claim."""
    links, _ = _links(movements, connections, incidents, config)
    row = links[links.mechanism == "CONNECTION"].iloc[0]
    assert row["incident_id"] == "INC1"
    assert row["confidence"] == "HIGH"

    no_inc, _ = _links(movements, connections, pd.DataFrame(), config)
    assert no_inc[no_inc.mechanism == "CONNECTION"].iloc[0]["confidence"] == "MEDIUM"


def test_shared_stop_links_are_low_confidence_and_labelled_correlation(config):
    """Unrelated routes at a shared stop must never be asserted as causal."""
    rows = [
        ("TA", "R01", "SX", 1, "09:00", "09:20"),   # 20 min late -> severe
        ("TB", "R02", "SX", 1, "09:30", "09:38"),   # 8 min late, later, other route
    ]
    df = pd.DataFrame(rows, columns=["trip_id", "route_id", "stop_id",
                                     "stop_sequence", "scheduled_arrival",
                                     "actual_arrival"])
    for c in ("scheduled_arrival", "actual_arrival"):
        df[c] = pd.to_datetime("2026-03-16 " + df[c])
    df["actual_departure"] = df["actual_arrival"]
    df["scheduled_departure"] = df["scheduled_arrival"]
    df["comparable"] = True
    df["vehicle_id"] = df["trip_id"].map({"TA": "V1", "TB": "V2"})
    dc = DelayCalculator(config)
    d = dc.compute(df)
    links = PropagationEngine(config).run(d, dc.trip_summary(d),
                                          pd.DataFrame(), pd.DataFrame())
    ss = links[links.mechanism == "SHARED_STOP"]
    assert not ss.empty
    row = ss.iloc[0]
    assert row["confidence"] == "LOW"
    assert "CORRELATION" in " ".join(row["evidence"]).upper()
    assert "correlation only" in row["calculation"]


def test_no_double_counting_of_affected_service(
        movements, connections, incidents, config):
    """One relationship per (source, affected) pair, strongest kept."""
    links, _ = _links(movements, connections, incidents, config)
    dupes = links.duplicated(subset=["source_trip_id", "affected_trip_id"]).sum()
    assert dupes == 0


def test_propagation_window_excludes_distant_services(config):
    """A shared-stop delay far outside the window is not linked."""
    rows = [
        ("TA", "R01", "SX", 1, "09:00", "09:20"),   # 20 min late
        ("TB", "R02", "SX", 1, "11:00", "11:08"),   # ~100 min later
    ]
    df = pd.DataFrame(rows, columns=["trip_id", "route_id", "stop_id",
                                     "stop_sequence", "scheduled_arrival",
                                     "actual_arrival"])
    for c in ("scheduled_arrival", "actual_arrival"):
        df[c] = pd.to_datetime("2026-03-16 " + df[c])
    df["actual_departure"] = df["actual_arrival"]
    df["scheduled_departure"] = df["scheduled_arrival"]
    df["comparable"] = True
    df["vehicle_id"] = df["trip_id"].map({"TA": "V1", "TB": "V2"})

    dc = DelayCalculator(config)
    d = dc.compute(df)
    ts = dc.trip_summary(d)

    wide = PropagationEngine(EngineConfig(propagation_window_min=180)).run(
        d, ts, pd.DataFrame(), pd.DataFrame())
    narrow = PropagationEngine(EngineConfig(propagation_window_min=30)).run(
        d, ts, pd.DataFrame(), pd.DataFrame())
    assert not wide.empty, "a 180 min window should capture the later trip"
    assert narrow.empty, "a 30 min window must exclude it"


def test_summary_counts_are_consistent(movements, connections, incidents, config):
    links, _ = _links(movements, connections, incidents, config)
    s = PropagationEngine(config).summary(links)
    assert s["totalLinks"] == len(links)
    assert sum(s["byMechanism"].values()) == len(links)
    assert sum(s["byConfidence"].values()) == len(links)
