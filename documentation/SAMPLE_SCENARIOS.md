# Sample scenarios

Guided walk-throughs against the shipped synthetic data (seed 42). Each shows
what to do, what you should see, and what it demonstrates.

Run the pipeline first:

```bash
cd data-engine
python generate_datasets.py --seed 42
python -m transitpulse_engine.pipeline
```

---

## Scenario 1 — The worked example

**Demonstrates:** the full calculation chain, checkable by hand.

1. Open **Delay Investigation**.
2. Click **Show worked example**.

You see five steps: delay at each stop (0, 5, 8 min), how the delay developed,
propagation to the next service, connection risk, and passenger impact.

Check step 3 yourself: the vehicle arrives 09:28, needs 5 minutes to turn
around, so cannot leave before 09:33. The next trip was scheduled for 09:25.
That is 8 minutes of unavoidable delay — and it was observed departing at
exactly 09:33.

Then check step 4: the connecting service leaves 09:32, the feeder arrived
09:28, so 4 minutes are available against 5 required. At risk, by one minute.

Via the API:

```bash
curl -s http://localhost:8080/api/analytics/worked-example | python -m json.tool
```

---

## Scenario 2 — A delay following one vehicle

**Demonstrates:** VEHICLE_CONTINUITY propagation at HIGH confidence.

```bash
curl -s "http://localhost:8080/api/analytics/propagation?mechanism=VEHICLE_CONTINUITY&confidence=HIGH&limit=3" \
  | python -m json.tool
```

Pick a `source_trip_id` from the result, then in the UI select its route and
that trip on **Delay Investigation**.

In the propagation section you see the mechanism, the arithmetic, and the
evidence naming the vehicle. Open **vehicle chain** to see every trip that
vehicle worked that day:

```bash
curl -s http://localhost:8080/api/trips/T0011/vehicle-chain | python -m json.tool
```

The delay is not a statistical association here. The same bus physically could
not be in two places, and the engine shows the constraint rather than asserting
a conclusion.

---

## Scenario 3 — When the engine disagrees with itself

**Demonstrates:** self-downgrading confidence.

```bash
curl -s "http://localhost:8080/api/analytics/propagation?mechanism=VEHICLE_CONTINUITY&confidence=LOW" \
  | python -m json.tool
```

These are vehicle-continuity links where the maths predicted a knock-on but
the next service departed on time anyway. Read the last line of the evidence:

> "Observed departure was on time, so the predicted knock-on did not
> materialise (schedule recovery or vehicle swap)."

This is worth dwelling on. The engine made a prediction from a physical
constraint, checked it against what happened, found it did not hold, and
lowered its own confidence rather than reporting a cascade that never occurred.

---

## Scenario 4 — Missing GPS data

**Demonstrates:** missing data never becoming zero delay.

```bash
curl -s http://localhost:8080/api/analytics/validation | python -m json.tool
```

You see 17 missing GPS records and a warning stating the strategy in use.

Find a trip with a gap and inspect it:

```bash
curl -s http://localhost:8080/api/trips/T0001/delays | python -m json.tool
```

A stop with no observation returns `"delayMin": null`,
`"delayClass": "MISSING"` and a note. In the UI, the delay chart shows a
genuine gap in the line (`connectNulls={false}`), and the table shows an em
dash — never a zero that would read as an on-time arrival.

Now try the other strategy:

1. **Settings → DATA_QUALITY → `missing_gps_strategy`** → `ESTIMATE` → Save.
2. Re-run the pipeline.
3. Check the same trip: the gap now has an interpolated value with
   `delay_source: "ESTIMATED"`.

Note that a missing *first* or *last* stop stays unavailable in either mode.
There is nothing to interpolate between, and extrapolating would be invention.

---

## Scenario 5 — A diverted trip

**Demonstrates:** diversions excluded from schedule comparison.

```bash
curl -s http://localhost:8080/api/incidents/diversions | python -m json.tool
```

Take the `routeId` from a diversion and investigate its trips. The skipped stop
shows `dataQualityFlag: "DIVERTED_SKIPPED"` with the note:

> "Stop not served: the vehicle was diverted. Comparing it against the planned
> schedule would be invalid."

The rest of the trip is analysed normally. Without this handling, a diverted
vehicle would record an enormous fictional delay at a stop it never attempted,
and that single trip would distort the route's whole day.

---

## Scenario 6 — Low-volume stops

**Demonstrates:** small samples flagged rather than hidden.

1. Open **Stops**, set **Low volume** to *Low volume only*.
2. Note the banner counting them, and the `LOW` confidence badges.

Compare two stops: a busy interchange with hundreds of boardings, and a quiet
stop with a handful. The quiet one may show a far worse delay frequency, from
two or three observations.

```bash
curl -s "http://localhost:8080/api/stops?lowVolumeOnly=true" | python -m json.tool | head -40
```

The data is kept and shown, because hiding it would be its own distortion. It
is labelled so nobody reallocates resources on the basis of three buses.

---

## Scenario 7 — Connection risk and the buffer assumption

