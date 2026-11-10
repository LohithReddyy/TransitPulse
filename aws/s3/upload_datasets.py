#!/usr/bin/env python3
"""
Upload TransitPulse datasets and engine output to S3.

Credentials are never read from this file. boto3 resolves them in the usual
order: environment variables, a shared profile, or the instance/task IAM role.
Prefer an IAM role.

Examples
--------
    # dry run: show what would be uploaded
    python aws/s3/upload_datasets.py --bucket my-bucket --dry-run

    # upload raw datasets and processed engine output
    python aws/s3/upload_datasets.py --bucket my-bucket

    # a specific service date
    python aws/s3/upload_datasets.py --bucket my-bucket --service-date 2026-03-16
"""
from __future__ import annotations

import argparse
import os
import sys
from datetime import date
from pathlib import Path

try:
    import boto3
    from botocore.exceptions import BotoCoreError, ClientError, NoCredentialsError
except ImportError:  # pragma: no cover - dependency guidance
    print("boto3 is required. Install it with:  pip install boto3", file=sys.stderr)
    sys.exit(2)

ROOT = Path(__file__).resolve().parents[2]
DATASETS = ROOT / "datasets"
ENGINE_CSV = ROOT / "data-engine" / "output" / "csv"
ENGINE_JSON = ROOT / "data-engine" / "output" / "json"
MANIFEST = ROOT / "data-engine" / "output" / "manifest.json"


def upload_dir(s3, bucket: str, local_dir: Path, prefix: str,
               pattern: str, dry_run: bool) -> int:
    if not local_dir.is_dir():
        print(f"  skip {local_dir} (not found)")
        return 0
    files = sorted(local_dir.glob(pattern))
    if not files:
        print(f"  skip {local_dir} (no {pattern} files)")
        return 0
    count = 0
    for path in files:
        key = f"{prefix}/{path.name}"
        size_kb = path.stat().st_size / 1024
        if dry_run:
            print(f"  WOULD PUT s3://{bucket}/{key}  ({size_kb:.1f} KiB)")
        else:
            s3.upload_file(
                str(path), bucket, key,
                ExtraArgs={"ServerSideEncryption": "AES256"})
            print(f"  PUT s3://{bucket}/{key}  ({size_kb:.1f} KiB)")
        count += 1
    return count


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--bucket", default=os.getenv("S3_BUCKET"),
                        help="target bucket (or set S3_BUCKET)")
    parser.add_argument("--base-prefix", default="transitpulse")
    parser.add_argument("--service-date", default=str(date.today()),
                        help="partition value for raw data, YYYY-MM-DD")
    parser.add_argument("--run-date", default=str(date.today()),
                        help="partition value for processed data, YYYY-MM-DD")
    parser.add_argument("--region", default=os.getenv("AWS_REGION"))
    parser.add_argument("--profile", default=os.getenv("AWS_PROFILE") or None)
    parser.add_argument("--skip-raw", action="store_true")
    parser.add_argument("--skip-processed", action="store_true")
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()

    if not args.bucket:
        parser.error("a bucket is required: pass --bucket or set S3_BUCKET")

    session_kwargs = {}
    if args.profile:
        session_kwargs["profile_name"] = args.profile
    if args.region:
        session_kwargs["region_name"] = args.region

    try:
        session = boto3.Session(**session_kwargs)
        s3 = session.client("s3")
        if not args.dry_run:
            # Fail fast with a clear message rather than midway through.
            s3.head_bucket(Bucket=args.bucket)
    except NoCredentialsError:
        print("No AWS credentials found. Configure an IAM role, run "
              "`aws configure`, or set AWS_PROFILE.", file=sys.stderr)
        return 1
    except (ClientError, BotoCoreError) as exc:
        print(f"Could not access bucket '{args.bucket}': {exc}", file=sys.stderr)
        return 1

    total = 0
    base = args.base_prefix.strip("/")

    if not args.skip_raw:
        print(f"\nRaw datasets -> service_date={args.service_date}")
        total += upload_dir(
            s3, args.bucket, DATASETS,
            f"{base}/raw/service_date={args.service_date}", "*.csv", args.dry_run)

    if not args.skip_processed:
        print(f"\nProcessed engine output -> run_date={args.run_date}")
        total += upload_dir(
            s3, args.bucket, ENGINE_CSV,
            f"{base}/processed/run_date={args.run_date}", "*.csv", args.dry_run)

        print(f"\nAnalytics payloads -> run_date={args.run_date}")
        total += upload_dir(
            s3, args.bucket, ENGINE_JSON,
            f"{base}/analytics/run_date={args.run_date}", "*.json", args.dry_run)

        if MANIFEST.exists():
            key = f"{base}/manifests/run_date={args.run_date}/manifest.json"
            if args.dry_run:
                print(f"\n  WOULD PUT s3://{args.bucket}/{key}")
            else:
                s3.upload_file(str(MANIFEST), args.bucket, key,
                               ExtraArgs={"ServerSideEncryption": "AES256"})
                print(f"\n  PUT s3://{args.bucket}/{key}")
            total += 1

    verb = "would upload" if args.dry_run else "uploaded"
    print(f"\nDone: {verb} {total} object(s) to s3://{args.bucket}/{base}/")
    if total == 0:
        print("Nothing was found to upload. Run the data engine first:\n"
              "  cd data-engine && python generate_datasets.py && "
              "python -m transitpulse_engine.pipeline")
    return 0


if __name__ == "__main__":
    sys.exit(main())
