# TransitPulse

**Public Transit Delay Propagation & Passenger Impact Analysis System**
Project CAP-15

TransitPulse helps a transit authority answer a question that is harder than
it looks: when a service runs late, what else does that lateness break?

It compares planned against actual vehicle movements, works out how a delay
travels through the network, estimates how many passengers were exposed to it,
and — importantly — shows the arithmetic and the evidence behind every claim so
an operator can check it rather than take it on trust.

---

## Contents

- [What it does](#what-it-does)
- [How delay propagation is decided](#how-delay-propagation-is-decided)
- [Architecture](#architecture)
- [Technology](#technology)
- [Prerequisites](#prerequisites)
- [Quick start](#quick-start-docker)
- [Running it locally](#running-it-locally)
- [Project structure](#project-structure)
- [API reference](#api-reference)
- [Testing](#testing)
- [Edge cases handled](#edge-cases-handled)
- [Troubleshooting](#troubleshooting)
- [Documentation](#documentation)

---

## What it does

| Capability | What you get |
|------------|--------------|
| **Delay calculation** | Delay at every stop of every trip, classified on configurable thresholds |
| **Propagation analysis** | Which downstream services a delay reached, by what mechanism, with what confidence |
| **Connection risk** | Which passenger transfers were put at risk or missed, and by how much |
| **Passenger impact** | Estimated passengers exposed and passenger-minutes lost, per route and stop |
| **Recovery analysis** | Which late trips caught up, and by how much |
| **Sensitivity testing** | How much of the reported connection risk is an artefact of the buffer assumption |
| **Incident management** | Operator annotations that raise a correlation to a defensible cause |
| **Data quality reporting** | Missing GPS events, diversions and duplicates counted and surfaced, never hidden |

### Nine UI pages

Dashboard · Routes · Stops · Delay Investigation · Connection Risk ·
Incident Management · Passenger Impact · Analytics · Settings

Every page is backed by a live API call; there are no decorative controls.

---

## How delay propagation is decided

This is the heart of the system, so it is worth stating plainly: **a
downstream delay is not assumed to be caused by an upstream one.** Two
services running late in the same hour may share a cause, or may have nothing
to do with each other.

The engine recognises three mechanisms and grades them honestly:

| Mechanism | What links the services | Confidence |
|-----------|------------------------|------------|
| **VEHICLE_CONTINUITY** | The *same physical vehicle* works the next trip. It cannot depart before it arrives plus its turnaround — a hard constraint. | **HIGH** |
| **CONNECTION** | A timetabled transfer at an interchange. The feeder's lateness eats the transfer window. | **MEDIUM**, or HIGH with a corroborating incident |
| **SHARED_STOP** | Two unrelated services were late at the same stop inside the window. | **LOW** — reported explicitly as *correlation*, not cause |

Every emitted relationship carries the evidence behind it and the arithmetic
that produced the number. A vehicle-continuity link, for example, records:

```
forced_delay = (actual_arrival 09:28 + turnaround 5min)
             - scheduled_departure 09:25
             = 8.0 min
```

...together with the facts that support it: the vehicle id, the observed
arrival, the turnaround, and whether the next service *actually* departed late
as predicted. When a prediction does not materialise — the next trip left on
time anyway — the engine **downgrades its own confidence to LOW** rather than
quietly reporting a knock-on that never happened.

Where an operator has annotated an incident covering the route and time, that
annotation is attached to the link and raises its confidence, because human
evidence of a shared external cause is exactly what distinguishes causation
from coincidence.

A full walk-through, with the worked example, is in
[`documentation/DELAY_PROPAGATION_LOGIC.md`](documentation/DELAY_PROPAGATION_LOGIC.md).

---

## Architecture

```
┌──────────────────────────────────────────────────────────────┐
│  React Frontend (Vite)                                       │
│  9 pages · Recharts · Axios                                  │
└───────────────────────────┬──────────────────────────────────┘
                            │ REST /api/**
┌───────────────────────────▼──────────────────────────────────┐
│  Spring Boot REST API                                        │
│  Controllers → Services → Repositories (JPA)                 │
└──────────┬────────────────────────────────┬──────────────────┘
           │                                │ reads JSON
┌──────────▼───────────────┐    ┌───────────▼──────────────────┐
│  MySQL                   │    │  Python Data Engine          │
│  master + analytical     │    │  Pandas / NumPy              │
│  tables, views           │    │                              │
└──────────────────────────┘    │  ingestion → validation      │
                                │  delay calculation           │
┌──────────────────────────┐    │  propagation engine          │
│  AWS S3                  │◄───┤  connection risk engine      │
│  raw + processed data     │    │  passenger impact            │
└──────────┬───────────────┘    │  sensitivity analysis        │
           │                    └───────────┬──────────────────┘
┌──────────▼──────────────────────────────── ▼─────────────────┐
│  Snowflake Data Warehouse                                     │
│  RAW → CORE (star schema) → ANALYTICS (views)                 │
└───────────────────────────┬──────────────────────────────────┘
                            │
┌───────────────────────────▼──────────────────────────────────┐
│  Power BI                                                     │
│  5 report pages built on the ANALYTICS views                  │
└──────────────────────────────────────────────────────────────┘
```

### One deliberate design decision

The **Python engine owns every analytical definition**, and the Spring Boot
API serves its results rather than recomputing them. The alternative —
implementing delay maths in both Java and Python — guarantees the two will
drift apart, and then the dashboard and the warehouse disagree about how many
trips were late. One definition, in one place.

The API does compute delay for the single-trip investigation view, and that
code path is covered by tests asserting it matches the engine's arithmetic
exactly.

Diagrams and data flow: [`documentation/PROJECT_ARCHITECTURE.md`](documentation/PROJECT_ARCHITECTURE.md)

---

## Technology

| Layer | Stack |
|-------|-------|
| Frontend | React 18, Vite 5, React Router 6, Recharts 2, Axios |
| Backend | Java 17, Spring Boot 3.2, Spring Web, Spring Data JPA, Bean Validation, springdoc-openapi |
| Data processing | Python 3.11, Pandas, NumPy, pytest |
| Databases | MySQL 8 (application), H2 (zero-setup dev), Snowflake (warehouse) |
| BI | Power BI Desktop |
| Cloud / DevOps | AWS S3, AWS EC2, Docker, Docker Compose, GitHub Actions |

---

## Prerequisites

| Tool | Version | Needed for |
|------|---------|------------|
| Python | 3.9+ | the data engine |
| Java JDK | 17+ | the backend |
| Maven | 3.8+ | building the backend |
| Node.js | 18+ | the frontend |
| Docker Desktop | latest | the one-command route |
| MySQL | 8.0+ | optional — H2 works out of the box |

Everything runs on Windows, macOS and Linux. Windows commands are given
alongside the Unix ones throughout.

---

## Quick start (Docker)

```bash
git clone <your-repo-url> TransitPulse
cd TransitPulse

cp .env.example .env          # Windows: copy .env.example .env
# Edit .env and set MYSQL_ROOT_PASSWORD and MYSQL_PASSWORD

docker compose up --build
```

Compose starts MySQL, runs the data engine as a batch job, then starts the API
and the UI.

| Service | URL |
|---------|-----|
| Frontend | <http://localhost:3000> |
| API health | <http://localhost:8080/api/health> |
| Swagger UI | <http://localhost:8080/swagger-ui.html> |

```bash
docker compose logs -f                 # follow logs
docker compose run --rm data-engine    # recompute the analytics
docker compose down                    # stop
docker compose down -v                 # stop and delete the database volume
```

---

## Running it locally

Four terminals, or three if you skip MySQL.

### 1 — Data engine (run this first)

The engine produces the analytics the API serves, so it goes first.

```bash
cd data-engine

python -m venv .venv
source .venv/bin/activate          # Windows: .venv\Scripts\activate
pip install -r requirements.txt

python generate_datasets.py        # writes ../datasets/*.csv
python -m transitpulse_engine.pipeline
```

Expected output:

```
=== TransitPulse pipeline complete ===
  service date          2026-03-16
  routes / stops / trips 10 / 55 / 120
  on-time percentage    68.65%
  average delay         2.55 min
  delayed trips         51 (recovered 12, 23.53%)
  propagation links     71 (10 high confidence)
  connections at risk   14 (missed 3)
  passengers affected   11769 (estimate)
  unobserved movements  17
```

### 2 — Backend

```bash
cd backend
mvn spring-boot:run
```

Defaults to the `dev` profile: an in-memory H2 database, seeded from the CSVs
on startup. **No MySQL installation is required to run the project.**

Verify:

```bash
curl http://localhost:8080/api/health
curl http://localhost:8080/api/analytics/dashboard
```

### 3 — Frontend

```bash
cd frontend
npm install
npm run dev
```

Opens on <http://localhost:3000> and proxies `/api` to port 8080.

### 4 — MySQL (optional)

To use MySQL instead of H2:

```bash
mysql -u root -p < database/mysql/01_schema.sql
mysql -u root -p < database/mysql/02_seed_data.sql

cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=mysql
```

Set `MYSQL_USER` and `MYSQL_PASSWORD` in your environment first. To load the
engine's analytical results into MySQL as well, see
`database/mysql/03_load_engine_results.sql`.

Regenerating the seed file after changing the datasets:

```bash
python database/generate_seed_sql.py
```

## Project structure

```
TransitPulse/
├── frontend/                    React application
│   ├── src/
│   │   ├── components/          Layout, tables, cards, badges, charts
│   │   ├── pages/               The nine pages
│   │   ├── services/            API client, formatting, data hook
│   │   ├── styles/              Design tokens and global CSS
│   │   └── __tests__/           Component and client tests
│   └── vite.config.js
│
├── backend/                     Spring Boot application
│   └── src/main/java/com/transitpulse/
│       ├── controller/          7 REST controllers
│       ├── service/             8 services
│       ├── repository/          11 JPA repositories
│       ├── entity/              11 entities
│       ├── dto/                 Request and response objects
│       ├── exception/           Global handler and error types
│       └── config/              CORS and OpenAPI
│
├── data-engine/                 Python analytics
│   ├── transitpulse_engine/
│   │   ├── config.py            Every threshold, in one place
│   │   ├── ingestion.py         Loading and validation
│   │   ├── delay_calculator.py  Delay and classification
│   │   ├── propagation.py       The propagation engine
│   │   ├── connection_risk.py   Transfer risk
│   │   ├── passenger_impact.py  Impact estimation
│   │   ├── sensitivity.py       Assumption testing
│   │   ├── worked_example.py    The hand-checkable example
│   │   └── pipeline.py          Orchestration and export
│   ├── tests/                   72 tests
│   └── generate_datasets.py     Synthetic data generator
│
├── datasets/                    11 relationally consistent CSVs
├── database/
│   ├── mysql/                   Schema, seed, loader, queries
├── powerbi/                     Connection, model, DAX, layout
├── aws/                         S3 layout and upload, EC2 deployment
├── docker/                      Three Dockerfiles and nginx config
├── .github/workflows/           CI pipeline
├── documentation/               Seven detailed documents
├── tests/                       Cross-cutting contract checks
├── monitoring/                   Monitoring metrics
├── docker-compose.yml
├── .env.example
└── README.md
```

---

## API reference

Base URL `http://localhost:8080`. Every response uses one envelope:

```json
{
  "success": true,
  "message": "OK",
  "data": { },
  "timestamp": "2026-03-16T09:15:00"
}
```

### Endpoints

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/api/health` | Liveness check |
| GET | `/api/routes` | Routes with performance; `?search=&mode=` |
| GET | `/api/routes/{id}` | One route |
| GET | `/api/routes/{id}/stops` | Ordered stop pattern |
| GET | `/api/routes/{id}/trips` | Trips on a route |
| GET | `/api/stops` | Stops; `?search=&interchangeOnly=&lowVolumeOnly=` |
| GET | `/api/stops/{id}` | One stop |
| GET | `/api/stops/{id}/passenger-volumes` | Volume profile by time band |
| GET | `/api/trips` | Trips; `?routeId=` |
| GET | `/api/trips/{id}` | One trip |
| GET | `/api/trips/{id}/scheduled-movements` | Planned movements |
| GET | `/api/trips/{id}/actual-movements` | Observed movements |
| GET | `/api/trips/{id}/delays` | Delay at each stop |
| GET | `/api/trips/{id}/investigation` | Full investigation payload |
| GET | `/api/trips/{id}/vehicle-chain` | Trips sharing the vehicle |
| GET | `/api/analytics/dashboard` | Dashboard KPIs |
| GET | `/api/analytics/route-performance` | Per-route metrics |
| GET | `/api/analytics/stop-performance` | Per-stop metrics |
| GET | `/api/analytics/trip-summary` | Per-trip summary |
| GET | `/api/analytics/delay-distribution` | Delay class distribution |
| GET | `/api/analytics/propagation` | Links; `?mechanism=&confidence=&limit=` |
| GET | `/api/analytics/connection-risk` | Risk; `?status=&limit=` |
| GET | `/api/analytics/passenger-impact` | Impact by route and stop |
| GET | `/api/analytics/sensitivity` | Buffer sensitivity |
| GET | `/api/analytics/recovery` | Recovery analytics |
| GET | `/api/analytics/worked-example` | The worked example |
| GET | `/api/analytics/validation` | Data quality report |
| GET | `/api/analytics/engine-status` | Whether analytics exist |
| GET | `/api/incidents` | Incidents; `?routeId=&status=` |
| GET | `/api/incidents/diversions` | Diversions only |
| GET | `/api/incidents/{id}` | One incident |
| POST | `/api/incidents` | Create an annotation |
| PUT | `/api/incidents/{id}` | Update |
| DELETE | `/api/incidents/{id}` | Delete |
| GET | `/api/config` | Parameters; `?category=` |
| GET | `/api/config/grouped` | Grouped by category |
| GET | `/api/config/{key}` | One parameter |
| PUT | `/api/config/{key}` | Update a parameter |
| GET | `/api/vehicles` | Fleet |
| GET | `/api/connections` | Transfers; `?interchangeStopId=&structurallyTightOnly=` |

### Examples

```bash
# Dashboard KPIs
curl http://localhost:8080/api/analytics/dashboard

# Only propagation the engine is confident about
curl "http://localhost:8080/api/analytics/propagation?confidence=HIGH&limit=10"

# Investigate one trip end to end
curl http://localhost:8080/api/trips/T0001/investigation

# Record a disruption
curl -X POST http://localhost:8080/api/incidents \
  -H "Content-Type: application/json" \
  -d '{
    "incidentType": "TRAFFIC_CONGESTION",
    "routeId": "R01",
    "stopId": "S007",
    "startTime": "2026-03-16T09:00:00",
    "endTime": "2026-03-16T10:30:00",
    "severity": "HIGH",
    "description": "Congestion following a collision near Civic Centre"
  }'

# Widen the transfer buffer and see the effect
curl -X PUT http://localhost:8080/api/config/required_transfer_buffer_min \
  -H "Content-Type: application/json" \
  -d '{"configValue": "8.0"}'
```

Interactive docs: <http://localhost:8080/swagger-ui.html>

Full reference: [`documentation/API_DOCUMENTATION.md`](documentation/API_DOCUMENTATION.md)

### Demo authentication

There is none, by design. The API is unauthenticated and intended for local
and demonstration use. Before exposing it to a network, put it behind an
authenticating reverse proxy or add Spring Security — see the hardening notes
in `documentation/AWS_DEPLOYMENT.md`.

---

## Testing

```bash
# Python engine — 72 tests
cd data-engine && python -m pytest tests/ -v

# Backend — 34 tests
cd backend && mvn test

# Frontend — 31 tests
cd frontend && npm test

# Cross-cutting checks
pip install sqlglot
python tests/validate_sql.py          # all 10 SQL files parse
python tests/verify_api_contract.py   # frontend calls match backend routes
```

**137 automated tests**, all passing. What they cover:

- delay arithmetic and classification boundaries
- missing GPS never becoming a zero delay, in either strategy
- diverted stops excluded from schedule comparison
- propagation mechanisms, evidence, confidence and de-duplication
- connection risk, including the unobserved-feeder case
- passenger impact and low-volume confidence marking
- sensitivity monotonicity
- the worked example reproducing the specification's numbers
- API contract, validation and error handling
- null rendering in the UI (an em dash, never a zero)

---

## Edge cases handled

These are the cases where a naive implementation produces confidently wrong
numbers, so each is handled explicitly and covered by tests.

### 1. Missing GPS events

A stop with no arrival event is recorded as `MISSING`, not as zero delay.
`actual_arrival` is nullable all the way through — CSV, MySQL, Snowflake, JPA
entity, DTO and UI — and such rows are excluded from every average. Treating
them as on time would systematically flatter performance, and the effect is
largest exactly where telemetry is worst.

Two strategies are configurable:

- `EXCLUDE` (default) — leave the gap out of the statistics.
- `ESTIMATE` — interpolate between the neighbouring *observed* stops and label
  the result `ESTIMATED`. Interpolation never extrapolates past the first or
  last observation, because there is nothing to interpolate between.

### 2. Route diversions

A diverted trip did not serve its planned stops, so comparing it against the
planned schedule would invent delays that never happened. Diverted records are
flagged `DIVERTED_SKIPPED` and excluded from stop-to-stop comparison, while the
rest of the trip is still analysed normally.

### 3. Low-volume stops

A stop with eight boardings can show a 100% delay rate from a single late bus.
Those stops are marked `LOW` confidence rather than hidden, impact estimates
derived from them carry a confidence field, and the Stops page reports how many
of the rows on screen are low-volume.

### 4. Delay recovery

A trip is "recovered" only if it was genuinely delayed first, then reduced its
delay below the on-time threshold by its last observed stop. Recovery
percentage is `recovered ÷ delayed`, never `recovered ÷ all trips`.

*This was a real bug during development:* the first implementation marked a
trip recovered whenever its delay fell, including trips that were never late,
which produced recovery rates above 100%. The definition was tightened and a
regression test now asserts recovery can never exceed 100%.

### 5. Correlation versus causation

Covered in [How delay propagation is decided](#how-delay-propagation-is-decided).
In short: three mechanisms, graded confidence, evidence attached to every
link, self-downgrading when a prediction fails, and `SHARED_STOP` links
labelled as correlation in the data, the API response and the UI.

### 6. Duplicate records

Deduplicated on business keys at ingestion, with the count reported rather
than silently dropped. The sample data genuinely exercises this: a loop route
serves one stop twice, which makes the natural passenger-volume key repeat.

---

## Troubleshooting

| Symptom | Cause and fix |
|---------|---------------|
| `Analytics payload 'dashboard' has not been generated yet` | The engine has not run. `cd data-engine && python -m transitpulse_engine.pipeline` |
| Dashboard shows counts but no delay figures | Same cause; the UI shows a banner with the command. Network counts come from the database, analytics from the engine. |
| `Missing dataset files … Run generate_datasets.py first` | `cd data-engine && python generate_datasets.py` |
| `Could not reach the TransitPulse API` in the UI | Backend is not running. `cd backend && mvn spring-boot:run` |
| Backend starts with an empty database | The CSVs were not found. Generate them, or set `TRANSITPULSE_DATASETS_DIR` to their location. |
| `Table 'transitpulse.routes' doesn't exist` (MySQL profile) | Run `01_schema.sql` then `02_seed_data.sql`. |
| `Access denied for user` (MySQL) | `MYSQL_USER` / `MYSQL_PASSWORD` not set, or the user lacks grants on `transitpulse`. |
| Port 8080 already in use | `SERVER_PORT=8081 mvn spring-boot:run`, and set `VITE_API_TARGET` to match. |
| Port 3000 already in use | `npm run dev -- --port 3001` |
| `mvn: command not found` | Install Maven, or use the IDE's bundled Maven. |
| Frontend build runs out of memory | `NODE_OPTIONS=--max-old-space-size=4096 npm run build` |
| Docker: `set MYSQL_PASSWORD in .env` | Compose requires real values; copy `.env.example` to `.env` and fill them in. |
| Docker: backend restarts repeatedly | MySQL is still initialising. Compose waits for health, but on a slow machine give it a minute, then `docker compose logs backend`. |
| Delay thresholds rejected on save | They must stay strictly increasing (`on_time < minor < moderate`). The error message names the values it refused. |
| Changed a setting but the charts look the same | Settings are stored immediately but the analytics are recomputed by the engine. Re-run the pipeline. |

---

## Documentation

| Document | Covers |
|----------|--------|
| [`PROJECT_ARCHITECTURE.md`](documentation/PROJECT_ARCHITECTURE.md) | Components, data flow, design decisions and trade-offs |
| [`DATABASE_SCHEMA.md`](documentation/DATABASE_SCHEMA.md) | Every table, column, key and index, MySQL and Snowflake |
| [`API_DOCUMENTATION.md`](documentation/API_DOCUMENTATION.md) | Every endpoint with request and response examples |
| [`DELAY_PROPAGATION_LOGIC.md`](documentation/DELAY_PROPAGATION_LOGIC.md) | The algorithm, the worked example, and the causation argument |
| [`SAMPLE_SCENARIOS.md`](documentation/SAMPLE_SCENARIOS.md) | Guided walk-throughs against the shipped data |
| [`POWER_BI_SETUP.md`](documentation/POWER_BI_SETUP.md) | Pointer into the `powerbi/` pack |
| [`AWS_DEPLOYMENT.md`](documentation/AWS_DEPLOYMENT.md) | S3, EC2, hardening and cost |

---

## A note on the passenger numbers

Every passenger figure this system produces is a **modelled estimate** built
from average boardings and average on-board load. None of them counts
individual passengers.

That is a limitation of the input data, not a presentation choice, and it is
why the word *estimate* appears on the KPI cards, in the API responses, in the
database column names, in the Power BI measure names, and here. Confirming how
many people were actually affected by a delay would require ticketing or
automatic passenger counting data, which this system does not have.

An estimate is still useful — it ranks routes by harm far better than average
delay does, because a slightly late full bus affects more people than a very
late empty one. It just should not be quoted as a measurement.

---

## Licence

MIT. The datasets are synthetic and contain no real passenger or operational
data.
