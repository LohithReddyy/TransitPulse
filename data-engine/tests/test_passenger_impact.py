"""Passenger impact estimation and low-volume confidence marking."""
import pandas as pd

from transitpulse_engine.connection_risk import ConnectionRiskEngine
from transitpulse_engine.delay_calculator import DelayCalculator
from transitpulse_engine.passenger_impact import PassengerImpactEngine, time_band


def _impact(movements, passenger_volumes, config):
    d = DelayCalculator(config).compute(movements)
    return PassengerImpactEngine(config).estimate(d, passenger_volumes), d


def test_time_band_mapping():
    assert time_band(pd.Timestamp("2026-03-16 06:00")) == "EARLY"
    assert time_band(pd.Timestamp("2026-03-16 09:00")) == "AM_PEAK"
    assert time_band(pd.Timestamp("2026-03-16 13:00")) == "MIDDAY"
    assert time_band(pd.Timestamp("2026-03-16 17:00")) == "PM_PEAK"
    assert time_band(pd.Timestamp("2026-03-16 21:00")) == "EVENING"


def test_passengers_exposed_is_boardings_plus_load(movements, passenger_volumes, config):
    """Stop SB: 40 boardings + 8 average load = 48 exposed."""
    impact, _ = _impact(movements, passenger_volumes, config)
    sb = impact[(impact.trip_id == "T1") & (impact.stop_id == "SB")].iloc[0]
    assert sb["passengers_exposed"] == 48
    assert sb["delay_min"] == 5.0
    assert sb["passenger_minutes_lost"] == 240.0        # 5 x 48


def test_only_delayed_events_are_counted(movements, passenger_volumes, config):
    """The on-time first stop of T1 contributes no passenger impact."""
    impact, _ = _impact(movements, passenger_volumes, config)
    assert impact[(impact.trip_id == "T1") & (impact.stop_id == "SA")].empty


def test_all_outputs_flagged_as_estimates(movements, passenger_volumes, config):
    impact, _ = _impact(movements, passenger_volumes, config)
    assert impact["is_estimate"].all()
    assert impact["estimate_basis"].notna().all()


def test_low_volume_stop_marked_low_confidence(movements, passenger_volumes, config):
    """Stop SC has only 5 boardings, below the threshold of 20."""
    impact, _ = _impact(movements, passenger_volumes, config)
    sc = impact[(impact.trip_id == "T1") & (impact.stop_id == "SC")].iloc[0]
    assert sc["is_low_volume"] == "Y"
    assert sc["confidence"] == "LOW"


def test_missing_volume_profile_is_not_zero_confidence_high(
        movements, passenger_volumes, config):
    """A stop with no volume profile gets NONE confidence, not a silent zero."""
    pv = passenger_volumes[passenger_volumes.stop_id != "SD"]
    impact, _ = _impact(movements, pv, config)
    sd = impact[impact.stop_id == "SD"]
    if not sd.empty:
        row = sd.iloc[0]
        assert row["confidence"] == "NONE"
        assert not bool(row["has_volume_data"])
        assert "No volume profile" in row["estimate_basis"]


def test_by_route_aggregation_totals_match(movements, passenger_volumes, config):
    impact, _ = _impact(movements, passenger_volumes, config)
    by_route = PassengerImpactEngine(config).by_route(impact)
    assert by_route["passengers_exposed"].sum() == impact["passengers_exposed"].sum()
    assert round(by_route["passenger_minutes_lost"].sum(), 1) == round(
        impact["passenger_minutes_lost"].sum(), 1)


def test_connection_impact_uses_configured_wait(movements, connections, config):
    d = DelayCalculator(config).compute(movements)
    conns = connections.copy()
    conns.loc[0, "connecting_scheduled_departure"] = pd.Timestamp("2026-03-16 09:26:00")
    risk = ConnectionRiskEngine(config).evaluate(conns, d)
    ci = PassengerImpactEngine(config).connection_impact(risk)
    assert ci["missedTransferPassengers"] == 12
    assert ci["additionalWaitMinutes"] == 12 * config.additional_wait_on_missed_connection_min
    assert ci["isEstimate"] is True


def test_summary_marks_estimate(movements, passenger_volumes, config):
    impact, _ = _impact(movements, passenger_volumes, config)
    s = PassengerImpactEngine(config).summary(impact, None)
    assert s["isEstimate"] is True
    assert s["passengersExposed"] > 0
