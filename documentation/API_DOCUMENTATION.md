# API documentation

Base URL `http://localhost:8080`
Interactive docs `http://localhost:8080/swagger-ui.html`
OpenAPI spec `http://localhost:8080/v3/api-docs`

## Response envelope

Every successful response:

```json
{
  "success": true,
  "message": "OK",
  "data": { },
  "timestamp": "2026-03-16T09:15:00"
}
```

Every error:

```json
{
  "success": false,
  "status": 404,
  "error": "Not Found",
  "message": "Route not found: R99",
  "path": "/api/routes/R99",
  "fieldErrors": null,
  "timestamp": "2026-03-16T09:15:00"
}
```

### Status codes

| Code | Meaning |
|------|---------|
| 200 | Success |
| 201 | Created |
| 400 | Validation or semantic error; `fieldErrors` populated where applicable |
| 404 | Entity not found; the message names the id |
| 503 | Analytics requested before the Python engine has produced them |

A 503 is returned with the command to fix it rather than an empty result,
because an empty propagation list is indistinguishable from "no delays
propagated" and would be read as good news:

```json
{
  "success": false,
  "status": 503,
  "error": "Analytics Unavailable",
  "message": "Analytics payload 'propagation' has not been generated yet. Run the data engine first:  cd data-engine && python -m transitpulse_engine.pipeline"
}
```

---

## Health

### `GET /api/health`

```json
{ "success": true, "data": { "status": "UP", "application": "TransitPulse", "version": "1.0.0" } }
```

---

## Routes

### `GET /api/routes`

| Parameter | Type | Notes |
|-----------|------|-------|
| `search` | string | Matches route id or name, case-insensitive |
| `mode` | string | `BUS`, `TRAM`, `METRO`, `RAIL`, `FERRY` or `ALL` |

```json
{
  "success": true,
  "data": [
    {
      "routeId": "R01",
      "routeName": "Harbour Line",
      "mode": "BUS",
      "originName": "Harbour Terminal",
      "destinationName": "Civic Centre",
      "status": "ACTIVE",
      "stopCount": 9,
      "scheduledTrips": 12,
      "avgDelayMin": 3.02,
      "maxDelayMin": 18.4,
      "onTimePercentage": 74.53,
      "delayedTrips": 5,
      "performanceBand": "FAIR"
    }
  ]
}
```

Performance fields are null when the engine has not run; the route list itself
still works.

### `GET /api/routes/{routeId}`
### `GET /api/routes/{routeId}/stops`

```json
{
  "success": true,
  "data": [
    { "routeStopId": "R01-01", "routeId": "R01", "stopId": "S001",
      "stopSequence": 1, "distanceFromOriginKm": 0.00, "scheduledRuntimeMin": 3 }
  ]
}
```

### `GET /api/routes/{routeId}/trips`

---

## Stops

### `GET /api/stops`

| Parameter | Type | Notes |
|-----------|------|-------|
| `search` | string | Stop id or name |
| `interchangeOnly` | boolean | Default false |
| `lowVolumeOnly` | boolean | Default false |

```json
{
  "success": true,
  "data": [
    {
      "stopId": "S007",
      "stopName": "Civic Centre",
      "stopType": "INTERCHANGE",
      "interchange": true,
      "routeConnections": ["R01", "R02", "R05", "R06", "R07", "R08"],
      "passengerVolume": 1843,
      "averageWaitMin": 4.6,
      "avgDelayMin": 3.41,
      "delayFrequencyPct": 38.2,
      "observations": 68,
      "lowVolume": false,
      "confidence": "HIGH"
    }
  ]
}
```

`confidence` is `LOW` when boardings fall below `low_volume_stop_threshold`
or fewer than five observations exist. Rank stops only after filtering those
out, or the ranking is dominated by noise.

### `GET /api/stops/{stopId}`
### `GET /api/stops/{stopId}/passenger-volumes`

---

## Trips

