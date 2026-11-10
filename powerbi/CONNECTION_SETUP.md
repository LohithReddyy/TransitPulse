# Connecting Power BI Desktop to Snowflake

## Prerequisites

- Power BI Desktop, August 2023 release or newer.
- The TransitPulse warehouse built by `../database/snowflake/01` → `06`.
- A Snowflake role with `USAGE` on the warehouse, database and schemas, and
  `SELECT` on the `ANALYTICS` views.

A minimal reader role:

```sql
USE ROLE ACCOUNTADMIN;

CREATE ROLE IF NOT EXISTS TRANSITPULSE_READER;
GRANT USAGE ON WAREHOUSE TRANSITPULSE_WH      TO ROLE TRANSITPULSE_READER;
GRANT USAGE ON DATABASE  TRANSITPULSE          TO ROLE TRANSITPULSE_READER;
GRANT USAGE ON SCHEMA    TRANSITPULSE.ANALYTICS TO ROLE TRANSITPULSE_READER;
GRANT USAGE ON SCHEMA    TRANSITPULSE.CORE      TO ROLE TRANSITPULSE_READER;
GRANT SELECT ON ALL VIEWS  IN SCHEMA TRANSITPULSE.ANALYTICS TO ROLE TRANSITPULSE_READER;
GRANT SELECT ON ALL TABLES IN SCHEMA TRANSITPULSE.CORE      TO ROLE TRANSITPULSE_READER;
GRANT SELECT ON FUTURE VIEWS  IN SCHEMA TRANSITPULSE.ANALYTICS TO ROLE TRANSITPULSE_READER;

GRANT ROLE TRANSITPULSE_READER TO USER <your_user>;
```

## Steps

1. **Home → Get Data → More… → Snowflake.**
2. Fill in:
   - **Server**: `<account_identifier>.snowflakecomputing.com`
     (for example `ab12345.eu-west-2.aws.snowflakecomputing.com`)
   - **Warehouse**: `TRANSITPULSE_WH`
3. **Advanced options → Role**: `TRANSITPULSE_READER`.
4. Sign in. Prefer **Microsoft account (SSO)** or **Snowflake** username and
   password entered into the credential dialog — never into a query, a
   parameter or a file in this repository.
5. In the Navigator, expand `TRANSITPULSE` → `ANALYTICS` and tick the views
   listed in `datasets.md`.
6. Choose **Import**. The dataset is small, and Import gives faster visuals
   and full DAX support. Use DirectQuery only if you need live data and accept
   the warehouse staying warm.
7. **Transform Data** if you want to rename columns, then **Close & Apply**.

## Scheduled refresh (Power BI Service)

1. Publish the report to a workspace.
2. Workspace → Dataset → **Settings → Data source credentials** → sign in to
   Snowflake again (credentials are not carried over from Desktop).
3. **Scheduled refresh** → on → set a time shortly after your pipeline runs.
4. A gateway is not required for Snowflake, which is a cloud source.

## Troubleshooting

| Symptom | Cause and fix |
|---------|---------------|
| `Object does not exist or not authorized` | The role lacks `SELECT`, or the views were never created. Re-run `05_analytics_views.sql` and the grants above. |
| Navigator shows no views | You are browsing the wrong database or schema; views live in `TRANSITPULSE.ANALYTICS`. |
| Empty tables | The `RAW` tables were never loaded. Run `04_load_data.sql`, then rebuild `CORE` with `03_star_schema.sql`. |
| Very slow refresh | The warehouse was suspended and is resuming. It auto-suspends after 60s by design; the first query pays the start-up cost. |
| `Incorrect username or password` on refresh | Set the credentials again in the Service; Desktop credentials do not publish. |
