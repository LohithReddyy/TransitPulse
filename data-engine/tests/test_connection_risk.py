"""Connection risk: available transfer time against the required buffer."""
import pandas as pd

from transitpulse_engine.config import EngineConfig
from transitpulse_engine.connection_risk import ConnectionRiskEngine
from transitpulse_engine.delay_calculator import DelayCalculator


def _risk(movements, connections, config, buffer_min=None):
    d = DelayCalculator(config).compute(movements)
    return ConnectionRiskEngine(config).evaluate(connections, d, buffer_min), d


def test_available_transfer_time_formula(movements, connections, config):
    """09:32 connecting departure - 09:28 feeder arrival = 4 min."""
    risk, _ = _risk(movements, connections, config)
    row = risk.iloc[0]
    assert row["available_transfer_min"] == 4.0
    assert row["feeder_delay_min"] == 8.0


def test_connection_at_risk_when_below_buffer(movements, connections, config):
    risk, _ = _risk(movements, connections, config)
    row = risk.iloc[0]
    assert row["is_at_risk"] is True or bool(row["is_at_risk"])
    assert not bool(row["is_missed"])            # 4 min available, still positive
    assert row["status"] == "AT_RISK"
    assert row["shortfall_min"] == 1.0           # 5 required - 4 available


def test_connection_safe_with_smaller_buffer(movements, connections, config):
    risk, _ = _risk(movements, connections, config, buffer_min=3.0)
    assert risk.iloc[0]["status"] == "SAFE"


def test_connection_missed_when_feeder_arrives_after_departure(
        movements, connections, config):
    conns = connections.copy()
    conns.loc[0, "connecting_scheduled_departure"] = pd.Timestamp("2026-03-16 09:26:00")
    risk, _ = _risk(movements, conns, config)
    row = risk.iloc[0]
    assert bool(row["is_missed"])
    assert row["status"] == "MISSED"
    assert row["risk_score"] == 1.0


def test_unobserved_feeder_is_unknown_not_safe(movements, connections, config):
    """No GPS for the feeder must not be reported as a safe connection."""
    conns = connections.copy()
    conns.loc[0, "feeder_trip_id"] = "T_NO_GPS"
    risk, _ = _risk(movements, conns, config)
    row = risk.iloc[0]
    assert row["status"] == "UNKNOWN_NO_GPS"
    assert not bool(row["is_at_risk"])
    assert not bool(row["is_missed"])
    assert row["risk_score"] is None
    s = ConnectionRiskEngine(config).summary(risk)
    assert s["unknown"] == 1
    assert s["assessed"] == 0


def test_risk_score_scales_with_shortfall(movements, connections, config):
    risk, _ = _risk(movements, connections, config)
    assert 0.0 < risk.iloc[0]["risk_score"] < 1.0


def test_summary_excludes_unknown_from_percentages(movements, connections, config):
    risk, _ = _risk(movements, connections, config)
    s = ConnectionRiskEngine(config).summary(risk)
    assert s["assessed"] == 1
    assert s["atRisk"] == 1
    assert s["atRiskPercentage"] == 100.0
    assert s["passengersAtRisk"] == 12


def test_negative_buffer_rejected():
    import pytest
    with pytest.raises(ValueError, match="cannot be negative"):
        EngineConfig(required_transfer_buffer_min=-1.0)