### `GET /api/trips`  ·  `?routeId=`
### `GET /api/trips/{tripId}`
### `GET /api/trips/{tripId}/scheduled-movements`
### `GET /api/trips/{tripId}/actual-movements`

A record with no GPS event is returned with nulls and a flag, never with a
fabricated time:

```json
{
  "actualMovementId": "A-T0001-04",
  "tripId": "T0001",
  "stopId": "S019",
  "stopSequence": 4,
  "actualArrival": null,
  "actualDeparture": null,
  "observedDelayMin": null,
  "dataQualityFlag": "MISSING_GPS",
  "isDiverted": "N"
}
```

### `GET /api/trips/{tripId}/delays`

```json
{
  "success": true,
  "data": [
    {
      "stopSequence": 1, "stopId": "S001", "stopName": "Harbour Terminal",
      "scheduledArrival": "2026-03-16T07:00:00",
      "actualArrival": "2026-03-16T07:00:00",
      "delayMin": 0.0, "delayClass": "ON_TIME",
      "dataQualityFlag": "OK", "observed": true, "diverted": false, "note": null
    },
    {
      "stopSequence": 4, "stopId": "S019", "stopName": "Riverside Quay",
      "scheduledArrival": "2026-03-16T07:14:00",
      "actualArrival": null,
      "delayMin": null, "delayClass": "MISSING",
      "dataQualityFlag": "MISSING_GPS", "observed": false, "diverted": false,
      "note": "No GPS arrival event recorded. Never counted as zero delay."
    }
  ]
}
```

### `GET /api/trips/{tripId}/investigation`

The full payload behind the Delay Investigation page.

```json
{
  "success": true,
  "data": {
    "tripId": "T0011",
    "routeId": "R01",
    "routeName": "Harbour Line",
    "vehicleId": "V003",
    "serviceDate": "2026-03-16",
    "stopDelays": [ ],
    "maxDelayMin": 13.6,
    "finalDelayMin": 13.6,
    "firstDelayStopSequence": 2,
    "delayClass": "MODERATE",
    "recovered": false,
    "recoveryMin": 0.0,
    "stopsTotal": 9,
    "stopsObserved": 9,
    "stopsMissing": 0,
    "dataConfidence": "HIGH",
    "propagation": [
      {
        "source_trip_id": "T0011",
        "affected_trip_id": "T0012",
        "mechanism": "VEHICLE_CONTINUITY",
        "confidence": "HIGH",
        "propagated_delay_min": 12.6,
        "lag_min": -7.6,
        "vehicle_id": "V003",
        "incident_id": null,
        "calculation": "forced_delay = (actual_arrival 07:41 + turnaround 5min) - scheduled_departure 07:34 = 12.6 min",
        "evidence": [
          "Same vehicle V003 operates trip T0011 then trip T0012.",
          "Trip T0011 arrived 13.6 min late at stop S007 (07:41).",
          "Scheduled turnaround is 5 min, so the vehicle cannot depart before 07:46.",
          "Trip T0012 was scheduled to depart at 07:34."
        ]
      }
    ],
    "affectedConnections": [ ],
    "relatedIncidents": [ ],
    "explanation": "Trip T0011 on route Harbour Line reached a maximum delay of 13.6 min, first falling behind at stop 2. It finished 13.6 min late. The engine identified 1 downstream service(s) this delay could have reached; each carries its own evidence and confidence level."
  }
}
```

A negative `lag_min` means the next trip was due to depart before the vehicle
had even arrived — the most constrained case, not an error.

### `GET /api/trips/{tripId}/vehicle-chain`

---

## Analytics

### `GET /api/analytics/dashboard`

The only analytics endpoint that always answers. Physical counts come from the
database; delay figures from the engine.

