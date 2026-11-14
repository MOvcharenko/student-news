FROM node:26-alpine AS build
WORKDIR /app
# npm ci installs exactly what package-lock.json pins and fails if the lock
# file and package.json disagree, the same as the CI jobs.
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

# nginx-unprivileged is the same nginx with every process running as the
# nginx user (uid 101) instead of a root master process. It listens on 8080
# because binding ports below 1024 has traditionally required root.
FROM nginxinc/nginx-unprivileged:1.31-alpine
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 8080
