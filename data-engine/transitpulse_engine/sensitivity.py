"""
Sensitivity analysis.

The required transfer buffer is an assumption, not a measurement. This
module re-runs the connection risk engine across a range of buffers so the
operator can see how much of the reported risk is a product of that
assumption rather than of the observed operation.
"""
from __future__ import annotations

from typing import Iterable, List

import pandas as pd

from .config import EngineConfig
from .connection_risk import ConnectionRiskEngine
from .passenger_impact import PassengerImpactEngine


class SensitivityAnalyzer:
    def __init__(self, config: EngineConfig | None = None):
        self.config = config or EngineConfig()
        self.risk_engine = ConnectionRiskEngine(self.config)
        self.impact_engine = PassengerImpactEngine(self.config)

    def run(self, connections: pd.DataFrame, delays: pd.DataFrame,
            buffers: Iterable[float] | None = None) -> pd.DataFrame:
        buffers = list(buffers if buffers is not None
                       else self.config.sensitivity_buffers_min)
        rows: List[dict] = []
        for buf in buffers:
            risk = self.risk_engine.evaluate(connections, delays, buffer_min=buf)
            s = self.risk_engine.summary(risk)
            impact = self.impact_engine.connection_impact(risk)
            rows.append({
                "required_buffer_min": float(buf),
                "connections_assessed": s["assessed"],
                "at_risk_connections": s["atRisk"],
                "missed_connections": s["missed"],
                "safe_connections": s["safe"],
                "at_risk_percentage": s["atRiskPercentage"],
                "passengers_at_risk": s["passengersAtRisk"],
                "passengers_missed": s["passengersMissed"],
                "additional_wait_minutes": impact["additionalWaitMinutes"],
            })
        df = pd.DataFrame(rows).sort_values("required_buffer_min").reset_index(drop=True)
        if len(df) > 1:
            base = df.iloc[0]
            df["delta_at_risk_vs_lowest_buffer"] = (
                df["at_risk_connections"] - base["at_risk_connections"]).astype(int)
            df["delta_passengers_vs_lowest_buffer"] = (
                df["passengers_at_risk"] - base["passengers_at_risk"]).astype(int)
        return df

    def interpretation(self, result: pd.DataFrame) -> dict:
        """Plain-language reading of the sensitivity table."""
        if result is None or result.empty:
            return {}
        lo, hi = result.iloc[0], result.iloc[-1]
        spread = int(hi["at_risk_connections"] - lo["at_risk_connections"])
        assessed = int(hi["connections_assessed"]) or 1
        return {
            "bufferRangeMin": [float(lo["required_buffer_min"]),
                               float(hi["required_buffer_min"])],
            "atRiskRange": [int(lo["at_risk_connections"]), int(hi["at_risk_connections"])],
            "passengersAtRiskRange": [int(lo["passengers_at_risk"]),
                                      int(hi["passengers_at_risk"])],
            "atRiskSpread": spread,
            "sensitivityPctOfAssessed": round(spread / assessed * 100, 2),
            "verdict": ("HIGHLY_SENSITIVE" if spread / assessed > 0.3 else
                        "MODERATELY_SENSITIVE" if spread / assessed > 0.1 else
                        "ROBUST"),
            "note": (f"Moving the required transfer buffer from "
                     f"{lo['required_buffer_min']:.0f} to {hi['required_buffer_min']:.0f} "
                     f"minutes changes the at-risk connection count by {spread}, which is "
                     f"{round(spread / assessed * 100, 1)}% of all assessed connections. "
                     "Reported connection risk should always be quoted together with "
                     "the buffer assumption used."),
        }
