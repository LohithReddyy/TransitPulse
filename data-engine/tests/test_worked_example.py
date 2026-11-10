"""The worked example must reproduce the specification's numbers exactly."""
from transitpulse_engine.config import EngineConfig
from transitpulse_engine.worked_example import build


def test_stop_level_delays_match_specification():
    w = build()
    steps = {s["stop"]: s for s in w["step1DelayAtEachStop"]}
    assert steps["Stop A"]["delayMin"] == 0.0
    assert steps["Stop B"]["delayMin"] == 5.0
    assert steps["Stop C"]["delayMin"] == 8.0


def test_eight_minute_delay_propagates_to_next_service():
    p = build()["step3PropagationToNextService"]
    assert p["predictedPropagatedDelayMin"] == 8.0
    assert p["observedDelayMin"] == 8.0
    assert p["predictionMatchesObservation"] is True
    assert p["mechanism"] == "VEHICLE_CONTINUITY"
    assert p["confidence"] == "HIGH"


def test_every_propagation_step_shows_its_arithmetic():
    p = build()["step3PropagationToNextService"]
    assert len(p["steps"]) == 4
    for step in p["steps"]:
        assert step["calculation"]
        assert "=" in step["calculation"]


def test_connection_is_at_risk_with_four_minutes_available():
    c = build()["step4ConnectionRisk"]
    assert c["availableTransferMin"] == 4.0
    assert c["requiredBufferMin"] == 5.0
    assert c["shortfallMin"] == 1.0
    assert c["isAtRisk"] is True
    assert c["isMissed"] is False
    assert c["status"] == "AT_RISK"


def test_connection_becomes_safe_with_three_minute_buffer():
    c = build(transfer_buffer_min=3.0)["step4ConnectionRisk"]
    assert c["status"] == "SAFE"
    assert c["isAtRisk"] is False


def test_passenger_impact_arithmetic():
    pi = build(passengers_on_board=48, transferring_passengers=12)["step5PassengerImpact"]
    assert pi["passengerMinutesLostOnDelay"] == 8 * 48
    assert pi["additionalWaitPassengerMinutes"] == 12 * 15
    assert pi["totalPassengerMinutesImpact"] == 8 * 48 + 12 * 15
    assert pi["isEstimate"] is True
    assert "not confirmed passenger counts" in pi["estimateCaveat"]


def test_correlation_versus_causation_is_addressed():
    p = build()["step3PropagationToNextService"]
    assert "whyNotMerelyCorrelation" in p
    assert "physical constraint" in p["whyNotMerelyCorrelation"]


def test_respects_custom_turnaround_configuration():
    w = build(EngineConfig(vehicle_turnaround_min=10.0))
    assert w["step3PropagationToNextService"]["predictedPropagatedDelayMin"] == 13.0
