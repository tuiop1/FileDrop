# syntax=docker/dockerfile:1

FROM node:24-alpine AS build

WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund

COPY . ./
RUN npm run build

FROM nginx:alpine AS runtime

ENV FILEDROP_BACKEND_URL=http://host.docker.internal:8080

COPY nginx.conf /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist/frontend/browser/ /usr/share/nginx/html/

EXPOSE 80
