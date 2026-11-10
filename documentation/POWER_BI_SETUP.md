# Power BI setup

The detailed pack lives in [`../powerbi/`](../powerbi/). This page is the
summary and the pointer.

## Why there is no .pbix file

A `.pbix` is a proprietary archive that Power BI Desktop must author; it cannot
be generated correctly from outside the tool. Rather than ship a broken file,
this project provides everything needed to build the report in a few minutes,
with every measure written out ready to paste.

## The pack

| File | Contents |
|------|----------|
| [`powerbi/README.md`](../powerbi/README.md) | Build order and the no-Snowflake fallback |
| [`powerbi/CONNECTION_SETUP.md`](../powerbi/CONNECTION_SETUP.md) | Connecting to Snowflake, the reader role, scheduled refresh, troubleshooting |
| [`powerbi/datasets.md`](../powerbi/datasets.md) | The ten views to import and what each column means |
| [`powerbi/DATA_MODEL.md`](../powerbi/DATA_MODEL.md) | Relationships, inactive relationships, sort-by columns |
| [`powerbi/DAX_MEASURES.md`](../powerbi/DAX_MEASURES.md) | Around 35 measures, grouped by theme |
| [`powerbi/DASHBOARD_LAYOUT.md`](../powerbi/DASHBOARD_LAYOUT.md) | Five pages, visual by visual |

## Build order

1. Run `database/snowflake/01` → `06`.
2. Connect Power BI Desktop (`CONNECTION_SETUP.md`).
3. Import the views and dimensions (`datasets.md`).
4. Create the relationships (`DATA_MODEL.md`).
5. Paste the measures (`DAX_MEASURES.md`).
6. Lay out the pages (`DASHBOARD_LAYOUT.md`).

## Required measures

The specification asks for these; all are in `DAX_MEASURES.md`:

| Measure | Definition |
|---------|-----------|
| Total Trips | `DISTINCTCOUNT(FACT_DELAY_EVENT[trip_key])` |
| Delayed Trips | distinct trips where `is_delayed = 1` |
| On-Time Percentage | on-time ÷ **observed** movements |
| Average Delay | `AVERAGE(FACT_DELAY_EVENT[delay_min])` |
| Passengers Affected (est) | `SUM(FACT_PASSENGER_IMPACT[passengers_exposed])` |
| Missed Connections | `SUM(FACT_CONNECTION_RISK[missed_count])` |
| Recovery Percentage | recovered ÷ **delayed** trips |

## Three things to get right

**Divide by observed movements, not all rows.** `FACT_DELAY_EVENT` carries
`is_assessable` for exactly this. Including unmeasured stops in the denominator
understates punctuality and rewards poor telemetry.

**Do not replace blanks with zero.** The facts store `BLANK()` for an
unobserved arrival so that `AVERAGE` ignores it. A `COALESCE(..., 0)` would
turn every missing GPS event into an on-time arrival.

**Keep the estimate labelling.** The passenger measures are named `(est)` and
the layout puts a standing caveat on that page. A stakeholder reading "12,000
passengers affected" as a measured count would be misled.

## No Snowflake account?

Point Power BI at `data-engine/output/csv/*.csv` instead. Get Data →
Text/CSV, load `route_performance.csv`, `delay_events.csv`,
`propagation_results.csv`, `connection_risk.csv`, `passenger_impact.csv` and
`sensitivity_analysis.csv`. The measures work unchanged apart from table names.
