# Deploying TransitPulse on AWS EC2

## 1. Launch the instance

| Setting  | Recommended                                      |
|----------|--------------------------------------------------|
| AMI      | Amazon Linux 2023 or Ubuntu 22.04 LTS            |
| Type     | `t3.medium` (2 vCPU, 4 GiB) minimum              |
| Storage  | 30 GiB gp3                                       |
| IAM role | Attach a role with the S3 policy from `../s3/bucket_structure.md` |

`t3.micro` is too small: the Maven and Vite builds will run out of memory.
If you must use one, build the images elsewhere and pull them instead.

## 2. Security group

| Port | Source          | Purpose            |
|------|-----------------|--------------------|
| 22   | your IP only    | SSH                |
| 3000 | your IP / CIDR  | frontend           |
| 8080 | your IP / CIDR  | API and Swagger    |

Do not expose 3306. MySQL is reachable only inside the Docker network.

## 3. Deploy

```bash
ssh -i <key>.pem ec2-user@<public-ip>

curl -fsSL https://raw.githubusercontent.com/<you>/TransitPulse/main/aws/ec2/deploy.sh -o deploy.sh
chmod +x deploy.sh
./deploy.sh https://github.com/<you>/TransitPulse.git
```

The first run creates `.env` from the template and stops. Fill in real
passwords, then run the same command again to start the stack.

## 4. Verify

```bash
curl http://localhost:8080/api/health
curl http://localhost:8080/api/analytics/dashboard | head -40
docker compose ps
```

## 5. Operating it

```bash
cd /opt/transitpulse

docker compose logs -f backend          # follow backend logs
docker compose restart backend          # restart one service
docker compose down                     # stop everything
docker compose run --rm data-engine     # re-run the analytical pipeline

git pull && docker compose up --build -d   # deploy an update
```

## 6. Putting it behind HTTPS

For anything beyond a demo, terminate TLS in front of the stack:

1. Create an Application Load Balancer with an ACM certificate.
2. Target group → instance port 3000 for the UI, 8080 for `/api/*`.
3. Restrict the security group so only the ALB can reach those ports.
4. Set `TRANSITPULSE_CORS_ORIGINS` to the HTTPS origin and restart the backend.

## 7. Cost note

A `t3.medium` running continuously costs roughly US$30/month in most regions,
plus EBS and data transfer. Stop the instance when it is not in use, or run
the stack locally with Docker Compose instead.
