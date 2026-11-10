# Project architecture

## 1. Components

| Component | Technology | Responsibility |
|-----------|-----------|----------------|
| Frontend | React 18 + Vite | Nine pages; renders analytics and captures incident annotations |
| Backend | Spring Boot 3.2 | REST API; master data from the database, analytics from the engine |
| Data engine | Python + Pandas | Every analytical definition: delay, propagation, risk, impact |
| Application DB | MySQL 8 / H2 | Routes, stops, trips, movements, incidents, configuration |
| Warehouse | Snowflake | Star schema and the Power BI facing views |
| BI | Power BI | Five report pages |
| Object store | AWS S3 | Raw and processed data, partitioned by date |

## 2. Data flow

```
                       ┌──────────────────────┐
                       │  datasets/*.csv      │
                       │  (11 source files)   │
                       └──────────┬───────────┘
                                  │
         ┌────────────────────────┼────────────────────────┐
         │                        │                        │
         ▼                        ▼                        ▼
┌────────────────┐    ┌──────────────────────┐   ┌──────────────────┐
│ MySQL          │    │ Python data engine   │   │ AWS S3 raw/      │
│ (seed scripts) │    │                      │   │ service_date=... │
└───────┬────────┘    │ 1 ingestion          │   └──────────────────┘
        │             │   + validation       │
        │             │ 2 delay calculation  │
        │             │ 3 propagation        │
        │             │ 4 connection risk    │
        │             │ 5 passenger impact   │
        │             │ 6 sensitivity        │
        │             └──────┬───────────────┘
        │                    │
        │        ┌───────────┴────────────┐
        │        ▼                        ▼
        │  ┌──────────────┐      ┌─────────────────┐
        │  │ output/json  │      │ output/csv      │
        │  │ (API serves) │      │ (SQL loads)     │
        │  └──────┬───────┘      └────────┬────────┘
        │         │                       │
        │         │              ┌────────┴─────────┐
        │         │              ▼                  ▼
        │         │     ┌────────────────┐  ┌─────────────────┐
        │         │     │ MySQL          │  │ Snowflake RAW   │
        │         │     │ analytical     │  │   → CORE        │
        │         │     │ tables         │  │   → ANALYTICS   │
        │         │     └────────────────┘  └────────┬────────┘
        │         │                                  │
        ▼         ▼                                  ▼
┌──────────────────────────┐              ┌──────────────────┐
│  Spring Boot REST API    │              │  Power BI        │
└────────────┬─────────────┘              └──────────────────┘
             │
             ▼
┌──────────────────────────┐
│  React frontend          │
└──────────────────────────┘
```

## 3. Backend layering

```
Controller   HTTP concerns only: routing, status codes, validation triggers
    ↓
Service      Business logic and orchestration
    ↓
Repository   Spring Data JPA; derived queries and a few explicit JPQL ones
    ↓
Entity       JPA mappings matching the SQL schema exactly
```

Seven controllers, eight services, eleven repositories, eleven entities.

### Services

| Service | Responsibility |
|---------|----------------|
| `RouteService` | Routes, enriched with engine performance metrics |
| `StopService` | Stops, volume, and low-confidence marking |
| `DelayAnalysisService` | Single-trip delay and investigation |
| `AnalyticsService` | Dashboard, propagation, risk, impact, sensitivity, recovery |
| `IncidentService` | CRUD and validation for annotations |
| `ConfigurationService` | Parameters, bounds, and threshold ordering |
| `EngineOutputService` | Reads and caches the engine's JSON payloads |
| `DatasetLoaderService` | Seeds the dev database from the CSVs on startup |

## 4. Design decisions

### 4.1 The engine owns the analytics

**Decision:** analytical definitions live in Python; the API serves the
results.

**Why:** implementing delay classification, propagation and impact in both
Java and Python guarantees the two drift. When they drift, the dashboard and
the warehouse disagree about how many trips were late, and there is no way to
tell which is right. One definition in one place removes that class of bug.

