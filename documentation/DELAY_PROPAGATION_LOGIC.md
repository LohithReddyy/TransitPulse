# Delay propagation logic

This document explains exactly how TransitPulse decides that one service's
delay affected another, why it grades those claims rather than asserting them,
and how to check any number it produces by hand.

---

## 1. The underlying problem

Observing that two services ran late within the same hour tells you very
little. They might be causally linked, they might share an external cause, or
they might be unrelated. A system that treats every temporal coincidence as
causation will confidently produce a network-wide cascade from what was
actually a rainy Tuesday.

TransitPulse therefore does not infer causation from timing alone. It looks for
a **mechanism** — a specific reason why one service's lateness could reach
another — and reports how strong the evidence for that mechanism is.

---

## 2. Delay calculation

Before anything propagates, delay has to be measured.

```
delay_minutes = actual_arrival - scheduled_arrival
```

Computed at **every stop of every trip**, and only where an arrival was
actually observed.

### Classification

Thresholds are configurable; the defaults are:

| Class | Condition |
|-------|-----------|
| `EARLY` | delay ≤ −2 min |
| `ON_TIME` | −2 < delay < 2 min |
| `MINOR` | 2 ≤ delay < 5 min |
| `MODERATE` | 5 ≤ delay < 15 min |
| `SEVERE` | delay ≥ 15 min |
| `MISSING` | no usable observation |

The thresholds must stay strictly increasing. The configuration layer rejects
any update that would invert them, because a classifier with
`on_time > minor` silently mislabels everything.

### `MISSING` is a class, not a value

A stop with no GPS arrival, or one skipped by a diversion, produces
`delay_min = null` and `delay_class = MISSING`. It is excluded from every
average.

This matters more than it first appears. If missing events were recorded as
zero delay, performance would improve precisely where telemetry is worst — so
the routes with the poorest data would look like the best performers, and
investment would flow to the wrong places.

---

## 3. The three mechanisms

### 3.1 VEHICLE_CONTINUITY — confidence HIGH

The same physical vehicle operates the next trip. A vehicle cannot depart
before it has arrived and completed its turnaround, so this is a *physical
constraint*, not a statistical association.

```
earliest_possible_departure = actual_arrival + scheduled_turnaround
forced_delay                = earliest_possible_departure - scheduled_departure
```

A link is emitted when `forced_delay ≥ min_propagated_delay_min` (default 2)
and the next departure falls inside the propagation window.

**Evidence recorded:** the vehicle id, both trip ids, the observed arrival, the
turnaround allowance, the next trip's scheduled departure, and what that trip
*actually* did.

**Self-correction:** if the next trip was observed departing on time anyway —
the schedule had slack, or the vehicle was swapped — the engine **downgrades
its own confidence to LOW** and says so in the evidence:

> "Observed departure was on time, so the predicted knock-on did not
> materialise (schedule recovery or vehicle swap)."

A system that only ever confirmed its own predictions would not be worth
trusting.

### 3.2 CONNECTION — confidence MEDIUM, or HIGH with corroboration

A timetabled transfer at an interchange. A late feeder eats into the window
passengers need to change services.

```
available_transfer_time = connecting_departure - feeder_actual_arrival
shortfall               = required_buffer - available_transfer_time
```

A link is emitted whenever `shortfall > 0`. Note this is gated on zero, not on
`min_propagated_delay_min`: unlike a vehicle knock-on, *any* positive shortfall
against the buffer is a real risk to a passenger trying to make that
connection. (An earlier version reused the knock-on threshold here and
discarded genuine one-minute shortfalls; a test now pins the correct
behaviour.)

Confidence is MEDIUM by default. It rises to HIGH when an operator incident
covers the route and time, because that is independent evidence of a shared
cause.

### 3.3 SHARED_STOP — confidence LOW, labelled correlation

Two services on *different* routes, with no vehicle in common and no
timetabled transfer, were both late at the same stop inside the window.

This is the weakest signal, and the engine says so explicitly in the evidence
attached to every such link:

> "No vehicle continuity and no timetabled transfer links these trips: this is
> a CORRELATION at a shared location, not a demonstrated cause."

The recorded calculation is likewise honest about what it is:

```
correlation only: min(source_delay 20.0, affected_delay 8.0) = 8.0 min
```

It rises to MEDIUM only when an operator incident covers that stop and window,
which supports a shared external cause — still not proof that one service
delayed the other.

