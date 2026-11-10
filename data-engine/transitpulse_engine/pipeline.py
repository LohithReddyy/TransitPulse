"""
Pipeline orchestration.

Runs ingestion -> delay -> propagation -> connection risk -> passenger
impact -> sensitivity, and writes outputs in two shapes:

  * CSV  - wide tables for MySQL / Snowflake loading
  * JSON - the analytics payloads the Spring Boot API serves to React

Run:  python -m transitpulse_engine.pipeline --output ./output
"""
from __future__ import annotations

import argparse
import json
import logging
from datetime import date, datetime
from pathlib import Path
from typing import Any, Dict

import numpy as np
import pandas as pd

from .config import EngineConfig
from .connection_risk import ConnectionRiskEngine
from .delay_calculator import DelayCalculator
from .ingestion import DataIngestion
from .passenger_impact import PassengerImpactEngine
from .propagation import PropagationEngine
from .sensitivity import SensitivityAnalyzer
from .worked_example import build as build_worked_example

log = logging.getLogger("transitpulse.pipeline")


def _json_default(obj: Any):
    if isinstance(obj, (pd.Timestamp, datetime, date)):
        return None if pd.isna(obj) else obj.isoformat()
    if isinstance(obj, (np.integer,)):
        return int(obj)
    if isinstance(obj, (np.floating,)):
        return None if np.isnan(obj) else float(obj)
    if isinstance(obj, (np.bool_,)):
        return bool(obj)
    if obj is pd.NaT:
        return None
    raise TypeError(f"Not JSON serialisable: {type(obj)}")


def _records(df: pd.DataFrame) -> list:
    """DataFrame -> JSON-safe list of dicts, with NaN turned into null."""
    if df is None or df.empty:
        return []
    return json.loads(df.to_json(orient="records", date_format="iso"))