**Cost:** the API depends on the engine having run. Handled explicitly —
`/api/analytics/*` returns HTTP 503 with the exact command to run, the
dashboard falls back to database counts and shows a banner, and nothing
silently returns an empty result that could be mistaken for "no delays".

**Exception:** `DelayAnalysisService` computes delay for the single-trip
investigation, because round-tripping one trip through the engine would be
absurd. That path is covered by tests asserting it matches the engine's
arithmetic.

### 4.2 Nullable actual arrivals, end to end

**Decision:** `actual_arrival` is nullable in the CSV, MySQL, Snowflake, the
JPA entity, the DTO and the UI.

**Why:** the alternative — a sentinel value or zero — makes a missing GPS event
indistinguishable from an on-time arrival. That bias is not random: it is
largest where telemetry is worst, so the worst-instrumented routes would
appear to perform best.

**Cost:** null handling everywhere. Contained by one rule, tested in both the
engine and the UI: **null renders as an em dash, never as zero.**

### 4.3 H2 by default

**Decision:** the `dev` profile uses in-memory H2, seeded from the CSVs.

**Why:** the project can be cloned and run with no database installation. For
a system being evaluated, "clone and run" matters.

**Cost:** two schema paths. Mitigated by `ddl-auto: validate` on the MySQL
profile, so the entities are checked against the real schema rather than
quietly adapting to it.

### 4.4 Graded confidence instead of binary propagation

**Decision:** every propagation link carries a mechanism, evidence and a
confidence level, and the engine downgrades itself when a prediction fails.

**Why:** a binary "this delay caused that one" would be wrong often enough to
discredit the whole tool the first time an operator checked it against what
they knew. Showing the reasoning lets a user disagree with a specific link
rather than distrusting everything.

**Cost:** more complex output and a UI that has to explain itself. Worth it:
this is the feature that makes the analysis defensible.

### 4.5 Analytics served from JSON rather than re-queried

**Decision:** `EngineOutputService` reads JSON files, caching on modified
time.

**Why:** the payloads are small and change only when the pipeline runs.
Re-deriving them per request would add latency and a second implementation.

**Cost:** the API and the engine must share a filesystem. True in local
development and in Docker Compose (a shared volume). For a distributed
deployment, the same payloads would come from S3 — which is why they are
written there too.

## 5. Failure behaviour

| Failure | Behaviour |
|---------|-----------|
| Engine has not run | 503 with the command to run; dashboard degrades to database counts and warns |
| Datasets missing | `FileNotFoundError` naming the directory and the generator command |
| Database empty | API starts and serves empty collections; the loader logs where it looked |
| Orphan foreign keys in CSV | Row skipped and counted; load continues |
| Duplicate business keys | Last record wins; count reported in the validation payload |
| Unparseable timestamp | Treated as absent and counted, never as epoch zero |
| Invalid configuration | 400 with the specific bound or ordering that was violated |
| Unknown entity | 404 naming the id that was not found |

## 6. Testing strategy

| Layer | Count | Focus |
|-------|-------|-------|
| Python | 72 | Arithmetic, edge cases, propagation semantics, pipeline integration |
| Backend | 34 | Service logic, API contract, validation, error mapping |
| Frontend | 31 | Component rendering, null handling, API client error surfacing |
| Contract | 2 | All SQL parses; every frontend call matches a backend route |

**139 checks total.** The emphasis is on the cases where a plausible
implementation is confidently wrong: missing data becoming zero, recovery
without a prior delay, double-counted propagation, risk percentages computed
over unassessable connections.

## 7. Scaling notes

The shipped dataset is one service day: 120 trips, 792 movements. For a real
network:

- **Ingestion** is vectorised Pandas and handles a few million movements in
  memory. Beyond that, partition by service date and process per day.
- **Propagation** is the expensive stage. Vehicle continuity is O(trips);
  shared-stop comparison is O(movements × window) and would need spatial or
  temporal indexing at scale.
- **The API** caches payloads in memory. At real volume the per-trip endpoints
  should query the warehouse directly rather than filtering JSON.
- **Snowflake** is where the historical analysis belongs; the star schema is
  already partition-friendly through `date_key`.
