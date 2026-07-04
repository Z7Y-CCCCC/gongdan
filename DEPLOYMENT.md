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
APP_UPLOAD_REPORT_IMAGE_DIR=/var/lib/heihu-workorder/report-images
```

Production defaults disable WeChat mock login, enable Flyway migrations, and set `spring.jpa.hibernate.ddl-auto=validate`.
Production startup requires explicit seed passwords for both the factory admin and the super admin, and rejects common sample values for database passwords, seed passwords, WeChat credentials, and SMS auth headers.
Token sessions are stored in the database as token hashes when the backend runs through Spring, so multiple backend instances can share login state without storing reusable bearer tokens. Horizontal scaling still requires all instances to share the same database and uploaded file storage path or object storage.
`APP_UPLOAD_REPORT_IMAGE_DIR` is currently the shared upload root for report images and custom-field attachments.

Health checks:

- `/actuator/health`
- `/actuator/health/liveness`
- `/actuator/health/readiness`

### Database Initialization

The production profile runs Flyway from `backend/src/main/resources/db/migration`.
`V1__baseline_schema.sql` creates the baseline business schema for an empty database. `V2__create_user_sessions.sql` and `V3__clear_plaintext_user_sessions.sql` move login sessions to hashed database-backed tokens. `V4__create_auth_attempts.sql` adds login lockout tracking, `V5__create_sms_codes.sql` and `V6__create_sms_ip_rate_limits.sql` add SMS login controls, `V7__create_custom_field_attachments.sql` adds protected attachment metadata for custom fields, `V8__create_warehouses.sql` adds warehouse master data, `V9__create_suppliers.sql` adds supplier master data, `V10__create_boms.sql` adds bill of materials master data, and `V11__create_sales_orders.sql` adds sales order management.

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

## Reverse Proxy and Browser Security

The bundled frontend nginx config sets baseline browser security headers (`Content-Security-Policy`, `X-Frame-Options`, `X-Content-Type-Options`, `Referrer-Policy`, and `Permissions-Policy`) and caches fingerprinted `/assets/` files for 30 days. Keep API traffic under the same site path (`/api`) where possible.

Terminate TLS at the public load balancer or reverse proxy and add HSTS there after the domain is confirmed HTTPS-only:

```nginx
add_header Strict-Transport-Security "max-age=15552000" always;
```

For commercial production, set `APP_CORS_ALLOWED_ORIGIN_PATTERNS` to explicit HTTPS origins. Wildcard CORS origins are rejected by the backend prod startup guard.
