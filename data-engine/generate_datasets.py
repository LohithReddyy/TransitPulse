#!/usr/bin/env python3
"""
Synthetic dataset generator for TransitPulse.

Produces a relationally consistent transit network: routes, stops, the
ordered stop pattern of each route, vehicles, trips, scheduled movements,
actual movements (with deliberate delays, recoveries, missing GPS records
and diversions), passenger volumes, transfer connections and incidents.

Run:  python generate_datasets.py [--seed 42] [--out ../datasets]
"""
from __future__ import annotations

import argparse
import csv
from datetime import datetime, timedelta
from pathlib import Path

import numpy as np

SERVICE_DATE = "2026-03-16"          # a Monday
BASE = datetime.fromisoformat(f"{SERVICE_DATE}T05:00:00")

ROUTE_DEFS = [
    ("R01", "Harbour Line",        "BUS",   "Harbour Terminal",  "Civic Centre",      9),
    ("R02", "Civic Crosstown",     "BUS",   "Civic Centre",      "Elmgrove Depot",    8),
    ("R03", "Elmgrove Express",    "BUS",   "Elmgrove Depot",    "Northgate",         6),
    ("R04", "Northgate Loop",      "BUS",   "Northgate",         "Northgate",         7),
    ("R05", "Riverside Tram",      "TRAM",  "Riverside Quay",    "Civic Centre",      8),
    ("R06", "University Shuttle",  "BUS",   "Civic Centre",      "University Gate",   5),
    ("R07", "Airport Rapid",       "BUS",   "Civic Centre",      "Airport Terminal",  6),
    ("R08", "Southbank Metro",     "METRO", "Southbank",         "Civic Centre",      7),
    ("R09", "Industrial Feeder",   "BUS",   "Elmgrove Depot",    "Ironworks Park",    5),
    ("R10", "Hillside Connector",  "BUS",   "Northgate",         "Hillside Village",  5),
]

STOP_NAMES = [
    "Harbour Terminal", "Dockside Market", "Mariner Square", "Old Customs House",
    "Beacon Street", "Cathedral Steps", "Civic Centre", "Parliament Walk",
    "Lantern Row", "Foundry Lane", "Elmgrove Depot", "Elmgrove Park",
    "Willow Crescent", "Kestrel Avenue", "Northgate", "Northgate Retail",
    "Birch Hollow", "Juniper Court", "Riverside Quay", "Ferry Steps",
    "Millpond Bridge", "Weavers Yard", "Clocktower Plaza", "Granary Walk",
    "University Gate", "Science Quadrangle", "Library Crossing", "Halls of Residence",
    "Airport Terminal", "Air Cargo North", "Longfield Interchange", "Cedar Gardens",
    "Southbank", "Southbank Pier", "Tannery Road", "Glasshouse Street",
    "Ironworks Park", "Forge Gate", "Quarry Road", "Slate Hill",
    "Hillside Village", "Hillside Chapel", "Orchard Rise", "Bramble Way",
    "Copper Street", "Saltmarsh Halt", "Linden Avenue", "Printworks Corner",
    "Rookery Lane", "Abbey Fields", "Guildhall Steps", "Shipwright Quay",
    "Anchor Hill", "Tollgate Cross", "Meadowbank",
]

INCIDENT_TYPES = ["SIGNAL_FAILURE", "ROAD_WORKS", "TRAFFIC_CONGESTION",
                  "VEHICLE_BREAKDOWN", "DIVERSION", "WEATHER", "SPECIAL_EVENT"]


def hhmm(dt: datetime) -> str:
    return dt.strftime("%Y-%m-%d %H:%M:%S")


