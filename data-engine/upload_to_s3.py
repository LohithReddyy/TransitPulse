
import os
from pathlib import Path

import boto3
from botocore.exceptions import ClientError

BUCKET_NAME = "transitpulse-data-lohith-2026"
REGION = "us-east-1"

PROJECT_ROOT = Path(__file__).resolve().parent.parent
DATASETS_DIR = PROJECT_ROOT / "datasets"
OUTPUT_DIR = PROJECT_ROOT / "data-engine" / "output"

session = boto3.Session(region_name=REGION)

s3 = session.client("s3")


def upload_folder(folder: Path, prefix: str):
    if not folder.exists():
        print(f"Folder not found: {folder}")
        return

    for file_path in folder.rglob("*"):
        if not file_path.is_file():
            continue

        relative_path = file_path.relative_to(folder).as_posix()
        s3_key = f"{prefix}/{relative_path}"

        try:
            s3.upload_file(str(file_path), BUCKET_NAME, s3_key)
            print(f"Uploaded: s3://{BUCKET_NAME}/{s3_key}")
        except ClientError as error:
            print(f"Failed to upload {file_path}: {error}")
            raise


if __name__ == "__main__":

    print("\nUploading processed analytics...")
    upload_folder(OUTPUT_DIR / "csv", "processed")
    upload_folder(OUTPUT_DIR / "json", "processed")

    print("\nS3 upload completed.")