```json
{
  "success": true,
  "data": {
    "serviceDate": "2026-03-16",
    "totalRoutes": 10,
    "totalStops": 55,
    "totalVehicles": 30,
    "totalScheduledTrips": 120,
    "totalMovements": 792,
    "observedMovements": 775,
    "unobservedMovements": 17,
    "missingGpsRecords": 11,
    "divertedRecords": 6,
    "delayedTrips": 51,
    "onTimePercentage": 68.65,
    "averageDelayMin": 2.55,
    "p90DelayMin": 9.7,
    "maxDelayMin": 19.0,
    "recoveredTrips": 12,
    "recoveryPercentage": 23.53,
    "passengersAffected": 11769,
    "passengerMinutesLost": 105535.2,
    "propagationLinks": 71,
    "highConfidencePropagations": 10,
    "connectionsAtRisk": 14,
    "missedConnections": 3,
    "openIncidents": 0,
    "totalIncidents": 21,
    "delayClassDistribution": {
      "ON_TIME": 531, "MODERATE": 150, "MINOR": 69,
      "SEVERE": 24, "MISSING": 17, "EARLY": 1
    },
    "analyticsAvailable": true,
    "estimateCaveat": "Passenger figures are estimates derived from average volume profiles, not confirmed passenger counts."
  }
}
```

### `GET /api/analytics/propagation`

| Parameter | Type | Notes |
|-----------|------|-------|
| `mechanism` | string | `VEHICLE_CONTINUITY`, `CONNECTION`, `SHARED_STOP`, `ALL` |
| `confidence` | string | `HIGH`, `MEDIUM`, `LOW`, `ALL` |
| `limit` | int | Default 200 |

```json
{
  "success": true,
  "data": {
    "summary": {
      "totalLinks": 71,
      "byMechanism": { "SHARED_STOP": 54, "VEHICLE_CONTINUITY": 11, "CONNECTION": 6 },
      "byConfidence": { "LOW": 44, "MEDIUM": 17, "HIGH": 10 },
      "affectedTripsUnique": 32,
      "avgPropagatedDelayMin": 8.7,
      "maxPropagatedDelayMin": 18.5
    },
    "links": [ ],
    "returned": 10,
    "totalAvailable": 71,
    "note": "Each link carries its mechanism, evidence and a confidence level. SHARED_STOP links are correlation only and are never presented as a demonstrated cause."
  }
}
```

### `GET /api/analytics/connection-risk`

| Parameter | Notes |
|-----------|-------|
| `status` | `AT_RISK`, `MISSED`, `SAFE`, `HELD_BY_CONNECTING_SERVICE`, `UNKNOWN_NO_GPS`, `ALL` |
| `limit` | Default 200 |

```json
{
  "summary": {
    "totalConnections": 80, "assessed": 80, "atRisk": 14, "missed": 3,
    "safe": 66, "heldByConnectingService": 1, "unknown": 0,
    "passengersAtRisk": 139, "passengersMissed": 39,
    "atRiskPercentage": 17.5, "plannedTightConnections": 8
  },
  "connections": [
    {
      "connection_id": "C0014",
      "interchange_stop_id": "S007",
      "feeder_trip_id": "T0014",
      "connecting_trip_id": "T0026",
      "feeder_delay_min": 9.1,
      "planned_transfer_min": 8.0,
      "available_transfer_min": -1.1,
      "required_buffer_min": 5.0,
      "shortfall_min": 6.1,
      "status": "MISSED",
      "risk_score": 1.0,
      "transferring_passengers": 13,
      "explanation": "Feeder trip T0014 arrived 9.1 min late. Available transfer time -1.1 min against a required 5 min buffer -> MISSED."
    }
  ]
}
```

`atRiskPercentage` divides by `assessed`, not `totalConnections`. A connection
whose feeder had no GPS record is `UNKNOWN_NO_GPS` and sits in neither
numerator nor denominator.

### `GET /api/analytics/passenger-impact`
### `GET /api/analytics/sensitivity`
### `GET /api/analytics/recovery`
### `GET /api/analytics/worked-example`
### `GET /api/analytics/validation`
### `GET /api/analytics/route-performance`
### `GET /api/analytics/stop-performance`
### `GET /api/analytics/trip-summary`
### `GET /api/analytics/delay-distribution`
### `GET /api/analytics/engine-status`

