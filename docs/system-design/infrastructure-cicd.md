# Infrastructure, Containerization & CI/CD

This document details the configuration for local containerized development, production deployments, and the CI/CD pipeline.

---

## 1. Multi-Stage Dockerfile (Backend)

The `Dockerfile` is optimized for security and performance. It utilizes a multi-stage build to ensure the runtime image contains no build-time utilities or raw source code, and runs under a restricted non-root user.

```dockerfile
# ==========================================
# Stage 1: Build the Application
# ==========================================
FROM maven:3.9.6-eclipse-temurin-17-alpine AS builder
WORKDIR /build

# Copy Maven descriptor and resolve dependencies (enables layer caching)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and build jar
COPY src ./src
RUN mvn clean package -DskipTests -B

# ==========================================
# Stage 2: Production Runtime
# ==========================================
FROM eclipse-temurin:17-jre-alpine AS runner
WORKDIR /app

# Install security patches and create a non-root system group & user
RUN apk update && apk upgrade && \
    addgroup -g 10001 -S spring && \
    adduser -u 10001 -S spring -G spring

# Copy the built jar file from the builder stage
COPY --from=builder /build/target/cloudshare-*.jar ./app.jar

# Adjust ownership to the non-root user
RUN chown -R spring:spring /app
USER spring:spring

# Expose backend API port
EXPOSE 8080

# Environment-agnostic JVM tuning parameters
ENV JAVA_OPTS="-XX:+UseG1GC \
               -XX:MaxRAMPercentage=75.0 \
               -XX:MinRAMPercentage=50.0 \
               -XX:+ExitOnOutOfMemoryError"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

---

## 2. Local Docker-Compose Environment (`docker-compose.yml`)

The following compose configuration builds a local copy of CloudShare side-by-side with PostgreSQL, Redis, MinIO (for free, S3-compliant object storage), ClamAV (for scanning uploads), and an Nginx reverse proxy.

```yaml
version: '3.8'

services:
  # Nginx Gateway & Static Asset Router
  gateway:
    image: nginx:1.25-alpine
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./nginx/nginx.conf:/etc/nginx/nginx.conf:ro
      - ./nginx/ssl:/etc/nginx/ssl:ro
      - ./frontend:/usr/share/nginx/html:ro
    depends_on:
      - app

  # Spring Boot Stateless App
  app:
    build:
      context: .
      dockerfile: Dockerfile
    environment:
      - SPRING_PROFILES_ACTIVE=dev
      - SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/cloudshare
      - SPRING_DATASOURCE_USERNAME=cloudshare_user
      - SPRING_DATASOURCE_PASSWORD=StrongDBPassword123!
      - SPRING_DATA_REDIS_HOST=cache
      - SPRING_DATA_REDIS_PORT=6379
      - CLAMAV_HOST=clamav
      - CLAMAV_PORT=3310
      - STORAGE_PROVIDER=MINIO # Toggles storage service to MinIO
      - MINIO_ENDPOINT=http://storage:9000
      - MINIO_ACCESS_KEY=minioadmin
      - MINIO_SECRET_KEY=minioadmin
      - MINIO_BUCKET_NAME=cloudshare-bucket
      - CRYPTO_MASTER_KEK=32ByteHexadecimalMasterKeyEncryptKek
    depends_on:
      - db
      - cache
      - clamav
      - storage

  # PostgreSQL Relational Metadata Database
  db:
    image: postgres:16-alpine
    environment:
      - POSTGRES_DB=cloudshare
      - POSTGRES_USER=cloudshare_user
      - POSTGRES_PASSWORD=StrongDBPassword123!
    ports:
      - "5432:5432"
    volumes:
      - pgdata:/var/lib/postgresql/data

  # Redis Cache & Sessions Store
  cache:
    image: redis:7-alpine
    ports:
      - "6379:6379"
    volumes:
      - redisdata:/data

  # ClamAV Virus Scanner Daemon
  clamav:
    image: clamav/clamav:latest
    ports:
      - "3310:3310"

  # MinIO S3-Compatible Object Storage (Free local S3)
  storage:
    image: minio/minio:latest
    command: server /data --console-address ":9001"
    ports:
      - "9000:9000" # MinIO API
      - "9001:9001" # MinIO Console Web UI
    environment:
      - MINIO_ROOT_USER=minioadmin
      - MINIO_ROOT_PASSWORD=minioadmin
    volumes:
      - miniadata:/data

