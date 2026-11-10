"""
Delay propagation engine.

Downstream lateness is not assumed to be *caused* by upstream lateness.
Every propagation relationship this engine emits carries:

  * a mechanism  - how the delay could travel (vehicle continuity, transfer
                   connection, or shared-stop congestion),
  * evidence     - the specific facts that support it,
  * a confidence - HIGH / MEDIUM / LOW, derived from the strength of that
                   evidence rather than from the size of the delay.

Mechanisms, strongest first:

  1. VEHICLE_CONTINUITY - the same physical vehicle operates the next trip.
     A late arrival minus the scheduled turnaround is a hard, causal
     constraint on the next departure. Highest confidence.
  2. CONNECTION        - a timetabled transfer at an interchange: the feeder
     trip is late, the connecting service either waits or passengers miss it.
  3. SHARED_STOP       - two services share a stop inside the propagation
     window. Correlation only; lowest confidence, and never asserted as
     causal without a corroborating incident annotation.

Double counting is prevented by keying every emitted relationship on
(source_trip, affected_trip, mechanism) and keeping only the highest
confidence relationship per (source_trip, affected_trip) pair.
"""
from __future__ import annotations

from dataclasses import dataclass, asdict
from typing import Dict, List, Optional

import pandas as pd

from .config import EngineConfig

CONFIDENCE_RANK = {"LOW": 1, "MEDIUM": 2, "HIGH": 3}


@dataclass
class PropagationLink:
    """One traceable upstream -> downstream delay relationship."""
    source_trip_id: str
    source_route_id: str
    source_stop_id: str
    source_delay_min: float
    affected_trip_id: str
    affected_route_id: str
    affected_stop_id: str
    affected_delay_min: float
    propagated_delay_min: float
    mechanism: str
    confidence: str
    lag_min: float
    vehicle_id: Optional[str]
    evidence: List[str]
    calculation: str
    incident_id: Optional[str] = None

    def to_dict(self) -> dict:
        return asdict(self)


