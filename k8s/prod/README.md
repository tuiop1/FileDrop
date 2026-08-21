# Production Kubernetes on EC2

These manifests run FileDrop on a single EC2 instance using K3s, its bundled
Traefik Ingress controller, local PostgreSQL/Redis/ClamAV Pods, and an external
Amazon S3 bucket.

## Before deploying

- Use an EC2 instance with at least 2 vCPUs and 8 GiB RAM.
- Install K3s with Traefik and Secret encryption enabled.
- Allow inbound TCP 80 and 443 in the EC2 Security Group. Restrict SSH to your
  IP or use AWS Systems Manager.
- Assign a stable Elastic IP and preferably a DNS name.
- Create the S3 bucket configured by `FILEDROP_S3_BUCKET` in the configured
  `FILEDROP_S3_REGION`, with Block Public Access enabled.
- Attach an IAM instance role to EC2 that can access objects in that bucket.
- Configure EC2 metadata for IMDSv2 with a response hop limit of 2 so Pods can
  obtain the instance-role credentials.

The backend uses the AWS SDK default credential chain. Do not put long-lived
AWS access keys in `01-secret.yaml`.

A minimal instance-role policy is:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": ["s3:GetBucketLocation", "s3:ListBucket"],
      "Resource": "arn:aws:s3:::filedrop-prod-jieksaoes"
    },
    {
      "Effect": "Allow",
      "Action": ["s3:GetObject", "s3:PutObject", "s3:DeleteObject"],
      "Resource": "arn:aws:s3:::filedrop-prod-jieksaoes/*"
    }
  ]
}
```

Replace the bucket name in both policy resources when using another bucket.

## First deployment

Run these commands from the repository root with `kubectl` configured for the
EC2 K3s cluster.

1. Edit `01-configmap.yaml` and set:

   - `FILEDROP_BASE_URL` to the public URL, including `http://` or `https://`.
   - `FILEDROP_S3_REGION` to the bucket's AWS Region.
   - `FILEDROP_S3_BUCKET` to the existing private bucket.
   - The same hostname under `rules[].host` in `09-ingress.yaml`.

2. Create the real Secret and replace every `change-me-*` value:

   ```bash
   cp k8s/prod/01-secret.yaml.example k8s/prod/01-secret.yaml
   $EDITOR k8s/prod/01-secret.yaml
   ```

   Generate independent PostgreSQL and Redis passwords with:

   ```bash
   openssl rand -hex 32
   ```

   Generate the required encryption master key with:

   ```bash
   openssl rand -base64 32
   ```

   `POSTGRES_PASSWORD` and `SPRING_DATASOURCE_PASSWORD` must contain the same
   value. Keep `FILEDROP_MASTER_KEY` stable and backed up securely.

   The real Secret is ignored by Git. If it was staged before the ignore rule,
   untrack it without deleting the local file:

   ```bash
   git restore --staged -- k8s/prod/01-secret.yaml
   ```

3. Create the namespace and configuration:

   ```bash
   kubectl apply -f k8s/prod/00-namespace.yaml
   kubectl apply -f k8s/prod/01-configmap.yaml
   kubectl apply -f k8s/prod/01-secret.yaml
   ```

4. Start infrastructure and wait for it. ClamAV can take several minutes to
   download its initial signatures:

   ```bash
   kubectl apply \
     -f k8s/prod/02-postgres.yaml \
     -f k8s/prod/03-redis.yaml \
     -f k8s/prod/04-clamav.yaml

   kubectl wait \
     --namespace filedrop \
     --for=condition=Available \
     deployment/postgres \
     deployment/redis \
     deployment/clamav \
     --timeout=10m
   ```

5. Deploy FileDrop and its Ingress:

   ```bash
   kubectl apply \
     -f k8s/prod/07-backend.yaml \
     -f k8s/prod/08-frontend.yaml \
     -f k8s/prod/09-ingress.yaml

   kubectl rollout status deployment/filedrop-backend -n filedrop --timeout=5m
   kubectl rollout status deployment/filedrop-frontend -n filedrop --timeout=5m
   ```

6. Point the public DNS `A` record at the EC2 Elastic IP, then verify:

   ```bash
   kubectl get pods,services,ingress -n filedrop
   curl -I http://filedrop-prod.xyz
   ```

   Replace `filedrop-prod.xyz` with the configured domain. There is no local
   `/etc/hosts` mapping in production.

## Verify the EC2 role

Run an AWS CLI Pod to confirm that Pods can obtain the EC2 instance role:

```bash
kubectl run aws-identity \
  --namespace filedrop \
  --rm -i \
  --restart=Never \
  --image=amazon/aws-cli \
  -- sts get-caller-identity
```

If this cannot find credentials, verify that the IAM instance profile is
attached, IMDS is enabled, IMDSv2 is required, and its response hop limit is 2.

## Starting it again

K3s normally starts automatically after an EC2 reboot and recreates the Pods.
Usually you only need to check status:

```bash
sudo systemctl status k3s
kubectl get pods -n filedrop
```

If K3s was stopped manually:

```bash
sudo systemctl start k3s
```

Do not reapply every manifest after a normal restart. Apply a manifest only
when it changes:

```bash
kubectl apply -f k8s/prod/07-backend.yaml
kubectl rollout status deployment/filedrop-backend -n filedrop --timeout=5m
```

Use a new immutable image tag for every backend or frontend release, update the
corresponding `image:` field, and apply that Deployment.

## Troubleshooting

```bash
kubectl get pods -n filedrop -w
kubectl logs -n filedrop deployment/filedrop-backend --tail=200
kubectl logs -n filedrop deployment/redis --tail=100
kubectl describe ingress -n filedrop filedrop
kubectl get events -n filedrop --sort-by=.lastTimestamp
kubectl get ingressclass,storageclass
kubectl get pvc -n filedrop
```

The PVCs use K3s local-path storage. They survive Pod replacement and normally
survive EC2 stop/start, but they are tied to that instance's disk. Back up
PostgreSQL and `FILEDROP_MASTER_KEY`; for stronger production durability, use
RDS PostgreSQL or an EBS CSI-backed StorageClass.

The current Ingress is HTTP-only. Configure a real domain and TLS through
Traefik/cert-manager before sending passwords or files over the public internet.
