# FileDrop

### Secure file sharing backend with encrypted cloud storage, malware scanning, and controlled file access

[![Java](https://img.shields.io/badge/Java-25-orange?logo=openjdk)](https://www.java.com/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-Backend-6DB33F?logo=springboot\&logoColor=white)](https://spring.io/projects/spring-boot)
[![AWS](https://img.shields.io/badge/AWS-EC2%20%7C%20S3-FF9900?logo=amazonwebservices\&logoColor=white)](https://aws.amazon.com/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-Database-4169E1?logo=postgresql\&logoColor=white)](https://www.postgresql.org/)
[![Docker](https://img.shields.io/badge/Docker-Containerized-2496ED?logo=docker\&logoColor=white)](https://www.docker.com/)
[![Kubernetes](https://img.shields.io/badge/Kubernetes-K3s%20%7C%20Minikube-326CE5?logo=kubernetes\&logoColor=white)](https://kubernetes.io/)
[![CI](https://github.com/tuiop1/FileDrop/actions/workflows/testandbuild.yml/badge.svg)](https://github.com/tuiop1/FileDrop/actions/workflows/testandbuild.yml)

FileDrop is a backend-focused file-sharing application that allows users to upload files and share them through temporary download links without creating an account.

Uploaded files pass through validation, content detection, malware scanning, integrity verification, and **AES-GCM encryption** before being stored in **Amazon S3**. The application is deployed on **Amazon EC2** and uses PostgreSQL for metadata and Redis for rate limiting.

---

## Project Walkthrough

[![FileDrop Project Walkthrough](https://img.youtube.com/vi/nlauuOm-8DM/maxresdefault.jpg)](https://www.youtube.com/watch?v=nlauuOm-8DM)

---

## Features

* Upload and share files through unique download links
* Separate **download** and **management** access
* Configurable expiration time and download limits
* Optional password protection for downloads
* Server-side file type detection with **Apache Tika**
* Malware scanning with **ClamAV**
* **AES-GCM encryption** before permanent storage
* Encrypted object storage in **Amazon S3**
* File integrity verification using cryptographic checksums
* Secure token-based access
* **Redis-backed rate limiting**
* Automatic cleanup of expired and exhausted drops
* Streaming downloads for efficient file handling
* Dockerized local and production environments
* Kubernetes configurations for local **Minikube** and production **K3s** deployments

---

## Architecture

FileDrop is structured as a **modular Spring Boot application**, with separate responsibilities for:

* file upload, download, and lifecycle management
* storage
* encryption
* malware scanning and content validation
* integrity verification
* access management
* shared infrastructure

The storage layer is abstracted from the business logic, allowing different storage implementations between development and production environments.

In production:

* **Amazon EC2** hosts the application
* **Amazon S3** stores encrypted file objects
* **PostgreSQL** stores file metadata
* **Redis** handles rate limiting
* **ClamAV** scans uploaded content

<!-- Architecture diagram will be added here -->

---

## File Processing Pipeline

Uploaded files are treated as untrusted input and must pass through the processing pipeline before becoming available.

### Upload

1. The client uploads a file with expiration, download-limit, and optional password settings.
2. Request and file constraints are validated.
3. The file is written to temporary storage.
4. Its actual content type is detected using Apache Tika.
5. ClamAV scans the file for malware.
6. A cryptographic checksum is calculated.
7. Encryption metadata and a data key are generated.
8. The file is encrypted using AES-GCM.
9. The encrypted object is uploaded to Amazon S3.
10. File metadata and access information are stored in PostgreSQL.
11. Download and management links are returned to the client.

### Download

For each download request, FileDrop:

1. validates the download token;
2. checks expiration and remaining downloads;
3. validates the password when password protection is enabled;
4. retrieves the encrypted object from storage;
5. decrypts the file while streaming it;
6. returns the original file to the client;
7. updates the remaining download count.

Files are processed using streams rather than loading the complete content into JVM memory.

---

## Security

### Content validation

The backend detects the actual file type using **Apache Tika** instead of trusting only the MIME type supplied by the client.

### Malware scanning

Uploaded files are scanned with **ClamAV** before they are transferred to permanent storage.

### Encryption

File contents are encrypted using **AES-GCM** before being uploaded to Amazon S3.

AES-GCM provides both confidentiality and authentication of the encrypted data.

### Access control

FileDrop does not require user accounts.

Each upload generates separate credentials for:

* downloading the file
* managing the file

Management operations require a dedicated management token, while normal downloads use the public download token.

Files can also be protected with an optional download password.

### Rate limiting

Public endpoints are protected by **Redis-backed rate limiting** to reduce abuse of upload and download operations.

---

## Technology Stack

### Backend

* **Java 25**
* **Spring Boot**
* **Spring Modulith**
* Spring MVC
* Spring Data JPA
* Hibernate
* Maven
* Liquibase

### Data & Storage

* **PostgreSQL**
* **Redis**
* **Amazon S3**

### Security & File Processing

* **AES-GCM**
* **Apache Tika**
* **ClamAV**
* Cryptographic checksums
* Secure access tokens

### Infrastructure & DevOps

* **Amazon EC2**
* **Docker**
* **Docker Compose**
* **Kubernetes**
* **K3s**
* **Minikube**
* **GitHub Actions**

### API

* REST API
* Swagger / OpenAPI

### Frontend

A lightweight frontend is included as a demo client for the backend.

---

## API Endpoints

The public REST API is versioned under:

```text
/api/v1/drops
```

### Main endpoints

| Method   | Endpoint                           | Description                                            | Access                    |
| -------- | ---------------------------------- | ------------------------------------------------------ | ------------------------- |
| `POST`   | `/api/v1/drops`                    | Upload a new file and create a drop                    | Public                    |
| `GET`    | `/api/v1/drops/d/{token}`          | Download a file using its download token               | Download token            |
| `POST`   | `/api/v1/drops/d/{token}`          | Download a password-protected file                     | Download token + password |
| `GET`    | `/api/v1/drops/{id}`               | Get drop metadata and current state                    | Management token          |
| `PATCH`  | `/api/v1/drops/{id}/expiration`    | Update the drop expiration time                        | Management token          |
| `PATCH`  | `/api/v1/drops/{id}/max-downloads` | Update the maximum number of downloads                 | Management token          |
| `DELETE` | `/api/v1/drops/{id}`               | Delete the drop and request removal of its stored file | Management token          |

`{token}` is the public download token generated when the drop is created.

`{id}` is the UUID of the drop and is used together with its management token.

### Upload

```http
POST /api/v1/drops
Content-Type: multipart/form-data
```

The request contains two multipart parts:

* `file` containing the uploaded binary file
* `metadata` containing the drop configuration

The metadata can configure properties such as expiration, maximum downloads, and optional password protection.

A successful upload returns the information required to access and manage the newly created drop.

### Download

Files without password protection are downloaded with:

```http
GET /api/v1/drops/d/{token}
```

Password-protected files use the same resource through:

```http
POST /api/v1/drops/d/{token}
Content-Type: application/json
```

with the download password supplied in the request body.

Download responses include:

```http
Content-Disposition: attachment
X-Downloads-Remaining: <remaining-downloads>
```

The file body is streamed back to the client.

### Management

Management operations require the management token in the request header:

```http
X-Management-Token: <management-token>
```

Get the current drop information:

```http
GET /api/v1/drops/{id}
```

Update expiration:

```http
PATCH /api/v1/drops/{id}/expiration
```

Update the maximum number of downloads:

```http
PATCH /api/v1/drops/{id}/max-downloads
```

Delete a drop:

```http
DELETE /api/v1/drops/{id}
```

Interactive API documentation is available through **Swagger / OpenAPI** when the backend is running.

---

## CI Pipeline

GitHub Actions automatically verifies changes pushed to the main development branches.

The pipeline performs:

1. repository checkout
2. Java environment setup
3. Maven verification
4. production Docker Compose validation
5. production backend Docker image build

CI verifies both the Java application and the production container configuration.

---

## Kubernetes

FileDrop includes separate Kubernetes configurations for local development and production deployment.

The local environment uses **Minikube** to run and test the application stack in Kubernetes. The setup includes Kubernetes **Deployments**, **Services**, configuration resources, persistent storage, health probes, and **Ingress** routing.

For production, FileDrop uses **K3s**, a lightweight Kubernetes distribution suitable for the EC2 deployment. The production manifests manage the application and supporting services while **Amazon S3** remains the external persistent object storage.

The Kubernetes configuration is separated into:

```text
k8s/
├── local/
└── prod/
```

This provides separate environment-specific configurations while keeping the deployment model consistent between local development and production.

---

## Production Deployment

FileDrop is deployed on **Amazon Web Services**.

The backend application runs on **Amazon EC2**, while uploaded files are stored independently in **Amazon S3**.

This keeps permanent file storage independent from the lifecycle and local filesystem of the EC2 instance.

Production services include:

| Service        | Responsibility         |
| -------------- | ---------------------- |
| Amazon EC2     | Application hosting    |
| Amazon S3      | Encrypted file storage |
| PostgreSQL     | Metadata persistence   |
| Redis          | Rate limiting          |
| ClamAV         | Malware scanning       |
| Docker Compose | Service orchestration  |

Production configuration is separated from local development through Spring profiles, environment variables, and a dedicated `docker-compose.prod.yaml`.

Secrets and AWS credentials are provided through environment or infrastructure configuration rather than being committed to the repository.

---

## Running Locally

### Requirements

* Docker
* Docker Compose
* Git

Clone the repository:

```bash
git clone https://github.com/tuiop1/FileDrop.git
cd FileDrop
```

Create the environment configuration:

```bash
cp .env.example .env
```

Configure the required values in `.env`.

Start the application:

```bash
docker compose up --build
```

Run it in the background:

```bash
docker compose up -d --build
```

Check the running services:

```bash
docker compose ps
```

Stop the application:

```bash
docker compose down
```
