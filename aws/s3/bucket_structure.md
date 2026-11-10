# TransitPulse S3 bucket layout

```
s3://<your-bucket>/
└── transitpulse/
    ├── raw/                         # immutable source data, partitioned by date
    │   └── service_date=YYYY-MM-DD/
    │       ├── routes.csv
    │       ├── stops.csv
    │       ├── route_stops.csv
    │       ├── vehicles.csv
    │       ├── trips.csv
    │       ├── scheduled_movements.csv
    │       ├── actual_movements.csv
    │       ├── passenger_volumes.csv
    │       ├── connections.csv
    │       ├── incidents.csv
    │       └── diversions.csv
    │
    ├── processed/                   # engine output, ready for Snowflake
    │   └── run_date=YYYY-MM-DD/
    │       ├── delay_events.csv
    │       ├── trip_delay_summary.csv
    │       ├── route_performance.csv
    │       ├── stop_performance.csv
    │       ├── propagation_results.csv
    │       ├── connection_risk.csv
    │       ├── passenger_impact.csv
    │       ├── passenger_impact_by_route.csv
    │       ├── passenger_impact_by_stop.csv
    │       └── sensitivity_analysis.csv
    │
    ├── analytics/                   # JSON payloads served by the API
    │   └── run_date=YYYY-MM-DD/
    │       └── *.json
    │
    └── manifests/
        └── run_date=YYYY-MM-DD/manifest.json
```

## Why this layout

Partitioning by date keeps every run reproducible: a result can always be
traced back to the exact inputs it came from, which matters when a propagation
claim is challenged. `raw/` is write-once; re-running the engine only ever adds
a new `processed/` partition.

## Lifecycle policy

A suggested policy, applied per prefix:

| Prefix       | Transition to Standard-IA | Transition to Glacier | Expiry   |
|--------------|---------------------------|-----------------------|----------|
| `raw/`       | 30 days                   | 90 days               | never    |
| `processed/` | 30 days                   | 180 days              | 2 years  |
| `analytics/` | never                     | never                 | 90 days  |

## Security

- Block all public access on the bucket.
- Enable default encryption (SSE-S3, or SSE-KMS with a customer managed key).
- Enable versioning on `raw/` so a bad overwrite is recoverable.
- Grant access with an IAM role, never with long-lived access keys in code.

Minimum IAM policy for the upload scripts:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "TransitPulseObjectAccess",
      "Effect": "Allow",
      "Action": ["s3:PutObject", "s3:GetObject", "s3:DeleteObject"],
      "Resource": "arn:aws:s3:::<your-bucket>/transitpulse/*"
    },
    {
      "Sid": "TransitPulseListBucket",
      "Effect": "Allow",
      "Action": "s3:ListBucket",
      "Resource": "arn:aws:s3:::<your-bucket>",
      "Condition": {
        "StringLike": { "s3:prefix": "transitpulse/*" }
      }
    }
  ]
}
```