class Pipeline:
    def __init__(self, config: EngineConfig | None = None):
        self.config = config or EngineConfig.load()
        self.results: Dict[str, Any] = {}

    # ------------------------------------------------------------------ #

    def run(self) -> Dict[str, Any]:
        cfg = self.config
        log.info("Loading datasets from %s", cfg.datasets_dir)
        ingestion = DataIngestion(cfg)
        frames = ingestion.load_all()
        movements = ingestion.movements()

        log.info("Calculating delays for %d movements", len(movements))
        dc = DelayCalculator(cfg)
        delays = dc.compute(movements)
        trip_summary = dc.trip_summary(delays)
        route_summary = dc.route_summary(delays, trip_summary)
        stop_summary = dc.stop_summary(delays)

        log.info("Running propagation engine")
        pe = PropagationEngine(cfg)
        links = pe.run(delays, trip_summary, frames["connections"], frames["incidents"])

        log.info("Evaluating connection risk")
        cre = ConnectionRiskEngine(cfg)
        risk = cre.evaluate(frames["connections"], delays)

        log.info("Estimating passenger impact")
        pie = PassengerImpactEngine(cfg)
        impact = pie.estimate(delays, frames["passenger_volumes"], risk)
        impact_route = pie.by_route(impact)
        impact_stop = pie.by_stop(impact)

        log.info("Running sensitivity analysis")
        sa = SensitivityAnalyzer(cfg)
        sensitivity = sa.run(frames["connections"], delays)

        self.results = {
            "frames": frames,
            "delays": delays,
            "trip_summary": trip_summary,
            "route_summary": route_summary,
            "stop_summary": stop_summary,
            "propagation": links,
            "connection_risk": risk,
            "passenger_impact": impact,
            "passenger_impact_route": impact_route,
            "passenger_impact_stop": impact_stop,
            "sensitivity": sensitivity,
            "validation": ingestion.report,
            "summaries": {
                "dashboard": self._dashboard(frames, delays, trip_summary,
                                             route_summary, links, risk, impact,
                                             pe, cre, pie),
                "propagation": pe.summary(links),
                "connectionRisk": cre.summary(risk),
                "passengerImpact": pie.summary(impact, risk),
                "sensitivity": sa.interpretation(sensitivity),
                "validation": ingestion.report.to_dict(),
            },
            "workedExample": build_worked_example(cfg),
        }
        return self.results

    # ------------------------------------------------------------------ #

    def _dashboard(self, frames, delays, trip_summary, route_summary, links,
                   risk, impact, pe, cre, pie) -> dict:
        cfg = self.config
        delayed = trip_summary[trip_summary["max_delay_min"].fillna(0)
                               >= cfg.on_time_threshold_min]
        obs = delays[delays["delay_min"].notna()]
        on_time_obs = int((obs["delay_min"] < cfg.on_time_threshold_min).sum())
        recovered = int(trip_summary["recovered"].sum())
        risk_summary = cre.summary(risk)
        impact_summary = pie.summary(impact, risk)

        return {
            "serviceDate": str(frames["trips"]["service_date"].iloc[0]),
            "totalRoutes": int(len(frames["routes"])),
            "totalStops": int(len(frames["stops"])),
            "totalVehicles": int(len(frames["vehicles"])),
            "totalScheduledTrips": int(len(frames["trips"])),
            "totalMovements": int(len(delays)),
            "observedMovements": int(len(obs)),
            # Counts every movement with no usable observation: missing GPS
            # events AND stops skipped by a diversion. Named for what it
            # measures, so it is not confused with the GPS-only count the API
            # reports separately as missingGpsRecords.
            "unobservedMovements": int(delays["is_missing"].sum()),
            "delayedTrips": int(len(delayed)),
            "onTimePercentage": round(on_time_obs / len(obs) * 100, 2) if len(obs) else 0.0,
            "averageDelayMin": round(float(obs["delay_min"].mean()), 2) if len(obs) else 0.0,
            "p90DelayMin": round(float(obs["delay_min"].quantile(0.9)), 2) if len(obs) else 0.0,
            "maxDelayMin": round(float(obs["delay_min"].max()), 2) if len(obs) else 0.0,
            "recoveredTrips": recovered,
            "recoveryPercentage": (round(recovered / len(delayed) * 100, 2)
                                   if len(delayed) else 0.0),
            "passengersAffected": impact_summary["passengersExposed"],
            "passengerMinutesLost": impact_summary["passengerMinutesLost"],
            "propagationLinks": int(len(links)),
            "highConfidencePropagations": (int((links["confidence"] == "HIGH").sum())
                                           if not links.empty else 0),
            "connectionsAtRisk": risk_summary["atRisk"],
            "missedConnections": risk_summary["missed"],
            "openIncidents": int((frames["incidents"]["status"] != "CLOSED").sum()),
            "totalIncidents": int(len(frames["incidents"])),
            "delayClassDistribution": delays["delay_class"].value_counts().to_dict(),
            "estimateCaveat": ("Passenger figures are estimates derived from average "
                               "volume profiles, not confirmed passenger counts."),
        }

    # ------------------------------------------------------------------ #

    def export(self, output_dir: str | Path | None = None) -> Path:
        """Write CSV and JSON outputs for the backend and warehouse."""
        if not self.results:
            self.run()
        out = Path(output_dir or self.config.output_dir)
        (out / "csv").mkdir(parents=True, exist_ok=True)
        (out / "json").mkdir(parents=True, exist_ok=True)

        csv_tables = {
            "delay_events": self.results["delays"],
            "trip_delay_summary": self.results["trip_summary"],
            "route_performance": self.results["route_summary"],
            "stop_performance": self.results["stop_summary"],
            "propagation_results": self.results["propagation"],
            "connection_risk": self.results["connection_risk"],
            "passenger_impact": self.results["passenger_impact"],
            "passenger_impact_by_route": self.results["passenger_impact_route"],
            "passenger_impact_by_stop": self.results["passenger_impact_stop"],
            "sensitivity_analysis": self.results["sensitivity"],
        }
        for name, df in csv_tables.items():
            if df is None or df.empty:
                continue
            frame = df.copy()
            # Evidence is a list; flatten it so the CSV stays loadable by SQL.
            if "evidence" in frame.columns:
                frame["evidence"] = frame["evidence"].apply(
                    lambda v: " | ".join(v) if isinstance(v, list) else v)
            frame.to_csv(out / "csv" / f"{name}.csv", index=False)
            log.info("wrote csv/%s.csv (%d rows)", name, len(frame))

        json_payloads = {
            "dashboard": self.results["summaries"]["dashboard"],
            "route_performance": _records(self.results["route_summary"]),
            "stop_performance": _records(self.results["stop_summary"]),
            "trip_delay_summary": _records(self.results["trip_summary"]),
            "propagation": {
                "summary": self.results["summaries"]["propagation"],
                "links": _records(self.results["propagation"]),
            },
            "connection_risk": {
                "summary": self.results["summaries"]["connectionRisk"],
                "connections": _records(self.results["connection_risk"]),
            },
            "passenger_impact": {
                "summary": self.results["summaries"]["passengerImpact"],
                "byRoute": _records(self.results["passenger_impact_route"]),
                "byStop": _records(self.results["passenger_impact_stop"]),
            },
            "sensitivity": {
                "interpretation": self.results["summaries"]["sensitivity"],
                "results": _records(self.results["sensitivity"]),
            },
            "validation": self.results["summaries"]["validation"],
            "worked_example": self.results["workedExample"],
            "configuration": self.config.to_dict(),
        }
        for name, payload in json_payloads.items():
            with (out / "json" / f"{name}.json").open("w", encoding="utf-8") as fh:
                json.dump(payload, fh, indent=2, default=_json_default)
            log.info("wrote json/%s.json", name)

        manifest = {
            "generatedAt": datetime.now().isoformat(timespec="seconds"),
            "engineVersion": "1.0.0",
            "csvTables": sorted(p.name for p in (out / "csv").glob("*.csv")),
            "jsonPayloads": sorted(p.name for p in (out / "json").glob("*.json")),
            "rowCounts": {k: int(len(v)) for k, v in csv_tables.items()
                          if v is not None},
            "configuration": self.config.to_dict(),
        }
        with (out / "manifest.json").open("w", encoding="utf-8") as fh:
            json.dump(manifest, fh, indent=2, default=_json_default)
        return out


