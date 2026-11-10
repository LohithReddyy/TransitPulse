"""
Passenger impact estimation.

Every number this module produces is an ESTIMATE derived from average
passenger-volume profiles, not a confirmed passenger count. Each output row
carries `estimate_basis` and `confidence` so the UI can label it as such,
and low-volume stops are marked so small samples are not over-read.
"""
from __future__ import annotations

import numpy as np
import pandas as pd

from .config import EngineConfig


def time_band(ts) -> str:
    """Map a timestamp onto the passenger-volume time bands."""
    if pd.isna(ts):
        return "MIDDAY"
    h = ts.hour
    if h < 7:
        return "EARLY"
    if h < 10:
        return "AM_PEAK"
    if h < 16:
        return "MIDDAY"
    if h < 19:
        return "PM_PEAK"
    return "EVENING"


class PassengerImpactEngine:
    def __init__(self, config: EngineConfig | None = None):
        self.config = config or EngineConfig()

    # ------------------------------------------------------------------ #

    def estimate(self, delays: pd.DataFrame, passenger_volumes: pd.DataFrame,
                 connection_risk: pd.DataFrame | None = None) -> pd.DataFrame:
        """
        Estimate passengers exposed to delay at each delayed stop event.

        passengers_exposed    = boardings + average_load at that stop/band
        additional_wait_min   = delay_min x boardings  (passenger-minutes)
        """
        cfg = self.config
        obs = delays[delays["delay_min"].notna()].copy()
        obs = obs[obs["delay_min"] >= cfg.on_time_threshold_min]
        if obs.empty:
            return pd.DataFrame()

        obs["time_band"] = obs["scheduled_arrival"].apply(time_band)

        pv = passenger_volumes.copy()
        for col in ("boardings", "alightings", "average_load"):
            pv[col] = pd.to_numeric(pv[col], errors="coerce").fillna(0)
        pv = pv.drop_duplicates(subset=["route_id", "stop_id", "time_band"], keep="last")

        merged = obs.merge(
            pv[["route_id", "stop_id", "time_band", "boardings", "alightings",
                "average_load", "is_low_volume", "confidence"]],
            on=["route_id", "stop_id", "time_band"], how="left")

        # No volume profile for this stop/band: estimate nothing rather than zero.
        merged["has_volume_data"] = merged["boardings"].notna()
        merged[["boardings", "alightings", "average_load"]] = (
            merged[["boardings", "alightings", "average_load"]].fillna(0))
        merged["is_low_volume"] = merged["is_low_volume"].fillna("Y")
        merged["confidence"] = merged["confidence"].fillna("NONE")

        merged["passengers_exposed"] = (merged["boardings"]
                                        + merged["average_load"]).round(0).astype(int)
        merged["passenger_minutes_lost"] = (merged["delay_min"]
                                            * merged["passengers_exposed"]).round(1)
        merged["estimated_transferring"] = (merged["boardings"]
                                            * cfg.transfer_rate).round(1)
        merged["estimate_basis"] = np.where(
            merged["has_volume_data"],
            "Average boardings and load for this stop and time band",
            "No volume profile available for this stop and time band")
        merged["confidence"] = np.where(merged["has_volume_data"],
                                        merged["confidence"], "NONE")
        merged["is_estimate"] = True

        cols = ["trip_id", "route_id", "stop_id", "stop_sequence", "time_band",
                "scheduled_arrival", "actual_arrival", "delay_min", "delay_class",
                "boardings", "average_load", "passengers_exposed",
                "passenger_minutes_lost", "estimated_transferring",
                "is_low_volume", "confidence", "estimate_basis", "is_estimate",
                "has_volume_data"]
        return merged[cols].reset_index(drop=True)

    # ------------------------------------------------------------------ #

    def by_route(self, impact: pd.DataFrame) -> pd.DataFrame:
        if impact is None or impact.empty:
            return pd.DataFrame()
        g = impact.groupby("route_id")
        out = pd.DataFrame({
            "delayed_stop_events": g.size(),
            "passengers_exposed": g["passengers_exposed"].sum().astype(int),
            "passenger_minutes_lost": g["passenger_minutes_lost"].sum().round(1),
            "avg_delay_min": g["delay_min"].mean().round(2),
            "low_confidence_events": g.apply(
                lambda x: int((x["confidence"].isin(["LOW", "NONE"])).sum()),
                include_groups=False),
        }).reset_index()
        out["avg_additional_wait_per_passenger_min"] = np.where(
            out["passengers_exposed"] > 0,
            (out["passenger_minutes_lost"] / out["passengers_exposed"]).round(2), 0.0)
        out["confidence"] = np.where(
            out["low_confidence_events"] / out["delayed_stop_events"] > 0.5,
            "LOW", "MEDIUM")
        return out.sort_values("passenger_minutes_lost", ascending=False).reset_index(drop=True)

    def by_stop(self, impact: pd.DataFrame) -> pd.DataFrame:
        if impact is None or impact.empty:
            return pd.DataFrame()
        g = impact.groupby("stop_id")
        out = pd.DataFrame({
            "delayed_events": g.size(),
            "passengers_exposed": g["passengers_exposed"].sum().astype(int),
            "passenger_minutes_lost": g["passenger_minutes_lost"].sum().round(1),
            "avg_delay_min": g["delay_min"].mean().round(2),
            "is_low_volume": g["is_low_volume"].agg(
                lambda s: "Y" if (s == "Y").mean() > 0.5 else "N"),
        }).reset_index()
        out["low_confidence"] = out["is_low_volume"].eq("Y")
        return out.sort_values("passenger_minutes_lost", ascending=False).reset_index(drop=True)

    def connection_impact(self, connection_risk: pd.DataFrame) -> dict:
        """Passengers affected through missed or at-risk transfers."""
        cfg = self.config
        if connection_risk is None or connection_risk.empty:
            return {"missedTransferPassengers": 0, "atRiskTransferPassengers": 0,
                    "additionalWaitMinutes": 0.0, "isEstimate": True}
        assessed = connection_risk[connection_risk["status"] != "UNKNOWN_NO_GPS"]
        missed = assessed[assessed["is_missed"]]
        at_risk = assessed[assessed["is_at_risk"] & ~assessed["is_missed"]]
        missed_pax = int(missed["transferring_passengers"].sum())
        return {
            "missedTransferPassengers": missed_pax,
            "atRiskTransferPassengers": int(at_risk["transferring_passengers"].sum()),
            "additionalWaitMinutes": round(
                missed_pax * cfg.additional_wait_on_missed_connection_min, 1),
            "assumedWaitPerMissedConnectionMin": cfg.additional_wait_on_missed_connection_min,
            "isEstimate": True,
            "estimateBasis": ("Typical transferring passenger counts per connection "
                              "multiplied by the assumed wait for the next service"),
        }

    def summary(self, impact: pd.DataFrame,
                connection_risk: pd.DataFrame | None = None) -> dict:
        base = {"passengersExposed": 0, "passengerMinutesLost": 0.0,
                "delayedStopEvents": 0, "lowConfidenceShare": 0.0}
        if impact is not None and not impact.empty:
            base = {
                "passengersExposed": int(impact["passengers_exposed"].sum()),
                "passengerMinutesLost": round(float(impact["passenger_minutes_lost"].sum()), 1),
                "delayedStopEvents": int(len(impact)),
                "lowConfidenceShare": round(
                    float(impact["confidence"].isin(["LOW", "NONE"]).mean()) * 100, 2),
            }
        base.update(self.connection_impact(connection_risk))
        base["isEstimate"] = True
        return base