volumes:
  pgdata:
  redisdata:
  miniadata:
```

---

## 3. Production Deployment (Kubernetes Manifest Spec)

For production deployments, the application runs on a Kubernetes cluster. Below is an example Kubernetes configuration specifying resource allocation limits and readiness/liveness checks using Spring Boot Actuator.

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: cloudshare-backend
  namespace: cloudshare
  labels:
    app: cloudshare-backend
spec:
  replicas: 3
  selector:
    matchLabels:
      app: cloudshare-backend
  template:
    metadata:
      labels:
        app: cloudshare-backend
    spec:
      containers:
        - name: app
          image: cloudshare/backend:v1.0.0
          ports:
            - containerPort: 8080
          resources:
            requests:
              memory: "512Mi"
              cpu: "250m"
            limits:
              memory: "1024Mi"
              cpu: "1000m"
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: 8080
            initialDelaySeconds: 15
            periodSeconds: 10
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8080
            initialDelaySeconds: 20
            periodSeconds: 15
          envFrom:
            - secretRef:
                name: cloudshare-secrets
            - configMapRef:
                name: cloudshare-config
---
apiVersion: v1
kind: Service
metadata:
  name: cloudshare-backend-service
  namespace: cloudshare
spec:
  selector:
    app: cloudshare-backend
  ports:
    - protocol: TCP
      port: 80
      targetPort: 8080
  type: ClusterIP
```

---

## 4. CI/CD Pipeline Configuration (GitHub Actions)

This pipeline triggers automatically on commits to the `main` or `release/*` branches. It validates the code, runs automated tests using JUnit and Mockito, performs security vulnerability scans (OWASP Dependency Check, Trivy), builds the docker image, and deploys.

```yaml
name: CloudShare CI/CD Pipeline

on:
  push:
    branches: [ main, "release/*" ]
  pull_request:
    branches: [ main ]

jobs:
  # Job 1: Build & Verify Code Base
  verify:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout Source Code
        uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '17'
          cache: 'maven'

      - name: Compile and Run Unit/Integration Tests
        run: mvn clean verify -B

      - name: Publish Test Report
        uses: scacap/action-surefire-report@v1
        if: always()

  # Job 2: Static Security Scanning
  security-scans:
    needs: verify
    runs-on: ubuntu-latest
    steps:
      - name: Checkout Source Code
        uses: actions/checkout@v4

      - name: Run OWASP Dependency-Check (SCA)
        uses: dependency-check/Dependency-Check_Action@main
        with:
          project: 'CloudShare'
          path: '.'
          format: 'HTML'
          out: 'reports'

      - name: Run Static Code Analysis (SonarCloud / Spotbugs)
        run: mvn spotbugs:check -B

  # Job 3: Build & Publish Container Images
  publish-images:
    needs: security-scans
    if: github.event_name == 'push' && github.ref == 'refs/heads/main'
    runs-on: ubuntu-latest
    steps:
      - name: Checkout Source Code
        uses: actions/checkout@v4

      - name: Set up QEMU (Multi-platform builds)
        uses: docker/setup-qemu-action@v3

      - name: Set up Docker Buildx
        uses: docker/setup-buildx-action@v3

      - name: Log in to GitHub Container Registry (GHCR)
        uses: docker/login-action@v3
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}

      - name: Build and Push Backend Container Image
        uses: docker/build-push-action@v5
        with:
          context: .
          file: ./Dockerfile
          push: true
          tags: |
            ghcr.io/${{ github.repository }}/backend:latest
            ghcr.io/${{ github.repository }}/backend:${{ github.sha }}

      - name: Run Trivy Vulnerability Scan on Built Image
        uses: aquasecurity/trivy-action@master
        with:
          image-ref: 'ghcr.io/${{ github.repository }}/backend:${{ github.sha }}'
          format: 'table'
          exit-code: '1' # Fails build if HIGH or CRITICAL issues are found
          ignore-unfixed: true
          vuln-type: 'os,library'
          severity: 'HIGH,CRITICAL'
```