**Demonstrates:** sensitivity analysis, and which numbers are robust.

Open **Connection Risk**. With the default 5-minute buffer you see roughly 14
connections at risk and 3 missed. Now read the sensitivity table:

| Buffer | At risk | Missed |
|--------|---------|--------|
| 3 min | 11 | 3 |
| 5 min | 14 | 3 |
| 8 min | 23 | 3 |
| 10 min | 26 | 3 |

At-risk counts more than double across the range. Missed connections never
move.

That difference is the whole point. "26 connections at risk" is a statement
about an assumption; "3 missed connections" is a statement about the
operation — the feeder arrived after its connection had left, and no view about
walking speed changes that.

Then change the assumption and watch it propagate:

1. **Settings → CONNECTION → `required_transfer_buffer_min`** → `8.0` → Save.
2. Re-run the pipeline.
3. Reload **Connection Risk**: the headline at-risk figure is now the 8-minute
   row.

---

## Scenario 8 — Structurally tight connections

**Demonstrates:** separating timetable problems from operational ones.

```bash
curl -s "http://localhost:8080/api/connections?structurallyTightOnly=true" | python -m json.tool
```

These connections are tight *even if every service runs exactly to time*:
their planned transfer window is already below the required buffer.

No amount of improved punctuality fixes them. They need a timetable change,
which is a different department and a different budget — and that is precisely
why the distinction is worth surfacing.

---

## Scenario 9 — Correlation versus causation

**Demonstrates:** how the engine refuses to overclaim.

```bash
curl -s "http://localhost:8080/api/analytics/propagation?mechanism=SHARED_STOP&limit=2" \
  | python -m json.tool
```

Read the evidence:

> "No vehicle continuity and no timetabled transfer links these trips: this is
> a CORRELATION at a shared location, not a demonstrated cause."

And the calculation:

```
correlation only: min(source_delay 20.0, affected_delay 8.0) = 8.0 min
```

Now compare with a VEHICLE_CONTINUITY link, where the calculation derives a
figure from a constraint. Same output table, very different epistemic status —
and the API, the UI and the Power BI pack all preserve the distinction.

On the **Analytics** page, the stacked chart of mechanism against confidence
shows the pattern at a glance: vehicle continuity is mostly HIGH, shared stop
is entirely LOW.

---

## Scenario 10 — Recording an incident and seeing it strengthen a claim

**Demonstrates:** human evidence raising confidence.

1. Open **Incident Management**.
2. Create one: type `TRAFFIC_CONGESTION`, a route with known delays, a window
   covering them, severity `HIGH`.
3. Re-run the pipeline.
4. Open **Analytics → propagation** and look for links on that route with an
   `incident_id`.

Connection links covered by an incident move from MEDIUM to HIGH, and the
evidence gains a line naming it. The engine cannot see *why* a vehicle was
late; an operator can, and recording it makes every downstream analysis more
defensible.

Try the validation too: set `isDiversion` to `Y` and leave both stop fields
empty. The API refuses, explaining that a diversion must identify its stop so
schedule comparison can be excluded for it.

---

## Scenario 11 — Ranking routes by harm, not by lateness

**Demonstrates:** why passenger impact is a different question from delay.

Open **Passenger Impact** and compare the ordering with **Routes** sorted by
average delay. They differ.

A route two minutes late with full buses costs more passenger-minutes than one
ten minutes late with empty ones. Ranking by delay alone sends resources to the
wrong place.

```bash
curl -s http://localhost:8080/api/analytics/passenger-impact | python -m json.tool | head -40
```

Every figure carries `is_estimate: true`, and the page carries a standing
caveat. The ranking is useful; the absolute numbers are modelled.

---

## Scenario 12 — Breaking the configuration on purpose

**Demonstrates:** validation that protects the analysis.

1. **Settings → DELAY → `on_time_threshold_min`** → `9` → Save.

Rejected:

> "Delay thresholds must be strictly increasing, but would become on_time=9.0,
> minor=5.0, moderate=15.0. Change rejected."

Each value is individually within bounds. The *combination* would make
classification meaningless, so it is a cross-field check.

```bash
curl -X PUT http://localhost:8080/api/config/required_transfer_buffer_min \
  -H "Content-Type: application/json" -d '{"configValue": "999"}'
# 400: must be at most 30

curl -X PUT http://localhost:8080/api/config/missing_gps_strategy \
  -H "Content-Type: application/json" -d '{"configValue": "ASSUME_ZERO"}'
# 400: must be EXCLUDE or ESTIMATE. Missing GPS events are never treated as zero delay.
```

---

## Scenario 13 — Running with no analytics at all

**Demonstrates:** honest degradation.

```bash
cd data-engine
mv output output_backup
cd ../backend && mvn spring-boot:run
```

The **Dashboard** still loads: route, stop and trip counts come from the
database. A banner appears with the exact command to generate the analytics,
and the navbar shows an *engine not run* badge.

Visit **Analytics**: a 503 is surfaced as a readable error carrying the same
command, rather than empty charts that would read as "no delays today".

```bash
mv output_backup output   # restore
```