These links are kept rather than discarded because they are genuinely useful
for spotting congestion hotspots. They are simply never presented as
causation; the UI, the API response and the Power BI pack all carry the
distinction.

---

## 4. Preventing double counting

Without care, one delayed trip could be counted several times against the same
downstream service — once per mechanism, or once per shared stop.

Every relationship is keyed on `(source_trip, affected_trip, mechanism)`, and
the engine then keeps **one relationship per `(source_trip, affected_trip)`
pair**: the one with the strongest confidence, breaking ties on the larger
propagated delay. The database enforces the same rule with a unique constraint
on `(source_trip_id, affected_trip_id)`, so the invariant survives loading.

A test asserts no duplicate pair can appear in the output.

---

## 5. The propagation window

`propagation_window_min` (default 90) bounds how far **forward** the engine
looks. A service three hours later is not plausibly affected by this morning's
delay through any of these mechanisms.

A *negative* lag — the next trip was scheduled to depart before the vehicle
even arrived — is never excluded. That is the most constrained case there is,
and excluding it would drop exactly the links that matter most.

---

## 6. Worked example

The full chain, with numbers you can verify by hand. This is served live at
`GET /api/analytics/worked-example` and rendered on the Delay Investigation
page.

### Scenario

**Route A** (one vehicle, three stops):

| Stop | Scheduled | Actual |
|------|-----------|--------|
| A | 09:00 | 09:00 |
| B | 09:10 | 09:15 |
| C | 09:20 | 09:28 |

The vehicle then works **Route B**, scheduled to depart 09:25, with a
5-minute turnaround. A **connecting service** at Stop C departs 09:32;
passengers need 5 minutes to transfer. 48 passengers are on board; 12 intend
to transfer at Stop C.

### Step 1 — Delay at each stop

| Stop | Calculation | Delay | Class |
|------|-------------|-------|-------|
| A | 09:00 − 09:00 | 0 min | ON_TIME |
| B | 09:15 − 09:10 | 5 min | MODERATE |
| C | 09:28 − 09:20 | 8 min | MODERATE |

### Step 2 — How the delay developed

| Segment | Before | After | Change | Reading |
|---------|--------|-------|--------|---------|
| A → B | 0 | 5 | +5 | lost time on this segment |
| B → C | 5 | 8 | +3 | lost further time; no recovery |

### Step 3 — Propagation to the next service

| # | What | Calculation |
|---|------|-------------|
| 1 | Route A arrived late at its final stop | `09:28 − 09:20 = 8 min late` |
| 2 | Add the scheduled turnaround | `09:28 + 5 min = 09:33 earliest departure` |
| 3 | Compare with Route B's schedule | `09:33 − 09:25 = 8 min forced delay` |
| 4 | Check against observation | `09:33 − 09:25 = 8 min observed — matches` |

**Mechanism** VEHICLE_CONTINUITY · **Confidence** HIGH

Why this is treated as causal rather than coincidental: the prediction was
derived from a physical constraint *before* looking at Route B's actual
departure, and the observation then matched it. A delay on an unrelated route
at the same stop would only ever have been reported as correlation.

### Step 4 — Connection risk

| # | What | Calculation |
|---|------|-------------|
| 1 | Available transfer time | `09:32 − 09:28 = 4 min` |
| 2 | Compare with the buffer | `4 min < 5 min required → AT RISK (shortfall 1 min)` |

Status **AT_RISK**, not **MISSED**: the connecting service has not left yet.
Whether passengers actually make it depends on whether it is held — which the
engine reports separately as `HELD_BY_CONNECTING_SERVICE` when the connecting
service's own observed departure was late enough to absorb the shortfall.

With a 3-minute buffer the same connection is **SAFE**. The status is a
function of an assumption, which is why §8 exists.

### Step 5 — Passenger impact (estimates)

| # | What | Calculation |
|---|------|-------------|
| 1 | Passenger-minutes lost to the delay | `8 min × 48 passengers = 384 passenger-minutes` |
| 2 | Passengers facing the at-risk connection | `12 passengers, 1 min short` |
| 3 | Added wait if the connection is missed | `12 × 15 min = 180 passenger-minutes` |

**Total estimated impact: 564 passenger-minutes.**

Every figure here is modelled from average volume profiles. Confirming it
would need ticketing or APC data.

