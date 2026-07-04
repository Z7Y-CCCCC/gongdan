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
APP_WECHAT_APP_ID=wx...
APP_WECHAT_SECRET=change-me
APP_UPLOAD_REPORT_IMAGE_DIR=/var/lib/heihu-workorder/report-images
```

Production defaults disable WeChat mock login and set `spring.jpa.hibernate.ddl-auto=validate`.

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
