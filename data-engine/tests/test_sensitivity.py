"""Sensitivity of connection risk to the transfer buffer assumption."""
from transitpulse_engine.delay_calculator import DelayCalculator
from transitpulse_engine.sensitivity import SensitivityAnalyzer


def test_all_requested_buffers_tested(movements, connections, config):
    d = DelayCalculator(config).compute(movements)
    res = SensitivityAnalyzer(config).run(connections, d, [3, 5, 8, 10])
    assert list(res["required_buffer_min"]) == [3.0, 5.0, 8.0, 10.0]


def test_at_risk_count_is_monotonic_in_buffer(movements, connections, config):
    """A larger required buffer can never reduce the number of at-risk links."""
    d = DelayCalculator(config).compute(movements)
    res = SensitivityAnalyzer(config).run(connections, d, [3, 5, 8, 10])
    assert res["at_risk_connections"].is_monotonic_increasing


def test_missed_connections_do_not_depend_on_buffer(movements, connections, config):
    """Whether a feeder arrives after departure is independent of the buffer."""
    d = DelayCalculator(config).compute(movements)
    res = SensitivityAnalyzer(config).run(connections, d, [3, 5, 8, 10])
    assert res["missed_connections"].nunique() == 1


def test_interpretation_reports_spread_and_verdict(movements, connections, config):
    d = DelayCalculator(config).compute(movements)
    sa = SensitivityAnalyzer(config)
    res = sa.run(connections, d, [3, 5, 8, 10])
    interp = sa.interpretation(res)
    assert interp["bufferRangeMin"] == [3.0, 10.0]
    assert interp["verdict"] in ("ROBUST", "MODERATELY_SENSITIVE", "HIGHLY_SENSITIVE")
    assert "buffer" in interp["note"].lower()


def test_deltas_measured_against_lowest_buffer(movements, connections, config):
    d = DelayCalculator(config).compute(movements)
    res = SensitivityAnalyzer(config).run(connections, d, [3, 5, 8, 10])
    assert res.iloc[0]["delta_at_risk_vs_lowest_buffer"] == 0
    assert (res["delta_at_risk_vs_lowest_buffer"] >= 0).all()