### Conclusion

An initial 5-minute delay at Stop B grew to 8 minutes by Stop C. Because the
same vehicle then worked Route B, 8 minutes was forced onto the next service,
which was observed departing exactly 8 minutes late. Separately, the late
arrival left only 4 minutes for a transfer needing 5, putting 12 transferring
passengers at risk and accounting for roughly 564 passenger-minutes of
estimated impact.

---

## 7. Delay recovery

A trip **recovered** only if all three hold:

1. it was genuinely delayed (`max_delay ≥ on_time_threshold`), and
2. its delay fell by at least `recovery_min_reduction_min`, and
3. its final observed delay is below the on-time threshold.

Condition (1) is not redundant. The first implementation omitted it, so any
trip whose delay dipped — including trips that were never late — counted as
recovered, and route recovery percentages exceeded 100%. The definition was
tightened and a regression test now asserts recovery can never exceed 100%.

Recovery percentage is always `recovered ÷ delayed`, never `recovered ÷ all`.

---

## 8. Sensitivity analysis

The required transfer buffer is an **assumption about people**, not a
measurement. Five minutes at a cross-platform interchange is generous; at a
large bus station with a road to cross it is optimistic.

So the engine re-runs the whole connection-risk analysis across a range of
buffers and reports how much the answer moves. On the shipped dataset:

| Buffer | At risk | Missed | Passengers at risk (est) |
|--------|---------|--------|--------------------------|
| 3 min | 11 | 3 | 130 |
| 5 min | 14 | 3 | 139 |
| 8 min | 23 | 3 | 324 |
| 10 min | 26 | 3 | 375 |

Two things are worth reading carefully.

**At-risk counts move a lot** — from 11 to 26, which is 18.8% of all assessed
connections. Any at-risk figure quoted without its buffer is close to
meaningless.

**Missed connections do not move at all.** Whether a feeder arrived *after*
its connection had already departed is a fact about the operation, independent
of any assumption about walking speed. That is the robust number, and it is
the one to lead with when the buffer is contested.

---

## 9. Configuration reference

| Parameter | Default | Effect |
|-----------|---------|--------|
| `on_time_threshold_min` | 2.0 | Boundary between ON_TIME and MINOR |
| `minor_delay_threshold_min` | 5.0 | Boundary between MINOR and MODERATE |
| `moderate_delay_threshold_min` | 15.0 | Boundary between MODERATE and SEVERE |
| `early_arrival_threshold_min` | −2.0 | Below this, an arrival is EARLY |
| `propagation_window_min` | 90 | Forward look-ahead for propagation |
| `min_propagated_delay_min` | 2.0 | Smallest knock-on worth reporting |
| `vehicle_turnaround_min` | 5.0 | Turnaround between a vehicle's trips |
| `recovery_min_reduction_min` | 1.0 | Smallest reduction counted as recovery |
| `required_transfer_buffer_min` | 5.0 | Minutes passengers need to transfer |
| `missed_connection_margin_min` | 0.0 | Below this available time, MISSED |
| `low_volume_stop_threshold` | 20 | Boardings below this mark LOW confidence |
| `transfer_rate` | 0.18 | Assumed share of boarders who transfer |
| `additional_wait_on_missed_connection_min` | 15.0 | Assumed wait after a missed connection |
| `missing_gps_strategy` | `EXCLUDE` | `EXCLUDE` or `ESTIMATE` |
| `sensitivity_buffers_min` | 3,5,8,10 | Buffers tested |

Editable from the Settings page or `PUT /api/config/{key}`. The engine reads
them on its next run.

---

## 10. What this engine cannot tell you

Stating the limits is part of making the output usable.

- **It cannot prove causation.** VEHICLE_CONTINUITY is the strongest claim
  available, and even that is a constraint plus a matching observation, not an
  experiment.
- **It cannot count passengers.** Every passenger figure is modelled from
  average profiles.
- **It cannot see the cause of a delay.** It sees that a vehicle was late, not
  why. Incident annotations are the only source of cause, and they are entered
  by people.
- **It cannot distinguish a vehicle swap from a recovered delay** without
  vehicle-level telemetry it does not have; it reports the discrepancy and
  lowers its confidence instead of guessing.
- **It assumes the timetable is the right benchmark.** A chronically
  unachievable schedule will show as poor performance, when the honest finding
  is that the schedule needs rewriting.
