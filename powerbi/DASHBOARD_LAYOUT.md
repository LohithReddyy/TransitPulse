# Suggested dashboard layout

Five pages. Each answers one question an operations manager actually asks.

Theme: dark navy canvas (`#0B1B33`), card background `#112B4A`, text
`#F2F7FF`, accents `#2F6FED` and `#22D3EE`. Save it once as a custom theme
(View → Themes → Customize) so every page matches the application.

A consistent colour rule across all pages:

| Delay class | Colour |
|-------------|--------|
| EARLY | `#8B5CF6` |
| ON_TIME | `#22C55E` |
| MINOR | `#EAB308` |
| MODERATE | `#F97316` |
| SEVERE | `#EF4444` |
| MISSING | `#64748B` |

MISSING gets a deliberately flat grey. It is not a performance band and
should not read as one.

---

## Page 1 — Network overview

**Slicers (top row):** `DIM_DATE[calendar_date]`, `DIM_ROUTE[route_name]`,
`DIM_TIME_BAND[time_band_name]`.

**KPI cards (row 2):**
`Total Trips` · `On-Time Percentage` · `Average Delay (min)` ·
`Delayed Trips` · `Passengers Affected (est)` · `Recovery Percentage`

**Row 3:**
- *Column chart* — `DIM_DELAY_CLASS[delay_class_name]` by `Total Movements`,
  coloured by the table above.
- *Bar chart* — `DIM_ROUTE[route_name]` by `On-Time Percentage`, sorted
  ascending so the worst route is on top.

**Row 4:**
- *Line chart* — `DIM_DATE[calendar_date]` with `Average Delay (min)` and
  `P90 Delay (min)` as two series. The gap between them shows whether delay is
  widespread or concentrated in a few bad trips.
- *Card* — `Coverage Warning`. Blank most of the time; appears when coverage
  drops below 85%.

---

## Page 2 — Route and stop performance

**Matrix:** rows `DIM_ROUTE[route_name]`, values `Total Trips`,
`On-Time Percentage`, `Average Delay (min)`, `P90 Delay (min)`,
`Delayed Trips`, `Data Coverage %`.

Conditional formatting on `On-Time Percentage`: red below 70, amber 70–85,
green above 85. Add data bars to `Average Delay (min)`.

**Scatter chart:** `VW_STOP_PERFORMANCE`, X = `total_boardings`,
Y = `delay_frequency_pct`, size = `observations`, legend = `confidence`.

This is the most useful visual on the page. The top-right quadrant is where
intervention pays off: busy stops that are frequently late. The top-left
quadrant looks alarming but is mostly noise from small samples, which is
exactly why `confidence` is on the legend rather than hidden.

**Table:** worst 20 stops, with a page-level filter of
`confidence <> "LOW"` so the ranking is not dominated by three-observation
stops.

---

## Page 3 — Delay propagation

**KPI cards:** `Propagation Links` · `High Confidence Links` ·
`Causal Links` · `Correlation Only Links` · `Affected Services` ·
`Avg Propagated Delay (min)`

**Donut chart:** `mechanism` by `Propagation Links`.

**Stacked column:** `mechanism` on the axis, `confidence` as the legend.
The shape tells the story: VEHICLE_CONTINUITY is mostly HIGH, SHARED_STOP is
entirely LOW.

**Sankey or matrix:** `source_route_name` → `affected_route_name`, values
`Total Propagated Delay (min)`. If you would rather not add a custom visual,
a matrix works and exports more cleanly.

**Detail table:** `source_trip_id`, `affected_trip_id`, `mechanism`,
`confidence`, `propagated_delay_min`, `incident_id`, `calculation`.

Set the `evidence` column as a tooltip on this table. Anyone questioning a
figure can hover and read the facts behind it instead of taking it on trust.

**Text box, bottom of page:**

> SHARED_STOP links describe two services being late at the same place, not a
> demonstrated cause. Only VEHICLE_CONTINUITY rests on a physical constraint —
> the vehicle cannot leave before it arrives — and those carry HIGH confidence.

---

## Page 4 — Connection risk and sensitivity

**KPI cards:** `Connections Assessed` · `Connections At Risk` ·
`Missed Connections` · `At Risk %` · `Connections Unassessed` ·
`Structurally Tight Connections`

**Clustered column:** `VW_SENSITIVITY_ANALYSIS`, axis
`required_buffer_min`, values `at_risk_connections`, `missed_connections`,
`safe_connections`.

**Line chart:** same axis, `passengers_at_risk` and
`additional_wait_minutes`.

**Matrix:** rows `interchange_stop_name`, values `Connections At Risk`,
`Missed Connections`, `At Risk %`, `Passengers At Risk of Missing Transfer (est)`.

**Text box:**

> At-risk counts move with the assumed transfer buffer; missed connections do
> not. Whether a feeder arrived after its connection had left is a fact about
> the operation. Always quote the buffer alongside any at-risk figure.

---

## Page 5 — Passenger impact and recovery

A standing caveat belongs at the top of this page, not in a footnote:

> **All passenger figures on this page are modelled estimates** derived from
> average boardings and on-board load, not counts of individual passengers.
> Confirming them would require ticketing or automatic passenger counting data.

**KPI cards:** `Passengers Affected (est)` ·
`Passenger Minutes Lost (est)` ·
`Avg Added Wait per Passenger (min, est)` ·
`Passengers Missing Transfer (est)` · `Low Confidence Impact Share`

**Bar chart:** `route_name` by `Passenger Minutes Lost (est)`, descending.

Rank by passenger-minutes, not by average delay. A route running two minutes
late with full buses harms more people than an empty route running ten
minutes late, and ranking by delay alone would send resources to the wrong
place.

**Heat map (matrix with background formatting):** rows `route_name`,
columns `time_band_name`, values `Passengers Affected (est)`.

**Recovery section:**
- KPI cards: `Trips That Were Delayed` · `Trips Recovered` ·
  `Recovery Percentage` · `Avg Recovery (min)`
- Table from `VW_RECOVERY_ANALYTICS`: `trip_id`, `route_id`,
  `max_delay_min`, `final_delay_min`, `recovery_min`, `data_confidence`.

---

## Cross-page settings

- **Sync slicers** (View → Sync slicers) for date and route across all pages.
- **Edit interactions**: set the caveat and warning text boxes to ignore
  filters so they never disappear mid-investigation.
- **Page navigation**: add buttons rather than relying on page tabs; the
  report reads better embedded.
- **Tooltips page**: a small hidden page showing route detail makes the
  overview charts far more useful on hover.
- **Accessibility**: set tab order on every page, give each visual alt text,
  and never rely on colour alone — the delay class names are always visible
  somewhere in the visual.