```json
{
  "success": true,
  "data": {
    "engineOutputDirectory": "/app/engine-output/json",
    "available": true,
    "payloads": ["configuration", "connection_risk", "dashboard", "passenger_impact",
                 "propagation", "route_performance", "sensitivity", "stop_performance",
                 "trip_delay_summary", "validation", "worked_example"]
  }
}
```

---

## Incidents

### `GET /api/incidents`  ·  `?routeId=` `?status=`
### `GET /api/incidents/diversions`
### `GET /api/incidents/{incidentId}`

### `POST /api/incidents`

```json
{
  "incidentType": "TRAFFIC_CONGESTION",
  "routeId": "R01",
  "stopId": "S007",
  "startTime": "2026-03-16T09:00:00",
  "endTime": "2026-03-16T10:30:00",
  "severity": "HIGH",
  "description": "Congestion following a collision near Civic Centre",
  "isDiversion": "N",
  "status": "OPEN",
  "reportedBy": "operations"
}
```

Returns 201 with the created incident, including its generated `incidentId`.

**Validation**

| Field | Rule |
|-------|------|
| `incidentType` | One of SIGNAL_FAILURE, ROAD_WORKS, TRAFFIC_CONGESTION, VEHICLE_BREAKDOWN, DIVERSION, WEATHER, SPECIAL_EVENT, OTHER |
| `routeId` | Required and must exist |
| `stopId` | Optional; must exist if supplied |
| `startTime` | Required, ISO-8601 |
| `endTime` | Optional; must not precede `startTime` |
| `severity` | LOW, MEDIUM, HIGH, CRITICAL |
| `status` | OPEN, MONITORING, CLOSED |
| `isDiversion` | Y or N. If `Y`, `skippedStopId` or `stopId` must identify the affected stop |

That last rule exists because a diversion with no stop recorded cannot be
excluded from schedule comparison, which would leave the engine reporting
delays for stops the vehicle never attempted.

```json
{
  "success": false,
  "status": 400,
  "error": "Validation Failed",
  "message": "One or more fields are invalid",
  "fieldErrors": {
    "incidentType": "incidentType must be one of SIGNAL_FAILURE, ROAD_WORKS, ..."
  }
}
```

### `PUT /api/incidents/{incidentId}`
### `DELETE /api/incidents/{incidentId}`

---

## Configuration

### `GET /api/config`  ·  `?category=`
### `GET /api/config/grouped`
### `GET /api/config/{key}`

### `PUT /api/config/{key}`

```json
{ "configValue": "8.0" }
```

```json
{
  "success": true,
  "message": "Updated required_transfer_buffer_min. Re-run the data engine to apply it to analytics.",
  "data": {
    "configKey": "required_transfer_buffer_min",
    "configValue": "8.0",
    "valueType": "NUMBER",
    "category": "CONNECTION",
    "minValue": 0.0000,
    "maxValue": 30.0000
  }
}
```

Two kinds of rejection:

```json
{ "status": 400, "message": "Value for 'required_transfer_buffer_min' must be at most 30" }
```

```json
{ "status": 400, "message": "Delay thresholds must be strictly increasing, but would become on_time=9.0, minor=5.0, moderate=15.0. Change rejected." }
```

The second is a cross-field check: each threshold is individually within
bounds, but the combination would make classification meaningless.

---

## Reference data

### `GET /api/vehicles`
### `GET /api/connections`  ·  `?interchangeStopId=` `?structurallyTightOnly=`

`structurallyTightOnly=true` returns connections where
`planned_transfer_min < required_buffer_min` — tight even if every service runs
exactly to time. These are timetable problems, not operational ones, and
fixing them does not require improving punctuality at all.

---

## CORS

`/api/**` allows origins from `transitpulse.cors.allowed-origins`, defaulting
to `http://localhost:3000` and `http://localhost:5173`. Override with
`TRANSITPULSE_CORS_ORIGINS`.

## Authentication

None. The API is unauthenticated by design for local and demonstration use.
Before exposing it, put it behind an authenticating reverse proxy or add
Spring Security.
