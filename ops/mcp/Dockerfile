FROM node:22-alpine

WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --omit=dev
COPY server.mjs ./

ENV NODE_ENV=production
ENV MCP_HOST=0.0.0.0
EXPOSE 9300

USER node
ENTRYPOINT ["node", "server.mjs"]
