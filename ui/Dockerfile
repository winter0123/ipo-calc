FROM node:22-alpine AS build
RUN npm i -g pnpm@10
WORKDIR /src
COPY package.json pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile
COPY . .
# API는 같은 도메인의 /api 로 호출한다(Caddy가 /api 접두사를 떼고 backend로 전달)
ARG VITE_API_BASE_URL=/api
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
RUN pnpm run build

FROM caddy:2-alpine
COPY --from=build /src/dist /srv/ui
COPY Caddyfile /etc/caddy/Caddyfile