def write_csv(path: Path, header: list[str], rows: list[list]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", newline="", encoding="utf-8") as fh:
        w = csv.writer(fh)
        w.writerow(header)
        w.writerows(rows)
    print(f"  {path.name:<28} {len(rows):>5} rows")


def build(seed: int, out_dir: Path) -> dict:
    rng = np.random.default_rng(seed)

    # ---------------- stops ----------------
    stops, stop_ids = [], []
    for i, name in enumerate(STOP_NAMES, start=1):
        sid = f"S{i:03d}"
        stop_ids.append(sid)
        # Interchanges are the hub names that several routes share
        is_interchange = name in {"Civic Centre", "Elmgrove Depot", "Northgate",
                                  "Harbour Terminal", "Longfield Interchange",
                                  "Southbank", "Riverside Quay"}
        stops.append([
            sid, name, "INTERCHANGE" if is_interchange else "STANDARD",
            round(float(51.45 + rng.normal(0, 0.05)), 6),
            round(float(-0.12 + rng.normal(0, 0.07)), 6),
            "Y" if is_interchange else "N",
            1 if is_interchange else int(rng.integers(0, 2)),   # step_free
        ])
    name_to_id = {name: s[0] for s, name in zip(stops, STOP_NAMES)}

    # ---------------- routes + route_stops ----------------
    routes, route_stops, route_pattern = [], [], {}
    for rid, rname, mode, origin, dest, n_stops in ROUTE_DEFS:
        # Build a stop pattern anchored on the declared origin and destination
        pattern = [name_to_id[origin]]
        pool = [s for s in stop_ids if s not in pattern and s != name_to_id[dest]]
        middle = list(rng.choice(pool, size=max(0, n_stops - 2), replace=False))
        pattern += [str(m) for m in middle]
        pattern.append(name_to_id[dest])
        # a loop route legitimately ends where it started
        route_pattern[rid] = pattern
        routes.append([rid, rname, mode, origin, dest, len(pattern),
                       "ACTIVE", f"{rname} serving {len(pattern)} stops"])
        cum = 0.0
        for seq, sid in enumerate(pattern, start=1):
            if seq > 1:
                cum += round(float(rng.uniform(0.6, 2.4)), 2)
            route_stops.append([f"{rid}-{seq:02d}", rid, sid, seq,
                                round(cum, 2),
                                int(rng.integers(2, 6))])   # scheduled run time to this stop
    rs_runtime = {(r[1], r[3]): r[5] for r in route_stops}

    # ---------------- vehicles ----------------
    vehicles = []
    for i in range(1, 31):
        vid = f"V{i:03d}"
        vehicles.append([vid, f"TP-{1000 + i}",
                         ["BUS", "BUS", "BUS", "TRAM", "METRO"][i % 5],
                         int(rng.integers(40, 120)),
                         ["Harbour Depot", "Elmgrove Depot", "Northgate Depot"][i % 3],
                         "IN_SERVICE"])
    vehicle_ids = [v[0] for v in vehicles]

    # ---------------- trips, scheduled + actual movements ----------------
    trips, sched, actual = [], [], []
    trip_counter = 0
    # vehicle_blocks: each vehicle runs a chain of trips so propagation has
    # genuine vehicle continuity to follow.
    blocks: dict[str, datetime] = {v: BASE + timedelta(minutes=int(rng.integers(0, 50)))
                                   for v in vehicle_ids}
    vehicle_route_cycle = {v: [] for v in vehicle_ids}
    for idx, (rid, *_rest) in enumerate(ROUTE_DEFS):
        for v in vehicle_ids[idx * 3:(idx * 3) + 3]:
            vehicle_route_cycle[v].append(rid)

    delay_seeds: list[tuple] = []      # (trip_id, onset_seq, minutes, cause)
    for vehicle in vehicle_ids:
        rids = vehicle_route_cycle[vehicle] or ["R01"]
        clock = blocks[vehicle]
        inherited = 0.0                # delay this vehicle carries into its next leg
        for leg in range(4):           # 4 legs per vehicle -> 120 trips
            rid = rids[leg % len(rids)]
            pattern = route_pattern[rid]
            trip_counter += 1
            tid = f"T{trip_counter:04d}"
            direction = leg % 2
            dep = clock
            # Decide this trip's disruption profile up front
            roll = rng.random()
            if roll < 0.22:
                onset = int(rng.integers(1, max(2, len(pattern) - 1)))
                minutes = float(round(rng.uniform(4, 18), 1))
                cause = "UPSTREAM_CONGESTION"
                recovers = rng.random() < 0.45
            elif roll < 0.33:
                onset = 1
                minutes = float(round(rng.uniform(2, 6), 1))
                cause = "LATE_DEPARTURE"
                recovers = rng.random() < 0.6
            else:
                onset, minutes, cause, recovers = 10 ** 6, 0.0, "NONE", False
            # A vehicle that arrived late on its previous leg starts this one
            # late too, unless the scheduled turnaround absorbed the delay.
            if inherited > 0:
                onset = 1
                minutes = max(minutes, round(inherited, 1))
                cause = "VEHICLE_CONTINUITY" if cause == "NONE" else cause
                recovers = rng.random() < 0.5
            if minutes:
                delay_seeds.append((tid, onset, minutes, cause))

            trips.append([tid, rid, vehicle, SERVICE_DATE, hhmm(dep), direction,
                          "WEEKDAY", f"BLOCK-{vehicle}-{leg + 1}", "SCHEDULED"])

            running = dep
            carried = 0.0
            missing_marked = False
            for seq, sid in enumerate(pattern, start=1):
                if seq > 1:
                    running = running + timedelta(minutes=rs_runtime[(rid, seq)])
                sched_arr = running
                sched_dep = running + timedelta(minutes=1 if seq < len(pattern) else 0)
                sched.append([f"{tid}-{seq:02d}", tid, rid, sid, seq,
                              hhmm(sched_arr), hhmm(sched_dep),
                              1 if seq == 1 else 0,
                              1 if seq == len(pattern) else 0])

                # ----- build the actual movement -----
                if seq >= onset:
                    if carried == 0.0:
                        carried = minutes
                    elif recovers:
                        carried = max(0.0, carried - float(round(rng.uniform(0.8, 2.5), 1)))
                    else:
                        carried += float(round(rng.uniform(-0.4, 1.6), 1))
                        carried = max(0.0, carried)
                noise = float(round(rng.normal(0, 0.7), 1))
                obs_delay = round(carried + noise, 1)

                # Missing GPS: ~3.5% of movements have no actual record at all
                if rng.random() < 0.035 and seq not in (1,):
                    missing_marked = True
                    actual.append([f"A-{tid}-{seq:02d}", tid, rid, sid, seq,
                                   "", "", "", "MISSING_GPS", "N"])
                    continue

                act_arr = sched_arr + timedelta(minutes=obs_delay)
                dwell = 1 if seq < len(pattern) else 0
                act_dep = act_arr + timedelta(minutes=dwell)
                actual.append([f"A-{tid}-{seq:02d}", tid, rid, sid, seq,
                               hhmm(act_arr), hhmm(act_dep), round(obs_delay, 1),
                               "OK", "N"])
                running = sched_arr
            # vehicle turns around; the carried delay follows it into the next leg
            runtime_total = sum(rs_runtime[(rid, s)] for s in range(2, len(pattern) + 1))
            turnaround = 6.0 + float(rng.integers(0, 10))
            clock = dep + timedelta(minutes=runtime_total + turnaround)
            # Whatever delay is left at the end of the leg, less the slack in
            # the turnaround, is handed to the vehicle's next trip.
            inherited = max(0.0, round(carried - turnaround * 0.4, 1))
            blocks[vehicle] = clock
            _ = missing_marked

    # ---------------- diversions ----------------
    # A handful of trips are diverted: they skip a planned stop and serve a
    # replacement. Diverted actual records are flagged so the comparison
    # layer never lines up a planned stop against a diverted one.
    all_trip_ids = [t[0] for t in trips]
    diverted_trips = [all_trip_ids[i] for i in
                      rng.choice(len(all_trip_ids), size=6, replace=False)]
    diversions = []
    for i, tid in enumerate(diverted_trips, start=1):
        trip_row = next(t for t in trips if t[0] == tid)
        rid = trip_row[1]
        pattern = route_pattern[rid]
        if len(pattern) < 4:
            continue
        skipped_seq = int(rng.integers(2, len(pattern) - 1))
        skipped_stop = pattern[skipped_seq - 1]
        replacement = next(s for s in stop_ids if s not in pattern)
        for row in actual:
            if row[1] == tid and row[4] == skipped_seq:
                row[8] = "DIVERTED_SKIPPED"
                row[9] = "Y"
                row[5] = row[6] = row[7] = ""
        diversions.append([f"DIV{i:03d}", tid, rid, skipped_stop, replacement,
                           skipped_seq, f"Stop {skipped_stop} not served; "
                           f"{replacement} served instead", SERVICE_DATE])

    # ---------------- passenger volumes ----------------
    pax = []
    for rid, *_r in ROUTE_DEFS:
        for seq, sid in enumerate(route_pattern[rid], start=1):
            for band in ("EARLY", "AM_PEAK", "MIDDAY", "PM_PEAK", "EVENING"):
                mult = {"EARLY": 0.35, "AM_PEAK": 1.0, "MIDDAY": 0.55,
                        "PM_PEAK": 0.95, "EVENING": 0.3}[band]
                base_board = float(rng.gamma(3.0, 9.0)) * mult
                boardings = int(max(0, round(base_board)))
                alightings = int(max(0, round(base_board * float(rng.uniform(0.6, 1.3)))))
                load = int(max(boardings, round(base_board * float(rng.uniform(1.4, 3.0)))))
                pax.append([f"PV-{rid}-{sid}-{band}", rid, sid, seq, band,
                            boardings, alightings, load,
                            round(float(rng.uniform(1.5, 9.0)), 1),
                            "Y" if boardings < 20 else "N"])

    # ---------------- connections (transfer opportunities) ----------------
    connections = []
    interchange_ids = [s[0] for s in stops if s[5] == "Y"]
    cid = 0
    sched_by_trip = {}
    for row in sched:
        sched_by_trip.setdefault(row[1], []).append(row)
    trip_route = {t[0]: t[1] for t in trips}

    for sid in interchange_ids:
        feeders = [r for r in sched if r[3] == sid and r[8] == 0]
        for feeder in feeders[:14]:
            f_trip, f_route, f_arr = feeder[1], feeder[2], datetime.fromisoformat(feeder[5])
            cands = [r for r in sched
                     if r[3] == sid and trip_route[r[1]] != f_route
                     and 0 <= (datetime.fromisoformat(r[6]) - f_arr).total_seconds() / 60 <= 30]
            for cand in cands[:2]:
                cid += 1
                planned = round((datetime.fromisoformat(cand[6]) - f_arr).total_seconds() / 60, 1)
                connections.append([
                    f"C{cid:04d}", sid, f_trip, f_route, cand[1], trip_route[cand[1]],
                    feeder[5], cand[6], planned, 5.0,
                    int(max(1, round(float(rng.gamma(2.0, 6.0)))))])

    # ---------------- incidents ----------------
    incidents = []
    for i in range(1, 16):
        rid = ROUTE_DEFS[int(rng.integers(0, len(ROUTE_DEFS)))][0]
        pattern = route_pattern[rid]
        sid = pattern[int(rng.integers(0, len(pattern)))]
        start = BASE + timedelta(minutes=int(rng.integers(60, 600)))
        dur = int(rng.integers(15, 120))
        itype = INCIDENT_TYPES[int(rng.integers(0, len(INCIDENT_TYPES)))]
        incidents.append([
            f"INC{i:03d}", itype, rid, sid, hhmm(start), hhmm(start + timedelta(minutes=dur)),
            ["LOW", "MEDIUM", "HIGH", "CRITICAL"][int(rng.integers(0, 4))],
            f"{itype.replace('_', ' ').title()} affecting route {rid} near stop {sid}.",
            "Y" if itype == "DIVERSION" else "N", "CLOSED", "operations"])
    for i, div in enumerate(diversions, start=len(incidents) + 1):
        incidents.append([
            f"INC{i:03d}", "DIVERSION", div[2], div[3],
            hhmm(BASE + timedelta(minutes=int(rng.integers(60, 600)))),
            hhmm(BASE + timedelta(minutes=int(rng.integers(600, 700)))),
            "HIGH", div[6], "Y", "CLOSED", "operations"])

    # ---------------- write everything ----------------
    print(f"\nWriting datasets to {out_dir}")
    write_csv(out_dir / "routes.csv",
              ["route_id", "route_name", "mode", "origin_name", "destination_name",
               "stop_count", "status", "description"], routes)
    write_csv(out_dir / "stops.csv",
              ["stop_id", "stop_name", "stop_type", "latitude", "longitude",
               "is_interchange", "step_free"], stops)
    write_csv(out_dir / "route_stops.csv",
              ["route_stop_id", "route_id", "stop_id", "stop_sequence",
               "distance_from_origin_km", "scheduled_runtime_min"], route_stops)
    write_csv(out_dir / "vehicles.csv",
              ["vehicle_id", "fleet_number", "vehicle_type", "capacity",
               "depot", "status"], vehicles)
    write_csv(out_dir / "trips.csv",
              ["trip_id", "route_id", "vehicle_id", "service_date",
               "scheduled_start_time", "direction", "service_pattern",
               "block_id", "status"], trips)
    write_csv(out_dir / "scheduled_movements.csv",
              ["scheduled_movement_id", "trip_id", "route_id", "stop_id",
               "stop_sequence", "scheduled_arrival", "scheduled_departure",
               "is_origin", "is_destination"], sched)
    write_csv(out_dir / "actual_movements.csv",
              ["actual_movement_id", "trip_id", "route_id", "stop_id",
               "stop_sequence", "actual_arrival", "actual_departure",
               "observed_delay_min", "data_quality_flag", "is_diverted"], actual)
    write_csv(out_dir / "passenger_volumes.csv",
              ["passenger_volume_id", "route_id", "stop_id", "stop_sequence",
               "time_band", "boardings", "alightings", "average_load",
               "average_wait_min", "is_low_volume"], pax)
    write_csv(out_dir / "connections.csv",
              ["connection_id", "interchange_stop_id", "feeder_trip_id",
               "feeder_route_id", "connecting_trip_id", "connecting_route_id",
               "feeder_scheduled_arrival", "connecting_scheduled_departure",
               "planned_transfer_min", "required_buffer_min",
               "typical_transferring_passengers"], connections)
    write_csv(out_dir / "incidents.csv",
              ["incident_id", "incident_type", "route_id", "stop_id",
               "start_time", "end_time", "severity", "description",
               "is_diversion", "status", "reported_by"], incidents)
    write_csv(out_dir / "diversions.csv",
              ["diversion_id", "trip_id", "route_id", "skipped_stop_id",
               "replacement_stop_id", "skipped_stop_sequence", "notes",
               "service_date"], diversions)

    return {"routes": len(routes), "stops": len(stops), "trips": len(trips),
            "scheduled_movements": len(sched), "actual_movements": len(actual),
            "passenger_volumes": len(pax), "connections": len(connections),
            "incidents": len(incidents), "diversions": len(diversions),
            "missing_gps": sum(1 for a in actual if a[8] == "MISSING_GPS"),
            "delay_seeds": len(delay_seeds)}


def main() -> None:
    ap = argparse.ArgumentParser(description="Generate TransitPulse datasets")
    ap.add_argument("--seed", type=int, default=42)
    ap.add_argument("--out", default=str(Path(__file__).resolve().parents[1] / "datasets"))
    args = ap.parse_args()
    summary = build(args.seed, Path(args.out))
    print("\nSummary:")
    for k, v in summary.items():
        print(f"  {k:<22} {v}")


if __name__ == "__main__":
    main()
