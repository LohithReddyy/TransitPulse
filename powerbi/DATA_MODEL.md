# Data model

## Relationships

Create these in Model view. All are single-direction, one-to-many from the
dimension to the fact. Leave cross-filtering as Single unless noted: making
them bidirectional on this model introduces ambiguous paths between the two
route keys on `FACT_PROPAGATION`.

| From (1) | To (many) | Cardinality | Active |
|----------|-----------|-------------|--------|
| `DIM_DATE[date_key]` | `FACT_DELAY_EVENT[date_key]` | 1:* | yes |
| `DIM_ROUTE[route_key]` | `FACT_DELAY_EVENT[route_key]` | 1:* | yes |
| `DIM_STOP[stop_key]` | `FACT_DELAY_EVENT[stop_key]` | 1:* | yes |
| `DIM_TRIP[trip_key]` | `FACT_DELAY_EVENT[trip_key]` | 1:* | yes |
| `DIM_VEHICLE[vehicle_key]` | `FACT_DELAY_EVENT[vehicle_key]` | 1:* | yes |
| `DIM_TIME_BAND[time_band_key]` | `FACT_DELAY_EVENT[time_band_key]` | 1:* | yes |
| `DIM_DELAY_CLASS[delay_class_key]` | `FACT_DELAY_EVENT[delay_class_key]` | 1:* | yes |
| `DIM_DATE[date_key]` | `FACT_PROPAGATION[date_key]` | 1:* | yes |
| `DIM_ROUTE[route_key]` | `FACT_PROPAGATION[source_route_key]` | 1:* | yes |
| `DIM_ROUTE[route_key]` | `FACT_PROPAGATION[affected_route_key]` | 1:* | **no** |
| `DIM_DATE[date_key]` | `FACT_CONNECTION_RISK[date_key]` | 1:* | yes |
| `DIM_STOP[stop_key]` | `FACT_CONNECTION_RISK[stop_key]` | 1:* | yes |
| `DIM_ROUTE[route_key]` | `FACT_CONNECTION_RISK[feeder_route_key]` | 1:* | yes |
| `DIM_ROUTE[route_key]` | `FACT_CONNECTION_RISK[connecting_route_key]` | 1:* | **no** |
| `DIM_DATE[date_key]` | `FACT_PASSENGER_IMPACT[date_key]` | 1:* | yes |
| `DIM_ROUTE[route_key]` | `FACT_PASSENGER_IMPACT[route_key]` | 1:* | yes |
| `DIM_STOP[stop_key]` | `FACT_PASSENGER_IMPACT[stop_key]` | 1:* | yes |
| `DIM_TIME_BAND[time_band_key]` | `FACT_PASSENGER_IMPACT[time_band_key]` | 1:* | yes |

### Why two relationships to `DIM_ROUTE` are inactive

`FACT_PROPAGATION` and `FACT_CONNECTION_RISK` each reference a route twice —
the source and the affected service. Power BI allows only one active path
between two tables, so the second is created inactive and activated in a
measure when you need it:

```dax
Links By Affected Route =
CALCULATE (
    COUNTROWS ( FACT_PROPAGATION ),
    USERELATIONSHIP ( DIM_ROUTE[route_key], FACT_PROPAGATION[affected_route_key] )
)
```

Without this, slicing propagation by route silently filters only the source
side, and a reader would conclude a route causes delays it is actually
receiving.

`FACT_SENSITIVITY`, `VW_RECOVERY_ANALYTICS` and `VW_DATA_QUALITY` stay
unrelated to the dimensions. They are already aggregated at a different grain
and relating them would double-count.

## Mark the date table

Select `DIM_DATE` → Table tools → **Mark as date table** → `calendar_date`.
Time intelligence functions are unreliable without this.

## Hide from report view

Hide every `*_key` column on the fact tables and the dimensions' surrogate
keys. Report authors should drag the descriptive columns (`route_name`,
`stop_name`, `delay_class_name`) and the measures, not raw keys.

## Sort-by columns

| Column | Sort by |
|--------|---------|
| `DIM_TIME_BAND[time_band_name]` | `sort_order` |
| `DIM_DELAY_CLASS[delay_class_name]` | `severity_order` |
| `DIM_DATE[month_name]` | `calendar_month` |

Without these, SEVERE sorts before ON_TIME and the charts read backwards.