def main() -> None:
    ap = argparse.ArgumentParser(description="Run the TransitPulse data pipeline")
    ap.add_argument("--datasets", default=None, help="datasets directory")
    ap.add_argument("--output", default=None, help="output directory")
    ap.add_argument("--config", default=None, help="path to config.json")
    ap.add_argument("--quiet", action="store_true")
    args = ap.parse_args()

    logging.basicConfig(
        level=logging.WARNING if args.quiet else logging.INFO,
        format="%(levelname)-7s %(name)s: %(message)s")

    cfg = EngineConfig.load(args.config)
    if args.datasets:
        cfg.datasets_dir = args.datasets
    if args.output:
        cfg.output_dir = args.output

    pipeline = Pipeline(cfg)
    results = pipeline.run()
    out = pipeline.export()

    d = results["summaries"]["dashboard"]
    print("\n=== TransitPulse pipeline complete ===")
    print(f"  service date          {d['serviceDate']}")
    print(f"  routes / stops / trips {d['totalRoutes']} / {d['totalStops']} / "
          f"{d['totalScheduledTrips']}")
    print(f"  on-time percentage    {d['onTimePercentage']}%")
    print(f"  average delay         {d['averageDelayMin']} min")
    print(f"  delayed trips         {d['delayedTrips']} "
          f"(recovered {d['recoveredTrips']}, {d['recoveryPercentage']}%)")
    print(f"  propagation links     {d['propagationLinks']} "
          f"({d['highConfidencePropagations']} high confidence)")
    print(f"  connections at risk   {d['connectionsAtRisk']} "
          f"(missed {d['missedConnections']})")
    print(f"  passengers affected   {d['passengersAffected']} (estimate)")
    print(f"  unobserved movements  {d['unobservedMovements']}")
    print(f"\n  outputs written to    {out}")


if __name__ == "__main__":
    main()
