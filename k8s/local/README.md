# Local Kubernetes setup

This directory runs FileDrop on a local Minikube cluster. NGINX Ingress exposes
the Angular frontend at `http://filedrop.test` and sends `/api` requests directly
to the Spring Boot backend.

## Prerequisites

Install and start:

- Docker
- `kubectl`
- Minikube

The manifests currently use the public images
`tuiop1/filedrop-backend:0.1.0` and `tuiop1/filedrop-frontend:0.1.0`.

## First-time setup

Run commands from the repository root.

1. Start Minikube and enable NGINX Ingress:

   ```bash
   minikube start --driver=docker --cpus=2 --memory=4096
   minikube addons enable ingress

   kubectl wait \
     --namespace ingress-nginx \
     --for=condition=Ready pod \
     --selector=app.kubernetes.io/component=controller \
     --timeout=5m
   ```

2. Create the local Secret and replace every `change-me-*` value:

   ```bash
   cp k8s/local/01-secret.yaml.example k8s/local/01-secret.yaml
   $EDITOR k8s/local/01-secret.yaml
   ```

   `k8s/local/01-secret.yaml` is ignored by Git. Values under `stringData` are
   plain text; Kubernetes encodes them when storing the Secret, but this is not
   encryption. These credentials are intended only for local development.
   If the file was staged before the ignore rule was added, untrack it once
   without deleting the local copy:

   ```bash
   git rm --cached k8s/local/01-secret.yaml
   ```

3. Create the namespace and configuration:

   ```bash
   kubectl apply -f k8s/local/00-namespace.yaml
   kubectl apply -f k8s/local/01-configmap.yaml
   kubectl apply -f k8s/local/01-secret.yaml
   ```

4. Start the infrastructure and wait for it:

   ```bash
   kubectl apply \
     -f k8s/local/02-postgres.yaml \
     -f k8s/local/03-redis.yaml \
     -f k8s/local/04-clamav.yaml \
     -f k8s/local/05-minio.yaml

   kubectl wait \
     --namespace filedrop \
     --for=condition=Available \
     deployment/postgres \
     deployment/redis \
     deployment/clamav \
     deployment/minio \
     --timeout=10m
   ```

   ClamAV may need several minutes to download its initial signature database.

5. Create the MinIO bucket and application user:

   ```bash
   kubectl apply -f k8s/local/06-minio-init.yaml
   kubectl wait \
     --namespace filedrop \
     --for=condition=Complete \
     job/minio-init \
     --timeout=5m
   ```

6. Start FileDrop and create the Ingress:

   ```bash
   kubectl apply \
     -f k8s/local/07-backend.yaml \
     -f k8s/local/08-frontend.yaml \
     -f k8s/local/09-ingress.yaml

   kubectl rollout status deployment/filedrop-backend -n filedrop --timeout=5m
   kubectl rollout status deployment/filedrop-frontend -n filedrop --timeout=5m
   ```

7. Map the local hostname. With the Docker-driver setup used by this project,
   edit `/etc/hosts` with `sudoedit /etc/hosts` and add exactly one entry:

   ```text
   127.0.0.1 filedrop.test
   ```

   Do not keep both `127.0.0.1` and the Minikube IP for this hostname. With a
   different Minikube driver that exposes the node directly, use the result of
   `minikube ip` instead of `127.0.0.1`.

8. Verify and open the application:

   ```bash
   kubectl get pods -n filedrop
   curl -I http://filedrop.test
   ```

   All Deployment Pods should be ready, the `minio-init` Pod should be
   `Completed`, and curl should return `HTTP/1.1 200 OK`. Open
   `http://filedrop.test` in a browser. Opening `http://127.0.0.1` directly
   returns the NGINX default 404 because the Ingress rule matches the
   `filedrop.test` host.

## Configuration values

`01-configmap.yaml` contains non-secret local settings:

- `SPRING_DATASOURCE_URL` and `SPRING_DATASOURCE_USERNAME`: PostgreSQL Service.
- `SPRING_DATA_REDIS_URL`: Redis Service.
- `FILEDROP_BASE_URL`: externally visible URL used in generated download links;
  it must match the Ingress hostname and scheme.
- `FILEDROP_S3_ENDPOINT`: internal MinIO Service.
- `FILEDROP_CLAMAV_HOST` and `FILEDROP_CLAMAV_PORT`: internal ClamAV Service.
- `FILEDROP_LOG_LEVEL`: backend log verbosity.

`01-secret.yaml` contains the PostgreSQL password, MinIO administrator
credentials, and the S3 credentials used by FileDrop. `POSTGRES_PASSWORD` and
`SPRING_DATASOURCE_PASSWORD` must be identical. The MinIO initialization Job
creates `FILEDROP_S3_ACCESS_KEY` with `FILEDROP_S3_SECRET_KEY`.

The manifests use the `dev` Spring profile, which includes a development-only
encryption key. Do not reuse this configuration for production.

## Starting it again

After a reboot or `minikube stop`, normally only run:

```bash
minikube start
kubectl get pods -n filedrop
```

Deployments restart automatically, PersistentVolumeClaims retain local data,
the Ingress addon remains enabled, and the `/etc/hosts` entry remains in place.
There is no need to apply every manifest again.

To stop the cluster without deleting its data:

```bash
minikube stop
```

`minikube delete` removes the cluster and its local persistent data. After
deleting it, repeat the complete first-time setup.

## Applying changes

After changing a manifest, apply only that file. For example:

```bash
kubectl apply -f k8s/local/07-backend.yaml
kubectl rollout status deployment/filedrop-backend -n filedrop --timeout=5m
```

For a new application release, use a new image tag, push it, update the
corresponding manifest, and apply it. To build the current Kubernetes images:

```bash
docker login

docker build \
  -f backend/Dockerfile \
  -t YOUR_DOCKERHUB_USER/filedrop-backend:0.1.0 \
  backend

docker build \
  -f frontend/Dockerfile.k8s \
  -t YOUR_DOCKERHUB_USER/filedrop-frontend:0.1.0 \
  frontend

docker push YOUR_DOCKERHUB_USER/filedrop-backend:0.1.0
docker push YOUR_DOCKERHUB_USER/filedrop-frontend:0.1.0
```

Update the `image:` values in `07-backend.yaml` and `08-frontend.yaml` to match.
Prefer a new version tag for every build because both Deployments use
`imagePullPolicy: IfNotPresent`.

## Troubleshooting

```bash
# Watch Pod state
kubectl get pods -n filedrop -w

# Current and previous backend logs
kubectl logs -n filedrop deployment/filedrop-backend --tail=200
kubectl logs -n filedrop \
  "$(kubectl get pod -n filedrop -l app=filedrop-backend \
  -o jsonpath='{.items[0].metadata.name}')" \
  --previous

# Pod and Ingress events
kubectl describe pod -n filedrop \
  "$(kubectl get pod -n filedrop -l app=filedrop-backend \
  -o jsonpath='{.items[0].metadata.name}')"
kubectl describe ingress -n filedrop filedrop
kubectl get events -n filedrop --sort-by=.lastTimestamp
```

If `http://127.0.0.1` shows an NGINX 404, that is expected; use
`http://filedrop.test`. If the hostname does not work, check that `/etc/hosts`
contains only the correct `filedrop.test` mapping and restart the browser to
clear its hostname cache.
