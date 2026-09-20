FROM node:22-alpine AS build
WORKDIR /workspace
COPY package*.json ./
RUN npm ci
COPY . .
ARG VITE_API_BASE_URL=/api
ARG APP_PLUGIN_ALLOWED_FRONTEND_ORIGINS=http://localhost:9101,http://localhost:9102
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
ENV APP_PLUGIN_ALLOWED_FRONTEND_ORIGINS=$APP_PLUGIN_ALLOWED_FRONTEND_ORIGINS
RUN npm run build
RUN node scripts/render-nginx.mjs nginx.conf /tmp/gongdan-nginx.conf

FROM nginx:1.27-alpine
COPY --from=build /tmp/gongdan-nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/dist /usr/share/nginx/html
EXPOSE 80
