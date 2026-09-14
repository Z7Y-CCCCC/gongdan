FROM node:22-alpine AS frontend-build
WORKDIR /workspace/frontend
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
ARG VITE_API_BASE_URL=/api
ARG VITE_MOBILE_BASE_URL=/mobile
ARG APP_PLUGIN_ALLOWED_FRONTEND_ORIGINS=http://localhost:9101,http://localhost:9102
ARG APP_ANALYTICS_ALLOWED_ORIGINS=
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
ENV VITE_MOBILE_BASE_URL=$VITE_MOBILE_BASE_URL
ENV APP_PLUGIN_ALLOWED_FRONTEND_ORIGINS=$APP_PLUGIN_ALLOWED_FRONTEND_ORIGINS
ENV APP_ANALYTICS_ALLOWED_ORIGINS=$APP_ANALYTICS_ALLOWED_ORIGINS
RUN npm run build
RUN node scripts/render-nginx.mjs nginx.conf /tmp/gongdan-nginx.conf

FROM node:22-alpine AS mobile-build
WORKDIR /workspace/mobile
COPY mobile/package*.json ./
COPY mobile/vendor/ ./vendor/
RUN npm ci --include=optional
COPY mobile/ ./
ARG VITE_API_BASE_URL=/api
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
ENV VITE_PUBLIC_BASE_URL=/mobile/
ENV VITE_WECHAT_MOCK_ENABLED=false
RUN npm run build

FROM nginx:1.27-alpine
COPY --from=frontend-build /tmp/gongdan-nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=frontend-build /workspace/frontend/dist /usr/share/nginx/html
COPY --from=mobile-build /workspace/mobile/dist/build/h5 /usr/share/nginx/html/mobile
EXPOSE 80
