"""
The hand-worked propagation example from the project specification.

This is a self-contained, fully traceable calculation that the API serves to
the UI so an operator can see exactly how the engine reasons, step by step,
on numbers they can check by hand.
"""
from __future__ import annotations

from datetime import datetime, timedelta
from typing import Any, Dict, List

from .config import EngineConfig

DATE = "2026-03-16"


def _t(hhmm: str) -> datetime:
    return datetime.fromisoformat(f"{DATE}T{hhmm}:00")


def _fmt(dt: datetime) -> str:
    return dt.strftime("%H:%M")


def build(config: EngineConfig | None = None,
          transfer_buffer_min: float | None = None,
          passengers_on_board: int = 48,
          transferring_passengers: int = 12) -> Dict[str, Any]:
    """Return the worked example with every intermediate step exposed."""
    cfg = config or EngineConfig()
    buffer_min = float(transfer_buffer_min if transfer_buffer_min is not None
                       else cfg.required_transfer_buffer_min)
    turnaround = cfg.vehicle_turnaround_min

    # ---------------- Step 1: delay at each stop on Route A ----------------
    route_a = [
        ("Stop A", _t("09:00"), _t("09:00")),
        ("Stop B", _t("09:10"), _t("09:15")),
        ("Stop C", _t("09:20"), _t("09:28")),
    ]
    stop_steps: List[Dict[str, Any]] = []
    for name, sched, act in route_a:
        delay = (act - sched).total_seconds() / 60.0
        stop_steps.append({
            "stop": name,
            "scheduled": _fmt(sched),
            "actual": _fmt(act),
            "delayMin": delay,
            "delayClass": cfg.classify_delay(delay),
            "calculation": f"{_fmt(act)} - {_fmt(sched)} = {delay:.0f} min",
        })
    final_delay = stop_steps[-1]["delayMin"]            # 8 min

    # ---------------- Step 2: growth of the delay along the trip ----------------
    growth = []
    for prev, cur in zip(stop_steps, stop_steps[1:]):
        growth.append({
            "segment": f"{prev['stop']} -> {cur['stop']}",
            "delayBeforeMin": prev["delayMin"],
            "delayAfterMin": cur["delayMin"],
            "changeMin": cur["delayMin"] - prev["delayMin"],
            "interpretation": (
                "delay grew: the vehicle lost further time on this segment"
                if cur["delayMin"] > prev["delayMin"] else
                "delay reduced: partial recovery on this segment"
                if cur["delayMin"] < prev["delayMin"] else
                "delay unchanged on this segment"),
        })

    # ---------------- Step 3: propagation to the next service (Route B) -------
    arrival_c = route_a[-1][2]                           # 09:28 actual
    earliest_dep = arrival_c + timedelta(minutes=turnaround)   # 09:33
    route_b_sched_dep = _t("09:25")
    route_b_actual_dep = _t("09:33")
    forced = (earliest_dep - route_b_sched_dep).total_seconds() / 60.0   # 8 min
    observed_b_delay = (route_b_actual_dep - route_b_sched_dep).total_seconds() / 60.0

    propagation = {
        "mechanism": "VEHICLE_CONTINUITY",
        "confidence": "HIGH",
        "sourceTrip": "Route A service",
        "affectedTrip": "Route B service",
        "steps": [
            {"step": 1,
             "description": "Route A arrived late at its final stop",
             "calculation": f"actual {_fmt(arrival_c)} - scheduled "
                            f"{_fmt(route_a[-1][1])} = {final_delay:.0f} min late"},
            {"step": 2,
             "description": "Add the scheduled vehicle turnaround",
             "calculation": f"{_fmt(arrival_c)} + {turnaround:.0f} min turnaround = "
                            f"earliest possible departure {_fmt(earliest_dep)}"},
            {"step": 3,
             "description": "Compare with Route B's scheduled departure",
             "calculation": f"{_fmt(earliest_dep)} - {_fmt(route_b_sched_dep)} = "
                            f"{forced:.0f} min of unavoidable propagated delay"},
            {"step": 4,
             "description": "Check against what actually happened",
             "calculation": f"observed delay = {_fmt(route_b_actual_dep)} - "
                            f"{_fmt(route_b_sched_dep)} = {observed_b_delay:.0f} min, "
                            f"which matches the {forced:.0f} min prediction"},
        ],
        "predictedPropagatedDelayMin": forced,
        "observedDelayMin": observed_b_delay,
        "predictionMatchesObservation": abs(forced - observed_b_delay) <= 1.0,
        "evidence": [
            "The same physical vehicle operates Route A then Route B.",
            f"Route A's {final_delay:.0f} min late arrival at {_fmt(arrival_c)} plus the "
            f"{turnaround:.0f} min turnaround makes a {_fmt(route_b_sched_dep)} departure "
            "physically impossible.",
            f"Route B was observed departing at {_fmt(route_b_actual_dep)}, exactly the "
            "predicted time, which confirms the mechanism rather than merely correlating.",
        ],
        "whyNotMerelyCorrelation": (
            "Vehicle continuity is a physical constraint: the next service cannot "
            "depart before the vehicle arrives and turns around. The prediction was "
            "made from the constraint and then matched the observation, so this is "
            "treated as causal. A delay on an unrelated route at the same stop would "
            "only ever be reported as correlation."),
    }

    # ---------------- Step 4: connection risk ----------------
    feeder_arrival = _t("09:28")
    connecting_departure = _t("09:32")
    available = (connecting_departure - feeder_arrival).total_seconds() / 60.0   # 4 min
    shortfall = buffer_min - available
    at_risk = available < buffer_min
    missed = available < cfg.missed_connection_margin_min

    connection = {
        "interchange": "Stop C",
        "feederActualArrival": _fmt(feeder_arrival),
        "connectingScheduledDeparture": _fmt(connecting_departure),
        "requiredBufferMin": buffer_min,
        "availableTransferMin": available,
        "shortfallMin": round(max(0.0, shortfall), 1),
        "isAtRisk": at_risk,
        "isMissed": missed,
        "status": "MISSED" if missed else "AT_RISK" if at_risk else "SAFE",
        "steps": [
            {"step": 1,
             "description": "Available transfer time",
             "calculation": f"connecting departure {_fmt(connecting_departure)} - feeder "
                            f"arrival {_fmt(feeder_arrival)} = {available:.0f} min"},
            {"step": 2,
             "description": "Compare with the required transfer buffer",
             "calculation": f"{available:.0f} min available < {buffer_min:.0f} min required "
                            f"-> connection AT RISK (shortfall {max(0.0, shortfall):.0f} min)"},
        ],
        "explanation": (
            f"Passengers need {buffer_min:.0f} minutes to walk between services at this "
            f"interchange, but the late feeder leaves only {available:.0f} minutes. "
            "The connection is therefore at risk; whether it is actually missed depends "
            "on whether the connecting service is held."),
    }

    # ---------------- Step 5: passenger impact ----------------
    pax_minutes = final_delay * passengers_on_board
    extra_wait_total = transferring_passengers * cfg.additional_wait_on_missed_connection_min
    passenger_impact = {
        "assumptions": {
            "passengersOnBoardRouteA": passengers_on_board,
            "transferringPassengersAtStopC": transferring_passengers,
            "assumedWaitForNextConnectingServiceMin":
                cfg.additional_wait_on_missed_connection_min,
        },
        "steps": [
            {"step": 1,
             "description": "Passenger-minutes lost to the delay itself",
             "calculation": f"{final_delay:.0f} min delay x {passengers_on_board} "
                            f"passengers on board = {pax_minutes:.0f} passenger-minutes"},
            {"step": 2,
             "description": "Passengers exposed to the at-risk connection",
             "calculation": f"{transferring_passengers} passengers were booked to "
                            f"transfer at Stop C and face a {max(0.0, shortfall):.0f} min "
                            "shortfall"},
            {"step": 3,
             "description": "Additional waiting time if the connection is missed",
             "calculation": f"{transferring_passengers} passengers x "
                            f"{cfg.additional_wait_on_missed_connection_min:.0f} min wait "
                            f"for the next service = {extra_wait_total:.0f} "
                            "additional passenger-minutes"},
        ],
        "passengerMinutesLostOnDelay": pax_minutes,
        "passengersFacingMissedConnection": transferring_passengers,
        "additionalWaitPassengerMinutes": extra_wait_total,
        "totalPassengerMinutesImpact": pax_minutes + extra_wait_total,
        "isEstimate": True,
        "estimateCaveat": (
            "These are estimates built from average passenger-volume profiles, not "
            "confirmed passenger counts. Ticketing or APC data would be required to "
            "confirm how many passengers were actually affected."),
    }

    return {
        "title": "Worked propagation example: Route A -> Route B with an at-risk connection",
        "serviceDate": DATE,
        "configurationUsed": {
            "vehicleTurnaroundMin": turnaround,
            "requiredTransferBufferMin": buffer_min,
            "onTimeThresholdMin": cfg.on_time_threshold_min,
        },
        "step1DelayAtEachStop": stop_steps,
        "step2DelayProgression": growth,
        "step3PropagationToNextService": propagation,
        "step4ConnectionRisk": connection,
        "step5PassengerImpact": passenger_impact,
        "conclusion": (
            f"An initial 5 minute delay at Stop B grew to {final_delay:.0f} minutes by "
            f"Stop C. Because the same vehicle then works Route B, {forced:.0f} minutes of "
            "that delay was forced onto the next service, which was observed departing "
            f"exactly {observed_b_delay:.0f} minutes late. Separately, the late arrival "
            f"left only {available:.0f} minutes for a transfer needing {buffer_min:.0f}, "
            f"putting {transferring_passengers} transferring passengers at risk and "
            f"accounting for roughly {pax_minutes + extra_wait_total:.0f} "
            "passenger-minutes of total impact."),
    }
