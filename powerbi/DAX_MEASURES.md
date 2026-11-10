# DAX measures

Create a table called `_Measures` (Home → Enter Data → name it `_Measures`,
one dummy column, then hide the column) and add every measure there. Keeping
measures in one place stops them being lost when a table is refreshed.

A note that applies throughout: the fact tables deliberately store `BLANK()`
rather than `0` for an unobserved arrival. `AVERAGE` and `COUNT` ignore blanks,
so an average delay is never diluted by stops where nothing was measured. Do
not "fix" this with `COALESCE(..., 0)`: that would turn a missing GPS event
into an on-time arrival and flatter every performance figure.

## Core counts

```dax
Total Trips =
DISTINCTCOUNT ( FACT_DELAY_EVENT[trip_key] )
```

```dax
Total Movements =
COUNTROWS ( FACT_DELAY_EVENT )
```

```dax
Observed Movements =
SUM ( FACT_DELAY_EVENT[is_assessable] )
```

```dax
Missing Observations =
CALCULATE (
    COUNTROWS ( FACT_DELAY_EVENT ),
    FACT_DELAY_EVENT[is_missing] = TRUE ()
)
```

```dax
Delayed Trips =
CALCULATE (
    DISTINCTCOUNT ( FACT_DELAY_EVENT[trip_key] ),
    FACT_DELAY_EVENT[is_delayed] = 1
)
```

```dax
On Time Movements =
SUM ( FACT_DELAY_EVENT[is_on_time] )
```

## Punctuality

```dax
On-Time Percentage =
VAR Assessable = [Observed Movements]
RETURN
    IF ( Assessable = 0, BLANK (), DIVIDE ( [On Time Movements], Assessable ) )
```

```dax
Average Delay (min) =
AVERAGE ( FACT_DELAY_EVENT[delay_min] )
```

```dax
P90 Delay (min) =
PERCENTILE.INC ( FACT_DELAY_EVENT[delay_min], 0.90 )
```

```dax
Max Delay (min) =
MAX ( FACT_DELAY_EVENT[delay_min] )
```

```dax
Data Coverage % =
DIVIDE ( [Observed Movements], [Total Movements] )
```

A coverage figure belongs next to every punctuality figure. A route reporting
98% on time from 40% coverage is telling you about its GPS, not its service.

## Propagation

```dax
Propagation Links =
COUNTROWS ( FACT_PROPAGATION )
```

```dax
High Confidence Links =
CALCULATE (
    COUNTROWS ( FACT_PROPAGATION ),
    FACT_PROPAGATION[confidence] = "HIGH"
)
```

```dax
Affected Services =
DISTINCTCOUNT ( FACT_PROPAGATION[affected_trip_key] )
```

```dax
Total Propagated Delay (min) =
SUM ( FACT_PROPAGATION[propagated_delay_min] )
```

```dax
Avg Propagated Delay (min) =
AVERAGE ( FACT_PROPAGATION[propagated_delay_min] )
```

```dax
Causal Links =
CALCULATE (
    COUNTROWS ( FACT_PROPAGATION ),
    FACT_PROPAGATION[mechanism] = "VEHICLE_CONTINUITY"
)
```

```dax
Correlation Only Links =
CALCULATE (
    COUNTROWS ( FACT_PROPAGATION ),
    FACT_PROPAGATION[mechanism] = "SHARED_STOP"
)
```

```dax
Incident Corroborated Links =
CALCULATE (
    COUNTROWS ( FACT_PROPAGATION ),
    NOT ( ISBLANK ( FACT_PROPAGATION[incident_id] ) )
)
```

## Connection risk

```dax
Connections Assessed =
SUM ( FACT_CONNECTION_RISK[assessed_count] )
```

```dax
Connections At Risk =
SUM ( FACT_CONNECTION_RISK[at_risk_count] )
```

```dax
Missed Connections =
SUM ( FACT_CONNECTION_RISK[missed_count] )
```

```dax
Connections Unassessed =
SUM ( FACT_CONNECTION_RISK[unassessed_count] )
```

```dax
At Risk % =
DIVIDE ( [Connections At Risk], [Connections Assessed] )
```

The denominator is assessed connections, not all of them. A connection whose
feeder had no GPS record is unknown; counting it as safe would understate risk.

```dax
Structurally Tight Connections =
CALCULATE (
    COUNTROWS ( FACT_CONNECTION_RISK ),
    FACT_CONNECTION_RISK[planned_connection_tight] = TRUE ()
)
```

```dax
Avg Available Transfer (min) =
AVERAGE ( FACT_CONNECTION_RISK[available_transfer_min] )
```

## Passenger impact (estimates)

```dax
Passengers Affected (est) =
SUM ( FACT_PASSENGER_IMPACT[passengers_exposed] )
```

```dax
Passenger Minutes Lost (est) =
SUM ( FACT_PASSENGER_IMPACT[passenger_minutes_lost] )
```

```dax
Avg Added Wait per Passenger (min, est) =
DIVIDE (
    [Passenger Minutes Lost (est)],
    [Passengers Affected (est)]
)
```

```dax
Passengers At Risk of Missing Transfer (est) =
SUM ( FACT_CONNECTION_RISK[passengers_at_risk] )
```

```dax
Passengers Missing Transfer (est) =
SUM ( FACT_CONNECTION_RISK[passengers_missed] )
```

```dax
Low Confidence Impact Share =
VAR Total = COUNTROWS ( FACT_PASSENGER_IMPACT )
VAR LowConf =
    CALCULATE (
        COUNTROWS ( FACT_PASSENGER_IMPACT ),
        FACT_PASSENGER_IMPACT[confidence] IN { "LOW", "NONE" }
    )
RETURN
    DIVIDE ( LowConf, Total )
```

## Recovery

```dax
Trips That Were Delayed =
SUM ( VW_RECOVERY_ANALYTICS[was_delayed] )
```

```dax
Trips Recovered =
SUM ( VW_RECOVERY_ANALYTICS[recovered] )
```

```dax
Recovery Percentage =
DIVIDE ( [Trips Recovered], [Trips That Were Delayed] )
```

The denominator is trips that actually fell behind. Dividing by all trips
would make a punctual network look bad at recovering, which is meaningless.

```dax
Avg Recovery (min) =
CALCULATE (
    AVERAGE ( VW_RECOVERY_ANALYTICS[recovery_min] ),
    VW_RECOVERY_ANALYTICS[recovered] = 1
)
```

## Formatting and display

```dax
Delay Trend Label =
VAR Avg = [Average Delay (min)]
RETURN
    SWITCH (
        TRUE (),
        ISBLANK ( Avg ), "No data",
        Avg < 1,  "Running to schedule",
        Avg < 3,  "Minor slippage",
        Avg < 6,  "Noticeable delay",
        "Significant delay"
    )
```

```dax
Coverage Warning =
IF (
    [Data Coverage %] < 0.85,
    "⚠ Only "
        & FORMAT ( [Data Coverage %], "0.0%" )
        & " of movements were observed; treat these figures with caution.",
    BLANK ()
)
```

Put `Coverage Warning` in a card on every performance page. It stays blank
when coverage is good and speaks up only when it matters.

```dax
Estimate Caveat =
"Passenger figures are modelled estimates from average volume profiles, not confirmed counts."
```

## Formatting to apply

| Measure | Format |
|---------|--------|
| `On-Time Percentage`, `At Risk %`, `Recovery Percentage`, `Data Coverage %`, `Low Confidence Impact Share` | Percentage, 1 decimal |
| Any `… (min)` | Decimal, 2 decimals |
| Counts and passenger totals | Whole number, thousands separator |
