# syntax=docker/dockerfile:1

FROM node:24-alpine AS build

WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund

COPY . ./
RUN npm run build

FROM caddy:2.11.4-alpine AS runtime

ENV FILEDROP_CADDY_ADDRESS=http://localhost \
    FILEDROP_BACKEND_URL=http://host.docker.internal:8080 \
    FILEDROP_PROXY_TIMEOUT=15m

COPY Caddyfile /etc/caddy/Caddyfile
COPY --from=build /app/dist/frontend/browser/ /srv/

EXPOSE 80 443 443/udp 8081
