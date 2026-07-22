# Deployment Notes

## Backend

Use the `prod` profile for commercial deployment:

```bash
cd backend
mvn -DskipTests package
java -jar target/workorder-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod
```

Required production environment variables:

Do not deploy with the `change-*`, `wx...`, or `example.com` placeholder values below. The backend `prod` profile fails fast when known placeholder passwords or secrets are detected.

```bash
SPRING_DATASOURCE_URL=jdbc:mysql://db-host:3306/heihu_workorder?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
SPRING_DATASOURCE_USERNAME=heihu_app
SPRING_DATASOURCE_PASSWORD=change-me
APP_SEED_FACTORY_CODE=HHGD
APP_SEED_FACTORY_NAME=Your Factory
APP_SEED_ADMIN_USERNAME=admin
APP_SEED_ADMIN_PASSWORD=change-me
APP_SEED_ADMIN_NAME=System Admin
APP_SEED_SUPER_ADMIN_USERNAME=admin
APP_SEED_SUPER_ADMIN_PASSWORD=change-me-too
APP_WECHAT_APP_ID=wx...
APP_WECHAT_SECRET=change-me
APP_WECHAT_OPENID_TICKET_TTL_MINUTES=10
APP_CORS_ALLOWED_ORIGIN_PATTERNS=https://workorder.example.com,https://admin.example.com
APP_SESSION_TTL_MINUTES=720
APP_UPLOAD_STORAGE_TYPE=local
APP_UPLOAD_REPORT_IMAGE_DIR=/var/lib/heihu-workorder/report-images
APP_UPLOAD_S3_ENDPOINT=
APP_UPLOAD_S3_REGION=us-east-1
APP_UPLOAD_S3_BUCKET=
APP_UPLOAD_S3_ACCESS_KEY=
APP_UPLOAD_S3_SECRET_KEY=
APP_UPLOAD_S3_PATH_STYLE_ACCESS=true
APP_UPLOAD_S3_KEY_PREFIX=report-images
```

Production defaults disable WeChat mock login, enable Flyway migrations, and set `spring.jpa.hibernate.ddl-auto=validate`.
Production startup requires explicit seed passwords for both the factory admin and the super admin, and rejects common sample values for database passwords, seed passwords, WeChat credentials, and SMS auth headers.
Token sessions are stored in the database as token hashes when the backend runs through Spring, so multiple backend instances can share login state without storing reusable bearer tokens. Horizontal scaling requires all instances to share the same database and uploaded file storage.
For a single backend instance, `APP_UPLOAD_STORAGE_TYPE=local` stores report images and custom-field attachments under `APP_UPLOAD_REPORT_IMAGE_DIR`.
For multiple backend instances, set `APP_UPLOAD_STORAGE_TYPE=s3` and configure the S3-compatible settings. MinIO, AWS S3, and S3-compatible object storage can be used; `APP_UPLOAD_S3_KEY_PREFIX` defaults to `report-images`.

Health checks:

- `/actuator/health`
- `/actuator/health/liveness`
- `/actuator/health/readiness`

### Database Initialization

The production profile runs Flyway from `backend/src/main/resources/db/migration`.
`V1__baseline_schema.sql` creates the baseline business schema for an empty database. `V2__create_user_sessions.sql` and `V3__clear_plaintext_user_sessions.sql` move login sessions to hashed database-backed tokens. `V4__create_auth_attempts.sql` adds login lockout tracking, `V5__create_sms_codes.sql` and `V6__create_sms_ip_rate_limits.sql` add SMS login controls, `V7__create_custom_field_attachments.sql` adds protected attachment metadata for custom fields, `V8__create_warehouses.sql` adds warehouse master data, `V9__create_suppliers.sql` adds supplier master data, `V10__create_boms.sql` adds bill of materials master data, `V11__create_sales_orders.sql` adds sales order management, `V12__create_customers.sql` adds customer master data, and `V13__create_extension_records.sql` adds the advanced operations record store for SOP documents, equipment/workstations, quality rework, message push settings, payroll rules, and mobile field events.

For a fresh commercial database:

1. Create the MySQL database and application user.
2. Start with the `prod` profile; Flyway will create the schema before Hibernate validates it.
3. Verify the seed factory/admin can login.

For an existing database:

1. Back up the database.
2. Keep the production default `SPRING_FLYWAY_BASELINE_ON_MIGRATE=true` for the first Flyway-managed start. Flyway records the existing schema as version 1, then applies version 2+ migrations.
3. Keep `SPRING_JPA_HIBERNATE_DDL_AUTO=validate` before opening traffic.

## Frontend

```bash
cd frontend
cp .env.example .env.production
npm ci
npm run build
```

Set `VITE_API_BASE_URL` to the backend API path exposed by nginx, usually `/api`.

## Mobile

```bash
cd mobile
cp .env.example .env.production
npm ci
npm run build
```

For WeChat Mini Program builds, `VITE_API_BASE_URL` must be an absolute HTTPS API URL.

## Docker Compose

The repository includes a minimal production-style Compose stack:

