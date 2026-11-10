"""
Connection risk engine.

    available_transfer_time = connecting_departure - feeder_actual_arrival
    at risk when: available_transfer_time < required_transfer_buffer
    missed    when: available_transfer_time < missed_connection_margin (default 0)

Every connection is evaluated against both the planned timetable and the
observed arrival, so the UI can show how much of the risk is structural
(a tight timetable) versus operational (a late feeder).
"""
from __future__ import annotations

from typing import Optional

import numpy as np
import pandas as pd

from .config import EngineConfig


class ConnectionRiskEngine:
    def __init__(self, config: EngineConfig | None = None):
        self.config = config or EngineConfig()

    # ------------------------------------------------------------------ #

    def evaluate(self, connections: pd.DataFrame, delays: pd.DataFrame,
                 buffer_min: Optional[float] = None) -> pd.DataFrame:
        """Assess every timetabled transfer opportunity."""
        cfg = self.config
        required = float(buffer_min if buffer_min is not None
                         else cfg.required_transfer_buffer_min)
        if connections is None or connections.empty:
            return pd.DataFrame()

        obs = delays[delays["delay_min"].notna()]
        first_visit = (obs.sort_values(["trip_id", "stop_sequence"])
                          .drop_duplicates(subset=["trip_id", "stop_id"], keep="first"))
        feeder_arr = {(r.trip_id, r.stop_id): (float(r.delay_min), r.actual_arrival)
                      for r in first_visit.itertuples(index=False)}
        conn_dep_actual = {(r.trip_id, r.stop_id): r.actual_departure
                           for r in first_visit.itertuples(index=False)}

        rows = []
        for c in connections.itertuples(index=False):
            key = (c.feeder_trip_id, c.interchange_stop_id)
            planned_transfer = float(c.planned_transfer_min)
            conn_sched_dep = c.connecting_scheduled_departure

            if key in feeder_arr:
                feeder_delay, feeder_actual = feeder_arr[key]
                observed = True
            else:
                # No GPS for the feeder at the interchange: the connection
                # cannot be assessed. It is never assumed to be fine.
                feeder_delay, feeder_actual, observed = np.nan, pd.NaT, False

            # The connecting service may itself be running late and so still
            # be at the stop when the late feeder arrives.
            actual_conn_dep = conn_dep_actual.get(
                (c.connecting_trip_id, c.interchange_stop_id), pd.NaT)

            if observed:
                available = (conn_sched_dep - feeder_actual).total_seconds() / 60.0
                available_actual = (
                    (actual_conn_dep - feeder_actual).total_seconds() / 60.0
                    if pd.notna(actual_conn_dep) else np.nan)
            else:
                available = available_actual = np.nan

            planned_at_risk = planned_transfer < required
            if observed:
                at_risk = available < required
                missed = available < cfg.missed_connection_margin_min
                held = (pd.notna(available_actual)
                        and available_actual >= required > available)
                status = ("MISSED" if missed else
                          "HELD_BY_CONNECTING_SERVICE" if held else
                          "AT_RISK" if at_risk else "SAFE")
            else:
                at_risk = missed = held = False
                status = "UNKNOWN_NO_GPS"

            rows.append({
                "connection_id": c.connection_id,
                "interchange_stop_id": c.interchange_stop_id,
                "feeder_trip_id": c.feeder_trip_id,
                "feeder_route_id": c.feeder_route_id,
                "connecting_trip_id": c.connecting_trip_id,
                "connecting_route_id": c.connecting_route_id,
                "feeder_scheduled_arrival": c.feeder_scheduled_arrival,
                "feeder_actual_arrival": feeder_actual,
                "feeder_delay_min": None if pd.isna(feeder_delay) else round(feeder_delay, 2),
                "connecting_scheduled_departure": conn_sched_dep,
                "connecting_actual_departure": actual_conn_dep,
                "planned_transfer_min": round(planned_transfer, 2),
                "available_transfer_min": None if pd.isna(available) else round(available, 2),
                "available_transfer_actual_min": (None if pd.isna(available_actual)
                                                  else round(available_actual, 2)),
                "required_buffer_min": required,
                "shortfall_min": (None if pd.isna(available)
                                  else round(max(0.0, required - available), 2)),
                "planned_connection_tight": bool(planned_at_risk),
                "is_at_risk": bool(at_risk),
                "is_missed": bool(missed),
                "status": status,
                "transferring_passengers": int(c.typical_transferring_passengers),
                "risk_score": self._risk_score(available, required, observed),
                "explanation": self._explain(c, feeder_delay, available, required,
                                             status, observed),
            })
        return pd.DataFrame(rows)

    # ------------------------------------------------------------------ #

    @staticmethod
    def _risk_score(available: float, required: float, observed: bool) -> Optional[float]:
        """0 = safe, 1 = certainly missed. Linear in the shortfall."""
        if not observed or pd.isna(available):
            return None
        if available >= required:
            return 0.0
        if available <= 0:
            return 1.0
        return round(1.0 - (available / required), 3)

    @staticmethod
    def _explain(c, feeder_delay, available, required, status, observed) -> str:
        if not observed:
            return (f"Connection {c.connection_id} could not be assessed: no GPS "
                    f"arrival recorded for feeder trip {c.feeder_trip_id} at "
                    f"{c.interchange_stop_id}. Excluded from risk totals.")
        return (f"Feeder trip {c.feeder_trip_id} arrived {feeder_delay:.1f} min late. "
                f"Available transfer time {available:.1f} min against a required "
                f"{required:.0f} min buffer -> {status}.")

    # ------------------------------------------------------------------ #

    def summary(self, risk: pd.DataFrame) -> dict:
        if risk is None or risk.empty:
            return {"totalConnections": 0, "assessed": 0, "atRisk": 0, "missed": 0,
                    "safe": 0, "unknown": 0, "passengersAtRisk": 0,
                    "passengersMissed": 0, "atRiskPercentage": 0.0}
        assessed = risk[risk["status"] != "UNKNOWN_NO_GPS"]
        at_risk = assessed[assessed["is_at_risk"]]
        missed = assessed[assessed["is_missed"]]
        return {
            "totalConnections": int(len(risk)),
            "assessed": int(len(assessed)),
            "atRisk": int(len(at_risk)),
            "missed": int(len(missed)),
            "safe": int((assessed["status"] == "SAFE").sum()),
            "heldByConnectingService": int(
                (assessed["status"] == "HELD_BY_CONNECTING_SERVICE").sum()),
            "unknown": int((risk["status"] == "UNKNOWN_NO_GPS").sum()),
            "passengersAtRisk": int(at_risk["transferring_passengers"].sum()),
            "passengersMissed": int(missed["transferring_passengers"].sum()),
            "atRiskPercentage": (round(len(at_risk) / len(assessed) * 100, 2)
                                 if len(assessed) else 0.0),
            "plannedTightConnections": int(risk["planned_connection_tight"].sum()),
        }