class PropagationEngine:
    """Builds the propagation graph for a service day."""

    def __init__(self, config: EngineConfig | None = None):
        self.config = config or EngineConfig()

    # ------------------------------------------------------------------ #

    def run(self, delays: pd.DataFrame, trip_summary: pd.DataFrame,
            connections: pd.DataFrame,
            incidents: pd.DataFrame | None = None) -> pd.DataFrame:
        """Return every propagation link found, de-duplicated."""
        cfg = self.config
        delayed_trips = trip_summary[
            trip_summary["max_delay_min"].fillna(0) >= cfg.on_time_threshold_min]

        links: List[PropagationLink] = []
        links += self._vehicle_continuity(delays, delayed_trips, incidents)
        links += self._connection_links(delays, delayed_trips, connections, incidents)
        links += self._shared_stop_links(delays, delayed_trips, incidents)

        if not links:
            return pd.DataFrame(columns=[f for f in PropagationLink.__annotations__])

        df = pd.DataFrame([l.to_dict() for l in links])
        return self._deduplicate(df)

    # ------------------------------------------------------------------ #

    def _deduplicate(self, df: pd.DataFrame) -> pd.DataFrame:
        """
        Keep one relationship per (source_trip, affected_trip): the one with
        the strongest mechanism/confidence. This is what stops the same
        affected service being counted several times in impact totals.
        """
        df = df.copy()
        df["_rank"] = df["confidence"].map(CONFIDENCE_RANK).fillna(0)
        df = (df.sort_values(["_rank", "propagated_delay_min"], ascending=False)
                .drop_duplicates(subset=["source_trip_id", "affected_trip_id"],
                                 keep="first")
                .drop(columns="_rank")
                .sort_values(["source_trip_id", "affected_trip_id"])
                .reset_index(drop=True))
        return df

    @staticmethod
    def _incident_for(incidents: pd.DataFrame | None, route_id: str,
                      at_time) -> Optional[str]:
        """Find an operator-annotated incident covering this route and time."""
        if incidents is None or incidents.empty or pd.isna(at_time):
            return None
        m = incidents[(incidents["route_id"] == route_id)
                      & (incidents["start_time"] <= at_time)
                      & (incidents["end_time"] >= at_time)]
        return None if m.empty else str(m["incident_id"].iloc[0])

    # ------------------------------------------------------------------ #

    def _vehicle_continuity(self, delays: pd.DataFrame,
                            delayed_trips: pd.DataFrame,
                            incidents) -> List[PropagationLink]:
        """
        The same vehicle's next trip cannot leave before it arrives plus the
        scheduled turnaround. This is a physical constraint, so a knock-on
        here is evidence of causation, not merely correlation.
        """
        cfg = self.config
        out: List[PropagationLink] = []

        # last observed arrival per trip, and first scheduled departure per trip
        last_obs = (delays[delays["delay_min"].notna()]
                    .sort_values(["trip_id", "stop_sequence"])
                    .groupby("trip_id").tail(1)
                    .set_index("trip_id"))
        first_sched = (delays.sort_values(["trip_id", "stop_sequence"])
                       .groupby("trip_id").head(1).set_index("trip_id"))

        # order each vehicle's trips by scheduled start
        order = (first_sched.reset_index()[["trip_id", "vehicle_id",
                                            "scheduled_arrival", "route_id"]]
                 .dropna(subset=["vehicle_id"])
                 .sort_values(["vehicle_id", "scheduled_arrival"]))

        next_trip: Dict[str, tuple] = {}
        for vehicle, grp in order.groupby("vehicle_id", sort=False):
            rows = grp.to_dict("records")
            for cur, nxt in zip(rows, rows[1:]):
                next_trip[cur["trip_id"]] = (nxt["trip_id"], nxt["route_id"],
                                             nxt["scheduled_arrival"], vehicle)

        delayed_ids = set(delayed_trips["trip_id"])
        for trip_id in delayed_ids:
            if trip_id not in next_trip or trip_id not in last_obs.index:
                continue
            nxt_id, nxt_route, nxt_sched_dep, vehicle = next_trip[trip_id]
            src = last_obs.loc[trip_id]
            src_delay = float(src["delay_min"])
            if src_delay < cfg.on_time_threshold_min:
                continue

            actual_arrival = src["actual_arrival"]
            earliest_dep = actual_arrival + pd.Timedelta(minutes=cfg.vehicle_turnaround_min)
            forced = (earliest_dep - nxt_sched_dep).total_seconds() / 60.0
            lag = (nxt_sched_dep - actual_arrival).total_seconds() / 60.0

            if forced < cfg.min_propagated_delay_min:
                continue                      # turnaround absorbed the delay
            # The window bounds how far FORWARD we look. A negative lag means
            # the next trip was already due before the vehicle arrived, which is
            # the most constrained case of all, so it is never excluded.
            if lag > cfg.propagation_window_min:
                continue

            # what the next trip actually did, for comparison
            nxt_obs = delays[(delays["trip_id"] == nxt_id)
                             & delays["delay_min"].notna()]
            observed_next = (round(float(nxt_obs.sort_values("stop_sequence")
                                         ["delay_min"].iloc[0]), 2)
                             if not nxt_obs.empty else float("nan"))

            incident_id = self._incident_for(incidents, src["route_id"], actual_arrival)
            evidence = [
                f"Same vehicle {vehicle} operates trip {trip_id} then trip {nxt_id}.",
                f"Trip {trip_id} arrived {src_delay:.1f} min late at stop "
                f"{src['stop_id']} ({actual_arrival:%H:%M}).",
                f"Scheduled turnaround is {cfg.vehicle_turnaround_min:.0f} min, so the "
                f"vehicle cannot depart before {earliest_dep:%H:%M}.",
                f"Trip {nxt_id} was scheduled to depart at {nxt_sched_dep:%H:%M}.",
            ]
            confidence = "HIGH"
            if incident_id:
                evidence.append(
                    f"Operator incident {incident_id} annotated on route "
                    f"{src['route_id']} at this time corroborates the cause.")
            if pd.notna(observed_next):
                evidence.append(
                    f"Trip {nxt_id} was observed departing {observed_next:.1f} min late "
                    f"(predicted at least {forced:.1f} min).")
                if observed_next < cfg.min_propagated_delay_min:
                    # the next trip left on time: the constraint did not bind
                    confidence = "LOW"
                    evidence.append(
                        "Observed departure was on time, so the predicted knock-on "
                        "did not materialise (schedule recovery or vehicle swap).")

            out.append(PropagationLink(
                source_trip_id=trip_id,
                source_route_id=str(src["route_id"]),
                source_stop_id=str(src["stop_id"]),
                source_delay_min=round(src_delay, 2),
                affected_trip_id=nxt_id,
                affected_route_id=str(nxt_route),
                affected_stop_id=str(src["stop_id"]),
                affected_delay_min=observed_next,
                propagated_delay_min=round(forced, 2),
                mechanism="VEHICLE_CONTINUITY",
                confidence=confidence,
                lag_min=round(lag, 2),
                vehicle_id=str(vehicle),
                evidence=evidence,
                calculation=(
                    f"forced_delay = (actual_arrival {actual_arrival:%H:%M} + turnaround "
                    f"{cfg.vehicle_turnaround_min:.0f}min) - scheduled_departure "
                    f"{nxt_sched_dep:%H:%M} = {forced:.1f} min"),
                incident_id=incident_id))
        return out

    # ------------------------------------------------------------------ #

    def _connection_links(self, delays: pd.DataFrame, delayed_trips: pd.DataFrame,
                          connections: pd.DataFrame,
                          incidents) -> List[PropagationLink]:
        """A late feeder squeezes the transfer window of a connecting service."""
        cfg = self.config
        out: List[PropagationLink] = []
        if connections is None or connections.empty:
            return out

        delayed_ids = set(delayed_trips["trip_id"])
        obs = delays[delays["delay_min"].notna()]
        # A loop route can serve the same stop twice on one trip, so
        # (trip_id, stop_id) is not unique. Keep the first visit, which is the
        # one a timetabled transfer at that interchange refers to.
        first_visit = (obs.sort_values(["trip_id", "stop_sequence"])
                          .drop_duplicates(subset=["trip_id", "stop_id"], keep="first"))
        arrivals = {(r.trip_id, r.stop_id): (float(r.delay_min), r.actual_arrival)
                    for r in first_visit.itertuples(index=False)}

        for _, c in connections.iterrows():
            feeder, conn = c["feeder_trip_id"], c["connecting_trip_id"]
            if feeder not in delayed_ids:
                continue
            key = (feeder, c["interchange_stop_id"])
            if key not in arrivals:
                continue                      # feeder arrival not observed here
            feeder_delay, actual_arr = arrivals[key]
            if feeder_delay < cfg.on_time_threshold_min:
                continue

            conn_dep = c["connecting_scheduled_departure"]
            available = (conn_dep - actual_arr).total_seconds() / 60.0
            required = float(c.get("required_buffer_min")
                             or cfg.required_transfer_buffer_min)
            shortfall = required - available
            # Unlike a vehicle knock-on, ANY positive shortfall against the
            # required buffer is a real connection risk, so this is gated on
            # zero rather than on min_propagated_delay_min.
            if shortfall <= 0:
                continue
            if available > cfg.propagation_window_min:
                continue

            conn_obs = delays[(delays["trip_id"] == conn) & delays["delay_min"].notna()]
            observed = (round(float(conn_obs.sort_values("stop_sequence")["delay_min"].iloc[0]), 2)
                        if not conn_obs.empty else float("nan"))
            incident_id = self._incident_for(incidents, c["feeder_route_id"], actual_arr)

            evidence = [
                f"Timetabled transfer at interchange {c['interchange_stop_id']} "
                f"from trip {feeder} (route {c['feeder_route_id']}) to trip {conn} "
                f"(route {c['connecting_route_id']}).",
                f"Feeder arrived {feeder_delay:.1f} min late at {actual_arr:%H:%M}.",
                f"Connecting service departs {conn_dep:%H:%M}; available transfer "
                f"time {available:.1f} min against a required buffer of {required:.0f} min.",
            ]
            if incident_id:
                evidence.append(f"Corroborated by incident {incident_id}.")

            out.append(PropagationLink(
                source_trip_id=str(feeder),
                source_route_id=str(c["feeder_route_id"]),
                source_stop_id=str(c["interchange_stop_id"]),
                source_delay_min=round(feeder_delay, 2),
                affected_trip_id=str(conn),
                affected_route_id=str(c["connecting_route_id"]),
                affected_stop_id=str(c["interchange_stop_id"]),
                affected_delay_min=observed,
                propagated_delay_min=round(shortfall, 2),
                mechanism="CONNECTION",
                confidence="MEDIUM" if incident_id is None else "HIGH",
                lag_min=round(available, 2),
                vehicle_id=None,
                evidence=evidence,
                calculation=(
                    f"shortfall = required_buffer {required:.0f}min - available_transfer "
                    f"({conn_dep:%H:%M} - {actual_arr:%H:%M} = {available:.1f}min) "
                    f"= {shortfall:.1f} min"),
                incident_id=incident_id))
        return out

    # ------------------------------------------------------------------ #

    def _shared_stop_links(self, delays: pd.DataFrame, delayed_trips: pd.DataFrame,
                           incidents) -> List[PropagationLink]:
        """
        Correlation at a shared stop. Emitted as LOW confidence and labelled
        explicitly as correlation unless an incident annotation corroborates
        it, in which case it rises to MEDIUM.
        """
        cfg = self.config
        out: List[PropagationLink] = []
        obs = delays[delays["delay_min"].notna()].copy()
        severe = set(delayed_trips[
            delayed_trips["max_delay_min"].fillna(0)
            >= cfg.moderate_delay_threshold_min]["trip_id"])
        if not severe:
            return out

        for stop_id, grp in obs.groupby("stop_id", sort=False):
            grp = grp.sort_values("actual_arrival")
            sources = grp[grp["trip_id"].isin(severe)]
            for _, src in sources.iterrows():
                window_end = src["actual_arrival"] + pd.Timedelta(
                    minutes=cfg.propagation_window_min)
                later = grp[(grp["actual_arrival"] > src["actual_arrival"])
                            & (grp["actual_arrival"] <= window_end)
                            & (grp["route_id"] != src["route_id"])
                            & (grp["delay_min"] >= cfg.minor_delay_threshold_min)]
                for _, aff in later.iterrows():
                    lag = (aff["actual_arrival"] - src["actual_arrival"]).total_seconds() / 60.0
                    incident_id = self._incident_for(incidents, str(src["route_id"]),
                                                     src["actual_arrival"])
                    evidence = [
                        f"Trip {src['trip_id']} (route {src['route_id']}) was "
                        f"{src['delay_min']:.1f} min late at shared stop {stop_id}.",
                        f"Trip {aff['trip_id']} (route {aff['route_id']}) passed the same "
                        f"stop {lag:.0f} min later, {aff['delay_min']:.1f} min late.",
                        "No vehicle continuity and no timetabled transfer links these "
                        "trips: this is a CORRELATION at a shared location, not a "
                        "demonstrated cause.",
                    ]
                    confidence = "LOW"
                    if incident_id:
                        confidence = "MEDIUM"
                        evidence.append(
                            f"Operator incident {incident_id} covers this stop and time "
                            "window, which supports a shared external cause.")
                    out.append(PropagationLink(
                        source_trip_id=str(src["trip_id"]),
                        source_route_id=str(src["route_id"]),
                        source_stop_id=str(stop_id),
                        source_delay_min=round(float(src["delay_min"]), 2),
                        affected_trip_id=str(aff["trip_id"]),
                        affected_route_id=str(aff["route_id"]),
                        affected_stop_id=str(stop_id),
                        affected_delay_min=round(float(aff["delay_min"]), 2),
                        propagated_delay_min=round(min(float(src["delay_min"]),
                                                       float(aff["delay_min"])), 2),
                        mechanism="SHARED_STOP",
                        confidence=confidence,
                        lag_min=round(lag, 2),
                        vehicle_id=None,
                        evidence=evidence,
                        calculation=(
                            "correlation only: min(source_delay "
                            f"{src['delay_min']:.1f}, affected_delay "
                            f"{aff['delay_min']:.1f}) = "
                            f"{min(float(src['delay_min']), float(aff['delay_min'])):.1f} min"),
                        incident_id=incident_id))
        return out

    # ------------------------------------------------------------------ #

    def summary(self, links: pd.DataFrame) -> dict:
        """Headline propagation numbers for the dashboard."""
        if links.empty:
            return {"totalLinks": 0, "byMechanism": {}, "byConfidence": {},
                    "affectedTripsUnique": 0, "avgPropagatedDelayMin": 0.0,
                    "maxPropagatedDelayMin": 0.0}
        return {
            "totalLinks": int(len(links)),
            "byMechanism": links["mechanism"].value_counts().to_dict(),
            "byConfidence": links["confidence"].value_counts().to_dict(),
            "affectedTripsUnique": int(links["affected_trip_id"].nunique()),
            "avgPropagatedDelayMin": round(float(links["propagated_delay_min"].mean()), 2),
            "maxPropagatedDelayMin": round(float(links["propagated_delay_min"].max()), 2),
        }
