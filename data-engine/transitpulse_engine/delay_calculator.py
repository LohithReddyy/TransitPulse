"""
Delay calculation.

    delay_minutes = actual_arrival - scheduled_arrival

Computed at every stop of every trip. Missing GPS events are never counted
as zero delay: they are either excluded or explicitly estimated (and marked
as estimated) depending on `EngineConfig.missing_gps_strategy`.
"""
from __future__ import annotations

import numpy as np
import pandas as pd

from .config import EngineConfig


class DelayCalculator:
    """Turns joined scheduled/actual movements into classified delay events."""

    def __init__(self, config: EngineConfig | None = None):
        self.config = config or EngineConfig()

    # ------------------------------------------------------------------ #

    def compute(self, movements: pd.DataFrame) -> pd.DataFrame:
        df = movements.copy()

        delta = (df["actual_arrival"] - df["scheduled_arrival"])
        df["delay_min"] = (delta.dt.total_seconds() / 60.0).round(2)

        # A non-comparable record (missing GPS, diverted skip) has no delay.
        df.loc[~df["comparable"], "delay_min"] = np.nan
        df["delay_source"] = np.where(df["comparable"], "OBSERVED", "UNAVAILABLE")

        if self.config.missing_gps_strategy == "ESTIMATE":
            df = self._estimate_missing(df)

        df["delay_class"] = df["delay_min"].apply(
            lambda v: self.config.classify_delay(None if pd.isna(v) else float(v)))
        df["is_delayed"] = df["delay_min"] >= self.config.on_time_threshold_min
        df["is_missing"] = df["delay_class"].eq("MISSING")

        df["dwell_min"] = ((df["actual_departure"] - df["actual_arrival"])
                           .dt.total_seconds() / 60.0).round(2)
        df["schedule_adherence_sec"] = (df["delay_min"] * 60).round(0)
        return df

    # ------------------------------------------------------------------ #

    def _estimate_missing(self, df: pd.DataFrame) -> pd.DataFrame:
        """
        Interpolate a delay for a missing stop from the nearest observed
        stops on the same trip, and mark it ESTIMATED so no consumer mistakes
        it for a measurement. Interpolation only fills gaps *between* two
        observations; a missing first or last stop stays unavailable.
        """
        out = []
        for trip_id, grp in df.groupby("trip_id", sort=False):
            grp = grp.sort_values("stop_sequence").copy()
            gap = grp["delay_min"].isna() & grp["data_quality_flag"].eq("MISSING_GPS")
            if gap.any() and grp["delay_min"].notna().sum() >= 2:
                interp = grp["delay_min"].interpolate(method="linear",
                                                      limit_area="inside")
                filled = gap & interp.notna()
                grp.loc[filled, "delay_min"] = interp[filled].round(2)
                grp.loc[filled, "delay_source"] = "ESTIMATED"
            out.append(grp)
        return pd.concat(out).sort_index()

    # ------------------------------------------------------------------ #

    def trip_summary(self, delays: pd.DataFrame) -> pd.DataFrame:
        """One row per trip: worst delay, final delay, recovery behaviour."""
        rows = []
        for trip_id, grp in delays.groupby("trip_id", sort=False):
            grp = grp.sort_values("stop_sequence")
            obs = grp[grp["delay_min"].notna()]
            if obs.empty:
                rows.append({
                    "trip_id": trip_id, "route_id": grp["route_id"].iloc[0],
                    "vehicle_id": grp["vehicle_id"].iloc[0],
                    "stops_total": len(grp), "stops_observed": 0,
                    "stops_missing": int(grp["is_missing"].sum()),
                    "max_delay_min": None, "final_delay_min": None,
                    "avg_delay_min": None, "first_delay_stop_sequence": None,
                    "delay_class": "MISSING", "recovered": False,
                    "recovery_min": 0.0, "data_confidence": "NONE"})
                continue
            max_delay = float(obs["delay_min"].max())
            final_delay = float(obs["delay_min"].iloc[-1])
            onset = obs[obs["delay_min"] >= self.config.on_time_threshold_min]
            recovery = max(0.0, round(max_delay - final_delay, 2))
            coverage = len(obs) / len(grp)
            rows.append({
                "trip_id": trip_id,
                "route_id": grp["route_id"].iloc[0],
                "vehicle_id": grp["vehicle_id"].iloc[0],
                "stops_total": len(grp),
                "stops_observed": len(obs),
                "stops_missing": int(grp["is_missing"].sum()),
                "max_delay_min": round(max_delay, 2),
                "final_delay_min": round(final_delay, 2),
                "avg_delay_min": round(float(obs["delay_min"].mean()), 2),
                "first_delay_stop_sequence": (int(onset["stop_sequence"].iloc[0])
                                              if not onset.empty else None),
                "delay_class": self.config.classify_delay(max_delay),
                # A trip can only "recover" if it was actually delayed first.
                "recovered": bool(max_delay >= self.config.on_time_threshold_min
                                  and recovery >= self.config.recovery_min_reduction_min
                                  and final_delay < self.config.on_time_threshold_min),
                "recovery_min": recovery,
                "data_confidence": ("HIGH" if coverage >= 0.9 else
                                    "MEDIUM" if coverage >= 0.6 else "LOW"),
            })
        return pd.DataFrame(rows)

    def route_summary(self, delays: pd.DataFrame,
                      trip_summary: pd.DataFrame) -> pd.DataFrame:
        """Per-route performance aggregates for the dashboard and warehouse."""
        obs = delays[delays["delay_min"].notna()]
        g = obs.groupby("route_id")
        base = pd.DataFrame({
            "observations": g.size(),
            "avg_delay_min": g["delay_min"].mean().round(2),
            "p90_delay_min": g["delay_min"].quantile(0.90).round(2),
            "max_delay_min": g["delay_min"].max().round(2),
            "on_time_observations": g.apply(
                lambda x: int((x["delay_min"] < self.config.on_time_threshold_min).sum()),
                include_groups=False),
        }).reset_index()
        base["on_time_percentage"] = (
            base["on_time_observations"] / base["observations"] * 100).round(2)

        t = trip_summary.groupby("route_id")
        trips_agg = pd.DataFrame({
            "trips_total": t.size(),
            "trips_delayed": t.apply(
                lambda x: int((x["max_delay_min"].fillna(0)
                               >= self.config.on_time_threshold_min).sum()),
                include_groups=False),
            "trips_recovered": t["recovered"].sum().astype(int),
        }).reset_index()

        out = base.merge(trips_agg, on="route_id", how="outer")
        out["recovery_percentage"] = np.where(
            out["trips_delayed"] > 0,
            (out["trips_recovered"] / out["trips_delayed"] * 100).round(2), 0.0)
        out["performance_band"] = pd.cut(
            out["on_time_percentage"].fillna(0),
            bins=[-0.01, 60, 75, 90, 100.01],
            labels=["POOR", "FAIR", "GOOD", "EXCELLENT"]).astype(str)
        return out.sort_values("route_id").reset_index(drop=True)

    def stop_summary(self, delays: pd.DataFrame) -> pd.DataFrame:
        """Per-stop delay frequency, used by the Stops page."""
        obs = delays[delays["delay_min"].notna()]
        g = obs.groupby("stop_id")
        out = pd.DataFrame({
            "observations": g.size(),
            "avg_delay_min": g["delay_min"].mean().round(2),
            "max_delay_min": g["delay_min"].max().round(2),
            "delayed_observations": g.apply(
                lambda x: int((x["delay_min"] >= self.config.on_time_threshold_min).sum()),
                include_groups=False),
        }).reset_index()
        out["delay_frequency_pct"] = (
            out["delayed_observations"] / out["observations"] * 100).round(2)
        return out.sort_values("delay_frequency_pct", ascending=False).reset_index(drop=True)