```bash
cp .env.deploy.example .env
docker compose up --build -d
```

The stack starts MySQL, the Spring Boot backend with the `prod` profile, and an nginx-served admin frontend that proxies `/api` to the backend.
Uploaded report images are stored in the `report-images` Docker volume.

## Windows Internal Demo Without Docker

For a low-resource Windows Server that already runs MySQL 9.2, Java, and nginx, build the native release package:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File ops\build-windows-release.ps1
```

The package is written to `release-windows` and is designed for `C:\www\gongdan`. It runs the backend on `127.0.0.1:18080` and adds a separate nginx server on port `8088`, so an existing website on ports 80/443 is not replaced. The same site serves the admin UI at `/`, mobile H5 at `/mobile/`, and the API at `/api/`.

The `demo` Spring profile enables mock WeChat identity, log-only SMS, and Hibernate schema updates for internal workflow testing on MySQL 9.2. The Java 8 compatible Flyway line used by this Spring Boot version does not recognize MySQL 9.2, so use MySQL 8.4 LTS or upgrade the Spring Boot/Java/Flyway stack before a migration-managed production rollout. The demo profile must not be used as a public production profile. See `deploy/windows/README.md` for server installation and temporary HTTPS tunnel instructions.

Run the non-destructive preflight before starting or updating the stack:

```bash
sh ops/preflight-compose.sh
```

The preflight checks required `.env` values, rejects known placeholders and wildcard/non-HTTPS production CORS values, and validates the rendered Compose config.

## Backup

Back up both MySQL and uploaded report/custom-field attachments before every migration or release:

```bash
sh ops/backup-compose.sh
```

The script reads `.env`, writes timestamped files under `backups/`, and captures:

- `mysql_YYYYMMDD_HHMMSS.sql`
- `report-images_YYYYMMDD_HHMMSS.tar.gz`

Set `ENV_FILE` or `BACKUP_DIR` when needed:

```bash
ENV_FILE=/opt/gongdan/.env BACKUP_DIR=/data/gongdan-backups sh ops/backup-compose.sh
```

Store backup copies outside the Docker host as well. A local-only backup does not protect against disk loss.

## Restore and Drill

Restore is destructive and must be tested before a real incident. The restore script refuses to run unless `CONFIRM_RESTORE=yes` is set.

```bash
CONFIRM_RESTORE=yes \
DB_BACKUP_PATH=backups/mysql_YYYYMMDD_HHMMSS.sql \
UPLOAD_BACKUP_PATH=backups/report-images_YYYYMMDD_HHMMSS.tar.gz \
sh ops/restore-compose.sh
```

Run a restore drill for every release train:

1. Start a temporary Compose environment with a copy of production `.env` values adjusted for a non-production host.
2. Restore the latest database and attachment backups.
3. Run `sh ops/monitor-compose.sh`.
4. Log in as an admin, open work orders, report approvals, and attachment previews.
5. Record the backup timestamp, restore duration, operator, and result.

If `APP_UPLOAD_STORAGE_TYPE=s3` is used, back up and restore the object storage bucket with the provider's snapshot, replication, or lifecycle tooling in addition to the MySQL dump.

## Monitoring and Alerting

The stack has Docker health checks and Spring Boot actuator probes. For host-level monitoring or cron checks:

```bash
sh ops/monitor-compose.sh
```

Optional environment variables:

```bash
FRONTEND_URL=https://workorder.example.com/
BACKEND_HEALTH_URL=https://workorder.example.com/api/actuator/health/readiness
ALERT_WEBHOOK_URL=https://alert-gateway.example.com/hooks/gongdan
TIMEOUT_SECONDS=8
```

The script exits non-zero on frontend or backend readiness failure and posts a simple JSON payload to `ALERT_WEBHOOK_URL` when configured. In production, run it from cron or an external monitor at 1 minute to 5 minute intervals.

## CI/CD

GitHub Actions workflow `.github/workflows/ci.yml` runs on pushes to `main` and `codex/**` branches and on pull requests:

- backend Maven tests
- frontend `npm run build`
- mobile H5 `npm run build`
- Docker Compose config rendering

Keep deployment as a separate promoted step after CI passes. Before each production release, run `sh ops/preflight-compose.sh`, `sh ops/backup-compose.sh`, deploy, then run `sh ops/monitor-compose.sh`.

## Reverse Proxy and Browser Security

The bundled frontend nginx config sets baseline browser security headers (`Content-Security-Policy`, `X-Frame-Options`, `X-Content-Type-Options`, `Referrer-Policy`, and `Permissions-Policy`) and caches fingerprinted `/assets/` files for 30 days. Keep API traffic under the same site path (`/api`) where possible.

Terminate TLS at the public load balancer or reverse proxy and add HSTS there after the domain is confirmed HTTPS-only:

```nginx
add_header Strict-Transport-Security "max-age=15552000" always;
```

For commercial production, set `APP_CORS_ALLOWED_ORIGIN_PATTERNS` to explicit HTTPS origins. Wildcard CORS origins are rejected by the backend prod startup guard.
