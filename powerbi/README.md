# TransitPulse Power BI pack

A `.pbix` file cannot be generated programmatically — the format is a
proprietary archive that Power BI Desktop must author. This folder therefore
contains everything needed to build the dashboard in a few minutes:

| File | What it covers |
|------|----------------|
| `CONNECTION_SETUP.md` | Connecting Power BI Desktop to Snowflake |
| `DATA_MODEL.md`       | Tables to import and the relationships to create |
| `DAX_MEASURES.md`     | Every measure, ready to paste |
| `DASHBOARD_LAYOUT.md` | Page-by-page layout and visual choices |
| `datasets.md`         | The exact views to load and what each column means |

## Build order

1. Run the warehouse scripts in `../database/snowflake/` (01 → 06).
2. Follow `CONNECTION_SETUP.md` to connect.
3. Import the views listed in `datasets.md`.
4. Create the relationships in `DATA_MODEL.md`.
5. Paste the measures from `DAX_MEASURES.md`.
6. Lay out the pages per `DASHBOARD_LAYOUT.md`.

## No Snowflake account?

Point Power BI at the CSV files in `../data-engine/output/csv/` instead. Get
Data → Text/CSV, then load `route_performance.csv`, `delay_events.csv`,
`propagation_results.csv`, `connection_risk.csv`, `passenger_impact.csv` and
`sensitivity_analysis.csv`. The measures work unchanged apart from the table
names, which become the file names.

## One reporting rule

Every passenger figure in this model is a modelled estimate derived from
average volume profiles, not a count of individual passengers. The measures
below are named with `(est)` where that applies, and `DASHBOARD_LAYOUT.md`
places a standing caveat on the passenger page. Keep both: a stakeholder
reading "12,000 passengers affected" as a measured figure would be misled.
