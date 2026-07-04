# Deployment Notes

## Backend

Use the `prod` profile for commercial deployment:

```bash
cd backend
mvn -DskipTests package
java -jar target/workorder-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod
```

Required production environment variables:

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

Production defaults disable WeChat mock login and set `spring.jpa.hibernate.ddl-auto=validate`.
Production startup requires explicit seed passwords for both the factory admin and the super admin.
Current token sessions are stored in backend memory, so deploy the backend as a single instance or add Redis/JWT session storage before horizontal scaling.

Health checks:

- `/actuator/health`
- `/actuator/health/liveness`
- `/actuator/health/readiness`

### Database Initialization

The current `backend/src/main/resources/db/migration` scripts are manual patch scripts, not an enabled Flyway/Liquibase migration chain.

For a fresh commercial database:

1. Create the MySQL database and application user.
2. Run a controlled one-time initialization against an empty database with `SPRING_JPA_HIBERNATE_DDL_AUTO=update`.
3. Verify the seed factory/admin can login.
4. Switch back to the production default `SPRING_JPA_HIBERNATE_DDL_AUTO=validate` before opening traffic.

For an existing database:

1. Back up the database.
2. Apply the `V*.sql` scripts in version order if the target schema does not already contain those columns.
3. Start with the `prod` profile and keep `ddl-auto=validate`.

A full Flyway baseline migration should be added before multi-customer SaaS deployment.

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
npm run build:h5
```

For WeChat Mini Program builds, `VITE_API_BASE_URL` must be an absolute HTTPS API URL.
