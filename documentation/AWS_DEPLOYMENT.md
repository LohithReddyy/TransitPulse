# AWS deployment

Detailed material is in [`../aws/`](../aws/); this page ties it together and
covers hardening.

| Topic | Where |
|-------|-------|
| S3 bucket layout, lifecycle, IAM policy | [`aws/s3/bucket_structure.md`](../aws/s3/bucket_structure.md) |
| Upload script | [`aws/s3/upload_datasets.py`](../aws/s3/upload_datasets.py) |
| EC2 deployment script | [`aws/ec2/deploy.sh`](../aws/ec2/deploy.sh) |
| EC2 instructions | [`aws/ec2/README.md`](../aws/ec2/README.md) |

## Credentials

No credential appears anywhere in this repository, and `.env` is gitignored.

In order of preference:

1. **IAM role** attached to the EC2 instance — nothing to rotate or leak.
2. **AWS profile** on a developer machine (`aws configure`, then
   `AWS_PROFILE`).
3. Environment variables, for CI.

Never commit access keys. If one is exposed, deactivate it in IAM immediately
rather than deleting the user, so you can audit what it did.

## S3

```bash
aws s3api create-bucket --bucket my-transitpulse \
  --region eu-west-2 --create-bucket-configuration LocationConstraint=eu-west-2

aws s3api put-public-access-block --bucket my-transitpulse \
  --public-access-block-configuration \
  "BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true"

aws s3api put-bucket-encryption --bucket my-transitpulse \
  --server-side-encryption-configuration \
  '{"Rules":[{"ApplyServerSideEncryptionByDefault":{"SSEAlgorithm":"AES256"}}]}'

aws s3api put-bucket-versioning --bucket my-transitpulse \
  --versioning-configuration Status=Enabled
```

Upload:

```bash
pip install boto3

python aws/s3/upload_datasets.py --bucket my-transitpulse --dry-run
python aws/s3/upload_datasets.py --bucket my-transitpulse
```

The dry run prints every key it would write. Use it first: it is also the
quickest way to confirm your credentials resolve.

## EC2

Full steps in [`aws/ec2/README.md`](../aws/ec2/README.md). In short:

```bash
ssh ec2-user@<public-ip>
curl -fsSL <raw-url>/aws/ec2/deploy.sh -o deploy.sh && chmod +x deploy.sh
./deploy.sh https://github.com/<you>/TransitPulse.git
```

The first run installs Docker, clones the repository, creates `.env` from the
template and **stops**, so you cannot accidentally start MySQL with a
placeholder password. Fill in real values and run it again.

The script also refuses to start if `.env` still contains `change_me`.

Use at least a `t3.medium`. On a `t3.micro` the Maven and Vite builds run out
of memory.

## Hardening before any real exposure

This project ships without authentication, which is appropriate for local
evaluation and not for anything else.

1. **Add authentication.** Spring Security with OIDC, or an authenticating
   reverse proxy. The API currently allows anyone who can reach it to create
   and delete incident annotations.
2. **Terminate TLS** at an ALB with an ACM certificate. Restrict the instance
   security group so only the ALB can reach ports 3000 and 8080.
3. **Never expose 3306.** MySQL should stay inside the Docker network. For
   production, use RDS in a private subnet.
4. **Set `TRANSITPULSE_CORS_ORIGINS`** to your exact HTTPS origin. The default
   permits localhost, which is wrong once deployed.
5. **Rotate the database password** away from whatever was used to bootstrap,
   and store it in Secrets Manager or SSM Parameter Store rather than `.env`.
6. **Turn off the H2 console** by keeping the `docker` or `mysql` profile
   active. It is enabled only in `dev`.
7. **Back up.** `docker compose down -v` deletes the MySQL volume. Use RDS
   with automated backups, or snapshot the volume.

## Snowflake from S3

Rather than uploading with SnowSQL `PUT`, load from S3 with a storage
integration — commented out at the end of
`database/snowflake/01_database_setup.sql`. It uses an IAM role, so no key
is ever stored in Snowflake.

## Cost

| Item | Rough monthly cost |
|------|-------------------|
| EC2 `t3.medium`, always on | ~US$30 |
| EBS 30 GiB gp3 | ~US$3 |
| S3 for this dataset | pennies |
| Snowflake XSMALL, auto-suspend 60s | a few dollars at demo usage |

Stop the instance when it is not in use. For a demonstration, running the
stack locally with Docker Compose costs nothing at all.
